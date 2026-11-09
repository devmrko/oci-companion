package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.model.Repository;
import com.oracle.bmc.artifacts.requests.GetRepositoryRequest;
import com.oracle.bmc.artifacts.model.*;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildHandoff;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class OciBuildHandoff implements BuildHandoff {
    private final OciBuildHistory builds;
    public OciBuildHandoff(OciBuildHistory builds){this.builds=builds;}
    ArtifactsClient artifacts(Connection c,String region) throws java.io.IOException {
        var client=ArtifactsClient.builder().configuration(com.oracle.bmc.ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).build()).build(builds.authentication(c));
        client.setRegion(region);return client;
    }
    static String identity(GenericArtifact a,String compartment,Repository repository){
        if(a==null||repository==null||!compartment.equals(a.getCompartmentId())||!compartment.equals(repository.getCompartmentId())||!Objects.equals(a.getRepositoryId(),repository.getId()))throw Errors.SCOPE.exception();
        if(a.getLifecycleState()!=GenericArtifact.LifecycleState.Available||!Boolean.TRUE.equals(repository.getIsImmutable())||repository.getLifecycleState()!=Repository.LifecycleState.Available||a.getSha256()==null||!a.getSha256().matches("[a-fA-F0-9]{64}")||a.getSizeInBytes()==null||a.getSizeInBytes()<1)throw Errors.HANDOFF.exception();
        return a.getSha256().toLowerCase(Locale.ROOT)+":"+a.getSizeInBytes();
    }
    String identity(ArtifactsClient registry,GenericArtifact a,String compartment){
        return identity(a,compartment,registry.getRepository(GetRepositoryRequest.builder().repositoryId(a.getRepositoryId()).build()).getRepository());
    }
    public Proof verify(Connection c,String region,String compartment,String buildPipeline,String buildRun,String deployPipeline){
        var detail=builds.detail(c,region,compartment,buildPipeline,buildRun);
        if(!"SUCCEEDED".equals(detail.state())||!"SUCCEEDED".equals(detail.buildState())||!"SUCCEEDED".equals(detail.storageState()))throw Errors.HANDOFF.exception();
        try(var devops=builds.client(c,region);var registry=artifacts(c,region)){
            var sources=new TreeSet<String>();
            for(var a:detail.artifacts())if("Artifact Registry".equals(a.kind())){
                var stored=registry.getGenericArtifact(GetGenericArtifactRequest.builder().artifactId(a.id()).build()).getGenericArtifact();
                sources.add(identity(registry,stored,compartment));
            }
            if(sources.isEmpty())throw Errors.HANDOFF.exception();
            var pipeline=devops.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(deployPipeline).build()).getDeployPipeline();
            if(!compartment.equals(pipeline.getCompartmentId()))throw Errors.SCOPE.exception();
            var stages=new ArrayList<DeployStageSummary>();
            for(var s:devops.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(deployPipeline).build()))stages.add(s);
            if(stages.size()!=1)throw Errors.HANDOFF.exception();
            var stage=devops.getDeployStage(GetDeployStageRequest.builder().deployStageId(stages.getFirst().getId()).build()).getDeployStage();
            if(!(stage instanceof ComputeInstanceGroupDeployStage target)||!compartment.equals(stage.getCompartmentId())||!deployPipeline.equals(stage.getDeployPipelineId()))throw Errors.HANDOFF.exception();
            if(target.getDeployArtifactIds()==null||target.getDeployArtifactIds().isEmpty())throw Errors.HANDOFF.exception();
            var evidence=new TreeSet<String>();
            for(var id:target.getDeployArtifactIds()){
                var a=devops.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(id).build()).getDeployArtifact();
                OciDeploymentRuns.validatePackage(a,compartment,pipeline.getProjectId());
                var source=(GenericDeployArtifactSource)a.getDeployArtifactSource();
                var stored=registry.getGenericArtifactByPath(GetGenericArtifactByPathRequest.builder().repositoryId(source.getRepositoryId()).artifactPath(source.getDeployArtifactPath()).version(source.getDeployArtifactVersion()).build()).getGenericArtifact();
                var identity=identity(registry,stored,compartment);
                if(!sources.contains(identity))throw Errors.HANDOFF.exception();
                evidence.add(id+":"+stored.getId()+":"+identity);
            }
            var fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((buildPipeline+"\n"+buildRun+"\n"+deployPipeline+"\n"+sources+"\n"+evidence).getBytes(StandardCharsets.UTF_8)));
            return new Proof(buildRun,fingerprint,target.getDeployArtifactIds().size());
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
