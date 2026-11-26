package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.*;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

@Component
public class NodePortServices {
 public record Input(String environment,String endpoint,String namespace,String deployment,String service,int port,int targetPort,int nodePort){}
 public record Plan(Input input,String manifest,String deploymentUid,Readiness.Check check){}
 private final OciBuildHistory clients;private final OciOkeWorkloadConnections connections;private final OkeWorkloadHealth reader;
 private final JsonMapper json=JsonMapper.builder().build();
 public NodePortServices(OciBuildHistory clients,OciOkeWorkloadConnections connections,OkeWorkloadHealth reader){this.clients=clients;this.connections=connections;this.reader=reader;}
 static void validate(Input i){for(String name:List.of(i.namespace(),i.deployment(),i.service()))OciOkeWorkloadConnections.name(name);OciOkeWorkloadConnections.endpoint(i.endpoint());if(i.nodePort()<30000||i.nodePort()>32767||i.port()<1||i.port()>65535||i.targetPort()<1||i.targetPort()>65535)throw Errors.INVALID.exception();}
 static String manifest(Input i,JsonNode deployment,JsonNode services){
  validate(i);if(!deployment.path("metadata").path("name").asText().equals(i.deployment())||!deployment.path("metadata").path("namespace").asText().equals(i.namespace())||deployment.path("metadata").has("deletionTimestamp"))throw Errors.SCOPE.exception();
  if(!services.path("items").isArray())throw Errors.DEPLOYMENT_RUNTIME.exception();
  for(var service:services.path("items")){
   if(service.path("metadata").path("namespace").asText().equals(i.namespace())&&service.path("metadata").path("name").asText().equals(i.service()))throw new IllegalArgumentException("같은 Service 이름이 있습니다. 새 이름을 지정하세요.");
   for(var port:service.path("spec").path("ports"))if(port.path("nodePort").asInt()==i.nodePort())throw new IllegalArgumentException("이미 사용 중인 NodePort입니다. 다른 포트를 지정하세요.");
  }
  var selector=deployment.path("spec").path("selector");if(!selector.path("matchLabels").isObject()||selector.path("matchLabels").isEmpty()||!selector.path("matchExpressions").isEmpty())throw new IllegalArgumentException("Service 선택자로 표현할 수 없는 Deployment입니다.");
  return JsonMapper.builder().build().writeValueAsString(Map.of("apiVersion","v1","kind","Service","metadata",Map.of("name",i.service(),"namespace",i.namespace(),"labels",Map.of("app.kubernetes.io/managed-by","oci-companion")),"spec",Map.of("type","NodePort","selector",selector.path("matchLabels"),"ports",List.of(Map.of("protocol","TCP","port",i.port(),"targetPort",i.targetPort(),"nodePort",i.nodePort())))));
 }
 private JsonNode submit(OkeWorkloadHealth.Target target,Object content,String raw)throws Exception{
  Path file=Files.createTempFile("companion-nodeport-",".json");try{Files.writeString(file,json.writeValueAsString(content));var args=new ArrayList<>(List.of("create"));if(raw!=null)args.addAll(List.of("--raw",raw));args.addAll(List.of("-f",file.toString()));if(raw==null)args.addAll(List.of("-o","json"));return json.readTree(reader.command(target,args,524288));}finally{Files.deleteIfExists(file);}
 }
 private Plan preview(OkeWorkloadHealth.Target target,Input i)throws Exception{
  var deployment=reader.read(target,"deployment",i.deployment());var services=json.readTree(reader.command(target,List.of("get","services","--all-namespaces","-o","json"),2_000_000));
  String manifest=manifest(i,deployment,services);
  var review=submit(target,Map.of("apiVersion","authorization.k8s.io/v1","kind","SelfSubjectAccessReview","spec",Map.of("resourceAttributes",Map.of("namespace",i.namespace(),"verb","create","resource","services"))),"/apis/authorization.k8s.io/v1/selfsubjectaccessreviews");
  var status=review.path("status");var permission=status.path("allowed").asBoolean(false)?Readiness.Permission.CONFIRMED:status.path("denied").asBoolean(false)||status.path("evaluationError").asText("").isBlank()&&status.has("allowed")?Readiness.Permission.DENIED:Readiness.Permission.UNKNOWN;
  var check=new Readiness.Check("nodeport","NodePort Service",Readiness.State.FAIL,permission,"포트 충돌 없음 · Kubernetes create services 권한 "+(permission==Readiness.Permission.CONFIRMED?"확인":permission==Readiness.Permission.DENIED?"거부":"미확인"),"새 Service 한 개를 생성합니다.","관리자에게 대상 Namespace의 services create 권한과 클러스터 전체 services list 권한을 요청하세요.","","");
  return new Plan(i,manifest,deployment.path("metadata").path("uid").asText(),check);
 }
 public Plan preview(Connection c,String region,String compartment,Input i){validate(i);OkeWorkloadHealth.Target target=null;try{target=OkeEnvironmentTarget.prepare(clients,connections,c,region,compartment,i.environment(),i.endpoint(),i.namespace());return preview(target,i);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("NodePort 점검 실패: "+PipelineIamRules.safe(e.getMessage()));}finally{cleanup(target);}}
 public void create(Connection c,String region,String compartment,Plan plan){OkeWorkloadHealth.Target target=null;try{var i=plan.input();target=OkeEnvironmentTarget.prepare(clients,connections,c,region,compartment,i.environment(),i.endpoint(),i.namespace());var current=preview(target,i);if(!current.equals(plan)||current.check().permission()!=Readiness.Permission.CONFIRMED)throw Errors.BUILD_CHANGED.exception();var created=submit(target,json.readTree(plan.manifest()),null);if(!i.service().equals(created.path("metadata").path("name").asText()))throw new IllegalStateException("생성 결과를 확인하지 못했습니다. Service를 다시 조회하세요.");}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Service 생성 실패: "+PipelineIamRules.safe(e.getMessage()));}finally{cleanup(target);}}
 private void cleanup(OkeWorkloadHealth.Target target){if(target!=null)try{Files.deleteIfExists(Path.of(target.kubeconfig()));}catch(Exception ignored){}}
}
