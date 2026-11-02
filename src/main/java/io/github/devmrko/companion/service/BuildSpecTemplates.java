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
            case "python" -> "python3 -m venv .venv\n. .venv/bin/activate\nexport PIP_CONFIG_FILE=\"${PIP_CONFIG_FILE:-/dev/null}\"\nexport PIP_CACHE_DIR=\"${PIP_CACHE_DIR:-$PWD/.cache/pip}\"\npython -m pip install -r requirements.txt\npython -m pytest "+arguments;
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
        return configured(tool,tasks,profiles,directory,output,location,launcher,variables,vaultVariables,outputType,"Dockerfile",".");
    }
    public static String configured(String tool,String tasks,String profiles,String directory,String output,String location,String launcher,String variables,String vaultVariables,String outputType,String dockerfile,String context) {
        if(!Set.of("wrapper","system").contains(launcher) || !Set.of("BINARY","DOCKER_IMAGE").contains(outputType))throw Errors.BUILD_STAGE_INPUT.exception();
        Map<String,Object> spec=new org.yaml.snakeyaml.Yaml().load(render(tool,tasks,profiles,directory,output,location));
        @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)spec.get("steps");
        String command=steps.getFirst().get("command").toString();
        if(launcher.equals("system"))command=command.replace("\n./mvnw -B ","\nmvn -B ").replace("\n./gradlew --no-daemon ","\ngradle --no-daemon ");
        if(outputType.equals("DOCKER_IMAGE")) {
            if(output.isBlank() || !location.matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"))throw Errors.BUILD_STAGE_INPUT.exception();
            containerPath(dockerfile);containerPath(context);
            command += "\ndocker info >/dev/null\ndocker build --file "+quote(dockerfile)+" --tag "+quote(location)+" -- "+quote(context)
                +"\ndocker image inspect "+quote(location)+" --format '{{.Id}}'";
        }
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
    public static String architecture(String yaml,String architecture) {
        if(architecture.equals("NATIVE"))return yaml;
        String expected=switch(architecture){case "X86_64" -> "x86_64";case "ARM64" -> "aarch64";default -> throw Errors.BUILD_STAGE_INPUT.exception();};
        Map<String,Object> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)spec.get("steps");
        var step=steps.getFirst();String guard="actual_arch=$(uname -m)\nprintf 'Build architecture: %s\\n' \"$actual_arch\"\n"
            +"test \"$actual_arch\" = "+quote(expected)+" || { echo 'Architecture mismatch: use a matching build environment'; exit 1; }\n";
        step.put("command",step.get("command").toString().replace("set -euo pipefail\n","set -euo pipefail\n"+guard));
        @SuppressWarnings("unchecked") var outputs=(List<Map<String,Object>>)spec.getOrDefault("outputArtifacts",List.of());
        String imageArchitecture=architecture.equals("X86_64")?"amd64":"arm64";
        for(var output:outputs)if("DOCKER_IMAGE".equals(output.get("type"))) {
            String check="\ntest \"$(docker image inspect "+quote(output.get("location").toString())+" --format '{{.Architecture}}')\" = "+quote(imageArchitecture)
                +" || { echo 'Image architecture mismatch'; exit 1; }";
            step.put("command",step.get("command").toString()+check);
        }
        var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        return new org.yaml.snakeyaml.Yaml(options).dump(spec);
    }
    private static void containerPath(String path) {
        if(path.isBlank() || path.length()>1024 || path.startsWith("/") || path.startsWith("-")
            || path.chars().anyMatch(Character::isISOControl) || Arrays.asList(path.split("/",-1)).contains(".."))throw Errors.BUILD_STAGE_INPUT.exception();
    }
    public static String reports(String yaml,boolean enabled) {
        if(!enabled)return yaml;
        Map<String,Object> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)spec.get("steps");
        var step=steps.getFirst();String command=step.get("command").toString();
        String collect="""
            report_root="${OCI_PRIMARY_SOURCE_DIR:-$PWD}"
            collect_reports() {
              find "$report_root" -type f \\( -path '*/surefire-reports/*.xml' -o -path '*/failsafe-reports/*.xml' -o -path '*/test-results/*.xml' -o -path '*/reports/*.xml' \\) -print0 | tar --null -T - -czf "$report_root/test-reports.tar.gz"
            }
            trap 'build_status=$?; collect_reports || { if [ "$build_status" -eq 0 ]; then build_status=1; fi; }; exit "$build_status"' EXIT
            """;
        step.put("command",command.replace("set -euo pipefail\n","set -euo pipefail\n"+collect));
        @SuppressWarnings("unchecked") var outputs=new ArrayList<Map<String,Object>>((List<Map<String,Object>>)spec.getOrDefault("outputArtifacts",List.of()));
        if(outputs.stream().anyMatch(a->"test-reports".equals(a.get("name"))))throw Errors.BUILD_STAGE_INPUT.exception();
        outputs.add(Map.of("name","test-reports","type","BINARY","location","test-reports.tar.gz"));spec.put("outputArtifacts",outputs);
        var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        return "# test-reports 출력은 별도 Registry 전달 연결이 필요합니다. 실패한 단계에서는 전달이 실행되지 않을 수 있습니다.\n"+new org.yaml.snakeyaml.Yaml(options).dump(spec);
    }
    public static String runtime(String yaml,String tool,String runtime) {
        if(runtime.equals("existing"))return yaml;
        boolean java=Set.of("maven","gradle").contains(tool);
        String install;
        if(java && Set.of("jdk17","jdk21").contains(runtime)) {
            String version=runtime.substring(3);
            install="""
                runtime_dir=$(mktemp -d "${OCI_PRIMARY_SOURCE_DIR:-$PWD}/.jdk.XXXXXX")
                case "$(uname -m)" in x86_64) runtime_arch=x64;; aarch64|arm64) runtime_arch=aarch64;; *) echo 'Unsupported Java architecture' >&2; exit 1;; esac
                curl --fail --silent --show-error --location --max-time 60 "https://api.adoptium.net/v3/assets/latest/%s/hotspot?architecture=$runtime_arch&image_type=jdk&os=linux&vendor=eclipse" -o "$runtime_dir/metadata.json"
                python3 - "$runtime_dir" <<'RUNTIME_PY'
                import json, pathlib, sys
                root = pathlib.Path(sys.argv[1])
                package = json.loads((root / 'metadata.json').read_text())[0]['binary']['package']
                url, checksum = package['link'], package['checksum']
                if not url.startswith('https://github.com/adoptium/') or len(checksum) != 64 or any(c not in '0123456789abcdef' for c in checksum):
                    raise ValueError('Invalid runtime metadata')
                (root / 'url').write_text(url)
                (root / 'sha256').write_text(checksum + '  ' + str(root / 'jdk.tar.gz') + '\\n')
                RUNTIME_PY
                curl --fail --silent --show-error --location --max-time 300 "$(cat "$runtime_dir/url")" -o "$runtime_dir/jdk.tar.gz"
                sha256sum -c "$runtime_dir/sha256"
                tar xzf "$runtime_dir/jdk.tar.gz" --strip-components=1 -C "$runtime_dir"
                export JAVA_HOME="$runtime_dir"
                export PATH="$JAVA_HOME/bin:$PATH"
                java -version
                """.formatted(version);
        } else if(tool.equals("python") && runtime.equals("python311")) {
            install="if ! command -v python3.11 >/dev/null 2>&1; then\n  test \"$(id -u)\" -eq 0 || { echo 'Runtime installation requires root' >&2; exit 1; }\n  dnf install -y python3.11 python3.11-pip\nfi\npython3.11 --version\n";
        } else if(tool.equals("node") && runtime.equals("node22")) {
            install="if ! node -e \"process.exit(process.versions.node.split('.')[0] === '22' ? 0 : 1)\" 2>/dev/null; then\n  test \"$(id -u)\" -eq 0 || { echo 'Runtime installation requires root' >&2; exit 1; }\n  dnf module reset -y nodejs\n  dnf module enable -y nodejs:22\n  dnf install -y nodejs npm\n  export PATH=/usr/bin:$PATH\nfi\nnode --version\nnpm --version\n";
        } else throw Errors.BUILD_STAGE_INPUT.exception();
        Map<String,Object> spec=new org.yaml.snakeyaml.Yaml().load(yaml);
        @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)spec.get("steps");
        var step=steps.getFirst();
        String command=step.get("command").toString();
        if(runtime.equals("python311"))command=command.replace("python3 -m venv", "python3.11 -m venv");
        step.put("command",command.replace("set -euo pipefail\n","set -euo pipefail\n"+install));
        var options=new org.yaml.snakeyaml.DumperOptions();options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        return "# OCI Managed Build / Oracle Linux 8 · 다운로드 접근 필요 · OS 패키지 설치 시 root 필요\n"+new org.yaml.snakeyaml.Yaml(options).dump(spec);
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
