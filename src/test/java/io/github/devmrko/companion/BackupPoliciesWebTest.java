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
import io.github.devmrko.companion.port.BackupPolicies;
import io.github.devmrko.companion.port.VolumeBackups;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-policies-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class BackupPoliciesWebTest {
 @Autowired MockMvc mvc;@MockitoBean BackupPolicies policies;
 MockHttpSession connect()throws Exception{return (MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);}
 void setup(){var p=new BackupPolicies.Policy("p","<script>bronze</script>",true,"",List.of(new BackupPolicies.Schedule("ONE_DAY","INCREMENTAL","0시","UTC","7일",false,false)));var s=new BackupPolicies.State("v","disk","e","",new BackupPolicies.Assignment("a","old","e",""),List.of(p));when(policies.volumes(any(),anyString(),anyString())).thenReturn(List.of(new VolumeBackups.Volume("v","disk","AVAILABLE",50,"ad")));when(policies.policies(any(),anyString(),anyString())).thenReturn(List.of(p));when(policies.read(any(),anyString(),anyString(),eq("v"))).thenReturn(s);when(policies.plan(any(),anyString(),anyString(),eq("v"),anyString(),anyString())).thenAnswer(a->new BackupPolicies.Plan(a.getArgument(4),s,"REMOVE".equals(a.getArgument(4))?null:p,"e"));}
 @Test void renderPreviewCsrfAndSingleUse()throws Exception{setup();var s=connect();mvc.perform(get("/backups/policies").session(s)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;bronze")));var r=mvc.perform(post("/backups/policies/preview").session(s).with(csrf()).param("volume","v").param("operation","ASSIGN").param("policy","p")).andExpect(status().isOk()).andExpect(content().string(containsString("7일"))).andReturn();verify(policies,never()).apply(any(),anyString(),anyString(),any());String t=(String)r.getModelAndView().getModel().get("ticket");mvc.perform(post("/backups/policies/apply").session(s).param("ticket",t)).andExpect(status().isForbidden());mvc.perform(post("/backups/policies/apply").session(s).with(csrf()).param("ticket",t)).andExpect(redirectedUrl("/backups/policies?volume=v"));mvc.perform(post("/backups/policies/apply").session(s).with(csrf()).param("ticket",t)).andExpect(status().isConflict());verify(policies,times(1)).apply(any(),anyString(),anyString(),any());}
 @Test void scopeChangeBlocksRemoval()throws Exception{setup();var s=connect();mvc.perform(get("/backups/policies").session(s).param("volume","v")).andExpect(status().isOk()).andExpect(content().string(containsString("/backups?backupVolume=v#backup-history")));var r=mvc.perform(post("/backups/policies/preview").session(s).with(csrf()).param("volume","v").param("operation","REMOVE")).andExpect(status().isOk()).andExpect(content().string(containsString("기존 백업은 삭제하지 않습니다"))).andReturn();s.setAttribute("scopeInvalid",true);mvc.perform(post("/backups/policies/apply").session(s).with(csrf()).param("ticket",(String)r.getModelAndView().getModel().get("ticket"))).andExpect(status().isConflict());verify(policies,never()).apply(any(),anyString(),anyString(),any());}
}
