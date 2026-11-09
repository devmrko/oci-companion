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
 void setup(){when(backups.list(any(),anyString(),anyString())).thenReturn(new VolumeBackups.Catalog(List.of(new VolumeBackups.Volume("v","<script>disk</script>","AVAILABLE",50,"ad")),List.of(new VolumeBackups.Backup("b","snapshot","AVAILABLE",50,"v","FULL","2026-11-09","예약","2026-12-09T00:00:00Z","2026-11-10T00:00:00Z","보존 잠금","source-backup")),List.of("ad")));when(backups.plan(any(),anyString(),anyString(),any())).thenAnswer(a->new VolumeBackups.Plan(a.getArgument(3),"disk",50,"etag"));when(backups.apply(any(),anyString(),anyString(),any(),anyString())).thenReturn("new-id");}
 @Test void previewIsReadOnlyApplyIsBoundAndSingleUse()throws Exception{
  setup();var s=connect();mvc.perform(get("/backups").session(s)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;disk"))).andExpect(content().string(containsString("global-scope"))).andExpect(content().string(containsString("예약"))).andExpect(content().string(containsString("2026-12-09T00:00:00Z"))).andExpect(content().string(containsString("보존 잠금")));
  var p=mvc.perform(post("/backups/preview").session(s).with(csrf()).param("operation","BACKUP").param("target","v").param("name","test").param("type","FULL")).andExpect(status().isOk()).andExpect(content().string(containsString("백업 생성 확인"))).andReturn();verify(backups,never()).apply(any(),anyString(),anyString(),any(),anyString());String token=(String)p.getModelAndView().getModel().get("ticket");mvc.perform(post("/backups/apply").session(s).param("ticket",token)).andExpect(status().isForbidden());mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(redirectedUrl("/backups"));mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict()).andExpect(content().string(containsString("백업·복구")));verify(backups,times(1)).apply(any(),anyString(),anyString(),any(),eq(token));
 }
 @Test void scopeChangeBlocksRestore()throws Exception{
  setup();var s=connect();var p=mvc.perform(post("/backups/preview").session(s).with(csrf()).param("operation","RESTORE").param("target","b").param("name","new").param("domain","ad")).andExpect(status().isOk()).andExpect(content().string(containsString("새 볼륨 복원 확인"))).andReturn();String token=(String)p.getModelAndView().getModel().get("ticket");s.setAttribute("scopeInvalid",true);mvc.perform(post("/backups/apply").session(s).with(csrf()).param("ticket",token)).andExpect(status().isConflict());verify(backups,never()).apply(any(),anyString(),anyString(),any(),anyString());
 }
 VolumeBackups.Backup row(String id,String volume,String source,String state){return new VolumeBackups.Backup(id,id,state,50,volume,"FULL","2026-11-09",source,"미제공","","설정 없음","");}
 @Test void filtersIntersectKeepRestoreInventoryAndSupportDeletedVolumes()throws Exception{
  setup();var all=List.of(row("manual","v","수동","AVAILABLE"),row("scheduled","v","예약","AVAILABLE"),row("faulty","v","예약","FAULTY"),row("other","other-volume","예약","AVAILABLE"),row("deleted","deleted-volume","수동","AVAILABLE"));
  when(backups.list(any(),anyString(),anyString())).thenReturn(new VolumeBackups.Catalog(List.of(new VolumeBackups.Volume("v","disk","AVAILABLE",50,"ad")),all,List.of("ad")));var session=connect();
  var result=mvc.perform(get("/backups").session(session).param("backupVolume","v").param("backupSource","예약").param("backupState","AVAILABLE")).andExpect(status().isOk()).andExpect(content().string(containsString("1건 / 전체 5건"))).andReturn().getModelAndView().getModel();
  org.junit.jupiter.api.Assertions.assertEquals(List.of(all.get(1)),result.get("history"));org.junit.jupiter.api.Assertions.assertEquals(all,((VolumeBackups.Catalog)result.get("inventory")).backups());
  var deleted=mvc.perform(get("/backups").session(session).param("backupVolume","deleted-volume")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();org.junit.jupiter.api.Assertions.assertEquals(List.of(all.get(4)),deleted.get("history"));org.junit.jupiter.api.Assertions.assertEquals("deleted-volume",((Map<?,?>)deleted.get("historyVolumes")).get("deleted-volume"));
  mvc.perform(get("/backups").session(session).param("backupVolume","v").param("backupSource","수동").param("backupState","FAULTY")).andExpect(status().isOk()).andExpect(content().string(containsString("조건에 맞는 백업 없음")));
  var reset=mvc.perform(get("/backups").session(session)).andExpect(status().isOk()).andReturn().getModelAndView().getModel();org.junit.jupiter.api.Assertions.assertEquals(all,reset.get("history"));
  mvc.perform(get("/backups").session(session).param("backupSource","<script>alert(1)</script>")).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
 }
}
