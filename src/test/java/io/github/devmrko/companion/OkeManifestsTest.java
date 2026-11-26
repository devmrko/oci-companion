package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OkeManifestsTest {
    static final String IMAGE="icn.ocir.io/example/app@sha256:"+"a".repeat(64);
    static final String YAML="""
        apiVersion: apps/v1
        kind: Deployment
        metadata: {name: app, namespace: testing}
        spec:
          template:
            spec:
              containers:
              - {name: app, image: IMAGE}
        """.replace("IMAGE",IMAGE);
    static DeployArtifact artifact(String text){return DeployArtifact.builder().compartmentId("comp").projectId("project")
        .deployArtifactType(DeployArtifact.DeployArtifactType.KubernetesManifest)
        .argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None)
        .deployArtifactSource(InlineDeployArtifactSource.builder().base64EncodedContent(text.getBytes(StandardCharsets.UTF_8)).build()).build();}
    static List<String> inspect(String text){return OkeManifests.inspect(List.of(artifact(text)),"comp","project","testing");}
    @Test void generatedWalletSecurityAndThreeHeaderProbesPassExecutionInspection(){
        var base=new io.github.devmrko.companion.port.OkeDeploymentSetup.Input("env","repo","image","verify","description","testing","verify","arm64",2,100,512,8845,"/readyz","","ocir-pull","","",true);
        var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();var fields=mapper.convertValue(base,Map.class);
        fields.putAll(Map.of("probeHeaders","Host=localhost","startupPath","/healthz","livenessPath","/healthz","fileVolumes","secret wallet /app/wallet true","emptyDirs","/tmp","serviceType","NodePort","nodePort",31084,"createNamespace",false,"podRunAsNonRoot",true));
        var input=mapper.convertValue(fields,io.github.devmrko.companion.port.OkeDeploymentSetup.Input.class);String yaml=OkeDeploymentManifest.render(input,IMAGE);
        assertFalse(yaml.contains("&id"));assertEquals(List.of(IMAGE),inspect(yaml));
    }
    @Test void serviceReplacementCannotRetargetAnotherDeployment(){
        assertDoesNotThrow(()->OkeManifests.replacementTarget(artifact(YAML),"comp","project","testing","app"));
        assertThrows(RuntimeException.class,()->OkeManifests.replacementTarget(artifact(YAML),"comp","project","testing","other"));
        assertThrows(RuntimeException.class,()->OkeManifests.replacementTarget(artifact(YAML+"---\n"+YAML.replace("name: app","name: other")),"comp","project","testing","app"));
    }
    @Test void fixedImageAndExplicitNamespaceAreDisplayed(){assertEquals(List.of(IMAGE),inspect(YAML));}
    @Test void mutableImagesAndMissingNamespacesAreRejected(){
        assertThrows(RuntimeException.class,()->inspect(YAML.replace(IMAGE,"app:latest")));
        assertThrows(RuntimeException.class,()->inspect(YAML.replace(", namespace: testing","")));
        assertThrows(RuntimeException.class,()->inspect(YAML.replace("namespace: testing","namespace: other")));
    }
    @Test void secretObjectsAndClusterRolesAreRejected(){
        for(var kind:List.of("Secret","ClusterRole","List"))assertThrows(RuntimeException.class,()->inspect(YAML.replace("kind: Deployment","kind: "+kind)));
    }
    @Test void duplicateKeysAliasesAndDuplicateObjectsAreRejected(){
        assertThrows(RuntimeException.class,()->inspect(YAML+"kind: Service\n"));
        assertThrows(RuntimeException.class,()->inspect(YAML+"---\n"+YAML));
        assertThrows(RuntimeException.class,()->inspect(YAML.replace("containers:","initContainers: &x [{name: init, image: app:latest}]\n      containers:")));
        assertThrows(RuntimeException.class,()->inspect(YAML.replace("containers:","initContainers: &x [{name: init, image: app:latest}]\n      extra: *x\n      containers:")));
    }
    @Test void artifactsCannotCrossProjectOrUseSubstitution(){
        assertThrows(RuntimeException.class,()->OkeManifests.inspect(List.of(artifact(YAML)),"comp","other","testing"));
        var changed=artifact(YAML).toBuilder().argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.SubstitutePlaceholders).build();
        assertThrows(RuntimeException.class,()->OkeManifests.inspect(List.of(changed),"comp","project","testing"));
    }
    @Test void emptyAndOversizedManifestsFail(){
        assertThrows(RuntimeException.class,()->inspect(""));
        assertThrows(RuntimeException.class,()->inspect(" ".repeat(524289)));
    }
    static final String JOB="""
        apiVersion: batch/v1
        kind: Job
        metadata: {name: verify-001, namespace: testing}
        spec:
          activeDeadlineSeconds: 180
          backoffLimit: 0
          ttlSecondsAfterFinished: 3600
          template:
            spec:
              restartPolicy: Never
              serviceAccountName: verifier
              securityContext: {runAsNonRoot: true, runAsUser: 10001}
              containers:
              - name: verify
                image: IMAGE
                securityContext: {allowPrivilegeEscalation: false, readOnlyRootFilesystem: true, capabilities: {drop: [ALL]}}
                resources: {limits: {cpu: 500m, memory: 256Mi}}
                env:
                - name: TOKEN
                  valueFrom: {secretKeyRef: {name: app-auth, key: token}}
              volumes:
              - name: credentials
                projected:
                  sources:
                  - secret: {name: validation-auth}
        """.replace("IMAGE",IMAGE);
    @Test void boundedJobShowsExecutionLimitsAndOnlySecretReferences(){
        var result=OkeManifests.inspectDetails(List.of(artifact(JOB)),"comp","project","testing");
        assertEquals(List.of(IMAGE),result.images());var job=result.jobs().getFirst();
        assertEquals("verify-001",job.name());assertEquals("verifier",job.serviceAccount());
        assertEquals(180,job.deadlineSeconds());assertEquals(0,job.retries());assertEquals(3600,job.ttlSeconds());
        assertEquals(List.of("app-auth","validation-auth"),job.secrets());
    }
    @Test void unboundedOrUnsafeJobsFail(){
        for(var pair:List.of(
            List.of("activeDeadlineSeconds: 180","activeDeadlineSeconds: 601"),
            List.of("activeDeadlineSeconds: 180","activeDeadlineSeconds: 1.5"),
            List.of("backoffLimit: 0","backoffLimit: 3"),
            List.of("ttlSecondsAfterFinished: 3600","ttlSecondsAfterFinished: 0"),
            List.of("restartPolicy: Never","restartPolicy: OnFailure"),
            List.of("serviceAccountName: verifier","serviceAccountName: default"),
            List.of("runAsNonRoot: true","runAsNonRoot: false"),
            List.of("runAsUser: 10001","runAsUser: 0"),
            List.of("allowPrivilegeEscalation: false","allowPrivilegeEscalation: true"),
            List.of("readOnlyRootFilesystem: true","readOnlyRootFilesystem: false"),
            List.of("drop: [ALL]","drop: [NET_RAW]"),
            List.of("cpu: 500m","cpu: 2000m"),
            List.of("memory: 256Mi","memory: 8Gi"),
            List.of("namespace: testing","namespace: other"),
            List.of(IMAGE,"app:latest"),
            List.of("restartPolicy: Never","restartPolicy: Never\n      hostNetwork: true"),
            List.of("backoffLimit: 0","backoffLimit: 0\n  parallelism: 2"),
            List.of("secret: {name: validation-auth}","serviceAccountToken: {path: token}")))
            assertThrows(RuntimeException.class,()->inspect(JOB.replace(pair.get(0),pair.get(1))),pair.toString());
        assertThrows(RuntimeException.class,()->inspect(JOB.replace("  activeDeadlineSeconds: 180\n","")));
        assertThrows(RuntimeException.class,()->inspect(JOB.replace("projected:\n          sources:\n          - secret: {name: validation-auth}","hostPath: {path: /}")));
    }
}
