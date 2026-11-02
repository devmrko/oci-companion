# 명세 저장·런타임·테스트 보고서

2026-10-27 · 구현 및 실제 검증 기록. Redmine #1002, #1003.

## 화면

`빌드 → 환경 관리 → 빌드 구성`

1. 등록 소스 선택. 상단 요약에 소스·브랜치·도구·런타임·명령·명세 경로 유지.
2. 도구·런타임·명령·환경 변수·출력 설정 → 명세 미리보기.
3. 다운로드 또는 Git 내용 비교 → 확인한 내용 커밋.
4. 파이프라인 빌드 단계에서 같은 소스·브랜치·명세 경로 연결.
5. 보고서를 보관하려면 `test-reports` 출력을 Generic Artifact Registry 전달 단계에 연결.
6. 실행 이력 → 실행 상세 → 테스트 보고서/로그.

입력 변경 시 미리보기·Git 비교 티켓을 무효화한다. 생성/저장/실행은 별개 작업이다. Git 커밋은 빌드 실행을 뜻하지 않는다. 기존 트리거가 있으면 해당 Git 서비스의 규칙에 따라 실행될 수 있다.

## Git 연결

`GitSpecifications` 포트 / `JGitSpecifications` 어댑터. HTTPS Git으로 파일을 읽고 커밋한다. 앱 실행 환경에 Git CLI가 필요하지 않다. checkout·저장소 hook 실행 없이 임시 bare 저장소의 객체만 편집한다.

지원: GitHub, GitLab, OCI DevOps HTTPS Git, 허용한 Gitea 호스트. OCI의 Git 인증은 프로파일 서명 인증과 별개다. OCI Auth Token 또는 외부 Git 자격증명을 사용한다. 외부 Git 서비스별 실검증 범위는 아래 기록으로 한정한다.

- Vault: 인증 참조에 Secret OCID. 선택 프로파일·리전으로 CURRENT 번들을 읽는다. 프로파일에 `read secret-bundles` 권한 필요.
- 서버 환경 참조: `env:GITEA`는 `COMPANION_GIT_GITEA_TOKEN`을 참조한다. 값은 UI·JSON·Git에 저장하지 않는다.
- 자체 호스팅: `COMPANION_GIT_HOSTS`에 호스트 이름을 쉼표로 등록. HTTPS·인증서 검증·리다이렉트 차단 유지.

Gitea 예시(값은 운영 환경의 비밀 관리 도구에서 주입):

```text
COMPANION_GIT_HOSTS=git.example.com
COMPANION_GIT_GITEA_HOST=git.example.com
COMPANION_GIT_GITEA_USER=build-user
COMPANION_GIT_GITEA_TOKEN=<secret>
```

Gitea 소스 브랜치·커밋 조회도 호스트에 연결된 서버 자격증명을 사용한다. 서버 자격증명은 로컬 앱 전체 설정이다. OCI 프로파일별 Git 계정 분리는 현재 제공하지 않는다. Gitea 등록은 OCI Managed Build의 네이티브 Gitea 연결을 생성하지 않는다.

비교 결과는 프로파일·리전·구획·등록 소스·브랜치 HEAD·파일 내용에 연결된 15분 티켓이다. 커밋 전 원격 HEAD를 다시 확인하고 push lease로 경합을 검사한다. 기존 파일 외 다른 파일은 그대로 보존한다. 같은 내용은 새 커밋을 만들지 않는다. 응답 유실 시 원격 확인 후 다시 비교하며 자동 재시도하지 않는다.

현재 제한: 기존 브랜치에 YAML 파일 저장. 새 브랜치/PR 생성, 전체 저장소 편집은 제공하지 않는다. 보호 브랜치는 서버 정책을 따른다.

## 런타임

OCI Managed Build의 Linux 실행 환경을 대상으로 명세를 생성한다. 앱 서버에 런타임을 설치하지 않는다.

