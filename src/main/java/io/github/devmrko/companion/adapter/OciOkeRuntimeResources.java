package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeRuntimeResources;
import io.github.devmrko.companion.service.Errors;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class OciOkeRuntimeResources implements OkeRuntimeResources {
 private final OciBuildHistory clients;private final OciOkeWorkloadConnections connections;private final OkeWorkloadHealth reader;
 public OciOkeRuntimeResources(OciBuildHistory clients,OciOkeWorkloadConnections connections,OkeWorkloadHealth reader){this.clients=clients;this.connections=connections;this.reader=reader;}
 private OkeWorkloadHealth.Target environmentTarget(Connection c,String region,String compartment,String environment,String endpoint,String namespace)throws Exception {
  try(var d=clients.client(c,region)){
   var e=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(environment).build()).getDeployEnvironment();
   if(!(e instanceof OkeClusterDeployEnvironment oke)||!compartment.equals(e.getCompartmentId())||e.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.SCOPE.exception();
   return connections.prepare(c,region,compartment,oke.getClusterId(),endpoint,namespace,"unused");
  }
 }
 public String deployment(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String name){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.name(name);OciOkeWorkloadConnections.endpoint(endpoint);
   target=environmentTarget(c,region,compartment,environment,endpoint,namespace);
   return new String(reader.command(target,List.of("get","deployment",name,"-o","json"),524288),StandardCharsets.UTF_8);
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw Errors.DEPLOYMENT_RUNTIME.exception();}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public io.github.devmrko.companion.port.DeploymentImageVerification.Result verifyImages(Connection c,String region,String compartment,String environment,String endpoint,io.github.devmrko.companion.port.DeploymentImageVerification.Expected expected){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(expected.namespace());OciOkeWorkloadConnections.name(expected.deployment());OciOkeWorkloadConnections.endpoint(endpoint);
   target=environmentTarget(c,region,compartment,environment,endpoint,expected.namespace());
   return OkeWorkloadHealth.verifyImages(target,expected,reader.read(target,"deployment",expected.deployment()),reader.read(target,"replicasets",null),reader.read(target,"pods",null));
  }catch(Exception e){return io.github.devmrko.companion.port.DeploymentImageVerification.Result.unknown();}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public Retirement retirement(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String app){
  OkeWorkloadHealth.Target target=null;
  try{OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.name(app);target=environmentTarget(c,region,compartment,environment,endpoint,namespace);
   return retirement(reader.read(target,"deployment",app),reader.read(target,"services",null),reader.read(target,"ingresses",null),namespace,app);
  }catch(RuntimeException e){throw e;}catch(Exception e){throw Errors.DEPLOYMENT_RUNTIME.exception();}finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 static Retirement retirement(JsonNode deployment,JsonNode services,JsonNode ingresses,String namespace,String app){
  var meta=deployment.path("metadata");if(!app.equals(meta.path("name").asText())||!namespace.equals(meta.path("namespace").asText())||meta.has("deletionTimestamp"))throw Errors.CONFLICT.exception();
  var labels=deployment.path("spec").path("template").path("metadata").path("labels");JsonNode chosen=null;
  if(!services.path("items").isArray()||!ingresses.path("items").isArray())throw Errors.DEPLOYMENT_RUNTIME.exception();
  for(var service:services.path("items")){
   var selector=service.path("spec").path("selector");boolean matches=selector.isObject()&&!selector.isEmpty();
   for(String key:selector.propertyNames())if(!selector.path(key).equals(labels.path(key)))matches=false;
   if(!matches)continue;
   if(chosen!=null||!app.equals(service.path("metadata").path("name").asText())||!namespace.equals(service.path("metadata").path("namespace").asText())||!"ClusterIP".equals(service.path("spec").path("type").asText())||service.path("spec").has("externalIPs"))throw new IllegalArgumentException("이관 정리는 앱과 같은 이름의 단일 ClusterIP만 지원합니다 · 외부 노출을 먼저 확인하세요");chosen=service;
  }
  if(chosen==null)throw new IllegalArgumentException("원본 ClusterIP Service를 확인할 수 없습니다");
  for(var ingress:ingresses.path("items"))if(ingress(ingress).routes().stream().anyMatch(r->r.service().equals(app)))throw new IllegalArgumentException("Ingress가 원본 서비스를 참조합니다 · 외부 경로 전환 후 정리하세요");
  var sm=chosen.path("metadata");var result=new Retirement(meta.path("uid").asText(),meta.path("resourceVersion").asText(),sm.path("uid").asText(),sm.path("resourceVersion").asText());
  if(result.deploymentUid().isBlank()||result.deploymentVersion().isBlank()||result.serviceUid().isBlank()||result.serviceVersion().isBlank())throw Errors.CONFLICT.exception();return result;
 }
 public void retire(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String app,Retirement approved){
  if(!retirement(c,region,compartment,environment,endpoint,namespace,app).equals(approved))throw Errors.BUILD_CHANGED.exception();
  OkeWorkloadHealth.Target target=null;Path options=null;
  try{target=environmentTarget(c,region,compartment,environment,endpoint,namespace);options=Files.createTempFile("companion-retire-",".json");
   var json=tools.jackson.databind.json.JsonMapper.builder().build();
   // API-side UID and resourceVersion preconditions prevent deleting a replacement or concurrent change.
   for(String kind:List.of("deployments","services")){
    boolean deployment=kind.equals("deployments");Files.writeString(options,json.writeValueAsString(Map.of("apiVersion","v1","kind","DeleteOptions","propagationPolicy","Foreground","preconditions",Map.of("uid",deployment?approved.deploymentUid():approved.serviceUid(),"resourceVersion",deployment?approved.deploymentVersion():approved.serviceVersion()))));
    String uri=(deployment?"/apis/apps/v1":"/api/v1")+"/namespaces/"+namespace+"/"+kind+"/"+app;
    reader.command(target,List.of("delete","--raw",uri,"-f",options.toString()),524288);
   }
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);boolean absent=false;
   while(System.nanoTime()<deadline){
    String remaining=new String(reader.command(target,List.of("get","deployment,service",app,"--ignore-not-found","-o","name"),8192),StandardCharsets.UTF_8);
    if(remaining.isBlank()){absent=true;break;}Thread.sleep(1000);
   }
   if(!absent)throw new IllegalStateException("원본 삭제 완료 대기 시간 초과");
  }catch(Exception e){throw new IllegalStateException("원본 정리 완료를 확인하지 못했습니다 · 부분 삭제 여부를 조회하세요. 자동 재시도하지 않습니다.");}
  finally{if(options!=null)try{Files.deleteIfExists(options);}catch(Exception ignored){}if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public NodePorts nodePorts(Connection c,String region,String compartment,String environment,String endpoint,String namespace){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.endpoint(endpoint);target=environmentTarget(c,region,compartment,environment,endpoint,namespace);
   var data=tools.jackson.databind.json.JsonMapper.builder().build().readTree(reader.command(target,List.of("get","services","--all-namespaces","-o","json"),2_000_000));
   var items=data.path("items");if(!items.isArray()||items.size()>5000)throw new IllegalArgumentException();var result=new ArrayList<AllocatedPort>();
   for(var service:items)for(var port:service.path("spec").path("ports"))if(port.has("nodePort"))result.add(new AllocatedPort(service.path("metadata").path("namespace").asText(),service.path("metadata").path("name").asText(),port.path("nodePort").asInt()));
   return new NodePorts(true,List.copyOf(result));
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){return new NodePorts(false,List.of());}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public io.github.devmrko.companion.port.RegistryCredentials pullCredentials(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String secret,String host){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.name(secret);OciOkeWorkloadConnections.endpoint(endpoint);
   if(!host.equals(region+".ocir.io")&&!host.equals(com.oracle.bmc.Region.fromRegionId(region).getRegionCode()+".ocir.io"))throw Errors.SCOPE.exception();
   target=environmentTarget(c,region,compartment,environment,endpoint,namespace);
   byte[] encoded=reader.command(target,List.of("get","secret",secret,"-o","jsonpath={.data.\\.dockerconfigjson}"),65536);
   return io.github.devmrko.companion.service.PullRegistryCredentials.decode(encoded,host);
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}
  catch(Exception e){throw new IllegalStateException("OCIR 인증 Secret 조회 실패 · 환경·Namespace·Secret get 권한·레지스트리 호스트를 확인하세요.");}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public PullRegistry pullRegistry(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String secret){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.name(secret);OciOkeWorkloadConnections.endpoint(endpoint);
   target=environmentTarget(c,region,compartment,environment,endpoint,namespace);
   byte[] encoded=reader.command(target,List.of("get","secret",secret,"-o","jsonpath={.data.\\.dockerconfigjson}"),65536);
   var hosts=io.github.devmrko.companion.service.PullRegistryHosts.decode(encoded);
   return new PullRegistry(!hosts.isEmpty(),hosts,hosts.isEmpty()?"dockerconfigjson 레지스트리 호스트를 확인하지 못했습니다.":"");
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}
  catch(Exception e){return new PullRegistry(false,List.of(),"pull Secret 레지스트리 조회 실패 · Secret get 권한·API 연결·dockerconfigjson 형식을 확인하세요.");}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 public Snapshot read(Connection c,String region,String compartment,String environment,String endpoint,String namespace){
  OkeWorkloadHealth.Target target=null;
  try{
   OciOkeWorkloadConnections.name(namespace);OciOkeWorkloadConnections.endpoint(endpoint);
   target=environmentTarget(c,region,compartment,environment,endpoint,namespace);var t=target;var observed=Instant.now();
   var resources=new LinkedHashMap<String,Listing>();var ingresses=new ArrayList<Ingress>();var services=new ArrayList<Service>();
   try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
    var work=new LinkedHashMap<String,Future<Object>>();
    for(String kind:List.of("configmaps","secrets","serviceaccounts","persistentvolumeclaims"))work.put(kind,executor.submit(()->metadata(t,kind)));
    for(String kind:List.of("ingresses","services"))work.put(kind,executor.submit(()->reader.read(t,kind,null)));
    for(var entry:work.entrySet()){
     String kind=entry.getKey();
     try{
      Object value=entry.getValue().get();
      if(value instanceof Listing listing)resources.put(kind,listing);
      else {
       var items=((JsonNode)value).path("items");if(!items.isArray()||items.size()>500)throw new IllegalArgumentException();
       var names=new ArrayList<Resource>();
       for(var item:items){if(!namespace.equals(item.path("metadata").path("namespace").asText()))throw new IllegalArgumentException();String name=item.path("metadata").path("name").asText();OciOkeWorkloadConnections.name(name);
        names.add(new Resource(name,kind));if(kind.equals("ingresses"))ingresses.add(ingress(item));else services.add(new Service(name,item.path("spec").path("type").asText(),addresses(item)));
       }
       resources.put(kind,new Listing(true,List.copyOf(names),""));
      }
     }catch(Exception e){if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw e;}resources.put(kind,new Listing(false,List.of(),kind+" 조회 실패 · list 권한·API 연결·500개 한도 확인"));if(kind.equals("ingresses"))ingresses.clear();if(kind.equals("services"))services.clear();}
    }
   }
   return new Snapshot(observed,target.clusterId(),namespace,Map.copyOf(resources),List.copyOf(ingresses),List.copyOf(services));
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw Errors.CLOUD.exception();}
  finally{if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
 }
 Listing metadata(OkeWorkloadHealth.Target t,String kind)throws Exception{
  if(!Set.of("configmaps","secrets","serviceaccounts","persistentvolumeclaims").contains(kind))throw new IllegalArgumentException();
  // kubectl prints only these fields; no Secret/ConfigMap data enters the app process or files.
  String projection="jsonpath={range .items[*]}{.metadata.namespace}{'\\t'}{.metadata.name}{'\\t'}{.type}{'\\n'}{end}";
  return metadata(new String(reader.command(t,List.of("get",kind,"-o",projection),131072),StandardCharsets.UTF_8),t.namespace());
 }
 static Listing metadata(String output,String namespace){
  var result=new ArrayList<Resource>();for(String row:output.lines().toList()){
   String[] fields=row.split("\\t",-1);if(fields.length!=3||!namespace.equals(fields[0])||result.size()>=500)throw new IllegalArgumentException();
   OciOkeWorkloadConnections.name(fields[1]);if(fields[2].length()>253)throw new IllegalArgumentException();result.add(new Resource(fields[1],fields[2]));
  }
  result.sort(Comparator.comparing(Resource::name));return new Listing(true,List.copyOf(result),"");
 }
 static Ingress ingress(JsonNode item){
  var spec=item.path("spec");var routes=new ArrayList<Route>();var tls=new ArrayList<Tls>();
  if(spec.has("defaultBackend"))routes.add(route("","/",spec.path("defaultBackend")));
  for(var rule:spec.path("rules"))for(var path:rule.path("http").path("paths"))routes.add(route(rule.path("host").asText(""),path.path("path").asText("/"),path.path("backend")));
  for(var value:spec.path("tls")){var hosts=new ArrayList<String>();for(var host:value.path("hosts"))hosts.add(host.asText());tls.add(new Tls(List.copyOf(hosts),value.path("secretName").asText("")));}
  return new Ingress(item.path("metadata").path("name").asText(),spec.path("ingressClassName").asText(item.path("metadata").path("annotations").path("kubernetes.io/ingress.class").asText("")),List.copyOf(routes),List.copyOf(tls),addresses(item));
 }
 private static Route route(String host,String path,JsonNode backend){var service=backend.path("service");var port=service.path("port");return new Route(host,path,service.path("name").asText(""),port.has("number")?port.path("number").asText():port.path("name").asText(""));}
 private static List<String> addresses(JsonNode item){var out=new ArrayList<String>();for(var a:item.path("status").path("loadBalancer").path("ingress")){String s=a.path("ip").asText(a.path("hostname").asText(""));if(!s.isBlank())out.add(s);}return List.copyOf(out);}
}
