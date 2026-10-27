package io.github.devmrko.companion.web;

import io.github.devmrko.companion.service.BuildSpecTemplates;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!legacy")
public class BuildSpecificationPages {
    @PostMapping(value="/builds/specification",produces="text/plain;charset=UTF-8")
    String specification(@RequestParam String tool,@RequestParam String tasks,@RequestParam(defaultValue="") String profiles,
        @RequestParam(defaultValue=".") String directory,@RequestParam(defaultValue="") String outputName,@RequestParam(defaultValue="") String location,@RequestParam(defaultValue="wrapper") String launcher,@RequestParam(defaultValue="") String variables,@RequestParam(defaultValue="") String vaultVariables,@RequestParam(defaultValue="BINARY") String outputType) {
        return BuildSpecTemplates.configured(tool,tasks,profiles,directory,outputName,location,launcher,variables,vaultVariables,outputType);
    }
}
