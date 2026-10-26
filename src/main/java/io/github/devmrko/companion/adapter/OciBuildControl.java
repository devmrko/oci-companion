package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildControl;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciBuildControl implements BuildControl {
    private final OciBuildHistory clients;
    public OciBuildControl(OciBuildHistory clients) { this.clients=clients; }
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
    public String start(Connection c,String r,String compartment,String pipeline,String etag,String name,List<Parameter> parameters,String token) {
        var current=current(c,r,compartment,pipeline,etag);
        var expected=current.parameters().stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet());
        if(!expected.equals(parameters.stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet()))) throw Errors.BUILD_PARAMETERS.exception();
        try(var client=clients.client(c,r)) {
            return client.createBuildRun(CreateBuildRunRequest.builder().opcRetryToken(token)
                .createBuildRunDetails(CreateBuildRunDetails.builder().buildPipelineId(pipeline).displayName(name.isBlank()?null:name)
                    .buildRunArguments(BuildRunArgumentCollection.builder().items(parameters.stream()
                        .map(v->BuildRunArgument.builder().name(v.name()).value(v.value()).build()).toList()).build()).build()).build()).getBuildRun().getId();
        } catch(Exception e) { throw Errors.BUILD_UNKNOWN.exception(); }
    }
}
