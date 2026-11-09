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
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-container-preparation-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class ContainerPreparationWebTest {
 @Autowired MockMvc mvc; @MockitoBean ContainerPreparation preparation; @MockitoBean Registry registry;
 @Test void previewDownloadAndCreateUseSameScopedSingleUseTicket() throws Exception {
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  when(preparation.list(any(),anyString(),anyString())).thenReturn(List.of());when(preparation.options(any(),anyString(),anyString(),anyString())).thenReturn(new ContainerPreparation.Options(List.of("ad"),List.of(new ContainerPreparation.Shape("shape","processor",new VmSizing.Limits(1,8,1,128,1,16))),List.of(new ContainerPreparation.Subnet("subnet","private subnet","vcn",true))));when(registry.list(any(),anyString(),anyString(),anyString())).thenReturn(List.of());when(registry.artifacts(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(new Registry.Page(List.of(new Registry.Artifact("image","hello","v1","sha256:hash","10","AVAILABLE","")),""));
  var input=new ContainerPreparation.Input("hello","ad","shape",1,2,"subnet",false,"image",Map.of("PORT","8080"),List.of(),List.of());var preview=new ContainerPreparation.Preview(input,"registry/hello@sha256:hash","{\"name\":\"hello\"}","fingerprint");when(preparation.preview(any(),anyString(),anyString(),eq(input))).thenReturn(preview);
  mvc.perform(get("/environments/containers").session(session).param("domain","ad").param("shape","shape").param("repository","repo")).andExpect(status().isOk()).andExpect(content().string(containsString("생성값 확인")));
  var result=mvc.perform(post("/environments/containers/preview").session(session).with(csrf()).param("name","hello").param("domain","ad").param("shape","shape").param("repository","repo").param("cpu","1").param("memory","2").param("subnet","subnet").param("image","image").param("variables","PORT=8080")).andExpect(status().isOk()).andExpect(content().string(containsString("global-scope"))).andReturn();String ticket=(String)result.getModelAndView().getModel().get("ticket");verify(preparation,never()).create(any(),anyString(),anyString(),any(),anyString());
  mvc.perform(get("/environments/containers/request.json").session(session).param("ticket",ticket)).andExpect(status().isOk()).andExpect(content().json(preview.json()));
  mvc.perform(get("/environments/containers/request.json").session(session).param("ticket","other")).andExpect(status().isConflict());
  mvc.perform(post("/environments/containers/create").session(session).param("ticket",ticket)).andExpect(status().isForbidden());
  when(preparation.create(any(),anyString(),anyString(),eq(preview),eq(ticket))).thenReturn("created");mvc.perform(post("/environments/containers/create").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().is3xxRedirection());mvc.perform(post("/environments/containers/create").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());verify(preparation,times(1)).create(any(),anyString(),anyString(),eq(preview),eq(ticket));
 }
}
