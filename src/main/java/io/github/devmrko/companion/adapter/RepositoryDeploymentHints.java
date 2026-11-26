package io.github.devmrko.companion.adapter;

import java.util.*;
import java.util.regex.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads deployment hints as data only; never evaluates Helm, Docker or application expressions. */
public final class RepositoryDeploymentHints {
 public record Value(String value,String source){}
 public record Hints(Map<String,Value> values,List<String> notes,Map<String,Object> workload,String workloadSource){public Hints(Map<String,Value> values,List<String> notes){this(values,notes,Map.of(),"");}public Hints {values=Map.copyOf(values);notes=List.copyOf(notes);workload=workload==null?Map.of():Map.copyOf(workload);workloadSource=workloadSource==null?"":workloadSource;}}
 static String scalar(Object x){return x instanceof String||x instanceof Number?x.toString():"";}
 static Object at(Object root,String... path){Object current=root;for(String p:path){if(current instanceof Map<?,?> m)current=m.get(p);else if(current instanceof List<?> l&&p.equals("0")&&!l.isEmpty())current=l.getFirst();else return null;}return current;}
 static void put(Map<String,Value> values,String key,Object raw,String source){String value=scalar(raw);boolean valid=switch(key){case "port"->value.matches("[0-9]{1,5}")&&Integer.parseInt(value)>0&&Integer.parseInt(value)<=65535;case "replicas"->value.matches("[0-9]{1,3}")&&Integer.parseInt(value)>0&&Integer.parseInt(value)<=100;case "healthPath"->value.matches("/[A-Za-z0-9_./-]{0,200}")&&!value.contains("..");case "pullSecret"->value.matches("[a-z0-9][a-z0-9-]{0,62}");default->false;};if(valid)values.putIfAbsent(key,new Value(value,source));}
 public static Hints read(Map<String,String> files,String dockerfile){
  var result=new LinkedHashMap<String,Value>();var notes=new ArrayList<String>();var workloads=new LinkedHashMap<String,Map<String,Object>>();var options=new LoaderOptions();options.setCodePointLimit(262144);options.setMaxAliasesForCollections(10);options.setNestingDepthLimit(30);
  var yaml=new Yaml(new SafeConstructor(options));
  files.entrySet().stream().filter(e->e.getKey().matches("(?i).*(?:\\.yaml|\\.yml)$")&&!e.getKey().matches(".*(?:application|bootstrap)[^/]*\\.ya?ml$")).sorted(Map.Entry.comparingByKey()).forEach(e->{
   if(e.getValue().contains("{{")){notes.add(e.getKey()+": Helm 표현식은 실행하지 않습니다. 렌더된 값 또는 필수 입력을 확인하세요.");return;}
   try{for(Object doc:yaml.loadAll(e.getValue()))if("Deployment".equals(at(doc,"kind"))){String path=e.getKey();workloads.put(path+"#"+workloads.size(),ImportedDeployment.extract(doc));Object spec=at(doc,"spec","template","spec");
    put(result,"replicas",at(doc,"spec","replicas"),path+" · spec.replicas");put(result,"pullSecret",at(spec,"imagePullSecrets","0","name"),path+" · imagePullSecrets");
    Object container=at(spec,"containers","0");put(result,"port",at(container,"ports","0","containerPort"),path+" · containerPort");put(result,"healthPath",at(container,"readinessProbe","httpGet","path"),path+" · readinessProbe");
   }}catch(org.yaml.snakeyaml.error.YAMLException error){notes.add(e.getKey()+": YAML 해석 불가 · 필수값을 확인하세요.");}
  });
  String docker=files.getOrDefault(dockerfile,"").replaceAll("\\\\\\r?\\n"," ");
  // Only the final Docker stage describes the shipped container.
  var stages=Pattern.compile("(?im)^FROM\\s+").matcher(docker);int last=0;while(stages.find())last=stages.start();docker=docker.substring(last);
  var expose=Pattern.compile("(?im)^EXPOSE\\s+([0-9]{1,5})(?:/tcp)?(?:\\s|$)").matcher(docker);if(expose.find())put(result,"port",expose.group(1),dockerfile+" · EXPOSE");
  var check=Pattern.compile("(?im)^HEALTHCHECK\\s+([^\\r\\n]+)").matcher(docker);while(check.find()){var url=Pattern.compile("https?://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]{1,5})?(/[A-Za-z0-9_./-]*)").matcher(check.group(1));if(url.find())put(result,"healthPath",url.group(1),dockerfile+" · HEALTHCHECK");}
  for(var e:new TreeMap<>(files).entrySet())if(e.getKey().matches(".*(?:application|bootstrap)[^/]*\\.(?:properties|ya?ml)$")){
   if(e.getKey().endsWith(".properties")){var props=new Properties();try{props.load(new java.io.StringReader(e.getValue()));put(result,"port",props.getProperty("server.port"),e.getKey()+" · server.port");for(String k:List.of("health.path","readiness.path","management.endpoint.health.path"))put(result,"healthPath",props.getProperty(k),e.getKey()+" · "+k);}catch(Exception ignored){notes.add(e.getKey()+": 설정 해석 불가");}}
   else try{Object app=yaml.load(e.getValue());put(result,"port",at(app,"server","port"),e.getKey()+" · server.port");for(String k:List.of("health","readiness"))put(result,"healthPath",at(app,k,"path"),e.getKey()+" · "+k+".path");}catch(RuntimeException ignored){notes.add(e.getKey()+": 설정 해석 불가");}
  }
  if(workloads.size()>1)throw new IllegalArgumentException("Deployment 명세가 여러 개입니다 · 배포 대상을 하나로 정리하세요");return workloads.isEmpty()?new Hints(result,notes):new Hints(result,notes,workloads.values().iterator().next(),workloads.keySet().iterator().next().split("#")[0]);
 }
}
