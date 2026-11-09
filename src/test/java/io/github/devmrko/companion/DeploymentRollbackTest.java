package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeploymentRollbackTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 @Test void rollbackUsesSuccessfulPreviousRunAndSameEnvironment() throws Exception {
  var clients=mock(OciBuildHistory.class);var runs=mock(DeploymentRuns.class);var d=mock(DevopsClient.class);var pages=mock(DevopsPaginators.class);when(clients.client(c,"r")).thenReturn(d);when(d.getPaginators()).thenReturn(pages);var adapter=new OciDeploymentRollback(clients,runs);
  when(runs.plan(c,"r","comp","pipeline")).thenReturn(new DeploymentRuns.Plan("pipeline","app","env",List.of(),"current","OKE","cluster","namespace",List.of()));
  when(pages.listDeployStagesRecordIterator(any())).thenReturn(List.of(OkeDeployStageSummary.builder().id("stage").build()));when(d.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(OkeDeployStage.builder().id("stage").okeClusterDeployEnvironmentId("env").build()).build());
  var run=DeployPipelineDeployment.builder().id("old").displayName("v1").compartmentId("comp").deployPipelineId("pipeline").lifecycleState(Deployment.LifecycleState.Succeeded).deploymentExecutionProgress(DeploymentExecutionProgress.builder().deployStageExecutionProgress(Map.of("stage",OkeDeployStageExecutionProgress.builder().status(DeployStageExecutionProgress.Status.Succeeded).build())).build()).deployPipelineEnvironments(DeployPipelineEnvironmentCollection.builder().items(List.of(DeployPipelineEnvironment.builder().deployEnvironmentId("env").build())).build()).build();
  when(d.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(run).build());var plan=adapter.plan(c,"r","comp","pipeline","old");
  when(d.createDeployment(any())).thenReturn(CreateDeploymentResponse.builder().deployment(DeployPipelineDeployment.builder().id("restored").build()).build());assertEquals("restored",adapter.execute(c,"r","comp",plan,"token"));verify(d).createDeployment(argThat(x->x.getOpcRetryToken().equals("token")&&x.getCreateDeploymentDetails() instanceof CreateSingleDeployStageRedeploymentDetails v&&v.getPreviousDeploymentId().equals("old")&&v.getDeployStageId().equals("stage")));
  when(d.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(run.toBuilder().lifecycleState(Deployment.LifecycleState.Failed).build()).build());assertThrows(RuntimeException.class,()->adapter.plan(c,"r","comp","pipeline","old"));
  when(d.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(run.toBuilder().compartmentId("other").build()).build());assertThrows(RuntimeException.class,()->adapter.plan(c,"r","comp","pipeline","old"));
  when(d.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(run.toBuilder().deployPipelineEnvironments(DeployPipelineEnvironmentCollection.builder().items(List.of()).build()).build()).build());assertThrows(RuntimeException.class,()->adapter.plan(c,"r","comp","pipeline","old"));
  verify(d,times(1)).createDeployment(any());
 }
 @Test void stalePlanNeverStartsRollback() {
  var clients=mock(OciBuildHistory.class);var adapter=spy(new OciDeploymentRollback(clients,mock(DeploymentRuns.class)));var old=new DeploymentRollback.Plan("pipeline","run","v1","stage","env","OKE","old");doReturn(new DeploymentRollback.Plan("pipeline","run","v1","stage","env","OKE","changed")).when(adapter).plan(c,"r","comp","pipeline","run");assertThrows(RuntimeException.class,()->adapter.execute(c,"r","comp",old,"token"));verifyNoInteractions(clients);
 }
}
