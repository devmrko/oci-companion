package io.github.devmrko.companion.service;
import io.github.devmrko.foundation.core.error.*;
public enum Errors implements ErrorDescriptor {
    BUILD_STAGE_INPUT(ErrorCategory.VALIDATION, "빌드 단계의 소스·브랜치·명세 경로·이미지·선행 단계를 확인하세요."),
    BUILD_PURPOSE(ErrorCategory.VALIDATION, "실행 목적과 단계 구성이 다릅니다. 빌드·저장·배포 단계와 산출물 연결을 확인하세요."),
    DESTINATION_LINK(ErrorCategory.CONFLICT, "저장 위치 참조는 생성됐지만 단계 연결에 실패했습니다. 파이프라인 탭의 전달 위치에서 build-output 참조와 현재 연결을 확인하세요."),
    REGISTRY_INPUT(ErrorCategory.VALIDATION, "저장소 유형·이름·설명을 확인하세요. 파일: 영문·숫자·밑줄·하이픈, 이미지: 소문자·숫자·경로. 설명 최대 4,000자."),
    TOPIC_INPUT(ErrorCategory.VALIDATION, "알림 주제 이름은 영문·숫자·하이픈·밑줄 1–255자로 입력하세요."),
    DELIVERY_ORDER(ErrorCategory.VALIDATION, "뒤에 연결된 단계가 있습니다. 기존 순서를 OCI 콘솔에서 확인하고 전달 단계를 연결하세요."),
    OUTPUT_UNKNOWN(ErrorCategory.VALIDATION, "빌드 사양에서 출력 이름을 확인할 수 없습니다. outputArtifacts와 소스 읽기 권한을 확인하세요."),
    ADMIN_INPUT(ErrorCategory.VALIDATION, "이름(1–255자)과 설명(400자 이하)을 확인하세요."),
    ADMIN_UNKNOWN(ErrorCategory.UNAVAILABLE, "생성 결과를 확인하지 못했습니다. 재생성 전에 목록과 OCI 콘솔을 확인하세요."),
    BUILD_REQUIRED(ErrorCategory.VALIDATION, "필수 파라미터 값을 입력하세요."),
    BUILD_PRESET(ErrorCategory.CONFLICT, "프리셋과 현재 OCI 파라미터가 다릅니다. OCI 기본값을 불러와 다시 저장하세요."),
    BUILD_PARAMETERS(ErrorCategory.VALIDATION, "파라미터 이름·값을 확인하세요. 새 이름은 OCI에 먼저 저장하세요."),
    BUILD_CHANGED(ErrorCategory.CONFLICT, "설정 저장 실패 또는 OCI 설정 변경 · 새로 조회하고 권한을 확인하세요."),
    BUILD_UNKNOWN(ErrorCategory.UNAVAILABLE, "실행 결과를 확인하지 못했습니다. 다시 실행하기 전에 이력과 OCI 콘솔을 확인하세요."),
    INVALID(ErrorCategory.VALIDATION, "입력값을 확인하세요. 저장소에는 인증정보 없는 HTTPS URL을 사용하세요."),
    CONFIG(ErrorCategory.UNAVAILABLE, "OCI 프로파일을 읽거나 연결할 수 없습니다. API 키 설정과 tenancy 조회 권한을 확인하세요."),
    CLOUD(ErrorCategory.UNAVAILABLE, "OCI 요청을 완료하지 못했습니다. 연결·리전·IAM 권한과 OCI 콘솔을 확인하세요."),
    MISSING(ErrorCategory.NOT_FOUND, "현재 연결에서 찾을 수 없는 항목입니다."),
    CONFLICT(ErrorCategory.CONFLICT, "이미 사용된 요청입니다. 실행 이력을 확인하세요."),
    TARGET(ErrorCategory.VALIDATION, "현재는 Instance Group 배포와 Approval/Wait 단계로 구성된 VM 파이프라인만 실행할 수 있습니다."),
    SCOPE(ErrorCategory.CONFLICT, "OCI 설정의 계정 또는 리전이 변경되었습니다. 다시 연결하세요.");
    private final ErrorCategory category; private final String message;
    Errors(ErrorCategory category, String message) { this.category=category; this.message=message; }
    public ErrorCategory category() { return category; }
    public String code() { return name(); }
    public String safeMessage() { return message; }
    public BusinessException exception() { return new BusinessException(this); }
}
