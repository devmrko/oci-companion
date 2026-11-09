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
import io.github.devmrko.companion.error.BusinessException;
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
    public BuildOptions buildOptions(Connection c,String region,String comp,String id) {
        try(var client=clients.client(c,region)) {
            var p=pipeline(client,comp,id);var sources=new ArrayList<SourceOption>();var warnings=new ArrayList<String>();
            try {
                for(var r:client.getPaginators().listRepositoriesRecordIterator(ListRepositoriesRequest.builder().projectId(p.getProjectId()).build()))
                    if(r.getLifecycleState()==Repository.LifecycleState.Active)
                        sources.add(new SourceOption(r.getId(),r.getName(),"OCI",Objects.toString(r.getHttpUrl(),""),Objects.toString(r.getDefaultBranch(),"main")));
            }catch(Exception e){warnings.add("OCI 소스 조회 실패 · 저장소 읽기 권한 확인");}
            try {
                for(var r:client.getPaginators().listConnectionsRecordIterator(ListConnectionsRequest.builder().projectId(p.getProjectId()).build())) {
                    String provider=r instanceof GithubAccessTokenConnectionSummary?"GitHub":r instanceof GitlabAccessTokenConnectionSummary?"GitLab":"";
                    if(!provider.isEmpty() && r.getLifecycleState()==com.oracle.bmc.devops.model.Connection.LifecycleState.Active)
                        sources.add(new SourceOption(r.getId(),r.getDisplayName(),provider,"","main"));
                }
            }catch(Exception e){warnings.add("외부 연결 조회 실패 · DevOps connection 읽기 권한 확인");}
            return new BuildOptions(sources,Arrays.stream(BuildStage.Image.values()).filter(v->v!=BuildStage.Image.UnknownEnumValue).map(BuildStage.Image::getValue).toList(),warnings);
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public String createBuild(Connection c,String region,String comp,String id,String revision,NewBuild input,String token) {
        name(input.name());
        if(input.spec().isBlank() || input.spec().length()>1024 || input.spec().startsWith("/") || input.spec().contains("..")
            || input.timeout()<60 || input.timeout()>36000 || input.branch().isBlank() || input.branch().length()>255
            || input.branch().chars().anyMatch(Character::isISOControl))throw Errors.BUILD_STAGE_INPUT.exception();
        var definition=definitions.read(c,region,comp,id);
        if(!Objects.equals(revision,definition.revision()))throw Errors.BUILD_CHANGED.exception();
        if(!"ACTIVE".equals(definition.state()))throw Errors.BUILD_CHANGED.exception();
        if(!definition.stages().isEmpty()) {
            var previous=definition.stages().stream().filter(s->s.id().equals(input.predecessor()) && "ACTIVE".equals(s.state())).findFirst().orElseThrow(Errors.BUILD_STAGE_INPUT::exception);
            if(definition.stages().stream().anyMatch(s->s.after().contains(previous.name())))throw Errors.DELIVERY_ORDER.exception();
        }else if(!input.predecessor().isEmpty())throw Errors.BUILD_STAGE_INPUT.exception();
        var options=buildOptions(c,region,comp,id);
        var choice=options.sources().stream().filter(s->s.id().equals(input.source())).findFirst().orElseThrow(Errors.BUILD_STAGE_INPUT::exception);
        if(!options.images().contains(input.image()))throw Errors.BUILD_STAGE_INPUT.exception();
        BuildSource source;
        if(choice.provider().equals("OCI")) {
            var ref=new BuildDefinition.Source("","source","OCI",choice.id(),choice.url(),input.branch(),true);
            if(definitions.branches(c,region,ref).stream().noneMatch(b->b.value().equals(input.branch())))throw Errors.BUILD_STAGE_INPUT.exception();
            var spec=definitions.specification(c,region,ref,input.spec());
            if(spec.content().isBlank())throw Errors.BUILD_STAGE_INPUT.exception();
            validateSpecification(spec.content());
            source=DevopsCodeRepositoryBuildSource.builder().name("source").repositoryId(choice.id()).repositoryUrl(choice.url()).branch(input.branch()).build();
        }else {
            try {
                var url=java.net.URI.create(input.url());
                String host=choice.provider().equals("GitHub")?"github.com":"gitlab.com";
                if(!"https".equals(url.getScheme()) || !host.equals(url.getHost()) || url.getUserInfo()!=null || url.getQuery()!=null || url.getFragment()!=null || url.getPort()!=-1 || url.getPath().split("/").length<3)throw Errors.BUILD_STAGE_INPUT.exception();
            }catch(IllegalArgumentException e){throw Errors.BUILD_STAGE_INPUT.exception();}
            source=choice.provider().equals("GitHub")?GithubBuildSource.builder().name("source").connectionId(choice.id()).repositoryUrl(input.url()).branch(input.branch()).build():
                GitlabBuildSource.builder().name("source").connectionId(choice.id()).repositoryUrl(input.url()).branch(input.branch()).build();
        }
        try(var client=clients.client(c,region)) {
            pipeline(client,comp,id);
            return client.createBuildPipelineStage(CreateBuildPipelineStageRequest.builder().opcRetryToken(token).createBuildPipelineStageDetails(
                CreateBuildStageDetails.builder().buildPipelineId(id).displayName(input.name()).buildSpecFile(input.spec()).image(BuildStage.Image.create(input.image()))
                    .stageExecutionTimeoutInSeconds(input.timeout()).primaryBuildSource("source").buildSourceCollection(BuildSourceCollection.builder().items(List.of(source)).build())
                    .buildPipelineStagePredecessorCollection(BuildPipelineStagePredecessorCollection.builder().items(List.of(BuildPipelineStagePredecessor.builder()
                        .id(definition.stages().isEmpty()?id:input.predecessor()).build())).build()).build()).build()).getBuildPipelineStage().getId();
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    private static void validateSpecification(String content) {
        try {
            var options=new org.yaml.snakeyaml.LoaderOptions();options.setCodePointLimit(65536);options.setMaxAliasesForCollections(20);
            Object value=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options)).load(content);
            if(!(value instanceof Map<?,?> m) || !"0.1".equals(Objects.toString(m.get("version"),"")) || !"build".equals(m.get("component")) || !(m.get("steps") instanceof List<?> steps) || steps.isEmpty())throw Errors.BUILD_STAGE_INPUT.exception();
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.BUILD_STAGE_INPUT.exception();}
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
                if(a.getDeployArtifactSource() instanceof GenericDeployArtifactSource src)
                    artifacts.add(new Artifact(a.getId(),a.getDisplayName(),a.getDeployArtifactType().getValue(),src.getRepositoryId()+" / "+src.getDeployArtifactPath()+" : "+src.getDeployArtifactVersion(),src.getRepositoryId(),src.getDeployArtifactPath(),src.getDeployArtifactVersion()));
                else if(a.getDeployArtifactSource() instanceof OcirDeployArtifactSource src) {
                    String uri=src.getImageUri();int colon=uri.lastIndexOf(':');
                    artifacts.add(new Artifact(a.getId(),a.getDisplayName(),a.getDeployArtifactType().getValue(),uri,"","",colon>=0&&!uri.contains("@")?uri.substring(colon+1):""));
                }
            }
            } catch(Exception e) { warnings.add("산출물 참조 조회 실패 · DevOps 산출물 읽기 권한 확인"); }
            var repositories=new ArrayList<Choice>();
            try(var registry=registry(c,region)) {
            for(var repo:registry.getPaginators().listRepositoriesRecordIterator(com.oracle.bmc.artifacts.requests.ListRepositoriesRequest.builder().compartmentId(comp).build()))
                if(repo.getLifecycleState()==com.oracle.bmc.artifacts.model.Repository.LifecycleState.Available)
                    repositories.add(new Choice(repo.getId(),repo.getDisplayName()));
            } catch(Exception e) { warnings.add("Registry 조회 실패 · artifact-repositories 읽기 권한 확인"); }
            if(artifacts.stream().anyMatch(a->a.type().equals("DOCKER_IMAGE"))) {
                try(var registry=registry(c,region)) {
                    var containers=new ArrayList<com.oracle.bmc.artifacts.model.ContainerRepositorySummary>();
                    registry.getPaginators().listContainerRepositoriesRecordIterator(com.oracle.bmc.artifacts.requests.ListContainerRepositoriesRequest.builder().compartmentId(comp).build()).forEach(containers::add);
                    String endpoint=com.oracle.bmc.Region.fromRegionId(region).getRegionCode()+".ocir.io/";
                    for(int i=0;i<artifacts.size();i++) {
                        var a=artifacts.get(i);if(!a.type().equals("DOCKER_IMAGE"))continue;
                        for(var repo:containers) {
                            String uri=endpoint+repo.getNamespace()+"/"+repo.getDisplayName();
                            if(a.location().startsWith(uri+":") || a.location().startsWith(uri+"@")) {
                                artifacts.set(i,new Artifact(a.id(),a.name(),a.type(),a.location(),repo.getId(),"",a.version()));break;
                            }
                        }
                    }
                }catch(Exception e){warnings.add("이미지 저장소 선택값 조회 실패 · 현재 URI 확인");}
            }
            return new Settings(pipelineResponse.getEtag(),p.getDisplayName(),Objects.toString(p.getDescription(),""),stages,artifacts,repositories,warnings,deliveries);
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public void rename(Connection c,String region,String comp,String id,String etag,String name,String description) {
        name(name);if(description.length()>400)throw Errors.ADMIN_INPUT.exception();
        try(var client=clients.client(c,region)) {
            pipeline(client,comp,id);
            client.updateBuildPipeline(UpdateBuildPipelineRequest.builder().buildPipelineId(id).ifMatch(etag)
                .updateBuildPipelineDetails(UpdateBuildPipelineDetails.builder().displayName(name).description(description.isBlank()?null:description).build()).build());
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
    public void destination(Connection c,String region,String comp,String pipeline,String stage,String etag,String output,String repository,String path,String version,String token) {
        try(var client=clients.client(c,region)) {
            var p=pipeline(client,comp,pipeline);
            var response=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(stage).build());
            if(!(response.getBuildPipelineStage() instanceof DeliverArtifactStage d) || !comp.equals(d.getCompartmentId()) || !pipeline.equals(d.getBuildPipelineId()))throw Errors.SCOPE.exception();
            if(!Objects.equals(etag,response.getEtag()))throw Errors.BUILD_CHANGED.exception();
            var mapping=d.getDeliverArtifactCollection().getItems().stream().filter(a->a.getArtifactName().equals(output)).findFirst().orElseThrow(Errors.OUTPUT_UNKNOWN::exception);
            var previous=client.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(mapping.getArtifactId()).build()).getDeployArtifact();
            if(!p.getProjectId().equals(previous.getProjectId()))throw Errors.SCOPE.exception();
            String type;
            if(previous.getDeployArtifactType()==DeployArtifact.DeployArtifactType.GenericFile) {
                type="BINARY";
                if(previous.getDeployArtifactSource() instanceof GenericDeployArtifactSource old && repository.equals(old.getRepositoryId()) && path.equals(old.getDeployArtifactPath()) && version.equals(old.getDeployArtifactVersion()))return;
            } else if(previous.getDeployArtifactType()==DeployArtifact.DeployArtifactType.DockerImage)type="DOCKER_IMAGE";
            else throw Errors.BUILD_PARAMETERS.exception();
            // Create an independent reference: other pipelines may share the current artifact.
            String id=createArtifact(c,region,comp,pipeline,"build-output-"+token,type,repository,path,version,token);
            try {updateDelivery(c,region,comp,pipeline,stage,etag,output,id);}
            catch(Exception e){throw Errors.DESTINATION_LINK.exception();}
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
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
        name(name);if((type.equals("BINARY") && path.isBlank()) || path.length()>1024 || version.isBlank() || version.length()>255 || path.chars().anyMatch(Character::isISOControl) || version.chars().anyMatch(Character::isISOControl))throw Errors.BUILD_PARAMETERS.exception();
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
                try(var registry=registry(c,region)) {
                    var repo=registry.getContainerRepository(com.oracle.bmc.artifacts.requests.GetContainerRepositoryRequest.builder().repositoryId(repository).build()).getContainerRepository();
                    if(!comp.equals(repo.getCompartmentId()) || repo.getLifecycleState()!=com.oracle.bmc.artifacts.model.ContainerRepository.LifecycleState.Available)throw Errors.SCOPE.exception();
                    var selectedRegion=com.oracle.bmc.Region.fromRegionId(region);
                    if(!selectedRegion.getRealm().getRealmId().equals("oc1"))throw Errors.REGISTRY_INPUT.exception();
                    path=selectedRegion.getRegionCode()+".ocir.io/"+repo.getNamespace()+"/"+repo.getDisplayName();
                }
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
        // Add mappings to an existing direct delivery stage without changing execution order.
        var successors=definition.stages().stream().filter(s->s.after().contains(stage.name())).toList();
        if(successors.size()>1)throw Errors.DELIVERY_ORDER.exception();
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
            if(!successors.isEmpty()){
                var response=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(successors.getFirst().id()).build());
                if(!(response.getBuildPipelineStage() instanceof DeliverArtifactStage delivery)||!pipeline.equals(delivery.getBuildPipelineId())||!comp.equals(delivery.getCompartmentId())||delivery.getBuildPipelineStagePredecessorCollection()==null||delivery.getBuildPipelineStagePredecessorCollection().getItems().size()!=1||!predecessor.equals(delivery.getBuildPipelineStagePredecessorCollection().getItems().getFirst().getId()))throw Errors.DELIVERY_ORDER.exception();
                var items=new ArrayList<>(delivery.getDeliverArtifactCollection().getItems());
                if(items.stream().anyMatch(item->item.getArtifactName().equals(output)))throw Errors.BUILD_PARAMETERS.exception();
                items.add(DeliverArtifact.builder().artifactName(output).artifactId(artifact).build());
                client.updateBuildPipelineStage(UpdateBuildPipelineStageRequest.builder().buildPipelineStageId(delivery.getId()).ifMatch(response.getEtag()).updateBuildPipelineStageDetails(UpdateDeliverArtifactStageDetails.builder().deliverArtifactCollection(DeliverArtifactCollection.builder().items(items).build()).build()).build());return delivery.getId();
            }
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
