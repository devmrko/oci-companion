package io.github.devmrko.companion;
import io.github.devmrko.companion.service.BuildSpecDocument;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildSpecDocumentTest {
    private static final String YAML="""
        # Existing build specification
        version: 0.1
        component: build
        timeoutInSeconds: 1800
        env:
          variables:
            MODE: test
          vaultVariables:
            TOKEN: ocid1.vaultsecret.oc1.example
          exportedVariables: [VERSION]
        inputArtifacts:
          - name: existing-input
            type: URL
            url: https://example.com/input
            location: input.txt
        steps:
          - type: Command
            name: Test
            command: mvn test
            timeoutInSeconds: 120
            onFailure:
              - type: Command
                command: echo failed
          - type: VulnerabilityAudit
            name: audit
            configuration: {custom: keep}
          - type: Command
            command: echo done
        outputArtifacts:
          - name: report
            type: BINARY
            location: report.xml
        """;
    @Test void editsKeepOtherNativeFieldsAndFailureSteps() {
        var original=BuildSpecDocument.read(YAML);assertEquals(3,original.commands().size());assertEquals(YAML,BuildSpecDocument.update(YAML,original));
        var commands=new ArrayList<>(original.commands());var first=commands.getFirst();commands.set(0,new BuildSpecDocument.Command(first.path(),first.name(),"mvn verify"));
        String changed=BuildSpecDocument.update(YAML,new BuildSpecDocument.Fields(commands,Map.of("MODE","verify","MULTILINE","a\nb"),original.vaultVariables()));
        Map<?,?> before=new org.yaml.snakeyaml.Yaml().load(YAML),after=new org.yaml.snakeyaml.Yaml().load(changed);
        assertEquals(before.get("outputArtifacts"),after.get("outputArtifacts"));assertEquals(before.get("inputArtifacts"),after.get("inputArtifacts"));assertEquals(before.get("timeoutInSeconds"),after.get("timeoutInSeconds"));
        var steps=(List<?>)after.get("steps");assertEquals(((List<?>)before.get("steps")).get(1),steps.get(1));assertEquals(((Map<?,?>)((List<?>)before.get("steps")).getFirst()).get("onFailure"),((Map<?,?>)steps.getFirst()).get("onFailure"));
        assertEquals(List.of("VERSION"),((Map<?,?>)after.get("env")).get("exportedVariables"));assertEquals("a\nb",BuildSpecDocument.read(changed).variables().get("MULTILINE"));
    }
    @Test void editingAnAliasedStepDoesNotChangeAnotherStep(){
        String yaml="component: build\nsteps:\n- &step {type: Command, command: echo original}\n- *step\n";
        var fields=BuildSpecDocument.read(yaml);var commands=new ArrayList<>(fields.commands());var first=commands.getFirst();commands.set(0,new BuildSpecDocument.Command(first.path(),first.name(),"echo changed"));
        var result=BuildSpecDocument.read(BuildSpecDocument.update(yaml,new BuildSpecDocument.Fields(commands,fields.variables(),fields.vaultVariables())));
        assertEquals("echo changed",result.commands().getFirst().value());assertEquals("echo original",result.commands().get(1).value());
        assertThrows(RuntimeException.class,()->BuildSpecDocument.read("component: build\nsteps: &steps [{type: Command, command: echo x, onFailure: *steps}]"));
    }
    @Test void malformedYamlAndStepRemovalAreRejected(){
        assertThrows(RuntimeException.class,()->BuildSpecDocument.read("!!java.net.URL [https://example.com]"));
        assertThrows(RuntimeException.class,()->BuildSpecDocument.read("component: build\ncomponent: build\nsteps: []"));
        var fields=BuildSpecDocument.read(YAML);assertThrows(RuntimeException.class,()->BuildSpecDocument.update(YAML,new BuildSpecDocument.Fields(List.of(),fields.variables(),fields.vaultVariables())));
    }
}
