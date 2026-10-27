package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.BuildDefinition;
import io.github.devmrko.companion.port.BuildSetup;
import java.util.*;

/** Checks only observable configuration; never claims IAM or runtime connectivity is proven. */
public final class BuildChecks {
    private BuildChecks() {}
    public record Check(String name,String state,String message,boolean blocking) {}
    public static String purpose(BuildDefinition.Definition d) {
        if(d.stages().stream().anyMatch(s->s.type().equals("배포 호출")))return "deploy";
        return d.stages().stream().anyMatch(s->s.type().equals("산출물 전달"))?"store":"test";
    }
    public static List<Check> inspect(BuildDefinition.Definition d,BuildSetup.Settings settings,Map<String,BuildDefinition.Specification> specs) {
        var result=new ArrayList<Check>();
        boolean build=d.stages().stream().anyMatch(s->!s.spec().isEmpty());
        result.add(new Check("단계 상태",d.runnable()&&build?"확인":"누락",d.runnable()&&build?"ACTIVE 빌드 단계":"ACTIVE 빌드 단계가 필요합니다.",!d.runnable()||!build));
        var outputs=new HashMap<String,Set<String>>();
        for(var stage:d.stages()) if(!stage.spec().isEmpty()) {
            var spec=specs.get(stage.id());
            boolean readable=spec!=null&&!spec.content().isBlank();
            boolean local=stage.sources().stream().anyMatch(s->!s.repositoryId().isEmpty());
            result.add(new Check("명세 · "+stage.name(),readable?"확인":local?"오류":"미확인",readable?stage.spec():"명세 경로·소스 읽기 권한을 확인하세요.",!readable&&local));
            if(readable)for(String output:spec.outputs()) {
                var parts=output.split(" · ",3);
                if(parts.length==3)outputs.computeIfAbsent(parts[0],k->new HashSet<>()).add(parts[1]);
            }
        }
        boolean delivery=d.stages().stream().anyMatch(s->s.type().equals("산출물 전달"));
        if(delivery) {
            if(settings==null || settings.deliveries().isEmpty())result.add(new Check("저장 위치","오류","전달 설정 조회 실패",true));
            else for(var stage:settings.deliveries()) {
                if(stage.mappings().isEmpty())result.add(new Check("저장 위치","누락","전달 출력 매핑 없음",true));
                for(var mapping:stage.mappings()) {
                    var artifact=settings.artifacts().stream().filter(a->a.id().equals(mapping.artifact())).findFirst().orElse(null);
                    boolean missing=artifact==null;
                    var types=outputs.getOrDefault(mapping.output(),Set.of());
                    String expected=missing?"":artifact.type().equals("GENERIC_FILE")?"BINARY":"DOCKER_IMAGE";
                    boolean allRead=d.stages().stream().filter(s->!s.spec().isEmpty()).allMatch(s->specs.get(s.id())!=null&&!specs.get(s.id()).content().isBlank());
                    boolean mismatch=allRead&&!types.contains(expected);
                    result.add(new Check("저장 · "+mapping.output(),missing||mismatch?"오류":"확인",missing?"산출물 참조 조회 실패":mismatch?"명세 출력 이름·유형 불일치":artifact.location(),missing||mismatch));
                }
            }
        }else result.add(new Check("저장 위치","없음","Registry 전달 단계 없음. 빌드·테스트만 실행할 수 있습니다.",false));
        result.add(new Check("실행 권한·로그","미확인","사용자 권한과 파이프라인 권한은 별도입니다. 프로젝트 Logging 활성화와 서비스 권한을 OCI 콘솔에서 확인하세요.",false));
        return List.copyOf(result);
    }
    public static void validate(BuildDefinition.Definition d,BuildSetup.Settings settings,Map<String,BuildDefinition.Specification> specs,String requested) {
        if(requested==null || !Set.of("test","store","deploy").contains(requested) || !purpose(d).equals(requested))throw Errors.BUILD_PURPOSE.exception();
        if(requested.equals("deploy") && d.stages().stream().noneMatch(s->s.type().equals("산출물 전달")))throw Errors.BUILD_PURPOSE.exception();
        if(inspect(d,settings,specs).stream().anyMatch(Check::blocking))throw Errors.BUILD_PURPOSE.exception();
    }
}
