package io.github.devmrko.companion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.devmrko.companion.port.DeploymentRollback;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-rollback-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class DeploymentRollbackWebTest {
 @Autowired MockMvc mvc;@MockitoBean DeploymentRollback rollback;
 @Test void restorationIsScopedConfirmedAndSingleUse()throws Exception{
  var s=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);var plan=new DeploymentRollback.Plan("pipeline","old","v1","stage","env","OKE","hash");when(rollback.plan(any(),anyString(),anyString(),eq("pipeline"),eq("old"))).thenReturn(plan);
  var result=mvc.perform(get("/deployments/rollback").session(s).param("pipeline","pipeline").param("run","old")).andExpect(status().isOk()).andExpect(content().string(containsString("이 버전으로 복구 실행"))).andReturn();String token=(String)result.getModelAndView().getModel().get("ticket");
  mvc.perform(post("/deployments/rollback").session(s).param("ticket",token)).andExpect(status().isForbidden());when(rollback.execute(any(),anyString(),anyString(),eq(plan),eq(token))).thenReturn("new");mvc.perform(post("/deployments/rollback").session(s).with(csrf()).param("ticket",token)).andExpect(status().is3xxRedirection());mvc.perform(post("/deployments/rollback").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict());verify(rollback,times(1)).execute(any(),anyString(),anyString(),any(),anyString());
  result=mvc.perform(get("/deployments/rollback").session(s).param("pipeline","pipeline").param("run","old")).andReturn();token=(String)result.getModelAndView().getModel().get("ticket");s.setAttribute("compartment","other");mvc.perform(post("/deployments/rollback").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict());verify(rollback,times(1)).execute(any(),anyString(),anyString(),any(),anyString());
 }
}
