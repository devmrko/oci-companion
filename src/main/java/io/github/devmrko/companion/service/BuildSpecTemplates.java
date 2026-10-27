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
    public static String configured(String tool,String tasks,String profiles,String directory,String output,String location,String launcher,String variables,String vaultVariables,String outputType) {
        if(!Set.of("wrapper","system").contains(launcher) || !Set.of("BINARY","DOCKER_IMAGE").contains(outputType))throw Errors.BUILD_STAGE_INPUT.exception();
        Map<String,Object> spec=new org.yaml.snakeyaml.Yaml().load(render(tool,tasks,profiles,directory,output,location));
        @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)spec.get("steps");
        String command=steps.getFirst().get("command").toString();
        if(launcher.equals("system"))command=command.replace("\n./mvnw -B ","\nmvn -B ").replace("\n./gradlew --no-daemon ","\ngradle --no-daemon ");
        steps.getFirst().put("command",command);
        var plain=variables(variables,false);var vault=variables(vaultVariables,true);
        if(plain.keySet().stream().anyMatch(vault::containsKey))throw Errors.BUILD_STAGE_INPUT.exception();
        if(!plain.isEmpty() || !vault.isEmpty()){var env=new LinkedHashMap<String,Object>();if(!plain.isEmpty())env.put("variables",plain);if(!vault.isEmpty())env.put("vaultVariables",vault);spec.put("env",env);}
        if(!output.isBlank()) {
            @SuppressWarnings("unchecked") var artifacts=(List<Map<String,Object>>)spec.get("outputArtifacts");artifacts.getFirst().put("type",outputType);
        }
        var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        return "# 소스에 커밋 후 빌드 단계의 명세 경로를 지정하세요. 런타임은 실행 환경에서 준비합니다.\n"+new org.yaml.snakeyaml.Yaml(options).dump(spec);
    }
    private static Map<String,String> variables(String text,boolean vault) {
        if(text.length()>8192)throw Errors.BUILD_STAGE_INPUT.exception();var result=new LinkedHashMap<String,String>();
        for(String line:text.lines().toList()) {
            if(line.isBlank())continue;int split=line.indexOf('=');if(split<1)throw Errors.BUILD_STAGE_INPUT.exception();
            String key=line.substring(0,split).strip(),value=line.substring(split+1);
            if(!key.matches("[A-Za-z_][A-Za-z0-9_]*") || key.startsWith("OCI_") || result.containsKey(key) || value.chars().anyMatch(Character::isISOControl) || (vault && !value.matches("ocid1\\.vaultsecret\\.[A-Za-z0-9.]+")))throw Errors.BUILD_STAGE_INPUT.exception();result.put(key,value);
        }if(result.size()>30)throw Errors.BUILD_STAGE_INPUT.exception();return result;
    }
    private static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
}
