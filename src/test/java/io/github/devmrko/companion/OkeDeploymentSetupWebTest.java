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
import io.github.devmrko.companion.port.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-oke-setup-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class OkeDeploymentSetupWebTest {
 @Autowired MockMvc mvc;@MockitoBean OkeDeploymentSetup setup;@MockitoBean DeploymentEnvironments environments;@MockitoBean Registry registry;
 @Test void formValuesReachPreviewAndScopedSingleUseCreate()throws Exception{
  var s=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  when(environments.environments(any(),anyString(),anyString())).thenReturn(List.of(new DeploymentEnvironments.Environment("env","OKE","description","ACTIVE","project",List.of(),List.of(),"OKE","cluster")));when(registry.list(any(),anyString(),anyString(),eq("CONTAINER"))).thenReturn(List.of());when(registry.artifacts(any(),anyString(),anyString(),eq("CONTAINER"),eq("repo"),anyString())).thenReturn(new Registry.Page(List.of(new Registry.Artifact("image","hello","v1","sha256:hash","10","AVAILABLE","")),""));
  var result=mvc.perform(get("/deployments/oke/configure").session(s).param("environment","env").param("repository","repo")).andExpect(status().isOk()).andExpect(content().string(containsString("value=\"image\""))).andReturn();var token=(String)result.getModelAndView().getModel().get("formTicket");
  when(setup.preview(any(),anyString(),anyString(),any())).thenAnswer(inv->new OkeDeploymentSetup.Preview(inv.getArgument(3),"project","OKE","image@sha256:hash","kind: Deployment\n","fingerprint"));
  result=mvc.perform(post("/deployments/oke/configure/preview").session(s).with(csrf()).param("ticket",token).param("environment","env").param("repository","repo").param("image","image").param("name","test").param("description","purpose").param("namespace","companion-demo").param("app","hello").param("architecture","arm64").param("replicas","1").param("cpuMillis","500").param("memoryMi","512").param("port","8080").param("healthPath","/hello").param("variables","PORT=8080").param("pullSecret","pull").param("claim","data").param("mountPath","/data").param("rollback","true")).andExpect(status().isOk()).andExpect(content().string(containsString("YAML 다운로드"))).andReturn();
  verify(setup).preview(any(),anyString(),anyString(),argThat(i->i.architecture().equals("arm64")&&i.cpuMillis()==500&&i.memoryMi()==512&&i.claim().equals("data")&&i.rollback()));var create=(String)result.getModelAndView().getModel().get("createTicket");
  mvc.perform(get("/deployments/oke/configure/manifest.yaml").session(s).param("ticket",create)).andExpect(status().isOk()).andExpect(content().string("kind: Deployment\n"));
  mvc.perform(post("/deployments/oke/configure/create").session(s).param("ticket",create)).andExpect(status().isForbidden());when(setup.create(any(),anyString(),anyString(),any(),anyString())).thenReturn(new DeploymentSetup.Result("pipeline","","manifest","stage",""));
  mvc.perform(post("/deployments/oke/configure/create").session(s).with(csrf()).param("ticket",create)).andExpect(status().isOk()).andExpect(content().string(containsString("배포 실행·이력")));
  mvc.perform(post("/deployments/oke/configure/create").session(s).with(csrf()).param("ticket",create)).andExpect(status().isConflict());verify(setup,times(1)).create(any(),anyString(),anyString(),any(),anyString());
 }
}
