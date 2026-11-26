package io.github.devmrko.companion.adapter;
import com.oracle.bmc.identity.model.*;
import com.oracle.bmc.identity.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ActionPermissions;
import io.github.devmrko.companion.service.Readiness;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Positive proof only. Unrecognized policy syntax is not a denial. */
@Component @org.springframework.context.annotation.Profile("!demo")
public class OciActionPermissions implements ActionPermissions {
 private final OciPipelineIam clients;
 private final OciOkePreparation network;
 public OciActionPermissions(OciPipelineIam clients,OciOkePreparation network){this.clients=clients;this.network=network;}
 public Evidence securityList(Connection c,String region,String list){try(var client=network.network(c,region)){var value=client.getSecurityList(com.oracle.bmc.core.requests.GetSecurityListRequest.builder().securityListId(list).build()).getSecurityList();return inspect(c,value.getCompartmentId(),"security-lists");}catch(Exception e){return new Evidence(Readiness.Permission.UNKNOWN,OciPipelineIam.error(e),"Allow group <관리자 그룹> to manage security-lists in compartment id <보안 목록 구획 OCID>");}}

 public Evidence inspect(Connection c,String compartment,String resource){
  String guidance="Allow group <관리자 그룹> to manage "+resource+(compartment.equals(c.tenancy())?" in tenancy":" in compartment id <대상 구획 OCID>");
  if(c.demo())return new Evidence(Readiness.Permission.UNKNOWN,"데모에서는 권한을 확인하지 않습니다.",guidance);
  try(var client=clients.identity(c)){
   var groups=new ArrayList<Group>();
   for(var membership:client.getPaginators().listUserGroupMembershipsRecordIterator(ListUserGroupMembershipsRequest.builder().compartmentId(c.tenancy()).userId(c.user()).build())){
    if(membership.getLifecycleState()!=UserGroupMembership.LifecycleState.Active)continue;
    var group=client.getGroup(GetGroupRequest.builder().groupId(membership.getGroupId()).build()).getGroup();
    if(group.getLifecycleState()==Group.LifecycleState.Active)groups.add(group);
   }
   var scopes=new LinkedHashSet<String>();String current=compartment;
   while(scopes.add(current)&&!current.equals(c.tenancy()))current=client.getCompartment(GetCompartmentRequest.builder().compartmentId(current).build()).getCompartment().getCompartmentId();
   if(!scopes.contains(c.tenancy()))throw new IllegalStateException();
   for(String scope:scopes)for(var summary:client.getPaginators().listPoliciesRecordIterator(ListPoliciesRequest.builder().compartmentId(scope).build())){
    if(summary.getLifecycleState()!=Policy.LifecycleState.Active)continue;
    var policy=summary.getStatements()==null?client.getPolicy(GetPolicyRequest.builder().policyId(summary.getId()).build()).getPolicy():summary;
    for(String statement:policy.getStatements())for(var group:groups)
     if(grants(statement,group.getId(),group.getName(),group.getTimeCreated(),policy.getTimeCreated(),resource,scopes))return new Evidence(Readiness.Permission.CONFIRMED,"현재 그룹 멤버십과 조건 없는 관리 정책을 확인했습니다.",guidance);
   }
   return new Evidence(Readiness.Permission.UNKNOWN,"관리 권한을 확정할 정책 증거가 없습니다. 조건부·도메인 정책은 관리자 확인이 필요합니다.",guidance);
  }catch(Exception e){
   boolean denied=e instanceof com.oracle.bmc.model.BmcException b&&b.getStatusCode()==403&&"NotAuthorized".equals(b.getServiceCode());
   return new Evidence(denied?Readiness.Permission.DENIED:Readiness.Permission.UNKNOWN,OciPipelineIam.error(e),guidance);
  }
 }
 static boolean grants(String text,String id,String name,Date groupCreated,Date policyCreated,String resource,Set<String> scopes){
  var m=Pattern.compile("(?i)^\\s*allow\\s+group\\s+(.+?)\\s+to\\s+manage\\s+([a-z-]+)\\s+in\\s+(tenancy|compartment\\s+id\\s+[^\\s]+)\\s*$").matcher(text);
  if(!m.matches())return false;
  String granted=m.group(2).toLowerCase(Locale.ROOT);
  if(!granted.equals(resource)&&!granted.equals("all-resources")&&!(resource.equals("security-lists")&&granted.equals("virtual-network-family")))return false;
  String scope=m.group(3);if(!scope.equalsIgnoreCase("tenancy")&&!scopes.contains(scope.replaceFirst("(?i)^compartment\\s+id\\s+","")))return false;
  for(String subject:m.group(1).split(",")){
   subject=subject.trim();
   if(subject.equals("id "+id))return true;
   // Classic group names are immutable. A newer same-name group cannot inherit an older policy proof.
   if(groupCreated!=null&&policyCreated!=null&&!groupCreated.after(policyCreated)&&(subject.equals(name)||subject.equals("'"+name+"'")))return true;
  }
  return false;
 }
}
