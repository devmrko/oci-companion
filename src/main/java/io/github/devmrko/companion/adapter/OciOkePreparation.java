package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.containerengine.ContainerEngineClient;
import com.oracle.bmc.containerengine.model.NodeSourceViaImageDetails;
import com.oracle.bmc.containerengine.requests.*;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePreparation;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciOkePreparation implements OkePreparation {
 private final OciBuildHistory clients;
 public OciOkePreparation(OciBuildHistory clients){this.clients=clients;}
 private ClientConfiguration config(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
 ContainerEngineClient engine(Connection c,String r)throws Exception{var x=ContainerEngineClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 VirtualNetworkClient network(Connection c,String r)throws Exception{var x=VirtualNetworkClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 private String value(Object v){return Objects.toString(v,"미확인");}
 private Cluster map(com.oracle.bmc.containerengine.model.Cluster v){return new Cluster(v.getId(),v.getName(),v.getLifecycleState().getValue(),v.getKubernetesVersion());}
 public List<Cluster> clusters(Connection c,String r,String comp){try(var client=engine(c,r)){var result=new ArrayList<Cluster>();for(var v:client.getPaginators().listClustersRecordIterator(ListClustersRequest.builder().compartmentId(comp).build()))result.add(new Cluster(v.getId(),v.getName(),v.getLifecycleState().getValue(),v.getKubernetesVersion()));result.sort(Comparator.comparing(Cluster::name));return result;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Detail read(Connection c,String r,String comp,String id){try(var client=engine(c,r)){
  var cluster=client.getCluster(GetClusterRequest.builder().clusterId(id).build()).getCluster();if(!comp.equals(cluster.getCompartmentId()))throw Errors.SCOPE.exception();
  boolean privateEndpoint=cluster.getEndpointConfig()==null||!Boolean.TRUE.equals(cluster.getEndpointConfig().getIsPublicIpEnabled());
  var pools=new ArrayList<Pool>();var subnets=new ArrayList<Network>();var nsgs=new ArrayList<Network>();var notices=new ArrayList<String>();
  try{for(var summary:client.getPaginators().listNodePoolsRecordIterator(ListNodePoolsRequest.builder().compartmentId(comp).clusterId(id).build())){
   var p=client.getNodePool(GetNodePoolRequest.builder().nodePoolId(summary.getId()).build()).getNodePool();if(!comp.equals(p.getCompartmentId())||!id.equals(p.getClusterId()))continue;
   var shape=p.getNodeShapeConfig();String boot=p.getNodeSourceDetails() instanceof NodeSourceViaImageDetails image?value(image.getBootVolumeSizeInGBs()):"미확인";
   pools.add(new Pool(p.getName(),value(p.getLifecycleState()),p.getNodeShape(),shape==null?"미확인":value(shape.getOcpus()),shape==null?"미확인":value(shape.getMemoryInGBs()),boot,p.getNodeConfigDetails()==null?"미확인":value(p.getNodeConfigDetails().getSize())));
  }}catch(Exception e){notices.add("노드 풀 조회 실패 · node-pools 읽기 권한 확인. 표시된 목록은 일부일 수 있습니다.");}
  if(privateEndpoint)try(var net=network(c,r)){
   for(var s:net.getPaginators().listSubnetsRecordIterator(ListSubnetsRequest.builder().compartmentId(comp).vcnId(cluster.getVcnId()).build()))if("AVAILABLE".equals(s.getLifecycleState().getValue())&&s.getIpv6CidrBlock()==null&&(s.getIpv6CidrBlocks()==null||s.getIpv6CidrBlocks().isEmpty()))subnets.add(new Network(s.getId(),s.getDisplayName()));
   for(var n:net.getPaginators().listNetworkSecurityGroupsRecordIterator(ListNetworkSecurityGroupsRequest.builder().compartmentId(comp).vcnId(cluster.getVcnId()).build()))if("AVAILABLE".equals(n.getLifecycleState().getValue()))nsgs.add(new Network(n.getId(),n.getDisplayName()));
  }catch(Exception e){notices.add("사설 접속 네트워크 조회 실패 · virtual-network-family 읽기 권한 확인");}
  return new Detail(map(cluster),privateEndpoint,cluster.getVcnId(),List.copyOf(pools),List.copyOf(subnets),List.copyOf(nsgs),List.copyOf(notices));
 }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public String create(Connection c,String r,String comp,String cluster,String project,String name,String description,String subnet,List<String> nsgs,String token){
  if(name==null||name.isBlank()||name.length()>255||description==null||description.length()>400||nsgs==null||nsgs.size()>5||new HashSet<>(nsgs).size()!=nsgs.size())throw Errors.INVALID.exception();
  var detail=read(c,r,comp,cluster);if(!"ACTIVE".equals(detail.cluster().state()))throw Errors.SCOPE.exception();
  if(detail.privateEndpoint()&&(detail.subnets().stream().noneMatch(x->x.id().equals(subnet))||!detail.nsgs().stream().map(Network::id).toList().containsAll(nsgs)))throw Errors.SCOPE.exception();
  if(!detail.privateEndpoint()&&(!subnet.isBlank()||!nsgs.isEmpty()))throw Errors.INVALID.exception();
  try(var devops=clients.client(c,r)){
   var p=devops.getProject(GetProjectRequest.builder().projectId(project).build()).getProject();if(!comp.equals(p.getCompartmentId())||p.getLifecycleState()!=Project.LifecycleState.Active)throw Errors.SCOPE.exception();
   var request=CreateOkeClusterDeployEnvironmentDetails.builder().clusterId(cluster).projectId(project).displayName(name.strip()).description(description.strip());
   if(detail.privateEndpoint())request.networkChannel(PrivateEndpointChannel.builder().subnetId(subnet).nsgIds(nsgs).build());
   try{return devops.createDeployEnvironment(CreateDeployEnvironmentRequest.builder().opcRetryToken(token).createDeployEnvironmentDetails(request.build()).build()).getDeployEnvironment().getId();}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
}
