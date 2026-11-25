package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciOkeDeploymentSetup implements OkeDeploymentSetup {
 private final OkeRuntimeResources resources;private final OkeRuntimePreparation runtime;private final OciBuildHistory clients;private final OciDeploymentSetup registry;
 public OciOkeDeploymentSetup(OciBuildHistory clients,OciDeploymentSetup registry,OkeRuntimePreparation runtime,OkeRuntimeResources resources){this.resources=resources;this.runtime=runtime;this.clients=clients;this.registry=registry;}
 public Preview preview(Connection c,String r,String comp,Input i){
  try(var d=clients.client(c,r);var a=registry.artifacts(c,r)){
   var response=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(i.environment()).build());
   if(!(response.getDeployEnvironment() instanceof OkeClusterDeployEnvironment e)||!comp.equals(e.getCompartmentId())||e.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.SCOPE.exception();
   var project=d.getProject(GetProjectRequest.builder().projectId(e.getProjectId()).build()).getProject();if(!comp.equals(project.getCompartmentId())||project.getLifecycleState()!=Project.LifecycleState.Active)throw Errors.SCOPE.exception();
   var ir=a.getContainerImage(GetContainerImageRequest.builder().imageId(i.image()).build());var image=ir.getContainerImage();if(!comp.equals(image.getCompartmentId())||!i.repository().equals(image.getRepositoryId())||!"AVAILABLE".equals(image.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   var repo=a.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(i.repository()).build()).getContainerRepository();if(!comp.equals(repo.getCompartmentId())||!"AVAILABLE".equals(repo.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   var pull=i.pullSecret().isBlank()?null:resources.pullRegistry(c,r,comp,i.environment(),i.endpoint(),i.namespace(),i.pullSecret());
   String host=io.github.devmrko.companion.service.PullRegistryHosts.choose(i.registryHost(),r,com.oracle.bmc.Region.fromRegionId(r).getRegionCode(),pull);
   String live="ExistingDeployment".equals(i.serviceType())?resources.deployment(c,r,comp,i.environment(),i.endpoint(),i.namespace(),i.app()):"";
   String url=host+"/"+repo.getNamespace()+"/"+repo.getDisplayName()+"@"+image.getDigest();String yaml="ExistingDeployment".equals(i.serviceType())?ExistingOkeDeployment.render(live,i.namespace(),i.app(),url):OkeDeploymentManifest.render(i,url);
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((i.toString()+yaml+(live.isEmpty()?"":ExistingOkeDeployment.version(live))+e.toString()+Objects.toString(response.getEtag(),"")+Objects.toString(ir.getEtag(),"")).getBytes(StandardCharsets.UTF_8)));
   var report=runtime.inspect(c,r,comp,i);
   if(!i.pullSecret().isBlank()) {
    var checks=new ArrayList<OkeRuntimePreparation.Check>(report==null?List.of():report.checks());
    checks.add(new OkeRuntimePreparation.Check("pullRegistry",pull==null||!pull.available()?OkeRuntimePreparation.State.UNKNOWN:pull.hosts().contains(host)?OkeRuntimePreparation.State.PASS:OkeRuntimePreparation.State.WARN,
      pull==null||!pull.available()?"pull Secret 레지스트리를 확인하지 못했습니다.":pull.hosts().contains(host)?"이미지와 pull Secret 레지스트리 일치: "+host:"이미지 호스트 "+host+"와 pull Secret 호스트 "+String.join(", ",pull.hosts())+"가 다릅니다. 주소 형식을 바꾸세요."));
    report=new OkeRuntimePreparation.Report(java.time.Instant.now(),List.copyOf(checks));
   }
   return new Preview(i,e.getProjectId(),e.getDisplayName(),url,yaml,hash,report);
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public DeploymentSetup.Result create(Connection c,String r,String comp,Preview expected,String token){
  var p=preview(c,r,comp,expected.input());if(p.runtime()!=null&&!p.runtime().allowed())throw Errors.DEPLOYMENT_RUNTIME.exception();if(!expected.fingerprint().equals(p.fingerprint()))throw Errors.BUILD_CHANGED.exception();String pipeline="",manifest="",stage="";
  try(var d=clients.client(c,r)){
   var i=p.input();pipeline=d.createDeployPipeline(CreateDeployPipelineRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"pipeline")).createDeployPipelineDetails(CreateDeployPipelineDetails.builder().projectId(p.project()).displayName(i.name()).description(i.description()).build()).build()).getDeployPipeline().getId();
   manifest=d.createDeployArtifact(CreateDeployArtifactRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"manifest")).createDeployArtifactDetails(CreateDeployArtifactDetails.builder().projectId(p.project()).displayName(i.name()+"-manifest").deployArtifactType(DeployArtifact.DeployArtifactType.KubernetesManifest).argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(InlineDeployArtifactSource.builder().base64EncodedContent(p.yaml().getBytes(StandardCharsets.UTF_8)).build()).build()).build()).getDeployArtifact().getId();
   stage=d.createDeployStage(CreateDeployStageRequest.builder().opcRetryToken(OciDeploymentSetup.token(token,"stage")).createDeployStageDetails(CreateOkeDeployStageDetails.builder().deployPipelineId(pipeline).displayName("Deploy to OKE").okeClusterDeployEnvironmentId(i.environment()).kubernetesManifestDeployArtifactIds(List.of(manifest)).namespace(i.namespace()).rollbackPolicy(i.rollback()?AutomatedDeployStageRollbackPolicy.builder().build():NoDeployStageRollbackPolicy.builder().build()).deployStagePredecessorCollection(DeployStagePredecessorCollection.builder().items(List.of(DeployStagePredecessor.builder().id(pipeline).build())).build()).build()).build()).getDeployStage().getId();
   return new DeploymentSetup.Result(pipeline,"",manifest,stage,"");
  }catch(Exception e){return new DeploymentSetup.Result(pipeline,"",manifest,stage,"일부 생성 또는 응답 확인 실패 · 생성된 ID를 OCI에서 확인하세요. 자동 재생성하지 않습니다.");}
 }
 public void update(Connection c,String r,String comp,String pipeline,String fingerprint,Preview expected){
  var p=preview(c,r,comp,expected.input());if(p.runtime()!=null&&!p.runtime().allowed())throw Errors.DEPLOYMENT_RUNTIME.exception();if(!p.fingerprint().equals(expected.fingerprint()))throw Errors.BUILD_CHANGED.exception();
  try(var d=clients.client(c,r)){
   var current=d.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(pipeline).build()).getDeployPipeline();
   if(!comp.equals(current.getCompartmentId())||!p.project().equals(current.getProjectId()))throw Errors.SCOPE.exception();
   var stages=new ArrayList<DeployStageSummary>();for(var s:d.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(pipeline).build()))stages.add(s);
   if(stages.size()!=1)throw Errors.DEPLOYMENT_INPUT.exception();
   var stage=d.getDeployStage(GetDeployStageRequest.builder().deployStageId(stages.getFirst().getId()).build()).getDeployStage();
   if(!(stage instanceof OkeDeployStage oke)||!pipeline.equals(stage.getDeployPipelineId())||!comp.equals(stage.getCompartmentId())||!p.project().equals(stage.getProjectId())||!p.input().environment().equals(oke.getOkeClusterDeployEnvironmentId())||!p.input().namespace().equals(oke.getNamespace())||oke.getKubernetesManifestDeployArtifactIds()==null||oke.getKubernetesManifestDeployArtifactIds().size()!=1)throw Errors.SCOPE.exception();
   String id=oke.getKubernetesManifestDeployArtifactIds().getFirst();var response=d.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(id).build());var artifact=response.getDeployArtifact();
   if(!comp.equals(artifact.getCompartmentId())||!p.project().equals(artifact.getProjectId())||artifact.getDeployArtifactType()!=DeployArtifact.DeployArtifactType.KubernetesManifest||!(artifact.getDeployArtifactSource() instanceof InlineDeployArtifactSource)||artifact.getArgumentSubstitutionMode()!=DeployArtifact.ArgumentSubstitutionMode.None||response.getEtag()==null||response.getEtag().isBlank())throw Errors.SCOPE.exception();
   var environment=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(p.input().environment()).build()).getDeployEnvironment();
   if(!(environment instanceof OkeClusterDeployEnvironment env)||!fingerprint.equals(OciDeploymentRuns.fingerprint(current,oke,env,List.of(artifact))))throw Errors.BUILD_CHANGED.exception();
   OkeManifests.replacementTarget(artifact,comp,p.project(),p.input().namespace(),p.input().app());
   d.updateDeployArtifact(UpdateDeployArtifactRequest.builder().deployArtifactId(id).ifMatch(response.getEtag()).updateDeployArtifactDetails(UpdateDeployArtifactDetails.builder().deployArtifactSource(InlineDeployArtifactSource.builder().base64EncodedContent(p.yaml().getBytes(StandardCharsets.UTF_8)).build()).build()).build());
  }catch(BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e)+" · 원본 파이프라인을 확인한 뒤 다시 미리보기하세요.");}
 }
}
