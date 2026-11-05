# Functions 빌드 환경

2026-10-27 · Redmine #998

## 화면

`빌드 → 빌드 환경 → Functions` (`/builds/functions`)

- **기존 선택**: 글로벌 프로파일·리전·구획으로 애플리케이션과 Function 조회. 이미지·상태·메모리·결과 버킷 확인.
- **새로 구성**: Maven/JDK 21 Fn Project ZIP 다운로드. 아키텍처, 애플리케이션, Registry 접두사, 결과 버킷, 서브넷 입력. 실제 OCI 조회값을 선택 후보로 제공.
- **빌드 실행**: 공개 Git URL, 고정 커밋 SHA, Goal, Maven 프로파일, 작업 경로, 산출물 패턴 입력.
- **실행 이력**: 결과 상태, 산출물 경로·해시, Object Storage의 빌드 로그 확인. 실행 직후 로그 패널을 열고 5초 간격으로 갱신한다. 종료 시 자동 중지하며 수동 재조회와 다음 페이지도 제공.
- **도움말**: OCI Console/CLI 위치와 필요한 사용자·Function 권한 안내.

새 환경 생성은 ZIP의 CLI 안내로 진행한다. 다운로드만으로 리소스가 생성되지는 않는다. 이미지 준비에는 Docker/Fn CLI가 필요하며, 준비된 Function을 호출할 때 앱에 Docker/Fn CLI는 필요 없다.

## 독립 실행 규격

`FunctionBuilds` 포트와 OCI 어댑터를 분리했다. 템플릿은 앱 주소·DB·사용자 키를 포함하지 않는다. ZIP에는 Dockerfile, func.py, func.yaml, application.json, config.json, request.json, README.md가 들어간다. Fn/OCI CLI만으로 배포·호출할 수 있다.

Function 설정 `BUILD_RUNNER_CONTRACT=maven-v1`, `BUILD_NAMESPACE`, `BUILD_BUCKET`, `BUILD_PREFIX`를 사용한다. 설정을 넣기만 해서는 빌더가 되지 않으며 해당 실행 코드가 배포돼 있어야 한다. 앱은 애플리케이션과 Function 설정을 합쳐 확인한다.

실행마다 UUID를 발급한다. 결과 경로는 `<prefix>/<run>/result.json`, `build.log`, `test-reports.tar.gz`, `artifacts/<파일 경로>`이다. Resource Principal로 버킷에 저장한다. 최초 결과는 조건부 생성하여 중복 UUID의 재실행을 막는다. 앱은 일회성 제출과 범위·설정 변경 검사를 적용하며, 접수 여부가 불명확하면 자동 재시도하지 않는다.

HTTP202는 접수다. 최종 result.json의 SUCCEEDED와 산출물로 성공을 확인한다. 강제 종료로 RUNNING이 남으면 OCI 호출 로그를 확인한다. 앱의 빌드 로그는 버킷에서 읽는다.

## 실제 검증

DEFAULT / ap-seoul-1 / Joungmin.Ko. UI에서 다운로드한 템플릿을 배포하고 UI에서 호출했다.

| 항목 | 결과 |
|---|---|
| 애플리케이션 / Function | oci-companion-arm-build-test / maven-builder-998 |
| 실행 ID | 85e5762a-8b6d-462c-ab26-dd2f141aac88 |
| 소스 | devmrko/db-companion, 7a5cb34b68d8dae90f9c398667a0db976bb2f6d2 |
| 설정 | ARM, 3072 MiB, detached 3600초, Maven verify, quality |
| 결과 | SUCCEEDED, 핸들러 처리 117.56초 |
| 테스트 | Surefire XML 합산 1444개, 실패·오류·건너뜀 0 |
| JAR | 61,111,724 bytes, 다운로드 후 SHA256 일치 |
| SHA256 | b904797216979e87145057155505e90c6db3ef1090bb48747fcfb0ea73ddc444 |
| 결과 버킷 / 접두사 | oci-companion-arm-build-997 / maven-builder-998-e048a02d |
| 이미지 digest | sha256:5f0ea9640da6cbd638adaddfeb80f532b432120cb34b48902bc9a772d4d29a16 |

앱 자동 테스트 60개 통과. 실제 OCI 조회·템플릿 다운로드·GUI 호출과 결과 로그를 검증했다. 320/390/768/1024/1440/2048px에서 화면 넘침을 검사한다 (`scripts/browser-function-build.cjs`).

전용 Function·비공개 이미지·최소 버킷 쓰기 정책은 재사용을 위해 유지한다. 이미지 업로드 임시 Auth Token은 삭제했다. 기존 VM은 변경하지 않았다.

## 현재 범위

Maven/JDK 21, 공개 GitHub/GitLab, 40자리 고정 SHA, test/package/verify를 지원한다. x86 템플릿 선택은 제공하지만 실제 검증은 ARM만 수행했다. 비공개 Git 인증, Gradle/Python/npm 실행기, Artifact Registry 전달, 취소, 테스트 보고서 UI 집계는 후속 범위다. Function 내 컨테이너 이미지 빌드는 지원하지 않는다.

## 진행 로그 보완 (#999)

선택한 실행의 결과·로그를 직접 조회한다. 이력 목록의 현재 페이지에 실행이 없어도 로그를 확인할 수 있다. 최근 200KB를 표시하고 마지막 로그 따라가기를 제공한다. 조회 오류 시 기존 로그를 유지하고 자동 조회를 중지한다. 리전·구획이 바뀌면 이전 화면의 조회를 거부한다. Function의 로그 업로드와 화면 조회 주기가 각각 약 5초여서 표시 지연이 있을 수 있다.

템플릿 예약 변수 `application`과 충돌해 링크에 애플리케이션 OCID가 전달되지 않던 오류를 `functionApplication`으로 수정했다. 실제 ARM 실행 `8e14a94a-db72-4a17-b686-30227f7b93a1`에서 새로 고침 없이 RUNNING 로그가 증가하고 SUCCEEDED 후 자동 조회가 중지되는 것을 브라우저로 확인했다.
