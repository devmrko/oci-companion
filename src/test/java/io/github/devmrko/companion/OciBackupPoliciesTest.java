package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BackupPolicies.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class OciBackupPoliciesTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 OciBackupPolicies adapter(BlockstorageClient x)throws Exception{var a=spy(new OciBackupPolicies(mock(OciBuildHistory.class)));doReturn(x).when(a).storage(c,"region");return a;}
 Policy policy(){return new Policy("p","bronze",true,"",List.of());}
 State state(Assignment current){return new State("v","disk","v1","",current,List.of(policy()));}
 @Test void nativeAssignmentIsReadOnlyUntilApplyAndRejectsStalePolicy()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);doReturn(state(null)).when(a).read(c,"region","comp","v");var nativePolicy=VolumeBackupPolicy.builder().id("p").displayName("bronze").schedules(List.of()).build();when(x.getVolumeBackupPolicy(any())).thenReturn(GetVolumeBackupPolicyResponse.builder().volumeBackupPolicy(nativePolicy).etag("p1").build());var p=a.plan(c,"region","comp","v","ASSIGN","p");verify(x,never()).createVolumeBackupPolicyAssignment(any());a.apply(c,"region","comp",p);verify(x).createVolumeBackupPolicyAssignment(argThat(r->r.getCreateVolumeBackupPolicyAssignmentDetails().getAssetId().equals("v")&&r.getCreateVolumeBackupPolicyAssignmentDetails().getPolicyId().equals("p")));
  when(x.getVolumeBackupPolicy(any())).thenReturn(GetVolumeBackupPolicyResponse.builder().volumeBackupPolicy(nativePolicy).etag("p2").build());assertThrows(RuntimeException.class,()->a.apply(c,"region","comp",p));verify(x,times(1)).createVolumeBackupPolicyAssignment(any());
 }
 @Test void removalUsesExactAssignmentAndEtagAndRejectsReplacement()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);doReturn(state(new Assignment("a","p","a1",""))).when(a).read(c,"region","comp","v");var p=a.plan(c,"region","comp","v","REMOVE","");a.apply(c,"region","comp",p);verify(x).deleteVolumeBackupPolicyAssignment(argThat(r->r.getPolicyAssignmentId().equals("a")&&r.getIfMatch().equals("a1")));doReturn(state(new Assignment("b","p","b1",""))).when(a).read(c,"region","comp","v");assertThrows(RuntimeException.class,()->a.apply(c,"region","comp",p));verify(x,times(1)).deleteVolumeBackupPolicyAssignment(any());verify(x,never()).deleteVolume(any());verify(x,never()).deleteVolumeBackup(any());verify(x,never()).deleteVolumeBackupPolicy(any());
 }
 @Test void groupsCrossRegionSamePolicyAndUnknownPoliciesCannotBeAssigned()throws Exception{
  var x=mock(BlockstorageClient.class);var a=adapter(x);doReturn(state(null)).when(a).read(c,"region","comp","v");assertThrows(RuntimeException.class,()->a.plan(c,"region","comp","v","ASSIGN","other"));assertThrows(RuntimeException.class,()->a.plan(c,"region","comp","v","REMOVE",""));doReturn(state(new Assignment("a","p","e",""))).when(a).read(c,"region","comp","v");assertThrows(RuntimeException.class,()->a.plan(c,"region","comp","v","ASSIGN","p"));doReturn(new State("v","disk","e","group",null,List.of(policy()))).when(a).read(c,"region","comp","v");assertThrows(RuntimeException.class,()->a.plan(c,"region","comp","v","ASSIGN","p"));doReturn(new State("v","disk","e","",null,List.of(new Policy("p","cross",false,"other-region",List.of())))).when(a).read(c,"region","comp","v");assertThrows(RuntimeException.class,()->a.plan(c,"region","comp","v","ASSIGN","p"));verify(x,never()).createVolumeBackupPolicyAssignment(any());
 }
 @Test void scheduleKeepsNativeRetentionAndProtection(){var s=OciBackupPolicies.schedule(VolumeBackupSchedule.builder().period(VolumeBackupSchedule.Period.OneDay).backupType(VolumeBackupSchedule.BackupType.Incremental).retentionSeconds(604800).isRetentionLockEnabled(true).isPreventDeletionEnabled(true).build());assertEquals("7일",s.retention());assertTrue(s.locked());assertTrue(s.protectedFromDeletion());assertEquals("매일",s.period());}
}
