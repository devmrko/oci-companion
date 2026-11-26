package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import com.oracle.bmc.devops.requests.*;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component @Profile("!demo")
public class SourceServiceOnboarding {
 public record Request(String source,String context,String name,String branch,String builder,String repository,String dockerfile,String testCommand){}
 public record Draft(Request request,String commit,OperatingServices.Entry context,RepositoryDeploymentHints.Hints hints,List<String> pullSecrets){}
 public record Plan(Draft draft,OkeDeploymentSetup.Input input,Map<String,RepositoryDeploymentHints.Value> values,ArmBuilds.Plan build,PipelineIam.Check iam){}
 public record Progress(String id,Plan plan,String buildRun,String deployment,String state){}
 private final CatalogService catalog;private final SourceVersions versions;private final OperatingServices services;private final ArmBuilds builds;private final Registry registry;private final OciBuildHistory clients;private final OkeRuntimeResources resources;private final OkeWorkloadConnections connections;private final PipelineIam iam;private final OciOkeDeploymentSetup setup;private final ServiceOnboarding onboarding;private final BuildSpecDraftStore store;
 private final JsonMapper json=JsonMapper.builder().build();
 public SourceServiceOnboarding(CatalogService catalog,SourceVersions versions,OperatingServices services,ArmBuilds builds,Registry registry,OciBuildHistory clients,OkeRuntimeResources resources,OkeWorkloadConnections connections,PipelineIam iam,OciOkeDeploymentSetup setup,ServiceOnboarding onboarding,BuildSpecDraftStore store){this.catalog=catalog;this.versions=versions;this.services=services;this.builds=builds;this.registry=registry;this.clients=clients;this.resources=resources;this.connections=connections;this.iam=iam;this.setup=setup;this.onboarding=onboarding;this.store=store;}
 public List<Source> sources(DeploymentOperations.Context c){return catalog.sources(c.connection(),c.region(),c.compartment()).stream().filter(s->s.provider().equals("OCI")).toList();}
 public List<ArmBuilds.Builder> builders(DeploymentOperations.Context c){return builds.builders(c.connection(),c.region(),c.compartment());}
 public List<Registry.Repository> repositories(DeploymentOperations.Context c){return registry.list(c.connection(),c.region(),c.compartment(),"CONTAINER");}
 Source source(DeploymentOperations.Context c,String id){return sources(c).stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(Errors.SCOPE::exception);}
 Map<String,String> files(DeploymentOperations.Context c,Source src,String commit,String dockerfile){
  var files=new LinkedHashMap<String,String>();
  try(var client=clients.client(c.connection(),c.region())){
   var repo=client.getRepository(GetRepositoryRequest.builder().repositoryId(src.resourceId()).build()).getRepository();if(!c.compartment().equals(repo.getCompartmentId()))throw Errors.SCOPE.exception();
   try(var stream=client.getRepositoryArchiveContent(GetRepositoryArchiveContentRequest.builder().repositoryId(src.resourceId()).refName(commit).format("zip").build()).getInputStream();var zip=new ZipInputStream(stream)){
    long total=0;int count=0;for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
     if(++count>20000)throw Errors.SOURCE_READ.exception();if(entry.isDirectory())continue;
     String path=entry.getName();if(path.startsWith("/")||Arrays.asList(path.split("/")).contains(".."))throw Errors.SOURCE_READ.exception();
     // OCI archives may wrap all files in one repository directory.
     boolean selected=path.equals(dockerfile)||path.endsWith("/"+dockerfile)||path.matches(".*(?:\\.ya?ml|(?:application|bootstrap)[^/]*\\.properties)$");
     var out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int size=0;for(int n;(n=zip.read(buffer))!=-1;){total+=n;size+=n;if(total>80L*1024*1024)throw Errors.SOURCE_READ.exception();if(selected&&size<=262144)out.write(buffer,0,n);}
     if(selected&&size<=262144){if(files.size()>=200)throw Errors.SOURCE_READ.exception();files.put(path,out.toString(StandardCharsets.UTF_8));}
    }
   }
   String dockerPath=files.keySet().stream().filter(p->p.equals(dockerfile)||p.endsWith("/"+dockerfile)).min(Comparator.comparingInt(String::length)).orElseThrow(Errors.SOURCE_READ::exception);
   String prefix=dockerPath.substring(0,dockerPath.length()-dockerfile.length());var normalized=new TreeMap<String,String>();for(var e:files.entrySet())if(e.getKey().startsWith(prefix))normalized.put(e.getKey().substring(prefix.length()),e.getValue());return normalized;
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 public Draft inspect(DeploymentOperations.Context c,Request raw){
  var src=source(c,raw.source());var context=services.get(c,raw.context());OciOkeWorkloadConnections.name(raw.name());if(raw.name().length()>63)throw Errors.INVALID.exception();
  String branch=blank(raw.branch())?src.branch():raw.branch();String dockerfile=blank(raw.dockerfile())?"Dockerfile":raw.dockerfile();if(!dockerfile.matches("[A-Za-z0-9_][A-Za-z0-9_./-]{0,200}")||Arrays.asList(dockerfile.split("/")).contains(".."))throw Errors.INVALID.exception();
  if(blank(raw.testCommand()))throw Errors.INVALID.exception();var request=new Request(raw.source(),raw.context(),raw.name(),branch,raw.builder(),raw.repository(),dockerfile,raw.testCommand());
  var ref=versions.refs(c.connection(),c.region(),c.compartment(),src).stream().filter(r->r.name().equals(branch)).findFirst().orElseThrow(Errors.SOURCE_READ::exception);var commits=versions.commits(c.connection(),c.region(),c.compartment(),src,ref.name());if(commits.isEmpty())throw Errors.SOURCE_READ.exception();String commit=commits.getFirst().commit();if(!commit.matches("[a-f0-9]{40}"))throw Errors.SOURCE_READ.exception();
  var i=context.deployment();var snapshot=resources.read(c.connection(),c.region(),c.compartment(),context.registration().environment(),i.endpoint(),i.namespace());
  var listing=snapshot.resources().get("secrets");if(listing==null||!listing.available())throw Errors.DEPLOYMENT_RUNTIME.exception();
  var secrets=listing.items().stream().filter(r->"kubernetes.io/dockerconfigjson".equals(r.type())).map(OkeRuntimeResources.Resource::name).sorted().toList();
  return new Draft(request,commit,context,RepositoryDeploymentHints.read(files(c,src,commit,dockerfile),dockerfile),secrets);
 }
 static boolean blank(String value){return value==null||value.isBlank();}
 static Map<String,RepositoryDeploymentHints.Value> fill(Draft draft,Map<String,String> supplied){
  var values=new LinkedHashMap<>(draft.hints().values());
  for(String key:List.of("port","replicas","healthPath","pullSecret")){
   if(key.equals("pullSecret")&&values.containsKey(key)&&!draft.pullSecrets().contains(values.get(key).value()))values.remove(key);
   if(!values.containsKey(key)){String value=supplied.getOrDefault(key,"");if(blank(value))throw Errors.DEPLOYMENT_INPUT.exception();RepositoryDeploymentHints.put(values,key,value,"화면 입력");}
   if(!values.containsKey(key))throw Errors.DEPLOYMENT_INPUT.exception();
  }
  if(!draft.pullSecrets().contains(values.get("pullSecret").value()))throw Errors.SCOPE.exception();return Map.copyOf(values);
 }
 OkeDeploymentSetup.Input input(Draft d,Map<String,RepositoryDeploymentHints.Value> values,String image){
  var old=d.context().deployment();var r=d.request();var i=new OkeDeploymentSetup.Input(old.environment(),r.repository(),image,r.name(),"",old.namespace(),r.name(),"arm64",Integer.parseInt(values.get("replicas").value()),150,256,Integer.parseInt(values.get("port").value()),values.get("healthPath").value(),"",values.get("pullSecret").value(),"","",false);
  var tree=(ObjectNode)json.valueToTree(i);tree.put("endpoint",old.endpoint());tree.put("createNamespace",false);tree.put("automountServiceAccountToken",false);return json.treeToValue(tree,OkeDeploymentSetup.Input.class);
 }
 void vacant(DeploymentOperations.Context c,Draft d){var i=d.context().deployment();String name=d.request().name();if(services.list(c).stream().anyMatch(e->e.registration().name().equals(name))||connections.deployments(c.connection(),c.region(),c.compartment(),d.context().cluster(),i.endpoint(),i.namespace()).contains(name))throw Errors.CONFLICT.exception();var listing=resources.read(c.connection(),c.region(),c.compartment(),i.environment(),i.endpoint(),i.namespace()).resources().get("services");if(listing==null||!listing.available())throw Errors.DEPLOYMENT_RUNTIME.exception();if(listing.items().stream().anyMatch(x->x.name().equals(name)))throw Errors.CONFLICT.exception();}
 public Plan preview(DeploymentOperations.Context c,Draft draft,Map<String,String> supplied){
  var fresh=inspect(c,draft.request());if(!fresh.equals(draft))throw Errors.BUILD_CHANGED.exception();vacant(c,draft);var values=fill(draft,supplied);var r=draft.request();var src=source(c,r.source());
  var build=builds.preview(c.connection(),c.region(),c.compartment(),new ArmBuilds.Input(r.builder(),src.resourceId(),draft.commit(),r.repository(),r.name()+"-"+UUID.randomUUID().toString().substring(0,8),r.dockerfile(),r.testCommand()));
  var i=input(draft,values,"");OkeDeploymentManifest.render(i,"example.ocir.io/ns/image@sha256:"+"0".repeat(64));
  var check=iam.inspect(c.connection(),c.region(),c.compartment(),draft.context().registration().pipeline());return new Plan(draft,i,values,build,check);
 }
 String key(DeploymentOperations.Context c,String id){if(!id.matches("[a-f0-9-]{36}"))throw Errors.INVALID.exception();return String.join("\n","source-onboarding-v1",c.connection().profile(),c.connection().tenancy(),c.connection().user(),c.region(),c.compartment(),id);}
 void save(DeploymentOperations.Context c,Progress p){store.save(key(c,p.id()),new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(p),Instant.now().toString()));}
 public Progress read(DeploymentOperations.Context c,String id){return store.read(key(c,id)).map(d->json.readValue(d.content(),Progress.class)).orElseThrow(Errors.MISSING::exception);}
 public synchronized Progress registered(DeploymentOperations.Context c,String id){
  var p=read(c,id);if(!p.deployment().isBlank()&&onboarding.read(c,p.deployment()).state().equals("REGISTERED")){p=new Progress(p.id(),p.plan(),p.buildRun(),p.deployment(),"REGISTERED");save(c,p);}return p;
 }
 public ArmBuilds.Run buildStatus(DeploymentOperations.Context c,Progress p){return builds.read(c.connection(),c.region(),c.compartment(),p.buildRun());}
 public synchronized Progress start(DeploymentOperations.Context c,Plan approved,String token){
  if(store.read(key(c,token)).isPresent())throw Errors.CONFLICT.exception();if(!inspect(c,approved.draft().request()).equals(approved.draft()))throw Errors.BUILD_CHANGED.exception();vacant(c,approved.draft());if(!approved.iam().ready()||!iam.inspect(c.connection(),c.region(),c.compartment(),approved.draft().context().registration().pipeline()).ready())throw Errors.SCOPE.exception();
  // Persist consumption before sending any build request; an unknown response is not retried.
  save(c,new Progress(token,approved,"","","STARTING"));String run=builds.start(c.connection(),c.region(),c.compartment(),approved.build(),"",new char[0]);var p=new Progress(token,approved,run,"","BUILDING");save(c,p);return p;
 }
 public OkeDeploymentSetup.Preview deploymentPreview(DeploymentOperations.Context c,Progress p){
  if(!p.state().equals("BUILDING"))throw Errors.CONFLICT.exception();var run=buildStatus(c,p);if(!run.state().equals("SUCCEEDED")||!run.repository().equals(p.plan().input().repository())||!run.digest().matches("sha256:[a-f0-9]{64}"))throw Errors.HANDOFF.exception();
  var d=p.plan().draft();if(!services.get(c,d.request().context()).equals(d.context()))throw Errors.BUILD_CHANGED.exception();vacant(c,d);
  Registry.Artifact found=null;String next="";for(int n=0;n<10;n++){var page=registry.artifacts(c.connection(),c.region(),c.compartment(),"CONTAINER",run.repository(),next);found=page.items().stream().filter(x->x.state().equals("AVAILABLE")&&x.digest().equals(run.digest())).findFirst().orElse(null);if(found!=null||page.next().isBlank())break;next=page.next();}if(found==null)throw Errors.HANDOFF.exception();
  var preview=setup.preview(c.connection(),c.region(),c.compartment(),input(d,p.plan().values(),found.id()));if(preview.runtime()!=null&&!preview.runtime().allowed())throw Errors.DEPLOYMENT_RUNTIME.exception();if(!preview.imageUrl().endsWith("@"+run.digest()))throw Errors.HANDOFF.exception();return preview;
 }
 public synchronized ServiceOnboarding.Progress resume(DeploymentOperations.Context c,String id){
  var p=read(c,id);if(!p.state().equals("DEPLOYING")||!id.equals(p.deployment()))throw Errors.CONFLICT.exception();var d=p.plan().draft();
  if(!services.get(c,d.request().context()).equals(d.context()))throw Errors.BUILD_CHANGED.exception();vacant(c,d);
  if(!iam.inspect(c.connection(),c.region(),c.compartment(),d.context().registration().pipeline()).ready())throw Errors.SCOPE.exception();
  return onboarding.resumePrepared(c,id);
 }
 public synchronized ServiceOnboarding.Progress deploy(DeploymentOperations.Context c,String id,OkeDeploymentSetup.Preview approved){
  var p=read(c,id);var fresh=deploymentPreview(c,p);if(!fresh.fingerprint().equals(approved.fingerprint()))throw Errors.BUILD_CHANGED.exception();var d=p.plan().draft();var r=d.request();var base=d.context();
  var registration=new OperatingServices.Registration(r.name(),r.source(),r.branch(),r.builder(),base.registration().environment(),base.registration().pipeline(),r.dockerfile(),r.testCommand(),"",0);
  var context=new OperatingServices.Entry(registration,fresh.input(),base.connection(),base.cluster(),"",List.of(),base.revision());
  var selection=new ServiceOnboarding.Selection(r.source(),r.context(),base.cluster(),fresh.input().namespace(),r.name());var check=iam.inspect(c.connection(),c.region(),c.compartment(),base.registration().pipeline());
  var plan=new ServiceOnboarding.Plan(selection,context,fresh.yaml(),fresh.imageUrl(),d.commit(),check);
  if(!check.ready())throw Errors.SCOPE.exception();save(c,new Progress(id,p.plan(),p.buildRun(),id,"DEPLOYING"));return onboarding.createPrepared(c,plan,id);
 }
}
