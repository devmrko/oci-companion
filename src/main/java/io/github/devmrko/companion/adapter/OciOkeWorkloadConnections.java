package io.github.devmrko.companion.adapter;
import com.oracle.bmc.containerengine.model.CreateClusterKubeconfigContentDetails;
import com.oracle.bmc.containerengine.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeWorkloadConnections;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OciOkeWorkloadConnections implements OkeWorkloadConnections {
 private final io.github.devmrko.companion.port.OkePreparation cloud;
 private final OciBuildHistory clients;
 private final OkeWorkloadHealth reader;
 private final AgentConnectionRegistry registry;
 private final OkeConnectionStore store;
 private final Path root;
 private final String config,oci;
 private final JsonMapper json=JsonMapper.builder().build();
 public OciOkeWorkloadConnections(io.github.devmrko.companion.port.OkePreparation cloud,OkeWorkloadHealth reader,AgentConnectionRegistry registry,OkeConnectionStore store,
   @Value("${companion.data-dir:.data/catalog}") String root,@Value("${companion.oci-config}") String config,@Value("${companion.oci-cli:oci}") String oci){
  this.cloud=cloud;this.clients=new OciBuildHistory(config);this.reader=reader;this.registry=registry;this.store=store;this.root=Path.of(root).toAbsolutePath().resolve("oke-kubeconfigs");this.config=config;this.oci=oci;
 }
 public List<Cluster> clusters(Connection c,String r,String comp){if(c.demo())throw new IllegalArgumentException("실제 OCI 프로파일을 선택하세요.");return cloud.clusters(c,r,comp).stream().map(x->new Cluster(x.id(),x.name(),x.state())).toList();}
 static void name(String name){if(name==null||name.length()>253||!name.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?"))throw new IllegalArgumentException("namespace·Deployment 이름을 확인하세요.");}
 static void endpoint(String endpoint){if(!Set.of("PUBLIC_ENDPOINT","PRIVATE_ENDPOINT").contains(endpoint))throw new IllegalArgumentException("API 접속 경로를 선택하세요.");}
 OkeWorkloadHealth.Target prepare(Connection c,String r,String comp,String cluster,String endpoint,String ns,String deployment)throws Exception{
  if(c.demo())throw new IllegalArgumentException();endpoint(endpoint);name(ns);name(deployment);
  if(cluster==null||!cluster.startsWith("ocid1.cluster.")||!cluster.matches("[a-zA-Z0-9._-]{1,255}"))throw new IllegalArgumentException();
  byte[] content;
  try(var client=com.oracle.bmc.containerengine.ContainerEngineClient.builder().configuration(com.oracle.bmc.ClientConfiguration.builder().connectionTimeoutMillis(3000).readTimeoutMillis(10000).retryConfiguration(com.oracle.bmc.retrier.RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c))){
   client.setRegion(r);
   var actual=client.getCluster(GetClusterRequest.builder().clusterId(cluster).build()).getCluster();
   if(!comp.equals(actual.getCompartmentId()))throw new IllegalArgumentException("현재 구획의 클러스터를 선택하세요.");
   var details=CreateClusterKubeconfigContentDetails.builder().tokenVersion("2.0.0").endpoint(CreateClusterKubeconfigContentDetails.Endpoint.create(endpoint)).build();
   try(var input=client.createKubeconfig(CreateKubeconfigRequest.builder().clusterId(cluster).createClusterKubeconfigContentDetails(details).build()).getInputStream()){
    content=input.readNBytes(262145);if(content.length>262144)throw new IllegalArgumentException();
   }
  }
  String safe=safeConfig(content,c,r,cluster,config,oci);
  Files.createDirectories(root);if(Files.isSymbolicLink(root))throw new IllegalArgumentException();
  var path=Files.createTempFile(root,"oke-",".json",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  try{Files.writeString(path,safe);}catch(Exception e){Files.deleteIfExists(path);throw e;}
  return new OkeWorkloadHealth.Target("",c.profile(),c.tenancy(),c.user(),r,comp,cluster,path.toString(),ns,deployment,null,null,false,endpoint);
 }
 /** Accept only endpoint/CA data from the native response; never execute returned user commands. */
 static String safeConfig(byte[] content,Connection c,String r,String cluster,String config,String oci){
  var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setCodePointLimit(262144);options.setNestingDepthLimit(20);
  Object raw=new Yaml(new SafeConstructor(options)).load(new String(content,StandardCharsets.UTF_8));
  if(!(raw instanceof Map<?,?> doc)||!(doc.get("clusters") instanceof List<?> clusters)||clusters.size()!=1||!(clusters.getFirst() instanceof Map<?,?> item)||!(item.get("cluster") instanceof Map<?,?> info))throw new IllegalArgumentException();
  if(!(info.get("server") instanceof String server)||!(info.get("certificate-authority-data") instanceof String ca))throw new IllegalArgumentException();
  var uri=URI.create(server);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new IllegalArgumentException();
  if(ca.length()>131072||Base64.getDecoder().decode(ca).length==0)throw new IllegalArgumentException();
  var args=List.of("ce","cluster","generate-token","--cluster-id",cluster,"--region",r,"--profile",c.profile(),"--config-file",config,"--auth","api_key");
  var exec=Map.of("apiVersion","client.authentication.k8s.io/v1beta1","command",oci,"args",args,"interactiveMode","Never");
  return JsonMapper.builder().build().writeValueAsString(Map.of("apiVersion","v1","kind","Config","clusters",List.of(Map.of("name","cluster","cluster",Map.of("server",server,"certificate-authority-data",ca))),"users",List.of(Map.of("name","profile","user",Map.of("exec",exec))),"contexts",List.of(Map.of("name","selected","context",Map.of("cluster","cluster","user","profile"))),"current-context","selected"));
 }
 private void cleanup(OkeWorkloadHealth.Target t){if(t!=null)try{Files.deleteIfExists(Path.of(t.kubeconfig()));}catch(Exception ignored){}}
 private List<String> names(JsonNode list,String namespace){
  if(!list.path("items").isArray())throw new IllegalArgumentException();var result=new TreeSet<String>();
  for(var item:list.path("items")){if(!namespace.isEmpty()&&!namespace.equals(item.path("metadata").path("namespace").asText()))continue;String n=item.path("metadata").path("name").asText();name(n);result.add(n);if(result.size()>500)throw new IllegalArgumentException("조회 대상이 500개를 넘습니다. 범위를 줄여주세요.");}
  return List.copyOf(result);
 }
 public List<String> namespaces(Connection c,String r,String comp,String cluster,String endpoint){OkeWorkloadHealth.Target t=null;try{t=prepare(c,r,comp,cluster,endpoint,"default","unused");return names(reader.read(t,"namespaces",null),"");}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("namespace 조회 실패 · OCI 클러스터 접근, namespaces list RBAC, OCI CLI·kubectl 설치와 API 네트워크를 확인하세요. namespace를 알고 있으면 직접 입력할 수 있습니다.");}finally{cleanup(t);}}
 public List<String> deployments(Connection c,String r,String comp,String cluster,String endpoint,String namespace){OkeWorkloadHealth.Target t=null;try{name(namespace);t=prepare(c,r,comp,cluster,endpoint,namespace,"unused");return names(reader.read(t,"deployments",null),namespace);}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("Deployment 조회 실패 · namespace 이름, deployments list RBAC와 API 네트워크를 확인하세요.");}finally{cleanup(t);}}
 public Settings current(Connection c,String r,String comp,String id){if(id.isBlank())return new Settings("","","","","PUBLIC_ENDPOINT","","",false);var app=registry.get(c,r,comp,id);if(!"oke-workload-v1".equals(app.adapter()))throw new IllegalArgumentException("OKE 전용 연결을 선택하세요.");var t=store.get(c,r,comp,id);if(t==null)throw new IllegalArgumentException("저장된 OKE 조회 연결이 없습니다.");return new Settings(id,app.name(),app.environment(),t.clusterId(),Objects.requireNonNullElse(t.endpoint(),"PUBLIC_ENDPOINT"),t.namespace(),t.deployment(),t.inspectNodes());}
 public synchronized void remove(Connection c,String r,String comp,String id){
  try{
   if(c.demo()||!"oke-workload-v1".equals(registry.get(c,r,comp,id).adapter()))throw new IllegalArgumentException();
   var t=store.get(c,r,comp,id);
   if(t!=null){var path=Path.of(t.kubeconfig()).toAbsolutePath().normalize();
    if(!path.getParent().equals(root.normalize())||!path.getFileName().toString().matches("oke-[A-Za-z0-9_-]+\\.json")||Files.isSymbolicLink(root)||Files.isSymbolicLink(path))throw new IllegalArgumentException();
    Files.deleteIfExists(path);
   }
   store.remove(c,r,comp,id);registry.remove(c,r,comp,id);
  }catch(Exception e){throw new IllegalArgumentException("연결 해제를 완료하지 못했습니다. 로컬 파일 권한을 확인하고 다시 시도하세요. 클라우드 앱은 변경되지 않았습니다.");}
 }
 public synchronized String save(Connection c,String r,String comp,Settings s){
  OkeWorkloadHealth.Target t=null;String id=s.id();boolean created=false;AgentConnectionRegistry.Registration previous=null;OkeWorkloadHealth.Target oldTarget=null;
  try{
   if(s.name()==null||s.name().isBlank()||s.name().length()>100||s.environment()==null||s.environment().isBlank()||s.environment().length()>80)throw new IllegalArgumentException();
   if(!id.isBlank()){current(c,r,comp,id);previous=registry.get(c,r,comp,id);oldTarget=store.get(c,r,comp,id);}
   t=prepare(c,r,comp,s.cluster(),s.endpoint(),s.namespace(),s.deployment());
   var dep=reader.read(t,"deployment",s.deployment());
   if(!"Deployment".equals(dep.path("kind").asText())||!s.namespace().equals(dep.path("metadata").path("namespace").asText())||!s.deployment().equals(dep.path("metadata").path("name").asText())||dep.path("metadata").path("uid").asText().isBlank())throw new IllegalArgumentException();
   // Validate the same namespace reads the status screen will need before saving.
   reader.read(t,"replicasets",null);reader.read(t,"pods",null);
   id=registry.registerOke(c,r,comp,id,s.name(),s.environment(),new AgentConnectionRegistry.Dependency("OKE",s.name(),s.cluster()));created=s.id().isBlank();
   store.save(c,r,comp,id,new OkeWorkloadHealth.Target(id,c.profile(),c.tenancy(),c.user(),r,comp,t.clusterId(),t.kubeconfig(),t.namespace(),t.deployment(),null,null,s.inspectNodes(),s.endpoint()));
   t=null;if(oldTarget!=null){var oldPath=Path.of(oldTarget.kubeconfig()).toAbsolutePath().normalize();if(oldPath.getParent().equals(root.normalize())&&!Files.isSymbolicLink(root)&&!Files.isSymbolicLink(oldPath))cleanup(oldTarget);}return id;
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();if(created)registry.remove(c,r,comp,id);else if(previous!=null)registry.registerOke(c,r,comp,id,previous.name(),previous.environment(),previous.dependencies().getFirst());throw new IllegalArgumentException("연결 저장 실패 · 입력값, OCI 접근, deployments get·replicasets/pods list RBAC와 API 연결을 확인하세요.");}finally{cleanup(t);}
 }
}
