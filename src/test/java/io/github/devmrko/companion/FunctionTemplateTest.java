package io.github.devmrko.companion;

import io.github.devmrko.companion.service.FunctionTemplate;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionTemplateTest {
    @Test void archiveIsStandaloneAndContainsNoFixedProjectOrCredentials() throws Exception {
        var files=FunctionTemplate.files("DEFAULT","ap-seoul-1","ocid1.compartment.oc1..test",
            new FunctionTemplate.Settings("maven-builder","build-app","ARM","testnamespace","build-results","","builders"));
        assertTrue(files.get("func.yaml").contains("maven-v1"));
        assertTrue(files.get("func.py").contains("if_none_match=\"*\""));
        assertFalse(files.get("func.py").contains("devmrko/db-companion"));
        assertFalse(files.get("func.py").contains("127.0.0.1"));
        assertTrue(files.get("README.md").contains("--profile 'DEFAULT'"));
        var names=new HashSet<String>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(FunctionTemplate.zip(files)))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){names.add(entry.getName());assertTrue(zip.readAllBytes().length>0);}
        }
        assertEquals(Set.of("Dockerfile","func.py","func.yaml","config.json","application.json","request.json","README.md"),names);
    }
    @Test void executionRejectsCredentialsUnpinnedSourcesAndEscapingPaths() {
        String id=UUID.randomUUID().toString(),sha="a".repeat(40);
        assertTrue(FunctionTemplate.request(id,"https://github.com/owner/repo.git",sha,"verify","quality",".","target/*.jar").contains(sha));
        for(var url:List.of("http://github.com/a/b","https://token@github.com/a/b","https://127.0.0.1/a/b","https://github.com/a/b?token=secret","https://github.com:8443/a/b"))
            assertThrows(RuntimeException.class,()->FunctionTemplate.request(id,url,sha,"verify","",".",""));
        assertThrows(RuntimeException.class,()->FunctionTemplate.request(id,"https://github.com/a/b","main","verify","",".",""));
        assertThrows(RuntimeException.class,()->FunctionTemplate.request(id,"https://github.com/a/b",sha,"deploy","",".",""));
        for(var path:List.of("../outside","target/../../etc","/tmp/output","target\\outside"))
            assertThrows(RuntimeException.class,()->FunctionTemplate.request(id,"https://github.com/a/b",sha,"test","",path,""));
    }
}
