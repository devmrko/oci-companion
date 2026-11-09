package io.github.devmrko.companion;
import java.nio.file.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc
class CatalogWebTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BackupPolicies backupPolicies;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.VolumeBackups volumeBackups;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentRollback rollback;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.OkeDeploymentSetup okeDeploymentSetup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildProjectResources projectResources;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.ReportStorage reportStorage;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.GitSpecifications gitSpecifications;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.FunctionBuilds functions;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.SourceVersions sourceVersions;
    static final Path DIRECTORY;
    static { try { DIRECTORY=Files.createTempDirectory("catalog-web-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("companion.data-dir",()->DIRECTORY.toString()); }
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildAdministration administration;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildSetup setup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildLogs logs;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.Registry registry;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildAutomation automation;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentImages deploymentImages;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.ImagePlatforms imagePlatforms;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildDefinition definitions;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildControl control;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentEnvironments deploymentEnvironments;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentRuns deploymentRuns;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentLogs deploymentLogs;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.DeploymentSetup deploymentSetup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.VmCapacity vmCapacity;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.VmSizing vmSizing;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.OkePreparation okePreparation;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.ContainerPreparation containerPreparation;
    @org.springframework.test.context.bean.override.mockito.MockitoBean io.github.devmrko.companion.port.BuildHandoff handoff;
    @Test void deploymentHistoryAndNativeLogsRenderWithoutInjectingMarkup() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var run=new io.github.devmrko.companion.port.DeploymentRuns.Run("run","release-v1","SUCCEEDED","",java.util.List.of(new io.github.devmrko.companion.port.DeploymentRuns.Step("Deploy VM","vm","Rollout deployment","SUCCEEDED")));
        org.mockito.Mockito.when(deploymentRuns.run(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("pipeline"),org.mockito.ArgumentMatchers.eq("run"))).thenReturn(run);
        org.mockito.Mockito.when(deploymentRuns.history(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("pipeline"),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.github.devmrko.companion.port.DeploymentRuns.History(java.util.List.of(run),"next-page"));
        org.mockito.Mockito.when(deploymentLogs.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("pipeline"),org.mockito.ArgumentMatchers.eq("run"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq(""))).thenReturn(new io.github.devmrko.companion.port.BuildLogs.Page("READY","OCI Logging",java.util.List.of(new io.github.devmrko.companion.port.BuildLogs.Line("now","VM","<script>alert('log')</script>")),"next-log","until"));
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","pipeline").param("run","run")).andExpect(status().isOk()).andExpect(content().string(containsString("배포 로그"))).andExpect(content().string(containsString("실행 이력"))).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert")))).andExpect(content().string(containsString("next-log"))).andExpect(content().string(containsString("Rollout deployment")));
    }
    @Test void deploymentSetupRendersCapacityAndCreatesOnlyReviewedSettingsOnce() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var env=new io.github.devmrko.companion.port.DeploymentEnvironments.Environment("env","test VM environment","description","ACTIVE","project",java.util.List.of("vm"),java.util.List.of());
        org.mockito.Mockito.when(deploymentEnvironments.environment(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("env"))).thenReturn(env);
        org.mockito.Mockito.when(vmCapacity.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("vm"))).thenReturn(new io.github.devmrko.companion.port.VmCapacity.Capacity("VM","STOPPED","Flex","1","2","8","0",java.util.List.of(new io.github.devmrko.companion.port.VmCapacity.Disk("부트","boot","50","AVAILABLE")),java.util.List.of()));
        var page=mvc.perform(get("/deployments/configure").session(session).param("environment","env")).andExpect(status().isOk()).andExpect(content().string(containsString("할당 용량"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("formTicket");
        var input=new io.github.devmrko.companion.port.DeploymentSetup.Input("env","","a","release","","/tmp/app","ocarun","","test -f app","","test -f app","","","",600,1);
        var preview=new io.github.devmrko.companion.port.DeploymentSetup.Preview(input,"project","VM","app.tar.gz","v1","sha","1","version: '1.0'","fingerprint");
        org.mockito.Mockito.when(deploymentSetup.preview(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(preview);
        page=mvc.perform(post("/deployments/configure/preview").session(session).with(csrf()).param("ticket",ticket).param("environment","env").param("repository","").param("artifact","a").param("name","release").param("description","").param("destination","/tmp/app").param("runAs","ocarun").param("stop","").param("install","test -f app").param("start","").param("health","test -f app").param("recovery","").param("variables","").param("vaultVariables","").param("timeout","600").param("batch","1")).andExpect(status().isOk()).andExpect(content().string(containsString("생성 전 확인"))).andReturn();
        String createTicket=(String)page.getModelAndView().getModel().get("createTicket");
        org.mockito.Mockito.when(deploymentSetup.create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(preview),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.github.devmrko.companion.port.DeploymentSetup.Result("pipeline","package","spec","stage",""));
        mvc.perform(post("/deployments/configure/create").session(session).param("ticket",createTicket)).andExpect(status().isForbidden());
        mvc.perform(post("/deployments/configure/create").session(session).with(csrf()).param("ticket",createTicket).param("artifact","tampered")).andExpect(status().isOk()).andExpect(content().string(containsString("파이프라인 생성 완료")));
        mvc.perform(post("/deployments/configure/create").session(session).with(csrf()).param("ticket",createTicket)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentSetup).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(preview),org.mockito.ArgumentMatchers.eq(createTicket));
        org.mockito.Mockito.verifyNoInteractions(deploymentRuns);
    }
    @Test void buildHandoffIsRevalidatedAndCannotBeChangedByPostParameters() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(deploymentRuns.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.DeploymentRuns.Plan("p","release","VM",java.util.List.of("vm"),"plan"));
        org.mockito.Mockito.when(handoff.verify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("bp"),org.mockito.ArgumentMatchers.eq("br"),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.BuildHandoff.Proof("br","before",1),new io.github.devmrko.companion.port.BuildHandoff.Proof("br","after",1));
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("sourcePipeline","bp").param("sourceRun","br")).andExpect(status().isOk()).andExpect(content().string(containsString("SHA-256"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");assertNotNull(ticket);
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket).param("sourceRun","other")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentRuns,org.mockito.Mockito.never()).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("sourceRun","br")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("ticket"));
    }
    @Test void deploymentRetryRetainsBuildAndRechecksItsArtifact() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(deploymentRuns.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.DeploymentRuns.Plan("p","release","VM",java.util.List.of("vm"),"plan"));
        org.mockito.Mockito.when(handoff.verify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("bp"),org.mockito.ArgumentMatchers.eq("br"),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.BuildHandoff.Proof("br","proof",1));
        org.mockito.Mockito.when(deploymentRuns.start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("plan"),org.mockito.ArgumentMatchers.anyString())).thenReturn("run");
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("sourcePipeline","bp").param("sourceRun","br")).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(redirectedUrl("/deployments/runs?pipeline=p&run=run&sourcePipeline=bp&sourceRun=br"));
        var run=new io.github.devmrko.companion.port.DeploymentRuns.Run("run","release","FAILED","");
        org.mockito.Mockito.when(deploymentRuns.run(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("run"))).thenReturn(run);
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("run","run").param("sourcePipeline","bp").param("sourceRun","br"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("sourcePipeline=bp&amp;sourceRun=br&amp;pipeline=p")));
        org.mockito.Mockito.when(handoff.verify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("bp"),org.mockito.ArgumentMatchers.eq("br"),org.mockito.ArgumentMatchers.eq("p"))).thenThrow(io.github.devmrko.companion.service.Errors.HANDOFF.exception());
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p").param("sourcePipeline","bp").param("sourceRun","br"))
            .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("ticket"));
        org.mockito.Mockito.verify(deploymentRuns,org.mockito.Mockito.times(1)).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void architectureMismatchBlocksPreviewAndIsRecheckedAtSubmit() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var plan=new io.github.devmrko.companion.port.DeploymentRuns.Plan("p","release","env",java.util.List.of(),"plan","OKE","cluster","ns",java.util.List.of("image"),java.util.Map.of("image",java.util.List.of("arm64")));
        org.mockito.Mockito.when(deploymentRuns.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(plan);
        var arm=new io.github.devmrko.companion.port.ImagePlatforms.Result(java.util.List.of(new io.github.devmrko.companion.port.ImagePlatforms.Platform("linux","arm64")),true,"");
        var x86=new io.github.devmrko.companion.port.ImagePlatforms.Result(java.util.List.of(new io.github.devmrko.companion.port.ImagePlatforms.Platform("linux","amd64")),true,"");
        org.mockito.Mockito.when(imagePlatforms.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("image"))).thenReturn(x86);
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("ticket")).andExpect(content().string(not(containsString("확인한 배포 실행"))));
        org.mockito.Mockito.when(imagePlatforms.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("image"))).thenReturn(arm,x86);
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andReturn();String ticket=(String)page.getModelAndView().getModel().get("ticket");assertNotNull(ticket);
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentRuns,org.mockito.Mockito.never()).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void missingImagePreventsTicketAndIsCheckedAgainBeforeExecution() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var plan=new io.github.devmrko.companion.port.DeploymentRuns.Plan("p","release","env",java.util.List.of(),"plan","OKE","cluster","ns",java.util.List.of("image"));
        org.mockito.Mockito.when(deploymentRuns.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(plan);
        var missing=new io.github.devmrko.companion.port.DeploymentImages.Check("image",io.github.devmrko.companion.port.DeploymentImages.State.BLOCKED,"digest 없음");
        org.mockito.Mockito.when(deploymentImages.inspect(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of(missing));
        mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("ticket")).andExpect(content().string(not(containsString("확인한 배포 실행"))));
        org.mockito.Mockito.when(deploymentImages.inspect(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.DeploymentImages.Check("image",io.github.devmrko.companion.port.DeploymentImages.State.VERIFIED,"AVAILABLE")),java.util.List.of(missing));
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andReturn();String ticket=(String)page.getModelAndView().getModel().get("ticket");assertNotNull(ticket);
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentRuns,org.mockito.Mockito.never()).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void deploymentExecutionRequiresCsrfOneUseTicketAndUnchangedScope() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(deploymentRuns.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.DeploymentRuns.Plan("p","release 0.4.0","demo VM",java.util.List.of("vm"),"fingerprint"));
        org.mockito.Mockito.when(deploymentRuns.start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("fingerprint"),org.mockito.ArgumentMatchers.anyString())).thenReturn("run");
        var page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(content().string(containsString("release 0.4.0"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/deployments/runs").session(session).param("ticket",ticket)).andExpect(status().isForbidden());
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(redirectedUrl("/deployments/runs?pipeline=p&run=run"));
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        page=mvc.perform(get("/deployments/runs").session(session).param("pipeline","p")).andReturn();ticket=(String)page.getModelAndView().getModel().get("ticket");session.setAttribute("region","changed");
        mvc.perform(post("/deployments/runs").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentRuns,org.mockito.Mockito.times(1)).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("fingerprint"),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void environmentPreparationUsesNativePortAndRejectsReplayAndScopeChange() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(administration.projects(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildAdministration.Project("p","project","description","ACTIVE","topic")));
        org.mockito.Mockito.when(deploymentEnvironments.instances(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.DeploymentEnvironments.Vm("vm","test VM","STOPPED","shape")));
        org.mockito.Mockito.when(deploymentEnvironments.create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString())).thenReturn("env");
        var page=mvc.perform(get("/environments").session(session).param("tab","new")).andExpect(status().isOk()).andExpect(content().string(containsString("test VM"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/environments").session(session).param("ticket",ticket).param("project","p").param("name","test").param("instances","vm")).andExpect(status().isForbidden());
        mvc.perform(post("/environments").session(session).with(csrf()).param("ticket",ticket).param("project","p").param("name","test").param("instances","vm")).andExpect(redirectedUrl("/environments?environment=env"));
        mvc.perform(post("/environments").session(session).with(csrf()).param("ticket",ticket).param("project","p").param("name","test").param("instances","vm")).andExpect(status().isConflict());
        page=mvc.perform(get("/environments").session(session).param("tab","new")).andReturn();ticket=(String)page.getModelAndView().getModel().get("ticket");session.setAttribute("region","changed");
        mvc.perform(post("/environments").session(session).with(csrf()).param("ticket",ticket).param("project","p").param("name","test").param("instances","vm")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(deploymentEnvironments,org.mockito.Mockito.times(1)).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("test"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq(java.util.List.of("vm")),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void environmentReadFailureIsNotAnEmptyEnvironmentList() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(deploymentEnvironments.environments(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenThrow(io.github.devmrko.companion.service.Errors.CLOUD.exception());
        mvc.perform(get("/deployments").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString(io.github.devmrko.companion.service.Errors.CLOUD.safeMessage()))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("선택 구획에 VM 배포 환경이 없습니다."))));
    }
    @Test void projectResourcesCreateOnceAndRejectWrongScope() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(administration.projects(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildAdministration.Project("p","project","description","ACTIVE","topic")));
        org.mockito.Mockito.when(projectResources.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.BuildProjectResources.Resources(java.util.List.of(),java.util.List.of(),java.util.List.of()));
        var page=mvc.perform(get("/builds/project-resources").session(session).param("project","p")).andExpect(status().isOk()).andExpect(content().string(containsString("코드 저장소 생성"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/builds/project-resources").session(session).param("ticket",ticket).param("action","repository").param("name","test")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/project-resources").session(session).with(csrf()).param("ticket",ticket).param("action","repository").param("name","test")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/builds/project-resources").session(session).with(csrf()).param("ticket",ticket).param("action","repository").param("name","test")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(projectResources,org.mockito.Mockito.times(1)).createRepository(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("test"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq(ticket));
        page=mvc.perform(get("/builds/project-resources").session(session).param("project","p")).andReturn();ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/project-resources").session(session).with(csrf()).param("ticket",ticket).param("action","log").param("name","test")).andExpect(status().isConflict());
    }
    @Test void reportStorageRejectsStaleScopeCsrfAndOtherRunObjects() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/sources"));
        mvc.perform(post("/builds/report-storage/buckets").session(session).param("region","us-ashburn-1").param("compartment","demo-development").param("name","reports")).andExpect(status().isForbidden());
        mvc.perform(get("/builds/report-storage").session(session).param("region","wrong-region").param("compartment","demo-development")).andExpect(status().isConflict());
        mvc.perform(get("/builds/report-storage/report").session(session).param("region","us-ashburn-1").param("compartment","demo-development").param("pipeline","p").param("run","r").param("bucket","reports").param("prefix","reports").param("object","reports/p/another-run/stage/test-reports.tar.gz")).andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(reportStorage);
        mvc.perform(post("/builds/report-storage/buckets").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("name","reports")).andExpect(status().isOk());
        org.mockito.Mockito.verify(reportStorage).createBucket(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development"),org.mockito.ArgumentMatchers.eq("reports"));
    }
    @Test void automationShowsRealServiceErrorsAndConsumesMutationTicketOnce() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("p","pipeline","ACTIVE","project","project")));
        org.mockito.Mockito.when(definitions.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenReturn(new io.github.devmrko.companion.port.BuildDefinition.Definition("pipeline","project","ACTIVE","","revision",java.util.List.of()));
        org.mockito.Mockito.when(automation.schedules(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"))).thenThrow(io.github.devmrko.companion.service.Errors.CLOUD.exception());
        var page=mvc.perform(get("/builds/automation").session(session).param("pipeline","p")).andExpect(status().isOk()).andExpect(content().string(containsString("IAM 권한"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("ticket");
        mvc.perform(get("/builds/automation/preview").session(session).param("cron","0 0 * * *")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").exists());
        mvc.perform(post("/builds/automation/template").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",containsString(".zip")));
        mvc.perform(post("/builds/automation/schedule").session(session).param("ticket",ticket).param("function","fn").param("name","nightly").param("cron","0 0 * * *").param("confirmed","true")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/automation/schedule").session(session).with(csrf()).param("ticket",ticket).param("function","fn").param("name","nightly").param("cron","0 0 * * *").param("confirmed","true")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/builds/automation/schedule").session(session).with(csrf()).param("ticket",ticket).param("function","fn").param("name","nightly").param("cron","0 0 * * *").param("confirmed","true")).andExpect(status().isConflict());
        mvc.perform(get("/builds/configure").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Vault 참조")));
    }
    @Test void registeredTaskSourceRejectsUrlTamperingAndStaleScope() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var model=mvc.perform(get("/builds/tasks").session(session).param("view","new")).andReturn().getModelAndView().getModel();
        var scope=(io.github.devmrko.companion.web.CatalogPages.Selection)model.get("selection");
        var connection=(io.github.devmrko.companion.domain.Models.Connection)model.get("connection");
        var catalog=context.getBean(io.github.devmrko.companion.service.CatalogService.class);
        catalog.register(connection,scope.region(),scope.compartment(),"GITHUB","registered-test","https://github.com/a/registered-test","main","");
        var source=catalog.sources(connection,scope.region(),scope.compartment()).stream().filter(v->v.name().equals("registered-test")).findFirst().orElseThrow();
        org.mockito.Mockito.when(sourceVersions.refs(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(source))).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.SourceVersions.Ref("BRANCH","main","a".repeat(40))));
        mvc.perform(get("/builds/tasks/source-versions").session(session).param("region",scope.region()).param("compartment",scope.compartment()).param("sourceId",source.id()).param("source",source.url())).andExpect(status().isOk()).andExpect(jsonPath("$[0].commit").value("a".repeat(40)));
        mvc.perform(get("/builds/tasks/source-versions").session(session).param("region",scope.region()).param("compartment",scope.compartment()).param("sourceId",source.id()).param("source","https://github.com/a/wrong")).andExpect(status().isBadRequest());
        mvc.perform(get("/builds/tasks/source-versions").session(session).param("region","wrong-region").param("compartment",scope.compartment()).param("sourceId",source.id())).andExpect(status().isConflict());
    }
    @Test void taskWizardPersistsActualSettingsAndLoadsThemIntoExecution() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/builds").session(session)).andExpect(redirectedUrl("/builds/tasks"));
        var page=mvc.perform(get("/builds/tasks").session(session).param("view","new")).andExpect(status().isOk()).andExpect(content().string(containsString("현재 선택"))).andExpect(content().string(containsString("커밋 SHA 도움말"))).andReturn();
        var scope=(io.github.devmrko.companion.web.CatalogPages.Selection)page.getModelAndView().getModel().get("selection");
        var b=new io.github.devmrko.companion.port.FunctionBuilds.Builder("fn-task","maven","ACTIVE","image",3072,3600,"maven-v1","ns","bucket","prefix","etag");
        org.mockito.Mockito.when(functions.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("app-task"),org.mockito.ArgumentMatchers.eq("fn-task"))).thenReturn(b);
        mvc.perform(post("/builds/tasks/save").session(session).with(csrf()).param("region",scope.region()).param("compartment",scope.compartment()).param("name","작업 검증").param("runner","FUNCTIONS").param("application","app-task").param("function","fn-task").param("source","https://github.com/a/b").param("commit","a".repeat(40)).param("branch","main").param("goal","verify").param("profiles","quality").param("artifact","target/*.jar")).andExpect(redirectedUrl("/builds/tasks"));
        var model=mvc.perform(get("/builds/tasks").session(session)).andExpect(content().string(containsString("작업 검증"))).andReturn().getModelAndView().getModel();
        @SuppressWarnings("unchecked") var saved=(java.util.List<io.github.devmrko.companion.port.BuildTaskStore.Task>)model.get("tasks");var task=saved.stream().filter(t->t.name().equals("작업 검증")).findFirst().orElseThrow();
        org.mockito.Mockito.when(functions.applications(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.FunctionBuilds.Application("app-task","app","ACTIVE","GENERIC_ARM")));
        org.mockito.Mockito.when(functions.builders(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("app-task"))).thenReturn(java.util.List.of(b));
        mvc.perform(get("/builds/functions").session(session).param("view","execute").param("application","app-task").param("function","fn-task").param("task",task.id())).andExpect(status().isOk()).andExpect(content().string(containsString("value=\"quality\""))).andExpect(content().string(containsString("value=\"https://github.com/a/b\"")));
        org.mockito.Mockito.verify(functions,org.mockito.Mockito.never()).invoke(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void functionBuilderSelectionTemplateAndSingleUseExecutionAreScoped() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var builder=new io.github.devmrko.companion.port.FunctionBuilds.Builder("function-1","maven","ACTIVE","image",3072,3600,"maven-v1","ns","bucket","maven-1","etag-1");
        org.mockito.Mockito.when(functions.applications(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(new io.github.devmrko.companion.port.FunctionBuilds.Application("app-1","build-app","ACTIVE","GENERIC_ARM")));
        org.mockito.Mockito.when(functions.builders(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("app-1"))).thenReturn(java.util.List.of(builder));
        mvc.perform(get("/builds/functions").session(session).param("view","new")).andExpect(status().isOk()).andExpect(content().string(containsString("Fn Project ZIP 다운로드")));
        var page=mvc.perform(get("/builds/functions").session(session).param("view","execute").param("application","app-1").param("function","function-1")).andExpect(status().isOk()).andExpect(content().string(containsString("Maven 빌드 실행"))).andExpect(content().string(not(containsString("ApplicationAttributeMap")))).andReturn();
        var scope=(io.github.devmrko.companion.web.CatalogPages.Selection)page.getModelAndView().getModel().get("selection");
        org.mockito.Mockito.when(functions.run(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.github.devmrko.companion.port.FunctionBuilds.Run("run","RUNNING","{}",""));
        org.mockito.Mockito.when(functions.log(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("[INFO] Compiling");
        mvc.perform(get("/builds/functions/progress").session(session).param("region",scope.region()).param("compartment",scope.compartment()).param("application","app-1").param("function","function-1").param("run","run"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.log").value("[INFO] Compiling"));
        mvc.perform(get("/builds/functions/progress").session(session).param("region","wrong-region").param("compartment",scope.compartment()).param("application","app-1").param("function","function-1").param("run","run"))
            .andExpect(status().isConflict());
        String token=(String)page.getModelAndView().getModel().get("functionTicket");
        mvc.perform(post("/builds/functions/run").session(session).param("ticket",token).param("source","https://github.com/a/b").param("commit","a".repeat(40)).param("goal","test")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/functions/run").session(session).with(csrf()).param("ticket",token).param("source","https://github.com/a/b").param("commit","a".repeat(40)).param("goal","test")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/builds/functions/run").session(session).with(csrf()).param("ticket",token).param("source","https://github.com/a/b").param("commit","a".repeat(40)).param("goal","test")).andExpect(status().isConflict());
        page=mvc.perform(get("/builds/functions").session(session).param("view","execute").param("application","app-1").param("function","function-1")).andReturn();
        token=(String)page.getModelAndView().getModel().get("functionTicket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/builds/functions")).andExpect(redirectedUrl("/builds/functions"));
        mvc.perform(post("/builds/functions/run").session(session).with(csrf()).param("ticket",token).param("source","https://github.com/a/b").param("commit","a".repeat(40)).param("goal","test")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(functions,org.mockito.Mockito.times(1)).invoke(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("etag-1"),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void registryWorksWithoutPipelineAndUsesScopedSingleUseServerTicket() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var repository=new io.github.devmrko.companion.port.Registry.Repository("repo","GENERIC","builds","","Available",true,false,"server-etag");
        org.mockito.Mockito.when(registry.list(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("GENERIC"))).thenReturn(java.util.List.of(repository));
        org.mockito.Mockito.when(registry.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("GENERIC"),org.mockito.ArgumentMatchers.eq("repo"))).thenReturn(repository);
        org.mockito.Mockito.when(registry.artifacts(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("GENERIC"),org.mockito.ArgumentMatchers.eq("repo"),org.mockito.ArgumentMatchers.anyString())).thenReturn(new io.github.devmrko.companion.port.Registry.Page(java.util.List.of(),""));
        var result=mvc.perform(get("/builds").session(session).param("tab","registry").param("repository","repo")).andExpect(status().isOk()).andExpect(content().string(containsString("빌드 저장소로 선택"))).andReturn();
        String ticket=(String)result.getModelAndView().getModel().get("registryEditTicket");
        mvc.perform(post("/builds/registry").session(session).param("ticket",ticket).param("name","builds")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/registry").session(session).with(csrf()).param("ticket",ticket).param("name","builds").param("etag","forged")).andExpect(status().is3xxRedirection());
        org.mockito.Mockito.verify(registry).update(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("GENERIC"),org.mockito.ArgumentMatchers.eq("repo"),org.mockito.ArgumentMatchers.eq("server-etag"),org.mockito.ArgumentMatchers.eq("builds"),org.mockito.ArgumentMatchers.eq(""));
        mvc.perform(post("/builds/registry").session(session).with(csrf()).param("ticket",ticket).param("name","builds")).andExpect(status().isConflict()).andExpect(view().name("operation-error")).andExpect(content().string(containsString("권한 설정 안내"))).andExpect(content().string(containsString("빌드 이력"))).andExpect(content().string(containsString("global-scope")));
        String create=(String)result.getModelAndView().getModel().get("registryTicket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/registry").session(session).with(csrf()).param("ticket",create).param("name","builds")).andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(builds);
    }
    @Test void setupWritesUseServerEtagsAndSingleUseScopedTicket() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("p","pipeline","ACTIVE","project","project")));
        org.mockito.Mockito.when(definitions.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p")))
            .thenReturn(new io.github.devmrko.companion.port.BuildDefinition.Definition("pipeline","project","ACTIVE","","revision",java.util.List.of()));
        org.mockito.Mockito.when(setup.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p")))
            .thenReturn(new io.github.devmrko.companion.port.BuildSetup.Settings("server-etag","pipeline","",java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of()));
        var page=mvc.perform(get("/builds").session(session).param("tab","pipelines").param("pipeline","p")).andExpect(status().isOk())
            .andExpect(content().string(containsString("파이프라인 저장"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("setupTicket");
        mvc.perform(post("/builds/setup").session(session).param("ticket",ticket).param("action","pipeline").param("name","new")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/setup").session(session).with(csrf()).param("ticket",ticket).param("action","pipeline").param("name","new").param("etag","forged"))
            .andExpect(redirectedUrl("/builds?tab=pipelines&pipeline=p"));
        org.mockito.Mockito.verify(setup).rename(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("server-etag"),org.mockito.ArgumentMatchers.eq("new"),org.mockito.ArgumentMatchers.eq(""));
        mvc.perform(post("/builds/setup").session(session).with(csrf()).param("ticket",ticket).param("action","pipeline").param("name","new")).andExpect(status().isBadRequest());
        page=mvc.perform(get("/builds").session(session).param("tab","pipelines").param("pipeline","p")).andReturn();
        ticket=(String)page.getModelAndView().getModel().get("setupTicket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/setup").session(session).with(csrf()).param("ticket",ticket).param("action","pipeline").param("name","new")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(setup,org.mockito.Mockito.times(2)).read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"));
        org.mockito.Mockito.verify(setup,org.mockito.Mockito.times(2)).buildOptions(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p"));
        org.mockito.Mockito.verifyNoMoreInteractions(setup);
    }
    @Test void logsRequireCurrentScopeAndExposePermissions() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var page=mvc.perform(get("/builds").session(session).param("tab","topics")).andExpect(status().isOk())
            .andExpect(content().string(containsString("권한 설정 · OCI 콘솔"))).andExpect(content().string(containsString("ONS_TOPIC_CREATE"))).andReturn();
        var selection=(io.github.devmrko.companion.web.CatalogPages.Selection)page.getModelAndView().getModel().get("selection");
        org.mockito.Mockito.when(logs.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(selection.region()),org.mockito.ArgumentMatchers.eq(selection.compartment()),org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.eq("r"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq("")))
            .thenReturn(new io.github.devmrko.companion.port.BuildLogs.Page("READY","",java.util.List.of(new io.github.devmrko.companion.port.BuildLogs.Line("time","stage","actual log")),"",""));
        mvc.perform(get("/builds/logs").session(session).param("region",selection.region()).param("compartment",selection.compartment()).param("pipeline","p").param("run","r"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].message").value("actual log"));
        mvc.perform(get("/builds/logs").session(session).param("region","foreign").param("compartment",selection.compartment()).param("pipeline","p").param("run","r")).andExpect(status().isConflict());
    }
    @Test void topicTabSupportsEmptyEnvironmentCreationAndScopeProtection() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var page=mvc.perform(get("/builds").session(session).param("tab","topics")).andExpect(status().isOk())
            .andExpect(content().string(containsString("현재 리전·구획에 알림 주제가 없습니다.")))
            .andExpect(content().string(containsString("알림 주제와 로그"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("adminTicket");
        org.mockito.Mockito.when(administration.createTopic(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("build-topic"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq(ticket))).thenReturn("topic-created");
        mvc.perform(post("/builds/create").session(session).param("ticket",ticket).param("name","build-topic")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","build-topic"))
            .andExpect(redirectedUrl("/builds?tab=topics&topic=topic-created"));
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","build-topic")).andExpect(status().isBadRequest());
        page=mvc.perform(get("/builds").session(session).param("tab","topics")).andReturn();
        ticket=(String)page.getModelAndView().getModel().get("adminTicket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","build-topic")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(administration,org.mockito.Mockito.times(1)).createTopic(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void topicReadFailureIsNotDisplayedAsEmptyList() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(administration.topicDetails(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(io.github.devmrko.companion.service.Errors.CLOUD.exception());
        mvc.perform(get("/builds").session(session).param("tab","topics")).andExpect(status().isOk())
            .andExpect(content().string(containsString("목록을 조회하지 못했습니다.")))
            .andExpect(content().string(not(containsString("현재 리전·구획에 알림 주제가 없습니다."))));
    }

    @Test void projectAndPipelineTabsUseRealAdministrationPortAndScopedSingleUseCreation() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(administration.projects(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildAdministration.Project("project-empty","empty-project","description","ACTIVE","topic1")));
        org.mockito.Mockito.when(administration.topics(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.domain.Catalog.Choice("topic1","notifications")));
        var page=mvc.perform(get("/builds").session(session).param("tab","projects")).andExpect(status().isOk())
            .andExpect(content().string(containsString("empty-project"))).andExpect(content().string(containsString("notifications"))).andReturn();
        String ticket=(String)page.getModelAndView().getModel().get("adminTicket");
        org.mockito.Mockito.when(administration.createProject(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("new-project"),org.mockito.ArgumentMatchers.eq(""),org.mockito.ArgumentMatchers.eq("topic1"),org.mockito.ArgumentMatchers.eq(ticket))).thenReturn("created-project");
        mvc.perform(post("/builds/create").session(session).param("ticket",ticket).param("name","new-project")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","new-project").param("topic","topic1"))
            .andExpect(redirectedUrl("/builds?tab=projects&project=created-project"));
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","new-project")).andExpect(status().isBadRequest());
        page=mvc.perform(get("/builds").session(session).param("tab","pipelines").param("project","project-empty")).andExpect(status().isOk())
            .andExpect(content().string(containsString("파이프라인 생성"))).andExpect(content().string(containsString("empty-project"))).andReturn();
        ticket=(String)page.getModelAndView().getModel().get("adminTicket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/create").session(session).with(csrf()).param("ticket",ticket).param("name","pipeline").param("project","project-empty")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(administration,org.mockito.Mockito.never()).createPipeline(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }

    @Test void buildExecutionUsesSingleTicketAndRejectsStaleScope() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("p1","pipeline","ACTIVE")));
        org.mockito.Mockito.when(definitions.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new io.github.devmrko.companion.port.BuildDefinition.Definition("pipeline","project","ACTIVE","","revision",java.util.List.of(new io.github.devmrko.companion.port.BuildDefinition.Stage("stage","build","빌드·테스트","ACTIVE",java.util.List.of("시작"),"image","build_spec.yaml",3600,java.util.List.of(),java.util.List.of()))));
        org.mockito.Mockito.when(control.settings(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p1")))
            .thenReturn(new io.github.devmrko.companion.port.BuildControl.Settings("etag",java.util.List.of()));
        org.mockito.Mockito.when(control.start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("p1"),org.mockito.ArgumentMatchers.eq("etag"),org.mockito.ArgumentMatchers.eq("test"),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn("run1");
        var result=mvc.perform(get("/builds").session(session).param("tab","execute")).andExpect(status().isOk()).andExpect(content().string(containsString("프리셋 저장"))).andReturn();
        String ticket=(String)result.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/builds/action").session(session).param("ticket",ticket).param("action","run")).andExpect(status().isForbidden());
        mvc.perform(post("/builds/action").session(session).with(csrf()).param("ticket",ticket).param("action","run").param("runName","test").param("purpose","test")).andExpect(redirectedUrl("/builds?pipeline=p1&run=run1"));
        mvc.perform(post("/builds/action").session(session).with(csrf()).param("ticket",ticket).param("action","run")).andExpect(status().isConflict()).andExpect(view().name("operation-error")).andExpect(content().string(containsString("권한 설정 안내"))).andExpect(content().string(containsString("빌드 이력"))).andExpect(content().string(containsString("global-scope")));
        org.mockito.Mockito.verify(control,org.mockito.Mockito.times(1)).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
        result=mvc.perform(get("/builds").session(session).param("tab","execute")).andReturn();
        ticket=(String)result.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development"));
        mvc.perform(post("/builds/action").session(session).with(csrf()).param("ticket",ticket).param("action","run")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(control,org.mockito.Mockito.times(1)).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    @org.junit.jupiter.api.BeforeEach void clearSelections() throws Exception {
        var settings=DIRECTORY.resolve("settings");
        if(Files.exists(settings)) try(var files=Files.list(settings)) { for(var file:files.toList()) Files.delete(file); }
    }
    @Test void globalSelectionRestoresAfterReconnectAndAppearsOnGuides() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/guides")).andExpect(redirectedUrl("/guides"));
        mvc.perform(get("/guides").session(session)).andExpect(content().string(containsString("global-scope")));
        mvc.perform(post("/disconnect").session(session).with(csrf()));
        session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        var model=mvc.perform(get("/sources").session(session)).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        var selection=(io.github.devmrko.companion.web.CatalogPages.Selection)model.get("navSelection");
        assertEquals("us-ashburn-1",selection.region());assertEquals("demo-development",selection.compartment());
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","foreign").param("compartment","demo-development")).andExpect(status().isBadRequest());
        var cloud=context.getBean(io.github.devmrko.companion.port.CloudConnection.class);
        context.getBean(io.github.devmrko.companion.port.SelectionStore.class).write(cloud.connect("DEMO"),"unsubscribed","missing");
        mvc.perform(post("/disconnect").session(session).with(csrf()));
        session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/builds/tasks").session(session)).andExpect(content().string(containsString("저장한 리전·구획에 접근할 수 없습니다")));
        org.mockito.Mockito.verifyNoInteractions(builds);
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    io.github.devmrko.companion.port.BuildHistory builds;
    @Test void nativeSettingsLoadOriginalAndRejectConcurrentGitChanges() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/sources"));
        mvc.perform(post("/sources").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("provider","GITEA").param("name","native-edit-test").param("url","https://git.example.test/team/native.git").param("branch","main"));
        var connection=context.getBean(io.github.devmrko.companion.port.CloudConnection.class).connect("DEMO");
        var source=context.getBean(io.github.devmrko.companion.service.CatalogService.class).sources(connection,"us-ashburn-1","demo-development").stream().filter(v->v.name().equals("native-edit-test")).findFirst().orElseThrow();
        String yaml="version: 0.1\ncomponent: build\nsteps:\n- type: Command\n  command: mvn test\n";
        org.mockito.Mockito.when(gitSpecifications.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(new io.github.devmrko.companion.port.GitSpecifications.File("a".repeat(40),yaml));
        mvc.perform(get("/builds/settings").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("명세 불러오기")));
        var response=mvc.perform(post("/builds/settings/load").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("source",source.id()).param("branch","main").param("path","build_spec.yaml").param("username","user").param("secret","env:TEST")).andExpect(status().isOk()).andExpect(jsonPath("$.fields.commands[0].value").value("mvn test")).andReturn().getResponse().getContentAsString();
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var loaded=mapper.readTree(response);
        String body=mapper.writeValueAsString(java.util.Map.of("token",loaded.path("token").asString(),"fields",io.github.devmrko.companion.service.BuildSpecDocument.read(yaml),"username","user","secret","env:TEST","message","Edit native build"));
        String localBody=body.replace("mvn test","mvn verify");
        mvc.perform(post("/builds/settings/local/save").session(session).contentType("application/json").content(localBody)).andExpect(status().isForbidden());
        org.mockito.Mockito.clearInvocations(gitSpecifications);
        mvc.perform(post("/builds/settings/local/save").session(session).with(csrf()).contentType("application/json").content(localBody)).andExpect(status().isOk());
        mvc.perform(post("/builds/settings/local/load").session(session).with(csrf()).param("token",loaded.path("token").asString())).andExpect(status().isOk()).andExpect(jsonPath("$.fields.commands[0].value").value("mvn verify"));
        mvc.perform(post("/builds/settings/export").session(session).with(csrf()).contentType("application/json").content(localBody)).andExpect(status().isOk()).andExpect(jsonPath("$.content").value(containsString("mvn verify")));
        org.mockito.Mockito.verifyNoInteractions(gitSpecifications);
        org.mockito.Mockito.when(gitSpecifications.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(new io.github.devmrko.companion.port.GitSpecifications.File("b".repeat(40),yaml));
        String newer=mvc.perform(post("/builds/settings/load").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("source",source.id()).param("branch","main").param("path","build_spec.yaml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/builds/settings/local/load").session(session).with(csrf()).param("token",mapper.readTree(newer).path("token").asString())).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("원본 커밋이 변경")));
        mvc.perform(post("/builds/settings/preview").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        org.mockito.Mockito.when(gitSpecifications.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(new io.github.devmrko.companion.port.GitSpecifications.File("a".repeat(40),yaml));
        String preview=mvc.perform(post("/builds/settings/preview").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket=mapper.readTree(preview).path("ticket").asString();org.mockito.Mockito.when(gitSpecifications.save(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("a".repeat(40));
        mvc.perform(post("/builds/settings/save").session(session).param("ticket",ticket)).andExpect(status().isForbidden());
        mvc.perform(post("/builds/settings/save").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isOk());
        mvc.perform(post("/builds/settings/save").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        mvc.perform(post("/builds/settings/load").session(session).with(csrf()).param("region","other").param("compartment","demo-development")).andExpect(status().isConflict());
    }
    @Test void gitSaveRequiresPreviewAndCannotBeReplayed() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/sources"));
        mvc.perform(post("/sources").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("provider","GITEA").param("name","spec-save-test").param("url","https://git.example.test/team/spec.git").param("branch","main"));
        var connection=context.getBean(io.github.devmrko.companion.port.CloudConnection.class).connect("DEMO");
        var source=context.getBean(io.github.devmrko.companion.service.CatalogService.class).sources(connection,"us-ashburn-1","demo-development").stream().filter(v->v.name().equals("spec-save-test")).findFirst().orElseThrow();
        org.mockito.Mockito.when(gitSpecifications.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(new io.github.devmrko.companion.port.GitSpecifications.File("a".repeat(40),"old"));
        org.mockito.Mockito.when(gitSpecifications.save(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("b".repeat(40));
        mvc.perform(post("/builds/specification/git/save").session(session).param("ticket","invalid")).andExpect(status().isForbidden());
        var response=mvc.perform(post("/builds/specification/git/preview").session(session).with(csrf()).param("source",source.id()).param("branch","main").param("path","build_spec.yaml").param("username","user").param("secret","env:TEST").param("content","version: '0.1'").param("message","Save specification")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).path("ticket").asString();
        mvc.perform(post("/builds/specification/git/save").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isOk());
        mvc.perform(post("/builds/specification/git/save").session(session).with(csrf()).param("ticket",ticket)).andExpect(status().isConflict());
        org.mockito.Mockito.verify(gitSpecifications,org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void buildPageUsesSelectedRegionAndRejectsUnlistedRuns() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/builds")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back","/builds")).andExpect(redirectedUrl("/builds"));
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development")))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("p1","real-pipeline","ACTIVE")));
        org.mockito.Mockito.when(builds.runs(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development"),org.mockito.ArgumentMatchers.eq("p1")))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Run("r1","run-one","SUCCEEDED","2026-10-26T00:00:00Z")));
        org.mockito.Mockito.when(builds.detail(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("us-ashburn-1"),org.mockito.ArgumentMatchers.eq("demo-development"),org.mockito.ArgumentMatchers.eq("p1"),org.mockito.ArgumentMatchers.eq("r1")))
            .thenReturn(new io.github.devmrko.companion.port.BuildHistory.Detail("r1","run-one","SUCCEEDED","",java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Step("SAVE_OUTPUT_ARTIFACTS","SUCCEEDED"))));
        mvc.perform(get("/builds").session(session).param("tab","history")).andExpect(status().isOk()).andExpect(content().string(containsString("SAVE_OUTPUT_ARTIFACTS")));
        mvc.perform(get("/builds").session(session).param("pipeline","p1").param("run","foreign-run")).andExpect(status().isOk()).andExpect(content().string(containsString("현재 연결에서 찾을 수 없는 항목")));
        org.mockito.Mockito.verify(builds,org.mockito.Mockito.never()).detail(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("foreign-run"));
    }
    @Test void resourceWorkflowNeedsNoDatabaseAndIsolatesScopes() throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/connect").param("profile","DEMO")).andExpect(status().isForbidden());
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("DevOps 리소스")));
        mvc.perform(post("/catalog/refresh").session(session).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(status().isForbidden());
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("companion-api")));
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk());
        mvc.perform(post("/sources").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("provider","GITHUB").param("name","test-source").param("url","https://github.com/a/b").param("branch","main")).andExpect(redirectedUrl("/sources"));
        mvc.perform(get("/sources").session(session)).andExpect(content().string(containsString("test-source")));
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("interval","300")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("test-source")));
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy")).andExpect(status().isConflict());
        mvc.perform(get("/guides").session(session)).andExpect(status().isOk());
        mvc.perform(post("/disconnect").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }
    @Test void expandedScopeAndRepositoryFiltersPreserveSourcePage() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/sources").session(session)).andExpect(status().isOk());
        mvc.perform(post("/scope").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("interval","60").param("back","/sources")).andExpect(redirectedUrl("/sources"));
        mvc.perform(post("/catalog/refresh").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("back","/sources")).andExpect(redirectedUrl("/sources"));
        var result=mvc.perform(get("/sources").session(session).param("q","companion-api").param("project","demo-project").param("state","ACTIVE")).andExpect(status().isOk()).andReturn();
        var model=result.getModelAndView().getModel();
        assertEquals(2,((java.util.List<?>)model.get("rows")).size());
        assertEquals(2,((java.util.List<?>)model.get("scopeResults")).size());
        assertEquals(0,((io.github.devmrko.companion.web.CatalogPages.Selection)model.get("selection")).interval());
        mvc.perform(post("/catalog/refresh-one").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("target","foreign")).andExpect(status().isBadRequest());
        mvc.perform(post("/catalog/refresh-one").session(session).with(csrf()).param("region","ap-seoul-1").param("compartment","demo-tenancy").param("mode","subtree").param("target","demo-development")).andExpect(status().isOk()).andExpect(jsonPath("$.compartment.id").value("demo-development")).andExpect(jsonPath("$.error").isEmpty());

        result=mvc.perform(get("/sources").session(session).param("q","nonexistent")).andExpect(status().isOk()).andReturn();
        assertTrue(((java.util.List<?>)result.getModelAndView().getModel().get("rows")).isEmpty());
    }

    @Test void namedParametersUseSavedSelectRulesAndRejectMissingRequiredValues() throws Exception {
        var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
        org.mockito.Mockito.when(builds.pipelines(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.List.of(new io.github.devmrko.companion.port.BuildHistory.Pipeline("rules-p","rules","ACTIVE")));
        org.mockito.Mockito.when(control.settings(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("rules-p")))
            .thenReturn(new io.github.devmrko.companion.port.BuildControl.Settings("e",java.util.List.of(new io.github.devmrko.companion.port.BuildControl.Parameter("MODE","verify","mode"))));
        org.mockito.Mockito.when(definitions.read(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new io.github.devmrko.companion.port.BuildDefinition.Definition("rules","project","ACTIVE","","rev",java.util.List.of()));
        var model=mvc.perform(get("/builds").session(session).param("tab","execute")).andReturn().getModelAndView().getModel();
        String ticket=(String)model.get("ticket");
        mvc.perform(post("/builds/action").session(session).with(csrf()).param("ticket",ticket).param("action","rules")
            .param("ruleName","MODE").param("ruleType","select").param("ruleRequirement","required").param("ruleChoices","verify\npackage"))
            .andExpect(flash().attribute("message","입력 규칙 저장 완료"));
        var result=mvc.perform(get("/builds").session(session).param("tab","execute")).andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"package\""))).andReturn();
        var fields=(java.util.List<io.github.devmrko.companion.service.BuildInputs.Field>)result.getModelAndView().getModel().get("fields");
        assertTrue(fields.getFirst().required());assertEquals("select",fields.getFirst().type());
        ticket=(String)result.getModelAndView().getModel().get("ticket");
        mvc.perform(post("/builds/action").session(session).with(csrf()).param("ticket",ticket).param("action","run")
            .param("parameterName","MODE").param("parameterValue","").param("parameterDescription","mode"))
            .andExpect(flash().attribute("message","필수 파라미터 값을 입력하세요."));
        org.mockito.Mockito.verify(control,org.mockito.Mockito.never()).start(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }
 @Test void staleDeploymentFormsReturnToMatchingScreenWithoutWrites()throws Exception{
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  for(var entry:java.util.Map.of("/environments","/environments","/environments/oke","/environments/oke","/deployments/configure/create","/deployments/configure","/deployments/oke/configure/create","/deployments/oke/configure").entrySet()){
   mvc.perform(post(entry.getKey()).session(session).with(csrf()).param("ticket","expired").param("project","p").param("name","n"))
    .andExpect(status().isConflict()).andExpect(view().name("operation-error")).andExpect(model().attribute("operationBack",entry.getValue())).andExpect(model().attribute("scopeBack",entry.getValue())).andExpect(content().string(containsString("자동으로 다시 실행하지 않습니다"))).andExpect(content().string(containsString("확인 정보가 만료되었거나 설정이 변경되었습니다")));
  }
  org.mockito.Mockito.verifyNoInteractions(deploymentSetup,okeDeploymentSetup,deploymentEnvironments,okePreparation);
 }
 @Test void buildErrorsKeepTheirPageAndDoNotInvokeCloudWrites()throws Exception{
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  var requests=java.util.List.of(post("/builds/automation/state").param("ticket","expired").param("id","unused").param("etag","unused").param("active","false"),post("/builds/project-resources").param("ticket","expired").param("action","repository").param("name","unused"),post("/builds/functions/run").param("ticket","expired").param("commit","unused").param("goal","verify"),post("/builds/cancel").param("region","unused").param("compartment","unused").param("pipeline","unused").param("run","unused").param("confirmed","false"));
  var destinations=java.util.List.of("/builds/automation","/builds/project-resources","/builds/functions","/builds?tab=history");
  for(int n=0;n<requests.size();n++)mvc.perform(requests.get(n).session(session).with(csrf())).andExpect(status().isConflict()).andExpect(view().name("operation-error")).andExpect(model().attribute("operationBack",destinations.get(n)));
  mvc.perform(get("/builds/tasks").session(session).param("view","invalid")).andExpect(status().isBadRequest()).andExpect(view().name("operation-error")).andExpect(model().attribute("operationBack","/builds/tasks"));
  org.mockito.Mockito.verifyNoInteractions(automation,projectResources,functions,control);
 }
 @Test void buildDataEndpointsReturnJsonErrorsRatherThanHtml()throws Exception{
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  mvc.perform(get("/builds/automation/preview").session(session).param("cron","invalid")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.message").isNotEmpty());
  mvc.perform(get("/builds/tasks/environments").session(session).param("region","wrong").param("compartment","wrong")).andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("SCOPE"));
 }
 @Test void scopeChangePreservesPreviouslyMissingScreens()throws Exception{
  var session=(MockHttpSession)mvc.perform(post("/connect").with(csrf()).param("profile","DEMO")).andReturn().getRequest().getSession(false);
  for(String path:java.util.List.of("/builds/settings","/builds/configure","/agents/connections")){
   mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(model().attribute("scopeBack",path)).andExpect(content().string(containsString("name=\"back\" value=\""+path+"\"")));
   mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back",path)).andExpect(redirectedUrl(path));
  }
  for(String path:java.util.List.of("https://example.com","//example.com","/builds/settings?secret=untrusted","/agents/connections/../other"))mvc.perform(post("/scope").session(session).with(csrf()).param("region","us-ashburn-1").param("compartment","demo-development").param("back",path)).andExpect(redirectedUrl("/"));
 }
}
