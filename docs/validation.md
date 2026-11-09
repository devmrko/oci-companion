# CSS 컴포넌트·반응형 검증 — 2026-10-26

Redmine #987. 전역 모바일 `nav` 스타일이 사이드바를 가로 배치하는 문제를 수정했다.
입력·버튼·폼·표·본문·네비게이션의 클래스를 모든 템플릿에 적용했다.
상단 Grid 배치와 실제 높이 기반 모바일 메뉴 위치를 적용했다.
공통 클래스 사용법: [UI 컴포넌트](design/ui-components.md).

자동 테스트 21개 통과. 실제 DEFAULT 연결로 리소스/소스/빌드/도움말의
320/390/768/1024/1440px 표시를 확인했다. 컨트롤 겹침·페이지 가로 넘침 없음.
모바일 메뉴 세로 배치와 상단 아래 위치 확인. 390px 스크린샷 육안 검토.
8091 서버 반영. OCI 생성·변경 없음.

후속 정렬 수정: 이전 검사는 겹침만 확인해 로고/상단 높이와 폼 컨트롤 높이 차이를 놓쳤다.
로고와 상단 높이를 공유하고, 일반 입력/선택/버튼 42px, 상단 컨트롤 34px로 통일했다.
브라우저 검사에 높이 및 하단 기준선 비교를 추가해 4화면/5너비에서 통과했다.
사용자 캡처 수신 후 헤더 경계선과 과도한 입력 너비를 추가 수정했다.
헤더/로고는 CSS 공통 높이, 상단 선택 영역은 최대620px, 일반 필터는240px 기준으로 제한했다.
캡처 크기인2048px를 추가한 4화면/6너비 검사와 스크린샷 대조를 통과했다.

---

# 공통 범위 선택 검증 — 2026-10-26

Redmine #986. 자동 테스트 21개 통과.

- 상단 리전·구획 선택을 리소스/소스/빌드/도움말에서 공유. 화면별 선택기 제거.
- `SelectionStore`와 JSON 어댑터. 기본 저장 위치 `.data/catalog/settings`.
- 프로파일·tenancy·사용자·실서비스/테스트 구분별 해시 파일. 스키마 버전 1.
- 재접속 시 구독 리전·접근 가능 구획 목록과 대조해 복원.
- 손상 파일 또는 접근 불가 선택은 안내 후 재선택까지 리소스/빌드 조회 중지.
- 디렉터리 0700, 파일 0600, 원자적 교체. 저장 실패 시 기존 세션 선택 유지.
- 범위 변경 시 현재 메뉴로 돌아가며 이전 필터·파이프라인·실행 선택 해제.
- 미등록 소스 입력이 있으면 변경 전 확인. 등록된 소스·캐시는 보존.

실제 DEFAULT 프로파일 Chromium: 서울/Joungmin.Ko 선택, 메뉴 이동,
연결 해제 후 재접속 복원, 다른 리전 전환과 이전 실행 선택 해제,
작성 중 입력 확인 취소, 모바일 390px 표시 검증 통과. OCI 리소스 생성·변경 없음.
새 버전으로 8091 서버 재시작 완료. 저장은 로컬 단일 프로세스 기준이며 다른 PC와 동기화하지 않는다.

---

# 범위·필터 검증 — 2026-10-26

Redmine #983. 자동 테스트 18개 통과.

- 하위 구획 계산과 임의 범위 거절.
- 소스 화면 유지, 프로젝트·이름·상태 필터, 확장 범위의 자동 갱신 해제.
- 구획별 갱신 API 대상 검증, 실패 시 이전 캐시 유지.
- 외부 Git의 리전 독립성, 프로파일 격리, 기존 파일 이전·중복 방지·삭제.
- 기존 인증·CSRF·legacy 회귀 검증.

실행 JAR를 재빌드가 덮어써 클래스 로딩이 실패하는 문제를 확인했다.
run.sh에서 실행용 고유 복사본을 사용하도록 수정했다. 실제 서비스 검증은 수정 후 재실행해 통과했다.

DEFAULT/us-ashburn-1 실제 전체 조회: 접근 가능한 구획 307개, 프로젝트 3개, 저장소 5개.
307개 캐시가 이번 조회 시각으로 갱신됨. 클라우드 생성·변경 없음.
Chromium 전체 조회·이름/프로젝트 필터·메뉴·모바일 검증 통과.
다른 리전 및 빌드·배포 실행은 이번 검증에 포함하지 않음.

```sh
# 기본: 선택 구획 갱신 + 캐시 기반 전체 목록/필터 확인
COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-smoke.cjs
# 명시적으로 전체 구획 갱신까지 수행
COMPANION_TEST_PROFILE=DEFAULT COMPANION_TEST_ALL_SCOPES=1 node scripts/browser-smoke.cjs
```

---

# 실제 서비스·메뉴 검증 — 2026-10-26

Redmine #982. DEFAULT 프로파일로 실제 OCI SDK 인증·리전·구획·DevOps 조회 성공.
범위: us-ashburn-1, 루트 구획. DevOps 결과 0건. 클라우드 생성·변경 없음.
데모 어댑터는 src/test로 이동. 배포 JAR에 DemoCloud/DemoResourceCatalog 없음.
자동 테스트 15개 통과. 아래 이전 데모 검증은 당시 기록이며 현재 실행 방법이 아님.

```sh
./scripts/build.sh
./scripts/run.sh
COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-smoke.cjs
```

실제 서비스 브라우저 검증: 인증·목록 갱신, 좌측 그룹 메뉴, 접기/펼치기,
소스·도움말 이동, 모바일 메뉴, 로그아웃. 소스 등록·클라우드 쓰기는 수행하지 않음.
프로파일 변수를 생략하면 로그인 화면만 검증한다.
스크린샷과 실제 리소스 캐시는 Git에서 제외한다.

