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
