package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildHistory;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OciBuildHistory implements BuildHistory {
    private final String path;
    public OciBuildHistory(@Value("${companion.oci-config}") String path) { this.path=path; }
    ConfigFileAuthenticationDetailsProvider authentication(Connection c) throws java.io.IOException {
        var auth=new ConfigFileAuthenticationDetailsProvider(path,c.profile());
        if (!auth.getTenantId().equals(c.tenancy()) || !auth.getUserId().equals(c.user())) throw Errors.SCOPE.exception();
        return auth;
    }
    DevopsClient client(Connection c,String region) throws java.io.IOException {
        var auth=authentication(c);
        var client=DevopsClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000)
            .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(auth);
        client.setRegion(region);return client;
    }
    public List<Pipeline> pipelines(Connection c,String region,String compartment) {
        try(var client=client(c,region)) {
            var result=new ArrayList<Pipeline>();var projects=new HashMap<String,String>();
            for(var p:client.getPaginators().listBuildPipelinesRecordIterator(ListBuildPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Pipeline(p.getId(),p.getDisplayName(),p.getLifecycleState().getValue(),p.getProjectId(),projects.computeIfAbsent(p.getProjectId(),id->{ try { return client.getProject(GetProjectRequest.builder().projectId(id).build()).getProject().getName(); } catch(Exception e) { return id; } }),p.getDescription()));
            result.sort(Comparator.comparing(p->Objects.toString(p.name(),"")));return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Run> runs(Connection c,String region,String compartment,String pipeline) {
        try(var client=client(c,region)) {
            var p=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(pipeline).build()).getBuildPipeline();
            if(!compartment.equals(p.getCompartmentId())) throw Errors.SCOPE.exception();
            var result=new ArrayList<Run>();
            for(var r:client.getPaginators().listBuildRunsRecordIterator(ListBuildRunsRequest.builder().buildPipelineId(pipeline).build()))
                result.add(new Run(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),r.getTimeCreated().toInstant().toString()));
            result.sort(Comparator.comparing(Run::createdAt).reversed());return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public io.github.devmrko.companion.service.TestReports.Summary reports(Connection c,String region,String compartment,String pipeline,String run,String artifact) {
        var detail=detail(c,region,compartment,pipeline,run);
        if(detail.artifacts().stream().noneMatch(a->a.id().equals(artifact)&&a.kind().equals("Artifact Registry")&&a.location().endsWith(".tar.gz")))throw Errors.SCOPE.exception();
        try(var client=com.oracle.bmc.genericartifactscontent.GenericArtifactsContentClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).build()).build(authentication(c))) {
            client.setRegion(region);var response=client.getGenericArtifactContent(com.oracle.bmc.genericartifactscontent.requests.GetGenericArtifactContentRequest.builder().artifactId(artifact).build());
            try(var in=response.getInputStream()){return io.github.devmrko.companion.service.TestReports.read(in);}
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Detail detail(Connection c,String region,String compartment,String pipeline,String run) {
        try(var client=client(c,region)) {
            var r=client.getBuildRun(GetBuildRunRequest.builder().buildRunId(run).build()).getBuildRun();
            if(!compartment.equals(r.getCompartmentId()) || !pipeline.equals(r.getBuildPipelineId())) throw Errors.SCOPE.exception();
            var steps=new ArrayList<Step>();var artifacts=new ArrayList<StoredArtifact>();
            if(r.getBuildRunProgress()!=null && r.getBuildRunProgress().getBuildPipelineStageRunProgress()!=null)
                for(var stage:r.getBuildRunProgress().getBuildPipelineStageRunProgress().values()) {
                    steps.add(new Step(stage.getStageDisplayName(),stage.getStatus()==null?"—":stage.getStatus().getValue(),
                        stage instanceof com.oracle.bmc.devops.model.BuildStageRunProgress?"BUILD":stage instanceof com.oracle.bmc.devops.model.DeliverArtifactStageRunProgress?"DELIVER":"OTHER"));
                    if(stage instanceof com.oracle.bmc.devops.model.DeliverArtifactStageRunProgress delivery && delivery.getDeliveredArtifacts()!=null)
                        for(var artifact:delivery.getDeliveredArtifacts().getItems()) {
                            if(artifact instanceof com.oracle.bmc.devops.model.GenericDeliveredArtifact a)
                                artifacts.add(new StoredArtifact(a.getOutputArtifactName(),"Artifact Registry",a.getPath(),a.getVersion(),a.getDeliveredArtifactId()));
                            else if(artifact instanceof com.oracle.bmc.devops.model.ContainerRegistryDeliveredArtifact a)
                                artifacts.add(new StoredArtifact(a.getOutputArtifactName(),"Container Registry",a.getImageUri(),a.getDeliveredArtifactHash(),a.getDeployArtifactId()));
                        }
                    if(stage instanceof com.oracle.bmc.devops.model.BuildStageRunProgress build && build.getSteps()!=null)
                        for(var step:build.getSteps()) steps.add(new Step(step.getName(),step.getState()==null?"—":step.getState().getValue()));
                }
            var source=r.getBuildRunSource();String origin="기록 없음";
            if(source instanceof com.oracle.bmc.devops.model.ManualBuildRunSource)origin="수동 / API";
            else if(source instanceof com.oracle.bmc.devops.model.DevopsCodeRepositoryBuildRunSource x)origin="OCI Git 트리거 · "+(x.getTriggerInfo()==null?x.getTriggerId():x.getTriggerInfo().getDisplayName());
            else if(source instanceof com.oracle.bmc.devops.model.GithubBuildRunSource)origin="GitHub 트리거";
            else if(source instanceof com.oracle.bmc.devops.model.GitlabBuildRunSource)origin="GitLab 트리거";
            else if(source!=null)origin="외부 소스 트리거";
            var commit=r.getCommitInfo();
            return new Detail(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),r.getLifecycleDetails(),steps,artifacts,origin,
                commit==null?"":Objects.toString(commit.getRepositoryUrl(),""),commit==null?"":Objects.toString(commit.getRepositoryBranch(),""),commit==null?"":Objects.toString(commit.getCommitHash(),""));
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
}
