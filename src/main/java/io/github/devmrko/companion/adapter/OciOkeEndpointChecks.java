package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.springframework.stereotype.Component;
@Component
public class OciOkeEndpointChecks implements OkeEndpointChecks {
 private final OkeRuntimeResources resources;
 private static final Semaphore SLOTS=new Semaphore(4);
 private static final ThreadPoolExecutor DNS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),Thread.ofPlatform().daemon().name("oke-dns-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
 private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("oke-probe-deadline").factory());
 public OciOkeEndpointChecks(OkeRuntimeResources resources){this.resources=resources;}
 public Report inspect(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String ingress,String host,String path){
  if(!validHost(host)||!validPath(path))throw Errors.INVALID.exception();
  var s=resources.read(c,region,compartment,environment,endpoint,namespace);var listing=s.resources().get("ingresses");
  if(listing==null||!listing.available())return new Report(Instant.now(),"",List.of(new Check("Ingress","UNKNOWN","목록 조회 실패 · Kubernetes list 권한과 API 접속 확인")));
  var selected=s.ingresses().stream().filter(x->x.name().equals(ingress)).findFirst().orElseThrow(()->Errors.INVALID.exception());
  if(selected.routes().stream().noneMatch(x->host.equals(x.host())&&path.equals(x.path())))throw Errors.INVALID.exception();
  if(!SLOTS.tryAcquire())return new Report(Instant.now(),"",List.of(new Check("점검","UNKNOWN","다른 점검 진행 중 · 잠시 후 다시 실행")));
  try{return probe(host,path,selected.addresses());}finally{SLOTS.release();}
 }
 static boolean validHost(String host){return host!=null&&host.length()<=253&&host.contains(".")&&host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")&&!host.contains("..")&&!host.matches("[0-9.]+");}
 static boolean validPath(String path){return path!=null&&path.length()<=1024&&path.startsWith("/")&&!path.startsWith("//")&&path.matches("/[a-zA-Z0-9/._~!$&'()+,;=:@%\\-]*");}
 static boolean publicAddress(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
  byte[] b=a.getAddress();int x=b[0]&255,y=b[1]&255,z=b[2]&255;
  if(b.length==4)return x!=0&&x!=10&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==172&&y>=16&&y<=31)&&!(x==192&&(y==168||(y==0&&(z==0||z==2))||(y==88&&z==99)))&&!(x==198&&(y==18||y==19||(y==51&&z==100)))&&!(x==203&&y==0&&(b[2]&255)==113);
  // Conservative global-unicast IPv6 only; exclude documentation and transition ranges.
  return (x&0xe0)==0x20&&!(x==0x20&&y==0x01&&(z<=1||(z==0x0d&&(b[3]&255)==0xb8)))&&!(x==0x20&&y==0x02)&&!(x==0x3f&&y==0xff);
 }
 private static InetAddress[] resolve(String host)throws Exception{Future<InetAddress[]> f=DNS.submit(()->InetAddress.getAllByName(host));try{return f.get(3,TimeUnit.SECONDS);}finally{f.cancel(true);}}
 static Report probe(String host,String path,List<String> ingressAddresses){
  String target="https://"+host+path;var checks=new ArrayList<Check>();
  if(ingressAddresses.isEmpty())checks.add(new Check("Ingress 주소","WARN","주소 미할당 · DNS 응답이 해당 Ingress로 연결되는지는 별도 확인"));
  InetAddress[] addresses;
  try{addresses=resolve(host);if(addresses.length==0)throw new UnknownHostException();checks.add(new Check("DNS","PASS",String.join(", ",Arrays.stream(addresses).limit(8).map(InetAddress::getHostAddress).toList())));}
  catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();checks.add(new Check("DNS","UNKNOWN","이름 해석 실패·시간 초과 · DNS 레코드와 앱 서버 네트워크 확인"));return report(target,checks);}
  if(!ingressAddresses.isEmpty()){boolean same=Arrays.stream(addresses).anyMatch(a->ingressAddresses.contains(a.getHostAddress()));checks.add(new Check("Ingress 주소",same?"PASS":"WARN",same?"DNS와 Ingress IP 주소 일치":"DNS와 Ingress 주소 일치 미확인 · CDN·별칭·DNS 레코드 확인"));}
  if(Arrays.stream(addresses).anyMatch(a->!publicAddress(a))){checks.add(new Check("접속 범위","UNKNOWN","사설·예약 주소는 점검 미지원 · VCN 내부에서 별도 확인"));return report(target,checks);}
  InetAddress address=addresses[0];checks.add(new Check("접속 대상","PASS",address.getHostAddress()+":443 · DNS 첫 주소 1개 점검"));
  try(Socket tcp=new Socket()){
   var deadline=DEADLINES.schedule(()->{try{tcp.close();}catch(IOException ignored){}},10,TimeUnit.SECONDS);
   try{
    tcp.connect(new InetSocketAddress(address,443),3000);tcp.setSoTimeout(4000);
    try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,host,443,true)){
     tls.setSoTimeout(4000);var params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");params.setServerNames(List.of(new SNIHostName(host)));params.setApplicationProtocols(new String[]{"http/1.1"});tls.setSSLParameters(params);tls.startHandshake();
     var cert=(X509Certificate)tls.getSession().getPeerCertificates()[0];cert.checkValidity();long days=Duration.between(Instant.now(),cert.getNotAfter().toInstant()).toDays();
     checks.add(new Check("TLS",days<30?"WARN":"PASS","신뢰 체인·호스트명 확인 · 만료 "+cert.getNotAfter().toInstant()+" · "+days+"일 남음"));
     try{tls.getOutputStream().write(("HEAD "+path+" HTTP/1.1\r\nHost: "+host+"\r\nUser-Agent: OCI-Companion-Check\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));tls.getOutputStream().flush();int status=status(tls.getInputStream());checks.add(new Check("HTTP",status>=200&&status<300?"PASS":"WARN","HEAD "+status+(status>=300&&status<400?" · 리다이렉트 미추적":status==401||status==403?" · 인증·접근 정책 확인":status==405?" · HEAD 미지원, 앱 응답 별도 확인":"")+" · 본문 미수집"));}
     catch(Exception e){checks.add(new Check("HTTP","UNKNOWN","TLS 연결 후 HTTP 상태 확인 실패·시간 초과"));}
    }
   }finally{deadline.cancel(false);}
  }catch(SSLException|java.security.cert.CertificateException e){checks.add(new Check("TLS","FAIL","인증서 신뢰·호스트명·유효기간 또는 TLS 협상 실패 · 서버 인증서 확인"));}
  catch(Exception e){checks.add(new Check("연결","UNKNOWN","TCP/TLS 연결 실패·시간 초과 · 443 포트·방화벽·Ingress 컨트롤러 확인"));}
  return report(target,checks);
 }
 static int status(InputStream input)throws IOException{var b=new ByteArrayOutputStream();for(int i=0;i<256;i++){int v=input.read();if(v<0)break;if(v=='\n'){String line=b.toString(StandardCharsets.US_ASCII);if(!line.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: .*)?\\r?"))throw new IOException();return Integer.parseInt(line.substring(9,12));}b.write(v);}throw new IOException();}
 private static Report report(String target,List<Check> checks){return new Report(Instant.now(),target,List.copyOf(checks));}
}
