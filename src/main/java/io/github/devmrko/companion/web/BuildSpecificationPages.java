package io.github.devmrko.companion.web;

import io.github.devmrko.companion.service.BuildSpecTemplates;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!legacy")
public class BuildSpecificationPages {
    @PostMapping(value="/builds/specification",produces="text/plain;charset=UTF-8")
    String specification(@RequestParam String tool,@RequestParam String tasks,@RequestParam(defaultValue="") String profiles,
        @RequestParam(defaultValue=".") String directory,@RequestParam(defaultValue="") String outputName,@RequestParam(defaultValue="") String location,@RequestParam(defaultValue="wrapper") String launcher,@RequestParam(defaultValue="") String variables,@RequestParam(defaultValue="") String vaultVariables,@RequestParam(defaultValue="BINARY") String outputType,@RequestParam(defaultValue="existing") String runtime,@RequestParam(defaultValue="false") boolean reports,@RequestParam(defaultValue="Dockerfile") String dockerfile,@RequestParam(defaultValue=".") String context,@RequestParam(defaultValue="NATIVE") String architecture,@RequestParam(defaultValue="") String builderImage,
        @RequestParam(defaultValue="") String reportPath,@RequestParam(defaultValue="") String reportNamespace,@RequestParam(defaultValue="") String reportBucket,@RequestParam(defaultValue="build-reports") String reportPrefix,@RequestParam(defaultValue="") String reportRegion,@RequestParam(defaultValue="") String buildCommand) {
        String yaml=BuildSpecTemplates.configured(tool,tasks,profiles,directory,outputName,location,launcher,variables,vaultVariables,outputType,dockerfile,context);
        yaml=BuildSpecTemplates.command(yaml,buildCommand,directory);
        yaml=runtime.equals("builder")?BuildSpecTemplates.builder(yaml,builderImage,directory):BuildSpecTemplates.runtime(yaml,tool,runtime);
        return BuildSpecTemplates.architecture(BuildSpecTemplates.reports(yaml,reports,reportPath,reportNamespace,reportBucket,reportPrefix,reportRegion),architecture);
    }
}
