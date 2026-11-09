package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciOkeDeploymentSetup implements OkeDeploymentSetup {
 private final OciBuildHistory clients;private final OciDeploymentSetup registry;
 public OciOkeDeploymentSetup(OciBuildHistory clients,OciDeploymentSetup registry){this.clients=clients;this.registry=registry;}
 public Preview preview(Connection c,String r,String comp,Input i){
  try(var d=clients.client(c,r);var a=registry.artifacts(c,r)){
   var response=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(i.environment()).build());
   if(!(response.getDeployEnvironment() instanceof OkeClusterDeployEnvironment e)||!comp.equals(e.getCompartmentId())||e.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.SCOPE.exception();
   var project=d.getProject(GetProjectRequest.builder().projectId(e.getProjectId()).build()).getProject();if(!comp.equals(project.getCompartmentId())||project.getLifecycleState()!=Project.LifecycleState.Active)throw Errors.SCOPE.exception();
   var ir=a.getContainerImage(GetContainerImageRequest.builder().imageId(i.image()).build());var image=ir.getContainerImage();if(!comp.equals(image.getCompartmentId())||!i.repository().equals(image.getRepositoryId())||!"AVAILABLE".equals(image.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   var repo=a.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(i.repository()).build()).getContainerRepository();if(!comp.equals(repo.getCompartmentId())||!"AVAILABLE".equals(repo.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   String url=com.oracle.bmc.Region.fromRegionId(r).getRegionCode()+".ocir.io/"+repo.getNamespace()+"/"+repo.getDisplayName()+"@"+image.getDigest();String yaml=OkeDeploymentManifest.render(i,url);
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((i.toString()+yaml+e.toString()+Objects.toString(response.getEtag(),"")+Objects.toString(ir.getEtag(),"")).getBytes(StandardCharsets.UTF_8)));
   return new Preview(i,e.getProjectId(),e.getDisplayName(),url,yaml,hash);
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public DeploymentSetup.Result create(Connection c,String r,String comp,Preview expected,String token){
  var p=preview(c,r,comp,expected.input());if(!expected.fingerprint().equals(p.fingerprint()))throw Errors.BUILD_CHANGED.exception();String pipeline="",manifest="",stage="";
  try(var d=clients.client(c,r)){
   var i=p.input();pipeline=d.createDeployPipeline(CreateDeployPipelineRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"pipeline")).createDeployPipelineDetails(CreateDeployPipelineDetails.builder().projectId(p.project()).displayName(i.name()).description(i.description()).build()).build()).getDeployPipeline().getId();
   manifest=d.createDeployArtifact(CreateDeployArtifactRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"manifest")).createDeployArtifactDetails(CreateDeployArtifactDetails.builder().projectId(p.project()).displayName(i.name()+"-manifest").deployArtifactType(DeployArtifact.DeployArtifactType.KubernetesManifest).argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(InlineDeployArtifactSource.builder().base64EncodedContent(p.yaml().getBytes(StandardCharsets.UTF_8)).build()).build()).build()).getDeployArtifact().getId();
   stage=d.createDeployStage(CreateDeployStageRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"stage")).createDeployStageDetails(CreateOkeDeployStageDetails.builder().deployPipelineId(pipeline).displayName("Deploy to OKE").okeClusterDeployEnvironmentId(i.environment()).kubernetesManifestDeployArtifactIds(List.of(manifest)).namespace(i.namespace()).rollbackPolicy(i.rollback()?AutomatedDeployStageRollbackPolicy.builder().build():NoDeployStageRollbackPolicy.builder().build()).deployStagePredecessorCollection(DeployStagePredecessorCollection.builder().items(List.of(DeployStagePredecessor.builder().id(pipeline).build())).build()).build()).build()).getDeployStage().getId();
   return new DeploymentSetup.Result(pipeline,"",manifest,stage,"");
  }catch(Exception e){return new DeploymentSetup.Result(pipeline,"",manifest,stage,"일부 생성 또는 응답 확인 실패 · 생성된 ID를 OCI에서 확인하세요. 자동 재생성하지 않습니다.");}
 }
}
