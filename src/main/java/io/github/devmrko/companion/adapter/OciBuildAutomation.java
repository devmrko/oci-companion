package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.resourcescheduler.ScheduleClient;
import com.oracle.bmc.resourcescheduler.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildAutomation;
import io.github.devmrko.companion.port.BuildDefinition;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciBuildAutomation implements BuildAutomation {
    private final OciBuildHistory clients;private final OciFunctionBuilds functions;private final BuildDefinition definitions;
    public OciBuildAutomation(OciBuildHistory clients,OciFunctionBuilds functions,BuildDefinition definitions){this.clients=clients;this.functions=functions;this.definitions=definitions;}
    private BuildPipeline pipeline(com.oracle.bmc.devops.DevopsClient client,String comp,String id){var p=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(id).build()).getBuildPipeline();if(!comp.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();return p;}
    public List<BuildAutomation.Trigger> triggers(Connection c,String r,String comp,String id){
        try(var client=clients.client(c,r)){var p=pipeline(client,comp,id);var result=new ArrayList<BuildAutomation.Trigger>();
            for(var summary:client.getPaginators().listTriggersRecordIterator(ListTriggersRequest.builder().projectId(p.getProjectId()).build())){
                var t=client.getTrigger(GetTriggerRequest.builder().triggerId(summary.getId()).build()).getTrigger();
                var actions=t.getActions().stream().filter(a->a instanceof TriggerBuildPipelineAction b && id.equals(b.getBuildPipelineId())).toList();if(actions.isEmpty())continue;
                String provider=t instanceof DevopsCodeRepositoryTrigger?"OCI":t instanceof GithubTrigger?"GitHub":t instanceof GitlabTrigger?"GitLab":"기타";
                result.add(new BuildAutomation.Trigger(t.getId(),t.getDisplayName(),provider,t.getLifecycleState().getValue(),actions.stream().map(a->condition(a.getFilter())).collect(java.util.stream.Collectors.joining(" / "))));
            }return result;
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    private String condition(Filter f){
        if(f instanceof DevopsCodeRepositoryFilter x)return x.getEvents()+" · "+(x.getInclude()==null?"전체 브랜치":Objects.toString(x.getInclude().getHeadRef(),"전체 브랜치"));
        if(f instanceof GithubFilter x)return x.getEvents()+" · "+(x.getInclude()==null?"전체 브랜치":Objects.toString(x.getInclude().getHeadRef(),"전체 브랜치"));
        if(f instanceof GitlabFilter x)return x.getEvents()+" · "+(x.getInclude()==null?"전체 브랜치":Objects.toString(x.getInclude().getHeadRef(),"전체 브랜치"));
        return "상세 조건은 OCI 콘솔에서 확인";
    }
    public Created createTrigger(Connection c,String r,String comp,String id,String key,String name,String event,String branch,String token){
        if(name.isBlank() || name.length()>255 || branch.isBlank() || branch.length()>255 || !Set.of("PUSH","PULL_REQUEST_CREATED","PULL_REQUEST_UPDATED","PULL_REQUEST_MERGED").contains(event) || (name+branch).chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        var definition=definitions.read(c,r,comp,id);var source=definition.sources().stream().filter(s->s.key().equals(key)).findFirst().orElseThrow(Errors.MISSING::exception);
        if(!definition.runnable())throw Errors.BUILD_CHANGED.exception();
        try(var client=clients.client(c,r)){var p=pipeline(client,comp,id);CreateTriggerDetails details;Filter filter;
            if(source.provider().equals("OCI")){
                var repository=client.getRepository(GetRepositoryRequest.builder().repositoryId(source.repositoryId()).build()).getRepository();if(!p.getProjectId().equals(repository.getProjectId()))throw Errors.SCOPE.exception();
                filter=DevopsCodeRepositoryFilter.builder().events(List.of(DevopsCodeRepositoryFilter.Events.create(event))).include(DevopsCodeRepositoryFilterAttributes.builder().headRef(branch).build()).build();
                details=CreateDevopsCodeRepositoryTriggerDetails.builder().displayName(name).projectId(p.getProjectId()).repositoryId(source.repositoryId()).actions(List.of(TriggerBuildPipelineAction.builder().buildPipelineId(id).filter(filter).build())).build();
            }else if(source.provider().equalsIgnoreCase("GitHub")){
                filter=GithubFilter.builder().events(List.of(GithubFilter.Events.create(event))).include(GithubFilterAttributes.builder().headRef(branch).build()).build();
                details=CreateGithubTriggerDetails.builder().displayName(name).projectId(p.getProjectId()).actions(List.of(TriggerBuildPipelineAction.builder().buildPipelineId(id).filter(filter).build())).build();
            }else if(source.provider().equalsIgnoreCase("GitLab")){
                filter=GitlabFilter.builder().events(List.of(GitlabFilter.Events.create(event))).include(GitlabFilterAttributes.builder().headRef(branch).build()).build();
                details=CreateGitlabTriggerDetails.builder().displayName(name).projectId(p.getProjectId()).actions(List.of(TriggerBuildPipelineAction.builder().buildPipelineId(id).filter(filter).build())).build();
            }else throw Errors.INVALID.exception();
            var t=client.createTrigger(CreateTriggerRequest.builder().createTriggerDetails(details).opcRetryToken(token).build()).getTriggerCreateResult();
            if(t instanceof GithubTriggerCreateResult g)return new Created(t.getId(),g.getTriggerUrl(),g.getSecret());
            if(t instanceof GitlabTriggerCreateResult g)return new Created(t.getId(),g.getTriggerUrl(),g.getSecret());
            return new Created(t.getId(),"","");
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    public List<Dispatcher> dispatchers(Connection c,String r,String comp,String id){
        try(var client=functions.client(c,r)){var result=new ArrayList<Dispatcher>();
            for(var app:client.getPaginators().listApplicationsRecordIterator(com.oracle.bmc.functions.requests.ListApplicationsRequest.builder().compartmentId(comp).build()))
                for(var summary:client.getPaginators().listFunctionsRecordIterator(com.oracle.bmc.functions.requests.ListFunctionsRequest.builder().applicationId(app.getId()).build())){
                    var f=client.getFunction(com.oracle.bmc.functions.requests.GetFunctionRequest.builder().functionId(summary.getId()).build()).getFunction();var config=f.getConfig();
                    if(f.getLifecycleState()==com.oracle.bmc.functions.model.Function.LifecycleState.Active && config!=null && "devops-dispatch-v1".equals(config.get("BUILD_RUNNER_CONTRACT")) && id.equals(config.get("BUILD_PIPELINE_ID")) && r.equals(config.get("BUILD_REGION")))result.add(new Dispatcher(f.getId(),f.getDisplayName(),app.getDisplayName(),id));
                }return result;
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    private ScheduleClient scheduler(Connection c,String r)throws Exception{var client=ScheduleClient.builder().configuration(com.oracle.bmc.ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(com.oracle.bmc.retrier.RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c));client.setRegion(r);return client;}
    private String time(Date t){return t==null?"—":t.toInstant().toString();}
    public List<BuildAutomation.Schedule> schedules(Connection c,String r,String comp,String id){
        try(var client=scheduler(c,r)){var result=new ArrayList<BuildAutomation.Schedule>();
            for(var s:client.getPaginators().listSchedulesRecordIterator(ListSchedulesRequest.builder().compartmentId(comp).build())){
                if(s.getFreeformTags()==null || !id.equals(s.getFreeformTags().get("build-pipeline")))continue;
                var full=client.getSchedule(GetScheduleRequest.builder().scheduleId(s.getId()).build());
                result.add(new BuildAutomation.Schedule(s.getId(),s.getDisplayName(),s.getRecurrenceDetails(),s.getLifecycleState().getValue(),time(s.getTimeNextRun()),time(s.getTimeLastRun()),Objects.toString(s.getLastRunStatus(),"—"),full.getEtag()));
            }return result;
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public String createSchedule(Connection c,String r,String comp,String id,String fn,String name,String cron,String token){
        BuildSchedules.cron(cron);if(name.isBlank() || name.length()>255 || name.chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        if(!definitions.read(c,r,comp,id).runnable())throw Errors.BUILD_CHANGED.exception();
        if(dispatchers(c,r,comp,id).stream().noneMatch(d->d.id().equals(fn)))throw Errors.MISSING.exception();
        try(var client=scheduler(c,r)){
            var details=com.oracle.bmc.resourcescheduler.model.CreateScheduleDetails.builder().compartmentId(comp).displayName(name).action(com.oracle.bmc.resourcescheduler.model.CreateScheduleDetails.Action.StartResource)
                .recurrenceType(com.oracle.bmc.resourcescheduler.model.CreateScheduleDetails.RecurrenceType.Cron).recurrenceDetails(cron).localTimeZone("UTC")
                .resources(List.of(com.oracle.bmc.resourcescheduler.model.Resource.builder().id(fn).build())).freeformTags(Map.of("build-pipeline",id)).build();
            return client.createSchedule(CreateScheduleRequest.builder().createScheduleDetails(details).opcRetryToken(token).build()).getSchedule().getId();
        }catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    public void scheduleState(Connection c,String r,String comp,String pipeline,String id,String etag,boolean active){
        try(var client=scheduler(c,r)){var s=client.getSchedule(GetScheduleRequest.builder().scheduleId(id).build());if(!comp.equals(s.getSchedule().getCompartmentId()) || s.getSchedule().getFreeformTags()==null || !pipeline.equals(s.getSchedule().getFreeformTags().get("build-pipeline")))throw Errors.SCOPE.exception();
            if(etag.isBlank() || !etag.equals(s.getEtag()))throw Errors.BUILD_CHANGED.exception();
            if(active)client.activateSchedule(ActivateScheduleRequest.builder().scheduleId(id).ifMatch(etag).build());else client.deactivateSchedule(DeactivateScheduleRequest.builder().scheduleId(id).ifMatch(etag).build());
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.BUILD_CHANGED.exception();}
    }
}
