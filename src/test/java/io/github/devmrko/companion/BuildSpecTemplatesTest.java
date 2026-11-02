package io.github.devmrko.companion;
import io.github.devmrko.companion.service.BuildSpecTemplates;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildSpecTemplatesTest {
    @Test void builderFailureStillCopiesReportsAndCleansContainer() throws Exception {
        var root=java.nio.file.Files.createTempDirectory("builder-failure-");
        var executable=root.resolve("docker");
        java.nio.file.Files.writeString(executable,"""
            #!/bin/sh
            case "$1" in
              create) echo test-container;;
              start) exit 0;;
              inspect) echo 7;;
              cp) if [ "$2" = 'test-container:/workspace/.' ]; then
                    mkdir -p reports
                    echo '<testsuite><testcase name="failure"><failure>expected</failure></testcase></testsuite>' > reports/test.xml
                  fi;;
              rm) touch cleaned;;
            esac
            """);executable.toFile().setExecutable(true);
        String yaml=BuildSpecTemplates.reports(BuildSpecTemplates.builder(BuildSpecTemplates.render("go","./...","",".","",""),"golang:1.27.3","."),true);
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        String command=((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString();
        var builder=new ProcessBuilder("bash","-c",command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("PATH",root+":"+System.getenv("PATH"));
        var process=builder.start();process.getInputStream().readAllBytes();assertEquals(7,process.waitFor());
        assertTrue(java.nio.file.Files.exists(root.resolve("cleaned")));
        try(var input=java.nio.file.Files.newInputStream(root.resolve("test-reports.tar.gz"))){assertEquals(1,io.github.devmrko.companion.service.TestReports.read(input).failures());}
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.builder(yaml,"image:$(id)","."));
    }
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
    @Test void containerOutputBuildsTheExactImageAfterApplicationBuild() throws Exception {
        String yaml=BuildSpecTemplates.configured("maven","package","","app","image","application:v1","wrapper","","","DOCKER_IMAGE","docker/Dockerfile",".");
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        String command=((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString();
        assertTrue(command.indexOf("./mvnw")<command.indexOf("docker build"));
        assertTrue(command.contains("docker build --file 'docker/Dockerfile' --tag 'application:v1' -- '.'"));
        var output=(Map<?,?>)((List<?>)spec.get("outputArtifacts")).getFirst();
        assertEquals("DOCKER_IMAGE",output.get("type"));assertEquals("application:v1",output.get("location"));
        var process=new ProcessBuilder("bash","-n").start();try(var out=process.getOutputStream()){out.write(command.getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertEquals(0,process.waitFor());
        String binary=BuildSpecTemplates.configured("maven","package","",".","app","target/app.jar","wrapper","","","BINARY");
        assertFalse(binary.contains("docker build"));
    }
    @Test void containerRequiresExplicitImageTagAndRelativePaths() {
        for(String tag:List.of("","app","app:$(id)","app:v1;id","App:v1"))
            assertThrows(RuntimeException.class,()->BuildSpecTemplates.configured("maven","package","",".","image",tag,"wrapper","","","DOCKER_IMAGE","Dockerfile","."));
        for(String path:List.of("","/etc/passwd","../Dockerfile","a/../Dockerfile","--help")) {
            assertThrows(RuntimeException.class,()->BuildSpecTemplates.configured("maven","package","",".","image","app:v1","wrapper","","","DOCKER_IMAGE",path,"."));
            assertThrows(RuntimeException.class,()->BuildSpecTemplates.configured("maven","package","",".","image","app:v1","wrapper","","","DOCKER_IMAGE","Dockerfile",path));
        }
    }
    @Test void architectureMismatchStopsBeforeBuilding() throws Exception {
        String yaml=BuildSpecTemplates.architecture(BuildSpecTemplates.render("maven","verify","",".","",""),System.getProperty("os.arch").equals("aarch64")?"X86_64":"ARM64");
        Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        String command=((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString();
        var process=new ProcessBuilder("bash","-c",command).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1,process.waitFor());assertTrue(output.contains("Architecture mismatch"));assertFalse(output.contains("mvnw:"));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.architecture(yaml,"MULTI"));
    }
    @Test void additionalLanguageToolsUseTheirOwnCommands() {
        for(var choice:List.of(List.of("go","./...","go test './...'"),List.of("ruby","test","bundle exec rake 'test'"),List.of("typescript","build","npm run 'build'"))) {
            String yaml=BuildSpecTemplates.render(choice.get(0),choice.get(1),"",".","","");
            Map<?,?> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
            String command=((Map<?,?>)((List<?>)spec.get("steps")).getFirst()).get("command").toString();
            assertTrue(command.contains(choice.get(2)));
            assertFalse(command.contains("./mvnw"));
        }
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.runtime(BuildSpecTemplates.render("go","./...","",".","",""),"go","node22"));
    }
    @Test void invalidOutputPairAndUnsupportedToolAreRejected() {
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("maven","verify","",".","app",""));
        assertThrows(RuntimeException.class,()->BuildSpecTemplates.render("shell","echo hi","",".","",""));
        for(String tool:List.of("maven","gradle","python","node","typescript","go","ruby"))assertNotNull(new org.yaml.snakeyaml.Yaml().load(BuildSpecTemplates.render(tool,"test","",".","","")));
    }
}
