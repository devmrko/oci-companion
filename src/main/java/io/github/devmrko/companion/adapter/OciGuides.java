package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Guide;
import io.github.devmrko.companion.port.GuideCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OciGuides implements GuideCatalog {
    public List<Guide> all() { return List.of(
        new Guide("connect","프로파일 연결","앱이 실행되는 PC의 OCI API 키 프로파일로 연결합니다. 프로파일 선택은 공용 서버용 사용자 인증이 아닙니다.",
            List.of("OCI 콘솔의 사용자 설정에서 API 서명 키를 등록합니다.","~/.oci/config에 사용자·tenancy·fingerprint·key_file·region을 설정합니다. 개인 키는 PC에 보관합니다.","이 앱에서 프로파일을 선택합니다. GetTenancy로 연결을 확인하며, 이후 기능 권한은 각 API 요청 시 OCI가 판단합니다.","현재 API 키 프로파일을 지원합니다. 보안 토큰·인스턴스 프린시펄은 후속 연결 어댑터 대상입니다."),
            "oci iam tenancy get --tenancy-id <TENANCY_OCID> --profile <PROFILE>","ConfigFileAuthenticationDetailsProvider → IdentityClient.getTenancy",
            "연결 확인에 tenancy 조회 권한이 필요합니다. 연결 성공은 빌드·배포 권한 보장이 아닙니다.","https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm"),
        new Guide("source","소스 연결","프로젝트의 Git URL과 브랜치는 로컬 설계 정보입니다. 실제 소스 연결은 OCI 파이프라인에서 설정합니다.",
            List.of("개발자 서비스 → DevOps → 프로젝트로 이동합니다.","외부 연결에서 Git 공급자와 Vault 시크릿을 연결하거나 OCI 코드 저장소를 준비합니다.","빌드 파이프라인의 Managed Build 단계에서 저장소와 브랜치, build_spec.yaml 경로를 지정합니다.","이 앱의 URL을 바꿔도 OCI 소스 설정은 자동 변경되지 않습니다. 실행 전에 OCI 설정과 일치하는지 확인하세요."),
            "oci devops repository list --project-id <PROJECT_OCID> --all --profile <PROFILE>","DevopsClient.listRepositories / createConnection",
            "사용자의 DevOps 설정 권한과 빌드 파이프라인의 소스·Vault 접근 권한이 각각 필요합니다.","https://docs.oracle.com/en-us/iaas/Content/devops/using/getting_started.htm"),
        new Guide("build","빌드·테스트","빌드 명세를 저장소에 넣고 기존 OCI 빌드 파이프라인을 실행합니다.",
            List.of("프로젝트의 도구에 맞는 build_spec.yaml 초안을 내려받아 수정하고 커밋합니다.","런타임/JDK 버전과 Wrapper, 테스트 의존성, 산출물의 정확한 파일 경로를 확인합니다.","OCI 빌드 파이프라인에 Managed Build → Deliver Artifacts 단계를 설정합니다.","빌드 파이프라인 OCID를 프로젝트에 저장한 뒤 실행합니다. 기존 파이프라인에 자동 배포 트리거가 있다면 배포까지 진행될 수 있습니다.","실행 상태는 이 앱에서 갱신하고 상세 단계 로그는 OCI 빌드 실행 화면에서 확인합니다."),
            "oci devops build-run create --build-pipeline-id <BUILD_PIPELINE_OCID> --profile <PROFILE>","DevopsClient.createBuildRun / getBuildRun",
            "사용자의 빌드 실행 권한, 동적 그룹에 속한 빌드 파이프라인의 Vault·소스·산출물 저장소 접근 권한이 필요합니다.","https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm"),
        new Guide("artifacts","배포본 관리","OCI Artifact Registry 또는 Container Registry를 산출물의 원본 저장소로 사용합니다.",
            List.of("OCI에 Artifact Registry 저장소를 만들고 빌드의 Deliver Artifacts 단계와 연결합니다.","커밋과 연결된 고유 버전을 사용하고 운영 승격 시 같은 산출물을 참조합니다.","DevOps의 배포 아티팩트에서 산출물 버전과 배포 명세를 연결합니다.","이 버전의 앱은 산출물 목록·승격을 직접 관리하지 않습니다. OCI 설정을 사용합니다."),
            "oci devops deploy-artifact list --project-id <PROJECT_OCID> --all --profile <PROFILE>","DevopsClient.listDeployArtifacts",
            "빌드 파이프라인은 저장 권한, 배포 파이프라인과 대상은 필요한 산출물을 읽을 권한이 필요합니다.","https://docs.oracle.com/en-us/iaas/Content/devops/using/create_buildpipeline.htm"),
        new Guide("vm","VM 배포","기존 Compute 인스턴스 그룹의 rolling deployment 파이프라인을 실행합니다.",
            List.of("대상 Compute VM의 지원 OS와 Oracle Cloud Agent의 관리 명령 실행 기능, 네트워크 접근을 확인합니다.","DevOps → Environments에서 Compute instance group을 등록합니다.","배포 명세에 파일 배치·서비스 재시작·헬스 체크를 정의하고 아티팩트로 등록합니다.","Deploy pipeline에 Compute instance group rolling deployment 단계를 구성합니다.","이 앱에 배포 파이프라인 OCID를 저장합니다. 실제 배포에는 OCI에 설정된 아티팩트 버전과 파라미터 기본값이 사용됩니다.","재배포/복구는 OCI에서 검증된 이전 아티팩트를 지정해 수행합니다. DB 데이터 복구는 별도 절차입니다."),
            "oci devops deployment create-pipeline-deployment --pipeline-id <DEPLOY_PIPELINE_OCID> --profile <PROFILE>","DevopsClient.createDeployment(CreateDeployPipelineDeploymentDetails) / getDeployment",
            "사용자의 배포 실행 권한, 배포 파이프라인의 인스턴스·배포 아티팩트 접근 권한, 대상 VM의 필요한 리소스 접근 권한을 각각 설정합니다.","https://docs.oracle.com/en-us/iaas/Content/devops/using/deploy_instancegroups.htm"),
        new Guide("iam","권한·실패 확인","연결 사용자와 실행 파이프라인의 권한을 구분해 확인합니다.",
            List.of("사용자 그룹의 정책에서 해당 컴파트먼트의 필요한 DevOps 작업을 허용합니다.","빌드·배포 파이프라인을 동적 그룹으로 식별하고 필요한 리소스 권한을 부여합니다.","권한 오류와 리소스 없음은 OCI에서 같은 오류로 보일 수 있습니다. 리전·OCID·컴파트먼트도 확인합니다.","SUBMITTING 상태가 오래 지속되거나 UNKNOWN이면 요청 ID와 OCI 실행 이력을 먼저 확인합니다. 새 실행을 무조건 반복하지 않습니다."),
            "oci devops build-run get --build-run-id <BUILD_RUN_OCID> --profile <PROFILE>","OCI IAM policies + DevopsClient.getBuildRun / getDeployment",
            "정책 예제의 대상·범위를 실제 프로젝트에 맞게 좁혀 적용합니다. 이 앱은 IAM 정책을 자동 생성하지 않습니다.","https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_iampolicies.htm")
    ); }
}
