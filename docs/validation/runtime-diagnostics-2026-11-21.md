# Java 런타임 진단 화면 검증 — 2026-11-21

독립 Spring Boot 4.1.1 / Java 21 예제를 localhost:8093에 실행했다.
가짜 Actuator 응답 없이 실제 JVM·Micrometer·Spring Boot Actuator를 사용했다.
예제 코드는 `examples/runtime-diagnostics/java`이며 Companion 종속성이 없다.

## 실제 화면

DEFAULT / 서울 / Joungmin.Ko에서 인증 참조를 선택하고
`Java · Spring Boot Actuator` 유형으로 앱을 등록했다.

- 실제 health UP 확인.
- 메트릭 5종, 측정값 9행 확인: HTTP 누적 COUNT/TOTAL_TIME/MAX,
  GC COUNT/TOTAL_TIME/MAX, JVM 메모리, 플랫폼 스레드, 프로세스 CPU.
- 실제 스레드 덤프 22개, Java 스택 표시 확인.
- 브라우저에서 thread-dump.txt 다운로드와 임시 결과 삭제 실행.
- 인증 토큰을 임시로 잘못된 값으로 바꾸자 미확인·메트릭 없음 표시. 원래 값 복원.
- 원래 인증으로 다시 조회해 성공 확인.
- 인증 없는 Actuator 덤프 요청은 403으로 거부됨.
- MiB·CPU %, 공통 CSS 덤프 스크롤 영역을 최종 화면에서 확인.

덤프 파싱에서 임의 응답 필드는 버리고 스레드 이름·ID·상태·락·스택만 표시한다.
자동 테스트는 누적 통계의 단위, 잘못된 메트릭/덤프 응답 거부,
다운로드 대상·주체·범위·만료 검사를 포함한다.

## 범위

이 검증은 로컬 실제 Java 예제다. 원격 VM/OKE 터널 경로의 Java 진단,
Python 수집, Pyroscope, 전체 힙 덤프, JFR, 시계열 p95/p99는 아직 검증하지 않았다.
상태표에서 미연결·미지원 기능을 완료로 표시하지 않는다.

원본 로컬 증거: `.tools/runtime-diagnostics/`.
