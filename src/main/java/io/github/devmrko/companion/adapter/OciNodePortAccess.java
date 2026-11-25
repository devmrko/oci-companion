package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import com.oracle.bmc.containerengine.requests.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.model.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component @org.springframework.context.annotation.Profile("!demo")
public class OciNodePortAccess implements NodePortAccess {
 private final OciOkePreparation clients;private final BuildSpecDraftStore backups;
 public OciNodePortAccess(OciOkePreparation clients,BuildSpecDraftStore backups){this.clients=clients;this.backups=backups;}
 public List<NodePortAccess.SecurityList> lists(Connection c,String region,String compartment,String cluster){
  try(var engine=clients.engine(c,region);var network=clients.network(c,region)){
   var actual=engine.getCluster(GetClusterRequest.builder().clusterId(cluster).build()).getCluster();if(!compartment.equals(actual.getCompartmentId()))throw Errors.SCOPE.exception();
   var ids=new LinkedHashSet<String>();
   for(var summary:engine.getPaginators().listNodePoolsRecordIterator(ListNodePoolsRequest.builder().compartmentId(compartment).clusterId(cluster).build())){
    var pool=engine.getNodePool(GetNodePoolRequest.builder().nodePoolId(summary.getId()).build()).getNodePool();if(!cluster.equals(pool.getClusterId())||!compartment.equals(pool.getCompartmentId()))throw Errors.SCOPE.exception();
    if(pool.getNodeConfigDetails()==null||pool.getNodeConfigDetails().getPlacementConfigs()==null)continue;
    for(var placement:pool.getNodeConfigDetails().getPlacementConfigs()){
     var subnet=network.getSubnet(GetSubnetRequest.builder().subnetId(placement.getSubnetId()).build()).getSubnet();if(!actual.getVcnId().equals(subnet.getVcnId()))throw Errors.SCOPE.exception();ids.addAll(subnet.getSecurityListIds());
    }
   }
   var result=new ArrayList<NodePortAccess.SecurityList>();for(String id:ids){var list=network.getSecurityList(GetSecurityListRequest.builder().securityListId(id).build()).getSecurityList();if(!actual.getVcnId().equals(list.getVcnId()))throw Errors.SCOPE.exception();result.add(new NodePortAccess.SecurityList(id,list.getDisplayName()));}return List.copyOf(result);
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 static void validate(String source,int port){
  if(source==null||!source.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}/32")||port<30000||port>32767)throw Errors.INVALID.exception();
  for(String octet:source.substring(0,source.length()-3).split("\\."))if(Integer.parseInt(octet)>255||(octet.length()>1&&octet.startsWith("0")))throw Errors.INVALID.exception();
 }
 public List<NodePortAccess.Rule> references(Connection c,String region,String compartment,String cluster,String list,int port){
  if(port<30000||port>32767||lists(c,region,compartment,cluster).stream().noneMatch(x->x.id().equals(list)))throw Errors.SCOPE.exception();
  try(var network=clients.network(c,region)){
   var rules=network.getSecurityList(GetSecurityListRequest.builder().securityListId(list).build()).getSecurityList().getIngressSecurityRules();
   var result=new ArrayList<NodePortAccess.Rule>();
   for(var rule:rules){var tcp=rule.getTcpOptions();var range=tcp==null?null:tcp.getDestinationPortRange();int min=range==null?1:range.getMin(),max=range==null?65535:range.getMax();
    if(("6".equals(rule.getProtocol())||"all".equals(rule.getProtocol()))&&port>=min&&port<=max)result.add(new NodePortAccess.Rule(rule.getSource(),rule.getProtocol(),min,max,Objects.toString(rule.getDescription(),"")));
   }return List.copyOf(result);
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 static String marker(String source,int port){return "OCI Companion NodePort "+source+" "+port;}
 static boolean owned(IngressSecurityRule rule,String source,int port){
  var tcp=rule.getTcpOptions();var range=tcp==null?null:tcp.getDestinationPortRange();
  return marker(source,port).equals(rule.getDescription())&&source.equals(rule.getSource())&&"6".equals(rule.getProtocol())&&rule.getSourceType()==IngressSecurityRule.SourceType.CidrBlock&&!Boolean.TRUE.equals(rule.getIsStateless())&&range!=null&&range.getMin()==port&&range.getMax()==port&&tcp.getSourcePortRange()==null;
 }
 static List<IngressSecurityRule> rules(List<IngressSecurityRule> before,String source,int port,boolean remove){
  validate(source,port);var result=new ArrayList<>(before);
  if(remove){if(result.stream().noneMatch(rule->owned(rule,source,port)))throw Errors.CONFLICT.exception();result.removeIf(rule->owned(rule,source,port));}
  else {if(result.stream().anyMatch(rule->owned(rule,source,port)))throw Errors.CONFLICT.exception();result.add(IngressSecurityRule.builder().source(source).sourceType(IngressSecurityRule.SourceType.CidrBlock).protocol("6").isStateless(false).description(marker(source,port)).tcpOptions(TcpOptions.builder().destinationPortRange(PortRange.builder().min(port).max(port).build()).build()).build());}
  return List.copyOf(result);
 }
 public Preview preview(Connection c,String region,String compartment,String cluster,String list,String source,int port,boolean remove){
  validate(source,port);if(lists(c,region,compartment,cluster).stream().noneMatch(x->x.id().equals(list)))throw Errors.SCOPE.exception();
  try(var network=clients.network(c,region)){
   var response=network.getSecurityList(GetSecurityListRequest.builder().securityListId(list).build());var value=response.getSecurityList();if(response.getEtag()==null||response.getEtag().isBlank())throw Errors.CONFLICT.exception();rules(value.getIngressSecurityRules(),source,port,remove);
   return new Preview(cluster,list,value.getDisplayName(),source,port,remove,response.getEtag(),value.getIngressSecurityRules().size(),marker(source,port));
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 public void execute(Connection c,String region,String compartment,Preview expected,String token){
  var current=preview(c,region,compartment,expected.cluster(),expected.list(),expected.source(),expected.port(),expected.remove());if(!current.equals(expected))throw Errors.CONFLICT.exception();
  try(var network=clients.network(c,region)){
   var response=network.getSecurityList(GetSecurityListRequest.builder().securityListId(expected.list()).build());if(!expected.etag().equals(response.getEtag()))throw Errors.CONFLICT.exception();
   var value=response.getSecurityList();var replacement=rules(value.getIngressSecurityRules(),expected.source(),expected.port(),expected.remove());
   backups.save(String.join("\n","nodeport-security-backup",c.profile(),c.tenancy(),c.user(),region,compartment,token),new BuildSpecDraftStore.Draft(expected.etag(),com.fasterxml.jackson.databind.json.JsonMapper.builder().build().setFilterProvider(new com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider().addFilter("explicitlySetFilter",com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter.serializeAll())).writeValueAsString(value),Instant.now().toString()));
   network.updateSecurityList(UpdateSecurityListRequest.builder().securityListId(expected.list()).ifMatch(expected.etag()).updateSecurityListDetails(UpdateSecurityListDetails.builder().ingressSecurityRules(replacement).build()).build());
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e)+" · 다시 실행하기 전에 현재 규칙을 확인하세요.");}
 }
}
