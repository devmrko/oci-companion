package io.github.devmrko.companion.adapter;

import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.*;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.identity.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.PipelineIam;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OciPipelineIamTest {
    final Connection c=new Connection("p","tenant","u","r","label",false);
    @Test void queryFailureIsUnknownAndPreservesServiceError()throws Exception {
        var adapter=spy(new OciPipelineIam(mock(OciBuildHistory.class)));doNothing().when(adapter).scope(any(),any(),any(),any());
        doThrow(new com.oracle.bmc.model.BmcException(404,"NotAuthorizedOrNotFound","Cannot read ocid1.policy.oc1.private","request")).when(adapter).identity(c);
        var check=adapter.inspect(c,"r","comp","pipeline");assertFalse(check.inspected());assertFalse(check.ready());assertTrue(check.missing().isEmpty());assertTrue(check.error().contains("NotAuthorizedOrNotFound"));assertFalse(check.error().contains("ocid1."));
    }
    @Test void ancestorPolicyAndMatchingGroupCoverAllRequirements()throws Exception {
        var adapter=spy(new OciPipelineIam(mock(OciBuildHistory.class)));var client=mock(IdentityClient.class,RETURNS_DEEP_STUBS);
        doNothing().when(adapter).scope(any(),any(),any(),any());doReturn(client).when(adapter).identity(c);
        when(client.getPaginators().listDynamicGroupsRecordIterator(any())).thenReturn(List.of(DynamicGroup.builder().id("g").name("builders").matchingRule("resource.id = 'pipeline'").lifecycleState(DynamicGroup.LifecycleState.Active).build()));
        when(client.getPaginators().listPoliciesRecordIterator(any())).thenAnswer(invocation->{var r=(ListPoliciesRequest)invocation.getArgument(0);return r.getCompartmentId().equals("tenant")?List.of(Policy.builder().lifecycleState(Policy.LifecycleState.Active).statements(List.of("Allow dynamic-group builders to manage all-resources in tenancy")).build()):List.of();});
        when(client.getCompartment(any())).thenReturn(GetCompartmentResponse.builder().compartment(Compartment.builder().name("app").compartmentId("tenant").build()).build());
        assertTrue(adapter.inspect(c,"r","comp","pipeline").ready());verify(client,never()).createPolicy(any());verify(client,never()).createDynamicGroup(any());
    }
    @Test void createsNewResourcesOnlyAndRejectsChangedPreview()throws Exception {
        var adapter=spy(new OciPipelineIam(mock(OciBuildHistory.class)));var client=mock(IdentityClient.class);doReturn(client).when(adapter).identity(c);
        var check=new PipelineIam.Check(true,false,"new-group",true,"rule",List.of("statement"),List.of(),"");
        doReturn(check).when(adapter).inspect(c,"r","comp","pipeline");
        adapter.create(c,"r","comp","pipeline",check,"12345678-1234-1234-1234-123456789012");
        verify(client).createDynamicGroup(any());verify(client).createPolicy(any());verify(client,never()).updatePolicy(any());verify(client,never()).updateDynamicGroup(any());
        var changed=new PipelineIam.Check(true,false,"new-group",true,"rule",List.of(),List.of(),"");doReturn(changed).when(adapter).inspect(c,"r","comp","pipeline");
        assertThrows(RuntimeException.class,()->adapter.create(c,"r","comp","pipeline",check,"12345678-1234-1234-1234-123456789012"));
    }
    @Test void listSummariesLoadMissingRuleAndStatementsBeforeEvaluating()throws Exception {
        var adapter=spy(new OciPipelineIam(mock(OciBuildHistory.class)));var client=mock(IdentityClient.class,RETURNS_DEEP_STUBS);
        doNothing().when(adapter).scope(any(),any(),any(),any());doReturn(client).when(adapter).identity(c);
        when(client.getPaginators().listDynamicGroupsRecordIterator(any())).thenReturn(List.of(DynamicGroup.builder().id("g").name("builders").lifecycleState(DynamicGroup.LifecycleState.Active).build()));
        when(client.getDynamicGroup(any())).thenReturn(GetDynamicGroupResponse.builder().dynamicGroup(DynamicGroup.builder().matchingRule("resource.id = 'pipeline'").build()).build());
        when(client.getPaginators().listPoliciesRecordIterator(any())).thenReturn(List.of(Policy.builder().id("policy").lifecycleState(Policy.LifecycleState.Active).build()));
        when(client.getPolicy(any())).thenReturn(GetPolicyResponse.builder().policy(Policy.builder().statements(List.of("Allow dynamic-group builders to manage all-resources in tenancy")).build()).build());
        assertTrue(adapter.inspect(c,"r","tenant","pipeline").ready());verify(client).getDynamicGroup(any());verify(client).getPolicy(any());
    }
}
