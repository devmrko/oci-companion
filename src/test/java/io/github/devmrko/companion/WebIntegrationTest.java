package io.github.devmrko.companion;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class WebIntegrationTest {
    private static final Path DB;
    static { try { DB=Files.createTempFile("companion-web-",".db"); DB.toFile().deleteOnExit(); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url",()->"jdbc:sqlite:"+DB); }
    @Autowired MockMvc mvc;
    private MockHttpSession connect() throws Exception {
        return (MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
            .andReturn().getRequest().getSession(false);
    }
    @Test void anonymousCsrfHostAndLogoutBoundaries() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("DEMO")));
        mvc.perform(post("/connect").param("profile","DEMO")).andExpect(status().isForbidden());
        mvc.perform(get("/login").with(r->{r.setServerName("evil.example");return r;})).andExpect(status().isForbidden());
        var session=connect();
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("데모 모드")));
        mvc.perform(post("/disconnect").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/disconnect").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }
    @Test void invalidProfileCannotCreateAuthenticatedSession() throws Exception {
        var result=mvc.perform(post("/connect").with(csrf()).param("profile","NOT_A_PROFILE"))
            .andExpect(redirectedUrl("/login")).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        if(session!=null) assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
    }
    @Test void completeDemoWorkflowRendersEveryPage() throws Exception {
        var session=connect();
        mvc.perform(get("/projects/new").session(session)).andExpect(status().isOk());
        var result=mvc.perform(post("/projects").session(session).with(csrf())
            .param("name","DB Companion").param("repositoryUrl","https://github.com/devmrko/db-companion")
            .param("branch","main").param("recipe","maven").param("compartmentId","demo-compartment")
            .param("buildPipelineId","demo-build").param("deployPipelineId","demo-vm"))
            .andExpect(status().is3xxRedirection()).andReturn();
        String project=result.getResponse().getRedirectedUrl();
        mvc.perform(get(project).session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("DB Companion")));
        mvc.perform(get(project+"/edit").session(session)).andExpect(status().isOk());
        mvc.perform(get(project+"/pipelines").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("demo-vm")));
        mvc.perform(get(project+"/build-spec").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("./mvnw -B verify")));
        mvc.perform(get("/guides").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("SDK / API")));
        String token=UUID.randomUUID().toString();
        mvc.perform(post(project+"/run").session(session).param("kind","BUILD").param("expectedPipeline","demo-build").param("token",token).param("acknowledged","true")).andExpect(status().isForbidden());
        for (int i=0;i<2;i++) mvc.perform(post(project+"/run").session(session).with(csrf()).param("kind","BUILD").param("expectedPipeline","demo-build").param("token",token).param("acknowledged","true")).andExpect(redirectedUrl(project));
        mvc.perform(get(project).session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("DEMO_ACCEPTED")));
        mvc.perform(post(project+"/runs/"+token+"/refresh").session(session).with(csrf())).andExpect(redirectedUrl(project));
        mvc.perform(get(project).session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("DEMO_SUCCEEDED")));
    }
}
