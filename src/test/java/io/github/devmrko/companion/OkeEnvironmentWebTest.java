package io.github.devmrko.companion;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.devmrko.companion.port.DeploymentEnvironments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-oke-env-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class OkeEnvironmentWebTest {
 @Autowired MockMvc mvc; @MockitoBean DeploymentEnvironments environments;
 @Test void okeDetailDoesNotRequireComputeAndListPaginates() throws Exception {
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  var all=new ArrayList<DeploymentEnvironments.Environment>();for(int i=0;i<21;i++)all.add(new DeploymentEnvironments.Environment("env"+i,"oke-"+i,"","ACTIVE","p",List.of(),List.of(),"OKE","cluster-"+i));
  when(environments.environments(any(),anyString(),anyString())).thenReturn(all);when(environments.environment(any(),anyString(),anyString(),eq("env0"))).thenReturn(all.getFirst());
  mvc.perform(get("/deployments").session(session).param("environment","env0")).andExpect(status().isOk()).andExpect(content().string(containsString("cluster-0"))).andExpect(content().string(containsString("OKE 클러스터"))).andExpect(content().string(not(containsString("oke-20"))));
  verify(environments,never()).instances(any(),anyString(),anyString());
  mvc.perform(get("/deployments").session(session).param("page","1")).andExpect(status().isOk()).andExpect(content().string(containsString("oke-20"))).andExpect(content().string(containsString("2 / 2")));
  mvc.perform(get("/agents/connections").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("실제 OCI 연결")));
  mvc.perform(post("/agents/connections").session(session)).andExpect(status().isForbidden());
 }
}
