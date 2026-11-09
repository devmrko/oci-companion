package io.github.devmrko.companion;

import io.github.devmrko.companion.adapter.EnvironmentProfiles;
import io.github.devmrko.companion.adapter.EnvironmentProfiles.Document;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentProfilesTest {
    @TempDir Path temp;
    final Connection alice=new Connection("ALICE","tenancy","alice","ap-seoul-1","Alice",false);
    final Connection bob=new Connection("ALICE","tenancy","bob","ap-seoul-1","Bob",false);
    EnvironmentProfiles store()throws Exception{return new EnvironmentProfiles(temp.toRealPath().resolve("profiles").toString());}
    Document draft(String name){return new Document(1,1,name,"demo-token",Map.of("environment","dev","credentialRef","credential:demo"));}
    byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    Path file(String id)throws Exception{try(var paths=Files.walk(temp)){return paths.filter(p->p.getFileName().toString().equals(id+".json")).findFirst().orElseThrow();}}
    @Test void profilesAreIndependentAndEditsRequireCurrentRevision()throws Exception{
        var store=store();var a=store.save(alice,"",0,draft("A"));var b=store.save(alice,"",0,draft("B"));
        store.save(alice,a.id(),1,draft("A revised"));assertEquals("B",store.get(alice,b.id()).document().name());
        assertThrows(IllegalArgumentException.class,()->store.save(alice,a.id(),1,draft("stale")));
        assertEquals(2,store.get(alice,a.id()).revision());assertEquals(2,store.list(alice).size());
        assertThrows(IllegalArgumentException.class,()->store.save(alice,a.id(),2,new Document(1,1,"X","workforce-oidc",Map.of())));
    }
    @Test void ownershipAndRegionAreExplicit()throws Exception{
        var store=store();var a=store.save(alice,"",0,draft("A"));assertTrue(store.list(bob).isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.get(bob,a.id()));
        assertThrows(IllegalArgumentException.class,()->store.export(bob,a.id()));
        assertThrows(IllegalArgumentException.class,()->store.get(alice,"../outside"));
        var changedRegion=new Connection("ALICE","tenancy","alice","us-ashburn-1","Alice",false);
        assertEquals(a,store.get(changedRegion,a.id()));
        var changedProfile=new Connection("OTHER","tenancy","alice","ap-seoul-1","Alice",false);
        assertTrue(store.list(changedProfile).isEmpty());
        var changedTenancy=new Connection("ALICE","other","alice","ap-seoul-1","Alice",false);
        assertThrows(IllegalArgumentException.class,()->store.get(changedTenancy,a.id()));
    }
    @Test void draftAndConditionalRequirementsDoNotImplyDeploymentReady()throws Exception{
        var store=store();var d=new Document(1,1,"Draft","demo-token",Map.of("runtime","oke","telemetryEndpoint","https://traces.example.test"));
        var a=store.assess(d);assertTrue(a.missing().contains("Kubernetes namespace"));assertTrue(a.missing().contains("관측 인증 참조"));assertFalse(a.complete());
        assertNotNull(store.save(alice,"",0,d));
        var values=new LinkedHashMap<>(Map.of("environment","prod","credentialRef","plain-token"));
        assertFalse(store.assess(new Document(1,1,"Bad","demo-token",values)).errors().isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.save(alice,"",0,new Document(1,1,"Bad","demo-token",values)));
        assertThrows(IllegalArgumentException.class,()->store.save(alice,"",0,new Document(1,1,"Bad","demo-token",Map.of("password","never-store"))));
    }
    @Test void exportImportRoundTripCreatesNewOwnerAndNeverOverwrites()throws Exception{
        var store=store();var a=store.save(alice,"",0,draft("A"));String exported=store.export(alice,a.id());
        assertFalse(exported.contains(a.owner()));assertFalse(exported.contains(a.id()));assertFalse(exported.contains("revision"));
        var parsed=store.previewImport(bytes(exported));assertEquals(a.document(),parsed);assertTrue(store.list(bob).isEmpty());
        var imported=store.save(bob,"",0,parsed);assertNotEquals(a.id(),imported.id());assertEquals(1,imported.revision());
        assertEquals(a,store.get(alice,a.id()));
        var second=store.save(alice,"",0,parsed);assertNotEquals(a.id(),second.id());assertEquals(2,store.list(alice).size());
    }
    @Test void importRejectsAmbiguousUnsafeAndUnsupportedDocuments()throws Exception{
        var store=store();String valid="{\"schemaVersion\":1,\"templateVersion\":1,\"name\":\"Demo\",\"template\":\"demo-token\",\"values\":{}}";
        for(String bad:List.of(valid.replace("\"schemaVersion\":1","\"schemaVersion\":2"),
            valid.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\""),
            valid.replace("\"name\":\"Demo\"","\"name\":\"A\",\"name\":\"B\""),
            valid.replace("\"values\":{}","\"values\":{\"password\":\"secret-fixture\"}"),
            valid.replace("\"values\":{}","\"values\":{\"environment\":12}"),
            valid.replace("\"values\":{}","\"values\":{\"credentialRef\":\"/tmp/token\"}"),
            valid.replace("\"values\":{}","\"values\":{\"telemetryEndpoint\":\"https://user:pass@example.test\"}"),
            valid.replace("\"values\":{}","\"values\":{\"telemetryEndpoint\":\"https://example.test/?token=secret-fixture\"}"),
            valid.replace("\"values\":{}","\"values\":{\"environment\":null}"),valid+" {}","[]","{",valid.replace("\"values\":{}","\"owner\":\"alice\",\"values\":{}"))){
            var error=assertThrows(IllegalArgumentException.class,()->store.previewImport(bytes(bad)),bad);assertFalse(error.getMessage().contains("secret-fixture"));
        }
        assertThrows(IllegalArgumentException.class,()->store.previewImport(new byte[262145]));assertTrue(store.list(alice).isEmpty());
    }
    @Test void filesArePrivateAndCorruptionOrSymlinksFailClosed()throws Exception{
        var store=store();var a=store.save(alice,"",0,draft("A"));var path=file(a.id());
        assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(path));
        assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(path.getParent()));
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-r--r--"));assertThrows(IllegalArgumentException.class,()->store.get(alice,a.id()));
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));Files.writeString(path,"broken");assertThrows(IllegalArgumentException.class,()->store.list(alice));
        Files.delete(path);Files.createSymbolicLink(path,temp.toRealPath().resolve("elsewhere"));assertThrows(IllegalArgumentException.class,()->store.get(alice,a.id()));
        var alias=temp.toRealPath().resolve("alias");Files.createSymbolicLink(alias,temp.toRealPath().resolve("profiles"));
        assertThrows(IllegalArgumentException.class,()->new EnvironmentProfiles(alias.toString()).save(alice,"",0,draft("B")));
    }
}
