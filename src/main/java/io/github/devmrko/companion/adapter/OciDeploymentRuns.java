package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRuns;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
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
            if(p.getLifecycleState()!=DeployPipeline.LifecycleState.Active)throw Errors.INVALID.exception();
            var stages=new ArrayList<DeployStageSummary>();
            for(var s:client.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(pipeline).build()))stages.add(s);
            if(stages.size()!=1)throw Errors.INVALID.exception();
            var stage=client.getDeployStage(GetDeployStageRequest.builder().deployStageId(stages.getFirst().getId()).build()).getDeployStage();
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
                if(!(selector instanceof ComputeInstanceGroupByIdsSelector selected))throw Errors.INVALID.exception();
                ids.addAll(selected.getComputeInstanceIds());
            }
            if(ids.isEmpty())throw Errors.INVALID.exception();
            validateInstances(c,region,compartment,ids);
            var artifact=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(s.getDeploymentSpecDeployArtifactId()).build()).getDeployArtifact();
            if(!compartment.equals(artifact.getCompartmentId())||!(artifact.getDeployArtifactSource() instanceof InlineDeployArtifactSource))throw Errors.SCOPE.exception();
            var digest=MessageDigest.getInstance("SHA-256").digest((p.toString()+s.toString()+e.toString()+artifact.toString()+packages).getBytes(StandardCharsets.UTF_8));
            return new Plan(pipeline,p.getDisplayName(),e.getDisplayName(),List.copyOf(ids),HexFormat.of().formatHex(digest));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    static void validatePackage(DeployArtifact pkg,String compartment,String project){
        if(!compartment.equals(pkg.getCompartmentId())||!project.equals(pkg.getProjectId()))throw Errors.SCOPE.exception();
        if(!(pkg.getDeployArtifactSource() instanceof GenericDeployArtifactSource source)||source.getDeployArtifactVersion()==null||source.getDeployArtifactVersion().isBlank()||source.getDeployArtifactVersion().contains("${")||pkg.getArgumentSubstitutionMode()!=DeployArtifact.ArgumentSubstitutionMode.None)throw Errors.INVALID.exception();
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
                if(instance.getLifecycleState()!=com.oracle.bmc.core.model.Instance.LifecycleState.Running)throw Errors.INVALID.exception();
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
    public Run run(Connection c,String region,String compartment,String pipeline,String id){
        try(var client=clients.client(c,region)){
            var r=client.getDeployment(GetDeploymentRequest.builder().deploymentId(id).build()).getDeployment();
            if(!compartment.equals(r.getCompartmentId())||!pipeline.equals(r.getDeployPipelineId()))throw Errors.SCOPE.exception();
            return new Run(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),Objects.toString(r.getLifecycleDetails(),""));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
