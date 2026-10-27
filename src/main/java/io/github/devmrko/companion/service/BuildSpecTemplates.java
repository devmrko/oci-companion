package io.github.devmrko.companion.service;

import java.util.*;

/** Draft only: saving this text never changes Git or OCI. */
public final class BuildSpecTemplates {
    private BuildSpecTemplates() {}
    public static String render(String tool,String tasks,String profiles,String directory,String output,String location) {
        if(!Set.of("maven","gradle","python","node").contains(tool) || tasks.isBlank() || tasks.length()>1000 || profiles.length()>255
            || directory.isBlank() || directory.length()>1024 || output.length()>255 || location.length()>1024
            || List.of(tasks,profiles,directory,output,location).stream().anyMatch(s->s.chars().anyMatch(Character::isISOControl)))throw Errors.BUILD_STAGE_INPUT.exception();
        if(!profiles.isEmpty() && !tool.equals("maven"))throw Errors.BUILD_STAGE_INPUT.exception();
        if(output.isBlank()!=location.isBlank())throw Errors.BUILD_STAGE_INPUT.exception();
        if(!output.isBlank() && !output.matches("[A-Za-z][A-Za-z0-9_-]*"))throw Errors.BUILD_STAGE_INPUT.exception();
        // Tasks/options are arguments, never executable shell fragments.
        String arguments=Arrays.stream(tasks.trim().split("\\s+")).map(BuildSpecTemplates::quote).collect(java.util.stream.Collectors.joining(" "));
        String command=switch(tool) {
            case "maven" -> "./mvnw -B "+arguments+(profiles.isEmpty()?"":" "+quote("-P"+profiles));
            case "gradle" -> "./gradlew --no-daemon "+arguments;
            case "python" -> "python3 -m venv .venv\n. .venv/bin/activate\npython -m pip install -r requirements.txt\npython -m pytest "+arguments;
            default -> "npm ci\n"+Arrays.stream(tasks.trim().split("\\s+")).map(task->"npm run "+quote(task)).collect(java.util.stream.Collectors.joining("\n"));
        };
        var step=new LinkedHashMap<String,Object>();step.put("type","Command");step.put("name","Build and test");
        step.put("command","set -euo pipefail\ncd -- "+quote(directory)+"\n"+command);
        var spec=new LinkedHashMap<String,Object>();spec.put("version","0.1");spec.put("component","build");spec.put("timeoutInSeconds",3600);spec.put("shell","bash");spec.put("failImmediatelyOnError",true);spec.put("steps",List.of(step));
        if(!output.isBlank()) {
            var artifact=new LinkedHashMap<String,String>();artifact.put("name",output);artifact.put("type","BINARY");artifact.put("location",location);spec.put("outputArtifacts",List.of(artifact));
        }
        var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        return "# 초안: 런타임·의존성·명령·출력 경로를 확인하고 소스에 커밋하세요.\n"+new org.yaml.snakeyaml.Yaml(options).dump(spec);
    }
    private static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
}
