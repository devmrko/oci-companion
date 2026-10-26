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

빌드 실행은 OCI Python SDK로 수행했다. 앱의 기본 빌드 메뉴는 아직 미구현이다.
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
