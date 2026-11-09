package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.model.Repository;
import com.oracle.bmc.artifacts.responses.GetRepositoryResponse;
import com.oracle.bmc.artifacts.model.*;
import com.oracle.bmc.artifacts.responses.*;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.DevopsPaginators;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildHistory;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciBuildHandoffTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 final String hash="a".repeat(64);
 GenericArtifact artifact(String id,String repo,String hash){return GenericArtifact.builder().id(id).repositoryId(repo).compartmentId("comp").sha256(hash).sizeInBytes(100L).lifecycleState(GenericArtifact.LifecycleState.Available).build();}
 GenericRepository repository(String id,boolean immutable){return GenericRepository.builder().id(id).compartmentId("comp").isImmutable(immutable).lifecycleState(Repository.LifecycleState.Available).build();}
 @Test void immutableAvailableScopedArtifactsRequired(){
  var a=artifact("a","repo",hash);
  assertEquals(hash+":100",OciBuildHandoff.identity(a,"comp",repository("repo",true)));
  assertThrows(RuntimeException.class,()->OciBuildHandoff.identity(a,"other",repository("repo",true)));
  assertThrows(RuntimeException.class,()->OciBuildHandoff.identity(a,"comp",repository("repo",false)));
  assertThrows(RuntimeException.class,()->OciBuildHandoff.identity(a,"comp",repository("wrong",true)));
  assertThrows(RuntimeException.class,()->OciBuildHandoff.identity(artifact("a","repo",""),"comp",repository("repo",true)));
 }
 @Test void unsuccessfulBuildNeverReadsRegistry() throws Exception {
  var builds=mock(OciBuildHistory.class);
  when(builds.detail(c,"region","comp","bp","br")).thenReturn(new BuildHistory.Detail("br","run","FAILED","",List.of()));
  assertThrows(RuntimeException.class,()->new OciBuildHandoff(builds).verify(c,"region","comp","bp","br","dp"));
  verify(builds,never()).client(any(),any());
 }
 @Test void missingDeliveryCannotBePresentedAsVerified() throws Exception {
  var builds=mock(OciBuildHistory.class);
  when(builds.detail(c,"region","comp","bp","br")).thenReturn(new BuildHistory.Detail("br","run","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"))));
  assertThrows(RuntimeException.class,()->new OciBuildHandoff(builds).verify(c,"region","comp","bp","br","dp"));
  verify(builds,never()).client(any(),any());
 }
 @Test void copiedArtifactMatchesByContentAndChangedPackageFails() throws Exception {
  var builds=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);var registry=mock(ArtifactsClient.class);var pages=mock(DevopsPaginators.class);
  when(builds.client(c,"region")).thenReturn(devops);when(devops.getPaginators()).thenReturn(pages);
  when(builds.detail(c,"region","comp","bp","br")).thenReturn(new BuildHistory.Detail("br","run","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"),new BuildHistory.Step("delivery","SUCCEEDED","DELIVER")),List.of(new BuildHistory.StoredArtifact("bundle","Artifact Registry","release.zip","v","source"))));
  when(registry.getGenericArtifact(any())).thenReturn(GetGenericArtifactResponse.builder().genericArtifact(artifact("source","original",hash)).build());
  when(registry.getRepository(any())).thenAnswer(i->GetRepositoryResponse.builder().repository(repository(((com.oracle.bmc.artifacts.requests.GetRepositoryRequest)i.getArgument(0)).getRepositoryId(),true)).build());
  when(devops.getDeployPipeline(any())).thenReturn(GetDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().compartmentId("comp").projectId("project").build()).build());
  when(pages.listDeployStagesRecordIterator(any())).thenReturn(List.of(ComputeInstanceGroupDeployStageSummary.builder().id("stage").build()));
  when(devops.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(ComputeInstanceGroupDeployStage.builder().compartmentId("comp").deployPipelineId("dp").deployArtifactIds(List.of("package")).build()).build());
  when(devops.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().compartmentId("comp").projectId("project").argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(GenericDeployArtifactSource.builder().repositoryId("copy").deployArtifactPath("different/path.zip").deployArtifactVersion("v2").build()).build()).build());
  when(registry.getGenericArtifactByPath(any())).thenReturn(GetGenericArtifactByPathResponse.builder().genericArtifact(artifact("target","copy",hash)).build());
  var adapter=spy(new OciBuildHandoff(builds));doReturn(registry).when(adapter).artifacts(c,"region");
  var proof=adapter.verify(c,"region","comp","bp","br","dp");assertEquals(1,proof.packages());assertEquals(64,proof.fingerprint().length());
  when(registry.getGenericArtifactByPath(any())).thenReturn(GetGenericArtifactByPathResponse.builder().genericArtifact(artifact("target","copy","b".repeat(64))).build());
  assertThrows(RuntimeException.class,()->adapter.verify(c,"region","comp","bp","br","dp"));
  when(devops.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(ComputeInstanceGroupDeployStage.builder().compartmentId("comp").deployPipelineId("dp").deployArtifactIds(List.of()).build()).build());
  assertThrows(RuntimeException.class,()->adapter.verify(c,"region","comp","bp","br","dp"));
 }
}
