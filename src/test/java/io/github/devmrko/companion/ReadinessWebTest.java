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
import io.github.devmrko.companion.adapter.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-readiness-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class ReadinessWebTest {
 @Autowired MockMvc mvc;@MockitoBean OciBuildHistory history;@MockitoBean BuildDefinition definitions;@MockitoBean BuildSetup setup;
 @Test void scopeAwareReadOnlyChecksAndVocabularyRender()throws Exception{
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  when(history.pipelines(any(),anyString(),anyString())).thenReturn(List.of(new BuildHistory.Pipeline("p","pipeline","ACTIVE")));
  when(definitions.read(any(),anyString(),anyString(),eq("p"))).thenReturn(new BuildDefinition.Definition("pipeline","project","ACTIVE","","v",List.of()));
  mvc.perform(get("/readiness").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(content().string(containsString("필수 충족"))).andExpect(content().string(containsString("data-state=\"FAIL\""))).andExpect(content().string(containsString("OCI Build Pipeline"))).andExpect(content().string(not(containsString("미구현"))));
  clearInvocations(definitions,setup);mvc.perform(get("/readiness").session(session).param("pipeline","foreign")).andExpect(status().isOk()).andExpect(content().string(containsString("조회 실패")));verifyNoInteractions(definitions,setup);
  clearInvocations(history);session.setAttribute("scopeInvalid",true);mvc.perform(get("/readiness").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("미확인")));verifyNoInteractions(history);
 }
}
