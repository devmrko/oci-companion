package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildSetupTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    @Test void editRetainsRepositoryAndSecondarySourceAndRejectsStaleStage() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().buildPipeline(BuildPipeline.builder().id("pipe").compartmentId("comp").build()).build());
        var primary=DevopsCodeRepositoryBuildSource.builder().name("src").repositoryId("repo").repositoryUrl("https://example.com/repo").branch("main").build();
        var secondary=GithubBuildSource.builder().name("other").connectionId("connection").repositoryUrl("https://github.com/owner/repo").branch("main").build();
        when(client.getBuildPipelineStage(any())).thenReturn(GetBuildPipelineStageResponse.builder().etag("etag").buildPipelineStage(BuildStage.builder().id("stage").buildPipelineId("pipe").compartmentId("comp")
            .primaryBuildSource("src").buildSourceCollection(BuildSourceCollection.builder().items(List.of(primary,secondary)).build()).build()).build());
        var adapter=new OciBuildSetup(clients,mock(BuildDefinition.class));
        assertThrows(RuntimeException.class,()->adapter.updateBuild(c,"region","comp","pipe","stage","old","build","build.yaml",3600,"release"));
        verify(client,never()).updateBuildPipelineStage(any());
        adapter.updateBuild(c,"region","comp","pipe","stage","etag","build","build.yaml",1800,"release");
        var captor=org.mockito.ArgumentCaptor.forClass(UpdateBuildPipelineStageRequest.class);verify(client).updateBuildPipelineStage(captor.capture());
        var details=(UpdateBuildStageDetails)captor.getValue().getUpdateBuildPipelineStageDetails();
        assertEquals("etag",captor.getValue().getIfMatch());assertEquals(1800,details.getStageExecutionTimeoutInSeconds());
        var updated=(DevopsCodeRepositoryBuildSource)details.getBuildSourceCollection().getItems().getFirst();
        assertEquals("repo",updated.getRepositoryId());assertEquals("release",updated.getBranch());assertEquals(secondary,details.getBuildSourceCollection().getItems().get(1));
    }
    @Test void deliveryValidatesRevisionOutputTypeAndPreservesPredecessor() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(client);
        var defs=mock(BuildDefinition.class);var source=new BuildDefinition.Source("s","src","OCI","repo","url","main",true);
        var stage=new BuildDefinition.Stage("stage","build","빌드·테스트","ACTIVE",List.of("시작"),"image","build.yaml",3600,List.of(source),List.of());
        when(defs.read(c,"region","comp","pipe")).thenReturn(new BuildDefinition.Definition("pipeline","project","ACTIVE","","rev",List.of(stage)));
        when(defs.specification(c,"region",source,"build.yaml")).thenReturn(new BuildDefinition.Specification("","",List.of("jar · BINARY · target/app.jar"),List.of()));
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().buildPipeline(BuildPipeline.builder().id("pipe").projectId("project").compartmentId("comp").build()).build());
        when(client.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().id("artifact").projectId("project").lifecycleState(DeployArtifact.LifecycleState.Active).deployArtifactType(DeployArtifact.DeployArtifactType.GenericFile).build()).build());
        when(client.createBuildPipelineStage(any())).thenReturn(CreateBuildPipelineStageResponse.builder().buildPipelineStage(DeliverArtifactStage.builder().id("deliver").build()).build());
        var adapter=new OciBuildSetup(clients,defs);
        assertThrows(RuntimeException.class,()->adapter.deliver(c,"region","comp","pipe","stale","stage","jar","artifact","token"));
        assertThrows(RuntimeException.class,()->adapter.deliver(c,"region","comp","pipe","rev","stage","missing","artifact","token"));
        verify(client,never()).createBuildPipelineStage(any());
        assertEquals("deliver",adapter.deliver(c,"region","comp","pipe","rev","stage","jar","artifact","token"));
        var captor=org.mockito.ArgumentCaptor.forClass(CreateBuildPipelineStageRequest.class);verify(client).createBuildPipelineStage(captor.capture());
        var details=(CreateDeliverArtifactStageDetails)captor.getValue().getCreateBuildPipelineStageDetails();
        assertEquals("stage",details.getBuildPipelineStagePredecessorCollection().getItems().getFirst().getId());
        assertEquals("jar",details.getDeliverArtifactCollection().getItems().getFirst().getArtifactName());
        assertEquals("token",captor.getValue().getOpcRetryToken());
    }
    @Test void replacingDeliveryDestinationPreservesOtherMappingsAndGraph() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().buildPipeline(BuildPipeline.builder().id("pipe").projectId("project").compartmentId("comp").build()).build());
        var report=DeliverArtifact.builder().artifactName("report").artifactId("reports").build();
        when(client.getBuildPipelineStage(any())).thenReturn(GetBuildPipelineStageResponse.builder().etag("etag").buildPipelineStage(DeliverArtifactStage.builder()
            .id("deliver").buildPipelineId("pipe").compartmentId("comp").deliverArtifactCollection(DeliverArtifactCollection.builder().items(List.of(
                DeliverArtifact.builder().artifactName("jar").artifactId("old").build(),report)).build()).build()).build());
        when(client.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().projectId("project")
            .deployArtifactType(DeployArtifact.DeployArtifactType.GenericFile).lifecycleState(DeployArtifact.LifecycleState.Active).build()).build());
        var adapter=new OciBuildSetup(clients,mock(BuildDefinition.class));
        assertThrows(RuntimeException.class,()->adapter.updateDelivery(c,"region","comp","pipe","deliver","stale","jar","new"));
        verify(client,never()).updateBuildPipelineStage(any());
        adapter.updateDelivery(c,"region","comp","pipe","deliver","etag","jar","new");
        var captor=org.mockito.ArgumentCaptor.forClass(UpdateBuildPipelineStageRequest.class);verify(client).updateBuildPipelineStage(captor.capture());
        var details=(UpdateDeliverArtifactStageDetails)captor.getValue().getUpdateBuildPipelineStageDetails();
        assertEquals("etag",captor.getValue().getIfMatch());
        assertNull(details.getBuildPipelineStagePredecessorCollection());
        assertEquals("new",details.getDeliverArtifactCollection().getItems().getFirst().getArtifactId());
        assertEquals(report,details.getDeliverArtifactCollection().getItems().get(1));
    }
    @Test void completedBuildDoesNotImplyStoredArtifact() {
        var d=new io.github.devmrko.companion.port.BuildHistory.Detail("run","build","SUCCEEDED","",List.of(new io.github.devmrko.companion.port.BuildHistory.Step("build","SUCCEEDED","BUILD")));
        assertEquals("SUCCEEDED",d.buildState());assertEquals("기록 없음",d.storageState());assertTrue(d.artifacts().isEmpty());
    }
}
