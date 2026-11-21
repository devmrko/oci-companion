# Java·Python 메트릭과 실행 진단

2026-11-21 사용자 승인 설계. 기능 지원표와 실제 수집 결과를 구분한다.

## 수집 기준

| 영역 | 항목 | 방식 |
|---|---|---|
| API | RPS, 오류율, p50/p95/p99, 처리 중 요청 | 시계열·히스토그램 |
| 의존 호출 | DB·외부 API 지연/오류, 연결 풀·큐 대기 | APM·프레임워크 계측 |
| 프로세스·컨테이너 | CPU, RSS, throttling, OOM/재시작, 디스크·네트워크 | 런타임·플랫폼 |
| Java | Heap/Non-heap, GC, 플랫폼/가상 스레드, 스레드 풀 | JVM·프레임워크별 지원 확인 |
| Python | 워커별 CPU/RSS, GC, 스레드, 이벤트 루프 지연, 작업 큐 | 런타임·프레임워크별 계측 |
| 운영 | 성공률·응답시간 SLO, 배포 버전별 변화 | 시계열 비교 |

COUNT/TOTAL_TIME은 누적 통계다. RPS나 p95/p99로 표시하지 않는다.
조회 실패·미제공은 0이 아니다. 샘플링·수집 주기·조회 시각을 구분한다.

## 진단 기능

| 기능 | Java | Python |
|---|---|---|
| 스레드/스택 덤프 | jcmd 또는 Actuator | py-spy/faulthandler |
| 전체 힙 덤프 | HPROF | 동등한 표준 HPROF 기능 없음 |
| 할당 분석 | JFR/async-profiler | tracemalloc 등 |
| CPU 프로파일 | 런타임 프로파일러/Pyroscope | 런타임 프로파일러/Pyroscope |
| 대기·락 | 덤프/JFR/지원 프로파일러 | 도구별 범위 확인 |

프로파일은 전체 힙 덤프가 아니다. Python 스레드 덤프는 모든 asyncio Task의
상태를 보장하지 않는다. Java 가상 스레드는 대상 버전과 수집 방식별로 확인한다.
힙 덤프 자동 수집은 기본 제외한다. 큰 파일·중단 영향·메모리 내 비밀값 때문에
별도 권한, 저장·보관·삭제 정책이 필요하다.

## 구조와 화면

- 기본 수집: OpenTelemetry → 기존 OCI APM 또는 외부 저장소.
- 프로파일 후보: Pyroscope. eBPF/Java/언어 SDK에 따른 지원 차이 표시.
- 기존 연결 우선, 신규 환경에는 독립 실행 템플릿 제공.
- 메뉴: 등록 앱 상태 → 메트릭·진단 → 기능별 상태·메트릭·요청 시 덤프.
- 상태: 수집 확인 / 설정 확인 / 준비 필요 / 미확인 / 미지원.
- 도구 이름만 보고 사용 가능으로 판정하지 않는다.
- 웹과 향후 LLM은 같은 Java `RuntimeDiagnostics` 포트를 호출한다.

## 첫 구현 범위

Spring Boot Actuator의 실제 metrics와 threaddump API를 조회한다.
기존 프로파일·리전·구획별 앱 레지스트리, 인증 참조, 제한된 HTTP transport를 재사용한다.
FoundationHttpClients와 Python Foundation을 검토했다. 앱 고유의 OCI 범위 검사와
진단 결과 계약은 이 프로젝트에 두며 범용 HTTP 클라이언트를 새로 만들지 않는다.

- 연결 유형 `spring-actuator-v1`, 경로 `/actuator` 고정.
- 현재 loopback 터널 + Bearer 인증 지원. 인증값을 UI에 입력·노출하지 않음.
- 5개 메트릭을 지원 목록에서 선택해 제한된 병렬 조회. 최대 응답 2MB.
- discovery의 threaddump 표시는 설정 확인일 뿐, 실제 실행 성공은 수집 후 판정.
- 스레드 덤프는 POST·CSRF로 수집. 스택·상태·락 필드만 표시.
- 세션에 최근 1건만 보관, 다운로드 유효기간 10분, 임시 결과 삭제 제공.
- 전체 힙 덤프·JFR·Pyroscope·Python 수집 어댑터는 후속 구현.

## 근거

- https://opentelemetry.io/docs/specs/semconv/runtime/
- https://docs.spring.io/spring-boot/api/rest/actuator/threaddump.html
- https://grafana.com/docs/pyroscope/latest/configure-client/profile-types/
- https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html
- https://docs.python.org/3/library/tracemalloc.html
