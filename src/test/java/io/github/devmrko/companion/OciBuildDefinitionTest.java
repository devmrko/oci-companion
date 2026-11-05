package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildDefinition;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildDefinitionTest {
    @Test void readsBlobAtPinnedCommitAndExtractsRealOutput() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        var c=new Connection("profile","tenant","user","region","label",false);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.listCommits(any())).thenReturn(ListCommitsResponse.builder().repositoryCommitCollection(RepositoryCommitCollection.builder().items(List.of(RepositoryCommitSummary.builder().commitId("commit").build())).build()).build());
        when(client.getObject(any())).thenReturn(GetObjectResponse.builder().repositoryObject(RepositoryObject.builder().sha("blob").sizeInBytes(80L).isBinary(false).build()).build());
        String yaml="version: 0.1\noutputArtifacts:\n  - name: app.jar\n    type: BINARY\n    location: output/app.jar\n";
        when(client.getObjectContent(any())).thenReturn(GetObjectContentResponse.builder().inputStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).build());
        var source=new BuildDefinition.Source("key","source","OCI","repository","url","main",true);
        var result=new OciBuildDefinition(clients).specification(c,"region",source,"build_spec.yaml");
        assertEquals(List.of("app.jar · BINARY · output/app.jar"),result.outputs());
        verify(client).getObject(argThat(r->r.getRefName().equals("commit") && r.getFilePath().equals("build_spec.yaml")));
        verify(client).getObjectContent(argThat(r->r.getSha().equals("blob")));
    }
}
