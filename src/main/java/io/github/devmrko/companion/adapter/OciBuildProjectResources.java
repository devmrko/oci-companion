package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.model.CreateRepositoryDetails;
import com.oracle.bmc.logging.LoggingManagementClient;
import com.oracle.bmc.logging.model.*;
import com.oracle.bmc.logging.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import io.github.devmrko.companion.port.BuildProjectResources;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciBuildProjectResources implements BuildProjectResources {
    private final OciBuildHistory clients;
    public OciBuildProjectResources(OciBuildHistory clients){this.clients=clients;}
    private void project(Connection c,String region,String comp,String project){try(var client=clients.client(c,region)){var p=client.getProject(GetProjectRequest.builder().projectId(project).build()).getProject();if(!comp.equals(p.getCompartmentId())||!"ACTIVE".equals(p.getLifecycleState().getValue()))throw Errors.SCOPE.exception();}catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
    protected LoggingManagementClient logging(Connection c,String region)throws Exception{var client=LoggingManagementClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c));client.setRegion(region);return client;}
    private void name(String name){if(name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))throw Errors.ADMIN_INPUT.exception();}
    public LogReadiness logReadiness(Connection c,String region,String comp,String project){
        project(c,region,comp,project);
        try(var client=logging(c,region)){
            boolean disabled=false;
            for(var group:client.getPaginators().listLogGroupsRecordIterator(ListLogGroupsRequest.builder().compartmentId(comp).build()))
                for(var log:client.getPaginators().listLogsRecordIterator(ListLogsRequest.builder().logGroupId(group.getId()).build()))
                    if(log.getConfiguration()!=null && log.getConfiguration().getSource() instanceof OciService src
                        && "devops".equalsIgnoreCase(src.getService()) && project.equals(src.getResource()) && "all".equals(src.getCategory())){
                        if(Boolean.TRUE.equals(log.getIsEnabled()) && log.getLifecycleState()==LogLifecycleState.Active) return LogReadiness.READY;
                        disabled=true;
                    }
            return disabled?LogReadiness.DISABLED:LogReadiness.NOT_FOUND;
        }catch(Exception e){return LogReadiness.UNKNOWN;}
    }
    public Resources read(Connection c,String region,String comp,String project){
        project(c,region,comp,project);
        try(var devops=clients.client(c,region);var logging=logging(c,region)){
            var repos=new ArrayList<Repository>();for(var r:devops.getPaginators().listRepositoriesRecordIterator(ListRepositoriesRequest.builder().projectId(project).build()))repos.add(new Repository(r.getId(),r.getName(),Objects.toString(r.getHttpUrl(),""),Objects.toString(r.getDefaultBranch(),"main"),r.getLifecycleState().getValue()));
            var groups=new ArrayList<Choice>();var logs=new ArrayList<Log>();
            for(var group:logging.getPaginators().listLogGroupsRecordIterator(ListLogGroupsRequest.builder().compartmentId(comp).build())){
                groups.add(new Choice(group.getId(),group.getDisplayName()));
                for(var log:logging.getPaginators().listLogsRecordIterator(ListLogsRequest.builder().logGroupId(group.getId()).build()))if(log.getConfiguration()!=null&&log.getConfiguration().getSource() instanceof OciService src&&"devops".equals(src.getService())&&project.equals(src.getResource()))logs.add(new Log(log.getId(),group.getId(),log.getDisplayName(),Boolean.TRUE.equals(log.getIsEnabled()),log.getLifecycleState().getValue()));
            }return new Resources(repos,groups,logs);
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public String createRepository(Connection c,String region,String comp,String project,String name,String description,String token){
        name(name);if(description==null||description.length()>400)throw Errors.ADMIN_INPUT.exception();project(c,region,comp,project);
        try(var client=clients.client(c,region)){return client.createRepository(CreateRepositoryRequest.builder().opcRetryToken(token).createRepositoryDetails(CreateRepositoryDetails.builder().projectId(project).name(name).description(description).repositoryType(com.oracle.bmc.devops.model.Repository.RepositoryType.Hosted).defaultBranch("main").build()).build()).getRepository().getId();}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    public String createLogGroup(Connection c,String region,String comp,String project,String name,String token){name(name);project(c,region,comp,project);try(var client=logging(c,region)){return client.createLogGroup(CreateLogGroupRequest.builder().opcRetryToken(token).createLogGroupDetails(CreateLogGroupDetails.builder().compartmentId(comp).displayName(name).build()).build()).getOpcWorkRequestId();}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}}
    public void createLog(Connection c,String region,String comp,String project,String group,String name,String token){
        name(name);project(c,region,comp,project);var resources=read(c,region,comp,project);
        if(resources.groups().stream().noneMatch(g->g.id().equals(group)))throw Errors.SCOPE.exception();
        if(!resources.logs().isEmpty())throw Errors.BUILD_CHANGED.exception();
        try(var client=logging(c,region)){client.createLog(CreateLogRequest.builder().logGroupId(group).opcRetryToken(token).createLogDetails(CreateLogDetails.builder().displayName(name).logType(CreateLogDetails.LogType.Service).isEnabled(true).retentionDuration(30).configuration(Configuration.builder().compartmentId(comp).source(OciService.builder().service("devops").resource(project).category("all").build()).build()).build()).build());}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
}
