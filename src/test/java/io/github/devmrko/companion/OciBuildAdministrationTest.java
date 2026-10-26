package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OciBuildAdministrationTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    @Test void pipelineRequiresActiveProjectInSelectedCompartmentAndUsesRetryToken() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder()
            .id("project").compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
        when(client.createBuildPipeline(any())).thenReturn(CreateBuildPipelineResponse.builder().buildPipeline(BuildPipeline.builder().id("pipeline").build()).build());
        var adapter=new OciBuildAdministration(clients);
        assertThrows(RuntimeException.class,()->adapter.createPipeline(c,"region","foreign","project","name","","token"));
        verify(client,never()).createBuildPipeline(any());
        assertEquals("pipeline",adapter.createPipeline(c,"region","comp","project","name","description","token"));
        var request=org.mockito.ArgumentCaptor.forClass(CreateBuildPipelineRequest.class);
        verify(client).createBuildPipeline(request.capture());
        assertEquals("token",request.getValue().getOpcRetryToken());
        assertEquals("project",request.getValue().getCreateBuildPipelineDetails().getProjectId());
        assertEquals("description",request.getValue().getCreateBuildPipelineDetails().getDescription());
    }
    @Test void projectRequiresListedTopicAndCreatesActualNotificationConfiguration() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.createProject(any())).thenReturn(CreateProjectResponse.builder().project(Project.builder().id("project").build()).build());
        var adapter=spy(new OciBuildAdministration(clients));
        doReturn(List.of(new Choice("topic","notifications"))).when(adapter).topics(c,"region","comp");
        assertThrows(RuntimeException.class,()->adapter.createProject(c,"region","comp","name","","foreign","token"));
        verify(client,never()).createProject(any());
        assertEquals("project",adapter.createProject(c,"region","comp","name","description","topic","token"));
        var request=org.mockito.ArgumentCaptor.forClass(CreateProjectRequest.class);
        verify(client).createProject(request.capture());
        assertEquals("comp",request.getValue().getCreateProjectDetails().getCompartmentId());
        assertEquals("topic",request.getValue().getCreateProjectDetails().getNotificationConfig().getTopicId());
        assertEquals("token",request.getValue().getOpcRetryToken());
    }
}
