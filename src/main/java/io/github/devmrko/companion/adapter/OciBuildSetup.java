package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import io.github.devmrko.companion.port.BuildSetup;
import io.github.devmrko.companion.port.BuildDefinition;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciBuildSetup implements BuildSetup {
    private final OciBuildHistory clients;private final BuildDefinition definitions;
    public OciBuildSetup(OciBuildHistory clients,BuildDefinition definitions) { this.clients=clients;this.definitions=definitions; }
    ArtifactsClient registry(Connection c,String region) throws java.io.IOException {
        var client=ArtifactsClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000)
            .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c));client.setRegion(region);return client;
    }
    BuildPipeline pipeline(DevopsClient client,String comp,String id) {
        var p=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(id).build()).getBuildPipeline();
        if(!comp.equals(p.getCompartmentId())) throw Errors.SCOPE.exception();return p;
    }
    public Settings read(Connection c,String region,String comp,String pipeline) {
        try(var client=clients.client(c,region)) {
            var pipelineResponse=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(pipeline).build());
            var p=pipelineResponse.getBuildPipeline();if(!comp.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();
            var warnings=new ArrayList<String>();var stages=new ArrayList<EditableStage>();var deliveries=new ArrayList<Delivery>();
            for(var summary:client.getPaginators().listBuildPipelineStagesRecordIterator(ListBuildPipelineStagesRequest.builder().buildPipelineId(pipeline).build())) {
                var response=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(summary.getId()).build());
                if(response.getBuildPipelineStage() instanceof BuildStage b && b.getLifecycleState()!=BuildPipelineStage.LifecycleState.Deleted) {
                    var src=primary(b);
                    stages.add(new EditableStage(b.getId(),response.getEtag(),b.getDisplayName(),Objects.toString(b.getBuildSpecFile(),"build_spec.yaml"),
                        b.getStageExecutionTimeoutInSeconds()==null?3600:b.getStageExecutionTimeoutInSeconds(),Objects.toString(b.getImage(),""),src==null?"":src.getName(),src==null?"":src.getBranch()));
                } else if(response.getBuildPipelineStage() instanceof DeliverArtifactStage d && d.getLifecycleState()!=BuildPipelineStage.LifecycleState.Deleted) {
                    deliveries.add(new Delivery(d.getId(),response.getEtag(),d.getDisplayName(),d.getDeliverArtifactCollection()==null?List.of():
                        d.getDeliverArtifactCollection().getItems().stream().map(a->new Mapping(a.getArtifactName(),a.getArtifactId())).toList()));
                }
            }
            var artifacts=new ArrayList<Artifact>();
            try { for(var a:client.getPaginators().listDeployArtifactsRecordIterator(ListDeployArtifactsRequest.builder().projectId(p.getProjectId()).build())) {
                if(a.getLifecycleState()!=DeployArtifact.LifecycleState.Active) continue;
                String location="";
                if(a.getDeployArtifactSource() instanceof GenericDeployArtifactSource src) location=src.getRepositoryId()+" / "+src.getDeployArtifactPath()+" : "+src.getDeployArtifactVersion();
                else if(a.getDeployArtifactSource() instanceof OcirDeployArtifactSource src) location=src.getImageUri();
                else continue;
                artifacts.add(new Artifact(a.getId(),a.getDisplayName(),a.getDeployArtifactType().getValue(),location));
            }
            } catch(Exception e) { warnings.add("산출물 참조 조회 실패 · DevOps 산출물 읽기 권한 확인"); }
            var repositories=new ArrayList<Choice>();
            try(var registry=registry(c,region)) {
            for(var repo:registry.getPaginators().listRepositoriesRecordIterator(com.oracle.bmc.artifacts.requests.ListRepositoriesRequest.builder().compartmentId(comp).build()))
                if(repo.getLifecycleState()==com.oracle.bmc.artifacts.model.Repository.LifecycleState.Available)
                    repositories.add(new Choice(repo.getId(),repo.getDisplayName()));
            } catch(Exception e) { warnings.add("Registry 조회 실패 · artifact-repositories 읽기 권한 확인"); }
            return new Settings(pipelineResponse.getEtag(),p.getDisplayName(),Objects.toString(p.getDescription(),""),stages,artifacts,repositories,warnings,deliveries);
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public void rename(Connection c,String region,String comp,String id,String etag,String name,String description) {
        name(name);if(description.length()>400)throw Errors.ADMIN_INPUT.exception();
        try(var client=clients.client(c,region)) {
            pipeline(client,comp,id);
            client.updateBuildPipeline(UpdateBuildPipelineRequest.builder().buildPipelineId(id).ifMatch(etag)
                .updateBuildPipelineDetails(UpdateBuildPipelineDetails.builder().displayName(name).description(description).build()).build());
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.BUILD_CHANGED.exception(); }
    }
    public void updateBuild(Connection c,String region,String comp,String pipeline,String stage,String etag,String name,String spec,int timeout,String branch) {
        name(name);
        if(spec.isBlank() || spec.length()>1024 || spec.startsWith("/") || spec.contains("..") || timeout<60 || timeout>36000 || branch.length()>255 || branch.isBlank() || branch.chars().anyMatch(Character::isISOControl))throw Errors.BUILD_PARAMETERS.exception();
        try(var client=clients.client(c,region)) {
            pipeline(client,comp,pipeline);
            var response=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(stage).build());
            if(!(response.getBuildPipelineStage() instanceof BuildStage b) || !comp.equals(b.getCompartmentId()) || !pipeline.equals(b.getBuildPipelineId())) throw Errors.SCOPE.exception();
            if(!Objects.equals(etag,response.getEtag()))throw Errors.BUILD_CHANGED.exception();
            var primary=primary(b);if(primary==null)throw Errors.BUILD_PARAMETERS.exception();
            var sources=b.getBuildSourceCollection().getItems().stream().map(src->src.getName().equals(primary.getName())?branch(src,branch):src).toList();
            client.updateBuildPipelineStage(UpdateBuildPipelineStageRequest.builder().buildPipelineStageId(stage).ifMatch(etag)
                .updateBuildPipelineStageDetails(UpdateBuildStageDetails.builder().displayName(name).buildSpecFile(spec).stageExecutionTimeoutInSeconds(timeout)
                    .buildSourceCollection(BuildSourceCollection.builder().items(sources).build()).build()).build());
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.BUILD_CHANGED.exception(); }
    }
    public void updateDelivery(Connection c,String region,String comp,String pipeline,String stage,String etag,String output,String artifact) {
        try(var client=clients.client(c,region)) {
            var p=pipeline(client,comp,pipeline);
            var response=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(stage).build());
            if(!(response.getBuildPipelineStage() instanceof DeliverArtifactStage d) || !comp.equals(d.getCompartmentId()) || !pipeline.equals(d.getBuildPipelineId()))throw Errors.SCOPE.exception();
            if(!Objects.equals(etag,response.getEtag()))throw Errors.BUILD_CHANGED.exception();
            var old=d.getDeliverArtifactCollection().getItems().stream().filter(a->a.getArtifactName().equals(output)).findFirst().orElseThrow(Errors.OUTPUT_UNKNOWN::exception);
            var previous=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(old.getArtifactId()).build()).getDeployArtifact();
            var target=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(artifact).build()).getDeployArtifact();
            if(!p.getProjectId().equals(target.getProjectId()) || target.getLifecycleState()!=DeployArtifact.LifecycleState.Active || previous.getDeployArtifactType()!=target.getDeployArtifactType())throw Errors.BUILD_PARAMETERS.exception();
            var items=d.getDeliverArtifactCollection().getItems().stream().map(a->a.getArtifactName().equals(output)?DeliverArtifact.builder().artifactName(output).artifactId(artifact).build():a).toList();
            client.updateBuildPipelineStage(UpdateBuildPipelineStageRequest.builder().buildPipelineStageId(stage).ifMatch(etag)
                .updateBuildPipelineStageDetails(UpdateDeliverArtifactStageDetails.builder().deliverArtifactCollection(DeliverArtifactCollection.builder().items(items).build()).build()).build());
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.BUILD_CHANGED.exception(); }
    }
    public String createRepository(Connection c,String region,String comp,String name,String token) {
        name(name);
        try(var client=registry(c,region)) {
            return client.createRepository(com.oracle.bmc.artifacts.requests.CreateRepositoryRequest.builder().opcRetryToken(token)
                .createRepositoryDetails(com.oracle.bmc.artifacts.model.CreateGenericRepositoryDetails.builder().compartmentId(comp).displayName(name).isImmutable(true).build()).build())
                .getRepository().getId();
        } catch(Exception e) { throw Errors.ADMIN_UNKNOWN.exception(); }
    }
    public String createArtifact(Connection c,String region,String comp,String pipeline,String name,String type,String repository,String path,String version,String token) {
        name(name);if(path.isBlank() || path.length()>1024 || version.isBlank() || version.length()>255 || path.chars().anyMatch(Character::isISOControl) || version.chars().anyMatch(Character::isISOControl))throw Errors.BUILD_PARAMETERS.exception();
        try(var client=clients.client(c,region)) {
            var p=pipeline(client,comp,pipeline);DeployArtifactSource source;DeployArtifact.DeployArtifactType artifactType;
            if(type.equals("BINARY")) {
                try(var registry=registry(c,region)) {
                    var repo=registry.getRepository(com.oracle.bmc.artifacts.requests.GetRepositoryRequest.builder().repositoryId(repository).build()).getRepository();
                    if(!comp.equals(repo.getCompartmentId()) || repo.getLifecycleState()!=com.oracle.bmc.artifacts.model.Repository.LifecycleState.Available)throw Errors.SCOPE.exception();
                }
                artifactType=DeployArtifact.DeployArtifactType.GenericFile;
                source=GenericDeployArtifactSource.builder().repositoryId(repository).deployArtifactPath(path).deployArtifactVersion(version).build();
            } else if(type.equals("DOCKER_IMAGE")) {
                if(!path.matches("[a-zA-Z0-9.-]+/[a-zA-Z0-9_./-]+") || path.contains("..") || path.contains("@"))throw Errors.BUILD_PARAMETERS.exception();
                artifactType=DeployArtifact.DeployArtifactType.DockerImage;
                source=OcirDeployArtifactSource.builder().imageUri(path+":"+version).build();
            } else throw Errors.BUILD_PARAMETERS.exception();
            return client.createDeployArtifact(CreateDeployArtifactRequest.builder().opcRetryToken(token).createDeployArtifactDetails(CreateDeployArtifactDetails.builder()
                .projectId(p.getProjectId()).displayName(name).deployArtifactType(artifactType).deployArtifactSource(source)
                .argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.SubstitutePlaceholders).build()).build()).getDeployArtifact().getId();
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.ADMIN_UNKNOWN.exception(); }
    }
    public String deliver(Connection c,String region,String comp,String pipeline,String revision,String predecessor,String output,String artifact,String token) {
        var definition=definitions.read(c,region,comp,pipeline);
        if(!definition.revision().equals(revision))throw Errors.BUILD_CHANGED.exception();
        var stage=definition.stages().stream().filter(s->s.id().equals(predecessor) && !s.spec().isEmpty()).findFirst().orElseThrow(Errors.BUILD_PARAMETERS::exception);
        // Do not silently branch around existing tests/approvals/deployment. Only append after a terminal managed build.
        if(definition.stages().stream().anyMatch(s->s.after().contains(stage.name()))) throw Errors.DELIVERY_ORDER.exception();
        var source=stage.sources().stream().filter(BuildDefinition.Source::primary).findFirst().orElse(stage.sources().isEmpty()?null:stage.sources().getFirst());
        if(source==null)throw Errors.BUILD_PARAMETERS.exception();
        var outputs=definitions.specification(c,region,source,stage.spec()).outputs();
        var match=outputs.stream().map(o->o.split(" · ",3)).filter(o->o.length==3 && o[0].equals(output)).findFirst().orElseThrow(Errors.OUTPUT_UNKNOWN::exception);
        try(var client=clients.client(c,region)) {
            var p=pipeline(client,comp,pipeline);
            var a=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(artifact).build()).getDeployArtifact();
            if(!p.getProjectId().equals(a.getProjectId()) || a.getLifecycleState()!=DeployArtifact.LifecycleState.Active)throw Errors.SCOPE.exception();
            if(!(match[1].equals("BINARY") && a.getDeployArtifactType()==DeployArtifact.DeployArtifactType.GenericFile
                || match[1].equals("DOCKER_IMAGE") && a.getDeployArtifactType()==DeployArtifact.DeployArtifactType.DockerImage))throw Errors.BUILD_PARAMETERS.exception();
            return client.createBuildPipelineStage(CreateBuildPipelineStageRequest.builder().opcRetryToken(token).createBuildPipelineStageDetails(
                CreateDeliverArtifactStageDetails.builder().buildPipelineId(pipeline).displayName("Registry · "+output)
                    .buildPipelineStagePredecessorCollection(BuildPipelineStagePredecessorCollection.builder().items(List.of(BuildPipelineStagePredecessor.builder().id(predecessor).build())).build())
                    .deliverArtifactCollection(DeliverArtifactCollection.builder().items(List.of(DeliverArtifact.builder().artifactName(output).artifactId(artifact).build())).build()).build()).build())
                .getBuildPipelineStage().getId();
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.ADMIN_UNKNOWN.exception(); }
    }
    private static void name(String name) { if(name.isBlank() || name.length()>255 || name.chars().anyMatch(Character::isISOControl))throw Errors.ADMIN_INPUT.exception(); }
    private static BuildSource primary(BuildStage stage) {
        if(stage.getBuildSourceCollection()==null || stage.getBuildSourceCollection().getItems().isEmpty())return null;
        return stage.getBuildSourceCollection().getItems().stream().filter(s->s.getName().equals(stage.getPrimaryBuildSource())).findFirst().orElse(stage.getBuildSourceCollection().getItems().getFirst());
    }
    private static BuildSource branch(BuildSource s,String branch) {
        if(s instanceof DevopsCodeRepositoryBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof GithubBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof GitlabBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof GitlabServerBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof BitbucketCloudBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof BitbucketServerBuildSource v)return v.toBuilder().branch(branch).build();
        if(s instanceof VbsBuildSource v)return v.toBuilder().branch(branch).build();
        throw Errors.BUILD_PARAMETERS.exception();
    }
}
