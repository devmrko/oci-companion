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
    DevopsClient client(Connection c,String region) throws java.io.IOException {
        var auth=new ConfigFileAuthenticationDetailsProvider(path,c.profile());
        if (!auth.getTenantId().equals(c.tenancy()) || !auth.getUserId().equals(c.user())) throw Errors.SCOPE.exception();
        var client=DevopsClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000)
            .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(auth);
        client.setRegion(region);return client;
    }
    public List<Pipeline> pipelines(Connection c,String region,String compartment) {
        try(var client=client(c,region)) {
            var result=new ArrayList<Pipeline>();
            for(var p:client.getPaginators().listBuildPipelinesRecordIterator(ListBuildPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Pipeline(p.getId(),p.getDisplayName(),p.getLifecycleState().getValue()));
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
    public Detail detail(Connection c,String region,String compartment,String pipeline,String run) {
        try(var client=client(c,region)) {
            var r=client.getBuildRun(GetBuildRunRequest.builder().buildRunId(run).build()).getBuildRun();
            if(!compartment.equals(r.getCompartmentId()) || !pipeline.equals(r.getBuildPipelineId())) throw Errors.SCOPE.exception();
            var steps=new ArrayList<Step>();
            if(r.getBuildRunProgress()!=null && r.getBuildRunProgress().getBuildPipelineStageRunProgress()!=null)
                for(var stage:r.getBuildRunProgress().getBuildPipelineStageRunProgress().values()) {
                    steps.add(new Step(stage.getStageDisplayName(),stage.getStatus()==null?"—":stage.getStatus().getValue()));
                    if(stage instanceof com.oracle.bmc.devops.model.BuildStageRunProgress build && build.getSteps()!=null)
                        for(var step:build.getSteps()) steps.add(new Step(step.getName(),step.getState()==null?"—":step.getState().getValue()));
                }
            return new Detail(r.getId(),r.getDisplayName(),r.getLifecycleState().getValue(),r.getLifecycleDetails(),steps);
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
}