- 기존 환경: 설치하지 않음.
- Maven/Gradle JDK 17·21: CPU 아키텍처(x86_64/aarch64)를 판별하고 Adoptium 배포 메타데이터의 SHA-256으로 다운로드 검증. 빌드 작업 디렉터리에 압축 해제하고 같은 단계에서 JAVA_HOME/PATH 적용.
- Python 3.11: 기존 python3.11 우선 사용. 없으면 Oracle Linux 패키지 설치. 별도 venv와 requirements.txt 사용. 기본값은 `PIP_CONFIG_FILE=/dev/null`로 실행 이미지의 pip 파일 설정을 분리하며, 사용자가 지정한 PIP_CONFIG_FILE/PIP_CACHE_DIR은 유지한다.
- Node.js 22: 기존 major 22 우선 사용. 없으면 Oracle Linux 모듈/패키지 설치. package-lock.json 기반 npm ci.

JDK의 major만 선택하며 해당 major의 최신 배포 파일을 실행 시 조회한다. 완전히 고정된 빌드가 필요하면 다운로드 명세의 버전·URL·체크섬을 소스에서 고정한다. OS 패키지 설치에는 root와 저장소 접근이 필요하다. Function의 런타임은 독립 Fn 템플릿 Dockerfile로 관리한다.

## 보고서

`TestReports`는 JUnit XML이 들어 있는 tar.gz를 디스크에 추출하지 않고 읽는다. 테스트 케이스를 기준으로 집계해 중첩 testsuites의 중복 합계를 피한다. 실패 상세는 최대 100건·건당 2,000자. 압축 해제 입력 64 MiB, XML 4 MiB, 엔트리 10,000개 제한. DTD/외부 엔티티는 금지한다. macOS 메타데이터 파일은 제외한다.

- Function: 선택한 앱·함수의 bucket/prefix 아래 실행 ID로 읽는다. Maven 실패 시에도 Surefire/Failsafe XML 업로드를 시도하는 독립 템플릿으로 수정.
- DevOps: 선택 빌드 실행에 실제 전달된 Generic Artifact OCID만 읽는다. 실행 상세의 tar.gz 선택 → 집계.
- 명세의 보고서 수집 옵션: Surefire·Failsafe·Gradle test-results·reports 아래 XML을 `test-reports.tar.gz`로 묶는다. 실패한 빌드의 종료 코드는 보존한다. npm 테스트는 프로젝트에서 JUnit reporter 설정이 필요하다.
- 없음, 빈 보고서, 조회/파싱 오류는 테스트 통과로 표시하지 않는다.

Managed Build 실패 시 후속 전달 단계가 실행되지 않을 수 있다. 이 경우 보고서 수집만으로 영구 보관은 보장하지 않는다. Function 템플릿 수정은 이미 배포된 함수에 자동 반영되지 않으며 재배포해야 한다.

## 근거

