# 빌드 화면 검증

2026-11-02. DEFAULT / ap-seoul-1 / Joungmin.Ko.

## 실행 방법

앱 실행 후 전용 작업 `Go·Ruby 빌더 · 자동 실행 검증`을 사용한다.

```sh
COMPANION_UI_BUILD_WRITE=1 node scripts/browser-build-ui-run.cjs
```

실제 OCI 빌드를 1회 실행하고 기존 검증용 Registry에 산출물을 전달한다. 사전에 생성된 전용 소스·파이프라인·저장소·권한이 필요하다.

검증 경로:

1. 로그인 화면에서 DEFAULT 선택.
2. 상단에서 서울 리전·Joungmin.Ko 구획 적용.
3. 빌드 메뉴 → 검증 작업 → 설정 확인·실행.
4. 소스·검증 브랜치·최신 커밋 선택, 빌드·저장 목적 및 실행 이름 입력.
5. 확인 창의 소스·저장 위치 확인 → 빌드 실행.
6. 실행 상세에서 빌드·테스트와 Registry 전달 성공 확인.
7. 전달된 이미지·보고서, 요청 커밋, 실행 로그, 테스트 3건 통과 확인.
8. 이미지·태그 조회 → 검증용 Registry → v1의 Available 상태와 digest 확인.

Playwright headless Chromium이 실제 화면을 조작한다. OCI CLI/SDK 직접 호출, 앱 API 직접 호출, 네트워크 응답 대체는 사용하지 않는다. 앱 내부 SDK 호출은 실제 서비스를 사용한다. 진행 상태는 브라우저 새로 고침으로 확인한다.

캡처·결과는 `.tools/ui-flow-*.png`, `.tools/ui-flow-run.json`, `.tools/ui-flow-result.txt`에 남긴다. 실패 후 재실행 전에는 실행 이력에서 기존 요청의 접수 여부를 확인한다.

## 발견한 결함

소스와 브랜치를 지정하고 ‘최신 커밋’을 선택하면 `commitInfo.commitHash`가 비어 OCI 실행 요청이 실패했다. 선택한 브랜치의 최신 SHA를 조회해서 전달하도록 수정했다. 커밋을 조회할 수 없으면 실행 전에 차단한다. 명시적으로 선택한 SHA는 유지한다.

근거: [OCI CommitInfo 필수 필드](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/devops/models/oci.devops.models.CommitInfo.html).

또한 OCI의 컨테이너 전달 기록에 `imageUri`와 `deliveredArtifactHash`가 없어 결과 화면이 빈칸이었다. 기록 미제공을 표시하고 공통 버튼 클래스로 Registry 조회 링크를 추가했다. 현재 Registry 태그의 digest를 과거 실행의 digest로 대체하지 않는다.

## 결과

- 실행: `ui-flow-2026-11-02T08-14-03-151Z`.
- 실행 OCID: `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiagyd62oip4husxsgmmkrtfzgz7erk4frlzrztc3eec63a`.
- 빌드·테스트 및 Registry 전달: 모두 SUCCEEDED. Ruby HTTP 보고서 3건, 실패·오류 0. Go 빌드·테스트 성공 로그도 확인.
- 요청 커밋: `8de217a1662ab3528a87c752045928fbcd8fc2a0`. 소스·브랜치·최신 커밋을 화면에서 선택해 실행했다.
- 보고서: `automation/test-reports.tar.gz`, 버전 `run-8a310032-036a-4b64-aad6-613d656177ab`.
- Registry 화면: `companion/devops-dispatch-validation:v1`, Available, 생성 `2026-11-02T08:21:05.523Z`. 조회 시 digest `sha256:8918c2a853e1e1bce16b36943709e3d13479aee056489378d83db1a0f55de2644`.
- 진행 중 로그 자동 갱신·검색, 완료 후 테스트 집계·로그·이미지 태그/digest 펼치기 통과. Logging 반영 지연 중에는 특정 테스트 출력 검색이 일시적으로 비어 있었고, 이후 실제 출력 확인.
- 화면 결함 수정 후 앱을 재시작하고 같은 실행을 이력 메뉴에서 다시 열어 최종 표시를 확인했다. 중복 빌드는 실행하지 않았다.
- 보조 회귀 검사: Java 84개 통과. 최신 커밋 SHA 전달·조회 실패 시 실행 차단을 포함한다.

