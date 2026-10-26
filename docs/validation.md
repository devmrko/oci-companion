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
