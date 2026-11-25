package io.github.devmrko.companion.service;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OperatingServicesTest {
 final String digest="sha256:"+"a".repeat(64),image="r.ocir.io/ns/app@"+digest,commit="b".repeat(40);
 final DeploymentOperations.Context context=new DeploymentOperations.Context(new Connection("profile","tenant","user","r","label",false),"r","comp","session");
 final Map<String,BuildSpecDraftStore.Draft> records=new HashMap<>();
 final BuildSpecDraftStore store=new BuildSpecDraftStore(){public Optional<Draft> read(String k){return Optional.ofNullable(records.get(k));}public void save(String k,Draft d){records.put(k,d);}public void delete(String k){records.remove(k);}};
 final OkeDeploymentInputs inputs=mock(OkeDeploymentInputs.class);final OkeDeploymentSetup setup=mock(OkeDeploymentSetup.class);final OkeWorkloadConnections connections=mock(OkeWorkloadConnections.class);final DeploymentEnvironments environments=mock(DeploymentEnvironments.class);final CatalogService catalog=mock(CatalogService.class);final SourceVersions versions=mock(SourceVersions.class);final ArmBuilds builds=mock(ArmBuilds.class);final Registry registry=mock(Registry.class);final NodePortAccess access=mock(NodePortAccess.class);final OkeRuntimeResources resources=mock(OkeRuntimeResources.class);final DeploymentOperations operations=mock(DeploymentOperations.class);
 OperatingServices service;OperatingServices.Registration registration;
 DeploymentRuns.Plan deploymentPlan(){return new DeploymentRuns.Plan("pipeline","pipeline","env",List.of(),"fp","OKE","cluster","apps",List.of(image));}
 DeploymentOperations.Assessment assessment(){return new DeploymentOperations.Assessment(new DeploymentOperations.Target("pipeline","",""),Instant.now(),deploymentPlan(),null,List.of(),List.of(),null,List.of());}
 @BeforeEach void init(){
  service=new OperatingServices(store,inputs,setup,connections,environments,catalog,versions,builds,registry,access,resources,operations);
  registration=new OperatingServices.Registration("app","source","main","arm","env","pipeline","Dockerfile","test-command","",0);
  var input=new OkeDeploymentSetup.Input("env","repo","image","app","description","apps","app","arm64",2,100,512,8080,"/readyz","","pull","","",true);
  when(inputs.read(any(),any(),any(),any())).thenReturn(Optional.of(input));when(environments.environment(any(),any(),any(),any())).thenReturn(new DeploymentEnvironments.Environment("env","env","","ACTIVE","project",List.of(),List.of(),"OKE","cluster"));
  when(catalog.sources(any(),any(),any())).thenReturn(List.of(new Source("source","OCI","source","url","main","native-repo")));when(versions.refs(any(),any(),any(),any())).thenReturn(List.of(new SourceVersions.Ref("branch","main",commit)));
  var builder=new ArmBuilds.Builder("arm","host","user","/key");when(builds.builders(any(),any(),any())).thenReturn(List.of(builder));when(builds.preview(any(),any(),any(),any())).thenAnswer(a->new ArmBuilds.Plan(a.getArgument(3),image,"source","arm",builder));when(builds.start(any(),any(),any(),any(),any(),any())).thenReturn("arm-run");
  when(builds.read(any(),any(),any(),any())).thenReturn(new ArmBuilds.Run("arm-run","SUCCEEDED","complete",image,"repo",digest,"","",""));
  when(connections.save(any(),any(),any(),any())).thenReturn("connection");when(operations.inspect(any(),any())).thenReturn(assessment());when(operations.prepare(any(),any())).thenReturn(new DeploymentOperations.Prepared("prepared",Instant.now().plusSeconds(600),assessment()));when(operations.execute(any(),eq("prepared"))).thenReturn(new DeploymentOperations.Started("deployment-run",new DeploymentOperations.Target("pipeline","","")));
  when(registry.artifacts(any(),any(),any(),any(),any(),any())).thenReturn(new Registry.Page(List.of(new Registry.Artifact("new-image","app","tag",digest,"1","AVAILABLE","")),""));
  when(setup.preview(any(),any(),any(),any())).thenAnswer(a->new OkeDeploymentSetup.Preview(a.getArgument(3),"project","env",image,"yaml","fp"));
  service.register(context,registration);
 }
 @Test void pinnedBuildFlowsThroughExistingPreflightAndOneUseDeployment(){
  var planned=service.prepareBuild(context,"app",commit);var run=service.build(context,planned);var deploy=service.prepareDeploy(context,run.id());
  assertEquals("new-image",deploy.preview().input().image());assertEquals("ExistingDeployment",deploy.preview().input().serviceType());assertFalse(deploy.preview().input().createNamespace());
  var result=service.deploy(context,deploy);assertEquals("deployment-run",result.deploymentRun());
  var ordered=inOrder(setup,operations);ordered.verify(setup).update(any(),any(),any(),eq("pipeline"),eq("fp"),eq(deploy.preview()));ordered.verify(operations).prepare(any(),any());ordered.verify(operations).execute(any(),eq("prepared"));
  assertThrows(RuntimeException.class,()->service.deploy(context,deploy));verify(setup,times(1)).update(any(),any(),any(),any(),any(),any());verifyNoInteractions(access);
 }
 @Test void ownerScopeAndChangedRegistrationInvalidatePlans(){
  var plan=service.prepareBuild(context,"app",commit);service.register(context,registration);assertThrows(RuntimeException.class,()->service.build(context,plan));
  var other=new DeploymentOperations.Context(new Connection("profile","tenant","other","r","label",false),"r","comp","session");assertTrue(service.list(other).isEmpty());verify(builds,never()).start(any(),any(),any(),any(),any(),any());
 }
 @Test void failedOrMismatchedBuildNeverUpdatesDeployment(){
  var run=service.build(context,service.prepareBuild(context,"app",commit));
  when(builds.read(any(),any(),any(),any())).thenReturn(new ArmBuilds.Run("arm-run","FAILED","test",image,"repo","","error","",""));assertThrows(RuntimeException.class,()->service.prepareDeploy(context,run.id()));
  when(builds.read(any(),any(),any(),any())).thenReturn(new ArmBuilds.Run("arm-run","SUCCEEDED","complete",image,"other-repo",digest,"","",""));assertThrows(RuntimeException.class,()->service.prepareDeploy(context,run.id()));verify(setup,never()).update(any(),any(),any(),any(),any(),any());
 }
 @Test void uncertainMutationIsRecordedAndCannotBeAutomaticallyRetried(){
  var run=service.build(context,service.prepareBuild(context,"app",commit));var plan=service.prepareDeploy(context,run.id());doThrow(new IllegalStateException("response lost")).when(setup).update(any(),any(),any(),any(),any(),any());
  assertThrows(IllegalStateException.class,()->service.deploy(context,plan));assertEquals("UNKNOWN",service.release(context,run.id()).state());assertThrows(RuntimeException.class,()->service.prepareDeploy(context,run.id()));verify(operations,never()).execute(any(),any());
 }
 @Test void incorrectNodePortReferenceCannotBeRegistered(){
  when(resources.nodePorts(any(),any(),any(),any(),any(),any())).thenReturn(new OkeRuntimeResources.NodePorts(true,List.of(new OkeRuntimeResources.AllocatedPort("apps","different",31083))));
  assertThrows(RuntimeException.class,()->service.register(context,new OperatingServices.Registration("other","source","main","arm","env","pipeline","Dockerfile","test-command","",31083)));assertEquals(1,service.list(context).size());
 }
 @Test void recentRunsSurviveServiceRestartAndRemainOwnerScoped(){
  var run=service.build(context,service.prepareBuild(context,"app",commit));
  var restarted=new OperatingServices(store,inputs,setup,connections,environments,catalog,versions,builds,registry,access,resources,operations);
  assertEquals(List.of(run),restarted.recent(context,"app"));
  restarted.status(context,run.id());assertEquals(1,restarted.recent(context,"app").size());
  var other=new DeploymentOperations.Context(new Connection("profile","tenant","other","r","label",false),"r","comp","session");
  assertThrows(RuntimeException.class,()->restarted.release(other,run.id()));
  assertThrows(RuntimeException.class,()->restarted.recent(other,"app"));
 }
 @Test void restoringAndRemovingRegistrationNeverMutatesCloudResources(){
  var entry=service.get(context,"app");var run=service.build(context,service.prepareBuild(context,"app",commit));
  assertEquals(entry.deployment(),service.restoreConfiguration(context,"app"));
  verify(inputs).save(eq(context.connection()),eq("r"),eq("comp"),eq(entry.deployment()));
  service.remove(context,"app");assertTrue(service.list(context).isEmpty());assertEquals(run,service.release(context,run.id()));
  assertThrows(RuntimeException.class,()->service.prepareDeploy(context,run.id()));
  verify(setup,never()).update(any(),any(),any(),any(),any(),any());verify(operations,never()).execute(any(),any());
 }

}
