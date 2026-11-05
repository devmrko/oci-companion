package io.github.devmrko.companion.adapter;

import com.oracle.bmc.functions.FunctionsManagementClient;
import com.oracle.bmc.functions.model.*;
import com.oracle.bmc.functions.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OciFunctionBuildsTest {
    final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
    @Test void signingDoesNotConsumeInvocationBody() throws Exception {
        byte[] payload="{\"action\":\"build\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body=new OciFunctionBuilds.SignedBody(payload);
        try(var signature=body.duplicate()){assertArrayEquals(payload,signature.readAllBytes());}
        assertArrayEquals(payload,body.readAllBytes());
        try(var second=body.duplicate()){assertArrayEquals(payload,second.readAllBytes());}
    }
    @Test void rejectsCrossScopeAndStaleOrUndeclaredBuilderBeforeInvocation() throws Exception {
        var client=mock(FunctionsManagementClient.class);
        var adapter=spy(new OciFunctionBuilds(mock(OciBuildHistory.class)));doReturn(client).when(adapter).client(c,"region");
        when(client.getApplication(any())).thenReturn(GetApplicationResponse.builder().application(Application.builder().id("app").compartmentId("other").build()).build());
        assertThrows(RuntimeException.class,()->adapter.read(c,"region","comp","app","function"));verify(client,never()).getFunction(any());
        when(client.getApplication(any())).thenReturn(GetApplicationResponse.builder().application(Application.builder().id("app").compartmentId("comp").build()).build());
        var config=Map.of("BUILD_RUNNER_CONTRACT","maven-v1","BUILD_NAMESPACE","namespace","BUILD_BUCKET","bucket","BUILD_PREFIX","builder-1");
        var function=Function.builder().id("function").applicationId("app").compartmentId("comp").displayName("builder").lifecycleState(Function.LifecycleState.Active).memoryInMBs(3072L).timeoutInSeconds(300).config(config).build();
        when(client.getFunction(any())).thenReturn(GetFunctionResponse.builder().function(function).etag("etag").build());
        assertTrue(adapter.read(c,"region","comp","app","function").compatible());
        assertThrows(RuntimeException.class,()->adapter.invoke(c,"region","comp","app","function","old","{}"));
        when(client.getFunction(any())).thenReturn(GetFunctionResponse.builder().function(function.toBuilder().applicationId("other").build()).etag("etag").build());
        assertThrows(RuntimeException.class,()->adapter.read(c,"region","comp","app","function"));
        when(client.getFunction(any())).thenReturn(GetFunctionResponse.builder().function(function.toBuilder().config(Map.of()).build()).etag("etag").build());
        assertFalse(adapter.read(c,"region","comp","app","function").compatible());
        assertThrows(RuntimeException.class,()->adapter.invoke(c,"region","comp","app","function","etag/","{}"));
    }
}
