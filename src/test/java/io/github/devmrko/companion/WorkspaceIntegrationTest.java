package io.github.devmrko.companion;

import io.github.devmrko.companion.adapter.*;
import io.github.devmrko.companion.domain.Models.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.error.BusinessException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"demo","legacy"})
class WorkspaceIntegrationTest {
    private static final Path DB;
    static { try { DB=Files.createTempFile("companion-test-",".db"); DB.toFile().deleteOnExit(); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url",()->"jdbc:sqlite:"+DB); }
    @Autowired WorkspaceService service;
    @Autowired WorkspaceRepository workspaces;
    @Autowired RunRepository runs;
    @Autowired List<BuildRecipe> recipes;
    private final Connection actor=new Connection("DEMO","tenancy-a","user-a","region-a","test",true);
    private Workspace create() { return service.save(actor,"","Example","https://github.com/devmrko/db-companion","main","maven","demo-compartment","demo-build","demo-vm"); }

    @Test void sqliteScopesUpdatesAndPersists() throws Exception {
        Workspace w=create();
        service.save(actor,w.id(),"Edited",w.repositoryUrl(),"release",w.recipe(),w.compartmentId(),w.buildPipelineId(),w.deployPipelineId());
        assertEquals("Edited",service.get(actor,w.id()).name());
        assertEquals(w.createdAt(),service.get(actor,w.id()).createdAt());
        Connection other=new Connection("OTHER","tenancy-a","user-b","region-a","other",true);
        assertThrows(BusinessException.class,()->service.get(other,w.id()));
        assertThrows(BusinessException.class,()->service.save(other,w.id(),"Attack",w.repositoryUrl(),w.branch(),w.recipe(),w.compartmentId(),"",""));
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+DB);var query=connection.prepareStatement("SELECT name FROM workspaces WHERE id=?")) {
            query.setString(1,w.id());try(var rows=query.executeQuery()) { assertTrue(rows.next()); assertEquals("Edited",rows.getString(1)); }
        }
    }
    @Test void rejectsUrlsContainingCredentialsAndUnlistedRecipe() {
        assertThrows(BusinessException.class,()->service.save(actor,"","Example","https://user:secret@github.com/a/b","main","maven","demo","",""));
        assertThrows(BusinessException.class,()->service.save(actor,"","Example","https://github.com/a/b?token=secret","main","maven","demo","",""));
        assertThrows(BusinessException.class,()->service.save(actor,"","Example","https://github.com/a/b","main","arbitrary","demo","",""));
    }
    @Test void sameRequestOnlyRunsOnceAndCrossScopeRefreshFails() {
        Workspace w=create(); String token=UUID.randomUUID().toString();
        Run first=service.start(actor,w.id(),"BUILD",token,"demo-build");
        assertEquals(first,service.start(actor,w.id(),"BUILD",token,"demo-build"));
        assertEquals(1,service.runs(actor,w.id()).size());
        assertThrows(BusinessException.class,()->service.start(actor,w.id(),"DEPLOY",token,"demo-vm"));
        service.refresh(actor,w.id(),first.id());
        assertEquals("DEMO_SUCCEEDED",service.runs(actor,w.id()).getFirst().status());
        var other=create();
        assertThrows(BusinessException.class,()->service.refresh(actor,other.id(),first.id()));
    }
    @Test void uncertainRemoteOutcomeIsNeverAutomaticallyRetried() {
        Workspace w=create(); var calls=new java.util.concurrent.atomic.AtomicInteger();
        PipelineGateway failing=new PipelineGateway() {
            public List<Pipeline> pipelines(Connection c,String id) { return List.of(); }
            public void validate(Connection c,Workspace w,String kind) {}
            public RemoteRun start(Connection c,String kind,String id,String token) { calls.incrementAndGet(); throw new RuntimeException("secret-provider-details"); }
            public RemoteRun status(Connection c,String kind,String id) { throw new UnsupportedOperationException(); }
        };
        WorkspaceService failingService=new WorkspaceService(workspaces,runs,failing,recipes);
        String token=UUID.randomUUID().toString();
        var error=assertThrows(BusinessException.class,()->failingService.start(actor,w.id(),"BUILD",token,"demo-build"));
        assertFalse(error.getMessage().contains("secret-provider-details"));
        assertEquals("UNKNOWN",failingService.start(actor,w.id(),"BUILD",token,"demo-build").status());
        assertEquals(1,calls.get());
    }
    @Test void vmAdapterRejectsFunctionsOkeAndUnknownStages() {
        var target=new VmTarget();
        target.validateStages(List.of("COMPUTE_INSTANCE_GROUP_ROLLING_DEPLOYMENT","WAIT"));
        assertThrows(BusinessException.class,()->target.validateStages(List.of("FUNCTION_DEPLOYMENT")));
        assertThrows(BusinessException.class,()->target.validateStages(List.of("COMPUTE_INSTANCE_GROUP_ROLLING_DEPLOYMENT","OKE_DEPLOYMENT")));
        assertThrows(BusinessException.class,()->target.validateStages(List.of()));
    }
    @Test void stalePageCannotExecuteChangedPipeline() {
        Workspace w=create();
        assertThrows(BusinessException.class,()->service.start(actor,w.id(),"BUILD",UUID.randomUUID().toString(),"old-pipeline"));
        assertTrue(service.runs(actor,w.id()).isEmpty());
    }
    @Test void simultaneousSubmissionsOnlyCallProviderOnce() throws Exception {
        Workspace w=create(); var calls=new java.util.concurrent.atomic.AtomicInteger();
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        PipelineGateway gateway=new PipelineGateway() {
            public List<Pipeline> pipelines(Connection c,String id) { return List.of(); }
            public void validate(Connection c,Workspace w,String kind) {
                try { barrier.await(5,java.util.concurrent.TimeUnit.SECONDS); } catch(Exception e) { throw new RuntimeException(e); }
            }
            public RemoteRun start(Connection c,String kind,String id,String token) { calls.incrementAndGet();return new RemoteRun("remote-test","ACCEPTED"); }
            public RemoteRun status(Connection c,String kind,String id) { throw new UnsupportedOperationException(); }
        };
        var concurrent=new WorkspaceService(workspaces,runs,gateway,recipes);
        String token=UUID.randomUUID().toString();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->concurrent.start(actor,w.id(),"BUILD",token,"demo-build"));
            var b=executor.submit(()->concurrent.start(actor,w.id(),"BUILD",token,"demo-build"));
            assertEquals(token,a.get(10,java.util.concurrent.TimeUnit.SECONDS).id());
            assertEquals(token,b.get(10,java.util.concurrent.TimeUnit.SECONDS).id());
        }
        assertEquals(1,calls.get());
    }
}
