package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.model.*;
import com.oracle.bmc.artifacts.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentImages.State;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciDeploymentImagesTest {
    final Connection c=new Connection("DEFAULT","tenant","user","ap-seoul-1","",false);
    final String digest="sha256:"+"a".repeat(64),image="icn.ocir.io/ns/app@"+digest;
    @Test void unknownHostIsNotQueried() {
        var registry=mock(OciDeploymentSetup.class);var adapter=new OciDeploymentImages(registry);
        assertEquals(State.UNKNOWN,adapter.inspect(c,"ap-seoul-1","comp",List.of("evil.example/ns/app@"+digest)).getFirst().state());verifyNoInteractions(registry);
        assertNull(OciDeploymentImages.parse("ap-seoul-1","icn.ocir.io.evil/ns/app@"+digest));
    }
    ArtifactsClient repository(OciDeploymentSetup registry)throws Exception{
        var client=mock(ArtifactsClient.class);when(registry.artifacts(c,"ap-seoul-1")).thenReturn(client);
        when(client.listContainerRepositories(any())).thenReturn(ListContainerRepositoriesResponse.builder().containerRepositoryCollection(ContainerRepositoryCollection.builder().items(List.of(ContainerRepositorySummary.builder().id("repo").displayName("app").compartmentId("comp").build())).build()).build());
        when(client.getContainerRepository(any())).thenReturn(GetContainerRepositoryResponse.builder().containerRepository(ContainerRepository.builder().id("repo").namespace("ns").displayName("app").compartmentId("comp").lifecycleState(ContainerRepository.LifecycleState.Available).build()).build());return client;
    }
    @Test void missingDigestBlocksOnlyAfterSuccessfulExactRepositoryLookup()throws Exception{
        var registry=mock(OciDeploymentSetup.class);var client=repository(registry);var adapter=new OciDeploymentImages(registry);
        when(client.listContainerImages(any())).thenReturn(ListContainerImagesResponse.builder().containerImageCollection(ContainerImageCollection.builder().items(List.of()).build()).build());
        assertEquals(State.BLOCKED,adapter.inspect(c,"ap-seoul-1","comp",List.of(image)).getFirst().state());
        when(client.listContainerImages(any())).thenThrow(new RuntimeException("denied"));
        assertEquals(State.UNKNOWN,adapter.inspect(c,"ap-seoul-1","comp",List.of(image)).getFirst().state());
        assertEquals(State.UNKNOWN,adapter.inspect(c,"ap-seoul-1","comp",List.of(image.replace("/ns/","/other/"))).getFirst().state());
    }
    @Test void followsPaginationAndChecksExactDigestAndState()throws Exception{
        var registry=mock(OciDeploymentSetup.class);var client=repository(registry);var adapter=new OciDeploymentImages(registry);
        var available=ContainerImageSummary.builder().id("image").compartmentId("comp").repositoryId("repo").digest(digest).lifecycleState(ContainerImage.LifecycleState.Available).build();
        when(client.listContainerImages(any())).thenReturn(ListContainerImagesResponse.builder().opcNextPage("next").containerImageCollection(ContainerImageCollection.builder().items(List.of()).build()).build(),ListContainerImagesResponse.builder().containerImageCollection(ContainerImageCollection.builder().items(List.of(available)).build()).build());
        assertEquals(State.VERIFIED,adapter.inspect(c,"ap-seoul-1","comp",List.of(image)).getFirst().state());
        var capture=org.mockito.ArgumentCaptor.forClass(com.oracle.bmc.artifacts.requests.ListContainerImagesRequest.class);verify(client,times(2)).listContainerImages(capture.capture());assertEquals("next",capture.getAllValues().get(1).getPage());assertEquals(digest,capture.getValue().getImageDigest());assertEquals("repo",capture.getValue().getRepositoryId());
    }
}
