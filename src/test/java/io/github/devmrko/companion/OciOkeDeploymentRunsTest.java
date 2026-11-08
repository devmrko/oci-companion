package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import com.oracle.bmc.containerengine.*;
import com.oracle.bmc.containerengine.model.*;
import com.oracle.bmc.containerengine.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciOkeDeploymentRunsTest {
    final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
    @Test void clusterMustBeActiveInSelectedCompartment() throws Exception {
        var clients=mock(OciBuildHistory.class);var engine=mock(ContainerEngineClient.class);var adapter=spy(new OciDeploymentRuns(clients));
        doReturn(engine).when(adapter).clusters(c,"seoul");
        for(var cluster:List.of(Cluster.builder().compartmentId("other").lifecycleState(ClusterLifecycleState.Active).build(),Cluster.builder().compartmentId("comp").lifecycleState(ClusterLifecycleState.Creating).build())){
            when(engine.getCluster(any())).thenReturn(GetClusterResponse.builder().cluster(cluster).build());
            assertThrows(RuntimeException.class,()->adapter.validateCluster(c,"seoul","comp","cluster"));
        }
        when(engine.getCluster(any())).thenReturn(GetClusterResponse.builder().cluster(Cluster.builder().compartmentId("comp").lifecycleState(ClusterLifecycleState.Active).build()).build());
        assertDoesNotThrow(()->adapter.validateCluster(c,"seoul","comp","cluster"));
    }
    @Test void planBindsNamespaceClusterAndManifestBytes() throws Exception {
        var clients=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);var pages=mock(DevopsPaginators.class);var adapter=spy(new OciDeploymentRuns(clients));
        when(clients.client(c,"seoul")).thenReturn(devops);when(devops.getPaginators()).thenReturn(pages);
        when(devops.getDeployPipeline(any())).thenReturn(GetDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().id("pipeline").projectId("project").compartmentId("comp").lifecycleState(DeployPipeline.LifecycleState.Active).build()).build());
        when(pages.listDeployStagesRecordIterator(any())).thenReturn(List.of(OkeDeployStageSummary.builder().id("stage").build()));
        var stage=OkeDeployStage.builder().id("stage").projectId("project").deployPipelineId("pipeline").compartmentId("comp").lifecycleState(DeployStage.LifecycleState.Active).namespace("testing").okeClusterDeployEnvironmentId("env").kubernetesManifestDeployArtifactIds(List.of("artifact")).build();
        when(devops.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(stage).build());
        when(devops.getDeployEnvironment(any())).thenReturn(GetDeployEnvironmentResponse.builder().deployEnvironment(OkeClusterDeployEnvironment.builder().id("env").projectId("project").compartmentId("comp").clusterId("cluster").lifecycleState(DeployEnvironment.LifecycleState.Active).build()).build());
        doNothing().when(adapter).validateCluster(c,"seoul","comp","cluster");
        when(devops.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(OkeManifestsTest.artifact(OkeManifestsTest.YAML)).build());
        var plan=adapter.plan(c,"seoul","comp","pipeline");assertEquals("OKE",plan.targetType());assertEquals("cluster",plan.cluster());assertEquals("testing",plan.namespace());assertEquals(List.of(OkeManifestsTest.IMAGE),plan.images());
        when(devops.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(OkeManifestsTest.artifact(OkeManifestsTest.YAML.replace("name: app","name: changed"))).build());
        assertNotEquals(plan.fingerprint(),adapter.plan(c,"seoul","comp","pipeline").fingerprint());
        assertThrows(RuntimeException.class,()->adapter.start(c,"seoul","comp","pipeline",plan.fingerprint(),"ticket"));
        verify(devops,never()).createDeployment(any());
        when(devops.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(stage.toBuilder().namespace("other").build()).build());
        assertThrows(RuntimeException.class,()->adapter.plan(c,"seoul","comp","pipeline"));
    }
}
