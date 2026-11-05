# 빌드 자동 실행

2026-10-27 · Redmine [#1002](https://redmine.cloud-handson.com/issues/1002)

## 실행 방식

| 방식 | 실행 주체 | 저장 위치 | 소스 버전 |
|---|---|---|---|
| 수동 | 앱에서 OCI API 호출 | OCI 실행 이력 | 작업에서 선택한 SHA 또는 실행 화면 입력 |
| Push / Pull Request | OCI DevOps Trigger | OCI 프로젝트 | 이벤트 소스 버전. 스크립트 안의 별도 고정 checkout은 그대로 적용 |
| 시간 예약 | Resource Scheduler → 독립 Function → DevOps | OCI Schedule + Function 설정 | 파이프라인 기본 브랜치 최신 버전·기본 파라미터 |

앱 프로세스나 로컬 JSON을 예약 실행기로 사용하지 않는다. 앱을 닫아도 OCI 자동화는 계속 동작한다. 로컬 작업의 고정 SHA·프리셋을 자동화에 반영했다고 표시하지 않는다.

## 화면과 API

`빌드 → 자동 실행`에서 현재 프로파일·리전·구획의 파이프라인을 선택한다.

- 소스 트리거: 실제 목록·조건 조회, OCI/GitHub/GitLab 생성. Push, PR 생성·변경·병합 지원. 브랜치 조건 입력. 파일 필터·대상 브랜치·수정·삭제는 OCI 콘솔 안내.
- 외부 Webhook: 생성 응답의 URL·Secret을 해당 저장소에 설정한다. Secret은 캐시 금지 응답에만 표시하며 세션·로컬 JSON에 저장하지 않는다. 이 단계를 완료하기 전 외부 자동 실행 준비 완료로 판정하지 않는다.
- 시간 예약: 현재 파이프라인과 연결된 실행 요청 Function 선택, UTC 숫자 5필드 cron, UTC·한국시간 3회 미리보기, 생성·중지·활성화. 날짜와 요일 동시 제한은 서로 다른 cron 해석을 피하기 위해 거부한다.
- 목록의 예약은 `build-pipeline` 태그가 현재 파이프라인인 항목으로 한정한다. 모든 OCI 일정을 관리하는 화면은 아니다.
- IAM/조회 실패와 리소스 없음 구분. 생성 전 명시적인 자동 실행 동의 체크. 범위·파이프라인 revision 재조회, 세션 단일 사용 티켓, CSRF, 일정 상태 변경 ETag 검증.

BuildAutomation 포트와 OciBuildAutomation 어댑터를 분리한다. SDK 3.94.1의 ScheduleClient 및 DevopsClient를 사용한다. 서비스 API 호출은 실제 OCI를 사용하며 런타임 샘플 응답은 없다.

## 독립 Function

다운로드 ZIP: Dockerfile, func.py, func.yaml, README. Fn CLI로 사용자가 배포하고 재조회해 선택한다. Python3.12, 고정 SDK/FDK 버전, Resource Principal 사용. 앱 API·DB·사용자 키에 의존하지 않는다.

현재 템플릿은 DevOps 파이프라인 실행 요청용이다. Maven 빌더 Function 자체를 예약 실행하는 UI는 별도 범위다. 런타임 설정의 BUILD_PIPELINE_ID, BUILD_REGION, BUILD_RUNNER_CONTRACT로 대상이 지정된다.

실행 중 ACCEPTED/IN_PROGRESS/CANCELING 빌드가 있으면 건너뛴다. 이 조회와 새 실행 제출은 원자적이지 않다. 여러 일정의 동시 실행을 완전히 배제하는 분산 잠금은 제공하지 않는다. SDK 자동 재시도는 끄고 Fn 호출 ID에서 안정적인 retry token을 만든다. 응답 유실은 이력 확인 후 처리한다. Function 접수/완료는 DevOps 빌드 성공이 아니다.

## 빌드 흐름 보완

`빌드 → 환경 관리 → 빌드 구성` 또는 새 작업의 빌드 설정 단계에서 진입한다. 등록 소스를 전달하고 원래 창의 미저장 입력을 유지한다. 명세 생성·프로젝트·파이프라인·Registry·실행 결과로 이어지는 링크를 제공한다.

Maven/Gradle 실행은 wrapper/system 선택, 일반 환경 변수와 Vault OCID 참조 분리, 파일/이미지 출력 유형을 명세에 반영한다. 비밀 문자열은 입력하지 않는다. Python/npm 선택 시 Java 전용 필드를 숨긴다. Git 저장·런타임 준비·보고서 수집은 [명세 저장 설계](build-specification-delivery.md)에서 후속 구현했다. 컨테이너 빌드 명령 생성은 별도 범위다.

DevOps 실행 상세에서 현재 상태·범위·ETag를 검사한 취소 요청을 추가한다. 취소 접수와 최종 상태를 구분한다. 새 실행 설정/자동 실행/실행 로그 진입을 연결한다.

## 검증과 제한

- Java 테스트 69개 통과. 자동화 폼 렌더링, CSRF·티켓 재사용 차단, 조회 오류 구분, UTC cron, 환경 변수·Vault 참조 검증 포함.
- 독립 Python 오케스트레이션 테스트 3개: 실행 중 건너뜀, 기본 파라미터·안정적 토큰, 권한 실패 시 제출 차단. 단위 테스트에서만 API 호출을 대체한다.
- DEFAULT / ap-seoul-1 / Joungmin.Ko의 실제 DevOps 트리거·Schedule·Function 조회 성공. 해당 파이프라인의 트리거·연결 예약·실행 요청 Function은 없음으로 확인.
- 실제 브라우저: 네 빌드 도구 명세 미리보기, cron 미리보기, ZIP 다운로드, 소스 인계·원래 초안 유지, 320/390/768/1024/1440/2048px 가로 넘침·JS 오류 확인.
- 스크린샷 검토에서 긴 도움말의 아이콘 CSS 오용을 발견해 일반 접기 컴포넌트로 수정.
- 이번 검증에서 실제 트리거/일정 생성·활성화, Function 배포, 빌드·취소는 호출하지 않았다. 예약 시간에 자동 실행되는 통합 검증은 미완료다.
- 기존 db-companion 파이프라인 스크립트의 고정 SHA는 변경하지 않았다. 애플리케이션 소스와 명세 저장소 분리 문제, Git 쓰기·런타임 선택·보고서 집계는 후속 구현에서 보완했다. 외부 OCI 네이티브 연결 구성과 완전한 재실행 복원은 남아 있다. #1002 및 기존 #996 전체를 완료로 판정하지 않는다.

## 근거

- [OCI 트리거](https://docs.oracle.com/en-us/iaas/Content/devops/using/trigger_build.htm)
- [Functions 예약](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsscheduling.htm)
- [Resource Scheduler 생성](https://docs.oracle.com/en-us/iaas/Content/resource-scheduler/tasks/create-manage.htm)
- [빌드 명세](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm)
- [DevOps IAM](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_iampolicies.htm)

## 실제 검증 보완 — 2026-11-02

- 사용자 승인 [CI/CD 품질 기준](cicd-quality-criteria.md)에 따라 재사용 빌더와 자동 실행을 연결했다.
- 전용 파이프라인 `companion-builder-automation-validation`, OCI Git 브랜치 `validation/builder-automation-20261102`. 기존 업무 파이프라인의 트리거 조건은 변경하지 않았다.
- Go 1.27.3 빌드·테스트, Ruby 3.4.12/Bundler/Rake HTTP 3건, 독립 dispatcher 이미지 생성 및 보고서 전달을 수행한다. 보고서는 실행에서 생성한 REPORT_VERSION으로 저장해 재실행 충돌을 피한다.
- 수동 검증과 실제 Git Push 검증 성공. Push 실행의 OCI 원본 정보에 trigger ID 및 커밋 `8de217a1662ab3528a87c752045928fbcd8fc2a0`가 기록됐다.
- 실행 상세에 요청 종류·소스·브랜치·요청 커밋을 표시한다. 수동/API 실행에 커밋 정보가 없으면 로컬 작업의 SHA로 채우지 않는다. 다운로드한 소스는 실행 로그에서 확인한다.
- 독립 dispatcher Function 실제 호출에서 실행 중인 빌드를 감지해 SKIPPED 응답 확인. 시간 예약 통합 검증은 아래 결과에 별도 기록한다.
- OCI Scheduler는 최소 1시간 간격이다. 앱은 분 필드를 단일 0–59 값으로 제한하고, 분 반복·목록·범위를 API 호출 전에 거부한다. 생성 중인 예약의 활성화 버튼도 비활성화한다.
- 최초 Push 실행은 이벤트 처리 지연 후 생성됐다. 뒤늦게 추가했던 트리거 전용 정책은 실행 생성 시각이 정책 생성보다 앞섰음을 확인하고 삭제했다. 정책 부족을 원인으로 확정하지 않는다.
- 검증 범위는 OCI Git Push이다. GitHub/GitLab Webhook·PR 이벤트를 검증 완료로 확대하지 않는다.

### 검증 방식

- Playwright의 실제 Chromium(headless): 프로파일·범위 선택, 빌더 명세 생성, 트리거·예약 생성, 보고서 집계, 소스 버전 표시, 시간 입력 오류, 공통 클래스 배치.
- Python OCI SDK: 검증용 파이프라인·Function·IAM 준비, API 상태·이벤트 출처·저장소 결과 대조, 시험 일정 중지. 리소스 준비 전체를 화면에서 수행했다고 주장하지 않는다.
- 실제 이벤트: OCI Git Push와 Resource Scheduler 시간 도달. API 수동 실행을 자동 실행 성공으로 대체하지 않는다.
- 단위 테스트만 API/컨테이너 명령을 대체한다. 앱 서비스와 통합 테스트는 실제 OCI를 사용한다.
- 재실행용 UI 검사: `scripts/browser-builder-settings.cjs`. `COMPANION_TEST_PROFILE=DEFAULT`, `COMPANION_TEST_PIPELINE`, `COMPANION_TEST_RUN`, `COMPANION_TEST_TRIGGER_RUN`, `COMPANION_TEST_COMMIT`에 검증 리소스를 지정한다. OCI 리소스 생성은 하지 않는다. 보고서 기대값은 Ruby HTTP 예제 3건이다.

### 통합 검증 완료

2026-11-02 DEFAULT / ap-seoul-1 / Joungmin.Ko. 세 실행 모두 Managed Build·Deliver Artifacts SUCCEEDED.

| 경로 | 실제 Build Run OCID |
| --- | --- |
| 수동 기준 실행 | `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yianhql652czpns6rantuwmp44zijvmugzmj77lzuy4w72q` |
| OCI Git Push | `ocid1.devopsbuildrun.oc1.ap-seoul-1.aaaaaaaa6p7hdvlam4eefeqh3e7jc3y4f3qoqum76qxsbr4jzoufr2oft5lq` |
| Resource Scheduler → Function | `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiarb6iuwv4lhkowpv2qzp3j3jcgp7nixjpvb4fiomp4sna` |

- 시험 예약 `builder-validation-schedule`, `51 * * * *`: 07:51:45 UTC 호출 성공, 예약 빌드 08:00:01 UTC 성공. 반복 실행을 막기 위해 빌드 생성 확인 직후 예약 중지, 최종 INACTIVE 확인.
- Function `devops-build-dispatch`는 앱·로컬 DB·사용자 키 없이 Resource Principal로 호출했다. 직접 호출은 실행 중 빌드 감지 SKIPPED, Scheduler 호출은 새 빌드 생성으로 각각 확인.
- 수동·예약 실행의 실제 브라우저에서 JUnit 3건 통과와 로그 확인. Push 실행은 OCI 원본 trigger/commit 정보와 화면 표시를 대조했다.
- Java 테스트 84개, 실제 폼 390/768/1440px 및 예약 입력 오류 안내 통과. SDK 검증과 화면 검증의 범위는 위에 구분했다.
- Function principal 권한은 해당 구획의 빌드 실행 조회·읽기·생성에 한정한다. 대상 파이프라인은 Function 설정에서 지정한다. Scheduler principal은 해당 Function 조회·호출로 제한한다.
- 검증용 파이프라인은 빌더 검사와 dispatcher 이미지 생성·전달을 함께 수행했다. 일반 사용자 빌드마다 dispatcher를 배포할 필요는 없으며, 예약용 Function 배포는 환경 준비 단계이다.
