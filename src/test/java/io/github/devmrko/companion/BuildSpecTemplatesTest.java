package io.github.devmrko.companion;
import io.github.devmrko.companion.service.BuildSpecTemplates;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildSpecTemplatesTest {
    @Test void selectedTasksBecomeQuotedArgumentsAndOutputsAreOptional() {
        String draft=BuildSpecTemplates.render("maven","verify","ci",".","application","target/app.jar");
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(draft);
        assertEquals("build",spec.get("component"));assertEquals("0.1",spec.get("version"));
        var step=(Map<?,?>)((List<?>)spec.get("steps")).getFirst();
        assertEquals("set -euo pipefail\ncd -- '.'\n./mvnw -B 'verify' '-Pci'",step.get("command"));
        assertTrue(spec.containsKey("outputArtifacts"));
        spec=new org.yaml.snakeyaml.Yaml().load(BuildSpecTemplates.render("gradle","test;$(false)","",".","",""));
        assertFalse(spec.containsKey("outputArtifacts"));
        assertTrue(((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString().contains("'test;$(false)'"));
    }
    @Test void invalidOutputPairAndUnsupportedToolAreRejected() {
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("maven","verify","",".","app",""));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("shell","echo hi","",".","",""));
        for(String tool:List.of("maven","gradle","python","node"))assertNotNull(new org.yaml.snakeyaml.Yaml().load(BuildSpecTemplates.render(tool,"test","",".","","")));
    }
}
