package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Reads a registered target; never writes Kubernetes objects or reads Secret values. */
@Component
public class CurrentDeploymentInput {
 public record Result(OkeDeploymentSetup.Input input,List<String> warnings){}
 private final OperatingServices services;private final OkeRuntimeResources resources;
 private static final JsonMapper JSON=JsonMapper.builder().build();
 public CurrentDeploymentInput(OperatingServices services,OkeRuntimeResources resources){this.services=services;this.resources=resources;}
 public Result read(DeploymentOperations.Context c,String name){
  var entry=services.get(c,name);
  try{var i=entry.deployment();return convert(i,JSON.readTree(resources.deployment(c.connection(),c.region(),c.compartment(),i.environment(),i.endpoint(),i.namespace(),i.app())));}
  catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException("현재 Deployment 가져오기 실패: "+PipelineIamRules.safe(e.getMessage()));}
 }
 static Result convert(OkeDeploymentSetup.Input saved,JsonNode document){
  ImportedDeployment.extract(JSON.convertValue(document,Map.class));
  var warnings=new ArrayList<String>();var i=(ObjectNode)JSON.valueToTree(saved);
  var pod=document.path("spec").path("template").path("spec");var container=pod.path("containers").get(0);
  i.put("namespace",document.path("metadata").path("namespace").asText());i.put("app",document.path("metadata").path("name").asText());i.put("replicas",document.path("spec").path("replicas").asInt(1));i.put("createNamespace",false);
  var security=pod.path("securityContext");var cs=container.path("securityContext");
  String architecture=pod.path("nodeSelector").path("kubernetes.io/arch").asText("");if(Set.of("arm64","amd64").contains(architecture))i.put("architecture",architecture);else warnings.add("아키텍처 지정 없음: 저장된 입력을 유지합니다.");
  for(String key:security.propertyNames())if(!Set.of("runAsUser","runAsNonRoot","fsGroup","fsGroupChangePolicy","seccompProfile").contains(key))warnings.add("Pod securityContext."+key+"는 폼에서 표현하지 못합니다.");
  for(String key:cs.propertyNames())if(!Set.of("runAsNonRoot","readOnlyRootFilesystem","allowPrivilegeEscalation","capabilities").contains(key))warnings.add("컨테이너 securityContext."+key+"는 폼에서 표현하지 못합니다.");
  if(security.path("seccompProfile").path("type").asText().equals("Localhost"))warnings.add("Localhost seccomp는 폼에서 표현하지 못합니다. 프로파일을 직접 검토하세요.");
  i.put("podRunAsNonRoot",security.path("runAsNonRoot").asBoolean(false));i.put("containerRunAsNonRoot",cs.path("runAsNonRoot").asBoolean(false));i.put("readOnlyRootFilesystem",cs.path("readOnlyRootFilesystem").asBoolean(false));i.put("allowPrivilegeEscalation",cs.path("allowPrivilegeEscalation").asBoolean(true));
  i.put("enableServiceLinks",pod.path("enableServiceLinks").asBoolean(true));i.put("automountServiceAccountToken",pod.path("automountServiceAccountToken").asBoolean(true));
  if(security.has("runAsUser"))i.put("runAsUser",security.path("runAsUser").asLong());else warnings.add("runAsUser 없음: 안전 기본값 10001을 제안합니다.");
  if(security.has("fsGroup"))i.put("fsGroup",security.path("fsGroup").asLong());else i.putNull("fsGroup");
  if(security.path("seccompProfile").has("type"))i.put("seccompProfile",security.path("seccompProfile").path("type").asText());else warnings.add("seccompProfile 없음: RuntimeDefault를 제안합니다.");
  boolean drop=false;for(var cap:cs.path("capabilities").path("drop"))if(cap.asText().equals("ALL"))drop=true;i.put("dropAllCapabilities",drop);
  i.put("serviceAccount",pod.path("serviceAccountName").asText(""));i.put("terminationSeconds",pod.path("terminationGracePeriodSeconds").asInt(30));
  if(container.path("ports").size()==1)i.put("port",container.path("ports").get(0).path("containerPort").asInt());else warnings.add("복수 또는 미지정 컨테이너 포트: 저장된 포트를 유지합니다.");
  if(pod.path("imagePullSecrets").size()<=1)i.put("pullSecret",pod.path("imagePullSecrets").isEmpty()?"":pod.path("imagePullSecrets").get(0).path("name").asText());else warnings.add("복수 imagePullSecret은 폼에서 표현하지 못합니다.");
  var env=new ArrayList<String>();for(var e:container.path("env")){if(e.has("valueFrom"))warnings.add("개별 환경변수 참조는 폼에서 표현하지 못합니다: "+e.path("name").asText());else env.add(e.path("name").asText()+"="+e.path("value").asText());}i.put("variables",String.join("\n",env));
  i.put("configMap","");i.put("secret","");var kinds=new HashSet<String>();for(var e:container.path("envFrom")){String kind=e.has("configMapRef")?"configMap":"secret";if(!kinds.add(kind)||e.has("prefix"))warnings.add("복수 또는 접두사 envFrom은 폼에서 표현하지 못합니다.");else i.put(kind,e.path(kind.equals("configMap")?"configMapRef":"secretRef").path("name").asText());}
  var files=new ArrayList<String>();var dirs=new ArrayList<String>();i.put("claim","");i.put("mountPath","");
  for(var volume:pod.path("volumes")){
   JsonNode mount=null;for(var candidate:container.path("volumeMounts"))if(candidate.path("name").asText().equals(volume.path("name").asText())){if(mount!=null)warnings.add("한 볼륨의 복수 마운트는 폼에서 표현하지 못합니다.");mount=candidate;}
   if(mount==null){warnings.add("마운트되지 않은 볼륨은 가져오지 않습니다.");continue;}
   if(mount.has("subPath")||mount.has("subPathExpr"))warnings.add("subPath 마운트는 폼에서 표현하지 못합니다.");
   String path=mount.path("mountPath").asText();
   if(volume.has("secret")||volume.has("configMap")){String kind=volume.has("secret")?"secret":"configMap";var source=volume.path(kind);if(source.has("items")||source.path("optional").asBoolean(false))warnings.add("파일별 key/path/mode 또는 optional은 폼에서 표현하지 못합니다.");files.add((kind.equals("secret")?"secret":"configmap")+" "+source.path(kind.equals("secret")?"secretName":"name").asText()+" "+path+" "+mount.path("readOnly").asBoolean(false)+" "+String.format("%04o",source.path("defaultMode").asInt(420)));}
   else if(volume.has("emptyDir")){var source=volume.path("emptyDir");if(!source.path("medium").asText("").isBlank())warnings.add("emptyDir medium은 폼에서 표현하지 못합니다.");if(!source.has("sizeLimit"))warnings.add("emptyDir sizeLimit 없음: 64Mi를 제안합니다.");dirs.add(path+" "+source.path("sizeLimit").asText("64Mi"));}
   else if(volume.has("persistentVolumeClaim")){if(!i.path("claim").asText().isBlank())warnings.add("복수 PVC는 폼에서 표현하지 못합니다.");i.put("claim",volume.path("persistentVolumeClaim").path("claimName").asText());i.put("mountPath",path);}
  }if(!files.isEmpty()&&!security.has("fsGroup"))warnings.add("fsGroup 없음: 파일 볼륨 읽기를 위한 실행 UID의 그룹을 제안합니다.");i.put("fileVolumes",String.join("\n",files));i.put("emptyDirs",String.join("\n",dirs));
  String commonHeaders=null;Integer commonTimeout=null;
  for(String kind:List.of("readiness","liveness","startup")){
   var probe=container.path(kind+"Probe");String field=kind.equals("readiness")?"healthPath":kind+"Path";i.put(field,probe.path("httpGet").path("path").asText(""));i.put(kind+"InitialDelay",probe.path("initialDelaySeconds").asInt(0));i.put(kind+"Period",probe.path("periodSeconds").asInt(10));
   if(probe.isMissingNode())continue;
   var http=probe.path("httpGet");if(!http.path("scheme").asText("HTTP").equals("HTTP")||http.has("host")||http.path("port").asInt(-1)!=i.path("port").asInt())warnings.add("probe별 별도 포트·scheme·host는 폼에서 표현하지 못합니다.");
   if(probe.path("failureThreshold").asInt(3)!=(kind.equals("startup")?30:3)||probe.path("successThreshold").asInt(1)!=1)warnings.add("probe 실패·성공 횟수는 폼 기본값과 다릅니다.");
   if(!probe.has("httpGet"))warnings.add("HTTP 외 probe는 폼에서 표현하지 못합니다.");
   var headers=new ArrayList<String>();for(var h:probe.path("httpGet").path("httpHeaders"))headers.add(h.path("name").asText()+"="+h.path("value").asText());String current=String.join("\n",headers);int timeout=probe.path("timeoutSeconds").asInt(1);
   if(commonHeaders!=null&&(!commonHeaders.equals(current)||commonTimeout!=timeout))warnings.add("probe별 서로 다른 헤더·timeout은 폼에서 표현하지 못합니다.");else{commonHeaders=current;commonTimeout=timeout;}
  }i.put("probeHeaders",Objects.toString(commonHeaders,""));i.put("probeTimeout",commonTimeout==null?1:commonTimeout);
  var requests=container.path("resources").path("requests");var limits=container.path("resources").path("limits");
  if(!requests.equals(limits))warnings.add("서로 다른 requests/limits는 폼에서 표현하지 못합니다.");
  try{String cpu=requests.path("cpu").asText(),memory=requests.path("memory").asText();i.put("cpuMillis",cpu.endsWith("m")?Integer.parseInt(cpu.substring(0,cpu.length()-1)):new java.math.BigDecimal(cpu).multiply(new java.math.BigDecimal(1000)).intValueExact());i.put("memoryMi",memory.matches("[0-9]+Mi")?Integer.parseInt(memory.substring(0,memory.length()-2)):memory.matches("[0-9]+Gi")?Math.multiplyExact(Integer.parseInt(memory.substring(0,memory.length()-2)),1024):Integer.parseInt("unsupported"));}catch(Exception e){warnings.add("리소스 수량은 저장된 입력을 유지합니다. 확인하세요.");}
  for(String key:List.of("command","args","workingDir"))if(container.has(key))warnings.add(key+"는 폼에서 표현하지 못합니다.");
  warnings.add("이미지·Service 설정은 저장된 입력입니다. 현재 Deployment 가져오기는 전체 명세 복원이 아닙니다. 등록 서비스 재배포는 원본 명세를 보존합니다.");
  return new Result(JSON.treeToValue(i,OkeDeploymentSetup.Input.class),List.copyOf(new LinkedHashSet<>(warnings)));
 }
}
