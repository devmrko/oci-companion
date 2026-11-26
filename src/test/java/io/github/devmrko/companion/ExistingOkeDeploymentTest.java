package io.github.devmrko.companion.adapter;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ExistingOkeDeploymentTest {
 private static final String IMAGE="yny.ocir.io/ns/app@sha256:"+"a".repeat(64);
 private static final String LIVE="""
 {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"app","namespace":"apps","resourceVersion":"123","annotations":{"kubectl.kubernetes.io/last-applied-configuration":"omit"}},
 "spec":{"replicas":2,"selector":{"matchLabels":{"app":"app"}},"template":{"metadata":{"labels":{"app":"app"}},"spec":{
 "automountServiceAccountToken":false,"enableServiceLinks":false,"securityContext":{"runAsUser":10001,"seccompProfile":{"type":"RuntimeDefault"}},
 "volumes":[{"name":"wallet","secret":{"secretName":"wallet","defaultMode":288}},{"name":"tmp","emptyDir":{"sizeLimit":"64Mi"}}],
 "containers":[{"name":"app","image":"old","securityContext":{"allowPrivilegeEscalation":false},"readinessProbe":{"httpGet":{"path":"/readyz","port":8845,"httpHeaders":[{"name":"Host","value":"localhost"}]},"timeoutSeconds":5}}]}}},"status":{"readyReplicas":2}}
 """;
 @Test void preservesEntireLiveSpecAndOmitsServiceNamespaceAndServerMetadata()throws Exception{
  var before=JsonMapper.builder().build().readTree(LIVE);
  String yaml=ExistingOkeDeployment.render(LIVE,"apps","app",IMAGE);
  Map<?,?> output=new Yaml().load(yaml);
  var after=JsonMapper.builder().build().valueToTree(output);
  var expected=(tools.jackson.databind.node.ObjectNode)before.path("spec").deepCopy();
  ((tools.jackson.databind.node.ObjectNode)expected.path("template").path("spec").path("containers").get(0)).put("image",IMAGE);
  assertEquals(expected,after.path("spec"));
  assertEquals(Set.of("apiVersion","kind","metadata","spec"),output.keySet());
  assertFalse(yaml.contains("resourceVersion"));assertFalse(yaml.contains("last-applied"));
  assertFalse(yaml.contains("kind: Service"));assertFalse(yaml.contains("kind: Namespace"));
 }
 @Test void refusesWrongScopeUnpinnedImageAndLiteralEnvironment(){
  assertThrows(RuntimeException.class,()->ExistingOkeDeployment.render(LIVE,"other","app",IMAGE));
  assertThrows(RuntimeException.class,()->ExistingOkeDeployment.render(LIVE,"apps","other",IMAGE));
  assertThrows(RuntimeException.class,()->ExistingOkeDeployment.render(LIVE,"apps","app","repo:latest"));
  assertThrows(RuntimeException.class,()->ExistingOkeDeployment.render(LIVE.replace("\"image\":\"old\"","\"image\":\"old\",\"env\":[{\"name\":\"PASSWORD\",\"value\":\"never-preview\"}]"),"apps","app",IMAGE));
 }
}
