package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeDeploymentSetup.Input;
import io.github.devmrko.companion.service.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

class OkeRuntimeInputsTest {
 @TempDir Path directory;
 static final String IMAGE="yny.ocir.io/ns/app@sha256:"+"a".repeat(64);
 Input input(Map<String,Object> overrides){
  var base=new Input("env","repo","image","sustena-verify","description","apps","sustena-verify","arm64",2,250,256,8080,"/ready","","ocir-pull","","",true);
  var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();var map=mapper.convertValue(base,Map.class);map.putAll(overrides);return mapper.convertValue(map,Input.class);
 }
 Map pod(Input i){var docs=new ArrayList<Map>();new Yaml().loadAll(OkeDeploymentManifest.render(i,IMAGE)).forEach(x->docs.add((Map)x));var deployment=docs.stream().filter(d->"Deployment".equals(d.get("kind"))).findFirst().orElseThrow();return (Map)((Map)((Map)deployment.get("spec")).get("template")).get("spec");}
 @Test void walletTmpSecurityAndHostProbeAreRenderedTogether(){
  var settings=new HashMap<String,Object>();settings.put("fileVolumes","secret wallet /opt/wallet true\nconfigmap settings /opt/settings true");settings.put("emptyDirs","/tmp");settings.put("podRunAsNonRoot",true);settings.put("containerRunAsNonRoot",true);settings.put("readOnlyRootFilesystem",true);settings.put("dropAllCapabilities",true);settings.put("automountServiceAccountToken",false);settings.put("probeHeaders","Host=sustena.example.com");settings.put("probeTimeout",5);settings.put("startupPath","/startup");settings.put("livenessPath","/live");settings.put("createNamespace",false);settings.put("fsGroup",1000);
  var i=input(settings);var pod=pod(i);assertEquals(false,pod.get("automountServiceAccountToken"));assertEquals(true,((Map)pod.get("securityContext")).get("runAsNonRoot"));assertEquals(3,((List)pod.get("volumes")).size());
  var container=(Map)((List)pod.get("containers")).getFirst();var security=(Map)container.get("securityContext");assertEquals(true,security.get("readOnlyRootFilesystem"));assertEquals(Map.of("drop",List.of("ALL")),security.get("capabilities"));
  for(String key:List.of("readinessProbe","startupProbe","livenessProbe")){var probe=(Map)container.get(key);assertEquals(5,probe.get("timeoutSeconds"));assertEquals(List.of(Map.of("name","Host","value","sustena.example.com")),((Map)probe.get("httpGet")).get("httpHeaders"));}
  assertFalse(OkeDeploymentManifest.render(i,IMAGE).contains("kind: Namespace"));
 }
 @Test void duplicateMountsAndUnsafeHeadersAreRejected(){
  assertThrows(RuntimeException.class,()->pod(input(Map.of("fileVolumes","secret wallet /tmp true","emptyDirs","/tmp"))));
  assertThrows(RuntimeException.class,()->pod(input(Map.of("fileVolumes","secret wallet /../private true"))));
  assertThrows(RuntimeException.class,()->pod(input(Map.of("probeHeaders","Authorization=secret"))));
  assertThrows(RuntimeException.class,()->pod(input(Map.of("probeTimeout",0))));
 }
 @Test void defaultNamespaceNeverProducesNamespaceObject(){assertFalse(OkeDeploymentManifest.render(input(Map.of("namespace","default","createNamespace",true)),IMAGE).contains("kind: Namespace"));}
 @Test void savedInputsSurviveRestartAndStayBoundToActorAndEnvironment(){
  var c=new Connection("profile","tenant","user","region","label",false);var i=input(Map.of("fileVolumes","secret wallet /opt/wallet true","emptyDirs","/tmp","probeHeaders","Host=app.example","automountServiceAccountToken",false));
  var store=new OkeDeploymentInputs(new JsonBuildSpecDraftStore(directory.toString()));store.save(c,"region","comp",i);
  var reloaded=new OkeDeploymentInputs(new JsonBuildSpecDraftStore(directory.toString()));assertEquals(i,reloaded.read(c,"region","comp","env").orElseThrow());assertTrue(reloaded.read(c,"region","other","env").isEmpty());assertTrue(reloaded.read(new Connection("profile","tenant","other","region","label",false),"region","comp","env").isEmpty());assertTrue(reloaded.read(c,"region","comp","other-env").isEmpty());
 }
 @Test void nodePortUsesSeparateServiceNameAndRequestedPort(){
  String yaml=OkeDeploymentManifest.render(input(Map.of("serviceType","NodePort","nodePort",31084,"serviceName","sustena-verify-nodeport")),IMAGE);var docs=new ArrayList<Map>();new Yaml().loadAll(yaml).forEach(x->docs.add((Map)x));var service=docs.stream().filter(d->"Service".equals(d.get("kind"))).findFirst().orElseThrow();assertEquals("sustena-verify-nodeport",((Map)service.get("metadata")).get("name"));assertEquals("NodePort",((Map)service.get("spec")).get("type"));assertTrue(yaml.contains("nodePort: 31084"));
 }
}
