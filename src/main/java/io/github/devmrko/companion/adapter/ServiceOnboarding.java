package io.github.devmrko.companion.adapter;

import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component @org.springframework.context.annotation.Profile("!demo")
public class ServiceOnboarding {
 public record Selection(String source,String template,String cluster,String namespace,String name){}
 public record Plan(Selection selection,OperatingServices.Entry template,String yaml,String image,String version,PipelineIam.Check iam){}
 public record Progress(String id,Plan plan,String environment,String pipeline,String artifact,String stage,String run,String state,String error){}
 private final OperatingServices services;private final OkeWorkloadConnections connections;private final OkeRuntimeResources resources;private final OciBuildHistory clients;private final OciOkeDeploymentSetup setup;private final PipelineIam iam;private final OkeDeploymentInputs inputs;private final DeploymentOperations deployments;private final BuildSpecDraftStore store;
 private final JsonMapper json=JsonMapper.builder().build();
 public ServiceOnboarding(OperatingServices services,OkeWorkloadConnections connections,OkeRuntimeResources resources,OciBuildHistory clients,OciOkeDeploymentSetup setup,PipelineIam iam,OkeDeploymentInputs inputs,DeploymentOperations deployments,BuildSpecDraftStore store){this.services=services;this.connections=connections;this.resources=resources;this.clients=clients;this.setup=setup;this.iam=iam;this.inputs=inputs;this.deployments=deployments;this.store=store;}
 public List<OperatingServices.Entry> templates(DeploymentOperations.Context c){return services.list(c);}
 public List<OkeWorkloadConnections.Cluster> clusters(DeploymentOperations.Context c){return connections.clusters(c.connection(),c.region(),c.compartment());}
 public List<String> namespaces(DeploymentOperations.Context c,String template,String cluster){var t=services.get(c,template);if(!t.cluster().equals(cluster))throw Errors.SCOPE.exception();return connections.namespaces(c.connection(),c.region(),c.compartment(),cluster,t.deployment().endpoint()).stream().filter(t.deployment().namespace()::equals).toList();}
 String key(DeploymentOperations.Context c,String id){if(c.connection().demo()||!id.matches("[a-f0-9-]{36}"))throw Errors.SCOPE.exception();return String.join("\n","service-onboarding-v1",c.connection().profile(),c.connection().tenancy(),c.connection().user(),c.region(),c.compartment(),id);}
 void save(DeploymentOperations.Context c,Progress p){store.save(key(c,p.id()),new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(p),Instant.now().toString()));}
 public Progress read(DeploymentOperations.Context c,String id){var p=store.read(key(c,id)).map(d->json.readValue(d.content(),Progress.class)).orElseThrow(Errors.MISSING::exception);if(p.state().equals("NEEDS_ATTENTION")&&!p.environment().isBlank()&&p.pipeline().isBlank()&&p.error().startsWith("OCI 변경 작업 완료 대기 시간 초과"))return new Progress(p.id(),p.plan(),p.environment(),p.pipeline(),p.artifact(),p.stage(),p.run(),"ENVIRONMENT_PENDING",p.error());return p;}
 public Plan preview(DeploymentOperations.Context c,Selection selection){
  var t=services.get(c,selection.template());var i=t.deployment();var r=t.registration();
  if(!r.source().equals(selection.source())||!t.cluster().equals(selection.cluster())||!i.namespace().equals(selection.namespace()))throw Errors.SCOPE.exception();
  OciOkeWorkloadConnections.name(selection.name());if(selection.name().length()>63)throw Errors.INVALID.exception();if(services.list(c).stream().anyMatch(e->e.registration().name().equals(selection.name())))throw Errors.CONFLICT.exception();
  var names=connections.deployments(c.connection(),c.region(),c.compartment(),t.cluster(),i.endpoint(),i.namespace());if(names.contains(selection.name()))throw Errors.CONFLICT.exception();
  var snapshot=resources.read(c.connection(),c.region(),c.compartment(),r.environment(),i.endpoint(),i.namespace());
  var serviceNames=snapshot.resources().get("services");if(serviceNames==null||!serviceNames.available())throw Errors.DEPLOYMENT_RUNTIME.exception();if(serviceNames.items().stream().anyMatch(s->s.name().equals(selection.name())))throw Errors.CONFLICT.exception();
  String live=resources.deployment(c.connection(),c.region(),c.compartment(),r.environment(),i.endpoint(),i.namespace(),i.app());
  var cloned=OnboardingManifest.clone(live,i.namespace(),i.app(),selection.name());
  var check=iam.inspect(c.connection(),c.region(),c.compartment(),r.pipeline());
  return new Plan(selection,t,cloned.yaml(),cloned.image(),ExistingOkeDeployment.version(live),check);
 }
 private OkeDeploymentSetup.Input input(Plan p,String environment){
  var v=(ObjectNode)json.valueToTree(p.template().deployment());v.put("environment",environment);v.put("name",p.selection().name());v.put("app",p.selection().name());v.put("serviceName",p.selection().name());v.put("serviceType","ExistingDeployment");v.put("createNamespace",false);v.put("nodePort",0);return json.treeToValue(v,OkeDeploymentSetup.Input.class);
 }
 static boolean samePlan(Plan a,Plan b){
  if(!a.selection().equals(b.selection())||!a.template().equals(b.template())||!a.image().equals(b.image())||!a.version().equals(b.version())||!a.iam().equals(b.iam()))return false;
  var loader=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));var left=new ArrayList<Object>();var right=new ArrayList<Object>();loader.loadAll(a.yaml()).forEach(left::add);loader.loadAll(b.yaml()).forEach(right::add);return left.equals(right);
 }
 public synchronized Progress create(DeploymentOperations.Context c,Plan approved,String token){
  if(!token.matches("[a-f0-9-]{36}"))throw Errors.INVALID.exception();if(store.read(key(c,token)).isPresent())throw Errors.CONFLICT.exception();
  var fresh=preview(c,approved.selection());if(!samePlan(fresh,approved))throw Errors.BUILD_CHANGED.exception();
  if(!approved.iam().ready())throw new IllegalStateException("IAM 점검을 완료하고 빠진 권한을 확인하세요. 정책은 자동 생성하지 않습니다.");
  Progress p=new Progress(token,approved,"","","","","","CREATING","");save(c,p);return proceed(c,p);
 }
 public synchronized Progress resume(DeploymentOperations.Context c,String id){
  var p=read(c,id);if(!p.state().equals("ENVIRONMENT_PENDING")||p.environment().isBlank()||!p.pipeline().isBlank())throw Errors.CONFLICT.exception();
  if(!samePlan(preview(c,p.plan().selection()),p.plan()))throw Errors.BUILD_CHANGED.exception();
  return proceed(c,p);
 }
 private Progress proceed(DeploymentOperations.Context c,Progress p){
  String token=p.id();var approved=p.plan();
  try(var d=clients.client(c.connection(),c.region())){
   var old=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(approved.template().registration().environment()).build()).getDeployEnvironment();
   if(!(old instanceof OkeClusterDeployEnvironment env)||!env.getClusterId().equals(approved.selection().cluster())||!c.compartment().equals(env.getCompartmentId()))throw Errors.SCOPE.exception();
   if(p.environment().isBlank()){
   var request=CreateOkeClusterDeployEnvironmentDetails.builder().clusterId(env.getClusterId()).projectId(env.getProjectId()).displayName(approved.selection().name()+"-oke");
   if(env.getNetworkChannel() instanceof PrivateEndpointChannel channel)request.networkChannel(PrivateEndpointChannel.builder().subnetId(channel.getSubnetId()).nsgIds(channel.getNsgIds()).build());
   var created=d.createDeployEnvironment(CreateDeployEnvironmentRequest.builder().opcRetryToken(token).createDeployEnvironmentDetails(request.build()).build());
   p=new Progress(token,approved,created.getDeployEnvironment().getId(),"","","","","CREATING","");save(c,p);OciDevopsWorkRequests.await(d,created.getOpcWorkRequestId(),System::nanoTime,Thread::sleep,180_000_000_000L);
   }else{
    var ready=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(p.environment()).build()).getDeployEnvironment();
    if(!(ready instanceof OkeClusterDeployEnvironment target)||!c.compartment().equals(ready.getCompartmentId())||!env.getProjectId().equals(ready.getProjectId())||!target.getClusterId().equals(env.getClusterId())||!ready.getDisplayName().equals(approved.selection().name()+"-oke")||ready.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw new IllegalStateException("환경이 아직 준비되지 않았습니다. 완료 후 다시 확인하세요.");
   }
   var input=input(approved,p.environment());inputs.save(c.connection(),c.region(),c.compartment(),input);
   var result=setup.createReviewed(c.connection(),c.region(),new OkeDeploymentSetup.Preview(input,env.getProjectId(),approved.selection().name()+"-oke",approved.image(),approved.yaml(),token.replace("-","")),token);
   p=new Progress(token,approved,p.environment(),result.pipeline(),result.specification(),result.stage(),"","CREATING",result.error());save(c,p);if(!result.error().isBlank())throw new IllegalStateException(result.error());
   var check=iam.inspect(c.connection(),c.region(),c.compartment(),p.pipeline());if(!check.ready())throw new IllegalStateException("새 파이프라인 IAM 미충족 · 권한 점검 화면에서 빠진 문장을 확인하세요. 정책은 생성하지 않았습니다.");
   var prepared=deployments.prepare(c,new DeploymentOperations.Target(p.pipeline(),"",""));if(prepared.id().isBlank())throw Errors.DEPLOYMENT_RUNTIME.exception();
   var started=deployments.execute(c,prepared.id());p=new Progress(token,approved,p.environment(),p.pipeline(),p.artifact(),p.stage(),started.id(),"DEPLOYING","");save(c,p);return p;
  }catch(Exception e){p=new Progress(token,approved,p.environment(),p.pipeline(),p.artifact(),p.stage(),p.run(),(!p.environment().isBlank()&&p.pipeline().isBlank()&&(p.state().equals("ENVIRONMENT_PENDING")||Objects.toString(e.getMessage(),"").contains("완료 대기 시간 초과")))?"ENVIRONMENT_PENDING":"NEEDS_ATTENTION",PipelineIamRules.safe(e.getMessage()));save(c,p);return p;}
 }
 public DeploymentRuns.Run status(DeploymentOperations.Context c,Progress p){return p.run().isBlank()?null:deployments.status(c,p.pipeline(),p.run());}
 public synchronized Progress finish(DeploymentOperations.Context c,String id){
  var p=read(c,id);if(!p.state().equals("DEPLOYING"))return p;var result=status(c,p);if(result==null||!result.state().equals("SUCCEEDED"))return p;
  var r=p.plan().template().registration();services.register(c,new OperatingServices.Registration(p.plan().selection().name(),r.source(),r.branch(),r.builder(),p.environment(),p.pipeline(),r.dockerfile(),r.testCommand(),"",0));
  p=new Progress(p.id(),p.plan(),p.environment(),p.pipeline(),p.artifact(),p.stage(),p.run(),"REGISTERED","");save(c,p);return p;
 }
}
