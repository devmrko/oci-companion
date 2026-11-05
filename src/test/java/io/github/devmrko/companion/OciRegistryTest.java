package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.model.*;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.artifacts.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciRegistryTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    OciRegistry adapter(ArtifactsClient client) throws Exception {
        var adapter=spy(new OciRegistry(mock(OciBuildHistory.class)));doReturn(client).when(adapter).client(c,"region");return adapter;
    }
    @Test void rejectsOtherCompartmentBeforeListingOrUpdatingAndRejectsStaleEtag() throws Exception {
        var client=mock(ArtifactsClient.class);var adapter=adapter(client);
        when(client.getRepository(any())).thenReturn(GetRepositoryResponse.builder().etag("latest").repository(GenericRepository.builder().id("repo").displayName("repo").compartmentId("other").build()).build());
        assertThrows(RuntimeException.class,()->adapter.artifacts(c,"region","comp","GENERIC","repo",""));
        assertThrows(RuntimeException.class,()->adapter.update(c,"region","comp","GENERIC","repo","latest","repo","new"));
        verify(client,never()).listGenericArtifacts(any());verify(client,never()).updateRepository(any());
        when(client.getRepository(any())).thenReturn(GetRepositoryResponse.builder().etag("latest").repository(GenericRepository.builder().id("repo").displayName("repo").compartmentId("comp").isImmutable(true).build()).build());
        assertThrows(RuntimeException.class,()->adapter.update(c,"region","comp","GENERIC","repo","stale","repo","new"));
        verify(client,never()).updateRepository(any());
        adapter.update(c,"region","comp","GENERIC","repo","latest","repo","new");
        var request=org.mockito.ArgumentCaptor.forClass(UpdateRepositoryRequest.class);verify(client).updateRepository(request.capture());
        assertEquals("latest",request.getValue().getIfMatch());
        assertNull(request.getValue().getUpdateRepositoryDetails().getDefinedTags());
    }
    @Test void containerCreationIsPrivateAndCarriesRetryToken() throws Exception {
        var client=mock(ArtifactsClient.class);var adapter=adapter(client);
        when(client.createContainerRepository(any())).thenReturn(CreateContainerRepositoryResponse.builder().containerRepository(ContainerRepository.builder().id("repo").build()).build());
        assertEquals("repo",adapter.create(c,"region","comp","CONTAINER","team/application","readme",true,"token"));
        var request=org.mockito.ArgumentCaptor.forClass(CreateContainerRepositoryRequest.class);verify(client).createContainerRepository(request.capture());
        assertNull(request.getValue().getCreateContainerRepositoryDetails().getIsImmutable());assertFalse(request.getValue().getCreateContainerRepositoryDetails().getIsPublic());assertEquals("token",request.getValue().getOpcRetryToken());
        assertThrows(RuntimeException.class,()->adapter.create(c,"region","comp","OTHER","invalid","",true,"token2"));
    }
    @Test void artifactPagePreservesContinuationAndActualDigest() throws Exception {
        var client=mock(ArtifactsClient.class);var adapter=adapter(client);
        when(client.getRepository(any())).thenReturn(GetRepositoryResponse.builder().repository(GenericRepository.builder().id("repo").compartmentId("comp").build()).build());
        when(client.listGenericArtifacts(any())).thenReturn(ListGenericArtifactsResponse.builder().opcNextPage("next").genericArtifactCollection(GenericArtifactCollection.builder().items(List.of(
            GenericArtifactSummary.builder().id("file").artifactPath("app.jar").version("v1").sha256("hash").sizeInBytes(42L).build())).build()).build());
        var page=adapter.artifacts(c,"region","comp","GENERIC","repo","previous");
        assertEquals("next",page.next());assertEquals("hash",page.items().getFirst().digest());assertEquals("42",page.items().getFirst().bytes());
        verify(client).listGenericArtifacts(argThat(r->r.getPage().equals("previous")&&r.getRepositoryId().equals("repo")&&r.getLimit()==100));
    }
}
