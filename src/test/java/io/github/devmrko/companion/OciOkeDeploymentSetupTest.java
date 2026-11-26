package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeDeploymentSetup.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciOkeDeploymentSetupTest {
 @Test void nativeConfigurationUsesPinnedManifestAndRollbackPolicy()throws Exception{
  var c=new Connection("DEFAULT","tenant","user","home","label",false);var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);when(clients.client(c,"r")).thenReturn(d);var adapter=spy(new OciOkeDeploymentSetup(clients,mock(OciDeploymentSetup.class),mock(io.github.devmrko.companion.port.OkeRuntimePreparation.class),mock(io.github.devmrko.companion.port.OkeRuntimeResources.class)));
  var i=new Input("env","repo","image","hello","description","app-test","hello","amd64",1,250,256,8080,"/hello","","","","",true);var p=new Preview(i,"project","env","url","kind: Deployment\n","fingerprint");doReturn(p).when(adapter).preview(c,"r","comp",i);
  when(d.createDeployPipeline(any())).thenReturn(CreateDeployPipelineResponse.builder().opcWorkRequestId("pipeline-work").deployPipeline(DeployPipeline.builder().id("pipeline").build()).build());when(d.createDeployArtifact(any())).thenReturn(CreateDeployArtifactResponse.builder().opcWorkRequestId("artifact-work").deployArtifact(DeployArtifact.builder().id("manifest").build()).build());when(d.createDeployStage(any())).thenReturn(CreateDeployStageResponse.builder().opcWorkRequestId("stage-work").deployStage(OkeDeployStage.builder().id("stage").build()).build());
  when(d.getWorkRequest(any())).thenReturn(GetWorkRequestResponse.builder().workRequest(WorkRequest.builder().status(OperationStatus.Succeeded).build()).build());
  assertTrue(adapter.create(c,"r","comp",p,"token").complete());verify(d).createDeployArtifact(argThat(x->x.getCreateDeployArtifactDetails().getArgumentSubstitutionMode()==DeployArtifact.ArgumentSubstitutionMode.None&&new String(((InlineDeployArtifactSource)x.getCreateDeployArtifactDetails().getDeployArtifactSource()).getBase64EncodedContent(),StandardCharsets.UTF_8).equals(p.yaml())));verify(d).createDeployStage(argThat(x->x.getCreateDeployStageDetails() instanceof CreateOkeDeployStageDetails s&&s.getNamespace().equals("app-test")&&s.getOkeClusterDeployEnvironmentId().equals("env")&&s.getRollbackPolicy() instanceof AutomatedDeployStageRollbackPolicy));
  when(d.createDeployStage(any())).thenThrow(new RuntimeException("connection lost"));var partial=adapter.create(c,"r","comp",p,"another");assertFalse(partial.complete());assertEquals("pipeline",partial.pipeline());assertEquals("manifest",partial.specification());
  doReturn(new Preview(i,"project","env","url",p.yaml(),"changed")).when(adapter).preview(c,"r","comp",i);assertThrows(RuntimeException.class,()->adapter.create(c,"r","comp",p,"third"));verify(d,times(2)).createDeployPipeline(any());
 }
 @Test void missingReferenceAtSaveBlocksBeforeAnyNativeCreation(){
  var c=new Connection("DEFAULT","tenant","user","home","label",false);var clients=mock(OciBuildHistory.class);
  var adapter=spy(new OciOkeDeploymentSetup(clients,mock(OciDeploymentSetup.class),mock(io.github.devmrko.companion.port.OkeRuntimePreparation.class),mock(io.github.devmrko.companion.port.OkeRuntimeResources.class)));
  var i=new Input("env","repo","image","hello","description","app-test","hello","amd64",1,250,256,8080,"/hello","","","","",true);
  var old=new Preview(i,"project","env","url","yaml","fingerprint");
  var failed=new io.github.devmrko.companion.port.OkeRuntimePreparation.Report(java.time.Instant.now(),java.util.List.of(new io.github.devmrko.companion.port.OkeRuntimePreparation.Check("configmaps/settings",io.github.devmrko.companion.port.OkeRuntimePreparation.State.FAIL,"missing")));
  doReturn(new Preview(i,"project","env","url","yaml","fingerprint",failed)).when(adapter).preview(c,"r","comp",i);
  assertThrows(io.github.devmrko.companion.error.BusinessException.class,()->adapter.create(c,"r","comp",old,"token"));verifyNoInteractions(clients);
 }

 @Test void previewMatchesPullSecretRegistryHost()throws Exception {
  var c=new Connection("p","tenant","user","ap-chuncheon-1","label",false);var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);
  var registry=mock(OciDeploymentSetup.class);var artifacts=mock(com.oracle.bmc.artifacts.ArtifactsClient.class);var runtime=mock(io.github.devmrko.companion.port.OkeRuntimePreparation.class);var resources=mock(io.github.devmrko.companion.port.OkeRuntimeResources.class);
  when(clients.client(c,"ap-chuncheon-1")).thenReturn(d);when(registry.artifacts(c,"ap-chuncheon-1")).thenReturn(artifacts);
  when(d.getDeployEnvironment(any())).thenReturn(GetDeployEnvironmentResponse.builder().deployEnvironment(OkeClusterDeployEnvironment.builder().id("env").projectId("project").compartmentId("comp").displayName("env").lifecycleState(DeployEnvironment.LifecycleState.Active).build()).build());
  when(d.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder().id("project").compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
  when(artifacts.getContainerImage(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetContainerImageResponse.builder().containerImage(com.oracle.bmc.artifacts.model.ContainerImage.builder().compartmentId("comp").repositoryId("repo").digest("sha256:"+"a".repeat(64)).lifecycleState(com.oracle.bmc.artifacts.model.ContainerImage.LifecycleState.Available).build()).build());
  when(artifacts.getContainerRepository(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetContainerRepositoryResponse.builder().containerRepository(com.oracle.bmc.artifacts.model.ContainerRepository.builder().compartmentId("comp").namespace("ns").displayName("app").lifecycleState(com.oracle.bmc.artifacts.model.ContainerRepository.LifecycleState.Available).build()).build());
  when(resources.pullRegistry(any(),any(),any(),any(),any(),any(),any())).thenReturn(new io.github.devmrko.companion.port.OkeRuntimeResources.PullRegistry(true,java.util.List.of("ap-chuncheon-1.ocir.io"),""));
  var input=new Input("env","repo","image","hello","description","app-test","hello","arm64",1,250,256,8080,"/hello","","ocir-pull","","",true);
  var preview=new OciOkeDeploymentSetup(clients,registry,runtime,resources).preview(c,"ap-chuncheon-1","comp",input);
  assertTrue(preview.imageUrl().startsWith("ap-chuncheon-1.ocir.io/ns/app@sha256:"));assertTrue(preview.yaml().contains(preview.imageUrl()));assertTrue(preview.runtime().allowed());
  var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var node=(tools.jackson.databind.node.ObjectNode)mapper.valueToTree(input);node.put("serviceType","ExistingDeployment");var existing=mapper.treeToValue(node,Input.class);
  when(resources.deployment(any(),any(),any(),any(),any(),any(),any())).thenReturn("{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"metadata\":{\"name\":\"hello\",\"namespace\":\"app-test\",\"resourceVersion\":\"1\"},\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"hello\",\"image\":\"old\"}],\"imagePullSecrets\":[{\"name\":\"live-pull-secret\"}]}}}}");
  when(resources.pullRegistry(any(),any(),any(),any(),any(),any(),eq("live-pull-secret"))).thenReturn(new io.github.devmrko.companion.port.OkeRuntimeResources.PullRegistry(true,java.util.List.of("yny.ocir.io"),""));
  var preserved=new OciOkeDeploymentSetup(clients,registry,runtime,resources).preview(c,"ap-chuncheon-1","comp",existing);
  assertTrue(preserved.imageUrl().startsWith("yny.ocir.io/"));assertTrue(preserved.yaml().contains("live-pull-secret"));assertFalse(preserved.yaml().contains("ocir-pull"));

 }
 @Test void newManifestAndStageMustFinishBeforeDeploymentCanFollow(){
  var d=mock(DevopsClient.class);var input=new Input("env","repo","image","app","","ns","app","arm64",2,100,256,8080,"/","","","","",true);var preview=new Preview(input,"project","env","url","reviewed pinned manifest","a".repeat(64));
  var stage=OkeDeployStage.builder().id("stage").build();
  when(d.createDeployArtifact(any())).thenReturn(CreateDeployArtifactResponse.builder().opcWorkRequestId("create").deployArtifact(DeployArtifact.builder().id("new-manifest").build()).build());
  when(d.updateDeployStage(any())).thenReturn(UpdateDeployStageResponse.builder().opcWorkRequestId("update").build());
  when(d.getWorkRequest(any())).thenReturn(GetWorkRequestResponse.builder().workRequest(WorkRequest.builder().status(OperationStatus.Succeeded).build()).build());
  OciOkeDeploymentSetup.replaceManifest(d,stage,"stage-etag",preview);
  var order=inOrder(d);order.verify(d).createDeployArtifact(any());order.verify(d).getWorkRequest(argThat(r->r.getWorkRequestId().equals("create")));order.verify(d).updateDeployStage(argThat(r->r.getIfMatch().equals("stage-etag")&&r.getUpdateDeployStageDetails() instanceof UpdateOkeDeployStageDetails u&&u.getKubernetesManifestDeployArtifactIds().equals(java.util.List.of("new-manifest"))));order.verify(d).getWorkRequest(argThat(r->r.getWorkRequestId().equals("update")));verify(d,never()).updateDeployArtifact(any());
 }

}
