package io.github.devmrko.companion;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.devmrko.companion.port.DeploymentRuns;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class OkeDeploymentWebTest {
    static final Path DIRECTORY;
    static {try {DIRECTORY=Files.createTempDirectory("oke-web-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("companion.data-dir",()->DIRECTORY.toString());}
    @Autowired MockMvc mvc;
    @MockitoBean DeploymentRuns runs;
    @Test void okePreviewShowsActualTargetAndUsesExistingTicketProtection() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        when(runs.pipelines(any(),anyString(),anyString())).thenReturn(List.of(new DeploymentRuns.Pipeline("p","test OKE","ACTIVE")));
        when(runs.plan(any(),anyString(),anyString(),eq("p"))).thenReturn(new DeploymentRuns.Plan("p","test OKE","test env",List.of(),"fingerprint","OKE","cluster-checked","testing",List.of("registry/app@sha256:"+"a".repeat(64)),List.of(new DeploymentRuns.Job("verify-001","verifier",180,0,3600,List.of("app-auth")))));
        when(runs.start(any(),anyString(),anyString(),eq("p"),eq("fingerprint"),anyString())).thenReturn("run");
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("cluster-checked")))
            .andExpect(content().string(containsString("Namespace: testing")))
            .andExpect(content().string(containsString("verify-001")))
            .andExpect(content().string(containsString("app-auth")))
            .andExpect(content().string(containsString("registry/app@sha256:")))
            .andExpect(content().string(not(containsString("파이프라인이 VM에서 실행됩니다.")))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/deployments/runs").session(session).param("ticket",ticket)).andExpect(status().isForbidden());
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(redirectedUrl("/deployments/runs?pipeline=p&run=run"));
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        when(runs.run(any(),anyString(),anyString(),eq("p"),eq("run"))).thenReturn(new DeploymentRuns.Run("run","test deployment","SUCCEEDED",""));
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("run","run"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("SUCCEEDED")))
            .andExpect(content().string(containsString("업무 검증 결과")))
            .andExpect(content().string(containsString("미확인")));
        verify(runs,times(1)).start(any(),anyString(),anyString(),eq("p"),eq("fingerprint"),anyString());
    }
}
