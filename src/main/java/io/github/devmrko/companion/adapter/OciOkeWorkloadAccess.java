package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.oracle.bmc.containerengine.requests.GetClusterRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@Component
public class OciOkeWorkloadAccess implements OkeWorkloadAccess {
 static final String IMAGE="ghcr.io/oracle/oci-cli@sha256:9ecc89fffd09be2c9e1039132112f18b2ca1c9bf09bf01744070c1c42f6d848b";
 private final OkeRuntimeResources inventory;private final OciOkePreparation cloud;private final ReportStorage storage;private final OciOkeWorkloadConnections connections;private final OkeWorkloadHealth reader;
 private final JsonMapper json=JsonMapper.builder().build();
 public OciOkeWorkloadAccess(OkeRuntimeResources inventory,OciOkePreparation cloud,ReportStorage storage,OciOkeWorkloadConnections connections,OkeWorkloadHealth reader){this.inventory=inventory;this.cloud=cloud;this.storage=storage;this.connections=connections;this.reader=reader;}
 public Options options(Connection c,String r,String comp,String environment,String endpoint,String namespace){
  try{label(namespace);var s=inventory.read(c,r,comp,environment,endpoint,namespace);var a=s.resources().get("serviceaccounts");if(a==null||!a.available())throw Errors.CLOUD.exception();
   try(var client=cloud.engine(c,r)){var cluster=client.getCluster(GetClusterRequest.builder().clusterId(s.cluster()).build()).getCluster();if(!comp.equals(cluster.getCompartmentId()))throw Errors.SCOPE.exception();var b=storage.options(c,r,comp);return new Options(cluster.getId(),cluster.getType()==null?"UNKNOWN":cluster.getType().getValue(),b.namespace(),a.items().stream().map(OkeRuntimeResources.Resource::name).toList(),b.buckets());}
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public Plan prepare(Connection c,String r,String comp,Input i){return prepare(c,r,comp,i,"companion-access-"+UUID.randomUUID().toString());}
 private Plan prepare(Connection c,String r,String comp,Input i,String job){
  label(i.namespace());label(i.serviceAccount());if(!i.bucket().matches("[A-Za-z0-9_-]{1,256}")||!job.matches("companion-access-[a-f0-9-]{36}"))throw Errors.WORKLOAD_ACCESS_INPUT.exception();
  var o=options(c,r,comp,i.environment(),i.endpoint(),i.namespace());if(!o.clusterType().equals("ENHANCED_CLUSTER")||!o.accounts().contains(i.serviceAccount())||!o.buckets().contains(i.bucket()))throw Errors.WORKLOAD_ACCESS_INPUT.exception();
  String owner=owner(c,r,comp);return new Plan(i,o.cluster(),policy(comp,o.cluster(),i),manifest(i,o.objectNamespace(),r,job,owner),job,owner);
 }
 static void label(String s){if(s==null||!s.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))throw Errors.WORKLOAD_ACCESS_INPUT.exception();}
 static String policy(String comp,String cluster,Input i){
  label(i.namespace());label(i.serviceAccount());if(!comp.matches("ocid1\\.compartment\\.[a-zA-Z0-9.-]+")||!cluster.matches("ocid1\\.cluster\\.[a-zA-Z0-9.-]+")||!i.bucket().matches("[A-Za-z0-9_-]{1,256}"))throw Errors.WORKLOAD_ACCESS_INPUT.exception();
  return "Allow any-user to inspect objects in compartment id "+comp+" where all {request.principal.type='workload', request.principal.cluster_id='"+cluster+"', request.principal.namespace='"+i.namespace()+"', request.principal.service_account='"+i.serviceAccount()+"', target.bucket.name='"+i.bucket()+"'}";
 }
 String manifest(Input i,String ns,String region,String job,String owner){
  try(var in=getClass().getResourceAsStream("/probes/oke-workload-access.py")){
   if(in==null)throw new IllegalStateException();String code=new String(in.readAllBytes(),StandardCharsets.UTF_8);
   var labels=Map.of("app.kubernetes.io/managed-by","oci-companion","companion-access-owner",owner);
   var container=Map.of("name","probe","image",IMAGE,"command",List.of("python3","-c",code),"env",List.of(Map.of("name","OCI_RESOURCE_PRINCIPAL_REGION","value",region),Map.of("name","OBJECT_NAMESPACE","value",ns),Map.of("name","BUCKET","value",i.bucket())),"resources",Map.of("requests",Map.of("cpu","100m","memory","128Mi"),"limits",Map.of("cpu","500m","memory","256Mi")),"securityContext",Map.of("allowPrivilegeEscalation",false,"capabilities",Map.of("drop",List.of("ALL"))));
   return json.writeValueAsString(Map.of("apiVersion","batch/v1","kind","Job","metadata",Map.of("name",job,"namespace",i.namespace(),"labels",labels),"spec",Map.of("backoffLimit",0,"activeDeadlineSeconds",120,"ttlSecondsAfterFinished",3600,"template",Map.of("metadata",Map.of("labels",labels),"spec",Map.of("serviceAccountName",i.serviceAccount(),"automountServiceAccountToken",true,"restartPolicy","Never","containers",List.of(container))))));
  }catch(Exception e){throw Errors.CLOUD.exception();}
 }
 static String owner(Connection c,String r,String comp){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((c.toString()+"|"+r+"|"+comp).getBytes(StandardCharsets.UTF_8))).substring(0,48);}catch(Exception e){throw new IllegalStateException(e);}}
 private void scope(Connection c,String r,String comp,Plan p){if(!owner(c,r,comp).equals(p.owner())||!p.job().matches("companion-access-[a-f0-9-]{36}"))throw Errors.SCOPE.exception();label(p.input().namespace());}
 private OkeWorkloadHealth.Target target(Connection c,String r,String comp,Plan p)throws Exception{return connections.prepare(c,r,comp,p.cluster(),p.input().endpoint(),p.input().namespace(),"unused");}
 private void cleanup(OkeWorkloadHealth.Target t){if(t!=null)try{Files.deleteIfExists(Path.of(t.kubeconfig()));}catch(Exception ignored){}}
 public String execute(Connection c,String r,String comp,Plan p){scope(c,r,comp,p);var fresh=prepare(c,r,comp,p.input(),p.job());if(!fresh.equals(p))throw Errors.BUILD_CHANGED.exception();OkeWorkloadHealth.Target t=null;Path file=null;
  try{t=target(c,r,comp,p);file=Files.createTempFile("companion-access-",".json");Files.writeString(file,fresh.manifest());reader.command(t,List.of("create","-f",file.toString()),32768);return p.job();}
  catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}finally{cleanup(t);if(file!=null)try{Files.deleteIfExists(file);}catch(Exception ignored){}}
 }
 private JsonNode job(OkeWorkloadHealth.Target t,Plan p)throws Exception{var j=reader.read(t,"job",p.job());if(!p.job().equals(j.path("metadata").path("name").asText())||j.path("metadata").path("uid").asText().isBlank()||!p.owner().equals(j.path("metadata").path("labels").path("companion-access-owner").asText())||!p.input().namespace().equals(j.path("metadata").path("namespace").asText()))throw Errors.SCOPE.exception();return j;}
 public Result result(Connection c,String r,String comp,Plan p){scope(c,r,comp,p);OkeWorkloadHealth.Target t=null;
  try{t=target(c,r,comp,p);var j=job(t,p);String uid=j.path("metadata").path("uid").asText();var pods=json.readTree(reader.command(t,List.of("get","pods","-l","job-name="+p.job(),"-o","json"),2000000));
   for(var pod:pods.path("items")){boolean owned=false;for(var ref:pod.path("metadata").path("ownerReferences"))if(ref.path("kind").asText().equals("Job")&&ref.path("uid").asText().equals(uid))owned=true;if(!owned)continue;
    if(!pod.path("status").path("phase").asText().equals("Succeeded"))continue;
    String output=new String(reader.command(t,List.of("logs",pod.path("metadata").path("name").asText(),"-c","probe","--tail=1","--limit-bytes=4096"),4096),StandardCharsets.UTF_8);return decode(output);
   }
   boolean failed=j.path("status").path("failed").asInt()>0;for(var cond:j.path("status").path("conditions"))if(cond.path("type").asText().equals("Failed")&&cond.path("status").asText().equals("True"))failed=true;
   return new Result(Instant.now(),failed?"JOB_FAILED":"PENDING",0,failed?"검증 Pod 실패 · 이미지·SDK·네트워크·Job 이벤트 확인":"검증 Pod 실행 중 · 다시 조회하세요.");
  }catch(BusinessException e){throw e;}catch(Exception e){return new Result(Instant.now(),"UNKNOWN",0,"조회 실패 · Job 이름·만료·Kubernetes get/log 권한 확인");}finally{cleanup(t);}
 }
 Result decode(String output){try{var n=json.readTree(output);String state=n.path("state").asText();int status=n.path("httpStatus").asInt();if(n.path("schema").asInt()!=1||!n.path("operation").asText().equals("ListObjects")||!Set.of("GRANTED","DENIED_OR_NOT_FOUND","API_FAILURE","AUTH_FAILURE","CONNECTION_FAILURE").contains(state)||status<0||status>599||!n.path("httpStatus").isIntegralNumber()||!switch(state){case "GRANTED"->status==200;case "DENIED_OR_NOT_FOUND"->Set.of(401,403,404).contains(status);case "API_FAILURE"->status>=400&&!Set.of(401,403,404).contains(status);default->status==0;})throw new IllegalArgumentException();String note=switch(state){case "GRANTED"->"Pod의 Workload Identity로 ListObjects 성공 · 다른 작업 권한은 별도";case "DENIED_OR_NOT_FOUND"->"권한 부족 또는 대상 없음 · 표시된 정책·대상·전파 시간을 확인하세요.";case "AUTH_FAILURE"->"Workload Identity 인증 실패 · Enhanced 클러스터·ServiceAccount·SDK·네트워크 확인";default->"OCI API·네트워크 호출 실패 · 다시 확인하세요.";};return new Result(Instant.now(),state,status,note);}catch(Exception e){return new Result(Instant.now(),"UNKNOWN",0,"결과 형식 확인 실패 · 원문 로그는 표시하지 않습니다.");}}
 public void remove(Connection c,String r,String comp,Plan p){scope(c,r,comp,p);OkeWorkloadHealth.Target t=null;try{t=target(c,r,comp,p);job(t,p);reader.command(t,List.of("delete","job",p.job(),"--wait=false"),32768);}catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}finally{cleanup(t);}}
}
