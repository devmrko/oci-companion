package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.JsonBuildSpecDraftStore;
import io.github.devmrko.companion.port.BuildSpecDraftStore.Draft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import static org.junit.jupiter.api.Assertions.*;
class BuildSpecDraftTest {
    @TempDir Path root;
    @Test void persistenceIsolationPermissionsAndDeletion() throws Exception {
        var store=new JsonBuildSpecDraftStore(root.toString());var draft=new Draft("revision","component: build","2026-11-03");
        store.save("DEFAULT\nregion\ncompartment\npipeline\n../../source",draft);
        assertEquals(draft,new JsonBuildSpecDraftStore(root.toString()).read("DEFAULT\nregion\ncompartment\npipeline\n../../source").orElseThrow());
        assertTrue(store.read("OTHER\nregion\ncompartment\npipeline\n../../source").isEmpty());
        try(var files=Files.list(root.resolve("build-spec-drafts"))){assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(files.findFirst().orElseThrow()));}
        store.delete("DEFAULT\nregion\ncompartment\npipeline\n../../source");assertTrue(store.read("DEFAULT\nregion\ncompartment\npipeline\n../../source").isEmpty());
    }
}
