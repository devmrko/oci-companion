package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.OkeDeploymentSetup.Input;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.yaml.snakeyaml.*;

/** Standard Kubernetes objects, usable without Companion. */
public final class OkeDeploymentManifest {
 private static boolean label(String s){return s!=null&&s.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");}
 public static String render(Input i,String image){
  if(!label(i.namespace())||Set.of("default","kube-system","kube-public","kube-node-lease").contains(i.namespace())||!label(i.app())||!label(i.name())||i.description()==null||i.description().length()>1024||!Set.of("amd64","arm64").contains(i.architecture())||i.replicas()<1||i.replicas()>100||i.cpuMillis()<1||i.cpuMillis()>128000||i.memoryMi()<16||i.memoryMi()>1048576||i.port()<1||i.port()>65535||i.healthPath()==null||(!i.healthPath().isEmpty()&&(!i.healthPath().startsWith("/")||i.healthPath().length()>1024||i.healthPath().contains("\n")||i.healthPath().contains("\r")))||i.variables()==null||i.variables().length()>65536||i.pullSecret()==null||(!i.pullSecret().isBlank()&&!label(i.pullSecret()))||i.claim()==null||i.mountPath()==null)throw Errors.DEPLOYMENT_INPUT.exception();
  if(!image.matches("[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}"))throw Errors.DEPLOYMENT_INPUT.exception();
  if(!i.claim().isBlank()&&(!label(i.claim())||i.replicas()!=1||!i.mountPath().matches("/[A-Za-z0-9_./-]+")||i.mountPath().contains("..")||i.mountPath().equals("/")))throw Errors.DEPLOYMENT_INPUT.exception();
  if(i.claim().isBlank()&&!i.mountPath().isBlank())throw Errors.DEPLOYMENT_INPUT.exception();
  if(i.fsGroup()!=null&&(i.claim().isBlank()||i.fsGroup()<0||i.fsGroup()>2147483647L))throw Errors.DEPLOYMENT_INPUT.exception();
  var labels=Map.of("app.kubernetes.io/name",i.app());var container=new LinkedHashMap<String,Object>();container.put("name",i.app());container.put("image",image);container.put("ports",List.of(Map.of("name","http","containerPort",i.port())));
  var resources=Map.of("cpu",i.cpuMillis()+"m","memory",i.memoryMi()+"Mi");container.put("resources",Map.of("requests",new LinkedHashMap<>(resources),"limits",new LinkedHashMap<>(resources)));
  var names=new HashSet<String>();var env=new ArrayList<Map<String,String>>();
  for(var line:i.variables().lines().filter(x->!x.isBlank()).toList()){int pos=line.indexOf('=');if(pos<1)throw Errors.DEPLOYMENT_INPUT.exception();String key=line.substring(0,pos).strip();if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")||!names.add(key)||names.size()>100)throw Errors.DEPLOYMENT_INPUT.exception();env.add(Map.of("name",key,"value",line.substring(pos+1)));}if(!env.isEmpty())container.put("env",env);
  if(!i.healthPath().isBlank())container.put("readinessProbe",Map.of("httpGet",Map.of("port",i.port(),"path",i.healthPath()),"initialDelaySeconds",5,"periodSeconds",10));
  var pod=new LinkedHashMap<String,Object>();pod.put("containers",List.of(container));pod.put("nodeSelector",Map.of("kubernetes.io/arch",i.architecture()));
  if(!i.pullSecret().isBlank())pod.put("imagePullSecrets",List.of(Map.of("name",i.pullSecret())));
  if(!i.claim().isBlank()){container.put("volumeMounts",List.of(Map.of("name","data","mountPath",i.mountPath())));pod.put("volumes",List.of(Map.of("name","data","persistentVolumeClaim",Map.of("claimName",i.claim()))));}
  if(i.fsGroup()!=null)pod.put("securityContext",Map.of("fsGroup",i.fsGroup(),"fsGroupChangePolicy","OnRootMismatch"));
  var spec=new LinkedHashMap<String,Object>();spec.put("replicas",i.replicas());spec.put("revisionHistoryLimit",5);spec.put("progressDeadlineSeconds",600);spec.put("selector",Map.of("matchLabels",new LinkedHashMap<>(labels)));spec.put("strategy",Map.of("type",i.claim().isBlank()?"RollingUpdate":"Recreate"));spec.put("template",Map.of("metadata",Map.of("labels",new LinkedHashMap<>(labels)),"spec",pod));
  var docs=List.of(Map.of("apiVersion","v1","kind","Namespace","metadata",Map.of("name",i.namespace())),Map.of("apiVersion","apps/v1","kind","Deployment","metadata",Map.of("name",i.app(),"namespace",i.namespace()),"spec",spec),Map.of("apiVersion","v1","kind","Service","metadata",Map.of("name",i.app(),"namespace",i.namespace()),"spec",Map.of("type","ClusterIP","selector",new LinkedHashMap<>(labels),"ports",List.of(Map.of("port",i.port(),"targetPort",i.port(),"name","http")))));
  var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);return new Yaml(options).dumpAll(docs.iterator());
 }
}