## 검증 범위

기존 작업의 실행 흐름을 검증한다. 소스 저장소·프로젝트·파이프라인·IAM을 처음부터 화면으로 생성하는 전체 온보딩 검증은 별도다. Go·Ruby 설정 화면의 빌더 선택·명세 미리보기는 별도로 확인했다. 이 미리보기 결과를 이번 실행에서 새로 Git에 저장하지는 않았다.

## Maven Goal·PMD 추가 검증

화면: 빌드 → `db-companion 검증 · Functions` → 설정 확인·실행. 기존 ARM `maven-builder-998`을 사용했다. 이 결과는 OCI Managed Build의 Maven 경로 검증과 구분한다.

소스는 `devmrko/db-companion`, 커밋 `7a5cb34b68d8dae90f9c398667a0db976bb2f6d2`. 이 버전의 pom.xml은 `quality` 프로파일의 `verify` 단계에 Maven PMD Plugin 3.28.0 `check`를 연결한다.

| 화면 설정 | 실제 실행 명령의 끝부분 | 화면에서 확인한 결과 |
| --- | --- | --- |
| Goal `test`, 프로파일·산출물 경로 비움 | `clean test` | SUCCEEDED, 테스트 1,444건·실패/오류/건너뜀 0, PMD 미실행, JAR 없음 |
| Goal `verify`, 프로파일 `quality`, 산출물 `target/*.jar` | `-Pquality clean verify` | SUCCEEDED, 테스트 1,444건·실패/오류/건너뜀 0, `pmd:3.28.0:check` 실행 성공, JAR 저장 |

- test 실행 ID: `8bfcd094-4617-40d7-bda5-7962095ced95`.
- verify 실행 ID: `59e06df0-8f03-4634-97e3-2658fed699f3`.
- JAR: `db-manage-companion-0.1.0-alpha.17.jar`, 61,111,723 bytes, SHA256 `42dfa20220c131fab428690e042e21d980599e428330175338fc7fc7c8e67101`.
- 화면의 JUnit 집계 1,444건과 Maven 최종 로그의 1,444건이 일치했다.
- PMD 실행은 로그로 검증했다. 이후 아래 보고서 표시·실패 경로 검증을 추가했다. `package` 단독 실행은 아직 검증하지 않았다.
- 실시간 실행 상세는 자동 갱신되며 하단 실행 이력 목록은 새로 고침이 필요하다.

재현: `COMPANION_UI_BUILD_WRITE=1 node scripts/browser-maven-goals.cjs`. 기존 작업에서 두 번의 실제 Function 빌드를 실행한다. 화면 조작만 사용하며 `.tools/ui-maven-*`에 실행 ID·로그·산출물 정보·캡처를 남긴다.

## PMD 보고서 표시

실행 상세 → **보고서 보기** → **PMD 보고서**. Functions와 OCI DevOps가 같은 보고서 컴포넌트를 사용한다.

- 위반 수·분석 오류 수·보고서에 기록된 제외 항목 수, 파일·줄·규칙·우선순위·내용 표시.
- XML 원문 보기와 다운로드. 위반 목록은 500건, 오류 내용은 100건까지 표시하며 전체 내용은 XML로 확인한다.
- 보고서 없음과 위반 0건을 구분한다. JUnit 테스트 수와 PMD 위반 수를 합산하지 않는다.
- HTML 실행 대신 PMD XML을 읽고 화면의 텍스트·표로 표시한다. 외부 XML 엔티티는 차단한다.
- 신규 DevOps 명세와 Functions 템플릿도 `pmd.xml`을 보고서 압축 파일에 수집한다. 기존 배포된 Function·Git 명세를 자동 교체하지 않는다.

실제 검증: 위 verify 실행의 `target/pmd.xml`(PMD 7.17.0)을 조회해 위반 0·분석 오류 0, XML 원문과 다운로드 파일 일치를 확인했다. test 실행은 PMD 보고서 없음으로 표시했다. 기존 DevOps Ruby 3건 보고서도 조회했다. 390/768/1440px 화면 확인. 재현 스크립트: `COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-pmd-reports.cjs`(실제 조회만 수행).

보조 검사: Java 86개, Python 4개 통과. PMD 위반·분석 오류·제외 항목 파싱과 실패 시 PMD 파일 보존은 단위 테스트로 검증했다. 실제 위반 사례는 아래와 같다.

