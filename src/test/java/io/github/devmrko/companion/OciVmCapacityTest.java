package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.*;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciVmCapacityTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 @Test void wrongCompartmentCannotReadStorage() throws Exception {
  var adapter=spy(new OciVmCapacity(mock(OciBuildHistory.class)));var compute=mock(ComputeClient.class);doReturn(compute).when(adapter).compute(c,"seoul");when(compute.getInstance(any())).thenReturn(GetInstanceResponse.builder().instance(Instance.builder().compartmentId("other").build()).build());assertThrows(RuntimeException.class,()->adapter.read(c,"seoul","comp","vm"));verify(adapter,never()).storage(any(),any());
 }
 @Test void missingVolumePermissionDoesNotTurnCapacityIntoZero() throws Exception {
  var adapter=spy(new OciVmCapacity(mock(OciBuildHistory.class)));var compute=mock(ComputeClient.class);doReturn(compute).when(adapter).compute(c,"seoul");when(compute.getInstance(any())).thenReturn(GetInstanceResponse.builder().instance(Instance.builder().compartmentId("comp").displayName("test VM").lifecycleState(Instance.LifecycleState.Stopped).shape("Flex").shapeConfig(InstanceShapeConfig.builder().ocpus(1f).vcpus(2).memoryInGBs(8f).build()).build()).build());doThrow(new RuntimeException("forbidden")).when(adapter).storage(c,"seoul");var capacity=adapter.read(c,"seoul","comp","vm");assertEquals("1.0",capacity.ocpus());assertEquals("2",capacity.vcpus());assertEquals("8.0",capacity.memoryGb());assertEquals("미확인",capacity.localDiskGb());assertFalse(capacity.notices().isEmpty());
 }
}
