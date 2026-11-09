package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.containerinstances.ContainerInstanceClient;
import com.oracle.bmc.containerinstances.model.*;
import com.oracle.bmc.containerinstances.requests.*;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.requests.GetSubnetRequest;
import com.oracle.bmc.core.requests.GetRouteTableRequest;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
@Component
public class OciContainerPreparation implements ContainerPreparation {
 private final OciBuildHistory clients;
 public OciContainerPreparation(OciBuildHistory clients){this.clients=clients;}
 private ClientConfiguration config(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
 ContainerInstanceClient client(Connection c,String r)throws Exception{var x=ContainerInstanceClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 VirtualNetworkClient network(Connection c,String r)throws Exception{var x=VirtualNetworkClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 IdentityClient identity(Connection c,String r)throws Exception{var x=IdentityClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 ArtifactsClient artifacts(Connection c,String r)throws Exception{var x=ArtifactsClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(r);return x;}
 public Operation planAction(Connection c,String r,String comp,String id,Action action){
  try(var client=client(c,r)){
   var response=client.getContainerInstance(GetContainerInstanceRequest.builder().containerInstanceId(id).build());var v=response.getContainerInstance();
   if(!comp.equals(v.getCompartmentId()))throw Errors.SCOPE.exception();
   if(action==null||!action.allowed(v.getLifecycleState().getValue()))throw Errors.INVALID.exception();
   if(response.getEtag()==null||response.getEtag().isBlank())throw Errors.BUILD_CHANGED.exception();
   return new Operation(id,v.getDisplayName(),v.getLifecycleState().getValue(),response.getEtag(),action);
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public void applyAction(Connection c,String r,String comp,Operation expected){
  if(!expected.equals(planAction(c,r,comp,expected.instance(),expected.action())))throw Errors.BUILD_CHANGED.exception();
  try(var client=client(c,r)){
   switch(expected.action()){
    case START -> client.startContainerInstance(StartContainerInstanceRequest.builder().containerInstanceId(expected.instance()).ifMatch(expected.etag()).build());
    case STOP -> client.stopContainerInstance(StopContainerInstanceRequest.builder().containerInstanceId(expected.instance()).ifMatch(expected.etag()).build());
    case RESTART -> client.restartContainerInstance(RestartContainerInstanceRequest.builder().containerInstanceId(expected.instance()).ifMatch(expected.etag()).build());
    case DELETE -> client.deleteContainerInstance(DeleteContainerInstanceRequest.builder().containerInstanceId(expected.instance()).ifMatch(expected.etag()).build());
   }
  }catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
 }
 public List<Instance> list(Connection c,String r,String comp){try(var client=client(c,r)){var result=new ArrayList<Instance>();for(var v:client.getPaginators().listContainerInstancesRecordIterator(ListContainerInstancesRequest.builder().compartmentId(comp).build())){var s=v.getShapeConfig();result.add(new Instance(v.getId(),v.getDisplayName(),v.getLifecycleState().getValue(),v.getShape(),s==null?"미확인":Objects.toString(s.getOcpus(),"미확인"),s==null?"미확인":Objects.toString(s.getMemoryInGBs(),"미확인")));}return result;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Detail read(Connection c,String r,String comp,String id){try(var client=client(c,r)){
  var v=client.getContainerInstance(GetContainerInstanceRequest.builder().containerInstanceId(id).build()).getContainerInstance();if(!comp.equals(v.getCompartmentId()))throw Errors.SCOPE.exception();var processes=new ArrayList<ContainerPreparation.Process>();
  if(v.getContainers()!=null)for(var ref:v.getContainers()){var p=client.getContainer(GetContainerRequest.builder().containerId(ref.getContainerId()).build()).getContainer();if(!comp.equals(p.getCompartmentId())||!id.equals(p.getContainerInstanceId()))throw Errors.SCOPE.exception();processes.add(new ContainerPreparation.Process(p.getId(),p.getDisplayName(),p.getLifecycleState().getValue(),Objects.toString(p.getLifecycleDetails(),"")+(p.getHealthChecks()==null?"":p.getHealthChecks().stream().map(h->" · "+h.getName()+": "+Objects.toString(h.getStatus(),"미확인")+" "+Objects.toString(h.getStatusDetails(),"")).collect(java.util.stream.Collectors.joining())),Objects.toString(p.getExitCode(),"—"),p.getImageUrl()));}
  var work=new ArrayList<Work>();
  try{for(var w:client.listWorkRequests(ListWorkRequestsRequest.builder().compartmentId(comp).resourceId(id).limit(5).sortOrder(SortOrder.Desc).build()).getWorkRequestSummaryCollection().getItems()){
   if(!comp.equals(w.getCompartmentId()))continue;String error="";
   if(w.getStatus()==OperationStatus.Failed)error=client.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(w.getId()).limit(10).build()).getWorkRequestErrorCollection().getItems().stream().map(x->x.getCode()+": "+x.getMessage()).collect(java.util.stream.Collectors.joining("\n"));
   work.add(new Work(w.getId(),w.getStatus().getValue(),Objects.toString(w.getPercentComplete(),"미확인"),error));
  }}catch(Exception e){work.add(new Work("","조회 실패","미확인","Work Request 읽기 권한·연결 확인"));}
  return new Detail(id,v.getDisplayName(),v.getLifecycleState().getValue(),Objects.toString(v.getLifecycleDetails(),""),List.copyOf(processes),List.copyOf(work));
 }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Logs logs(Connection c,String r,String comp,String instance,String container){var detail=read(c,r,comp,instance);if(detail.processes().stream().noneMatch(x->x.id().equals(container)))throw Errors.SCOPE.exception();try(var client=client(c,r);var stream=client.retrieveLogs(RetrieveLogsRequest.builder().containerId(container).isPrevious(false).build()).getInputStream()){byte[] data=stream.readNBytes(262145);return new Logs(new String(data,0,Math.min(data.length,262144),StandardCharsets.UTF_8),data.length>262144);}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Options options(Connection c,String r,String comp,String domain){try(var client=client(c,r);var identity=identity(c,r);var network=network(c,r)){
  var domains=identity.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder().compartmentId(comp).build()).getItems().stream().map(x->x.getName()).sorted().toList();if(!domain.isBlank()&&!domains.contains(domain))throw Errors.SCOPE.exception();
  var shapes=new ArrayList<Shape>();if(!domain.isBlank())for(var s:client.getPaginators().listContainerInstanceShapesRecordIterator(ListContainerInstanceShapesRequest.builder().compartmentId(comp).availabilityDomain(domain).build())){var o=s.getOcpuOptions();var m=s.getMemoryOptions();if(o!=null&&m!=null)shapes.add(new Shape(s.getName(),s.getProcessorDescription(),new VmSizing.Limits(o.getMin(),o.getMax(),m.getMinInGBs(),m.getMaxInGBs(),m.getMinPerOcpuInGBs(),m.getMaxPerOcpuInGBs())));}
  var subnets=new ArrayList<Subnet>();for(var s:network.getPaginators().listSubnetsRecordIterator(ListSubnetsRequest.builder().compartmentId(comp).build()))if("AVAILABLE".equals(s.getLifecycleState().getValue())&&(s.getAvailabilityDomain()==null||s.getAvailabilityDomain().equals(domain)))subnets.add(new Subnet(s.getId(),s.getDisplayName(),s.getVcnId(),Boolean.TRUE.equals(s.getProhibitPublicIpOnVnic())));
  return new Options(domains,List.copyOf(shapes),List.copyOf(subnets));
 }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
 static void validate(Input i){
  if(i.healthPath()==null||i.healthPort()<0||i.healthPort()>65535||(i.healthPort()>0&&(!i.healthPath().startsWith("/")||i.healthPath().length()>1024||i.healthPath().contains("\r")||i.healthPath().contains("\n")))||(i.healthPort()==0&&!i.healthPath().isBlank()))throw Errors.INVALID.exception();
  if(i.name()==null||!i.name().matches("[A-Za-z0-9][A-Za-z0-9-]{0,62}")||i.variables()==null||i.variables().size()>100||i.command()==null||i.arguments()==null||i.command().size()>100||i.arguments().size()>100)throw Errors.INVALID.exception();
  for(var e:i.variables().entrySet())if(!e.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")||e.getValue()==null||e.getValue().length()>4096)throw Errors.INVALID.exception();
  for(var line:java.util.stream.Stream.concat(i.command().stream(),i.arguments().stream()).toList())if(line==null||line.length()>4096||line.contains("\u0000"))throw Errors.INVALID.exception();
 }
 static CreateContainerInstanceDetails details(String comp,Input i,String imageUrl){
  return CreateContainerInstanceDetails.builder().compartmentId(comp).displayName(i.name()).availabilityDomain(i.domain()).shape(i.shape()).shapeConfig(CreateContainerInstanceShapeConfigDetails.builder().ocpus(i.cpu()).memoryInGBs(i.memory()).build())
   .vnics(List.of(CreateContainerVnicDetails.builder().subnetId(i.subnet()).isPublicIpAssigned(i.publicIp()).build()))
   .containers(List.of(CreateContainerDetails.builder().displayName(i.name()).imageUrl(imageUrl).environmentVariables(i.variables()).command(i.command().isEmpty()?null:i.command()).arguments(i.arguments().isEmpty()?null:i.arguments()).healthChecks(i.healthPort()==0?null:List.of(CreateContainerHttpHealthCheckDetails.builder().name("HTTP").port(i.healthPort()).path(i.healthPath()).initialDelayInSeconds(10).intervalInSeconds(15).timeoutInSeconds(5).successThreshold(1).failureThreshold(3).failureAction(ContainerHealthCheckFailureAction.None).build())).build()))
   .containerRestartPolicy(ContainerInstance.ContainerRestartPolicy.Always).freeformTags(Map.of("created-by","oci-companion")).build();
 }
 static boolean privateRegistryRoute(java.util.List<com.oracle.bmc.core.model.RouteRule> routes){return routes.stream().anyMatch(x->x.getNetworkEntityId()!=null&&((x.getNetworkEntityId().startsWith("ocid1.natgateway.")&&"0.0.0.0/0".equals(x.getDestination()))||(x.getNetworkEntityId().startsWith("ocid1.servicegateway.")&&Objects.toString(x.getDestination(),"").startsWith("all-")&&Objects.toString(x.getDestination(),"").contains("services-in-oracle-services-network"))));}
 public Preview preview(Connection c,String r,String comp,Input i){validate(i);var options=options(c,r,comp,i.domain());if(!options.domains().contains(i.domain()))throw Errors.SCOPE.exception();var shape=options.shapes().stream().filter(x->x.name().equals(i.shape())).findFirst().orElseThrow(Errors.SCOPE::exception);shape.limits().validate(i.cpu(),i.memory());var subnet=options.subnets().stream().filter(x->x.id().equals(i.subnet())).findFirst().orElseThrow(Errors.SCOPE::exception);if(i.publicIp()&&subnet.privateOnly())throw Errors.INVALID.exception();
  try(var net=network(c,r)){
   var selected=net.getSubnet(GetSubnetRequest.builder().subnetId(i.subnet()).build()).getSubnet();if(!comp.equals(selected.getCompartmentId()))throw Errors.SCOPE.exception();
   if(!i.publicIp()&&!privateRegistryRoute(net.getRouteTable(GetRouteTableRequest.builder().rtId(selected.getRouteTableId()).build()).getRouteTable().getRouteRules()))throw Errors.CONTAINER_NETWORK.exception();
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
  try(var registry=artifacts(c,r)){
   var response=registry.getContainerImage(GetContainerImageRequest.builder().imageId(i.image()).build());var image=response.getContainerImage();if(!comp.equals(image.getCompartmentId())||!"AVAILABLE".equals(image.getLifecycleState().getValue())||image.getDigest()==null||!image.getDigest().matches("sha256:[a-f0-9]{64}"))throw Errors.SCOPE.exception();
   var repo=registry.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(image.getRepositoryId()).build()).getContainerRepository();if(!comp.equals(repo.getCompartmentId())||!"AVAILABLE".equals(repo.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   String url=com.oracle.bmc.Region.fromRegionId(r).getRegionCode()+".ocir.io/"+repo.getNamespace()+"/"+repo.getDisplayName()+"@"+image.getDigest();
   String json=com.oracle.bmc.model.internal.JsonConverter.objectToJsonBlob(details(comp,i,url));
   String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((json+Objects.toString(response.getEtag(),"")+subnet.toString()+shape.toString()).getBytes(StandardCharsets.UTF_8)));
   return new Preview(i,url,json,fingerprint);
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public String create(Connection c,String r,String comp,Preview expected,String token){var current=preview(c,r,comp,expected.input());if(!current.fingerprint().equals(expected.fingerprint()))throw Errors.BUILD_CHANGED.exception();try(var client=client(c,r)){return client.createContainerInstance(CreateContainerInstanceRequest.builder().opcRetryToken(token).createContainerInstanceDetails(details(comp,current.input(),current.imageUrl())).build()).getContainerInstance().getId();}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}}
}
