package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SourceServiceOnboardingTest {
 @Test void ociBranchWithoutInlineShaUsesCommitHistory(){
  var catalog=mock(CatalogService.class);var versions=mock(SourceVersions.class);var services=mock(OperatingServices.class);var resources=mock(OkeRuntimeResources.class);
  var service=spy(new SourceServiceOnboarding(catalog,versions,services,mock(ArmBuilds.class),mock(Registry.class),mock(OciBuildHistory.class),resources,mock(OkeWorkloadConnections.class),mock(PipelineIam.class),mock(OciOkeDeploymentSetup.class),mock(ServiceOnboarding.class),mock(BuildSpecDraftStore.class)));
  var c=new DeploymentOperations.Context(new Connection("p","t","u","r","",false),"r","comp","s");var src=new io.github.devmrko.companion.domain.Catalog.Source("source","OCI","new-source","","main","repository");when(catalog.sources(any(),any(),any())).thenReturn(List.of(src));
  var input=new OkeDeploymentSetup.Input("env","repo","image","base","","apps","base","arm64",2,100,256,8080,"/readyz","","pull","","",false);
  var reg=new OperatingServices.Registration("base","old-source","main","builder","env","pipeline","Dockerfile","test","",0);var entry=new OperatingServices.Entry(reg,input,"","cluster","",List.of(),"rev");when(services.get(c,"base")).thenReturn(entry);
  when(versions.refs(any(),any(),any(),any())).thenReturn(List.of(new SourceVersions.Ref("BRANCH","main","")));when(versions.commits(any(),any(),any(),any(),eq("main"))).thenReturn(List.of(new SourceVersions.Ref("COMMIT","message","a".repeat(40))));
  when(resources.read(any(),any(),any(),any(),any(),any())).thenReturn(new OkeRuntimeResources.Snapshot(java.time.Instant.now(),"cluster","apps",Map.of("secrets",new OkeRuntimeResources.Listing(true,List.of(new OkeRuntimeResources.Resource("pull","kubernetes.io/dockerconfigjson")),"")),List.of(),List.of()));
  doReturn(Map.of("Dockerfile","FROM python\nEXPOSE 8080\n")).when(service).files(any(),any(),any(),any());
  var d=service.inspect(c,new SourceServiceOnboarding.Request("source","base","new-app","","builder","repo","Dockerfile","test"));assertEquals("a".repeat(40),d.commit());assertEquals("8080",d.hints().values().get("port").value());assertEquals(List.of("pull"),d.pullSecrets());
 }
 @Test void onlyMissingFieldsCanBeSuppliedAndSecretsMustAlreadyExist(){
  var hints=new RepositoryDeploymentHints.Hints(Map.of("port",new RepositoryDeploymentHints.Value("8080","Dockerfile"),"healthPath",new RepositoryDeploymentHints.Value("/readyz","Dockerfile"),"pullSecret",new RepositoryDeploymentHints.Value("absent-secret","k8s/app.yaml")),List.of());
  var draft=new SourceServiceOnboarding.Draft(null,"commit",null,hints,List.of("existing-pull"));
  var values=SourceServiceOnboarding.fill(draft,Map.of("replicas","2","pullSecret","existing-pull","port","9999"));
  assertEquals("8080",values.get("port").value());assertEquals("화면 입력",values.get("pullSecret").source());
  assertThrows(RuntimeException.class,()->SourceServiceOnboarding.fill(draft,Map.of("replicas","2","pullSecret","new-secret")));
  assertThrows(RuntimeException.class,()->SourceServiceOnboarding.fill(draft,Map.of("pullSecret","existing-pull")));
 }
 @Test void consumedBuildAttemptIsSavedBeforeUnknownResponseAndCannotBeResubmitted(){
  var store=mock(BuildSpecDraftStore.class);var builds=mock(ArmBuilds.class);var iam=mock(PipelineIam.class);
  var service=spy(new SourceServiceOnboarding(mock(CatalogService.class),mock(SourceVersions.class),mock(OperatingServices.class),builds,mock(Registry.class),mock(OciBuildHistory.class),mock(OkeRuntimeResources.class),mock(OkeWorkloadConnections.class),iam,mock(OciOkeDeploymentSetup.class),mock(ServiceOnboarding.class),store));
  var c=new DeploymentOperations.Context(new Connection("p","t","u","r","",false),"r","comp","s");
  var reg=new OperatingServices.Registration("base","src","main","builder","env","pipeline","Dockerfile","test","",0);var entry=new OperatingServices.Entry(reg,null,"","cluster","",List.of(),"revision");
  var request=new SourceServiceOnboarding.Request("source","base","new","main","builder","repo","Dockerfile","test");var draft=new SourceServiceOnboarding.Draft(request,"a".repeat(40),entry,new RepositoryDeploymentHints.Hints(Map.of(),List.of()),List.of());
  var ready=new PipelineIam.Check(true,false,"group",false,"",List.of(),List.of(),"");var plan=new SourceServiceOnboarding.Plan(draft,null,Map.of(),null,ready);String token=UUID.randomUUID().toString();
  doReturn(draft).when(service).inspect(c,request);doNothing().when(service).vacant(c,draft);when(iam.inspect(any(),any(),any(),any())).thenReturn(ready);
  when(builds.start(any(),any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("response unknown"));
  assertThrows(RuntimeException.class,()->service.start(c,plan,token));var ordered=inOrder(store,builds);ordered.verify(store).save(any(),argThat(x->x.content().contains("STARTING")));ordered.verify(builds).start(any(),any(),any(),any(),any(),any());
  when(store.read(any())).thenReturn(Optional.of(new BuildSpecDraftStore.Draft("v1","consumed","")));assertThrows(RuntimeException.class,()->service.start(c,plan,token));verify(builds,times(1)).start(any(),any(),any(),any(),any(),any());
 }
}
