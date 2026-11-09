package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.CreateBuildRunDetails;
import com.oracle.bmc.devops.model.CreateDeployPipelineDeploymentDetails;
import com.oracle.bmc.devops.model.ComputeInstanceGroupDeployStageSummary;
import com.oracle.bmc.devops.model.ManualApprovalDeployStageSummary;
import com.oracle.bmc.devops.model.WaitDeployStageSummary;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("!demo")
public class OciCloud implements CloudConnection, PipelineGateway {
    private final String configPath;
    private final DeploymentTarget vm;
    private final BuildProjectResources resources;
    private static final ClientConfiguration CONFIG=ClientConfiguration.builder()
        .connectionTimeoutMillis(5000).readTimeoutMillis(30000)
        .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
    public OciCloud(@Value("${companion.oci-config}") String configPath, DeploymentTarget vm, BuildProjectResources resources) { this.configPath=configPath; this.vm=vm;this.resources=resources; }
    public List<String> profiles() {
        var names=new LinkedHashSet<String>();
        var section=Pattern.compile("^\\[([^\\]\\r\\n]{1,128})\\]\\s*(?:#.*)?$");
        try (var lines=Files.lines(Path.of(configPath))) {
            lines.forEach(line -> { var matcher=section.matcher(line.trim()); if (matcher.matches()) names.add(matcher.group(1)); });
            return List.copyOf(names);
        } catch (IOException e) { return List.of(); }
    }
    private ConfigFileAuthenticationDetailsProvider provider(String profile) throws IOException {
        if (!profiles().contains(profile)) throw Errors.CONFIG.exception();
        return new ConfigFileAuthenticationDetailsProvider(configPath,profile);
    }
    public Connection connect(String profile) {
        try {
            var auth=provider(profile);
            try (var identity=IdentityClient.builder().configuration(CONFIG).build(auth)) {
                var tenancy=identity.getTenancy(GetTenancyRequest.builder().tenancyId(auth.getTenantId()).build()).getTenancy();
                return new Connection(profile,auth.getTenantId(),auth.getUserId(),auth.getRegion().getRegionId(),tenancy.getName(),false);
            }
        } catch (Exception e) { throw Errors.CONFIG.exception(); }
    }
    private DevopsClient client(Connection c) throws IOException {
        var auth=provider(c.profile());
        if (!auth.getTenantId().equals(c.tenancy()) || !auth.getUserId().equals(c.user()) || !auth.getRegion().getRegionId().equals(c.region())) throw Errors.SCOPE.exception();
        return DevopsClient.builder().configuration(CONFIG).build(auth);
    }
    public List<Pipeline> pipelines(Connection c,String compartment) {
        try (var client=client(c)) {
            var result=new ArrayList<Pipeline>();
            for (var p:client.getPaginators().listBuildPipelinesRecordIterator(ListBuildPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Pipeline(p.getId(),p.getDisplayName(),"BUILD"));
            for (var p:client.getPaginators().listDeployPipelinesRecordIterator(ListDeployPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Pipeline(p.getId(),p.getDisplayName(),"DEPLOY"));
            return result;
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
    public void validate(Connection c,Workspace w,String kind) {
        try (var client=client(c)) {
            if (kind.equals("BUILD")) {
                var pipeline=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(w.buildPipelineId()).build()).getBuildPipeline();
                if (!pipeline.getCompartmentId().equals(w.compartmentId())) throw Errors.INVALID.exception();
            } else {
                var pipeline=client.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(w.deployPipelineId()).build()).getDeployPipeline();
                if (!pipeline.getCompartmentId().equals(w.compartmentId())) throw Errors.INVALID.exception();
                var types=new ArrayList<String>();
                for (var stage:client.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(w.deployPipelineId()).build())) {
                    types.add(stage instanceof ComputeInstanceGroupDeployStageSummary ? "COMPUTE_INSTANCE_GROUP_ROLLING_DEPLOYMENT"
                        : stage instanceof ManualApprovalDeployStageSummary ? "MANUAL_APPROVAL"
                        : stage instanceof WaitDeployStageSummary ? "WAIT" : "UNSUPPORTED");
                }
                vm.validateStages(types);
            }
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
    public RemoteRun start(Connection c,String kind,String id,String token) {
        try (var client=client(c)) {
            if (kind.equals("BUILD")) {
                var pipeline=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(id).build()).getBuildPipeline();
                io.github.devmrko.companion.service.BuildLogChecks.requireReady(resources.logReadiness(c,c.region(),pipeline.getCompartmentId(),pipeline.getProjectId()));
                var run=client.createBuildRun(CreateBuildRunRequest.builder().opcRetryToken(token)
                    .createBuildRunDetails(CreateBuildRunDetails.builder().buildPipelineId(id).displayName("companion-"+token).build()).build()).getBuildRun();
                return new RemoteRun(run.getId(),run.getLifecycleState().getValue());
            }
            var run=client.createDeployment(CreateDeploymentRequest.builder().opcRetryToken(token)
                .createDeploymentDetails(CreateDeployPipelineDeploymentDetails.builder().deployPipelineId(id).displayName("companion-"+token).build()).build()).getDeployment();
            return new RemoteRun(run.getId(),run.getLifecycleState().getValue());
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
    public RemoteRun status(Connection c,String kind,String id) {
        try (var client=client(c)) {
            if (kind.equals("BUILD")) {
                var run=client.getBuildRun(GetBuildRunRequest.builder().buildRunId(id).build()).getBuildRun();
                return new RemoteRun(run.getId(),run.getLifecycleState().getValue());
            }
            var run=client.getDeployment(GetDeploymentRequest.builder().deploymentId(id).build()).getDeployment();
            return new RemoteRun(run.getId(),run.getLifecycleState().getValue());
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
}
