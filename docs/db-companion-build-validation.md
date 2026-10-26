# db-companion 실제 빌드 검증 — 2026-10-26

Redmine #984, 상위 작업 #979.

- 프로파일: `DEFAULT`
- 위치: `ap-seoul-1` / `Joungmin.Ko`
- 소스: `https://github.com/devmrko/db-companion`, `main`
- 검증 커밋: `7a5cb34b68d8dae90f9c398667a0db976bb2f6d2`
- 버전: `0.1.0-alpha.17`
- DevOps 프로젝트: `db-companion`
- 빌드 파이프라인: `db-companion-maven`

## 경로

실제 앱의 소스 화면에서 GitHub 주소를 등록했다.
서울 구획을 선택하고 새로 고침한 화면에서 생성한 프로젝트와 파이프라인을 확인했다.
등록 소스가 리전 변경 후에도 유지되고 중복이 없음을 확인했다.

최초 빌드 실행은 OCI Python SDK로 수행했다. 당시 앱의 기본 빌드 메뉴는 미구현이었다.
GitHub 소스에 OCI 빌드 명세가 없으므로 별도 OCI 저장소 `build-config`에 설정을 두었다.
Managed Build가 공개 GitHub의 고정 커밋을 체크아웃한다. 원본 저장소는 변경하지 않았다.

설정: [빌드 예제](../examples/build/db-companion/README.md).
Java 21.0.13, Maven 3.9.9, Node.js 22.13.1을 사용했다.

## 확인한 문제

- 생성 직후 제출한 첫 실행은 단계 진행 기록이 없는 `SUCCEEDED`였다.
  빌드 성공 근거에서 제외하고, 단계가 `ACTIVE`인 것을 확인한 뒤 다시 실행했다.
- 실제 실행에서 Java 테스트 1,444개와 PMD는 통과했다.
  JavaScript 테스트는 698개 통과, 1개 실패, 1개 건너뜀.
- 실패 원인은 OCI 이미지의 `lsof` 누락이었다.
  `app-runtime.test.mjs`의 오류 메시지 검증이 예상한 분기까지 도달하지 못했다.
  빌드 설정에 `dnf install -y lsof`를 추가했다. 테스트 코드는 변경하지 않았다.
- OCI Logging 반영이 실행 상태보다 늦었다. 전체 로그는 페이지네이션으로 조회해야 한다.

## 최종 결과

`lsof` 추가 후 재실행: `SUCCEEDED`.
Managed Build의 명령 실행과 `SAVE_OUTPUT_ARTIFACTS` 단계 모두 `SUCCEEDED`를 확인했다.
출력: `db-companion.jar`, `test-reports.tar.gz`.
빌드 설정 커밋: `0a24065d170ca4414bbf2216e7546627196b0257`.

로그 반영 후 최종 결과를 대조했다.

- Java: 1,444개 통과, 실패·건너뜀 0.
- PMD: 통과.
- JavaScript: 700개 중 699개 통과, 1개 건너뜀, 실패 0.
- Publication 검사: 1,084개 파일, 발견 사항 0.

## 범위

전용 알림 토픽(구독 없음), 프로젝트, 코드 저장소, 파이프라인, 로그 그룹/서비스 로그를 생성했다.
IAM 정책은 해당 파이프라인의 전용 저장소 읽기와 전용 토픽 사용으로 제한했다.
IAM 쓰기는 테넌시 홈 리전에서 수행했다.

Registry 영구 저장과 배포는 이번 검증에 포함하지 않는다.
키·계정 식별자·실제 로그·스크린샷은 Git에서 제외한다.

## 화면 연결 — Redmine #985

빌드 메뉴(`/builds`)를 활성화했다. 선택 리전·구획의 파이프라인, 실행 이력,
실행 상세와 단계 상태를 OCI SDK로 직접 조회한다. `BuildHistory` 포트와 OCI 어댑터로 분리했다.
목록에 없는 파이프라인/실행은 거절하며, OCI 응답의 구획·파이프라인 소속도 검사한다.
단계 기록이 없는 실행에는 별도 안내를 표시한다.

자동 테스트 19개 통과. DEFAULT/서울/Joungmin.Ko 실제 Chromium에서 db-companion-maven의
성공 상세, 실패 상세, 모바일 표시를 검증했다. 서버 재시작 후 8091에 반영했다.
당시 화면에서 새 빌드 제출, 로그 본문, Registry 조회는 지원하지 않았다.


## 실행 화면 — Redmine #988

빌드 메뉴를 실행/이력으로 나눴다. `BuildControl`이 실제 OCI 기본값 조회·저장 및
실행을 담당하고, `BuildPresetStore`는 JSON 프리셋을 담당한다.

