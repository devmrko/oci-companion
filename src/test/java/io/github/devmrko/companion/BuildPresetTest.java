package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.JsonBuildPresetStore;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildPresetTest {
    @TempDir Path root;
    @Test void scopesAndAtomicPersistence() throws Exception {
        var c=new Connection("DEFAULT","tenant","user","ap-seoul-1","test",false);
        var store=new JsonBuildPresetStore(root.toString());
        var value=new BuildPresetStore.Preset("../../preset",List.of(new BuildControl.Parameter("VERSION","1","release")));
        store.save(c,"ap-seoul-1","comp","p1",value);
        assertEquals(value,new JsonBuildPresetStore(root.toString()).list(c,"ap-seoul-1","comp","p1").getFirst());
        assertTrue(store.list(c,"ap-seoul-1","comp","p2").isEmpty());
        assertTrue(store.list(c,"us-ashburn-1","comp","p1").isEmpty());
        assertTrue(store.list(new Connection("OTHER","tenant","user","ap-seoul-1","test",false),"ap-seoul-1","comp","p1").isEmpty());
        store.delete(c,"ap-seoul-1","comp","p1",value.name());
        assertTrue(store.list(c,"ap-seoul-1","comp","p1").isEmpty());
        try(var files=Files.list(root.resolve("build-presets"))) {
            var file=files.findFirst().orElseThrow(); Files.writeString(file,"broken");
        }
        assertThrows(IllegalStateException.class,()->store.save(c,"ap-seoul-1","comp","p1",value));
    }
}
