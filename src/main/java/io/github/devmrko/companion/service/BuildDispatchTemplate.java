package io.github.devmrko.companion.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
public final class BuildDispatchTemplate {
    private BuildDispatchTemplate() {}
    public static Map<String,String> files(String pipeline,String region,String compartment){
        var files=new LinkedHashMap<String,String>();
        for(var name:List.of("func.py","Dockerfile"))try(var input=new ClassPathResource("dispatch-template/"+name).getInputStream()){files.put(name,new String(input.readAllBytes(),StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}
        var config=Map.of("BUILD_RUNNER_CONTRACT","devops-dispatch-v1","BUILD_PIPELINE_ID",pipeline,"BUILD_REGION",region);
        files.put("func.yaml","schema_version: 20180708\nname: devops-build-dispatch\nversion: 0.0.1\nruntime: docker\nmemory: 256\ntimeout: 120\nconfig: "+JsonMapper.builder().build().writeValueAsString(config)+"\n");
        files.put("README.md","""
            # 예약 빌드 Function
            OCI Companion 없이 Fn CLI로 배포하고 OCI Resource Scheduler에서 호출합니다.
            1. Functions 애플리케이션과 해당 아키텍처의 Fn CLI 빌드 환경을 준비합니다.
            2. OCI DevOps API로 나가는 HTTPS 네트워크와 OCIR 인증을 구성합니다.
            3. fn -v deploy --app <APPLICATION_NAME>
            4. Function OCID가 포함된 동적 그룹을 만듭니다.
               ALL {resource.type='fnfunc', resource.id='<FUNCTION_OCID>'}
            5. 해당 그룹에 아래 권한을 부여합니다. 실행 파이프라인의 소스/Registry/Logging 권한은 별도입니다.
               Allow dynamic-group <FUNCTION_GROUP> to manage devops-build-run in compartment id %s
            6. 앱의 빌드 → 자동 실행에서 같은 파이프라인을 선택하고 Function 목록을 다시 조회합니다.
            7. UTC cron을 확인해 예약을 생성합니다. 일정은 OCI Resource Scheduler에 저장됩니다.
            8. 일정 OCID를 포함한 동적 그룹과 Function 호출 정책을 구성합니다.
               ALL {resource.type='resourceschedule', resource.id='<SCHEDULE_OCID>'}
               Allow dynamic-group <SCHEDULE_GROUP> to manage functions-family in compartment id %s
            9. Resource Scheduler 실행 상태, Function 로그, DevOps 실행 이력을 각각 확인합니다.

            기본 동작: 파이프라인에 저장된 기본 파라미터와 최신 브랜치 소스로 실행합니다.
            로컬 작업의 고정 커밋/프리셋은 사용하지 않습니다. build_spec.yaml에 고정 SHA가 있으면 그대로 적용됩니다.
            ACCEPTED/IN_PROGRESS/CANCELING 빌드가 있으면 건너뜁니다. 조회와 제출은 원자적이지 않으므로 여러 일정의 동시 실행을 완전히 막지는 않습니다.
            SDK 자동 재시도는 꺼져 있습니다. 응답 유실 시 빌드 이력을 확인한 후 다시 실행하세요.
            Function 접수 성공은 빌드 성공이 아닙니다. buildRunId로 결과를 확인하세요.
            예약 중지·시간 변경: OCI 콘솔 → 거버넌스 및 관리 → Resource Scheduler → Schedules.
            공식 문서: https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsscheduling.htm
            """.formatted(compartment,compartment));return files;
    }
}
