package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class OnboardingManifestTest {
 static final String LIVE="""
 {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"original","namespace":"apps","resourceVersion":"7"},"spec":{"replicas":2,"selector":{"matchLabels":{"app":"original"}},"template":{"metadata":{"labels":{"app":"original"}},"spec":{"automountServiceAccountToken":false,"imagePullSecrets":[{"name":"pull"}],"volumes":[{"name":"wallet","secret":{"secretName":"wallet"}}],"containers":[{"name":"original","image":"region.ocir.io/ns/app@sha256:%s","ports":[{"containerPort":8080}],"volumeMounts":[{"name":"wallet","mountPath":"/wallet","readOnly":true}],"readinessProbe":{"httpGet":{"path":"/readyz","port":8080,"httpHeaders":[{"name":"Host","value":"app.test"}]},"timeoutSeconds":5},"securityContext":{"readOnlyRootFilesystem":true}}]}}}}
 """.formatted("a".repeat(64));
 @Test void isolatesSelectorsAndKeepsRuntimeConfiguration(){
  var result=OnboardingManifest.clone(LIVE,"apps","original","new-app");var parts=result.yaml().split("\n---\n");var j=JsonMapper.builder().build();var d=j.readTree(parts[0]);var s=j.readTree(parts[1]);
  assertEquals("new-app",d.path("metadata").path("name").asText());assertEquals(d.path("spec").path("selector").path("matchLabels"),d.path("spec").path("template").path("metadata").path("labels"));
  var pod=d.path("spec").path("template").path("spec");assertEquals("wallet",pod.path("volumes").get(0).path("secret").path("secretName").asText());assertEquals("pull",pod.path("imagePullSecrets").get(0).path("name").asText());assertEquals(5,pod.path("containers").get(0).path("readinessProbe").path("timeoutSeconds").asInt());assertFalse(pod.path("automountServiceAccountToken").asBoolean());
  assertEquals("ClusterIP",s.path("spec").path("type").asText());assertFalse(s.path("spec").path("ports").get(0).has("nodePort"));assertFalse(d.path("metadata").has("resourceVersion"));assertEquals(2,result.replicas());
 }
 @Test void rejectsSourceMutationUnpinnedImageAndLiteralEnvironment(){
  assertThrows(RuntimeException.class,()->OnboardingManifest.clone(LIVE,"apps","original","original"));
  assertThrows(RuntimeException.class,()->OnboardingManifest.clone(LIVE.replace("@sha256:"+"a".repeat(64),":latest"),"apps","original","new-app"));
  assertThrows(RuntimeException.class,()->OnboardingManifest.clone(LIVE.replace("\"ports\":", "\"env\":[{\"name\":\"TOKEN\",\"value\":\"not-for-preview\"}],\"ports\":"),"apps","original","new-app"));
 }
}
