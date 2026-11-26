package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class NamespaceRetirementTest {
 private final JsonMapper json=JsonMapper.builder().build();
 private final String deployment="""
 {"metadata":{"name":"verify","namespace":"old","uid":"deployment-uid","resourceVersion":"12"},"spec":{"template":{"metadata":{"labels":{"app":"verify"}}}}}
 """;
 private final String services="""
 {"items":[{"metadata":{"name":"verify","namespace":"old","uid":"service-uid","resourceVersion":"13"},"spec":{"type":"ClusterIP","selector":{"app":"verify"}}}]}
 """;
 @Test void capturesExactIdentityAndRejectsExternalExposure(){
  var empty=json.readTree("{\"items\":[]}");var d=json.readTree(deployment);var s=json.readTree(services);var plan=OciOkeRuntimeResources.retirement(d,s,empty,"old","verify");assertEquals("deployment-uid",plan.deploymentUid());assertEquals("13",plan.serviceVersion());
  assertThrows(RuntimeException.class,()->OciOkeRuntimeResources.retirement(d,json.readTree(services.replace("ClusterIP","NodePort")),empty,"old","verify"));
  assertThrows(RuntimeException.class,()->OciOkeRuntimeResources.retirement(d,s,json.readTree("{\"items\":[{\"spec\":{\"defaultBackend\":{\"service\":{\"name\":\"verify\",\"port\":{\"number\":80}}}}}]}"),"old","verify"));
  assertThrows(RuntimeException.class,()->OciOkeRuntimeResources.retirement(d,s,empty,"other","verify"));
 }
 @Test void rejectsReplacementAndAdditionalSelectingService(){
  var d=json.readTree(deployment);var empty=json.readTree("{\"items\":[]}");var duplicate=services.replace("}]}","},"+services.substring(services.indexOf('[')+1,services.lastIndexOf(']'))+"]}");
  assertThrows(RuntimeException.class,()->OciOkeRuntimeResources.retirement(d,json.readTree(duplicate),empty,"old","verify"));
  assertThrows(RuntimeException.class,()->OciOkeRuntimeResources.retirement(json.readTree(deployment.replace("\"uid\":\"deployment-uid\",","")),json.readTree(services),empty,"old","verify"));
 }
}