- 실행 이름과 파라미터 이름/값/설명을 편집한다.
- OCI에 저장: 파이프라인 기본 파라미터 전체 교체. ETag로 충돌 방지.
- 빌드 실행: 기존 파라미터의 실행별 값으로 제출하고 해당 이력으로 이동.
- 프리셋: 프로파일·tenancy·user·리전·구획·파이프라인별 저장/불러오기/삭제.
  앱 data-dir의 build-presets에 JSON 저장, 디렉터리 0700/파일 0600, 원자적 교체.
- 실행 요청은 단일 사용 세션 티켓, OCI retry token, CSRF, 현재 범위 검증 적용.
  결과 불명 시 재요청하지 않고 이력 확인 안내.
- OCI 도움말 명령에 현재 프로파일·리전·파이프라인을 반영한다.

자동 테스트 25개 통과. 실제 DEFAULT/서울/Joungmin.Ko Chromium에서 프리셋
저장·불러오기·삭제, OCI 기본값 저장(기존 빈 목록 유지), GUI 실행 제출과 이력 이동 확인.
4화면 × 6너비 레이아웃 검사 통과. 실행 탭 1440/390px 별도 확인.
새 실행 이름: db-companion-gui-1792991931154.
검증 시 Maven/Node 명령 단계 IN_PROGRESS. 완료 성공으로 간주하지 않는다.

소스·브랜치·빌드 사양은 기존 OCI 파이프라인 설정을 사용한다.
현재 build-config는 db-companion 커밋을 스크립트에서 고정하므로 임의 파라미터를
추가해도 원본 체크아웃이 바뀌지 않는다. 소스 override, 빌드 사양 편집,
로그 본문, Registry 전송은 이번 구현 범위 밖이다.

## 파이프라인 상세·선택식 실행 — Redmine #989

빈 파라미터 표 중심 화면을 교체했다. 빌드 메뉴는 실행 탭으로 열리며,
프로젝트/파이프라인 선택 시 실제 OCI 설정을 조회한다.

- 단계 선후 관계, 상태, 소스 저장소/기본 브랜치, 이미지, 제한 시간 표시.
- OCI 저장소의 빌드 사양을 해당 커밋의 blob으로 읽어 명령과 출력 산출물을 표시.
  단순 bash/sh 파일 호출은 최대 3개 파일에서 Git 소스 명령을 찾아 별도 표시.
  파일 조회 실패는 안내하며 스크립트를 실행하지 않는다.
- 실행 이름은 선택. 기본 실행은 모든 소스의 기본 브랜치 최신 커밋 사용.
- 특정 소스 지정 시 브랜치 필수, 커밋 선택. OCI 브랜치는 페이지네이션 조회,
  커밋은 최근 최대 50개 선택 목록. 외부 Git은 등록된 기본 브랜치만 제공하며
  외부 인증/커밋 목록 미지원 사실을 표시한다.
- 파라미터는 이름별 입력 항목. OCI가 제공하지 않는 필수 여부/선택지는
  앱의 입력 규칙에서 지정하고 범위별 JSON에 저장한다. 미지정은 필수로 추정하지 않는다.
- 확인 화면에서 대상/소스 버전/파라미터 검토 후 실행.
- 파이프라인과 단계 ETag 조합으로 변경 감지. 다른 소스·없는 브랜치/커밋,
  누락 필수값·선택지 밖 값, 오래된 프리셋은 서버에서 거절.
- 프리셋에 실행 이름/소스 버전도 저장. 기존 JSON 프리셋은 기본 소스 방식으로 읽는다.
- OCI 기본 파라미터 변경과 로컬 입력 규칙 변경은 별도 폼이다.

31개 자동 테스트 통과. 실제 DEFAULT/서울/Joungmin.Ko에서 단계, build_spec.yaml,
명령, JAR/보고서 출력, build.sh의 GitHub 고정 커밋 표시를 확인했다.
실제 main 브랜치와 커밋 목록 조회, 확인 화면, 390px 표시 검증.
GUI 제출 후 OCI 응답에서 repositoryBranch=main,
commitHash=0a24065d170ca4414bbf2216e7546627196b0257 일치 확인.
실행명 db-companion-selected-source-1792993056387은 최종 SUCCEEDED.
Java/Maven/Node 명령 및 SAVE_OUTPUT_ARTIFACTS 단계 모두 SUCCEEDED 확인.
이전 실행 db-companion-gui-1792991931154의 최종 상태는 SUCCEEDED 확인.

재현: db-companion 예제 파이프라인이 선택 범위에 있어야 한다.
```sh
COMPANION_TEST_PROFILE=DEFAULT COMPANION_TEST_PIPELINE='<pipeline OCID>' node scripts/browser-build-definition.cjs
```
기본 검사는 읽기 전용. 실제 제출은 COMPANION_TEST_RUN=1을 추가한다.

한계: 이미지/제한시간/buildspec 편집은 포함하지 않는다. db-companion의 실제 GitHub
소스 커밋은 build.sh에 고정되어 있으며 실행 폼의 커밋 선택은 build-config 저장소에 적용된다.
스크립트 소스 추출은 정적 탐지이며 동적 셸 명령을 완전히 해석하지 않는다.
