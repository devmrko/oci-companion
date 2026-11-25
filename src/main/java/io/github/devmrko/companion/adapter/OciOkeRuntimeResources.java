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
    for(String kind:List.of("configmaps","secrets","serviceaccounts"))work.put(kind,executor.submit(()->metadata(t,kind)));
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
  if(!Set.of("configmaps","secrets","serviceaccounts").contains(kind))throw new IllegalArgumentException();
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
