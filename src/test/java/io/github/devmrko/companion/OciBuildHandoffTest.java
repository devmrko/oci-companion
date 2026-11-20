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
 BuildHistory.Detail imageDetail(String uri,String digest){return new BuildHistory.Detail("br","run","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"),new BuildHistory.Step("delivery","SUCCEEDED","DELIVER")),List.of(new BuildHistory.StoredArtifact("image","Container Registry",uri,digest,"artifact")));}
 @Test void imageProofMatchesRepositoryAndDigestAndNormalizesOcirHostname() throws Exception {
  String digest="sha256:"+"a".repeat(64),path="namespace/app";
  var detail=imageDetail("icn.ocir.io/"+path+":latest",digest);
  var proof=OciBuildHandoff.imageProof(detail,"ap-seoul-1","bp","dp",List.of("ocir.ap-seoul-1.oci.oraclecloud.com/"+path+"@"+digest));
  assertEquals("IMAGE",proof.kind());assertEquals(1,proof.packages());
  assertNotEquals(proof.fingerprint(),OciBuildHandoff.imageProof(detail,"ap-seoul-1","other","dp",List.of("icn.ocir.io/"+path+"@"+digest)).fingerprint());
 }
 @Test void missingDigestOtherRepositoryMutableTagAndUnbuiltSidecarAreRejected(){
  String digest="sha256:"+"a".repeat(64),uri="icn.ocir.io/namespace/app";
  var detail=imageDetail(uri+":v1",digest);
  for(var targets:List.of(List.of(uri+":v1"),List.of(uri+"@sha256:"+"b".repeat(64)),List.of("icn.ocir.io/namespace/other@"+digest),List.of(uri+"@"+digest,"icn.ocir.io/namespace/sidecar@"+digest)))
   assertThrows(RuntimeException.class,()->OciBuildHandoff.imageProof(detail,"ap-seoul-1","bp","dp",targets));
  assertThrows(RuntimeException.class,()->OciBuildHandoff.imageProof(imageDetail(uri+":v1",""),"ap-seoul-1","bp","dp",List.of(uri+"@"+digest)));
 }
 @Test void okeStageUsesNativeImageEvidenceAndRejectsChangedManifest() throws Exception {
  var builds=mock(OciBuildHistory.class);var devops=mock(DevopsClient.class);var registry=mock(ArtifactsClient.class);var pages=mock(DevopsPaginators.class);var adapter=spy(new OciBuildHandoff(builds));
  when(builds.client(c,"ap-seoul-1")).thenReturn(devops);doReturn(registry).when(adapter).artifacts(c,"ap-seoul-1");when(devops.getPaginators()).thenReturn(pages);
  when(builds.detail(c,"ap-seoul-1","comp","bp","br")).thenReturn(imageDetail("icn.ocir.io/example/app:latest","sha256:"+"a".repeat(64)));
  when(devops.getDeployPipeline(any())).thenReturn(GetDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().id("dp").projectId("project").compartmentId("comp").build()).build());
  when(pages.listDeployStagesRecordIterator(any())).thenReturn(List.of(OkeDeployStageSummary.builder().id("stage").build()));
  when(devops.getDeployStage(any())).thenReturn(GetDeployStageResponse.builder().deployStage(OkeDeployStage.builder().id("stage").deployPipelineId("dp").projectId("project").compartmentId("comp").namespace("testing").kubernetesManifestDeployArtifactIds(List.of("manifest")).build()).build());
  when(devops.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(OkeManifestsTest.artifact(OkeManifestsTest.YAML)).build());
  assertEquals("IMAGE",adapter.verify(c,"ap-seoul-1","comp","bp","br","dp").kind());
  var exported=io.github.devmrko.companion.service.BuildImageIdentity.exported("publish",OkeManifestsTest.IMAGE,"stage").orElseThrow();
  when(builds.detail(c,"ap-seoul-1","comp","bp","br")).thenReturn(new BuildHistory.Detail("br","run","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD")),List.of(exported)));
  assertEquals("IMAGE",adapter.verify(c,"ap-seoul-1","comp","bp","br","dp").kind());
  when(devops.getDeployArtifact(any())).thenReturn(GetDeployArtifactResponse.builder().deployArtifact(OkeManifestsTest.artifact(OkeManifestsTest.YAML.replace("a".repeat(64),"b".repeat(64)))).build());
  assertThrows(RuntimeException.class,()->adapter.verify(c,"ap-seoul-1","comp","bp","br","dp"));
  verify(registry,never()).getGenericArtifact(any());
 }
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
