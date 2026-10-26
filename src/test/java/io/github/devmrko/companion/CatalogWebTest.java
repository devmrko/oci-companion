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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    io.github.devmrko.companion.port.BuildHistory builds;
    @Test void buildPageUsesSelectedRegionAndRejectsUnlistedRuns() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/builds")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/builds")).andExpect(redirectedUrl("/builds"));
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development")))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("p1","real-pipeline","ACTIVE")));
        org.mockito.Mockito.when(builds.runs(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development"),org.mockito.ArgumentMatchers.eq("p1")))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Run("r1","run-one","SUCCEEDED","2026-10-26T00:00:00Z")));
        org.mockito.Mockito.when(builds.detail(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development"),org.mockito.ArgumentMatchers.eq("p1"),org.mockito.ArgumentMatchers.eq("r1")))
            .thenReturn(new io.github.devmrko.companion.port.BuildHistory.Detail("r1","run-one","SUCCEEDED","",java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Step("SAVE_OUTPUT_ARTIFACTS","SUCCEEDED"))));
        mvc.perform(get("/builds").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("SAVE_OUTPUT_ARTIFACTS")));
        mvc.perform(get("/builds").session(session).param("pipeline","p1").param("run","foreign-run")).andExpect(status().isOk()).andExpect(content().string(containsString("현재 연결에서 찾을 수 없는 항목")));
        org.mockito.Mockito.verify(builds,org.mockito.Mockito.never()).detail(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("foreign-run"));
    }
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
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("test-source")));
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(status().isConflict());
        mvc.perform(get("/guides").session(session)).andExpect(status().isOk());
        mvc.perform(post("/disconnect").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }
    @Test void expandedScopeAndRepositoryFiltersPreserveSourcePage() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk());
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("interval","60").param("back","/sources")).andExpect(redirectedUrl("/sources"));
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("back","/sources")).andExpect(redirectedUrl("/sources"));
        var result=mvc.perform(get("/sources").session(session).param("q","companion-api").param("project","demo-project").param("state","ACTIVE")).andExpect(status().isOk()).andReturn();
        var model=result.getModelAndView().getModel();
        assertEquals(2,((java.util.List<?>)model.get("rows")).size());
        assertEquals(2,((java.util.List<?>)model.get("scopeResults")).size());
        assertEquals(0,((io.github.devmrko.companion.web.CatalogPages.Selection)model.get("selection")).interval());
        mvc.perform(post("/catalog/refresh-one").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("target","foreign")).andExpect(status().isBadRequest());
        mvc.perform(post("/catalog/refresh-one").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("target","demo-development")).andExpect(status().isOk()).andExpect(jsonPath("$.compartment.id").value("demo-development")).andExpect(jsonPath("$.error").isEmpty());

        result=mvc.perform(get("/sources").session(session).param("q","nonexistent")).andExpect(status().isOk()).andReturn();
        assertTrue(((java.util.List<?>)result.getModelAndView().getModel().get("rows")).isEmpty());
    }
}