형식 근거: [PMD XML 보고서](https://pmd.github.io/pmd/pmd_userdocs_report_formats.html).

## PMD 실패 경로 — 2026-11-02

전용 Function `maven-builder-998`의 기존 0.0.1 빌드 소스는 성공 후에만 보고서를 저장했다. 최신 독립 템플릿을 반영한 ARM 이미지 `0.0.2-pmd-reports`로 이 검증용 Function을 갱신했다. digest: `sha256:039e6e846e2f78b0917f4197c047db29f8e1b6d434682b1af2a5373e20c5b560`. Function의 버킷·권한·기타 설정은 유지했다.

소스: [격리된 테스트 모듈](https://github.com/devmrko/db-companion/tree/6e93238821d4fc02e5a92f7e623ace2fdc2002d3/validation/pmd-failure). 브랜치 `validation/oci-companion-pmd-failure-20261102`, 커밋 `6e93238821d4fc02e5a92f7e623ace2fdc2002d3`. main에는 반영하지 않았다.

화면에서 기존 Functions 작업의 커밋·작업 경로를 위 모듈로 지정하고 `verify` + `quality` 실행:

- 실행 `7b6fdf46-f52d-477c-8cdc-2879d144437a`: **FAILED**.
- JUnit: 테스트 1건, 실패·오류·건너뜀 0.
- PMD: 위반 1건, 분석 오류 0. `QualityFailureFixture.java:4`, `UnusedLocalVariable`, 우선순위 3.
- 실패 후에도 보고서 압축 파일이 Object Storage에 남고 화면에서 위반 표·XML을 조회했다. XML 다운로드 내용 일치 확인.
- 배포용 JAR는 게시되지 않았다. `verify` 중 로컬에 만들어진 JAR를 실패 후 게시하지 않는 것을 확인했다.
- 390/768/1440px 화면에서 가로 넘침 없음.

초기 예제 커밋 `4f3800296146d4afef6dd64aed63c063af612d59`은 변수명이 `unused`여서 PMD 제외 규칙에 해당했다. 실행 `7f7d3664-a992-4dd3-a2b3-1e895330438f`는 정상 성공했으며 실패 검증 근거에서 제외했다. `calculatedValue`로 수정한 위 커밋이 최종 검증 대상이다.

재현: `COMPANION_UI_BUILD_WRITE=1 node scripts/browser-pmd-failure.cjs`. 실제 실패 빌드를 1회 실행한다. 빌드 실행·상태·보고서 검증은 브라우저 화면으로 수행했다. 테스트 소스 준비는 GitHub API, 빌더 준비는 기존 로컬 ARM Colima/Docker, Function 갱신은 OCI SDK를 사용했다. 업로드 임시 토큰 삭제 및 검증용 로컬 Colima 중지 완료. 전체 환경 준비를 UI만으로 수행한 것은 아니다.

## 네이티브 명령·환경변수·보고서 저장 — 2026-11-03

화면: **빌드 → 새 파이프라인 구성 → 빌드 명세 → 고급 설정 · 명령·환경변수**. 직접 명령이 비어 있으면 기존 도구별 Goal·profile을 사용한다. 직접 명령이 있으면 이를 `steps[].command`로 저장한다. 환경변수는 `env.variables`, 비밀값 참조는 `env.vaultVariables`다. POM은 변경하지 않는다.

보고서 영역에서 선택 구획의 Object Storage 버킷 조회·생성·선택과 저장 접두어를 설정한다. 성공 시 `Preserve reports` 단계, 실패 시 OCI `onFailure`가 원본 XML 압축 파일을 업로드한다. 실행 상세 → **Object Storage 보고서**에서 버킷·접두어 선택 → **이 실행의 보고서 조회** → **Object Storage 보고서 보기**.

실제 DEFAULT / ap-seoul-1 / Joungmin.Ko 검증:

- 화면에서 비공개 Standard 버킷 `companion-build-reports-validation` 생성·선택.
- 고급 명령과 `BUILD_PROFILE=quality` 환경변수를 입력하고 Gitea 검증 저장소의 `native-report-build.yaml`에 비교·커밋. 명세는 앱 실행 파일에 종속되지 않는다.
- 전용 파이프라인 `companion-native-reports-validation`, 빌드 목록의 **일반 명령·환경변수 · 보고서 보관 검증** 작업으로 확인 가능.
- OCI 소스 브랜치 `validation/native-report-storage-20261103`. Maven 컨테이너에서 고정된 db-companion 테스트 모듈 커밋 `6e93238821d4fc02e5a92f7e623ace2fdc2002d3` 사용. 앱의 PMD 전용 설정 없이 일반 명령 `mvn -B verify -P"$BUILD_PROFILE"`로 실행했다.

| 실행 | 명세 커밋 | 빌드 결과 | 보고서 |
| --- | --- | --- | --- |
| `native-reports-failure-2026-11-03T01-17-57-883Z` | `877c2203fd317729026cccc9419ba7dbac4c9098` | FAILED | JUnit 1건 통과, PMD 위반 1건·오류 0. `onFailure` 성공, 원본 XML 조회·다운로드 일치 |
| `native-reports-succeeded-2026-11-03T01-24-52-831Z` | `cc709cc99bc606ea358838434b605f68622d1c1b` | SUCCEEDED | `test` 명령으로 JUnit 1건 통과. PMD는 실행되지 않아 보고서 없음으로 표시. 성공 보관 단계 완료 |

실패 실행 OCID: `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiars3xu2z64xfqpgtbq2radtzla5szwjy6bdyxgbz2wwya`.
성공 실행 OCID: `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiac6fespl47fm2ep3ayznfbajhpmhhxizbst72wru4sspq`.

두 실행 모두 **앱 화면으로 제출하고 결과·Object Storage 보고서·빌드 로그를 검증**했다. 390/768/1440px 가로 넘침 없음. 보고서 표·명령 입력·저장소 선택은 공통 CSS 클래스를 사용한다. 최종 Java 89개 검사와 JavaScript 구문 검사가 통과했다.

환경 준비 범위: 전용 OCI 파이프라인·빌드 단계·해당 파이프라인과 보고서 버킷으로 제한한 IAM 정책은 SDK로 준비했다. OCI Git 명세 복사는 기존 SSH 연결을 사용했다. 화면에서는 Gitea 저장, 버킷 생성·선택, 빌드 작업 등록, 실행·결과 조회를 수행했다. HTTPS 준비 과정에서 생성한 임시 Auth Token은 모두 삭제했다. 기존 VM·Function·다른 파이프라인은 변경하지 않았다.

재현:

- `COMPANION_UI_STORAGE_WRITE=1 node scripts/browser-report-storage.cjs`: 실제 버킷 조회·필요 시 생성, 명세 생성·반응형 화면 검사.
- `COMPANION_UI_STORAGE_WRITE=1 COMPANION_NATIVE_BUILD_VALIDATION=1 node scripts/browser-report-storage.cjs`: 추가로 실제 Gitea 검증 저장소에 명세 저장. `COMPANION_BUILD_GOAL=test`로 성공 명령 선택.
- 전용 검증 파이프라인 메타데이터 준비 후 `COMPANION_UI_BUILD_WRITE=1 node scripts/browser-native-report-run.cjs`: 실제 실패 실행·보관 검증. `COMPANION_BUILD_GOAL=test`는 성공 명세 커밋을 준비한 경우 사용한다.
- `COMPANION_REPORT_RUN_URL` 지정 시 새 실행 없이 해당 기존 실행의 보고서·로그를 검증한다.

한계: 정상 명령 실패와 성공을 검증했다. 강제 종료·시간 초과 시 보관은 보장하지 않는다. 현재 화면 집계는 JUnit·PMD XML에 한정한다. Functions 실행기 계약은 이번 변경 대상이 아니다.

## 기존 설정 조회·수정 — 2026-11-03

화면: **빌드 작업 → 명세 조회·수정** 또는 `/builds/settings`.

- OCI SDK로 파이프라인·단계·파라미터를 조회한다. 선택 단계의 주 소스·브랜치·명세 경로를 사용한다. OCI Git 명세는 프로파일 권한으로 읽으며 커밋 SHA를 함께 표시한다.
- 외부 Git은 등록 소스·브랜치·명세 경로와 Git 인증 참조로 읽는다. 새 명세 생성 화면의 기본값을 기존 설정으로 표시하지 않는다.
- 저장된 `Command` 단계, `onFailure` 명령, `env.variables`, `env.vaultVariables`를 입력 항목에 채운다. 파이프라인 파라미터 수정은 기존 OCI 기본값 편집 기능으로 연결한다.
- 변경 비교 전에 조회 시점의 Git 커밋과 OCI 단계 구성을 재확인한다. 저장은 기대 커밋·Git ref lease와 일회성 티켓을 사용한다. 저장 후 원본을 다시 읽어 화면을 채운다.
- 수정하지 않은 단계·입력·출력·exportedVariables 등은 유지한다. YAML 서식·주석과 앵커 표현은 달라질 수 있다. 변경이 없으면 원문을 그대로 유지한다.

실제 화면 검증: `COMPANION_UI_GIT_WRITE=1 node scripts/browser-native-settings.cjs`.

1. 기존 검증 작업에서 OCI 명세를 조회. `validation/native-report-storage-20261103` / `native-report-build.yaml` / 커밋 표시, 실제 Maven 명령과 실패 처리, `BUILD_PROFILE=quality` 입력 확인.
2. Gitea 검증 저장소의 같은 명세를 읽어 명령과 환경변수를 수정. 변경 비교 → 실제 커밋 → 새 커밋에서 재조회한 입력값 확인.
3. 검증용 명령·환경변수 변경을 복원하고 다시 원본 조회. 조회한 YAML을 파싱해 원래 설정과의 의미상 일치, 다른 단계·실패 처리·산출물 보존 확인.
4. 390/768/1440px 가로 넘침 없음. 브라우저 오류 없음.

OCI 경로는 실제 조회를 검증했고, Git 저장·재조회는 실제 Gitea에서 검증했다. 이번 검증에서는 OCI Git에 새 커밋을 쓰거나 빌드를 실행하지 않았다. OCI Git 저장에는 별도 Git 쓰기 인증이 필요하다.

보조 검사: Java 93개 통과. Git 동시 변경·일회성 저장·CSRF·범위 변경 차단, 다른 YAML 필드 보존, 앵커로 공유된 단계의 독립 편집·순환 참조 거부를 확인했다.

## 로컬 명세 초안 — 2026-11-03

기존 명세 편집 화면의 기본 저장은 **로컬 저장**이다. 프로파일·사용자·테넌시·리전·구획·파이프라인·소스·브랜치·명세 경로로 구분한 JSON을 `${companion.data-dir}/build-spec-drafts`에 저장한다. 디렉터리 0700, 파일 0600, 원자적 교체를 사용한다. 저장 포트와 JSON 어댑터를 분리한다. Git 인증 정보는 초안에 저장하지 않는다. 일반 환경변수·명령은 평문이므로 비밀값 대신 Vault 참조를 사용한다.

- 로컬 저장·불러오기·삭제 및 편집값 YAML 다운로드. Git 반영은 선택 펼침 영역이다.
- 원격 원본을 조회한 뒤 같은 원본 커밋의 초안을 복원한다. 원본 커밋이 달라졌으면 초안을 보존하고 적용을 거부한다. 완전한 오프라인 편집은 이번 범위가 아니다.
- 로컬 저장은 Git 쓰기나 OCI 실행 설정 변경을 수행하지 않는다. 실행 파라미터의 기존 로컬 프리셋 기능은 유지한다.
- 새 명세 작성은 기존 YAML 다운로드를 사용하며 Git 저장은 선택임을 표시한다.

네이티브 확인: [빌드 명세](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm)는 주 소스 저장소의 YAML 경로를 사용한다. [CreateBuildRunDetails](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/devops/models/oci.devops.models.CreateBuildRunDetails.html)는 파이프라인·커밋·실행 인자를 받고 YAML 본문 업로드 항목은 없다. 따라서 로컬 초안을 저장했다고 원격 실행에 적용됐다고 표시하지 않는다. 명령·명세를 변경해 실행하려면 연결 소스에 반영해야 한다.

실제 화면: `COMPANION_NATIVE_SETTINGS_READ_ONLY=1 node scripts/browser-native-settings.cjs`. OCI 원본 조회 → 명령 수정 → 로컬 저장 → 페이지 재진입 시 원본 미변경 확인 → 초안 복원 → YAML 다운로드 내용 확인 → 검증 초안 삭제. 원격 Git 쓰기·빌드 실행 없음. 390/768/1440px 확인. 자동 검사 94개 통과: 초안 파일 재조회·권한·분리·삭제, CSRF, 원격 Git 호출 없는 로컬 저장·복원·다운로드, 원본 커밋 변경 시 적용 거부 포함.
