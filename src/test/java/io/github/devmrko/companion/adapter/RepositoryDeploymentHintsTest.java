package io.github.devmrko.companion.adapter;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryDeploymentHintsTest {
 @Test void dockerfileOnlyProvidesPortAndProbeWithoutInventingReplicasOrSecret()throws Exception{
  String dockerfile;try(var in=getClass().getResourceAsStream("/onboarding/Dockerfile")){dockerfile=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  var h=RepositoryDeploymentHints.read(Map.of("Dockerfile",dockerfile),"Dockerfile");
  assertEquals("8080",h.values().get("port").value());assertEquals("/readyz",h.values().get("healthPath").value());assertEquals("Dockerfile · EXPOSE",h.values().get("port").source());assertFalse(h.values().containsKey("replicas"));assertFalse(h.values().containsKey("pullSecret"));
 }
 @Test void manifestWinsAndSecretContentsAreNeverHints(){
  var h=RepositoryDeploymentHints.read(Map.of("k8s/app.yaml","kind: Deployment\nspec:\n  replicas: 2\n  template:\n    spec:\n      imagePullSecrets: [{name: existing-pull}]\n      containers:\n      - ports: [{containerPort: 9000}]\n        readinessProbe:\n          httpGet: {path: /healthy}\n---\nkind: Secret\nstringData: {password: NEVER_COPY}\n","Dockerfile","FROM base\nEXPOSE 8080\n","application.properties","server.port=7070"),"Dockerfile");
  assertEquals("9000",h.values().get("port").value());assertEquals("2",h.values().get("replicas").value());assertEquals("existing-pull",h.values().get("pullSecret").value());assertFalse(h.toString().contains("NEVER_COPY"));
 }
 @Test void doesNotEvaluateTemplatesVariablesOrBuildStagePorts(){
  var h=RepositoryDeploymentHints.read(Map.of("chart/templates/deployment.yaml","replicas: {{ .Values.replicaCount }}","Dockerfile","FROM build\nEXPOSE 1111\nFROM runtime\nEXPOSE ${PORT}\n","application.yaml","server: {port: 8181}\nhealth: {path: /actuator/health}"),"Dockerfile");
  assertEquals("8181",h.values().get("port").value());assertEquals("/actuator/health",h.values().get("healthPath").value());assertFalse(h.notes().isEmpty());
 }
}
