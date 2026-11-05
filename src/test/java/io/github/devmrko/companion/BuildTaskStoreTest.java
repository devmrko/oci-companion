package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.JsonBuildTaskStore;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildTaskStore.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildTaskStoreTest {
    @TempDir Path root;
    @Test void taskClassificationPersistsAndOlderRecordsStayUnclassified() throws Exception {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var old=mapper.readValue("{\"id\":\"old\",\"runner\":\"FUNCTIONS\"}",Task.class);
        assertEquals("FILE",mapper.readValue("{\"id\":\"old-vm\",\"target\":\"VM\"}",Task.class).target());
        assertEquals("UNSPECIFIED",old.target());assertEquals("UNKNOWN",old.architecture());
        var c=new Connection("DEFAULT","tenant","user","region","test",false);
        var store=new JsonBuildTaskStore(root.toString());
        var task=new Task("id","container","DEVOPS","","","pipeline","source","a".repeat(40),"","","","","source-id","main","CONTAINER","X86_64");
        store.save(c,"region","comp",task);
        assertEquals(task,new JsonBuildTaskStore(root.toString()).list(c,"region","comp").getFirst());
    }
    @Test void tasksPersistAndRemainIsolatedByIdentityAndScope() throws Exception {
        var c=new Connection("DEFAULT","tenant","user","region","test",false);
        var store=new JsonBuildTaskStore(root.toString());
        var t=new Task("id","검증","FUNCTIONS","app","fn","","https://github.com/a/b","a".repeat(40),"verify","quality",".","target/*.jar");
        store.save(c,"region","comp",t);
        assertEquals(t,new JsonBuildTaskStore(root.toString()).list(c,"region","comp").getFirst());
        assertTrue(store.list(c,"other","comp").isEmpty());assertTrue(store.list(c,"region","other").isEmpty());
        assertTrue(store.list(new Connection("DEFAULT","tenant","other-user","region","test",false),"region","comp").isEmpty());
        store.save(c,"region","comp",t);assertEquals(1,store.list(c,"region","comp").size());
        store.delete(c,"region","comp","id");assertTrue(store.list(c,"region","comp").isEmpty());
        try(var files=Files.list(root.resolve("build-tasks"))){Files.writeString(files.findFirst().orElseThrow(),"broken");}
        assertThrows(IllegalStateException.class,()->store.save(c,"region","comp",t));
    }
}
