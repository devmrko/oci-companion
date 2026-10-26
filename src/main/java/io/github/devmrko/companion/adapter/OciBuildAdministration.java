package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.ons.NotificationControlPlaneClient;
import com.oracle.bmc.ons.requests.ListTopicsRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import io.github.devmrko.companion.port.BuildAdministration;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciBuildAdministration implements BuildAdministration {
    private final OciBuildHistory clients;
    public OciBuildAdministration(OciBuildHistory clients) { this.clients=clients; }
    public List<Project> projects(Connection c,String region,String compartment) {
        try(var client=clients.client(c,region)) {
            var result=new ArrayList<Project>();
            for(var p:client.getPaginators().listProjectsRecordIterator(ListProjectsRequest.builder().compartmentId(compartment).build()))
                result.add(new Project(p.getId(),p.getName(),Objects.toString(p.getDescription(),""),p.getLifecycleState().getValue(),
                    p.getNotificationConfig()==null?"":Objects.toString(p.getNotificationConfig().getTopicId(),"")));
            result.sort(Comparator.comparing(Project::name));return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Choice> topics(Connection c,String region,String compartment) {
        try(var client=NotificationControlPlaneClient.builder().configuration(ClientConfiguration.builder()
                .connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build())
                .build(clients.authentication(c))) {
            client.setRegion(region);var result=new ArrayList<Choice>();
            for(var t:client.getPaginators().listTopicsRecordIterator(ListTopicsRequest.builder().compartmentId(compartment).build()))
                if("ACTIVE".equalsIgnoreCase(t.getLifecycleState().getValue())) result.add(new Choice(t.getTopicId(),t.getName()));
            result.sort(Comparator.comparing(Choice::name));return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public String createProject(Connection c,String region,String compartment,String name,String description,String topic,String token) {
        validate(name,description);
        if(topics(c,region,compartment).stream().noneMatch(t->t.id().equals(topic))) throw Errors.MISSING.exception();
        try(var client=clients.client(c,region)) {
            return client.createProject(CreateProjectRequest.builder().opcRetryToken(token).createProjectDetails(
                CreateProjectDetails.builder().compartmentId(compartment).name(name.trim()).description(description)
                    .notificationConfig(NotificationConfig.builder().topicId(topic).build()).build()).build()).getProject().getId();
        } catch(Exception e) { throw Errors.ADMIN_UNKNOWN.exception(); }
    }
    public String createPipeline(Connection c,String region,String compartment,String project,String name,String description,String token) {
        validate(name,description);
        try(var client=clients.client(c,region)) {
            var p=client.getProject(GetProjectRequest.builder().projectId(project).build()).getProject();
            if(!compartment.equals(p.getCompartmentId()) || !"ACTIVE".equalsIgnoreCase(p.getLifecycleState().getValue()))
                throw Errors.SCOPE.exception();
            try {
                return client.createBuildPipeline(CreateBuildPipelineRequest.builder().opcRetryToken(token).createBuildPipelineDetails(
                    CreateBuildPipelineDetails.builder().projectId(project).displayName(name.trim()).description(description).build()).build())
                    .getBuildPipeline().getId();
            } catch(Exception e) { throw Errors.ADMIN_UNKNOWN.exception(); }
        } catch(io.github.devmrko.foundation.core.error.BusinessException e) { throw e; }
        catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    private void validate(String name,String description) {
        if(name==null || name.isBlank() || name.length()>255 || name.chars().anyMatch(Character::isISOControl)
            || description==null || description.length()>400) throw Errors.ADMIN_INPUT.exception();
    }
}
