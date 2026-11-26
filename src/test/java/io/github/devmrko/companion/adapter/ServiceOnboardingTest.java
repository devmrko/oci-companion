package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ServiceOnboardingTest {
 @Test void missingIamOrChangedLiveVersionCannotCreateResources(){
  var services=mock(OperatingServices.class);var conn=mock(OkeWorkloadConnections.class);var resources=mock(OkeRuntimeResources.class);var clients=mock(OciBuildHistory.class);var setup=mock(OciOkeDeploymentSetup.class);var iam=mock(PipelineIam.class);var store=mock(BuildSpecDraftStore.class);
  var c=new DeploymentOperations.Context(new Connection("p","t","u","r","",false),"r","comp","session");
  var input=new OkeDeploymentSetup.Input("env","repo","image","original","","apps","original","arm64",2,100,512,8080,"/readyz","","pull","","",false);
  var reg=new OperatingServices.Registration("original","source","main","builder","env","pipeline","Dockerfile","pytest","",0);
  var entry=new OperatingServices.Entry(reg,input,"connection","cluster","",List.of(),"revision");
  when(services.get(c,"original")).thenReturn(entry);when(services.list(c)).thenReturn(List.of(entry));when(conn.deployments(any(),any(),any(),any(),any(),any())).thenReturn(List.of("original"));
  when(resources.read(any(),any(),any(),any(),any(),any())).thenReturn(new OkeRuntimeResources.Snapshot(Instant.now(),"cluster","apps",Map.of("services",new OkeRuntimeResources.Listing(true,List.of(),"")),List.of(),List.of()));
  when(resources.deployment(any(),any(),any(),any(),any(),any(),any())).thenReturn(OnboardingManifestTest.LIVE);
  var missing=new PipelineIam.Check(true,false,"group",true,"",List.of("missing"),List.of(),"");when(iam.inspect(any(),any(),any(),any())).thenReturn(missing);
  var service=new ServiceOnboarding(services,conn,resources,clients,setup,iam,mock(OkeDeploymentInputs.class),mock(DeploymentOperations.class),store);
  var selection=new ServiceOnboarding.Selection("source","original","cluster","apps","new-app");var plan=service.preview(c,selection);
  var reordered=new ServiceOnboarding.Plan(plan.selection(),plan.template(),new org.yaml.snakeyaml.Yaml().dumpAll(new org.yaml.snakeyaml.Yaml().loadAll(plan.yaml()).iterator()),plan.image(),plan.version(),plan.iam());assertNotEquals(plan.yaml(),reordered.yaml());assertTrue(ServiceOnboarding.samePlan(plan,reordered));
  assertThrows(RuntimeException.class,()->service.create(c,plan,UUID.randomUUID().toString()));verifyNoInteractions(clients,setup);
  when(iam.inspect(any(),any(),any(),any())).thenReturn(new PipelineIam.Check(true,false,"group",false,"",List.of(),List.of(),""));var allowed=service.preview(c,selection);
  when(resources.deployment(any(),any(),any(),any(),any(),any(),any())).thenReturn(OnboardingManifestTest.LIVE.replace("\"7\"","\"8\""));assertThrows(RuntimeException.class,()->service.create(c,allowed,UUID.randomUUID().toString()));verifyNoInteractions(clients,setup);
  assertThrows(RuntimeException.class,()->service.preview(c,new ServiceOnboarding.Selection("different-source","original","cluster","apps","new-app")));
 }
 @Test void targetImageMismatchPreventsSourceRetirementEvenAfterCloudSuccess(){
  var services=mock(OperatingServices.class);var resources=mock(OkeRuntimeResources.class);var deployments=mock(DeploymentOperations.class);var store=mock(BuildSpecDraftStore.class);
  var service=spy(new ServiceOnboarding(services,mock(OkeWorkloadConnections.class),resources,mock(OciBuildHistory.class),mock(OciOkeDeploymentSetup.class),mock(PipelineIam.class),mock(OkeDeploymentInputs.class),deployments,store));
  var c=new DeploymentOperations.Context(new Connection("p","t","u","r","",false),"r","comp","session");
  var input=new OkeDeploymentSetup.Input("env","repo","image","original","","apps","original","arm64",2,100,512,8080,"/readyz","","pull","","",false);
  var reg=new OperatingServices.Registration("original","source","main","builder","env","pipeline","Dockerfile","pytest","",0);var entry=new OperatingServices.Entry(reg,input,"connection","cluster","",List.of(),"revision");
  var document=OnboardingManifest.cloneTo(OnboardingManifestTest.LIVE,"apps","original","target","moved");var plan=new ServiceOnboarding.Plan(new ServiceOnboarding.Selection("source","original","cluster","target","moved"),entry,document.yaml(),document.image(),"7",null);
  var progress=new ServiceOnboarding.Progress("id",plan,"new-env","new-pipeline","artifact","stage","run","REGISTERED","");doReturn(progress).when(service).read(c,"id");when(services.get(c,"original")).thenReturn(entry);when(deployments.status(c,"new-pipeline","run")).thenReturn(new DeploymentRuns.Run("run","run","SUCCEEDED",""));
  when(resources.verifyImages(any(),any(),any(),any(),any(),any())).thenReturn(DeploymentImageVerification.Result.unknown());
  assertThrows(IllegalArgumentException.class,()->service.retirementPreview(c,"id"));verify(resources,never()).retirement(any(),any(),any(),any(),any(),any(),any());verify(resources,never()).retire(any(),any(),any(),any(),any(),any(),any(),any());verify(services,never()).remove(any(),any());
 }
 @Test void onlyKnownEnvironmentTimeoutOffersResume(){
  var store=mock(BuildSpecDraftStore.class);var service=new ServiceOnboarding(mock(OperatingServices.class),mock(OkeWorkloadConnections.class),mock(OkeRuntimeResources.class),mock(OciBuildHistory.class),mock(OciOkeDeploymentSetup.class),mock(PipelineIam.class),mock(OkeDeploymentInputs.class),mock(DeploymentOperations.class),store);
  var c=new DeploymentOperations.Context(new Connection("p","t","u","r","",false),"r","comp","session");String id=UUID.randomUUID().toString();var json=tools.jackson.databind.json.JsonMapper.builder().build();
  var pending=new ServiceOnboarding.Progress(id,null,"environment","","","","","NEEDS_ATTENTION","OCI 변경 작업 완료 대기 시간 초과 · 배포를 시작하지 않았습니다.");
  when(store.read(any())).thenReturn(Optional.of(new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(pending),"")));assertEquals("ENVIRONMENT_PENDING",service.read(c,id).state());
  var uncertain=new ServiceOnboarding.Progress(id,null,"environment","","","","","NEEDS_ATTENTION","응답 소실");when(store.read(any())).thenReturn(Optional.of(new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(uncertain),"")));assertThrows(RuntimeException.class,()->service.resume(c,id));
 }

}
