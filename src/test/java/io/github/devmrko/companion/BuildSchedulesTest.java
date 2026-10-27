package io.github.devmrko.companion;
import io.github.devmrko.companion.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuildSchedulesTest {
    @Test void utcPreviewIsExplicitAndRejectsAmbiguousCron() {
        var times=BuildSchedules.next("0 0 * * 1-5",Instant.parse("2026-10-23T00:01:00Z"));
        assertEquals("2026-10-26T00:00:00Z · 서울 2026-10-26T09:00",times.getFirst());
        for(String cron:List.of("0 0 0 * * *","0 24 * * *","0 0 1 * 1","@daily","0 0 * * MON","0 0 * * *;echo hi"))assertThrows(RuntimeException.class,()->BuildSchedules.cron(cron));
    }
    @Test void generatedSpecificationSeparatesSecretsAndKeepsArgumentsLiteral() {
        var yaml=BuildSpecTemplates.configured("maven","verify","quality",".","app","target/app.jar","system","MODE=ci\nNOTE=$(whoami)","TOKEN=ocid1.vaultsecret.oc1.test","BINARY");
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);var env=(Map<?,?>)spec.get("env");assertEquals("$(whoami)",((Map<?,?>)env.get("variables")).get("NOTE"));
        assertEquals("ocid1.vaultsecret.oc1.test",((Map<?,?>)env.get("vaultVariables")).get("TOKEN"));assertTrue(((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString().contains("mvn -B 'verify' '-Pquality'"));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.configured("maven","test","",".","","","system","TOKEN=x","TOKEN=ocid1.vaultsecret.oc1.test","BINARY"));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.configured("maven","test","",".","","","system","","TOKEN=raw-secret","BINARY"));
    }
    @Test void dispatcherTemplateIsIndependentAndUsesResourcePrincipal() {
        var files=BuildDispatchTemplate.files("pipeline","ap-seoul-1","compartment");var script=files.get("func.py");
        assertTrue(script.contains("get_resource_principals_signer"));assertTrue(script.contains("opc_retry_token=token"));assertFalse(script.contains("localhost"));assertFalse(script.contains(".oci/config"));
        Map<?,?> settings=new org.yaml.snakeyaml.Yaml().load(files.get("func.yaml"));assertEquals("pipeline",((Map<?,?>)settings.get("config")).get("BUILD_PIPELINE_ID"));
    }
}
