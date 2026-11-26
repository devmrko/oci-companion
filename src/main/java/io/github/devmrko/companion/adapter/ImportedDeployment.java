package io.github.devmrko.companion.adapter;

import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;
import io.github.devmrko.companion.port.OkeRuntimeResources;
import io.github.devmrko.companion.service.DeploymentVolumes;

/** Import workload settings, never cluster objects, Secret values or allocated Service addresses. */
final class ImportedDeployment {
 private static final Set<String> CONTAINER=Set.of("env","envFrom","resources","ports","readinessProbe","livenessProbe","startupProbe","volumeMounts","securityContext","command","args","workingDir");
 private static final Set<String> POD=Set.of("volumes","securityContext","enableServiceLinks","automountServiceAccountToken","serviceAccountName","terminationGracePeriodSeconds");
 private static final JsonMapper JSON=JsonMapper.builder().build();
 static Map<String,Object> map(Object value){if(value==null)return Map.of();if(!(value instanceof Map<?,?>))throw new IllegalArgumentException("배포 설정 객체 형식 오류");return (Map<String,Object>)value;}
 static List<Map<String,Object>> list(Object value){if(value==null)return List.of();if(!(value instanceof List<?> items)||items.size()>100)throw new IllegalArgumentException("배포 설정 목록 한도 초과");return items.stream().map(ImportedDeployment::map).toList();}
 static String text(Object value){return value==null?"":value.toString();}
 static void environment(Map<String,Object> container){
  for(var env:list(container.get("env"))){String name=text(env.get("name"));if(!name.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("환경변수 이름 형식 오류");
   if(env.containsKey("value")){String value=text(env.get("value"));if(name.matches("(?i).*(password|passwd|token|secret|credential|key|fingerprint).*" )||value.contains("-----BEGIN")||value.contains("ocid1.")||value.length()>4096)throw new IllegalArgumentException("민감한 환경값은 Secret 참조를 사용하세요 · 값은 가져오지 않았습니다");}
  }
 }
 static void security(Object value){var s=map(value);if(Boolean.TRUE.equals(s.get("privileged"))||Boolean.TRUE.equals(s.get("allowPrivilegeEscalation"))||map(s.get("capabilities")).containsKey("add"))throw new IllegalArgumentException("권한 상승 설정은 자동 가져오기에서 허용하지 않습니다");}
 static Map<String,Object> selected(Map<String,Object> input,Set<String> fields){var result=new LinkedHashMap<String,Object>();for(String key:new TreeSet<>(fields))if(input.containsKey(key))result.put(key,input.get(key));return result;}
 static Map<String,Object> extract(Object document){
  var spec=map(map(map(document).get("spec")).get("template"));var pod=map(spec.get("spec"));var containers=list(pod.get("containers"));
  if(containers.size()!=1||!list(pod.get("initContainers")).isEmpty())throw new IllegalArgumentException("자동 가져오기는 앱 컨테이너 한 개를 지원합니다 · init/sidecar는 상세 설정을 사용하세요");
  for(String field:List.of("hostNetwork","hostPID","hostIPC"))if(Boolean.TRUE.equals(pod.get(field)))throw new IllegalArgumentException("호스트 접근 설정은 자동 가져오기에서 허용하지 않습니다");
  var container=selected(containers.getFirst(),CONTAINER);environment(container);security(container.get("securityContext"));security(pod.get("securityContext"));
  for(String probe:List.of("readinessProbe","livenessProbe","startupProbe")){
   var http=map(map(container.get(probe)).get("httpGet"));for(var header:list(http.get("httpHeaders")))DeploymentVolumes.headers(text(header.get("name"))+"="+text(header.get("value")));
  }
  for(var volume:list(pod.get("volumes"))){var types=new HashSet<>(volume.keySet());types.remove("name");if(types.size()!=1||Collections.disjoint(types,Set.of("secret","configMap","emptyDir","persistentVolumeClaim")))throw new IllegalArgumentException("자동 가져오기 볼륨은 Secret·ConfigMap·emptyDir·PVC만 지원합니다");}
  var imported=new LinkedHashMap<String,Object>();imported.put("container",container);imported.put("pod",selected(pod,POD));references(imported);return imported;
 }
 static Map<String,Set<String>> references(Map<String,Object> imported){
  var result=new LinkedHashMap<String,Set<String>>();var pod=map(imported.get("pod"));var container=map(imported.get("container"));
  for(var v:list(pod.get("volumes"))){reference(result,"secrets",map(v.get("secret")).get("secretName"));reference(result,"configmaps",map(v.get("configMap")).get("name"));reference(result,"persistentvolumeclaims",map(v.get("persistentVolumeClaim")).get("claimName"));}
  for(var e:list(container.get("env"))){var from=map(e.get("valueFrom"));reference(result,"secrets",map(from.get("secretKeyRef")).get("name"));reference(result,"configmaps",map(from.get("configMapKeyRef")).get("name"));}
  for(var e:list(container.get("envFrom"))){reference(result,"secrets",map(e.get("secretRef")).get("name"));reference(result,"configmaps",map(e.get("configMapRef")).get("name"));}
  reference(result,"serviceaccounts",pod.get("serviceAccountName"));return result;
 }
 private static void reference(Map<String,Set<String>> result,String kind,Object value){if(value==null)return;String name=text(value);OciOkeWorkloadConnections.name(name);result.computeIfAbsent(kind,k->new LinkedHashSet<>()).add(name);}
 static void requireReferences(Map<String,Object> imported,OkeRuntimeResources.Snapshot snapshot){
  for(var entry:references(imported).entrySet()){var listing=snapshot.resources().get(entry.getKey());if(listing==null||!listing.available())throw new IllegalArgumentException("참조 조회 불가: "+entry.getKey());for(String name:entry.getValue())if(listing.items().stream().noneMatch(i->i.name().equals(name)))throw new IllegalArgumentException("대상 Namespace에 참조 없음: "+entry.getKey()+"/"+name);}
 }
 static String overlay(String generated,Map<String,Object> imported){
  if(imported.isEmpty())return generated;
  var yaml=new Yaml(new SafeConstructor(new LoaderOptions()));var docs=new ArrayList<Object>();
  for(Object document:yaml.loadAll(generated)){var doc=map(document);if("Deployment".equals(doc.get("kind"))){var pod=map(map(map(doc.get("spec")).get("template")).get("spec"));var container=list(pod.get("containers")).getFirst();var copy=JSON.readValue(JSON.writeValueAsString(imported),Map.class);container.putAll(map(copy.get("container")));pod.putAll(map(copy.get("pod")));}docs.add(document);}
  var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);return new Yaml(options).dumpAll(docs.iterator());
 }
}
