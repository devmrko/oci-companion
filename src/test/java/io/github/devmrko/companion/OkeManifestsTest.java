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
}
