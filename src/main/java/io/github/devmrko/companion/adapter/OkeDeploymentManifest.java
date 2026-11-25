package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.OkeDeploymentSetup.Input;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import io.github.devmrko.companion.service.DeploymentVolumes;
import org.yaml.snakeyaml.*;

/** Standard Kubernetes objects, usable without Companion. */
public final class OkeDeploymentManifest {
 private static boolean label(String s){return s!=null&&s.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");}
 public static String render(Input i,String image){
  if(!label(i.namespace())||Set.of("kube-system","kube-public","kube-node-lease").contains(i.namespace())||!label(i.app())||!label(i.name())||i.description()==null||i.description().length()>1024||!Set.of("amd64","arm64").contains(i.architecture())||i.replicas()<1||i.replicas()>100||i.cpuMillis()<1||i.cpuMillis()>128000||i.memoryMi()<16||i.memoryMi()>1048576||i.port()<1||i.port()>65535||i.healthPath()==null||(!i.healthPath().isEmpty()&&(!i.healthPath().startsWith("/")||i.healthPath().length()>1024||i.healthPath().contains("\n")||i.healthPath().contains("\r")))||i.variables()==null||i.variables().length()>65536||i.pullSecret()==null||(!i.pullSecret().isBlank()&&!label(i.pullSecret()))||i.claim()==null||i.mountPath()==null)throw Errors.DEPLOYMENT_INPUT.exception();
  if(!image.matches("[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}"))throw Errors.DEPLOYMENT_INPUT.exception();
  if(!i.claim().isBlank()&&(!label(i.claim())||i.replicas()!=1||!i.mountPath().matches("/[A-Za-z0-9_./-]+")||i.mountPath().contains("..")||i.mountPath().equals("/")))throw Errors.DEPLOYMENT_INPUT.exception();
  if(i.claim().isBlank()&&!i.mountPath().isBlank())throw Errors.DEPLOYMENT_INPUT.exception();
  if(i.fsGroup()!=null&&((i.claim().isBlank()&&i.fileVolumes().isBlank()&&i.emptyDirs().isBlank())||i.fsGroup()<0||i.fsGroup()>2147483647L))throw Errors.DEPLOYMENT_INPUT.exception();
  for(String ref:List.of(i.configMap(),i.secret(),i.serviceAccount()))if(!ref.isBlank()&&!label(ref))throw Errors.DEPLOYMENT_INPUT.exception();
  for(String path:List.of(i.startupPath(),i.livenessPath()))if(!path.isBlank()&&(!path.startsWith("/")||path.length()>1024||path.chars().anyMatch(Character::isISOControl)))throw Errors.DEPLOYMENT_INPUT.exception();
  if(i.terminationSeconds()<1||i.terminationSeconds()>600)throw Errors.DEPLOYMENT_INPUT.exception();
  var fileVolumes=DeploymentVolumes.parse(i.fileVolumes());var emptyDirs=DeploymentVolumes.emptyDirs(i.emptyDirs());var headers=DeploymentVolumes.headers(i.probeHeaders());
  if(i.probeTimeout()<1||i.probeTimeout()>60)throw Errors.DEPLOYMENT_INPUT.exception();
  var labels=Map.of("app.kubernetes.io/name",i.app());var container=new LinkedHashMap<String,Object>();container.put("name",i.app());container.put("image",image);container.put("ports",List.of(Map.of("name","http","containerPort",i.port())));
  var resources=Map.of("cpu",i.cpuMillis()+"m","memory",i.memoryMi()+"Mi");container.put("resources",Map.of("requests",new LinkedHashMap<>(resources),"limits",new LinkedHashMap<>(resources)));
  var names=new HashSet<String>();var env=new ArrayList<Map<String,String>>();
  for(var line:i.variables().lines().filter(x->!x.isBlank()).toList()){int pos=line.indexOf('=');if(pos<1)throw Errors.DEPLOYMENT_INPUT.exception();String key=line.substring(0,pos).strip();if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")||!names.add(key)||names.size()>100)throw Errors.DEPLOYMENT_INPUT.exception();env.add(Map.of("name",key,"value",line.substring(pos+1)));}if(!env.isEmpty())container.put("env",env);
  if(!i.healthPath().isBlank())container.put("readinessProbe",Map.of("httpGet",Map.of("port",i.port(),"path",i.healthPath()),"initialDelaySeconds",5,"periodSeconds",10));
  var envFrom=new ArrayList<Map<String,Object>>();
  if(!i.configMap().isBlank())envFrom.add(Map.of("configMapRef",Map.of("name",i.configMap(),"optional",false)));
  if(!i.secret().isBlank())envFrom.add(Map.of("secretRef",Map.of("name",i.secret(),"optional",false)));
  if(!envFrom.isEmpty())container.put("envFrom",envFrom);
  if(!i.startupPath().isBlank())container.put("startupProbe",Map.of("httpGet",Map.of("port",i.port(),"path",i.startupPath()),"periodSeconds",10,"failureThreshold",30));
  if(!i.livenessPath().isBlank())container.put("livenessProbe",Map.of("httpGet",Map.of("port",i.port(),"path",i.livenessPath()),"periodSeconds",10,"failureThreshold",3));
  for(String key:List.of("readinessProbe","startupProbe","livenessProbe"))if(container.containsKey(key)){
   var original=(Map<String,Object>)container.get(key);var probe=new LinkedHashMap<>(original);var http=new LinkedHashMap<>((Map<String,Object>)probe.get("httpGet"));if(!headers.isEmpty())http.put("httpHeaders",headers.stream().map(LinkedHashMap::new).toList());probe.put("httpGet",http);probe.put("timeoutSeconds",i.probeTimeout());container.put(key,probe);
  }
  var containerSecurity=new LinkedHashMap<String,Object>();if(i.containerRunAsNonRoot()!=null)containerSecurity.put("runAsNonRoot",i.containerRunAsNonRoot());if(i.readOnlyRootFilesystem()!=null)containerSecurity.put("readOnlyRootFilesystem",i.readOnlyRootFilesystem());if(Boolean.TRUE.equals(i.dropAllCapabilities()))containerSecurity.put("capabilities",Map.of("drop",List.of("ALL")));if(!containerSecurity.isEmpty())container.put("securityContext",containerSecurity);
  var pod=new LinkedHashMap<String,Object>();pod.put("containers",List.of(container));pod.put("nodeSelector",Map.of("kubernetes.io/arch",i.architecture()));
  pod.put("terminationGracePeriodSeconds",i.terminationSeconds());
  if(!i.serviceAccount().isBlank())pod.put("serviceAccountName",i.serviceAccount());
  if(!i.pullSecret().isBlank())pod.put("imagePullSecrets",List.of(Map.of("name",i.pullSecret())));
  var volumes=new ArrayList<Map<String,Object>>();var mounts=new ArrayList<Map<String,Object>>();var paths=new HashSet<String>();
  if(!i.claim().isBlank()){mounts.add(Map.of("name","data","mountPath",i.mountPath()));volumes.add(Map.of("name","data","persistentVolumeClaim",Map.of("claimName",i.claim())));paths.add(i.mountPath());}
  int index=0;for(var v:fileVolumes){if(!paths.add(v.path()))throw Errors.DEPLOYMENT_INPUT.exception();String name="file-"+index++;mounts.add(Map.of("name",name,"mountPath",v.path(),"readOnly",v.readOnly()));volumes.add(Map.of("name",name,v.kind().equals("secret")?"secret":"configMap",Map.of(v.kind().equals("secret")?"secretName":"name",v.name())));}
  index=0;for(var path:emptyDirs){if(!paths.add(path))throw Errors.DEPLOYMENT_INPUT.exception();String name="scratch-"+index++;mounts.add(Map.of("name",name,"mountPath",path));volumes.add(Map.of("name",name,"emptyDir",Map.of()));}
  if(!volumes.isEmpty()){container.put("volumeMounts",mounts);pod.put("volumes",volumes);}
  var podSecurity=new LinkedHashMap<String,Object>();if(i.fsGroup()!=null){podSecurity.put("fsGroup",i.fsGroup());podSecurity.put("fsGroupChangePolicy","OnRootMismatch");}if(i.podRunAsNonRoot()!=null)podSecurity.put("runAsNonRoot",i.podRunAsNonRoot());if(!podSecurity.isEmpty())pod.put("securityContext",podSecurity);
  if(i.automountServiceAccountToken()!=null)pod.put("automountServiceAccountToken",i.automountServiceAccountToken());
  var spec=new LinkedHashMap<String,Object>();spec.put("replicas",i.replicas());spec.put("revisionHistoryLimit",5);spec.put("progressDeadlineSeconds",600);spec.put("selector",Map.of("matchLabels",new LinkedHashMap<>(labels)));spec.put("strategy",Map.of("type",i.claim().isBlank()?"RollingUpdate":"Recreate"));spec.put("template",Map.of("metadata",Map.of("labels",new LinkedHashMap<>(labels)),"spec",pod));
  if(!Set.of("ClusterIP","NodePort").contains(i.serviceType())||!label(i.serviceName())||(i.serviceType().equals("NodePort")&&(i.nodePort()<30000||i.nodePort()>32767)))throw Errors.DEPLOYMENT_INPUT.exception();
  var servicePort=new LinkedHashMap<String,Object>();servicePort.put("port",i.port());servicePort.put("targetPort",i.port());servicePort.put("name","http");if(i.serviceType().equals("NodePort"))servicePort.put("nodePort",i.nodePort());
  var docs=new ArrayList<Map<String,Object>>();if(i.createNamespace()&&!i.namespace().equals("default"))docs.add(Map.of("apiVersion","v1","kind","Namespace","metadata",Map.of("name",i.namespace())));docs.add(Map.of("apiVersion","apps/v1","kind","Deployment","metadata",Map.of("name",i.app(),"namespace",i.namespace()),"spec",spec));docs.add(Map.of("apiVersion","v1","kind","Service","metadata",Map.of("name",i.serviceName(),"namespace",i.namespace()),"spec",Map.of("type",i.serviceType(),"selector",new LinkedHashMap<>(labels),"ports",List.of(servicePort))));
  var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);return new Yaml(options).dumpAll(docs.iterator());
 }
}
