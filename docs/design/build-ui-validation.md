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
- PMD 실행은 로그로 검증했다. PMD 위반 전용 목록·집계 화면은 미구현이다. PMD 위반으로 빌드가 실패하는 사례와 `package` 단독 실행은 이번 범위에 포함하지 않았다.
- 실시간 실행 상세는 자동 갱신되며 하단 실행 이력 목록은 새로 고침이 필요하다.

재현: `COMPANION_UI_BUILD_WRITE=1 node scripts/browser-maven-goals.cjs`. 기존 작업에서 두 번의 실제 Function 빌드를 실행한다. 화면 조작만 사용하며 `.tools/ui-maven-*`에 실행 ID·로그·산출물 정보·캡처를 남긴다.