---

# 검증 — 2026-10-26

리소스 개편: `./scripts/build.sh` 통과. 테스트 15개(기존 10 + 신규 5).

- 기본 모드 DataSource 빈 없음. legacy SQLite 회귀 테스트 통과.
- 프로파일·사용자·리전·구획별 캐시 분리와 재시작 후 읽기.
- 조회 실패 시 이전 스냅샷·시각 유지. 캐시 손상 복구.
- 소스 파일 손상 시 덮어쓰기 차단. 소스 등록 검증·중복 처리·캐시와 분리 저장.
- CSRF·세션 종료·이전 화면 범위 불일치 차단.

Playwright Chromium: 연결 → 조회 → OCI/GitHub 소스 등록 → 리전 전환 → 도움말 → 연결 해제 통과.
1440px/390px 확인. 모바일 문서 가로 넘침·JS 오류 없음.

```sh
PORT=8092 COMPANION_DATA_DIR=.tools/catalog-smoke ./scripts/run.sh --spring.profiles.active=demo
COMPANION_TEST_URL=http://127.0.0.1:8092 node scripts/browser-smoke.cjs
```

브라우저 의존성 설치는 아래 이전 검증 명령 참조. 스크린샷은 `.tools`에 저장.
실제 OCI 연결·권한·페이지네이션 응답·빌드·배포는 미검증.
주기 갱신은 화면 타이머 구현이며, 실제 주기를 기다리는 장시간 검증은 하지 않음.
후속: 지정 프로파일의 읽기 전용 조회를 콘솔과 대조하고, Git 인증·빌드·VM 배포를 별도로 검증.

---

# Validation — 2026-10-21

`./scripts/build.sh`: passed, Java 21, pinned Foundation source built locally,
application packaged as an executable Spring Boot JAR.

10 integration tests passed using actual temporary SQLite databases and an explicit demo
provider. Covered anonymous access, CSRF, host restriction, logout invalidation, invalid
profile rejection, page rendering, project update/persistence, scope isolation, unsafe
repository URLs, duplicate request tokens, concurrent submissions, uncertain provider
outcomes without retry, stale pipeline selection and unsupported deployment stages.

Playwright Chromium desktop (1440px) and mobile (390px): profile connection, project form,
build submission/status refresh, VM demo submission, pipeline list and all six help
sections passed. No JavaScript errors or mobile document overflow. Visual inspection of
login and project screenshots completed. Browser required macOS sandbox escalation for
its Mach port; no cloud resources were used.

Reproduce browser checks with the demo server already running on port 8091:

```sh
npm install --prefix .tools/browser --no-audit --no-fund playwright@1.49.1
# Install Chromium with .tools/browser/node_modules/.bin/playwright install chromium if needed.
node scripts/browser-smoke.cjs
```

The smoke script requires the DEMO profile and demo banner before writing sample data.
It adds one demo project per run; screenshots are kept under ignored `.tools/`.

OCI SDK adapters compile against 3.94.1. **No live OCI authentication, IAM, build or VM
deployment test has been performed.** Demo success is not evidence of cloud execution.
Generated build specs are starter templates, not verified builds of arbitrary repositories.
Functions and OKE adapters, resource provisioning, live log rendering, artifact promotion
and rollback are outside this first milestone.

Official references checked for help/specification wording:

- https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm
- https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm
- https://docs.oracle.com/en-us/iaas/Content/devops/using/deploy_instancegroups.htm
- https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deployment/create-pipeline-deployment.html

The remaining real-environment acceptance test is to connect an API-key profile with
the necessary permissions, map pre-existing build and VM pipelines, verify their source,
artifact and target settings, then execute a build and deployment and compare remote
status with the OCI console. Private Git authentication and VM agent/network readiness
must be prepared in OCI first.


## agent-operations 머지 전 재검증 — 2026-11-09

- `./scripts/build.sh`: Java 154개 통과, 실패·오류·건너뜀 0.
- `/opt/homebrew/bin/python3 -m unittest discover -s tests -v`: Python 3.14.7에서 15개 통과. macOS 기본 Python 3.9는 tomllib이 없어 사용할 수 없었다. 대상 Python 3.12 컨테이너 재검증은 이번 범위가 아니다.
- `COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-merge-review.cjs`: 최신 빌드를 별도 8092 서버에서 실제 Chromium으로 확인. DEFAULT / ap-seoul-1 / Joungmin.Ko.
- 환경 목록·OKE 상세·배포 파이프라인 목록과 OKE 대상 확인, Functions 화면, 에이전트 및 등록 앱 화면을 조회했다. 390/768/1440px 검사에서 등록 앱 표의 넘침을 발견해 기존 table-scroll·table-cell·form-field·ui-control 클래스를 적용했다.
- VM 대상은 실제 STOPPED여서 실행 준비가 거부됐다. 부정확한 저장소 URL 오류 대신 VM 실행 상태 확인 안내로 수정했다. 대상 VM을 시작하거나 배포를 실행하지 않았다.
- 이 호스트에는 등록 앱 인증·터널 설정이 없어 앱 연결의 미설정 화면을 검증했다. 실제 앱 health·Pod·노드 상태와 Python Function 호출은 이번에 재검증하지 않았다. 이전 세션의 실환경 검증 기록과 구분한다.
- 원격 실행·인프라 변경 없이 현재 조회 조건을 검증했다. 화면 스크립트는 확인한 배포 실행 버튼을 누르지 않는다. 테스트용 8092 서버와 데이터 디렉터리는 기존 8091 앱과 분리했다.
