package io.github.devmrko.companion.adapter;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryHelmTest {
 @TempDir Path temp;
 @Test void isolatesCredentialsAndUsesLocalRenderingOnly()throws Exception{
  Path binary=temp.resolve("helm");Files.writeString(binary,"""
#!/bin/sh
[ -z "$OCI_CONFIG_FILE$AWS_SECRET_ACCESS_KEY$KUBECONFIG" ] || exit 11
[ -d "$HELM_PLUGINS" ] || exit 12
[ "$1" = template ] || exit 13
case "$*" in *--dry-run=client*--no-hooks*--skip-tests*--skip-schema-validation*--kubeconfig*) ;; *) exit 14;; esac
[ -f "$3/templates/deployment.yaml" ] || exit 15
printf 'kind: Deployment\\nspec:\\n  replicas: 2\\n  template:\\n    spec:\\n      containers: [{ports: [{containerPort: 8181}]}]\\n'
""");binary.toFile().setExecutable(true);
  var result=new RepositoryHelm(binary.toString()).render(Map.of("chart/Chart.yaml","apiVersion: v2\nname: test\nversion: 0.1.0","chart/templates/deployment.yaml","{{ .Values.port }}","Dockerfile","FROM base\nEXPOSE 8080"),"verify","test");
  assertFalse(result.containsKey("chart/templates/deployment.yaml"));var hints=RepositoryDeploymentHints.read(result,"Dockerfile");assertEquals("8181",hints.values().get("port").value());assertEquals("chart/helm-rendered.yaml",hints.workloadSource());
 }
 @Test void refusesAmbiguousChartsAndDoesNotExposeTemplateErrorValues()throws Exception{
  var renderer=new RepositoryHelm("missing");assertThrows(IllegalArgumentException.class,()->renderer.render(Map.of("a/Chart.yaml","x","b/Chart.yaml","x"),"verify","test"));
  Path binary=temp.resolve("helm");Files.writeString(binary,"#!/bin/sh\necho SECRET_TEMPLATE_VALUE >&2\nexit 1\n");binary.toFile().setExecutable(true);
  var error=assertThrows(IllegalArgumentException.class,()->new RepositoryHelm(binary.toString()).render(Map.of("Chart.yaml","x"),"verify","test"));assertFalse(error.getMessage().contains("SECRET_TEMPLATE_VALUE"));assertTrue(error.getMessage().contains("종료 코드 1"));
 }
 @Test void nestedDependenciesAreNotSeparateRootCharts(){assertEquals(List.of("chart/"),RepositoryHelm.roots(Map.of("chart/Chart.yaml","","chart/charts/dependency/Chart.yaml","")));}
}
