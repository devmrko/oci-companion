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
    @Test void runtimeInstallationIsScopedToCompatibleToolAndShellIsValid() throws Exception {
        for(var choice:List.of(List.of("maven","jdk21"),List.of("gradle","jdk17"),List.of("python","python311"),List.of("node","node22"))) {
            String yaml=BuildSpecTemplates.runtime(BuildSpecTemplates.render(choice.get(0),"test","",".","",""),choice.get(0),choice.get(1));
            Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);var step=(Map<?,?>)((List<?>)spec.get("steps")).getFirst();assertTrue(step.get("command").toString().contains(choice.get(0).equals("maven")||choice.get(0).equals("gradle")?"sha256sum -c":"--version"));
            var process=new ProcessBuilder("bash","-n").start();try(var out=process.getOutputStream()){out.write(step.get("command").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertEquals(0,process.waitFor());
        }
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.runtime(BuildSpecTemplates.render("node","test","",".","",""),"node","jdk21"));
    }
    @Test void reportCollectionPreservesFailedBuildExitCode() throws Exception {
        var root=java.nio.file.Files.createTempDirectory("report-command-");var reports=java.nio.file.Files.createDirectories(root.resolve("target/surefire-reports"));
        java.nio.file.Files.writeString(reports.resolve("TEST.xml"),"<testsuite><testcase name='failure'><failure>expected</failure></testcase></testsuite>");
        var wrapper=root.resolve("mvnw");java.nio.file.Files.writeString(wrapper,"#!/bin/sh\nexit 7\n");wrapper.toFile().setExecutable(true);
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(BuildSpecTemplates.reports(BuildSpecTemplates.render("maven","verify","",".","",""),true));
        String command=((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString();
        var process=new ProcessBuilder("bash","-c",command).directory(root.toFile()).redirectErrorStream(true).start();process.getInputStream().readAllBytes();assertEquals(7,process.waitFor());
        try(var input=java.nio.file.Files.newInputStream(root.resolve("test-reports.tar.gz"))){assertEquals(1,io.github.devmrko.companion.service.TestReports.read(input).failures());}
    }
    @Test void invalidOutputPairAndUnsupportedToolAreRejected() {
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("maven","verify","",".","app",""));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("shell","echo hi","",".","",""));
        for(String tool:List.of("maven","gradle","python","node"))assertNotNull(new org.yaml.snakeyaml.Yaml().load(BuildSpecTemplates.render(tool,"test","",".","","")));
    }
}
