package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.*;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ResourceCatalog;
import io.github.devmrko.companion.service.CatalogService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CatalogTest {
    @TempDir Path directory;
    private final Connection c=new Connection("DEMO","tenant","user","ap-seoul-1","demo",true);
    @Test void cacheSurvivesRestartAndSeparatesIdentityRegionAndCompartment() {
        var store=new JsonCatalogStore(directory.toString());
        var service=new CatalogService(new DemoResourceCatalog(),store);
        var snapshot=service.refresh(c,"ap-seoul-1","one").snapshot();
        assertEquals(4,snapshot.resources().size());
        assertEquals(snapshot,new JsonCatalogStore(directory.toString()).read(snapshot.scope()).orElseThrow());
        assertNull(service.read(c,"us-ashburn-1","one").snapshot());
        assertNull(service.read(c,"ap-seoul-1","two").snapshot());
        assertNull(service.read(new Connection("OTHER","tenant","user",c.region(),"demo",true),"ap-seoul-1","one").snapshot());
        assertNull(service.read(new Connection("DEMO","tenant","other",c.region(),"demo",true),"ap-seoul-1","one").snapshot());
    }
    @Test void failedFetchKeepsPreviousSnapshotAndTimestamp() {
        var store=new JsonCatalogStore(directory.toString());
        var good=new CatalogService(new DemoResourceCatalog(),store).refresh(c,c.region(),"one").snapshot();
        ResourceCatalog failure=new ResourceCatalog() {
            public List<Choice> regions(Connection c) { return List.of(); }
            public List<Choice> compartments(Connection c) { return List.of(); }
            public List<Resource> resources(Connection c,String r,String p) { throw new RuntimeException("sensitive server details"); }
        };
        var result=new CatalogService(failure,store).refresh(c,c.region(),"one");
        assertEquals(good,result.snapshot()); assertNotNull(result.error()); assertFalse(result.error().contains("sensitive"));
    }
    @Test void corruptSourceFileIsNotOverwrittenAndCacheCanRecover() throws Exception {
        var store=new JsonCatalogStore(directory.toString());var service=new CatalogService(new DemoResourceCatalog(),store);
        service.refresh(c,c.region(),"one");
        service.register(c,c.region(),"one","GITHUB","test","https://github.com/a/b","main","");
        Path sources;
        try(var paths=Files.list(directory)) { sources=paths.filter(p->p.toString().endsWith("-sources.json")).findFirst().orElseThrow(); }
        Files.writeString(sources,"{broken");
        assertThrows(IllegalStateException.class,()->service.register(c,c.region(),"one","GITHUB","test","https://github.com/a/b","main",""));
        assertEquals("{broken",Files.readString(sources));
        Path cache;
        try(var paths=Files.list(directory)) { cache=paths.filter(p->p.toString().endsWith("-cache.json")).findFirst().orElseThrow(); }
        Files.writeString(cache,"{broken");
        assertNotNull(service.read(c,c.region(),"one").error());
        assertNull(service.refresh(c,c.region(),"one").error());
    }
    @Test void validatesSourcesAndPersistsIndependentOfCache() {
        var store=new JsonCatalogStore(directory.toString());var service=new CatalogService(new DemoResourceCatalog(),store);
        for(String url:List.of("https://user:secret@github.com/a/b","https://github.com/a/b?token=secret","http://github.com/a/b","https://evil.test/a/b"))
            assertThrows(RuntimeException.class,()->service.register(c,c.region(),"one","GITHUB","test",url,"main",""));
        service.refresh(c,c.region(),"one");
        assertThrows(RuntimeException.class,()->service.register(c,c.region(),"one","OCI","test","","main","foreign-id"));
        service.register(c,c.region(),"one","OCI","test","","main","demo-repository");
        service.register(c,c.region(),"one","GITHUB","test","https://github.com/a/b","main","");
        service.register(c,c.region(),"one","GITHUB","test","https://github.com/a/b","develop","");
        assertEquals(2,service.sources(c,c.region(),"one").size());
        service.refresh(c,c.region(),"one");assertEquals(2,service.sources(c,c.region(),"one").size());
        assertTrue(service.sources(c,c.region(),"two").isEmpty());
        var restarted=new CatalogService(new DemoResourceCatalog(),new JsonCatalogStore(directory.toString()));
        assertEquals(2,restarted.sources(c,c.region(),"one").size());
    }
}
