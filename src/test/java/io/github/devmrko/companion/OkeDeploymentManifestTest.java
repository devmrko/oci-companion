package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.OkeDeploymentSetup.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
class OkeDeploymentManifestTest {
 Input input(String namespace,String vars,String claim,String mount,int replicas){return new Input("env","repo","image","demo","description",namespace,"hello","amd64",replicas,250,256,8080,"/hello",vars,"pull-secret",claim,mount,true);}
 @Test void manifestPreservesNativeResourceValuesAndQuotedEnvironment(){
  String text=OkeDeploymentManifest.render(input("companion-demo","FLAG=false\nPORT=8080\nWORDS=a: b","","",2),"icn.ocir.io/ns/image@sha256:"+"a".repeat(64));
  assertEquals(List.of("icn.ocir.io/ns/image@sha256:"+"a".repeat(64)),OkeManifests.inspect(List.of(OkeManifestsTest.artifact(text)),"comp","project","companion-demo"));var docs=new ArrayList<Map>();new Yaml().loadAll(text).forEach(x->docs.add((Map)x));assertEquals(3,docs.size());assertEquals("Namespace",docs.get(0).get("kind"));var spec=(Map)docs.get(1).get("spec");assertEquals(2,spec.get("replicas"));var pod=(Map)((Map)spec.get("template")).get("spec");assertEquals(Map.of("kubernetes.io/arch","amd64"),pod.get("nodeSelector"));var container=(Map)((List)pod.get("containers")).get(0);assertEquals(Map.of("cpu","250m","memory","256Mi"),((Map)container.get("resources")).get("requests"));assertEquals("false",((Map)((List)container.get("env")).get(0)).get("value"));assertTrue(container.containsKey("readinessProbe"));assertEquals("ClusterIP",((Map)docs.get(2).get("spec")).get("type"));
 }
 @Test void pvcUsesRecreateAndDoesNotCreateOrDeleteStorage(){
  var yaml=OkeDeploymentManifest.render(input("companion-demo","","data","/data",1),"icn.ocir.io/ns/image@sha256:"+"a".repeat(64));assertTrue(yaml.contains("Recreate"));assertTrue(yaml.contains("claimName: data"));assertFalse(yaml.contains("kind: PersistentVolumeClaim"));
  assertThrows(RuntimeException.class,()->OkeDeploymentManifest.render(input("companion-demo","","data","/data",2),"icn.ocir.io/ns/image@sha256:"+"a".repeat(64)));
 }
 @Test void invalidNamespaceDuplicateVariablesAndUnpinnedImageFail(){
  String image="icn.ocir.io/ns/image@sha256:"+"a".repeat(64);
  assertThrows(RuntimeException.class,()->OkeDeploymentManifest.render(input("kube-system","","","",1),image));assertThrows(RuntimeException.class,()->OkeDeploymentManifest.render(input("companion-demo","A=1\nA=2","","",1),image));assertThrows(RuntimeException.class,()->OkeDeploymentManifest.render(input("companion-demo","","","",1),"image:latest"));
 }
}
