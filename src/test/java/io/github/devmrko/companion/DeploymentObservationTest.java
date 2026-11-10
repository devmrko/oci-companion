package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeploymentObservationTest {
 final Connection c=new Connection("p","t","u","r","name",false);
 @Test void oldExecutionUsesReferencedEnvironmentInsteadOfCurrentPipeline()throws Exception{
  var client=mock(DevopsClient.class);var svc=spy(new OciDeploymentObservation("/unused"));doReturn(client).when(svc).client(c,"r");var old=mock(Deployment.class);when(old.getCompartmentId()).thenReturn("comp");when(old.getDeployPipelineId()).thenReturn("pipeline");when(old.getProjectId()).thenReturn("project");when(old.getDeployPipelineEnvironments()).thenReturn(DeployPipelineEnvironmentCollection.builder().items(List.of(DeployPipelineEnvironment.builder().deployEnvironmentId("historical-env").build())).build());when(client.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(old).build());when(client.getDeployEnvironment(any())).thenReturn(GetDeployEnvironmentResponse.builder().deployEnvironment(OkeClusterDeployEnvironment.builder().id("historical-env").displayName("env").compartmentId("comp").projectId("project").clusterId("cluster").build()).build());var result=svc.read(c,"r","comp","pipeline","run");assertEquals("cluster",result.targets().getFirst().cluster());verify(client).getDeployEnvironment(argThat(r->r.getDeployEnvironmentId().equals("historical-env")));verify(client,never()).getDeployPipeline(any());
 }
 @Test void crossCompartmentRunCannotReadEnvironments()throws Exception{var client=mock(DevopsClient.class);var svc=spy(new OciDeploymentObservation("/unused"));doReturn(client).when(svc).client(c,"r");var old=mock(Deployment.class);when(old.getCompartmentId()).thenReturn("other");when(client.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(old).build());assertThrows(IllegalArgumentException.class,()->svc.read(c,"r","comp","pipeline","run"));verify(client,never()).getDeployEnvironment(any());}
}
