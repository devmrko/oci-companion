package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BackupPolicies;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciBackupPolicies implements BackupPolicies {
 private final OciVolumeBackups clients;
 public OciBackupPolicies(OciBuildHistory clients){this.clients=new OciVolumeBackups(clients);}
 BlockstorageClient storage(Connection c,String region)throws Exception{return clients.storage(c,region);}
 private static String text(Object x){return x==null?"":x.toString();}
 private static String label(String value){return switch(value){case "ONE_DAY"->"매일";case "ONE_WEEK"->"매주";case "ONE_MONTH"->"매월";case "ONE_YEAR"->"매년";case "INCREMENTAL"->"증분";case "FULL"->"전체";case "REGIONAL_DATA_CENTER_TIME"->"리전 현지 시간";default->value;};}
 static Schedule schedule(VolumeBackupSchedule s){
  var fields=new ArrayList<String>();if(s.getOffsetType()!=null)fields.add(s.getOffsetType().getValue());
  if(s.getOffsetSeconds()!=null)fields.add("주기 시작 후 "+s.getOffsetSeconds()+"초");
  if(s.getMonth()!=null)fields.add(s.getMonth().getValue());if(s.getDayOfMonth()!=null)fields.add(s.getDayOfMonth()+"일");if(s.getDayOfWeek()!=null)fields.add(s.getDayOfWeek().getValue());if(s.getHourOfDay()!=null)fields.add(s.getHourOfDay()+"시");
  String retention=s.getRetentionSeconds()==null?"OCI 기본값":s.getRetentionSeconds()+"초";
  if(s.getRetentionSeconds()!=null&&s.getRetentionSeconds()%86400==0)retention=s.getRetentionSeconds()/86400+"일";
  if(s.getRetentionPeriod()!=null)retention=s.getRetentionPeriod().getRetentionTimeAmount()+" "+s.getRetentionPeriod().getRetentionTimeUnit();
  return new Schedule(s.getPeriod()==null?"":label(s.getPeriod().getValue()),s.getBackupType()==null?"":label(s.getBackupType().getValue()),String.join(" · ",fields),s.getTimeZone()==null?"OCI 기본값":label(s.getTimeZone().getValue()),retention,Boolean.TRUE.equals(s.getIsRetentionLockEnabled()),Boolean.TRUE.equals(s.getIsPreventDeletionEnabled()));
 }
 static Policy policy(VolumeBackupPolicy p){return new Policy(p.getId(),p.getDisplayName(),p.getCompartmentId()==null,text(p.getDestinationRegion()),p.getSchedules()==null?List.of():p.getSchedules().stream().map(OciBackupPolicies::schedule).toList());}
 public List<io.github.devmrko.companion.port.VolumeBackups.Volume> volumes(Connection c,String region,String comp){try(var x=storage(c,region)){var result=new ArrayList<io.github.devmrko.companion.port.VolumeBackups.Volume>();for(var v:x.getPaginators().listVolumesRecordIterator(ListVolumesRequest.builder().compartmentId(comp).build()))if(comp.equals(v.getCompartmentId()))result.add(new io.github.devmrko.companion.port.VolumeBackups.Volume(v.getId(),v.getDisplayName(),v.getLifecycleState().getValue(),v.getSizeInGBs(),v.getAvailabilityDomain()));return result;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public List<Policy> policies(Connection c,String region,String comp){try(var x=storage(c,region)){
  var result=new LinkedHashMap<String,Policy>();
  for(var p:x.getPaginators().listVolumeBackupPoliciesRecordIterator(ListVolumeBackupPoliciesRequest.builder().build()))if(p.getCompartmentId()==null)result.put(p.getId(),policy(p));
  for(var p:x.getPaginators().listVolumeBackupPoliciesRecordIterator(ListVolumeBackupPoliciesRequest.builder().compartmentId(comp).build()))if(comp.equals(p.getCompartmentId()))result.put(p.getId(),policy(p));
  return result.values().stream().sorted(Comparator.comparing(Policy::name,Comparator.nullsLast(String::compareTo))).toList();
 }catch(Exception e){throw Errors.CLOUD.exception();}}
 public State read(Connection c,String region,String comp,String volume){if(volume==null||volume.isBlank())throw Errors.BACKUP_INPUT.exception();try(var x=storage(c,region)){
  var response=x.getVolume(GetVolumeRequest.builder().volumeId(volume).build());var v=response.getVolume();if(!comp.equals(v.getCompartmentId()))throw Errors.SCOPE.exception();if(!"AVAILABLE".equals(v.getLifecycleState().getValue()))throw Errors.BUILD_CHANGED.exception();
  var assignments=x.getVolumeBackupPolicyAssetAssignment(GetVolumeBackupPolicyAssetAssignmentRequest.builder().assetId(volume).build()).getItems();if(assignments.size()>1)throw Errors.BUILD_CHANGED.exception();Assignment current=null;
  if(!assignments.isEmpty()){
   var r=x.getVolumeBackupPolicyAssignment(GetVolumeBackupPolicyAssignmentRequest.builder().policyAssignmentId(assignments.get(0).getId()).build());var a=r.getVolumeBackupPolicyAssignment();if(!a.getId().equals(assignments.get(0).getId())||!a.getAssetId().equals(volume)&&!a.getAssetId().equals(v.getVolumeGroupId()))throw Errors.BUILD_CHANGED.exception();current=new Assignment(a.getId(),a.getPolicyId(),r.getEtag(),text(a.getXrcKmsKeyId()));
  }
  return new State(volume,v.getDisplayName(),response.getEtag(),text(v.getVolumeGroupId()),current,policies(c,region,comp));
 }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Plan plan(Connection c,String region,String comp,String volume,String operation,String id){
  if(!"ASSIGN".equals(operation)&&!"REMOVE".equals(operation))throw Errors.BACKUP_INPUT.exception();var s=read(c,region,comp,volume);if(!s.group().isBlank())throw Errors.BACKUP_POLICY_GROUP.exception();
  if(s.etag()==null||s.etag().isBlank())throw Errors.BUILD_CHANGED.exception();
  // Unrelated policy changes should not invalidate a reviewed operation.
  var before=new State(s.volume(),s.name(),s.etag(),s.group(),s.current(),List.of());
  if("REMOVE".equals(operation)){
   if(id!=null&&!id.isBlank()||s.current()==null||s.current().etag()==null||s.current().etag().isBlank())throw Errors.BUILD_CHANGED.exception();return new Plan(operation,before,null,"");
  }
  var chosen=s.policies().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(Errors.SCOPE::exception);if(!chosen.destination().isBlank())throw Errors.BACKUP_POLICY_REGION.exception();if(s.current()!=null&&s.current().policy().equals(id))throw Errors.BACKUP_INPUT.exception();
  try(var x=storage(c,region)){var r=x.getVolumeBackupPolicy(GetVolumeBackupPolicyRequest.builder().policyId(id).build());var p=r.getVolumeBackupPolicy();if(p.getCompartmentId()!=null&&!comp.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();if(!policy(p).equals(chosen)||r.getEtag()==null||r.getEtag().isBlank())throw Errors.BUILD_CHANGED.exception();return new Plan(operation,before,chosen,r.getEtag());}catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public void apply(Connection c,String region,String comp,Plan p){
  var current=plan(c,region,comp,p.state().volume(),p.operation(),p.selected()==null?"":p.selected().id());if(!p.equals(current))throw Errors.BUILD_CHANGED.exception();
  try(var x=storage(c,region)){
   if("REMOVE".equals(p.operation()))x.deleteVolumeBackupPolicyAssignment(DeleteVolumeBackupPolicyAssignmentRequest.builder().policyAssignmentId(p.state().current().id()).ifMatch(p.state().current().etag()).build());
   else x.createVolumeBackupPolicyAssignment(CreateVolumeBackupPolicyAssignmentRequest.builder().createVolumeBackupPolicyAssignmentDetails(CreateVolumeBackupPolicyAssignmentDetails.builder().assetId(p.state().volume()).policyId(p.selected().id()).build()).build());
  }catch(Exception e){throw Errors.BACKUP_POLICY_UNKNOWN.exception();}
 }
}
