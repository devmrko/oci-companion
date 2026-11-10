package io.github.devmrko.companion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="companion.extra-hosts=100.64.0.10") @ActiveProfiles("demo") @AutoConfigureMockMvc
class ConfiguredHostsTest {
 @Autowired MockMvc mvc;
 @Test void explicitHostAndLoopbackAllowed()throws Exception{
  mvc.perform(get("/login").with(r->{r.setServerName("100.64.0.10");return r;})).andExpect(status().isOk());
  mvc.perform(get("/login")).andExpect(status().isOk());
 }
 @Test void otherHostsAndSpoofedForwardingDenied()throws Exception{
  mvc.perform(get("/login").header("X-Forwarded-Host","100.64.0.10").with(r->{r.setServerName("evil.example");return r;})).andExpect(status().isForbidden());
  mvc.perform(get("/login").with(r->{r.setServerName("100.64.0.11");return r;})).andExpect(status().isForbidden());
 }
 @Test void extraHostStillRequiresCsrf()throws Exception{
  mvc.perform(post("/connect").with(r->{r.setServerName("100.64.0.10");return r;})).andExpect(status().isForbidden());
 }
}
