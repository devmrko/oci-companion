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
import io.github.devmrko.companion.port.VolumeBackups;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-backups-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class VolumeBackupsWebTest {
 @Autowired MockMvc mvc;@MockitoBean VolumeBackups backups;
 MockHttpSession connect()throws Exception{return (MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);}
 void setup(){when(backups.list(any(),anyString(),anyString())).thenReturn(new VolumeBackups.Catalog(List.of(new VolumeBackups.Volume("v","<script>disk</script>","AVAILABLE",50,"ad")),List.of(new VolumeBackups.Backup("b","snapshot","AVAILABLE",50,"v","FULL","2026-11-09")),List.of("ad")));when(backups.plan(any(),anyString(),anyString(),any())).thenAnswer(a->new VolumeBackups.Plan(a.getArgument(3),"disk",50,"etag"));when(backups.apply(any(),anyString(),anyString(),any(),anyString())).thenReturn("new-id");}
 @Test void previewIsReadOnlyApplyIsBoundAndSingleUse()throws Exception{
  setup();var s=connect();mvc.perform(get("/backups").session(s)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;disk"))).andExpect(content().string(containsString("global-scope")));
  var p=mvc.perform(post("/backups/preview").session(s).with(csrf()).param("operation","BACKUP").param("target","v").param("name","test").param("type","FULL")).andExpect(status().isOk()).andExpect(content().string(containsString("백업 생성 확인"))).andReturn();verify(backups,never()).apply(any(),anyString(),anyString(),any(),anyString());String token=(String)p.getModelAndView().getModel().get("ticket");mvc.perform(post("/backups/apply").session(s).param("ticket",token)).andExpect(status().isForbidden());mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(redirectedUrl("/backups"));mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict()).andExpect(content().string(containsString("백업·복구")));verify(backups,times(1)).apply(any(),anyString(),anyString(),any(),eq(token));
 }
 @Test void scopeChangeBlocksRestore()throws Exception{
  setup();var s=connect();var p=mvc.perform(post("/backups/preview").session(s).with(csrf()).param("operation","RESTORE").param("target","b").param("name","new").param("domain","ad")).andExpect(status().isOk()).andExpect(content().string(containsString("새 볼륨 복원 확인"))).andReturn();String token=(String)p.getModelAndView().getModel().get("ticket");s.setAttribute("scopeInvalid",true);mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict());verify(backups,never()).apply(any(),anyString(),anyString(),any(),anyString());
 }
}
