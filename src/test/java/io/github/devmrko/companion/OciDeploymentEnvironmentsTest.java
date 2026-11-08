package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.responses.*;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciDeploymentEnvironmentsTest {
 final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
 @Test void createReferencesExistingVmAndRejectsOtherCompartmentBeforeMutation() throws Exception {
  var clients=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);var compute=mock(ComputeClient.class);when(clients.client(c,"region")).thenReturn(devops);
  var adapter=spy(new OciDeploymentEnvironments(clients));doReturn(compute).when(adapter).compute(c,"region");
  when(devops.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder().id("p").compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
  when(compute.getInstance(any())).thenReturn(GetInstanceResponse.builder().instance(Instance.builder().id("vm").compartmentId("other").lifecycleState(Instance.LifecycleState.Stopped).build()).build());
  assertThrows(RuntimeException.class,()->adapter.create(c,"region","comp","p","env","",List.of("vm"),"token"));verify(devops,never()).createDeployEnvironment(any());
  when(compute.getInstance(any())).thenReturn(GetInstanceResponse.builder().instance(Instance.builder().id("vm").compartmentId("comp").lifecycleState(Instance.LifecycleState.Stopped).build()).build());
  when(devops.createDeployEnvironment(any())).thenReturn(CreateDeployEnvironmentResponse.builder().deployEnvironment(ComputeInstanceGroupDeployEnvironment.builder().id("env").build()).build());
  assertEquals("env",adapter.create(c,"region","comp","p","env","",List.of("vm"),"token"));
  var capture=org.mockito.ArgumentCaptor.forClass(com.oracle.bmc.devops.requests.CreateDeployEnvironmentRequest.class);verify(devops).createDeployEnvironment(capture.capture());var details=(CreateComputeInstanceGroupDeployEnvironmentDetails)capture.getValue().getCreateDeployEnvironmentDetails();
  assertEquals(List.of("vm"),((ComputeInstanceGroupByIdsSelector)details.getComputeInstanceGroupSelectors().getItems().getFirst()).getComputeInstanceIds());assertEquals("token",capture.getValue().getOpcRetryToken());
  verify(compute,never()).instanceAction(any());verify(compute,never()).updateInstance(any());verify(compute,never()).launchInstance(any());
 }
 @Test void configuredAgentDoesNotClaimRuntimeOrNetworkReady() throws Exception {
  var clients=mock(OciBuildHistory.class);var compute=mock(ComputeClient.class);var adapter=spy(new OciDeploymentEnvironments(clients));doReturn(compute).when(adapter).compute(c,"region");
  when(compute.getInstance(any())).thenReturn(GetInstanceResponse.builder().instance(Instance.builder().id("vm").displayName("test").shape("shape").compartmentId("comp").imageId("image").lifecycleState(Instance.LifecycleState.Running).agentConfig(InstanceAgentConfig.builder().pluginsConfig(List.of(InstanceAgentPluginConfigDetails.builder().name("Compute Instance Run Command").desiredState(InstanceAgentPluginConfigDetails.DesiredState.Enabled).build())).build()).build()).build());
  when(compute.getImage(any())).thenReturn(GetImageResponse.builder().image(Image.builder().operatingSystem("Oracle Linux").operatingSystemVersion("9").build()).build());
  var result=adapter.inspect(c,"region","comp","vm");assertEquals(3,result.checks().stream().filter(x->x.state().equals("확인")).count());assertEquals(4,result.checks().stream().filter(x->x.state().equals("미확인")).count());
 }
 @Test void okeDetailMapsClusterAndRejectsForeignCompartment() throws Exception {
  var clients=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(devops);var adapter=new OciDeploymentEnvironments(clients);
  when(devops.getDeployEnvironment(any())).thenReturn(GetDeployEnvironmentResponse.builder().deployEnvironment(OkeClusterDeployEnvironment.builder().id("env").compartmentId("comp").clusterId("cluster").projectId("p").lifecycleState(DeployEnvironment.LifecycleState.Active).build()).build());
  var e=adapter.environment(c,"region","comp","env");assertEquals("OKE",e.type());assertEquals("cluster",e.clusterId());assertTrue(e.instances().isEmpty());assertThrows(RuntimeException.class,()->adapter.environment(c,"region","other","env"));
 }
}
