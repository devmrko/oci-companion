package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRuns;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciDeploymentRunsTest {
    final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
    @Test void changedConfigurationIsRejectedBeforeStarting() throws Exception {
        var clients=mock(OciBuildHistory.class);var adapter=spy(new OciDeploymentRuns(clients));
        doReturn(new DeploymentRuns.Plan("p","release","env",List.of("vm"),"new")).when(adapter).plan(c,"seoul","comp","p");
        assertThrows(RuntimeException.class,()->adapter.start(c,"seoul","comp","p","old","ticket"));
        verifyNoInteractions(clients);
    }
    @Test void approvedConfigurationUsesSelectedRegionAndRetryToken() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"seoul")).thenReturn(client);
        var adapter=spy(new OciDeploymentRuns(clients));doReturn(new DeploymentRuns.Plan("p","release","env",List.of("vm"),"same")).when(adapter).plan(c,"seoul","comp","p");
        when(client.createDeployment(any())).thenReturn(CreateDeploymentResponse.builder().deployment(DeployPipelineDeployment.builder().id("run").build()).build());
        assertEquals("run",adapter.start(c,"seoul","comp","p","same","ticket"));
        var capture=org.mockito.ArgumentCaptor.forClass(com.oracle.bmc.devops.requests.CreateDeploymentRequest.class);verify(client).createDeployment(capture.capture());
        assertEquals("ticket",capture.getValue().getOpcRetryToken());assertEquals("p",capture.getValue().getCreateDeploymentDetails().getDeployPipelineId());
    }
    @Test void anotherCompartmentRunIsNotExposed() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"seoul")).thenReturn(client);
        when(client.getDeployment(any())).thenReturn(GetDeploymentResponse.builder().deployment(DeployPipelineDeployment.builder().id("run").deployPipelineId("p").compartmentId("other").build()).build());
        assertThrows(RuntimeException.class,()->new OciDeploymentRuns(clients).run(c,"seoul","comp","p","run"));
    }
    @Test void targetVmMustBeRunningInSelectedCompartment() throws Exception {
        var clients=mock(OciBuildHistory.class);var compute=mock(com.oracle.bmc.core.ComputeClient.class);var adapter=spy(new OciDeploymentRuns(clients));doReturn(compute).when(adapter).compute(c,"seoul");
        when(compute.getInstance(any())).thenReturn(com.oracle.bmc.core.responses.GetInstanceResponse.builder().instance(com.oracle.bmc.core.model.Instance.builder().compartmentId("other").lifecycleState(com.oracle.bmc.core.model.Instance.LifecycleState.Running).build()).build());
        assertThrows(RuntimeException.class,()->adapter.validateInstances(c,"seoul","comp",List.of("vm")));
        when(compute.getInstance(any())).thenReturn(com.oracle.bmc.core.responses.GetInstanceResponse.builder().instance(com.oracle.bmc.core.model.Instance.builder().compartmentId("comp").lifecycleState(com.oracle.bmc.core.model.Instance.LifecycleState.Stopped).build()).build());
        assertThrows(RuntimeException.class,()->adapter.validateInstances(c,"seoul","comp",List.of("vm")));
        when(compute.getInstance(any())).thenReturn(com.oracle.bmc.core.responses.GetInstanceResponse.builder().instance(com.oracle.bmc.core.model.Instance.builder().compartmentId("comp").lifecycleState(com.oracle.bmc.core.model.Instance.LifecycleState.Running).build()).build());
        assertDoesNotThrow(()->adapter.validateInstances(c,"seoul","comp",List.of("vm")));
    }
    @Test void packageRequiresSameScopeAndPinnedVersion(){
        var good=DeployArtifact.builder().compartmentId("comp").projectId("project").argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(GenericDeployArtifactSource.builder().repositoryId("repo").deployArtifactPath("release.zip").deployArtifactVersion("0.4.1").build()).build();
        assertDoesNotThrow(()->OciDeploymentRuns.validatePackage(good,"comp","project"));
        assertThrows(RuntimeException.class,()->OciDeploymentRuns.validatePackage(good,"other","project"));
        var parameterized=DeployArtifact.builder().compartmentId("comp").projectId("project").argumentSubstitutionMode(DeployArtifact.ArgumentSubstitutionMode.None).deployArtifactSource(GenericDeployArtifactSource.builder().deployArtifactVersion("${version}").build()).build();
        assertThrows(RuntimeException.class,()->OciDeploymentRuns.validatePackage(parameterized,"comp","project"));
    }
}