- [OCI 빌드 명세](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm)
- [OCI 실행 환경](https://docs.oracle.com/en-us/iaas/Content/devops/using/runtime_details.htm)
- [Temurin 배포 파일·체크섬](https://adoptium.net/installation/archives/)
- [Oracle Linux Python](https://docs.oracle.com/en/operating-systems/oracle-linux/8/python/python-InstallingPython.html)
- [OCI Generic Artifact Content API](https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/genericartifactscontent/GenericArtifactsContent.html)

## 검증 결과

- Java 77개, Python 4개 통과. 실패 빌드의 보고서 보존, XML 외부 엔티티 차단, Git 경로 검증, CSRF·티켓 재사용 거부 포함.
- 실제 Gitea 전용 비공개 저장소 `joungmin/oci-companion-build-validation`: 명세 커밋, 읽기, 브랜치·커밋 조회, 원격 동시 변경 거부 확인. GitHub/GitLab/OCI HTTPS 쓰기는 이번에 실검증하지 않았다.
- DEFAULT / ap-seoul-1 / Joungmin.Ko의 별도 `oci-companion-runtime-validation` 파이프라인: JDK21 다운로드·체크섬·Maven 테스트, Python3.11/pytest, Node22 테스트 후 Registry 전달 SUCCEEDED. JDK17 및 Python/Node 미설치 환경의 OS 패키지 경로는 실검증하지 않았다.
- 실행 OCID: `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiaxwns4e3e53fa3zpqmgnga6gqntnhqgsgf4d7oyrjak2q`. 소스는 OCI build-config의 `validation/runtime-reports-20261027`, 커밋 `04b6fef`. 기존 db-companion 파이프라인과 main 브랜치 설정은 유지했다.
- 전달: `runtime-validation/test-reports.tar.gz`, 버전 `validation-3b80bb185b19`. 실제 실행 상세 화면에서 3개 테스트·실패 0·오류 0·건너뜀 0 집계 확인.
- 기존 Function 실제 Object Storage 보고서: 1,444개 테스트, 실패·오류·건너뜀 0.
- 최초 패키지 JDK 설치는 완료 전 로그가 중단되어 실패했다. 원인을 메모리 부족으로 확정하지 않았다. 체크섬 배포 파일 방식으로 변경한 후 통과했다. Python은 이미지 pip 프록시 오류를 기본 파일 설정 분리로 해결했다. Registry 전달 권한은 검증 파이프라인 principal의 구획 단위 읽기·생성으로 제한했고 삭제 권한은 부여하지 않았다.
- 신규 사용자 검증 범위: 실제 서비스 브라우저 흐름, 6개 화면 폭, 스크린샷·작업 순서 점검. 사람 대상 관찰은 미실시.

## 컨테이너 이미지 명세 — 2026-11-02

- 위치: 빌드 → 환경 관리 → 빌드 구성 → 빌드 명세 → 산출물 유형 `컨테이너 이미지`.
- 입력: 출력 이름, 로컬 `이미지:태그`, Dockerfile 경로, 빌드 컨텍스트. 경로는 작업 경로 기준이며 상위·절대 경로는 허용하지 않는다.
- 생성 순서: 앱 빌드·테스트 → Docker 연결 확인 → `docker build` → 이미지 ID 확인. 생성 태그와 `DOCKER_IMAGE` 출력 location을 일치시킨다.
- 기존 Dockerfile과 Docker 실행 환경을 사용한다. Dockerfile 생성, Docker 설치, 멀티 아키텍처 빌드는 포함하지 않는다.
- OCIR 영구 저장은 기존 산출물 참조·전달 단계 연결이 필요하다. 이번 변경은 명세 생성까지이며 실제 이미지 빌드·OCIR 전달·OKE 배포 검증은 별도다.
- 근거: [OCI 빌드 명세](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm)의 DOCKER_IMAGE 출력은 앞선 단계에서 생성한 이미지 태그를 참조한다.

## 배포용 빌드 분류와 실제 컨테이너 예제 — 2026-11-02

- 작업 분류: VM용 빌드 / 컨테이너 빌드(OKE·OCI Container Instances) / Function 빌드. 실행 서비스(DevOps / Functions Maven)와 별도 저장한다.
- 대상 아키텍처: x86(amd64) / ARM(arm64) / 미지정. 기존 JSON은 유형·아키텍처 미지정으로 읽는다. 분류 값 자체가 빌드 환경을 변경하거나 배포 호환성을 검증하지는 않는다.
- 명세의 아키텍처 선택은 실행 호스트와 생성 이미지의 아키텍처가 불일치하면 중단한다. 교차 빌드·멀티 아키텍처 manifest는 미구현이다.
- Functions Maven 실행기는 테스트·파일 산출물용이다. Function 배포용 이미지를 빌드하는 기능과 혼동하지 않는다. 컨테이너·Function 이미지 분류에서는 이 실행기를 선택할 수 없다.
- Function 이미지에는 Fn FDK/호출 규약 및 애플리케이션 shape에 맞는 이미지가 필요하다. 일반 HTTP 컨테이너를 그대로 Function 이미지로 간주하지 않는다. [Oracle FDK](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionssupportedlanguageversions.htm), [아키텍처](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsspecifyingcomputearchitectures.htm).

실제 예제:

- DEFAULT / ap-seoul-1 / Joungmin.Ko. 빌드 작업 `컨테이너 예제 · x86` 등록.
- 파이프라인: `ocid1.devopsbuildpipeline.oc1.ap-seoul-1.amaaaaaavsea7yia5a345uuefthz67jbjyu4i2nqcql3fvntolnbz7n5lraa`. 빌드 실행: `ocid1.devopsbuildrun.oc1.ap-seoul-1.amaaaaaavsea7yiaqh56troeaapboipptuwkfgclwjc7imhppafbwfnkl6eq`. Managed Build와 Deliver Artifacts 모두 SUCCEEDED.
- OCI build-config의 별도 브랜치 `examples/container-demo-20261102`, 커밋 `5fc63ef45c0b` 접두사. 기존 main·기존 빌드 파이프라인은 변경하지 않았다.
- 소스: `examples/container-demo/`. 실제 OCI 브랜치에는 `container-demo/`로 복사하고 생성한 명세를 루트 `build_spec.yaml`에 저장했다. 예제에 보관한 명세도 그 배치를 기준으로 한다.
- Python 3.11 테스트 2개 통과. 이미지 빌드 후 임시 컨테이너를 실행해 HTTP 응답 `status=ok`, `architecture=x86_64` 확인 후 컨테이너 삭제.
- 비공개 OCIR 이미지: `icn.ocir.io/apackrsct01/companion/container-demo:x86-20261102-v1`.
- digest: `sha256:3916cad8c0fb958b60eb44d99f966268455fc3d26eb1c66adfdb785c986d4883`. Dockerfile 기반 이미지는 `python:3.11-slim` 태그를 사용했으므로 재빌드 시 기반 이미지가 바뀔 수 있다. 이 결과를 재사용할 때는 digest를 지정한다.
- 권한: 전용 파이프라인 principal에 소스·DevOps artifact 읽기, 기존 알림 주제 사용, `companion/container-demo` 저장소 use 권한만 부여했다.
- 보고서는 빌드 출력으로 수집했으나 이 예제의 영구 전달 대상은 이미지뿐이다. OKE·Container Instances 배포와 ARM 이미지 빌드는 실행하지 않았다. Function 이미지 예제도 이번 범위에서 실행하지 않았다.

## 분류 정정 — 2026-11-02

사용자 확인: 컨테이너 이미지는 VM에서도 내려받아 실행할 수 있으므로 VM과 컨테이너를 같은 축으로 분류하지 않는다. 위 구현 기록의 VM용/컨테이너/Function 분류를 다음과 같이 정정했다.

- 흐름: 소스 → 언어·빌드 설정 → 산출물 → 저장소 → 배포 대상.
- 작업 산출물: 파일(JAR·압축 파일 등) / 컨테이너 이미지 / Function 패키지·이미지 / 미지정.
- 기존 JSON의 target=VM은 FILE로 호환 읽기한다. target 필드명은 기존 저장 형식 호환을 위해 유지하며 배포 대상을 뜻하지 않는다.
- 컨테이너의 실행 후보는 VM(Docker·Podman) / OKE / Container Instances. 실제 배포 설정과 호환성 검사는 별도이며 아직 미구현이다.
- 기존 Maven/Gradle/Python/npm 설정에 언어별 런타임·의존성·시작 명령 안내를 추가했다. Python 기본 명세는 pytest 테스트이며 wheel 자동 생성은 구현하지 않았다. Node는 정적 파일과 서버의 실행 조건을 구분해 안내한다.
- 일반 Java 바이트코드 JAR는 아키텍처 미지정이 가능하나 Java 버전과 네이티브 의존성을 확인한다. 이미지·네이티브 파일은 실행 대상 아키텍처를 확인한다.
- Function 패키지·이미지는 분류이며 새 패키징 생성기를 추가한 것은 아니다. 기존 실행기는 그대로 사용한다.

### 기존 작업 분류 보정·필터 검증 — 2026-11-02

기존 작업 3개가 UNSPECIFIED로 남아 파일 필터에서 빠졌었다. 실제 DevOps 전달 artifact `db-companion-jar`가 GENERIC_FILE임을 SDK로 확인하고, 같은 파이프라인을 사용하는 두 작업과 artifact=target/*.jar인 Functions Maven 작업을 FILE로 보정했다. 실행 서비스가 Functions라는 이유로 Function 패키지로 분류하지 않는다. 다른 미확인 작업에 이 보정을 일괄 적용하지 않는다.

필터마다 건수를 표시하고 미지정 필터를 추가했다. 실제 DEFAULT/서울/Joungmin.Ko 화면에서 전체4 → 파일3 → 컨테이너1 → Function0 → 미지정0 → 전체4와 빈 결과 안내를 검증했다. Java81 테스트 통과.
