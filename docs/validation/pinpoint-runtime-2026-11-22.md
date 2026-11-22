# Pinpoint Java 덤프·Python 수집 검증

2026-11-22, 기존 OKE의 전용 namespace에 공식 차트 4.0.1 평가 환경을 설치했습니다. Companion과 독립된 서버·에이전트입니다.

## 실제 결과

- Python Flask 예제에 OpenTelemetry HTTP/protobuf 적용. Collector 9997로 `/hello` 요청 30건 전송.
- Pinpoint Web 서비스맵에 `python-diagnostics` / OPENTELEMETRY_SERVER 등록. 응답 요약 30건, 오류 0, 응답시간 그래프 확인.
- Java 21 / Spring Boot 4.1.1 예제에 Pinpoint 4.0.1 네이티브 에이전트 적용. `DEFAULT`, `java-diagnostics`, `java-demo-1` 사용.
- 인증된 `/diagnostics/slow` 요청을 동시에 2건 실행. 서비스맵의 실시간 화면에서 활성 요청 2건 확인.
- 서버 행의 덤프 버튼 → 처리 중 요청 선택. Web의 Thread Dump에서 `TIMED_WAITING`, `Thread.sleep`, `example.Application.slow(Application.java:23)` 확인.
- CLI 상태 조회뿐 아니라 제품 브라우저 화면으로 수집·덤프를 확인했습니다.

## 재현

Java 예제의 `DIAGNOSTICS_SLOW_ENABLED=true`는 30초 지연 요청을 활성화합니다. Bearer 인증이 필요하며 기본값은 비활성입니다. 검증 이후 기존 예제를 복원합니다.

Pinpoint 서비스맵: 앱 선택 → 시간 범위 옆 ▶ → 서버 행의 덤프 버튼 → 아직 실행 중인 요청 선택. 종료된 요청은 활성 덤프 대상이 아닙니다.

Python: `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:9997`, `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`, metrics/logs exporter `none`. OpenTelemetry 계측으로 기존 앱 실행.

## 한계

Python 요청 추적 검증이며 Python 스레드 덤프 지원을 의미하지 않습니다. 평가 구성에서 검증했으며 운영 HA·장애 복구·부하 시험은 수행하지 않았습니다. 운영 구성 기준은 설치 ZIP의 OPERATIONS.md와 공식 production profile을 따릅니다.

검증 후 전용 namespace 삭제를 요청하고 PV를 정리한 뒤 OCI 볼륨 7개의 TERMINATED 상태를 확인했습니다. Java·Python 예제는 기존 로컬 SigNoz 연결로 복원했습니다.

추가 HTTP 검증: 지연 요청 기본 비활성 404, 인증 누락 403, 활성화+인증 성공 200.
