package io.github.devmrko.companion.service;
import io.github.devmrko.foundation.core.error.*;
public enum Errors implements ErrorDescriptor {
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
