package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildControl;
import io.github.devmrko.companion.port.BuildDefinition;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciBuildControl implements BuildControl {
    private final OciBuildHistory clients;
    private final BuildDefinition definitions;
    public OciBuildControl(OciBuildHistory clients,BuildDefinition definitions) { this.clients=clients;this.definitions=definitions; }
    public void cancel(Connection c,String region,String compartment,String pipeline,String run) {
        try(var client=clients.client(c,region)) {
            var response=client.getBuildRun(GetBuildRunRequest.builder().buildRunId(run).build());var r=response.getBuildRun();
            if(!compartment.equals(r.getCompartmentId()) || !pipeline.equals(r.getBuildPipelineId()))throw Errors.SCOPE.exception();
            if(!Set.of("ACCEPTED","IN_PROGRESS").contains(r.getLifecycleState().getValue()))throw Errors.BUILD_CHANGED.exception();
            client.cancelBuildRun(CancelBuildRunRequest.builder().buildRunId(run).ifMatch(response.getEtag()).build());
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.BUILD_UNKNOWN.exception();}
    }
    public Settings settings(Connection c,String region,String compartment,String pipeline) {
        try(var client=clients.client(c,region)) {
            var response=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(pipeline).build());
            var p=response.getBuildPipeline();
            if(!compartment.equals(p.getCompartmentId())) throw Errors.SCOPE.exception();
            var parameters=p.getBuildPipelineParameters();
            return new Settings(response.getEtag(),parameters==null?List.of():parameters.getItems().stream()
                .map(v->new Parameter(v.getName(),Objects.toString(v.getDefaultValue(),""),Objects.toString(v.getDescription(),""))).toList());
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    private Settings current(Connection c,String r,String compartment,String pipeline,String etag) {
        var current=settings(c,r,compartment,pipeline);
        if(etag==null || !etag.equals(current.etag())) throw Errors.BUILD_CHANGED.exception();
        return current;
    }
    public void defaults(Connection c,String r,String compartment,String pipeline,String etag,List<Parameter> parameters) {
        current(c,r,compartment,pipeline,etag);
        try(var client=clients.client(c,r)) {
            client.updateBuildPipeline(UpdateBuildPipelineRequest.builder().buildPipelineId(pipeline).ifMatch(etag)
                .updateBuildPipelineDetails(UpdateBuildPipelineDetails.builder().buildPipelineParameters(
                    BuildPipelineParameterCollection.builder().items(parameters.stream().map(v->BuildPipelineParameter.builder()
                        .name(v.name()).defaultValue(v.value()).description(v.description()).build()).toList()).build()).build()).build());
        } catch(Exception e) { throw Errors.BUILD_CHANGED.exception(); }
    }
    public String start(Connection c,String r,String compartment,String pipeline,String etag,String name,List<Parameter> parameters,String token,BuildDefinition.SourceOverride override,String revision) {
        var current=current(c,r,compartment,pipeline,etag);
        var expected=current.parameters().stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet());
        if(!expected.equals(parameters.stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet()))) throw Errors.BUILD_PARAMETERS.exception();
        var definition=definitions.read(c,r,compartment,pipeline);
        if(!definition.runnable() || (revision!=null && !revision.equals(definition.revision()))) throw Errors.BUILD_CHANGED.exception();
        CommitInfo commit=null;
        if(!override.source().isEmpty()) {
            var source=definition.sources().stream().filter(v->v.key().equals(override.source())).findFirst().orElseThrow(Errors.BUILD_PARAMETERS::exception);
            if(override.branch().isBlank() || override.branch().length()>255) throw Errors.BUILD_PARAMETERS.exception();
            if(definitions.branches(c,r,source).stream().noneMatch(v->v.value().equals(override.branch()))) throw Errors.BUILD_PARAMETERS.exception();
            var commits=definitions.commits(c,r,source,override.branch());
            String hash=override.commit();
            if(hash.isEmpty()) {
                if(commits.isEmpty()) throw Errors.SOURCE_READ.exception();
                hash=commits.getFirst().value();
            } else if(commits.stream().noneMatch(v->v.value().equals(override.commit())) && !definitions.validCommit(c,r,source,hash)) throw Errors.BUILD_PARAMETERS.exception();
            // OCI requires commitHash whenever commitInfo is supplied, even for a branch's latest commit.
            commit=CommitInfo.builder().repositoryUrl(source.url()).repositoryBranch(override.branch()).commitHash(hash).build();
        } else if(!override.branch().isEmpty() || !override.commit().isEmpty()) throw Errors.BUILD_PARAMETERS.exception();
        try(var client=clients.client(c,r)) {
            return client.createBuildRun(CreateBuildRunRequest.builder().opcRetryToken(token)
                .createBuildRunDetails(CreateBuildRunDetails.builder().buildPipelineId(pipeline).commitInfo(commit).displayName(name.isBlank()?null:name)
                    .buildRunArguments(BuildRunArgumentCollection.builder().items(parameters.stream()
                        .map(v->BuildRunArgument.builder().name(v.name()).value(v.value()).build()).toList()).build()).build()).build()).getBuildRun().getId();
        } catch(Exception e) { throw Errors.BUILD_UNKNOWN.exception(); }
    }
}
