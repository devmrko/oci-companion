package io.github.devmrko.companion;
import java.nio.file.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class CatalogWebTest {
    static final Path DIRECTORY;
    static { try { DIRECTORY=Files.createTempDirectory("catalog-web-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("companion.data-dir",()->DIRECTORY.toString()); }
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Test void resourceWorkflowNeedsNoDatabaseAndIsolatesScopes() throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/connect").param("profile","DEMO")).andExpect(status().isForbidden());
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("DevOps 리소스")));
        mvc.perform(post("/catalog/refresh").session(session).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(status().isForbidden());
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("companion-api")));
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk());
        mvc.perform(post("/sources").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("provider","GITHUB").param("name","test-source").param("url","https://github.com/a/b").param("branch","main")).andExpect(redirectedUrl("/sources"));
        mvc.perform(get("/sources").session(session)).andExpect(content().string(containsString("test-source")));
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("interval","300")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk()).andExpect(content().string(not(containsString("test-source"))));
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(status().isConflict());
        mvc.perform(get("/guides").session(session)).andExpect(status().isOk());
        mvc.perform(post("/disconnect").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }
}
