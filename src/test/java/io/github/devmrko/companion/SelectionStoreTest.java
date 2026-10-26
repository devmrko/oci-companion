package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.JsonSelectionStore;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SelectionStoreTest {
    @TempDir Path directory;
    @Test void selectionIsDurablePrivateAndIdentityScoped() throws Exception {
        var store=new JsonSelectionStore(directory.toString());
        var c=new Connection("DEFAULT","tenant","user","region","label",false);
        store.write(c,"seoul","team");
        assertEquals("seoul",new JsonSelectionStore(directory.toString()).read(c).orElseThrow().region());
        assertTrue(store.read(new Connection("OTHER","tenant","user","region","label",false)).isEmpty());
        assertTrue(store.read(new Connection("DEFAULT","another","user","region","label",false)).isEmpty());
        assertTrue(store.read(new Connection("DEFAULT","tenant","another","region","label",false)).isEmpty());
        Path file;try(var files=Files.list(directory.resolve("settings"))) { file=files.findFirst().orElseThrow(); }
        assertEquals("rw-------",PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        Files.writeString(file,"{broken");
        assertThrows(IllegalStateException.class,()->store.read(c));assertEquals("{broken",Files.readString(file));
    }
}
