package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VolumeBackups.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class OciVolumeBackupsTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 OciVolumeBackups adapter(BlockstorageClient x)throws Exception{var a=spy(new OciVolumeBackups(mock(OciBuildHistory.class)));doReturn(x).when(a).storage(c,"region");doReturn(List.of("ad")).when(a).domains(c,"region","comp");return a;}
 @Test void fullBackupUsesNativeTypeAndRetryToken()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);var input=new Input("BACKUP","volume","backup","FULL","");when(x.getVolume(any())).thenReturn(GetVolumeResponse.builder().etag("etag").volume(com.oracle.bmc.core.model.Volume.builder().id("volume").compartmentId("comp").displayName("disk").sizeInGBs(50L).lifecycleState(com.oracle.bmc.core.model.Volume.LifecycleState.Available).build()).build());when(x.createVolumeBackup(any())).thenReturn(CreateVolumeBackupResponse.builder().volumeBackup(VolumeBackup.builder().id("backup-id").build()).build());var p=a.plan(c,"region","comp",input);verify(x,never()).createVolumeBackup(any());assertEquals("backup-id",a.apply(c,"region","comp",p,"retry"));verify(x).createVolumeBackup(argThat(r->r.getOpcRetryToken().equals("retry")&&r.getCreateVolumeBackupDetails().getType()==CreateVolumeBackupDetails.Type.Full&&r.getCreateVolumeBackupDetails().getVolumeId().equals("volume")));
 }
 @Test void restoreCreatesSeparateVolumeAndRejectsStaleBackup()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);var i=new Input("RESTORE","backup","restored","","ad");var b=VolumeBackup.builder().id("backup").compartmentId("comp").displayName("snapshot").sizeInGBs(50L).lifecycleState(VolumeBackup.LifecycleState.Available).build();when(x.getVolumeBackup(any())).thenReturn(GetVolumeBackupResponse.builder().etag("first").volumeBackup(b).build());when(x.createVolume(any())).thenReturn(CreateVolumeResponse.builder().volume(com.oracle.bmc.core.model.Volume.builder().id("new-volume").build()).build());var p=a.plan(c,"region","comp",i);assertEquals("new-volume",a.apply(c,"region","comp",p,"retry"));verify(x).createVolume(argThat(r->r.getOpcRetryToken().equals("retry")&&r.getCreateVolumeDetails().getAvailabilityDomain().equals("ad")&&((VolumeSourceFromVolumeBackupDetails)r.getCreateVolumeDetails().getSourceDetails()).getId().equals("backup")&&r.getCreateVolumeDetails().getSizeInGBs()==50));verify(x,never()).updateVolume(any());when(x.getVolumeBackup(any())).thenReturn(GetVolumeBackupResponse.builder().etag("changed").volumeBackup(b).build());assertThrows(RuntimeException.class,()->a.apply(c,"region","comp",p,"retry2"));verify(x,times(1)).createVolume(any());
 }
 @Test void scopeStateAndDomainAreChecked()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);when(x.getVolumeBackup(any())).thenReturn(GetVolumeBackupResponse.builder().etag("x").volumeBackup(VolumeBackup.builder().compartmentId("other").lifecycleState(VolumeBackup.LifecycleState.Available).build()).build());assertThrows(RuntimeException.class,()->a.plan(c,"region","comp",new Input("RESTORE","b","n","","ad")));assertThrows(RuntimeException.class,()->a.plan(c,"region","comp",new Input("RESTORE","b","n","","other-ad")));when(x.getVolumeBackup(any())).thenReturn(GetVolumeBackupResponse.builder().etag("x").volumeBackup(VolumeBackup.builder().compartmentId("comp").lifecycleState(VolumeBackup.LifecycleState.Creating).build()).build());assertThrows(RuntimeException.class,()->a.plan(c,"region","comp",new Input("RESTORE","b","n","","ad")));verify(x,never()).createVolume(any());
 }
 @Test void invalidInputDoesNotCreateResources()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);for(var i:List.of(new Input("DELETE","b","n","",""),new Input("BACKUP","b","n","INVALID",""),new Input("BACKUP","b","\n","FULL",""),new Input("BACKUP","b","n",null,"")))assertThrows(RuntimeException.class,()->a.plan(c,"region","comp",i));verify(x,never()).createVolumeBackup(any());verify(x,never()).createVolume(any());
 }
}
