package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentSetup.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciDeploymentSetupTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 final Input input=new Input("env","repo","artifact","release","description","/tmp/app","ocarun","","test -f app","","test -f app","","","",600,1);
 Preview preview(String fingerprint){return new Preview(input,"project","VM","app.tar.gz","v1","sha","10","version: '1.0'",fingerprint);}
 @Test void previewRequiresImmutableRepositoryAndArtifactInSameScope() throws Exception {
  var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);var registry=mock(com.oracle.bmc.artifacts.ArtifactsClient.class);var compute=mock(com.oracle.bmc.core.ComputeClient.class);var adapter=spy(new OciDeploymentSetup(clients));when(clients.client(c,"seoul")).thenReturn(d);doReturn(registry).when(adapter).artifacts(c,"seoul");doReturn(compute).when(adapter).compute(c,"seoul");
  when(d.getDeployEnvironment(any())).thenReturn(GetDeployEnvironmentResponse.builder().deployEnvironment(ComputeInstanceGroupDeployEnvironment.builder().compartmentId("comp").projectId("project").displayName("VM").lifecycleState(DeployEnvironment.LifecycleState.Active).computeInstanceGroupSelectors(ComputeInstanceGroupSelectorCollection.builder().items(java.util.List.of(ComputeInstanceGroupByIdsSelector.builder().computeInstanceIds(java.util.List.of("vm")).build())).build()).build()).build());
  when(d.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder().compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
  when(compute.getInstance(any())).thenReturn(com.oracle.bmc.core.responses.GetInstanceResponse.builder().instance(com.oracle.bmc.core.model.Instance.builder().compartmentId("comp").lifecycleState(com.oracle.bmc.core.model.Instance.LifecycleState.Stopped).build()).build());
  var repo=com.oracle.bmc.artifacts.model.GenericRepository.builder().compartmentId("comp").isImmutable(false).lifecycleState(com.oracle.bmc.artifacts.model.Repository.LifecycleState.Available).build();
  when(registry.getRepository(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetRepositoryResponse.builder().repository(repo).build());
  assertThrows(RuntimeException.class,()->adapter.preview(c,"seoul","comp",input));verify(registry,never()).getGenericArtifact(any());
  when(registry.getRepository(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetRepositoryResponse.builder().repository(repo.toBuilder().isImmutable(true).build()).build());
  var artifact=com.oracle.bmc.artifacts.model.GenericArtifact.builder().compartmentId("comp").repositoryId("other").artifactPath("app.tar.gz").version("v1").sha256("sha").lifecycleState(com.oracle.bmc.artifacts.model.GenericArtifact.LifecycleState.Available).build();
  when(registry.getGenericArtifact(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetGenericArtifactResponse.builder().genericArtifact(artifact).build());assertThrows(RuntimeException.class,()->adapter.preview(c,"seoul","comp",input));
  when(registry.getGenericArtifact(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetGenericArtifactResponse.builder().genericArtifact(artifact.toBuilder().repositoryId("repo").build()).build());var p=adapter.preview(c,"seoul","comp",input);assertEquals("v1",p.version());
  when(registry.getGenericArtifact(any())).thenReturn(com.oracle.bmc.artifacts.responses.GetGenericArtifactResponse.builder().genericArtifact(artifact.toBuilder().repositoryId("repo").sha256("changed").build()).build());assertNotEquals(p.fingerprint(),adapter.preview(c,"seoul","comp",input).fingerprint());
 }
 @Test void changedReviewCannotCreateCloudResources() throws Exception {
  var clients=mock(OciBuildHistory.class);var adapter=spy(new OciDeploymentSetup(clients));doReturn(preview("new")).when(adapter).preview(c,"seoul","comp",input);
  assertThrows(RuntimeException.class,()->adapter.create(c,"seoul","comp",preview("old"),"12345678-token"));verifyNoInteractions(clients);
 }
 @Test void nativeResourcesAreLinkedWithoutExecutingDeployment() throws Exception {
  var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);when(clients.client(c,"seoul")).thenReturn(d);var adapter=spy(new OciDeploymentSetup(clients));doReturn(preview("same")).when(adapter).preview(c,"seoul","comp",input);
  when(d.createDeployPipeline(any())).thenReturn(CreateDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().id("pipeline").build()).build());
  when(d.createDeployArtifact(any())).thenReturn(CreateDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().id("package").build()).build(),CreateDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().id("spec").build()).build());
  when(d.createDeployStage(any())).thenReturn(CreateDeployStageResponse.builder().deployStage(ComputeInstanceGroupDeployStage.builder().id("stage").build()).build());
  var result=adapter.create(c,"seoul","comp",preview("same"),"12345678-token");assertTrue(result.complete());
  var requests=org.mockito.ArgumentCaptor.forClass(com.oracle.bmc.devops.requests.CreateDeployArtifactRequest.class);verify(d,times(2)).createDeployArtifact(requests.capture());
  var pkg=requests.getAllValues().getFirst().getCreateDeployArtifactDetails();assertEquals("project",pkg.getProjectId());assertEquals(DeployArtifact.ArgumentSubstitutionMode.None,pkg.getArgumentSubstitutionMode());assertEquals("v1",((GenericDeployArtifactSource)pkg.getDeployArtifactSource()).getDeployArtifactVersion());
  assertArrayEquals(preview("same").yaml().getBytes(java.nio.charset.StandardCharsets.UTF_8),((InlineDeployArtifactSource)requests.getAllValues().getLast().getCreateDeployArtifactDetails().getDeployArtifactSource()).getBase64EncodedContent());
  var stage=org.mockito.ArgumentCaptor.forClass(com.oracle.bmc.devops.requests.CreateDeployStageRequest.class);verify(d).createDeployStage(stage.capture());var details=(CreateComputeInstanceGroupDeployStageDetails)stage.getValue().getCreateDeployStageDetails();assertEquals("pipeline",details.getDeployStagePredecessorCollection().getItems().getFirst().getId());assertEquals("env",details.getComputeInstanceGroupDeployEnvironmentId());assertNull(details.getRolloutPolicy().getBatchDelayInSeconds());assertEquals("spec",details.getDeploymentSpecDeployArtifactId());assertEquals(java.util.List.of("package"),details.getDeployArtifactIds());verify(d,never()).createDeployment(any());
 }
 @Test void partialCreationReportsKnownIdsWithoutRetryOrDeletion() throws Exception {
  var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);when(clients.client(c,"seoul")).thenReturn(d);var adapter=spy(new OciDeploymentSetup(clients));doReturn(preview("same")).when(adapter).preview(c,"seoul","comp",input);
  when(d.createDeployPipeline(any())).thenReturn(CreateDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().id("pipeline").build()).build());when(d.createDeployArtifact(any())).thenThrow(new RuntimeException("failure"));
  var result=adapter.create(c,"seoul","comp",preview("same"),"12345678-token");assertFalse(result.complete());assertEquals("pipeline",result.pipeline());assertFalse(result.error().isEmpty());verify(d,times(1)).createDeployArtifact(any());verify(d,never()).deleteDeployPipeline(any());
 }
}
