package io.github.devmrko.companion;

import io.github.devmrko.companion.port.FunctionBuilds;
import io.github.devmrko.companion.service.PythonFunctionRequest;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class PythonFunctionWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean io.github.devmrko.companion.adapter.OciFunctionBuilds functions;
    static final Path DIRECTORY;
    static { try { DIRECTORY=Files.createTempDirectory("python-function-web-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("companion.data-dir",()->DIRECTORY.toString()); }
    private MockHttpSession connect() throws Exception {
        when(functions.applications(any(),anyString(),anyString())).thenReturn(List.of(new FunctionBuilds.Application("app","build","ACTIVE","GENERIC_ARM")));
        when(functions.builders(any(),anyString(),anyString(),eq("app"))).thenReturn(List.of(new FunctionBuilds.Builder("fn","python","ACTIVE","image",3072,3600,"python-v1","ns","bucket","prefix","etag")));
        return (MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
    }
    private String ticket(MockHttpSession session) throws Exception {
        var result=mvc.perform(get("/builds/functions").session(session).param("view","execute").param("application","app").param("function","fn"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Python 빌드 실행")))
            .andExpect(content().string(not(containsString("name=\"profiles\"")))).andReturn();
        return (String)result.getModelAndView().getModel().get("functionTicket");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(MockHttpSession session,String ticket) {
        return post("/builds/functions/run").session(session).param("ticket",ticket).param("commit","a".repeat(40)).param("goal","verify")
            .param("sourceObject","sources/project.tgz").param("sourceVersion","version-1").param("sourceSha256","b".repeat(64))
            .param("requiredFreeBytes","536870912").param("directory","project").param("artifact","dist/*.whl");
    }
    @Test void submitsPinnedBundleWithBudgetAndSingleUseCsrfTicket() throws Exception {
        var session=connect();var ticket=ticket(session);
        mvc.perform(request(session,ticket)).andExpect(status().isForbidden());
        mvc.perform(request(session,ticket).with(csrf())).andExpect(status().is3xxRedirection());
        var payload=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(functions).invoke(any(),anyString(),anyString(),eq("app"),eq("fn"),eq("etag"),payload.capture());
        var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(payload.getValue());
        assertEquals("version-1",json.path("sourceBundle").path("version").asText());
        assertEquals("b".repeat(64),json.path("sourceBundle").path("sha256").asText());
        assertEquals(536870912L,json.path("requiredFreeBytes").asLong());
        assertFalse(json.has("source"));
        mvc.perform(request(session,ticket).with(csrf())).andExpect(status().isConflict());
    }
    @Test void rejectsMavenInputBeforeCloudInvocation() throws Exception {
        var session=connect();
        mvc.perform(request(session,ticket(session)).with(csrf()).param("source","https://github.com/a/b")).andExpect(status().isBadRequest());
        mvc.perform(request(session,ticket(session)).with(csrf()).param("profiles","ci")).andExpect(status().isBadRequest());
        // Budget validation is tested directly below; both cross-contract attempts must never invoke OCI.
        verify(functions,never()).invoke(any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void rejectsInvalidCapacityHashVersionAndEscapingPaths() {
        var run=UUID.randomUUID().toString();
        for(var budget:List.of("","0","-1","1.5","9223372036854775808","1e9"))
            assertThrows(RuntimeException.class,()->PythonFunctionRequest.request(run,"sources/a","v1","b".repeat(64),"a".repeat(40),"test",".","",budget));
        assertThrows(RuntimeException.class,()->PythonFunctionRequest.request(run,"sources/a","","b".repeat(64),"a".repeat(40),"test",".","","1"));
        assertThrows(RuntimeException.class,()->PythonFunctionRequest.request(run,"sources/a","v1","bad","a".repeat(40),"test",".","","1"));
        assertThrows(RuntimeException.class,()->PythonFunctionRequest.request(run,"sources/a","v1","b".repeat(64),"a".repeat(40),"test","../outside","","1"));
    }
}
