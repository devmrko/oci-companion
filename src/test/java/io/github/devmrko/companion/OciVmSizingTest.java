package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VmSizing;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciVmSizingTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 final VmSizing.Snapshot before=new VmSizing.Snapshot("vm","test","STOPPED","Flex",1,8,"etag",new VmSizing.Limits(1,8,1,128,1,16),List.of(new VmSizing.Volume("boot","BOOT","boot",50,"vtag")));
 @Test void nativeCpuUpdateUsesEtagAndDoesNotStartVm() throws Exception {
  var adapter=spy(new OciVmSizing(mock(OciBuildHistory.class)));doReturn(before).when(adapter).read(c,"region","comp","vm");var compute=mock(ComputeClient.class);doReturn(compute).when(adapter).compute(c,"region");
  adapter.apply(c,"region","comp",before,new VmSizing.Change("",2,16,0));verify(compute).updateInstance(argThat(r->r.getIfMatch().equals("etag")&&r.getUpdateInstanceDetails().getShapeConfig().getMemoryInGBs()==16f));verify(compute,never()).instanceAction(any());
 }
 @Test void nativeVolumeUpdateUsesEtag() throws Exception {
  var adapter=spy(new OciVmSizing(mock(OciBuildHistory.class)));doReturn(before).when(adapter).read(c,"region","comp","vm");var storage=mock(BlockstorageClient.class);doReturn(storage).when(adapter).storage(c,"region");
  adapter.apply(c,"region","comp",before,new VmSizing.Change("boot",0,0,60));verify(storage).updateBootVolume(argThat(r->r.getIfMatch().equals("vtag")&&r.getBootVolumeId().equals("boot")&&r.getUpdateBootVolumeDetails().getSizeInGBs()==60));
 }
 @Test void staleSnapshotCannotMutateCloud() throws Exception {
  var adapter=spy(new OciVmSizing(mock(OciBuildHistory.class)));var after=new VmSizing.Snapshot("vm","test","RUNNING","Flex",1,8,"new-etag",before.limits(),before.volumes());doReturn(after).when(adapter).read(c,"region","comp","vm");
  assertThrows(RuntimeException.class,()->adapter.apply(c,"region","comp",before,new VmSizing.Change("",2,16,0)));verify(adapter,never()).compute(any(),any());verify(adapter,never()).storage(any(),any());
 }
}
