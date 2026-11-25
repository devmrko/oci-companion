package io.github.devmrko.companion.service;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Saved bindings and guided handoff; native build/deployment services retain execution ownership. */
@Service
public class OperatingServices {
 public record Registration(String name,String source,String branch,String builder,String environment,String pipeline,String dockerfile,String testCommand,String securityList,int nodePort){}
 public record Entry(Registration registration,OkeDeploymentSetup.Input deployment,String connection,String cluster,String securityListName,List<NodePortAccess.Rule> rules,String revision){}
 public record BuildPlan(Entry service,String commit,ArmBuilds.Plan build){}
 public record Release(String id,Entry service,String commit,String buildRun,String deploymentRun,String state,String error){}
 public record Status(Release release,ArmBuilds.Run build,DeploymentRuns.Run deployment){}
 public record DeployPlan(Release release,OkeDeploymentSetup.Preview preview,String pipelineFingerprint){}
 private final BuildSpecDraftStore store;private final OkeDeploymentInputs inputs;private final OkeDeploymentSetup setup;private final OkeWorkloadConnections connections;private final DeploymentEnvironments environments;private final CatalogService catalog;private final SourceVersions versions;private final ArmBuilds builds;private final Registry registry;private final NodePortAccess access;private final OkeRuntimeResources resources;private final DeploymentOperations deployments;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 public OperatingServices(BuildSpecDraftStore store,OkeDeploymentInputs inputs,OkeDeploymentSetup setup,OkeWorkloadConnections connections,DeploymentEnvironments environments,CatalogService catalog,SourceVersions versions,ArmBuilds builds,Registry registry,NodePortAccess access,OkeRuntimeResources resources,DeploymentOperations deployments){this.store=store;this.inputs=inputs;this.setup=setup;this.connections=connections;this.environments=environments;this.catalog=catalog;this.versions=versions;this.builds=builds;this.registry=registry;this.access=access;this.resources=resources;this.deployments=deployments;}
 private String key(DeploymentOperations.Context c){if(c.connection().demo())throw Errors.SCOPE.exception();return String.join("\n","operating-services-v1",c.connection().profile(),c.connection().tenancy(),c.connection().user(),c.region(),c.compartment());}
 private void save(String key,Object value){store.save(key,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(value),Instant.now().toString()));}
 public List<Entry> list(DeploymentOperations.Context c){return store.read(key(c)).map(d->List.of(json.readValue(d.content(),Entry[].class))).orElse(List.of());}
 public List<Release> recent(DeploymentOperations.Context c,String name){
  get(c,name);
  return store.read(key(c)+"\nrecent\n"+name).map(d->List.of(json.readValue(d.content(),String[].class))).orElse(List.of()).stream().map(id->release(c,id)).toList();
 }
 private synchronized void remember(DeploymentOperations.Context c,Release release){
  String name=release.service().registration().name();
  if(list(c).stream().noneMatch(e->e.registration().name().equals(name)))return;
  var ids=new ArrayList<>(recent(c,name).stream().map(Release::id).toList());
  ids.remove(release.id());ids.add(0,release.id());save(key(c)+"\nrecent\n"+name,ids.stream().limit(20).toList());
 }
 public OkeDeploymentSetup.Input restoreConfiguration(DeploymentOperations.Context c,String name){
  var input=get(c,name).deployment();inputs.save(c.connection(),c.region(),c.compartment(),input);return input;
 }
 public synchronized void remove(DeploymentOperations.Context c,String name){
  get(c,name);save(key(c),list(c).stream().filter(e->!e.registration().name().equals(name)).toList());
  store.delete(key(c)+"\nrecent\n"+name);
 }
 public Entry get(DeploymentOperations.Context c,String name){return list(c).stream().filter(e->e.registration().name().equals(name)).findFirst().orElseThrow(Errors.MISSING::exception);}
 private Source source(DeploymentOperations.Context c,String id){return catalog.sources(c.connection(),c.region(),c.compartment()).stream().filter(s->s.id().equals(id)&&s.provider().equals("OCI")).findFirst().orElseThrow(Errors.SCOPE::exception);}
 public List<SourceVersions.Ref> commits(DeploymentOperations.Context c,Entry e){return versions.commits(c.connection(),c.region(),c.compartment(),source(c,e.registration().source()),e.registration().branch());}
 private void target(DeploymentOperations.Context c,Entry e,DeploymentRuns.Plan p){if(p==null||!p.targetType().equals("OKE")||!e.cluster().equals(p.cluster())||!e.deployment().namespace().equals(p.namespace()))throw Errors.SCOPE.exception();}
 public synchronized Entry register(DeploymentOperations.Context c,Registration r){
  if(r.name()==null||!r.name().matches("[a-z0-9][a-z0-9-]{0,62}")||r.branch()==null||r.branch().isBlank()||r.branch().length()>255)throw Errors.INVALID.exception();
  var actor=c.connection();var i=inputs.read(actor,c.region(),c.compartment(),r.environment()).orElseThrow(Errors.DEPLOYMENT_INPUT::exception);
  var environment=environments.environment(actor,c.region(),c.compartment(),r.environment());if(!environment.type().equals("OKE")||!environment.state().equals("ACTIVE"))throw Errors.SCOPE.exception();
  var src=source(c,r.source());if(versions.refs(actor,c.region(),c.compartment(),src).stream().noneMatch(x->x.name().equals(r.branch())))throw Errors.SOURCE_READ.exception();
  if(builds.builders(actor,c.region(),c.compartment()).stream().noneMatch(b->b.name().equals(r.builder())))throw Errors.MISSING.exception();
  registry.read(actor,c.region(),c.compartment(),"CONTAINER",i.repository());
  String listName="";List<NodePortAccess.Rule> rules=List.of();
  if(r.nodePort()!=0){
   var ports=resources.nodePorts(actor,c.region(),c.compartment(),r.environment(),i.endpoint(),i.namespace());
   if(!ports.available()||ports.ports().stream().noneMatch(p->p.namespace().equals(i.namespace())&&p.service().equals(i.serviceName())&&p.port()==r.nodePort()))throw Errors.DEPLOYMENT_RUNTIME.exception();
   if(r.securityList()!=null&&!r.securityList().isBlank()){var chosen=access.lists(actor,c.region(),c.compartment(),environment.clusterId()).stream().filter(x->x.id().equals(r.securityList())).findFirst().orElseThrow(Errors.SCOPE::exception);listName=chosen.name();rules=access.references(actor,c.region(),c.compartment(),environment.clusterId(),chosen.id(),r.nodePort());}
  }else if(r.securityList()!=null&&!r.securityList().isBlank())throw Errors.INVALID.exception();
  var entry=new Entry(r,i,"",environment.clusterId(),listName,rules,UUID.randomUUID().toString());
  target(c,entry,deployments.inspect(c,new DeploymentOperations.Target(r.pipeline(),"","")).plan());
  String connection=connections.save(actor,c.region(),c.compartment(),new OkeWorkloadConnections.Settings("",r.name(),"운영 서비스",environment.clusterId(),i.endpoint(),i.namespace(),i.app(),true));
  entry=new Entry(r,i,connection,environment.clusterId(),listName,rules,entry.revision());var entries=new ArrayList<>(list(c));entries.removeIf(x->x.registration().name().equals(r.name()));if(entries.size()>=100)throw Errors.INVALID.exception();entries.add(entry);save(key(c),entries);return entry;
 }
 public BuildPlan prepareBuild(DeploymentOperations.Context c,String name,String commit){if(commit==null||!commit.matches("[a-f0-9]{40}"))throw Errors.INVALID.exception();var e=get(c,name);var r=e.registration();var src=source(c,r.source());var plan=builds.preview(c.connection(),c.region(),c.compartment(),new ArmBuilds.Input(r.builder(),src.resourceId(),commit,e.deployment().repository(),name+"-"+commit.substring(0,Math.min(12,commit.length()))+"-"+UUID.randomUUID().toString().substring(0,8),r.dockerfile(),r.testCommand()));return new BuildPlan(e,commit,plan);}
 public synchronized Release build(DeploymentOperations.Context c,BuildPlan plan){
  if(!get(c,plan.service().registration().name()).equals(plan.service()))throw Errors.BUILD_CHANGED.exception();
  String run=builds.start(c.connection(),c.region(),c.compartment(),plan.build(),"",new char[0]);var release=new Release(UUID.randomUUID().toString(),plan.service(),plan.commit(),run,"","BUILDING","");save(key(c)+"\n"+release.id(),release);remember(c,release);return release;
 }
 public Release release(DeploymentOperations.Context c,String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw Errors.INVALID.exception();return store.read(key(c)+"\n"+id).map(d->json.readValue(d.content(),Release.class)).orElseThrow(Errors.MISSING::exception);}
 public Status status(DeploymentOperations.Context c,String id){var r=release(c,id);remember(c,r);var build=builds.read(c.connection(),c.region(),c.compartment(),r.buildRun());var deployment=r.deploymentRun().isBlank()?null:deployments.status(c,r.service().registration().pipeline(),r.deploymentRun());return new Status(r,build,bindBuildDigest(deployment,build.digest(),r.service().deployment().namespace(),r.service().deployment().app()));}
 static DeploymentRuns.Run bindBuildDigest(DeploymentRuns.Run run,String digest,String namespace,String app){
  if(run==null||!run.cloudState().equals("SUCCEEDED"))return run;
  var images=run.images().stream().map(i->i.workload().equals(namespace+"/"+app)&&i.container().equals(app)?new DeploymentImageVerification.Image(i.workload(),i.pod(),i.container(),digest,i.actual(),i.ready()):i).toList();
  var target=images.stream().filter(i->i.workload().equals(namespace+"/"+app)&&i.container().equals(app)).toList();
  if(!target.isEmpty()&&target.stream().anyMatch(i->!digest.equals(i.actual())))return new DeploymentRuns.Run(run.id(),run.name(),"FAILED","실패: 이미지 불일치 · 빌드 결과와 실제 Pod imageID가 다릅니다.",run.steps(),run.cloudState(),images);
  if(run.state().equals("SUCCEEDED")&&(target.isEmpty()||!digest.matches("sha256:[a-f0-9]{64}")))return new DeploymentRuns.Run(run.id(),run.name(),"UNVERIFIED","빌드 digest와 실제 Pod 비교 기록이 없어 성공을 확인하지 못했습니다.",run.steps(),run.cloudState(),images);
  return new DeploymentRuns.Run(run.id(),run.name(),run.state(),run.detail(),run.steps(),run.cloudState(),images);
 }
 public DeployPlan prepareDeploy(DeploymentOperations.Context c,String id){
  var status=status(c,id);var r=status.release();if(!r.deploymentRun().isBlank()||!r.state().equals("BUILDING")||!status.build().state().equals("SUCCEEDED")||!get(c,r.service().registration().name()).equals(r.service()))throw Errors.BUILD_CHANGED.exception();
  var image=status.build();if(!image.repository().equals(r.service().deployment().repository())||!image.digest().matches("sha256:[a-f0-9]{64}"))throw Errors.HANDOFF.exception();
  Registry.Artifact found=null;String next="";
  for(int page=0;page<10;page++){var result=registry.artifacts(c.connection(),c.region(),c.compartment(),"CONTAINER",image.repository(),next);found=result.items().stream().filter(x->x.state().equals("AVAILABLE")&&x.digest().equals(image.digest())).findFirst().orElse(null);if(found!=null||result.next().isBlank())break;next=result.next();}
  if(found==null)throw Errors.HANDOFF.exception();
  var values=(tools.jackson.databind.node.ObjectNode)json.valueToTree(r.service().deployment());values.put("image",found.id());values.put("serviceType","ExistingDeployment");values.put("createNamespace",false);
  var input=json.treeToValue(values,OkeDeploymentSetup.Input.class);var preview=setup.preview(c.connection(),c.region(),c.compartment(),input);
  if(!preview.imageUrl().endsWith("@"+image.digest())||(preview.runtime()!=null&&!preview.runtime().allowed()))throw Errors.HANDOFF.exception();
  var nativePlan=deployments.inspect(c,new DeploymentOperations.Target(r.service().registration().pipeline(),"","")).plan();target(c,r.service(),nativePlan);return new DeployPlan(r,preview,nativePlan.fingerprint());
 }
 public synchronized Release deploy(DeploymentOperations.Context c,DeployPlan plan){
  var r=release(c,plan.release().id());if(!r.equals(plan.release())||!get(c,r.service().registration().name()).equals(r.service()))throw Errors.BUILD_CHANGED.exception();
  // A consumed attempt is not retried automatically after an ambiguous cloud response.
  save(key(c)+"\n"+r.id(),new Release(r.id(),r.service(),r.commit(),r.buildRun(),"","DEPLOYING",""));
  try{
   setup.update(c.connection(),c.region(),c.compartment(),r.service().registration().pipeline(),plan.pipelineFingerprint(),plan.preview());
   var prepared=deployments.prepare(c,new DeploymentOperations.Target(r.service().registration().pipeline(),"",""));target(c,r.service(),prepared.assessment().plan());
   if(prepared.id().isBlank()||!prepared.assessment().plan().images().equals(List.of(plan.preview().imageUrl())))throw Errors.HANDOFF.exception();
   var started=deployments.execute(c,prepared.id());var result=new Release(r.id(),r.service(),r.commit(),r.buildRun(),started.id(),"DEPLOYED","");save(key(c)+"\n"+r.id(),result);return result;
  }catch(Exception e){save(key(c)+"\n"+r.id(),new Release(r.id(),r.service(),r.commit(),r.buildRun(),"","UNKNOWN",PipelineIamRules.safe(e.getMessage())));throw e;}
 }
}
