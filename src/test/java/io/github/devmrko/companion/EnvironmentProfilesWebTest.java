package io.github.devmrko.companion;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class EnvironmentProfilesWebTest {
    static final Path DIRECTORY;
    static {try{DIRECTORY=Files.createTempDirectory("profiles-web-").toRealPath();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("companion.environment-profiles-dir",()->DIRECTORY.resolve("profiles").toString());r.add("companion.data-dir",()->DIRECTORY.resolve("catalog").toString());}
    @Autowired MockMvc mvc;
    @Test void uploadPreviewSaveAndExportRoundTripWithCsrfAndPagination()throws Exception{
        mvc.perform(get("/settings/profiles")).andExpect(redirectedUrl("/login"));
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/settings/profiles").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("채워야 할 항목")));
        mvc.perform(post("/settings/profiles").session(session).param("name","Demo").param("template","demo-token")).andExpect(status().isForbidden());
        String location=mvc.perform(post("/settings/profiles").session(session).with(csrf()).param("name","Roundtrip").param("template","demo-token").param("v.environment","dev"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String id=location.substring(location.indexOf("selected=")+9);
        mvc.perform(get(location).session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("작성 중")));
        String json=mvc.perform(get("/settings/profiles/"+id+"/export").session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition",containsString("attachment;"))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var upload=new MockMultipartFile("file","profile.json","application/json",json.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/settings/profiles/import").file(upload).session(session)).andExpect(status().isForbidden());
        mvc.perform(multipart("/settings/profiles/import").file(upload).session(session).with(csrf())).andExpect(status().isOk())
            .andExpect(content().string(containsString("가져오기 미리보기입니다"))).andExpect(model().attribute("selected", "")).andExpect(model().attribute("revision",0L));
        mvc.perform(get("/settings/profiles").session(session)).andExpect(model().attribute("total",1));
        String newLocation=mvc.perform(post("/settings/profiles").session(session).with(csrf()).param("name","Imported").param("template","demo-token").param("v.environment","dev"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();assertNotEquals(location,newLocation);
        mvc.perform(post("/settings/profiles").session(session).with(csrf()).param("selected",id).param("revision","0").param("name","Stale").param("template","demo-token"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/settings/profiles").session(session).with(csrf()).param("name","Bad").param("template","demo-token").param("v.password","never-store"))
            .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("never-store"))));
        mvc.perform(multipart("/settings/profiles/import").file(new MockMultipartFile("file","bad.json","application/json","{ broken secret-fixture".getBytes(StandardCharsets.UTF_8))).session(session).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("secret-fixture"))));
        for(int i=0;i<19;i++)mvc.perform(post("/settings/profiles").session(session).with(csrf()).param("name","Page "+i).param("template","demo-token")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/settings/profiles").session(session)).andExpect(model().attribute("items",hasSize(20))).andExpect(model().attribute("total",21));
        mvc.perform(get("/settings/profiles").session(session).param("page","1")).andExpect(model().attribute("items",hasSize(1)));
    }
}
