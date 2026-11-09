# VM 배포 구성 검증 — 2026-11-09

DEFAULT / ap-seoul-1 / Joungmin.Ko, 실제 OCI와 브라우저를 사용했다. 테스트 서버는 8092, 기존 8091 프로세스는 변경하지 않았다.

## 결과

- 환경 `companion-vm-preparation-20261104`, 불변 저장소 `companion-gitea-onboarding-20261103`, `hello/app.tar.gz` 선택.
- UI에서 설치·상태 확인 명령을 입력하고 생성 전 명세를 검토한 뒤 OCI 파이프라인을 생성했다.
- 파이프라인: `companion-vm-configuration-20261109014303`
- 파이프라인 ID: `ocid1.devopsdeploypipeline.oc1.ap-seoul-1.amaaaaaavsea7yiatdsdkwzumichj74t3nnrlkjd33tm5pg3j7ynetimdm4q`
- 네이티브 재조회: 단일 VM 단계 ACTIVE, 올바른 환경·배포본·명세 연결, 기본 배치 간격 60초, 배포 실행 0건.
- `codex-test-vm`: STOPPED, E4.Flex, 1 OCPU / 2 vCPU, 메모리 16GB, 부트 볼륨 80GB. 로컬 디스크 값 미제공은 `미확인` 표시.
- 실행 전 확인 화면에서 중지 VM 차단. VM 시작·사양 변경·명령 실행은 하지 않았다.
- 390 / 768 / 1440px에서 입력·검토·결과·실행 차단 화면의 가로 넘침 검사. 긴 버전 문자열 줄바꿈 수정. 브라우저 JS 오류 0건.
- Java 테스트 163개 통과: 명세 검증, 잘못된 범위·저장소·파일 차단, 검토 변경, CSRF·티켓 재사용, 부분 생성 결과, 사양 읽기 실패 등을 포함한다.

## 발견한 문제

배치 간격 0초로 단계 생성 시 OCI가 `InvalidParameter`를 반환했다. 명시값을 제거하고 OCI 기본값을 사용하도록 수정했다. 첫 시도에서 생성된 파이프라인·파일 참조·명세는 자동 삭제하지 않고 부분 생성 결과로 표시했다. 해당 검증 파이프라인은 단계가 없는 상태이며 실제 배포 이력은 없다. 진단용 로컬 기록: `.tools/deployment-setup-partial.json`.

## 재현

`scripts/browser-deployment-setup.cjs`는 DEFAULT 프로파일의 실제 리소스를 사용한다. `COMPANION_TEST_PROFILE=DEFAULT`로 조회·검토만 수행한다. `COMPANION_TEST_CREATE=1`을 추가하면 검증 전용 파이프라인을 생성한다. 이 스크립트는 배포 실행 버튼을 누르지 않는다. 리소스 위치는 기존 `.tools/vm-environment-validation.json`, `.tools/ui-onboarding-state.json`을 사용한다.

설정 생성이 통과한 것이며, 운영 앱의 설치·기동·HTTP 상태 확인 성공을 의미하지 않는다.
