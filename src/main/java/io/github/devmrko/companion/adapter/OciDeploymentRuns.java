package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRuns;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class OciDeploymentRuns implements DeploymentRuns {
    private final OciBuildHistory clients;
    public OciDeploymentRuns(OciBuildHistory clients){this.clients=clients;}
    public List<Pipeline> pipelines(Connection c,String region,String compartment){
        try(var client=clients.client(c,region)){
            var result=new ArrayList<Pipeline>();
            for(var p:client.getPaginators().listDeployPipelinesRecordIterator(ListDeployPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Pipeline(p.getId(),p.getDisplayName(),p.getLifecycleState().getValue()));
            result.sort(Comparator.comparing(p->Objects.toString(p.name(),"")));return result;
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Plan plan(Connection c,String region,String compartment,String pipeline){
        try(var client=clients.client(c,region)){
            var p=client.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(pipeline).build()).getDeployPipeline();
            if(!compartment.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();
            if(p.getLifecycleState()!=DeployPipeline.LifecycleState.Active)throw Errors.DEPLOYMENT_INPUT.exception();
            var stages=new ArrayList<DeployStageSummary>();
            for(var s:client.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(pipeline).build()))stages.add(s);
            if(stages.size()!=1)throw Errors.DEPLOYMENT_INPUT.exception();
            var stage=client.getDeployStage(GetDeployStageRequest.builder().deployStageId(stages.getFirst().getId()).build()).getDeployStage();
            if(stage instanceof OkeDeployStage oke)return okePlan(c,region,compartment,p,oke,client);
            if(!(stage instanceof ComputeInstanceGroupDeployStage s)||!compartment.equals(s.getCompartmentId())||!pipeline.equals(s.getDeployPipelineId()))throw Errors.SCOPE.exception();
            var packages=new StringBuilder();
            if(s.getDeployArtifactIds()!=null)for(var artifactId:s.getDeployArtifactIds()){
                var pkg=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(artifactId).build()).getDeployArtifact();
                validatePackage(pkg,compartment,p.getProjectId());packages.append(pkg.toString());
            }
            var env=client.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(s.getComputeInstanceGroupDeployEnvironmentId()).build()).getDeployEnvironment();
            if(!(env instanceof ComputeInstanceGroupDeployEnvironment e)||!compartment.equals(e.getCompartmentId()))throw Errors.SCOPE.exception();
            var ids=new ArrayList<String>();
            for(var selector:e.getComputeInstanceGroupSelectors().getItems()){
                if(!(selector instanceof ComputeInstanceGroupByIdsSelector selected))throw Errors.DEPLOYMENT_INPUT.exception();
                ids.addAll(selected.getComputeInstanceIds());
            }
            if(ids.isEmpty())throw Errors.DEPLOYMENT_INPUT.exception();
            validateInstances(c,region,compartment,ids);
            var artifact=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(s.getDeploymentSpecDeployArtifactId()).build()).getDeployArtifact();
            if(!compartment.equals(artifact.getCompartmentId())||!(artifact.getDeployArtifactSource() instanceof InlineDeployArtifactSource))throw Errors.SCOPE.exception();
            var digest=MessageDigest.getInstance("SHA-256").digest((p.toString()+s.toString()+e.toString()+artifact.toString()+packages).getBytes(StandardCharsets.UTF_8));
            return new Plan(pipeline,p.getDisplayName(),e.getDisplayName(),List.copyOf(ids),HexFormat.of().formatHex(digest));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    private Plan okePlan(Connection c,String region,String compartment,DeployPipeline pipeline,OkeDeployStage stage,com.oracle.bmc.devops.DevopsClient client) throws Exception {
        if(!compartment.equals(stage.getCompartmentId())||!pipeline.getId().equals(stage.getDeployPipelineId())||!pipeline.getProjectId().equals(stage.getProjectId()))throw Errors.SCOPE.exception();
        if(stage.getLifecycleState()!=DeployStage.LifecycleState.Active)throw Errors.DEPLOYMENT_INPUT.exception();
        var environment=client.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(stage.getOkeClusterDeployEnvironmentId()).build()).getDeployEnvironment();
        if(!(environment instanceof OkeClusterDeployEnvironment env)||!compartment.equals(env.getCompartmentId())||!pipeline.getProjectId().equals(env.getProjectId()))throw Errors.SCOPE.exception();
        if(env.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.DEPLOYMENT_INPUT.exception();
        validateCluster(c,region,compartment,env.getClusterId());
        if(stage.getKubernetesManifestDeployArtifactIds()==null||stage.getKubernetesManifestDeployArtifactIds().isEmpty())throw Errors.DEPLOYMENT_INPUT.exception();
        var artifacts=new ArrayList<DeployArtifact>();
        for(var id:stage.getKubernetesManifestDeployArtifactIds())artifacts.add(client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(id).build()).getDeployArtifact());
        var checked=OkeManifests.inspectDetails(artifacts,compartment,pipeline.getProjectId(),stage.getNamespace());
        var hash=MessageDigest.getInstance("SHA-256");
        hash.update((pipeline.toString()+stage.toString()+env.toString()).getBytes(StandardCharsets.UTF_8));
        for(var artifact:artifacts){
            hash.update(artifact.toString().getBytes(StandardCharsets.UTF_8));
            hash.update(((InlineDeployArtifactSource)artifact.getDeployArtifactSource()).getBase64EncodedContent());
        }
        return new Plan(pipeline.getId(),pipeline.getDisplayName(),env.getDisplayName(),List.of(),HexFormat.of().formatHex(hash.digest()),"OKE",env.getClusterId(),stage.getNamespace(),checked.images(),checked.architectures());
    }
    com.oracle.bmc.containerengine.ContainerEngineClient clusters(Connection c,String region) throws java.io.IOException {
        var client=com.oracle.bmc.containerengine.ContainerEngineClient.builder().configuration(com.oracle.bmc.ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).build()).build(clients.authentication(c));
        client.setRegion(region);return client;
    }
    void validateCluster(Connection c,String region,String compartment,String id) throws java.io.IOException {
        if(id==null||id.isBlank())throw Errors.DEPLOYMENT_INPUT.exception();
        try(var client=clusters(c,region)){
            var cluster=client.getCluster(com.oracle.bmc.containerengine.requests.GetClusterRequest.builder().clusterId(id).build()).getCluster();
            if(!compartment.equals(cluster.getCompartmentId()))throw Errors.SCOPE.exception();
            if(cluster.getLifecycleState()!=com.oracle.bmc.containerengine.model.ClusterLifecycleState.Active)throw Errors.DEPLOYMENT_INPUT.exception();
        }
    }
    static void validatePackage(DeployArtifact pkg,String compartment,String project){
        if(!compartment.equals(pkg.getCompartmentId())||!project.equals(pkg.getProjectId()))throw Errors.SCOPE.exception();
        if(!(pkg.getDeployArtifactSource() instanceof GenericDeployArtifactSource source)||source.getDeployArtifactVersion()==null||source.getDeployArtifactVersion().isBlank()||source.getDeployArtifactVersion().contains("${")||pkg.getArgumentSubstitutionMode()!=DeployArtifact.ArgumentSubstitutionMode.None)throw Errors.DEPLOYMENT_INPUT.exception();
    }
    com.oracle.bmc.core.ComputeClient compute(Connection c,String region) throws java.io.IOException {
        var client=com.oracle.bmc.core.ComputeClient.builder().configuration(com.oracle.bmc.ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).build()).build(clients.authentication(c));
        client.setRegion(region);return client;
    }
    void validateInstances(Connection c,String region,String compartment,List<String> ids) throws java.io.IOException {
        try(var client=compute(c,region)){
            for(var id:ids){
                var instance=client.getInstance(com.oracle.bmc.core.requests.GetInstanceRequest.builder().instanceId(id).build()).getInstance();
                if(!compartment.equals(instance.getCompartmentId()))throw Errors.SCOPE.exception();
                if(instance.getLifecycleState()!=com.oracle.bmc.core.model.Instance.LifecycleState.Running)throw Errors.DEPLOYMENT_VM_STATE.exception();
            }
        }
    }
    public String start(Connection c,String region,String compartment,String pipeline,String fingerprint,String token){
        if(!plan(c,region,compartment,pipeline).fingerprint().equals(fingerprint))throw Errors.BUILD_CHANGED.exception();
        try(var client=clients.client(c,region)){
            return client.createDeployment(CreateDeploymentRequest.builder().opcRetryToken(token).createDeploymentDetails(
                CreateDeployPipelineDeploymentDetails.builder().deployPipelineId(pipeline).displayName("companion-"+token).build()).build()).getDeployment().getId();
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public History history(Connection c,String region,String compartment,String pipeline,String page){
        if(page.length()>8192)throw Errors.DEPLOYMENT_INPUT.exception();
        try(var client=clients.client(c,region)){
            var p=client.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(pipeline).build()).getDeployPipeline();
            if(!compartment.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();
            var response=client.listDeployments(ListDeploymentsRequest.builder().deployPipelineId(pipeline).limit(50).page(page.isBlank()?null:page).sortOrder(SortOrder.Desc).build());
            return new History(response.getDeploymentCollection().getItems().stream().map(r->new Run(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),Objects.toString(r.getTimeCreated(),""))).toList(),Objects.toString(response.getOpcNextPage(),""));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Run run(Connection c,String region,String compartment,String pipeline,String id){
        try(var client=clients.client(c,region)){
            var r=client.getDeployment(GetDeploymentRequest.builder().deploymentId(id).build()).getDeployment();
            if(!compartment.equals(r.getCompartmentId())||!pipeline.equals(r.getDeployPipelineId()))throw Errors.SCOPE.exception();
            var steps=new ArrayList<Step>();
            if(r.getDeploymentExecutionProgress()!=null&&r.getDeploymentExecutionProgress().getDeployStageExecutionProgress()!=null)
                r.getDeploymentExecutionProgress().getDeployStageExecutionProgress().forEach((stageId,progress)->{
                    if(progress.getDeployStageExecutionProgressDetails()!=null)for(var target:progress.getDeployStageExecutionProgressDetails()){
                        if(target.getSteps()!=null)for(var step:target.getSteps())steps.add(new Step(Objects.toString(progress.getDeployStageDisplayName(),stageId),Objects.toString(target.getTargetId(),""),step.getName(),step.getState()==null?"UNKNOWN":step.getState().getValue()));
                        if(target.getRollbackSteps()!=null)for(var step:target.getRollbackSteps())steps.add(new Step(Objects.toString(progress.getDeployStageDisplayName(),stageId),Objects.toString(target.getTargetId(),""),"복구 · "+step.getName(),step.getState()==null?"UNKNOWN":step.getState().getValue()));
                    }
                });
            return new Run(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),Objects.toString(r.getLifecycleDetails(),""),List.copyOf(steps));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
