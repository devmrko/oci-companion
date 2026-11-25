package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildSpecDraftStore;
import io.github.devmrko.companion.service.Errors;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Cluster-scoped local access settings; never changes OCI networking. */
@Component
public class OkeApiAccess {
 public record Settings(String server,String tlsServerName,String jumpHost,String jumpUser,String keyPath){
  public Settings {server=Objects.toString(server,"").strip();tlsServerName=Objects.toString(tlsServerName,"").strip();jumpHost=Objects.toString(jumpHost,"").strip();jumpUser=Objects.toString(jumpUser,"").strip();keyPath=Objects.toString(keyPath,"").strip();}
 }
 public record Address(String server,String tlsServerName){}
 private record Tunnel(Settings settings,int port,Process process){}
 private final BuildSpecDraftStore store;
 private final Map<String,Tunnel> tunnels=new LinkedHashMap<>();
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 public OkeApiAccess(BuildSpecDraftStore store){this.store=store;}
 static String key(Connection c,String region,String compartment,String cluster){return String.join("\n","oke-api-access-v1",c.profile(),c.tenancy(),c.user(),region,compartment,cluster);}
 public Settings read(Connection c,String region,String compartment,String cluster){return store.read(key(c,region,compartment,cluster)).map(d->json.readValue(d.content(),Settings.class)).orElse(new Settings("","","","",""));}
 public static void validate(Settings s){
  if(!s.server().isEmpty()){
   URI u;try{u=URI.create(s.server());}catch(Exception e){throw Errors.INVALID.exception();}
   if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/"))||u.getPort()==0||u.getPort()>65535)throw Errors.INVALID.exception();
  }
  if(!s.tlsServerName().isEmpty()&&!s.tlsServerName().matches("[a-zA-Z0-9.-]{1,253}"))throw Errors.INVALID.exception();
  if(!s.jumpHost().isEmpty()){
   if(s.server().isEmpty()||!s.jumpHost().matches("[a-zA-Z0-9][a-zA-Z0-9.-]{0,252}")||!s.jumpUser().matches("[a-zA-Z_][a-zA-Z0-9_-]{0,63}")||!Path.of(s.keyPath()).isAbsolute()||s.keyPath().chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
  }else if(!s.jumpUser().isEmpty()||!s.keyPath().isEmpty())throw Errors.INVALID.exception();
 }
 public synchronized void save(Connection c,String region,String compartment,String cluster,Settings settings){
  validate(settings);String key=key(c,region,compartment,cluster);store.save(key,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(settings),Instant.now().toString()));close(key);
 }
 public synchronized void disconnect(Connection c,String region,String compartment,String cluster){close(key(c,region,compartment,cluster));}
 private void close(String key){var t=tunnels.remove(key);if(t!=null){t.process().descendants().forEach(ProcessHandle::destroy);t.process().destroy();}}
 @jakarta.annotation.PreDestroy public synchronized void close(){new ArrayList<>(tunnels.keySet()).forEach(this::close);}
 static List<String> sshCommand(Settings s,int port){
  validate(s);var target=URI.create(s.server());String host=target.getHost();if(host.contains(":"))host="["+host+"]";
  return List.of("/usr/bin/ssh","-N","-T","-o","BatchMode=yes","-o","ExitOnForwardFailure=yes","-o","StrictHostKeyChecking=yes","-o","ConnectTimeout=10","-o","ServerAliveInterval=30","-i",s.keyPath(),"-L","127.0.0.1:"+port+":"+host+":"+(target.getPort()<0?443:target.getPort()),s.jumpUser()+"@"+s.jumpHost());
 }
 Process start(List<String> command)throws Exception{return new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();}
 public synchronized Address address(Connection c,String region,String compartment,String cluster)throws Exception {
  var s=read(c,region,compartment,cluster);validate(s);if(s.jumpHost().isEmpty())return new Address(s.server(),s.tlsServerName());
  String key=key(c,region,compartment,cluster);var existing=tunnels.get(key);
  if(existing!=null&&(!existing.settings().equals(s)||!existing.process().isAlive())){close(key);existing=null;}
  if(existing==null){
   if(tunnels.size()>=16)throw new IllegalStateException("열린 SSH 터널이 너무 많습니다. 사용하지 않는 터널을 닫으세요.");
   if(!Files.isRegularFile(Path.of(s.keyPath())))throw new IllegalArgumentException("SSH 키 경로를 확인하세요.");
   int port;try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
   var process=start(sshCommand(s,port));boolean ready=false;
   try{
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
    while(process.isAlive()&&System.nanoTime()<deadline){try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),200);ready=true;break;}catch(java.io.IOException ignored){Thread.sleep(100);}}
    if(!ready)throw new IllegalStateException("SSH 터널 연결 실패 · 호스트 키 등록·SSH 키·접속 주소를 확인하세요.");
    existing=new Tunnel(s,port,process);tunnels.put(key,existing);
   }finally{if(!ready)process.destroy();}
  }
  return new Address("https://127.0.0.1:"+existing.port(),s.tlsServerName().isEmpty()?URI.create(s.server()).getHost():s.tlsServerName());
 }
 OkeWorkloadHealth.Target apply(OkeWorkloadHealth.Target t)throws Exception {
  if(t.clusterId()==null||t.clusterId().isBlank())return t;
  var c=new Connection(t.profile(),t.tenancy(),t.user(),t.region(),"",false);var a=address(c,t.region(),t.compartment(),t.clusterId());
  if(a.server().isEmpty()&&a.tlsServerName().isEmpty())return t;
  return new OkeWorkloadHealth.Target(t.appId(),t.profile(),t.tenancy(),t.user(),t.region(),t.compartment(),t.clusterId(),t.kubeconfig(),t.namespace(),t.deployment(),a.server().isEmpty()?t.server():a.server(),a.tlsServerName().isEmpty()?t.tlsServerName():a.tlsServerName(),t.inspectNodes(),t.endpoint());
 }
}
