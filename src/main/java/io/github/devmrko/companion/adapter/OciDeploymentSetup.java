package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentSetup;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciDeploymentSetup implements DeploymentSetup {
    private final OciBuildHistory clients;
    public OciDeploymentSetup(OciBuildHistory clients){this.clients=clients;}
    ArtifactsClient artifacts(Connection c,String region)throws Exception{var x=ArtifactsClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(region);return x;}
    ComputeClient compute(Connection c,String region)throws Exception{var x=ComputeClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(region);return x;}
    private ClientConfiguration config(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
    public Preview preview(Connection c,String region,String comp,Input input){
        String yaml=DeploymentSpecifications.render(input);
        try(var devops=clients.client(c,region);var registry=artifacts(c,region);var compute=compute(c,region)){
            var er=devops.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(input.environment()).build());
            if(!(er.getDeployEnvironment() instanceof ComputeInstanceGroupDeployEnvironment e)||!comp.equals(e.getCompartmentId())||e.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.SCOPE.exception();
            var project=devops.getProject(GetProjectRequest.builder().projectId(e.getProjectId()).build()).getProject();if(!comp.equals(project.getCompartmentId())||project.getLifecycleState()!=Project.LifecycleState.Active)throw Errors.SCOPE.exception();
            var ids=new LinkedHashSet<String>();if(e.getComputeInstanceGroupSelectors()==null||e.getComputeInstanceGroupSelectors().getItems()==null)throw Errors.DEPLOYMENT_SETUP.exception();
            for(var selector:e.getComputeInstanceGroupSelectors().getItems()){if(!(selector instanceof ComputeInstanceGroupByIdsSelector byId))throw Errors.DEPLOYMENT_SETUP.exception();ids.addAll(byId.getComputeInstanceIds());}
            if(ids.isEmpty()||ids.size()>50||input.batch()>ids.size())throw Errors.DEPLOYMENT_SETUP.exception();
            for(var id:ids){var vm=compute.getInstance(GetInstanceRequest.builder().instanceId(id).build()).getInstance();if(!comp.equals(vm.getCompartmentId())||Set.of("TERMINATED","TERMINATING").contains(vm.getLifecycleState().getValue()))throw Errors.SCOPE.exception();}
            var rr=registry.getRepository(com.oracle.bmc.artifacts.requests.GetRepositoryRequest.builder().repositoryId(input.repository()).build());var repo=rr.getRepository();
            if(!comp.equals(repo.getCompartmentId())||!Boolean.TRUE.equals(repo.getIsImmutable())||!"AVAILABLE".equals(repo.getLifecycleState().getValue()))throw Errors.DEPLOYMENT_ARTIFACT.exception();
            var ar=registry.getGenericArtifact(GetGenericArtifactRequest.builder().artifactId(input.artifact()).build());var a=ar.getGenericArtifact();
            if(!comp.equals(a.getCompartmentId())||!input.repository().equals(a.getRepositoryId())||!"AVAILABLE".equals(a.getLifecycleState().getValue())||a.getVersion()==null||a.getVersion().isBlank()||a.getVersion().contains("${")||a.getSha256()==null)throw Errors.DEPLOYMENT_ARTIFACT.exception();
            var hash=MessageDigest.getInstance("SHA-256");hash.update((e.toString()+Objects.toString(er.getEtag(),"")+repo.toString()+Objects.toString(rr.getEtag(),"")+a.toString()+Objects.toString(ar.getEtag(),"")+input.toString()+yaml).getBytes(StandardCharsets.UTF_8));
            return new Preview(input,e.getProjectId(),e.getDisplayName(),a.getArtifactPath(),a.getVersion(),a.getSha256(),Objects.toString(a.getSizeInBytes(),""),yaml,HexFormat.of().formatHex(hash.digest()));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    static String token(String token,String part){return UUID.nameUUIDFromBytes((token+":"+part).getBytes(StandardCharsets.UTF_8)).toString();}
    public Result create(Connection c,String region,String comp,Preview expected,String token){
        var p=preview(c,region,comp,expected.input());if(!p.fingerprint().equals(expected.fingerprint()))throw Errors.BUILD_CHANGED.exception();
        String pipeline="",pkg="",spec="",stage="";
        try(var d=clients.client(c,region)){
            var i=p.input();String suffix=token.substring(0,8);
            pipeline=d.createDeployPipeline(CreateDeployPipelineRequest.builder().opcRetryToken(token(token,"pipeline")).createDeployPipelineDetails(CreateDeployPipelineDetails.builder().projectId(p.project()).displayName(i.name()).description(i.description().isBlank()?null:i.description()).build()).build()).getDeployPipeline().getId();
            pkg=d.createDeployArtifact(CreateDeployArtifactRequest.builder().opcRetryToken(token(token,"package")).createDeployArtifactDetails(CreateDeployArtifactDetails.builder().projectId(p.project()).displayName(i.name()+"-package-"+suffix).deployArtifactType(DeployArtifact.DeployArtifactType.GenericFile).argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(GenericDeployArtifactSource.builder().repositoryId(i.repository()).deployArtifactPath(p.artifactPath()).deployArtifactVersion(p.version()).build()).build()).build()).getDeployArtifact().getId();
            spec=d.createDeployArtifact(CreateDeployArtifactRequest.builder().opcRetryToken(token(token,"spec")).createDeployArtifactDetails(CreateDeployArtifactDetails.builder().projectId(p.project()).displayName(i.name()+"-spec-"+suffix).deployArtifactType(DeployArtifact.DeployArtifactType.DeploymentSpec).argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(InlineDeployArtifactSource.builder().base64EncodedContent(p.yaml().getBytes(StandardCharsets.UTF_8)).build()).build()).build()).getDeployArtifact().getId();
            stage=d.createDeployStage(CreateDeployStageRequest.builder().opcRetryToken(token(token,"stage")).createDeployStageDetails(CreateComputeInstanceGroupDeployStageDetails.builder().deployPipelineId(pipeline).displayName("Deploy to VM").computeInstanceGroupDeployEnvironmentId(i.environment()).deployArtifactIds(List.of(pkg)).deploymentSpecDeployArtifactId(spec).rolloutPolicy(ComputeInstanceGroupLinearRolloutPolicyByCount.builder().batchCount(i.batch()).build()).deployStagePredecessorCollection(DeployStagePredecessorCollection.builder().items(List.of(DeployStagePredecessor.builder().id(pipeline).build())).build()).build()).build()).getDeployStage().getId();
            return new Result(pipeline,pkg,spec,stage,"");
        }catch(Exception e){
            String detail=e instanceof com.oracle.bmc.model.BmcException b?" (HTTP "+b.getStatusCode()+" · "+Objects.toString(b.getServiceCode(),"OCI 오류")+" · 요청 "+Objects.toString(b.getOpcRequestId(),"미확인")+")":"";
            return new Result(pipeline,pkg,spec,stage,"일부 생성 또는 응답 확인 실패 · 아래 ID와 OCI 프로젝트 목록을 확인한 뒤 복구하세요. 자동 재생성하지 않습니다."+detail);
        }
    }
}
