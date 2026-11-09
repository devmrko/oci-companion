package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildControl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildControlTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    final io.github.devmrko.companion.port.BuildProjectResources resources=mock(io.github.devmrko.companion.port.BuildProjectResources.class);
    @org.junit.jupiter.api.BeforeEach void readyLogs(){when(resources.logReadiness(any(),any(),any(),any())).thenReturn(io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.READY);}
    @Test void staleDefaultsAndForeignCompartmentNeverWrite() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().etag("new")
            .buildPipeline(BuildPipeline.builder().id("p").projectId("project").compartmentId("comp").build()).build());
        var adapter=new OciBuildControl(clients,mock(io.github.devmrko.companion.port.BuildDefinition.class),resources);
        assertThrows(RuntimeException.class,()->adapter.defaults(c,"region","comp","p","old",List.of()));
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","foreign","p","new","run",List.of(),"token"));
        verify(client,never()).updateBuildPipeline(any()); verify(client,never()).createBuildRun(any());
    }
    @Test void defaultsUseEtagAndPreserveDescription() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().etag("etag")
            .buildPipeline(BuildPipeline.builder().id("p").projectId("project").compartmentId("comp").build()).build());
        new OciBuildControl(clients,mock(io.github.devmrko.companion.port.BuildDefinition.class),resources).defaults(c,"region","comp","p","etag",List.of(new BuildControl.Parameter("VERSION","1","release")));
        var captor=org.mockito.ArgumentCaptor.forClass(UpdateBuildPipelineRequest.class);
        verify(client).updateBuildPipeline(captor.capture());
        assertEquals("etag",captor.getValue().getIfMatch());
        assertEquals("release",captor.getValue().getUpdateBuildPipelineDetails().getBuildPipelineParameters().getItems().getFirst().getDescription());
    }

    @Test void sourceOverrideIsScopedAndSerializedIntoActualRunRequest() throws Exception {
        var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);
        var definitions=mock(io.github.devmrko.companion.port.BuildDefinition.class);
        when(clients.client(c,"region")).thenReturn(client);
        when(client.getBuildPipeline(any())).thenReturn(GetBuildPipelineResponse.builder().etag("etag")
            .buildPipeline(BuildPipeline.builder().id("p").projectId("project").compartmentId("comp").build()).build());
        var source=new io.github.devmrko.companion.port.BuildDefinition.Source("stage::source","source","OCI","repo","https://example.com/repo","main",true);
        var stage=new io.github.devmrko.companion.port.BuildDefinition.Stage("stage","build","빌드","ACTIVE",List.of("시작"),"image","build_spec.yaml",3600,List.of(source),List.of());
        when(definitions.read(c,"region","comp","p")).thenReturn(new io.github.devmrko.companion.port.BuildDefinition.Definition("pipeline","project","ACTIVE","","rev",List.of(stage)));
        when(definitions.branches(c,"region",source)).thenReturn(List.of(new io.github.devmrko.companion.port.BuildDefinition.Choice("main","main")));
        when(definitions.commits(c,"region",source,"main")).thenReturn(List.of(new io.github.devmrko.companion.port.BuildDefinition.Choice("hash","hash")));
        when(client.createBuildRun(any())).thenReturn(CreateBuildRunResponse.builder().buildRun(BuildRun.builder().id("run").build()).build());
        var adapter=new OciBuildControl(clients,definitions,resources);
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","comp","p","etag","name",List.of(),"token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("foreign","main","hash"),"rev"));
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","comp","p","etag","name",List.of(),"token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main","unlisted"),"rev"));
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","comp","p","etag","name",List.of(),"token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main","hash"),"stale-stage-revision"));
        verify(client,never()).createBuildRun(any());
        for(var state:List.of(io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.DISABLED,io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.NOT_FOUND,io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.UNKNOWN)) {
            when(resources.logReadiness(c,"region","comp","project")).thenReturn(state);
            var error=assertThrows(io.github.devmrko.companion.error.BusinessException.class,()->adapter.start(c,"region","comp","p","etag","name",List.of(),"token",io.github.devmrko.companion.port.BuildDefinition.SourceOverride.none(),"rev"));
            assertEquals(state==io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.DISABLED?"BUILD_LOGS_REQUIRED":state==io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.NOT_FOUND?"BUILD_LOGS_NOT_FOUND":"BUILD_LOGS_UNVERIFIED",error.code());
            verify(client,never()).createBuildRun(any());
        }
        when(resources.logReadiness(c,"region","comp","project")).thenReturn(io.github.devmrko.companion.port.BuildProjectResources.LogReadiness.READY);
        assertEquals("run",adapter.start(c,"region","comp","p","etag","name",List.of(),"token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main","hash"),"rev"));
        var capture=org.mockito.ArgumentCaptor.forClass(CreateBuildRunRequest.class);verify(client).createBuildRun(capture.capture());
        assertEquals("https://example.com/repo",capture.getValue().getCreateBuildRunDetails().getCommitInfo().getRepositoryUrl());
        assertEquals("hash",capture.getValue().getCreateBuildRunDetails().getCommitInfo().getCommitHash());
        assertEquals("token",capture.getValue().getOpcRetryToken());
        clearInvocations(client);
        assertEquals("run",adapter.start(c,"region","comp","p","etag","latest",List.of(),"latest-token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main",""),"rev"));
        verify(client).createBuildRun(capture.capture());
        assertEquals("hash",capture.getValue().getCreateBuildRunDetails().getCommitInfo().getCommitHash());
        when(client.createBuildRun(any())).thenThrow(new com.oracle.bmc.model.BmcException(
            409,"IncorrectState","Logs need to be enabled in order to run the builds.","request-id"));
        var rejected=assertThrows(io.github.devmrko.companion.error.BusinessException.class,
            ()->adapter.start(c,"region","comp","p","etag","logs",List.of(),"logs-token",
                new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main","hash"),"rev"));
        assertEquals(io.github.devmrko.companion.service.Errors.BUILD_LOGS_REQUIRED.safeMessage(), rejected.getMessage());
        doThrow(new com.oracle.bmc.model.BmcException(
            409,"IncorrectState","Other state error","request-id")).when(client).createBuildRun(any());
        var unknown=assertThrows(io.github.devmrko.companion.error.BusinessException.class,
            ()->adapter.start(c,"region","comp","p","etag","other",List.of(),"other-token",
                new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main","hash"),"rev"));
        assertEquals(io.github.devmrko.companion.service.Errors.BUILD_UNKNOWN.safeMessage(), unknown.getMessage());
        clearInvocations(client);
        when(definitions.commits(c,"region",source,"main")).thenReturn(List.of());
        assertThrows(RuntimeException.class,()->adapter.start(c,"region","comp","p","etag","unresolved",List.of(),"unresolved-token",new io.github.devmrko.companion.port.BuildDefinition.SourceOverride("stage::source","main",""),"rev"));
        verify(client,never()).createBuildRun(any());
    }
}
