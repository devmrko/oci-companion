package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.OkeDeploymentSetup.Input;
import io.github.devmrko.companion.service.DeploymentVolumes;
import tools.jackson.databind.json.JsonMapper;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeploymentSecurityDefaultsTest {
 private final JsonMapper json=JsonMapper.builder().build();
 Input input(Map<String,Object> values){var i=new Input("env","repo","image","app","","apps","app","arm64",2,250,256,8845,"/readyz","","pull","","",true);var map=json.convertValue(i,Map.class);map.putAll(values);return json.convertValue(map,Input.class);}
 Map pod(Input i){for(Object value:new Yaml().loadAll(OkeDeploymentManifest.render(i,"region.ocir.io/ns/app@sha256:"+"a".repeat(64)))){var doc=(Map)value;if("Deployment".equals(doc.get("kind")))return (Map)((Map)((Map)doc.get("spec")).get("template")).get("spec");}throw new AssertionError();}
 @Test void sevenDefaultsReachManifestAndGroupCanReadSecretFiles(){
  var pod=pod(input(Map.of("fileVolumes","secret wallet /app/wallet true","emptyDirs","/tmp","livenessPath","/healthz")));var c=(Map)((List)pod.get("containers")).getFirst();var security=(Map)pod.get("securityContext");
  assertEquals(10001L,((Number)security.get("runAsUser")).longValue());assertEquals(10001L,((Number)security.get("fsGroup")).longValue());assertEquals("RuntimeDefault",((Map)security.get("seccompProfile")).get("type"));assertEquals(false,((Map)c.get("securityContext")).get("allowPrivilegeEscalation"));assertEquals(false,pod.get("enableServiceLinks"));
  var volumes=(List<Map>)pod.get("volumes");assertEquals(288,((Map)volumes.get(0).get("secret")).get("defaultMode"));assertEquals("64Mi",((Map)volumes.get(1).get("emptyDir")).get("sizeLimit"));assertEquals(10,((Map)c.get("livenessProbe")).get("initialDelaySeconds"));assertEquals(20,((Map)c.get("livenessProbe")).get("periodSeconds"));
 }
 @Test void explicitAdvancedValuesAndPerVolumeOverridesSurvive(){
  var i=input(Map.of("fileVolumes","configmap config /app/config true 0400","emptyDirs","/tmp 128Mi","runAsUser",2000,"fileDefaultMode","0640","enableServiceLinks",true,"readinessInitialDelay",13,"readinessPeriod",17));var pod=pod(i);var c=(Map)((List)pod.get("containers")).getFirst();assertEquals(true,pod.get("enableServiceLinks"));assertEquals(13,((Map)c.get("readinessProbe")).get("initialDelaySeconds"));assertEquals(17,((Map)c.get("readinessProbe")).get("periodSeconds"));var volumes=(List<Map>)pod.get("volumes");assertEquals(256,((Map)volumes.get(0).get("configMap")).get("defaultMode"));assertEquals("128Mi",((Map)volumes.get(1).get("emptyDir")).get("sizeLimit"));
 }
 @Test void invalidModesSizesAndPeriodsCannotReachManifest(){assertThrows(RuntimeException.class,()->pod(input(Map.of("fileDefaultMode","9999"))));assertThrows(RuntimeException.class,()->pod(input(Map.of("emptyDirSizeLimit","unbounded"))));assertThrows(RuntimeException.class,()->pod(input(Map.of("livenessPeriod",0))));assertThrows(RuntimeException.class,()->DeploymentVolumes.parse("secret wallet /app/wallet true 0999"));}
 @Test void advancedImportPreservesEnableServiceLinks(){var original=Map.of("spec",Map.of("template",Map.of("spec",Map.of("enableServiceLinks",false,"containers",List.of(Map.of("name","app"))))));assertEquals(false,((Map)ImportedDeployment.extract(original).get("pod")).get("enableServiceLinks"));}
}
