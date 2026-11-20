package io.github.devmrko.companion;
import io.github.devmrko.companion.port.BuildHistory;
import io.github.devmrko.companion.port.BuildHistory.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-insights-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class BuildInsightWebTest {
 @Autowired MockMvc mvc; @MockitoBean io.github.devmrko.companion.adapter.OciBuildHistory history;
 @Test void summaryComparisonReportsAndScopeGuardRender() throws Exception {
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  var t=java.time.Instant.now().minusSeconds(100).toString();when(history.runs(any(),anyString(),anyString(),eq("p"))).thenReturn(List.of(new Run("a","new","SUCCEEDED",t,t,java.time.Instant.now().toString()),new Run("b","old","FAILED","2026-01-01T00:00:00Z")));
  when(history.detail(any(),anyString(),anyString(),eq("p"),eq("a"))).thenReturn(new Detail("a","new","SUCCEEDED","",List.of(new Step("test","SUCCEEDED","BUILD","stage",t,java.time.Instant.now().toString()))));
  when(history.detail(any(),anyString(),anyString(),eq("p"),eq("b"))).thenReturn(new Detail("b","old","FAILED","",List.of()));
  when(history.executionContext(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(Map.of("image","OL8"));
  mvc.perform(get("/builds/insights").session(session).param("pipeline","p").param("reports","true")).andExpect(status().isOk()).andExpect(content().string(containsString("단계별 시간"))).andExpect(content().string(containsString("자동 비교 불가"))).andExpect(content().string(containsString("OL8")));
  when(history.detail(any(),anyString(),anyString(),eq("p"),eq("a"))).thenReturn(new Detail("a","new","SUCCEEDED","",List.of(),List.of(new StoredArtifact("report","Artifact Registry","report.tar.gz","v","report-id"),new StoredArtifact("app","Artifact Registry","app.tar.gz","v","app-id"))));
  when(history.reports(any(),anyString(),anyString(),eq("p"),eq("a"),eq("report-id"))).thenReturn(new io.github.devmrko.companion.service.TestReports.Summary("AVAILABLE",1,12,2,0,1,List.of()));
  mvc.perform(get("/builds/insights").session(session).param("pipeline","p").param("reports","true").param("artifact","report-id")).andExpect(status().isOk()).andExpect(content().string(containsString("12 / 2 / 0 / 1")));
  verify(history,never()).reports(any(),anyString(),anyString(),anyString(),anyString(),eq("app-id"));
  mvc.perform(get("/builds/insights").session(session).param("pipeline","p").param("previous","foreign")).andExpect(status().isOk()).andExpect(model().attributeExists("insightError"));
  verify(history,never()).detail(any(),anyString(),anyString(),anyString(),eq("foreign"));clearInvocations(history);session.setAttribute("scopeInvalid",true);
  mvc.perform(get("/builds/insights").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(model().attributeExists("insightError"));verify(history,never()).runs(any(),anyString(),anyString(),anyString());
 }
}
