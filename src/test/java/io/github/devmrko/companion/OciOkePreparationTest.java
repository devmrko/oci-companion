package io.github.devmrko.companion.adapter;
import com.oracle.bmc.containerengine.*;
import com.oracle.bmc.containerengine.model.*;
import com.oracle.bmc.containerengine.responses.*;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePreparation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciOkePreparationTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 @Test void crossCompartmentClusterCannotReadNodesOrNetwork() throws Exception {
  var adapter=spy(new OciOkePreparation(mock(OciBuildHistory.class)));var engine=mock(ContainerEngineClient.class);doReturn(engine).when(adapter).engine(c,"region");when(engine.getCluster(any())).thenReturn(GetClusterResponse.builder().cluster(Cluster.builder().compartmentId("other").build()).build());
  assertThrows(RuntimeException.class,()->adapter.read(c,"region","comp","cluster"));verify(engine,never()).getPaginators();verify(adapter,never()).network(any(),any());
 }
 @Test void privateEnvironmentRejectsUnknownSubnetBeforeWrite() throws Exception {
  var clients=mock(OciBuildHistory.class);var adapter=spy(new OciOkePreparation(clients));doReturn(new OkePreparation.Detail(new OkePreparation.Cluster("cluster","test","ACTIVE","v1"),true,"vcn",List.of(),List.of(new OkePreparation.Network("allowed","subnet")),List.of(),List.of())).when(adapter).read(c,"region","comp","cluster");
  assertThrows(RuntimeException.class,()->adapter.create(c,"region","comp","cluster","p","name","desc","other",List.of(),"ticket"));verify(clients,never()).client(any(),any());
 }
 @Test void nativePrivateEnvironmentCarriesNetworkAndIdempotencyToken() throws Exception {
  var clients=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(devops);var adapter=spy(new OciOkePreparation(clients));
  doReturn(new OkePreparation.Detail(new OkePreparation.Cluster("cluster","test","ACTIVE","v1"),true,"vcn",List.of(),List.of(new OkePreparation.Network("subnet","subnet")),List.of(new OkePreparation.Network("nsg","nsg")),List.of())).when(adapter).read(c,"region","comp","cluster");
  when(devops.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder().compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
  when(devops.createDeployEnvironment(any())).thenReturn(CreateDeployEnvironmentResponse.builder().deployEnvironment(OkeClusterDeployEnvironment.builder().id("env").build()).build());
  assertEquals("env",adapter.create(c,"region","comp","cluster","p","name","desc","subnet",List.of("nsg"),"ticket"));
  verify(devops).createDeployEnvironment(argThat(r->{var d=(CreateOkeClusterDeployEnvironmentDetails)r.getCreateDeployEnvironmentDetails();var n=(PrivateEndpointChannel)d.getNetworkChannel();return r.getOpcRetryToken().equals("ticket")&&d.getClusterId().equals("cluster")&&n.getSubnetId().equals("subnet")&&n.getNsgIds().equals(List.of("nsg"));}));
 }
}
