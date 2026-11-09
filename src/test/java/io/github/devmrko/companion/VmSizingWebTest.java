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
import io.github.devmrko.companion.port.VmSizing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-sizing-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class VmSizingWebTest {
 @Autowired MockMvc mvc; @MockitoBean VmSizing sizing;
 @Test void previewIsReadOnlyApplyNeedsCsrfAndTicketCannotBeReused() throws Exception {
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  var before=new VmSizing.Snapshot("vm","test VM","STOPPED","Flex",1,8,"etag",new VmSizing.Limits(1,8,1,128,1,16),List.of(new VmSizing.Volume("boot","BOOT","boot",50,"vtag")));
  when(sizing.read(any(),anyString(),anyString(),eq("vm"))).thenReturn(before);
  mvc.perform(get("/environments/sizing").session(session).param("instance","vm")).andExpect(status().isOk()).andExpect(content().string(containsString("스토리지 확장")));
  var preview=mvc.perform(post("/environments/sizing/preview").session(session).with(csrf()).param("instance","vm").param("cpu","2").param("memory","16")).andExpect(status().isOk()).andExpect(content().string(containsString("1.0 → 2.0"))).andExpect(content().string(containsString("global-scope"))).andReturn();
  verify(sizing,never()).apply(any(),anyString(),anyString(),any(),any());String ticket=(String)preview.getModelAndView().getModel().get("ticket");
  mvc.perform(post("/environments/sizing/apply").session(session).param("ticket",ticket)).andExpect(status().isForbidden());
  mvc.perform(post("/environments/sizing/apply").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().is3xxRedirection());
  mvc.perform(post("/environments/sizing/apply").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
  verify(sizing,times(1)).apply(any(),anyString(),anyString(),eq(before),eq(new VmSizing.Change("",2,16,0)));
  mvc.perform(post("/environments/sizing/preview").session(session).with(csrf()).param("instance","vm").param("volume","boot").param("size","60")).andExpect(status().isOk()).andExpect(content().string(containsString("50 → 60 GB")));
 }
}
