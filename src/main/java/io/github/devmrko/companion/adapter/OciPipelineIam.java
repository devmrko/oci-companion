package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.identity.model.*;
import com.oracle.bmc.devops.requests.GetBuildPipelineRequest;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.PipelineIam;
import io.github.devmrko.companion.service.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component @org.springframework.context.annotation.Profile("!demo")
public class OciPipelineIam implements PipelineIam {
    private final OciBuildHistory clients;
    public OciPipelineIam(OciBuildHistory clients){this.clients=clients;}
    IdentityClient identity(Connection c)throws Exception {
        var client=IdentityClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).build()).build(clients.authentication(c));
        try {
            var home=client.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder().tenancyId(c.tenancy()).build()).getItems().stream().filter(x->Boolean.TRUE.equals(x.getIsHomeRegion())).findFirst().orElseThrow();
            client.setRegion(home.getRegionName());return client;
        } catch(Exception e){client.close();throw e;}
    }
    void scope(Connection c,String region,String compartment,String pipeline)throws Exception {
        try(var devops=clients.client(c,region)) {
            var p=devops.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(pipeline).build()).getBuildPipeline();
            if(!compartment.equals(p.getCompartmentId()))throw Errors.SCOPE.exception();
        }
    }
    public Check inspect(Connection c,String region,String compartment,String pipeline) {
        boolean root=compartment.equals(c.tenancy());
        String name="companion-pipeline-"+UUID.nameUUIDFromBytes((c.tenancy()+"\n"+compartment).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().substring(0,12);
        String rule=PipelineIamRules.rule(compartment);
        if(c.demo())return new Check(true,root,name,false,rule,List.of(),List.of("데모: 실제 IAM을 조회하지 않았습니다."),"");
        try {
            scope(c,region,compartment,pipeline);
            try(var client=identity(c)) {
                var groups=new ArrayList<PipelineIamRules.Group>();
                for(var group:client.getPaginators().listDynamicGroupsRecordIterator(ListDynamicGroupsRequest.builder().compartmentId(c.tenancy()).build()))
                    if(group.getLifecycleState()==DynamicGroup.LifecycleState.Active)groups.add(new PipelineIamRules.Group(group.getId(),group.getName(),group.getMatchingRule()));
                var statements=new ArrayList<String>();var ancestors=new HashSet<String>();var visited=new HashSet<String>();String current=compartment;
                while(true) {
                    if(!visited.add(current))throw new IllegalStateException("구획 계층을 확인하지 못했습니다.");
                    ancestors.add(current);
                    for(var policy:client.getPaginators().listPoliciesRecordIterator(ListPoliciesRequest.builder().compartmentId(current).build()))
                        if(policy.getLifecycleState()==Policy.LifecycleState.Active)statements.addAll(policy.getStatements());
                    if(current.equals(c.tenancy()))break;
                    var parent=client.getCompartment(GetCompartmentRequest.builder().compartmentId(current).build()).getCompartment();
                    ancestors.add(parent.getName());current=parent.getCompartmentId();
                }
                var matching=groups.stream().filter(g->PipelineIamRules.matches(g.rule(),pipeline,compartment)).findFirst();
                boolean create=matching.isEmpty();
                if(matching.isPresent())name=matching.get().name();
                else {String proposed=name;if(groups.stream().anyMatch(g->g.name().equals(proposed)))name+="-"+UUID.nameUUIDFromBytes(pipeline.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().substring(0,8);}
                String scope=root?"in tenancy":"in compartment id "+compartment;
                var missing=PipelineIamRules.missing(groups,statements,ancestors,pipeline,compartment,name,scope);
                return new Check(true,root,name,create,rule,missing,List.of("조건부 정책·태그 규칙은 자동 판독하지 않습니다. 미확인 항목은 관리자 확인이 필요합니다.","생성 후 권한 전파에 수 분이 걸릴 수 있습니다."),"");
            }
        } catch(io.github.devmrko.companion.error.BusinessException e){throw e;}
        catch(Exception e){return new Check(false,root,name,true,rule,List.of(),List.of(),error(e));}
    }
    static String error(Exception e) {
        if(e instanceof com.oracle.bmc.model.BmcException b)return PipelineIamRules.safe(b.getServiceCode()+" ("+b.getStatusCode()+") "+b.getMessage());
        return "IAM 조회 또는 생성 실패: "+PipelineIamRules.safe(e.getMessage());
    }
    public void create(Connection c,String region,String compartment,String pipeline,Check preview,String token) {
        if(c.demo()||!preview.inspected()||preview.missing().isEmpty())throw Errors.INVALID.exception();
        var current=inspect(c,region,compartment,pipeline);
        if(!current.equals(preview))throw Errors.CONFLICT.exception();
        try(var client=identity(c)) {
            if(preview.createGroup())client.createDynamicGroup(CreateDynamicGroupRequest.builder().opcRetryToken(token)
                .createDynamicGroupDetails(CreateDynamicGroupDetails.builder().compartmentId(c.tenancy()).name(preview.groupName())
                    .description("Companion pipeline resource principals").matchingRule(preview.rule()).build()).build());
            client.createPolicy(CreatePolicyRequest.builder().opcRetryToken(token+"-policy")
                .createPolicyDetails(CreatePolicyDetails.builder().compartmentId(compartment).name("companion-pipeline-"+token.substring(0,12))
                    .description("Missing pipeline permissions reviewed in Companion").statements(preview.missing()).build()).build());
        } catch(Exception e) {
            // A group may already have been created; do not delete or mutate it on a partial failure.
            throw new IllegalStateException(error(e)+" · 일부 리소스가 생성됐을 수 있습니다. 다시 조회한 후 미리보기를 새로 여세요.");
        }
    }
}
