package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentEnvironments;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciDeploymentEnvironments implements DeploymentEnvironments {
    private final OciBuildHistory clients;
    public OciDeploymentEnvironments(OciBuildHistory clients){this.clients=clients;}
    ComputeClient compute(Connection c,String region) throws Exception {
        var client=ComputeClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c));
        client.setRegion(region);return client;
    }
    private Vm vm(Instance i){return new Vm(i.getId(),Objects.toString(i.getDisplayName(),i.getId()),i.getLifecycleState().getValue(),i.getShape());}
    private Instance checked(ComputeClient client,String comp,String id){var i=client.getInstance(GetInstanceRequest.builder().instanceId(id).build()).getInstance();if(!comp.equals(i.getCompartmentId()))throw Errors.SCOPE.exception();return i;}
    private Environment map(String id,String name,String description,String state,String project,ComputeInstanceGroupSelectorCollection selectors){
        var ids=new ArrayList<String>();var queries=new ArrayList<String>();
        if(selectors!=null&&selectors.getItems()!=null)for(var s:selectors.getItems()){
            if(s instanceof ComputeInstanceGroupByIdsSelector byIds)ids.addAll(byIds.getComputeInstanceIds());
            else if(s instanceof ComputeInstanceGroupByQuerySelector query)queries.add(query.getRegion()+": "+query.getQuery());
            else queries.add("지원하지 않는 선택 조건 · OCI 콘솔에서 확인");
        }
        return new Environment(id,Objects.toString(name,id),Objects.toString(description,""),state,project,List.copyOf(ids),List.copyOf(queries));
    }
    public List<Vm> instances(Connection c,String region,String comp){
        try(var client=compute(c,region)){var list=new ArrayList<Vm>();for(var i:client.getPaginators().listInstancesRecordIterator(ListInstancesRequest.builder().compartmentId(comp).build()))if(i.getLifecycleState()!=Instance.LifecycleState.Terminated)list.add(vm(i));list.sort(Comparator.comparing(Vm::name));return list;}
        catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public List<Environment> environments(Connection c,String region,String comp){
        try(var client=clients.client(c,region)){var list=new ArrayList<Environment>();for(var e:client.getPaginators().listDeployEnvironmentsRecordIterator(ListDeployEnvironmentsRequest.builder().compartmentId(comp).build()))if(e instanceof ComputeInstanceGroupDeployEnvironmentSummary v)list.add(map(v.getId(),v.getDisplayName(),v.getDescription(),v.getLifecycleState().getValue(),v.getProjectId(),v.getComputeInstanceGroupSelectors()));list.sort(Comparator.comparing(Environment::name));return list;}
        catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Environment environment(Connection c,String region,String comp,String id){
        try(var client=clients.client(c,region)){var e=client.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(id).build()).getDeployEnvironment();if(!comp.equals(e.getCompartmentId())||!(e instanceof ComputeInstanceGroupDeployEnvironment v))throw Errors.SCOPE.exception();return map(v.getId(),v.getDisplayName(),v.getDescription(),v.getLifecycleState().getValue(),v.getProjectId(),v.getComputeInstanceGroupSelectors());}
        catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Inspection inspect(Connection c,String region,String comp,String id){
        try(var client=compute(c,region)){
            var i=checked(client,comp,id);var checks=new ArrayList<Check>();
            checks.add(new Check("VM 상태",i.getLifecycleState()==Instance.LifecycleState.Running?"확인":"조치 필요",i.getLifecycleState().getValue()));
            try {var image=client.getImage(GetImageRequest.builder().imageId(i.getImageId()).build()).getImage();var os=Objects.toString(image.getOperatingSystem(),"");checks.add(new Check("기반 이미지 OS",Set.of("Oracle Linux","CentOS").contains(os)?"확인":"조치 필요",os+" "+Objects.toString(image.getOperatingSystemVersion(),"")+" · DevOps VM 배포는 Oracle Linux·CentOS 지원"));}
            catch(Exception e){checks.add(new Check("기반 이미지 OS","미확인","이미지 조회 권한 또는 이미지 삭제 여부를 확인하세요."));}
            var agent=i.getAgentConfig();String desired="미확인";
            if(agent!=null){if(Boolean.TRUE.equals(agent.getAreAllPluginsDisabled()))desired="DISABLED";else if(agent.getPluginsConfig()!=null)desired=agent.getPluginsConfig().stream().filter(p->"Compute Instance Run Command".equals(p.getName())).map(p->p.getDesiredState()==null?"미확인":p.getDesiredState().getValue()).findFirst().orElse("미확인");}
            checks.add(new Check("Run Command 설정",desired.equals("ENABLED")?"확인":desired.equals("DISABLED")?"조치 필요":"미확인",desired+" · 설정값이며 실제 플러그인 실행 상태는 별도 확인"));
            checks.add(new Check("Run Command 실행 상태","미확인","Compute → 인스턴스 → Oracle Cloud Agent에서 Running 확인"));
            checks.add(new Check("배포 실행 권한","미확인","DevOps 파이프라인·VM 동적 그룹의 Run Command 및 산출물 읽기 정책 확인"));
            checks.add(new Check("네트워크·Registry 접근","미확인","대상 VM에서 OCI 서비스 접근 경로·DNS·보안 규칙 확인"));
            checks.add(new Check("런타임·서비스 계정","미확인","Java·Python 등 런타임, 배포 경로, 서비스 및 ocarun 권한 확인"));
            return new Inspection(vm(i),checks);
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public String create(Connection c,String region,String comp,String project,String name,String description,List<String> ids,String token){
        if(name==null||name.isBlank()||name.length()>255||description==null||description.length()>400||ids==null||ids.isEmpty()||ids.size()>50||new HashSet<>(ids).size()!=ids.size())throw Errors.INVALID.exception();
        try(var devops=clients.client(c,region);var compute=compute(c,region)){
            var p=devops.getProject(GetProjectRequest.builder().projectId(project).build()).getProject();
            if(!comp.equals(p.getCompartmentId())||!"ACTIVE".equals(p.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
            for(String id:ids){var i=checked(compute,comp,id);if(i.getLifecycleState()==Instance.LifecycleState.Terminated||i.getLifecycleState()==Instance.LifecycleState.Terminating)throw Errors.SCOPE.exception();}
            var details=CreateComputeInstanceGroupDeployEnvironmentDetails.builder().projectId(project).displayName(name.strip()).description(description.isBlank()?null:description.strip()).computeInstanceGroupSelectors(ComputeInstanceGroupSelectorCollection.builder().items(List.of(ComputeInstanceGroupByIdsSelector.builder().computeInstanceIds(ids).build())).build()).build();
            try{return devops.createDeployEnvironment(CreateDeployEnvironmentRequest.builder().opcRetryToken(token).createDeployEnvironmentDetails(details).build()).getDeployEnvironment().getId();}
            catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
