package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildControl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildControlTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    @Test void staleDefaultsAndForeignCompartmentNeverWrite() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().etag("new")
            .buildPipeline(BuildPipeline.builder().id("p").compartmentId("comp").build()).build());
        var adapter=new OciBuildControl(clients);
        assertThrows(RuntimeException.class,()->adapter.defaults(c,"region","comp","p","old",List.of()));
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","foreign","p","new","run",List.of(),"token"));
        verify(client,never()).updateBuildPipeline(any()); verify(client,never()).createBuildRun(any());
    }
    @Test void defaultsUseEtagAndPreserveDescription() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().etag("etag")
            .buildPipeline(BuildPipeline.builder().id("p").compartmentId("comp").build()).build());
        new OciBuildControl(clients).defaults(c,"region","comp","p","etag",List.of(new BuildControl.Parameter("VERSION","1","release")));
        var captor=org.mockito.ArgumentCaptor.forClass(UpdateBuildPipelineRequest.class);
        verify(client).updateBuildPipeline(captor.capture());
        assertEquals("etag",captor.getValue().getIfMatch());
        assertEquals("release",captor.getValue().getUpdateBuildPipelineDetails().getBuildPipelineParameters().getItems().getFirst().getDescription());
    }
}
