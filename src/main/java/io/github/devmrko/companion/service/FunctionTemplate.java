package io.github.devmrko.companion.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

public final class FunctionTemplate {
    private FunctionTemplate() {}
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public record Settings(String name,String application,String architecture,String namespace,String bucket,String subnet,String registryPrefix) {}
    public static Map<String,String> files(String profile,String region,String compartment,Settings s) {
        if(!s.name().matches("[a-z][a-z0-9-]{0,49}") || !s.application().matches("[a-zA-Z][a-zA-Z0-9_-]{0,99}") || !Set.of("ARM","X86").contains(s.architecture())
            || !s.registryPrefix().matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*") || s.registryPrefix().length()>100
            || !s.namespace().matches("[a-zA-Z0-9_-]{1,100}") || !s.bucket().matches("[a-zA-Z0-9_.-]{1,100}")
            || (!s.subnet().isBlank() && !s.subnet().matches("ocid1\\.subnet\\.[a-zA-Z0-9.]+")))throw Errors.INVALID.exception();
        var config=Map.of("BUILD_RUNNER_CONTRACT","maven-v1","BUILD_NAMESPACE",s.namespace(),"BUILD_BUCKET",s.bucket(),"BUILD_PREFIX",s.name()+"-"+UUID.randomUUID().toString().substring(0,8),"BUILD_ALLOWED_GIT_HOSTS","github.com,gitlab.com");
        var result=new LinkedHashMap<String,String>();
        for(var name:List.of("Dockerfile","func.py"))try(var input=new ClassPathResource("fn-template/"+name).getInputStream()) { result.put(name,new String(input.readAllBytes(),StandardCharsets.UTF_8)); }catch(IOException e){throw new IllegalStateException("템플릿 읽기 실패");}
        result.put("func.yaml","schema_version: 20180708\nname: "+s.name()+"\nversion: 0.0.1\nruntime: docker\nmemory: 3072\ntimeout: 300\nconfig: "+JSON.writeValueAsString(config)+"\n");
        result.put("config.json",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        result.put("application.json",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("compartmentId",compartment,"displayName",s.application(),"shape","GENERIC_"+s.architecture(),"subnetIds",s.subnet().isBlank()?List.of("<SUBNET_OCID>"):List.of(s.subnet()))));
        result.put("request.json",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("action","build","runId",UUID.randomUUID().toString(),"source","https://github.com/<owner>/<repository>.git","commit","<40자리 커밋 SHA>","goal","verify","profiles","","directory",".","artifact","target/*.jar")));
        String flags=" --profile "+quote(profile)+" --region "+quote(region);
        String registry=region+".ocir.io/"+s.namespace()+"/"+s.registryPrefix();
        result.put("README.md","""
            # Maven Function 빌더

            이 프로젝트는 OCI Companion 없이 Fn CLI·OCI CLI로 배포하고 호출할 수 있습니다.
            요구 도구: Fn CLI, OCI CLI, Docker 또는 Fn CLI에 설정된 Podman.
            빌더 이미지는 최초 구성·도구 변경 때 만듭니다. 사용자 소스는 Function 내부에서 빌드합니다.
            지원: Maven/JDK21, 공개 GitHub·GitLab 저장소, 고정 커밋. 비밀값을 JSON에 넣지 마세요.

            ## 1. OCI 리소스
            콘솔 → 개발자 서비스 → Functions → 애플리케이션.
            같은 이름의 애플리케이션이 있으면 아키텍처·서브넷을 확인해 재사용하세요.
            없으면 application.json의 서브넷을 확인한 뒤 생성합니다. 인터넷 HTTPS 통신이 필요합니다.
            ```sh
            oci fn application list FLAGS --compartment-id COMPARTMENT --all
            oci fn application create FLAGS --from-json file://application.json
            oci os bucket create FLAGS --compartment-id COMPARTMENT --namespace-name NAMESPACE --name BUCKET
            oci artifacts container repository create FLAGS --compartment-id COMPARTMENT --display-name REPOSITORY --is-public false
            ```
            버킷·컨테이너 저장소가 있으면 해당 생성 명령을 생략합니다. 컨테이너 Registry와 결과 버킷은 서로 다른 용도입니다.

            ## 2. Fn Project 배포
            OCI Console의 사용자 Auth Token으로 Registry에 로그인합니다. 토큰은 저장소나 이미지에 넣지 마세요.
            별도 Fn context를 사용하고 실제 OCI 프로파일을 지정하세요.
            ```sh
            fn create context CONTEXT --provider oracle
            fn use context CONTEXT
            fn update context oracle.profile PROFILE
            fn update context oracle.compartment-id COMPARTMENT
            fn update context api-url API_URL
            fn update context registry REGISTRY
            docker login REGISTRY_HOST
            fn deploy --app APPLICATION
            ```
            애플리케이션 아키텍처에 맞게 이미지를 빌드합니다. ARM은 linux/arm64, x86은 linux/amd64입니다.
            생성된 Function OCID를 확인하고 긴 비동기 실행 시간을 설정합니다.
            ```sh
            oci fn function update FLAGS --function-id '<FUNCTION_OCID>' --detached-mode-timeout-in-seconds 3600
            ```

            ## 3. 권한과 로그
            콘솔 → ID 및 보안 → 정책. 사용자 그룹에는 Functions 조회·호출, 구성 담당자에는 Functions 생성·네트워크 사용·Registry 업로드 권한이 필요합니다.
            Function에는 아래 결과 버킷 쓰기 권한을 부여합니다. 앱에서 결과를 보는 사용자에게는 버킷 객체 읽기 권한이 필요합니다.
            ```text
            Allow any-user to manage objects in compartment id COMPARTMENT where all {request.principal.type='fnfunc', request.principal.id='<FUNCTION_OCID>', target.bucket.name=BUCKET, any {request.permission='OBJECT_CREATE', request.permission='OBJECT_OVERWRITE'}}
            ```
            Functions → 애플리케이션 → 로그에서 호출 로그를 활성화하세요. 강제 종료 시 결과 JSON이 RUNNING에 머물 수 있습니다.

            ## 4. 소스 빌드
            request.json의 소스 URL·커밋·goal·profiles·directory·artifact를 입력합니다.
            goal은 test/package/verify. test만 실행하면 artifact를 빈 문자열로 설정하세요.
            매 실행 새로운 UUID runId를 사용합니다. 동일 runId는 중복 실행하지 않습니다.
            ```sh
            oci fn function invoke FLAGS --function-id '<FUNCTION_OCID>' --fn-invoke-type detached --body file://request.json --file response.json
            ```
            HTTP202는 접수입니다. 결과 버킷의 BUILD_PREFIX/<runId>/result.json, build.log, artifacts/, test-reports.tar.gz를 확인하세요.
            로그는 실행 중 갱신됩니다. 최대 3GiB 메모리·3600초 실행이며 /tmp 용량은 환경에서 확인하세요.
            이 ZIP은 설정·코드만 포함합니다. 생성·배포 명령은 사용자가 실행합니다.
            """.replace("FLAGS",flags).replace("COMPARTMENT",quote(compartment)).replace("NAMESPACE",quote(s.namespace())).replace("BUCKET",quote(s.bucket()))
            .replace("REPOSITORY",quote(s.registryPrefix()+"/"+s.name())).replace("CONTEXT",quote(s.name()+"-context")).replace("PROFILE",quote(profile))
            .replace("API_URL",quote("https://functions."+region+".oci.oraclecloud.com")).replace("REGISTRY_HOST",quote(region+".ocir.io"))
            .replace("REGISTRY",quote(registry)).replace("APPLICATION",quote(s.application())).replace("compartment id "+quote(compartment),"compartment id "+compartment));
        return result;
    }
    private static String quote(String value) { return "'"+value.replace("'","'\"'\"'")+"'"; }
    public static byte[] zip(Map<String,String> files) {
        try(var bytes=new ByteArrayOutputStream();var out=new ZipOutputStream(bytes)) {
            for(var entry:files.entrySet()){out.putNextEntry(new ZipEntry(entry.getKey()));out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}
            out.finish();return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException("템플릿 생성 실패");}
    }
    public static String request(String run,String source,String commit,String goal,String profiles,String directory,String artifact) {
        try {
            var uri=java.net.URI.create(source);
            if(!"https".equals(uri.getScheme()) || !Set.of("github.com","gitlab.com").contains(Objects.toString(uri.getHost(),"")) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPort()!=-1 && uri.getPort()!=443))throw new IllegalArgumentException();
            UUID.fromString(run);
            if(!commit.matches("[0-9a-f]{40}") || !Set.of("test","package","verify").contains(goal) || !profiles.matches("[a-zA-Z0-9_,.-]{0,200}"))throw new IllegalArgumentException();
            for(var p:List.of(directory,artifact))if(p.length()>250 || p.startsWith("/") || Arrays.asList(p.split("/")).contains("..") || p.contains("\\") || p.chars().anyMatch(ch->ch<32))throw new IllegalArgumentException();
            return JSON.writeValueAsString(Map.of("action","build","runId",run,"source",source,"commit",commit,"goal",goal,"profiles",profiles,"directory",directory,"artifact",artifact));
        }catch(Exception e){throw Errors.INVALID.exception();}
    }
}
