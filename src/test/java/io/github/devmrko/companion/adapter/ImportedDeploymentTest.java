package io.github.devmrko.companion.adapter;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import io.github.devmrko.companion.port.*;
import static org.junit.jupiter.api.Assertions.*;
class ImportedDeploymentTest {
 static final String SOURCE="""
 apiVersion: apps/v1
 kind: Deployment
 spec:
   replicas: 2
   template:
     spec:
       automountServiceAccountToken: false
       volumes:
       - name: files
         configMap: {name: app-files}
       - name: tmp
         emptyDir: {}
       containers:
       - name: source
         env:
         - {name: APP_MODE, value: verify}
         - name: APP_AUTH
           valueFrom: {secretKeyRef: {name: app-auth, key: value}}
         ports: [{name: web, containerPort: 8080}]
         resources:
           requests: {cpu: 100m, memory: 64Mi}
           limits: {cpu: 250m, memory: 128Mi}
         readinessProbe:
           httpGet: {port: web, path: /readyz, httpHeaders: [{name: Host, value: verify.test}]}
           timeoutSeconds: 7
           failureThreshold: 8
         volumeMounts:
         - {name: files, mountPath: /config, readOnly: true}
         - {name: tmp, mountPath: /tmp}
         securityContext: {runAsUser: 10001, runAsNonRoot: true, readOnlyRootFilesystem: true}
 """;
 @Test void preservesAdvancedSettingsAndRequiresAllExistingReferences(){
  var imported=ImportedDeployment.extract(new Yaml().load(SOURCE));var input=new OkeDeploymentSetup.Input("env","repo","image","new-app","","apps","new-app","arm64",2,150,256,8080,"/readyz","","pull","","",false);
  String yaml=ImportedDeployment.overlay(OkeDeploymentManifest.render(input,"region.ocir.io/ns/app@sha256:"+"a".repeat(64)),imported);
  Map<String,Object> doc=null;for(Object item:new Yaml().loadAll(yaml))if("Deployment".equals(ImportedDeployment.map(item).get("kind")))doc=ImportedDeployment.map(item);assertNotNull(doc);var pod=ImportedDeployment.map(RepositoryDeploymentHints.at(doc,"spec","template","spec"));var container=ImportedDeployment.list(pod.get("containers")).getFirst();
  assertEquals("new-app",container.get("name"));assertEquals("250m",RepositoryDeploymentHints.at(container,"resources","limits","cpu"));assertEquals(7,RepositoryDeploymentHints.at(container,"readinessProbe","timeoutSeconds"));assertEquals(2,ImportedDeployment.list(pod.get("volumes")).size());assertEquals("verify",RepositoryDeploymentHints.at(container,"env","0","value"));
  var missing=new OkeRuntimeResources.Snapshot(java.time.Instant.now(),"cluster","apps",Map.of(),List.of(),List.of());assertThrows(RuntimeException.class,()->ImportedDeployment.requireReferences(imported,missing));
  var present=new OkeRuntimeResources.Snapshot(java.time.Instant.now(),"cluster","apps",Map.of("configmaps",new OkeRuntimeResources.Listing(true,List.of(new OkeRuntimeResources.Resource("app-files","")),""),"secrets",new OkeRuntimeResources.Listing(true,List.of(new OkeRuntimeResources.Resource("app-auth","Opaque")),"")),List.of(),List.of());assertDoesNotThrow(()->ImportedDeployment.requireReferences(imported,present));
 }
 @Test void refusesSensitiveLiteralsHostVolumesAndPrivilegeEscalation(){
  for(String changed:List.of(SOURCE.replace("APP_MODE","API_TOKEN"),SOURCE.replace("emptyDir: {}","hostPath: {path: /}"),SOURCE.replace("runAsUser: 10001","privileged: true"))){var error=assertThrows(RuntimeException.class,()->ImportedDeployment.extract(new Yaml().load(changed)));assertFalse(error.getMessage().contains("verify.test"));}
 }
 @Test void importedOrdinaryEnvironmentSurvivesImageOnlyRedeploy(){
  var raw=new Yaml().load(SOURCE);var doc=ImportedDeployment.map(raw);doc.put("metadata",Map.of("namespace","apps","name","source"));String rendered=ExistingOkeDeployment.render(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(doc),"apps","source","region.ocir.io/ns/app@sha256:"+"b".repeat(64));assertTrue(rendered.contains("APP_MODE"));assertTrue(rendered.contains("secretKeyRef"));
 }
}
