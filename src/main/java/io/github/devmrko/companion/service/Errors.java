package io.github.devmrko.companion.service;
import io.github.devmrko.companion.error.*;
public enum Errors implements ErrorDescriptor {
    CONTAINER_NETWORK(ErrorCategory.VALIDATION, "공용 IP 없이 외부 Registry에 접근하려면 NAT 경로가 필요합니다. OCIR은 NAT 또는 서비스 게이트웨이를 사용할 수 있습니다. 연결 가능한 서브넷을 선택하세요. 사용자 정의 경로는 OCI 콘솔에서 구성하세요."),
    CAPACITY_INPUT(ErrorCategory.VALIDATION, "OCI 허용 범위와 변경값을 확인하세요. 볼륨은 현재보다 큰 용량(최대 32,768GB)만 가능합니다."),
    CAPACITY_STATE(ErrorCategory.CONFLICT, "CPU·메모리 변경은 중지된 VM의 지원되는 Flex Shape에서 가능합니다."),
    CAPACITY_UNKNOWN(ErrorCategory.UNAVAILABLE, "변경 결과를 확인하지 못했습니다. 재요청 전에 OCI 상태·사양을 새로 조회하고 권한·한도를 확인하세요."),
    SCHEDULE_INPUT(ErrorCategory.VALIDATION, "UTC cron 5개 항목을 확인하세요. 최소 1시간 간격이며 분은 0–59 중 하나입니다. 일·요일 중 하나는 *로 두세요."),
    SOURCE_READ(ErrorCategory.UNAVAILABLE, "소스 버전 조회 실패 · 접근 권한·공개 여부·API 조회 한도를 확인하세요. 커밋 SHA 직접 입력도 가능합니다."),
    SOURCE_MISMATCH(ErrorCategory.VALIDATION, "선택한 소스와 파이프라인 소스가 다르거나 여러 단계에 중복됩니다. 소스 연결을 확인하세요."),
    BUILD_STAGE_INPUT(ErrorCategory.VALIDATION, "빌드 단계의 소스·브랜치·명세 경로·이미지·선행 단계를 확인하세요."),
    BUILD_PURPOSE(ErrorCategory.VALIDATION, "실행 목적과 단계 구성이 다릅니다. 빌드·저장·배포 단계와 산출물 연결을 확인하세요."),
    DESTINATION_LINK(ErrorCategory.CONFLICT, "저장 위치 참조는 생성됐지만 단계 연결에 실패했습니다. 파이프라인 탭의 전달 위치에서 build-output 참조와 현재 연결을 확인하세요."),
    REGISTRY_INPUT(ErrorCategory.VALIDATION, "저장소 유형·이름·설명을 확인하세요. 파일: 영문·숫자·밑줄·하이픈, 이미지: 소문자·숫자·경로. 설명 최대 4,000자."),
    TOPIC_INPUT(ErrorCategory.VALIDATION, "알림 주제 이름은 영문·숫자·하이픈·밑줄 1–255자로 입력하세요."),
    DELIVERY_ORDER(ErrorCategory.VALIDATION, "뒤에 연결된 단계가 있습니다. 기존 순서를 OCI 콘솔에서 확인하고 전달 단계를 연결하세요."),
    OUTPUT_UNKNOWN(ErrorCategory.VALIDATION, "빌드 사양에서 출력 이름을 확인할 수 없습니다. outputArtifacts와 소스 읽기 권한을 확인하세요."),
    DEPLOYMENT_SETUP(ErrorCategory.VALIDATION, "배포 설정을 확인하세요. 절대 경로·실행 계정·설치/상태 확인 명령·변수 형식이 필요합니다."),
    DEPLOYMENT_ARTIFACT(ErrorCategory.VALIDATION, "선택 구획의 사용 가능한 불변 저장소와 고정 버전 배포본을 선택하세요."),
    DEPLOYMENT_ARCHITECTURE(ErrorCategory.CONFLICT, "이미지와 배포 명세의 아키텍처가 일치하지 않습니다. 아키텍처 점검 결과를 확인하세요."),
    DEPLOYMENT_IMAGE(ErrorCategory.CONFLICT, "지정 이미지 digest를 사용할 수 없습니다. OCIR 점검 결과를 확인하고 배포 이미지를 다시 선택하세요."),
    DEPLOYMENT_VM_STATE(ErrorCategory.CONFLICT, "대상 VM이 RUNNING 상태가 아닙니다. OCI Compute에서 VM 상태를 확인한 뒤 다시 조회하세요."),
    ADMIN_INPUT(ErrorCategory.VALIDATION, "이름(1–255자)과 설명(400자 이하)을 확인하세요."),
    ADMIN_UNKNOWN(ErrorCategory.UNAVAILABLE, "생성 결과를 확인하지 못했습니다. 재생성 전에 목록과 OCI 콘솔을 확인하세요."),
    BUILD_REQUIRED(ErrorCategory.VALIDATION, "필수 파라미터 값을 입력하세요."),
    BUILD_PRESET(ErrorCategory.CONFLICT, "프리셋과 현재 OCI 파라미터가 다릅니다. OCI 기본값을 불러와 다시 저장하세요."),
    BUILD_PARAMETERS(ErrorCategory.VALIDATION, "파라미터 이름·값을 확인하세요. 새 이름은 OCI에 먼저 저장하세요."),
    BUILD_CHANGED(ErrorCategory.CONFLICT, "확인 정보가 만료되었거나 설정이 변경되었습니다. 새로 조회한 뒤 다시 확인하세요."),
    BUILD_LOGS_REQUIRED(ErrorCategory.CONFLICT, "프로젝트 로그가 비활성 또는 준비 중이어서 빌드를 시작하지 않았습니다. OCI 콘솔 → DevOps 프로젝트 → 로그에서 활성화한 뒤 실행하세요."),
    BUILD_LOGS_NOT_FOUND(ErrorCategory.CONFLICT, "현재 구획에서 이 프로젝트의 DevOps 서비스 로그를 찾지 못해 빌드를 요청하지 않았습니다. 프로젝트 리소스 화면에서 로그를 준비하세요. 다른 구획의 로그는 OCI 콘솔에서 확인하세요."),
    BUILD_LOGS_UNVERIFIED(ErrorCategory.UNAVAILABLE, "현재 구획에서 활성 프로젝트 로그를 확인하지 못해 빌드를 요청하지 않았습니다. 프로젝트 리소스 화면이나 OCI 콘솔에서 로그 위치·활성 상태·Logging 읽기 권한을 확인하세요. 다른 구획의 로그는 이 점검 범위에 포함되지 않습니다."),
    BUILD_UNKNOWN(ErrorCategory.UNAVAILABLE, "실행 결과를 확인하지 못했습니다. 다시 실행하기 전에 이력과 OCI 콘솔을 확인하세요."),
    DEPLOYMENT_INPUT(ErrorCategory.VALIDATION, "배포 설정을 확인하세요. 지원하는 대상·단계·명세 형식과 실행 상태가 필요합니다."),
    INVALID(ErrorCategory.VALIDATION, "입력값을 확인하세요. 저장소에는 인증정보 없는 HTTPS URL을 사용하세요."),
    CONFIG(ErrorCategory.UNAVAILABLE, "OCI 프로파일을 읽거나 연결할 수 없습니다. API 키 설정과 tenancy 조회 권한을 확인하세요."),
    DRAFT_STALE(ErrorCategory.CONFLICT, "원본 커밋이 변경되어 로컬 초안을 적용하지 않았습니다. 초안은 유지됩니다."),
    DRAFT_MISSING(ErrorCategory.NOT_FOUND, "이 조회 대상에 저장한 로컬 초안이 없습니다."),
    GIT_CONFLICT(ErrorCategory.CONFLICT, "원격 브랜치 변경·보호 규칙 또는 만료된 요청입니다. Git 내용을 다시 비교하세요."),
    GIT_ACCESS(ErrorCategory.UNAVAILABLE, "Git 연결 실패 · 저장소 URL·브랜치·Vault 읽기 권한·토큰의 저장소 권한을 확인하세요."),
    BACKUP_POLICY_GROUP(ErrorCategory.VALIDATION, "볼륨 그룹 소속입니다. OCI 콘솔에서 그룹의 백업 정책을 확인하세요."),
    BACKUP_POLICY_REGION(ErrorCategory.VALIDATION, "리전 간 복사 정책은 OCI 콘솔에서 복사 대상과 암호화 키를 확인해 연결하세요."),
    BACKUP_POLICY_UNKNOWN(ErrorCategory.UNAVAILABLE, "정책 변경 결과를 확인하지 못했습니다. 다시 실행하기 전에 연결 상태를 새로 조회하세요."),
    BACKUP_INPUT(ErrorCategory.VALIDATION, "백업·복원 대상, 이름, 백업 유형과 가용성 도메인을 확인하세요."),
    BACKUP_UNKNOWN(ErrorCategory.UNAVAILABLE, "백업·복원 요청 결과를 확인하지 못했습니다. 다시 실행하기 전에 OCI 목록에서 생성 여부를 확인하세요."),
    CLOUD(ErrorCategory.UNAVAILABLE, "OCI 요청을 완료하지 못했습니다. 연결·리전·IAM 권한과 OCI 콘솔을 확인하세요."),
    MISSING(ErrorCategory.NOT_FOUND, "현재 연결에서 찾을 수 없는 항목입니다."),
    CONFLICT(ErrorCategory.CONFLICT, "이미 사용된 요청입니다. 실행 이력을 확인하세요."),
    TARGET(ErrorCategory.VALIDATION, "현재는 Instance Group 배포와 Approval/Wait 단계로 구성된 VM 파이프라인만 실행할 수 있습니다."),
    HANDOFF(ErrorCategory.CONFLICT, "빌드·전달 성공과 배포 파일 일치를 확인하지 못했습니다. 같은 산출물을 사용하는 파이프라인과 불변 저장소·읽기 권한을 확인하세요."),
    SCOPE(ErrorCategory.CONFLICT, "OCI 설정의 계정 또는 리전이 변경되었습니다. 다시 연결하세요.");
    private final ErrorCategory category; private final String message;
    Errors(ErrorCategory category, String message) { this.category=category; this.message=message; }
    public ErrorCategory category() { return category; }
    public String code() { return name(); }
    public String safeMessage() { return message; }
    public BusinessException exception() { return new BusinessException(this); }
}
