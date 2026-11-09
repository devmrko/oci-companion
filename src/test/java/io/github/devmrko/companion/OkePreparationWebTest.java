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
@SpringBootTest(properties="companion.data-dir=${java.io.tmpdir}/companion-oke-preparation-test") @ActiveProfiles("demo") @AutoConfigureMockMvc
class OkePreparationWebTest {
 @Autowired MockMvc mvc; @MockitoBean OkePreparation preparation; @MockitoBean BuildAdministration projects;
 @Test void privateNetworkFormRendersAndCreateTicketCannotBeReused() throws Exception {
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  var cluster=new OkePreparation.Cluster("cluster","test cluster","ACTIVE","v1");when(preparation.clusters(any(),anyString(),anyString())).thenReturn(List.of(cluster));
  when(preparation.read(any(),anyString(),anyString(),eq("cluster"))).thenReturn(new OkePreparation.Detail(cluster,true,"vcn",List.of(new OkePreparation.Pool("nodes","ACTIVE","Flex","2","16","50","1")),List.of(new OkePreparation.Network("subnet","private subnet")),List.of(new OkePreparation.Network("nsg","security group")),List.of()));
  when(projects.projects(any(),anyString(),anyString())).thenReturn(List.of());
  var view=mvc.perform(get("/environments/oke").session(session).param("cluster","cluster")).andExpect(status().isOk()).andExpect(content().string(containsString("private subnet"))).andExpect(content().string(containsString("2 / 16"))).andReturn();
  var ticket=(String)view.getModelAndView().getModel().get("ticket");when(preparation.create(any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyList(),anyString())).thenReturn("env");
  mvc.perform(post("/environments/oke").session(session).param("ticket",ticket).param("project","p").param("name","name")).andExpect(status().isForbidden());
  mvc.perform(post("/environments/oke").session(session).with(csrf()).param("ticket",ticket).param("project","p").param("name","name").param("subnet","subnet").param("nsgs","nsg")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/environments/oke").session(session).with(csrf()).param("ticket",ticket).param("project","p").param("name","name")).andExpect(status().isConflict());
  verify(preparation,times(1)).create(any(),anyString(),anyString(),eq("cluster"),eq("p"),eq("name"),eq(""),eq("subnet"),eq(List.of("nsg")),eq(ticket));
 }
}
