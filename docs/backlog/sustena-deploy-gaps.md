# Sustena OKE 배포에서 확인한 Companion 기능 공백

Companion 개발자가 다음 보강 순서를 정할 때 쓰는 기록입니다. 2026-11-25 new-sustena(Gitea `joungmin/new-sustena`)를 joungminkokor 테넌시 OKE `oke-shared-a1`(ap-chuncheon-1, ARM64 A1 노드 2대) `personal-services`에 Companion 화면으로 다시 배포하면서, 단계마다 Companion이 해 준 것, 실패한 지점의 실제 오류 원문, CLI로 메운 방법을 적었습니다. 이 문서에는 초기 공백과 이후 보강 재검증 결과를 구분해 기록합니다.

## 보강 구현 후 재검증 (2026-11-25)

별도 작업 브랜치의 Companion을 `127.0.0.1:18991`에서 실행하여 검증 앱을 처음부터 배포하고 재배포했습니다. 기존 운영 앱은 이 검증에서 변경하지 않았습니다. 아래 초기 기록은 당시 결과로 보존합니다.

| 항목 | 실제 결과 | 증거 스크린샷 |
|---|---|---|
| IAM | `JOUNGMINKOKOR_API`에서 기존 정책의 요구 권한 6종 확인. 새 정책·동적 그룹 생성 없음 | `02-iam.png` |
| 이미지 주소 | 기존 `ocir-pull`의 리전 이름 호스트와 일치하는 주소로 이미지 pull 성공 | `14-deployment-preview.png`, `19-deployment-status.png` |
| 배포 입력 | 지갑 Secret 파일 볼륨, `/tmp` emptyDir, 보안 컨텍스트, 토큰 자동 마운트 해제, 프로브 Host·시간 제한을 입력하여 2/2 Ready. 앱 재시작 후 입력 유지 및 같은 구성 재배포 성공 | `13-deployment-input.png`, `21-redeploy-ready-reused.png` |
| 사설 API | Companion 관리 SSH 터널과 기본 `/opt/homebrew/bin/kubectl`로 Namespace 5개, 배포 참조, 상태, Pod 로그 조회. 래퍼 사용 없음 | `03-api-access.png`, `04-private-api-namespaces.png`, `20-pod-logs.png` |
| NodePort | 새 Service `sustena-verify-nodeport`의 31084 충돌 검사·생성. 승인된 출발지 /32 규칙 한 개를 미리보기·백업 후 추가하고 검증 후 제거 | `17-nodeport-rule-preview.png`, `18-nodeport-rule-added.png`, `25-nodeport-remove-preview.png`, `26-nodeport-removed.png` |
| ARM 빌드 | 화면에서 커밋 `a829ff928af94ea1275e27170fdf6b0d067429b0` 선택 → ARM VM 소스 전달 → Python AST 구문 검사 → podman ARM64 빌드 → OCIR push → digest 선택 → OKE 배포 성공 | `06-arm-preview.png`, `12-arm-diagnostic.png`, `14-deployment-preview.png` |
| 소스·Git 저장 | OCI `new-sustena/companion-build`에서 검증 저장소의 새 브랜치로 push. OCI 구성에서도 Git 저장 버튼으로 `deploy/companion-verify.yaml` 커밋 성공 | `22-source-push-preview.png`, `23-source-pushed.png`, `24-oci-git-save.png` |
| 조회 연결 | 동일 클러스터·Namespace·Deployment를 다시 등록해도 기존 연결 ID 재사용 | `21-redeploy-ready-reused.png` |

배포 대상은 `oke-shared-a1` / `personal-services` / `sustena-verify`이며 이미지 digest는 `sha256:87cb144fd0bd85cdaef0b6172fedf779d3932008311755ad562f6a812cffc25c`입니다. 두 차례 OCI 배포 실행 모두 `SUCCEEDED`, 상태 화면은 Ready 2/2였습니다. Namespace 객체는 생성하지 않았고 기존 Secret·ConfigMap은 참조만 했습니다. CLI 패치는 하지 않았습니다. Pod 로그는 실제 6줄 조회했으며 스크린샷에서는 원문을 생략했습니다.

실행 중 발견해 수정한 오류:

- IAM 목록 응답에서 동적 그룹 규칙·정책 문장이 생략되어 상세 조회로 보충했습니다.
- 새 화면의 사이드바 겹침은 공통 레이아웃으로, CSP의 인라인 스크립트 차단은 외부 스크립트로 수정했습니다.
- 기본 VM 인증으로 OCIR push가 종료 코드 125, `StatusCode: 403`으로 실패했습니다. 레지스트리가 일치하는 기존 인증을 일회성으로 입력한 재시도는 성공했습니다. 오류 출력은 제한된 길이로 마스킹합니다.
- 세 프로브가 공유한 HTTP 헤더 목록이 YAML 별칭으로 출력되어 실행 전 검증에서 거부됐습니다. 각 프로브의 독립 목록을 생성하도록 수정하고 실제 생성 명세를 파서에 통과시키는 회귀 테스트를 추가했습니다.
- OCI Git 인증에는 해당 계정의 도메인까지 포함한 사용자명이 필요했습니다. [Oracle HTTPS 인증 안내](https://docs.oracle.com/en-us/iaas/Content/devops/using/https_auth.htm)의 형식으로 입력한 후 push·명세 저장에 성공했습니다.
- 빌드·배포·Pod 로그의 전체 OCID, JSON 비밀 필드, 인라인 명세 base64를 가리는 공통 처리를 보강했습니다.

검증 정리 완료: Deployment·Service·Pod, DevOps 파이프라인 두 개와 단계·산출물, OKE 환경, 코드 저장소, OCIR 저장소 및 ARM VM의 검증 이미지 태그를 삭제했습니다. 이름으로 재조회한 검증용 클라우드 리소스와 Kubernetes 리소스는 0개였습니다. 임시 보안 규칙도 0개이며 기존 워커 수신 규칙 6개가 유지됐습니다. 삭제 기능이 없는 리소스 정리에는 OCI SDK와 기본 kubectl을 사용했습니다. 정리 후 운영 Deployment Ready 수는 `sustena-c` 2, `knowledge-mcp` 2, `finance-mcp` 1이었습니다. 후속 운영 재배포 지시에 따라 검증용 Companion 서버는 이어서 사용합니다.

검증 한계 및 남은 공백:

- IAM이 없는 테넌시에서 새 정책 생성·전파·빌드 통과 과정은 이번에 재현하지 않았습니다. 기존 정책 점검과 적용 가능한 권한만 확인했습니다.
- ARM 러너 점검은 현재 SDK의 지원 이미지 목록에 근거합니다. OCI 전체 서비스의 ARM 러너 제공 여부를 증명하지 않습니다. 원격 빌드의 테스트 단계는 Python AST 검사이며 Sustena 전체 단위 테스트 실행은 아닙니다.
- NodePort 생성·규칙 추가/제거·Pod Ready는 확인했지만 외부 HTTP 요청 성공은 별도 검증하지 않았습니다.
- 소스 push의 실제 검증은 OCI 저장소 간 새 브랜치 전달입니다. 초기 브랜치 충돌 방지는 회귀 테스트로 확인했습니다.
- 운영 `sustena-c` 재배포는 아래 후속 검증에서 완료했습니다. OCIR 인증 자동화와 운영 서비스 등록은 아래 B2·B3에서 완료했습니다.

초기 보강 검증의 `./mvnw verify`: **378개 테스트 통과**. 로그는 `/tmp/ridley-verify-1.log`, 스크린샷은 이 작업 트리의 `.tools/verification/screenshots/`에 있으며 Git에는 포함하지 않습니다. 전체 OCID·비밀값·API 키 지문을 문서에 남기지 않습니다.

## 운영 재배포 (후속 오너 승인, 2026-11-25)

운영 `sustena-c`를 Companion으로 재배포하여 OCI 실행 `SUCCEEDED`, Ready **2/2**, `https://sustena.store/readyz` **200**을 확인했습니다. 운영 이미지는 기존 `a829ff9`의 digest `sha256:d822e917de25d58511296d430e1038a4f5da0c6c0847b1ef66947f97741d4cfb`를 유지했습니다. CLI 패치나 복원은 실행하지 않았습니다.

- 사전 백업: `.tools/verification/production-backup/`의 Deployment·Service·ConfigMap YAML. Secret은 조회하거나 백업하지 않았으며 인라인 비밀 환경변수·민감한 ConfigMap 키가 있으면 중단하도록 검사했습니다. 백업은 Git에서 제외하고 파일 권한 0600으로 보관합니다.
- 현재 폼에 없는 `runAsUser`, seccomp, 지갑 파일 모드, emptyDir 크기 제한까지 유지하도록 **기존 Deployment 유지 · 이미지만 변경** 경로를 추가했습니다. 현재 단일 컨테이너 Deployment를 읽어 이미지 참조만 바꾸며 Service·Namespace 객체는 만들지 않습니다. 미리보기 이후 리소스 버전이 바뀌면 저장을 중단합니다. 인라인 환경변수를 포함한 Deployment는 이 경로에서 거부합니다.
- 저장 전 생성 명세와 백업의 전체 `spec`이 동일함을 확인했습니다. 실행 후에도 Deployment `spec`, `sustena-c-nodeport` Service `spec`, `sustena-config` ConfigMap 데이터가 동일했습니다. NodePort 31083·nginx·보안 규칙·Secret은 변경하지 않았습니다.
- 운영 재배포에 사용하는 새 DevOps 파이프라인 `sustena-c-companion`과 명세는 유지합니다. 운영 조회 연결 `sustena-c`도 등록했습니다. 검증 리소스가 아닌 후속 운영 서비스 등록 대상입니다.
- 스크린샷: `29-production-readyz-before.png`, `30-production-preview.png`, `31-production-started.png`, `32-production-deployment-result.png`, `33-production-ready.png`, `34-production-readyz-after.png` (앞 절과 같은 폴더).
- 전체 `./mvnw verify`: **380개 통과**, 0 실패·오류·건너뜀. 기능 커밋 `cc4a2ee`.

후속 B 단계에서는 ARM 빌드 대안 조사, OCIR 인증 재사용, 운영 서비스에서 이어지는 빌드·배포 흐름을 진행합니다.

## B1: Functions/Fn ARM 이미지 경로 조사 (2026-11-25)

과거 Function 성공 기록은 확인했지만 Sustena 컨테이너 이미지를 Function 내부에서 생성한 근거는 찾지 못했습니다. [기존 ARM Function 검증](../arm-function-build-validation.md)은 ARM에서 Maven 테스트 1,444개와 JAR 생성을 성공한 기록이며 컨테이너 이미지 생성은 명시적으로 범위 밖입니다. [Functions 빌드 환경](../function-build-environment.md)과 Python 빌더 구현도 JAR·wheel 산출물 경로입니다. 기존 Function 기능을 재사용할 수 있도록 ARM 이미지 화면에서 해당 환경 화면으로 연결했습니다.

- 로컬 작업공간과 Gitea의 `oci-companion-build-validation`, `oci-data-flow-aidp`, `oci-management`, `oci-opensearch` 파일 목록에서 별도 Fn 이미지 빌드 경로를 찾지 못했습니다. Gitea `oci-companion` 기본 브랜치 tree API는 HTTP 404였지만 가져온 `gitea/devmrko/sustena-deploy`의 기존 검증 문서는 확인했습니다. 조회한 범위 밖 저장소까지 없다고 단정하지 않습니다.
- 실제 `JOUNGMINKOKOR_API` / `ap-chuncheon-1`의 Functions API 조회 결과 애플리케이션은 0개였습니다. 재사용할 빌더가 없었으며 이번 조사에서 새 Function·IAM은 만들지 않았습니다. 로컬 `fn build --help` 시도는 `zsh:1: command not found: fn` (127)이었습니다. Docker·podman 실행 파일도 로컬 PATH에 없었습니다. 이는 현재 실행 환경의 한계이며 OCI Functions 자체가 ARM 이미지를 만들 수 없다는 증명은 아닙니다.
- [공식 아키텍처 문서](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsspecifyingcomputearchitectures.htm)는 Generic_ARM을 함수 실행 아키텍처로 정의하며 Fn의 이미지 생성은 Docker 빌드 경로입니다. [Docker 준비 안내](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsinstalldocker.htm)도 별도 엔진이 필요함을 설명합니다. [Function 권한](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsrunningasunprivileged.htm)은 UID 1000과 권한 상승 제한을 명시하므로 중첩 컨테이너 빌더를 검증 없이 동작한다고 가정하지 않습니다.
- [관리형 빌드 문서](https://docs.oracle.com/en-us/iaas/Content/devops/using/add_buildstage.htm)와 현재 SDK에는 사용할 ARM 관리형 이미지가 확인되지 않았습니다. 새로운 서비스 옵션의 존재 여부까지 확정하지 않았으며 실제 성공한 VM ARM 경로를 유지합니다.

남은 검증은 별도 ARM Function에 이미지 빌더를 준비하고 실제 이미지 생성·push까지 수행하는 것입니다. 현재 Function 경로를 VM보다 우선하도록 바꾸지 않았습니다. 기능 검증 범위를 구분한 안내를 추가했으며, 기존 문서에 남아 있던 전체 Function OCID도 제거했습니다.

### B1 추가 확인: 관리형 CUSTOM 러너와 Cloud Shell

`JOUNGMINKOKOR_API` / 춘천 리전에서 새 `sustena-verify-arm-capability` 빌드 파이프라인을 만들고 관리형 단계 생성 API를 두 번 호출했습니다. 빌드 실행은 하지 않았으며 생성된 단계와 파이프라인은 모두 삭제했습니다.

| 요청 | 실제 API 결과 |
|---|---|
| `CUSTOM`, OCPU 2, 8GiB, `shapeName=VM.Standard.A1.Flex`, `OL8_AARCH64_STANDARD_10` | HTTP 400 `InvalidParameter`, 원문 `Invalid Image: OL8_AARCH64_STANDARD_10` |
| 같은 CUSTOM 설정, `OL8_X86_64_STANDARD_10` | 생성 접수. 반환된 설정은 CUSTOM·OCPU 2·8GiB뿐이며 이미지도 x86 그대로. `shapeName`은 응답에 없음 |

[공식 CUSTOM 모델](https://docs.oracle.com/en-us/iaas/tools/typescript/latest/modules/_devops_lib_model_custom_build_runner_shape_config_.custombuildrunnershapeconfig.html)의 속성도 `buildRunnerType`, `ocpus`, `memoryInGBs`이며 셰이프 이름 필드는 없습니다. 따라서 위 요청 접수는 A1.Flex ARM 러너 선택 성공이 아닙니다. 시험한 ARM 이미지 이름은 거부됐지만 모든 미공개 이미지 이름을 시험한 것은 아닙니다. 근거는 로컬 `.tools/verification/arm-capability-results.json`입니다.

Cloud Shell은 [공식 문서](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cloudshellintro.htm)에서 유료 계정의 ARM 선택, Podman·Buildah·Fn 제공을 명시합니다. ARM이 제공되는 리전에서는 대화형 컨테이너 빌드 후보가 됩니다. 다만 외부 SSH 접속이 없고 장시간 비대화형 작업에는 Compute를 권장하므로 현재 Companion 서버의 자동 원격 빌더와 같은 연결 방식으로 대체할 수는 없습니다.

실제 로컬 OCI CLI 시도는 `oci cloud-shell --help` → `Error: No such command 'cloud-shell'.` (종료 2)였습니다. 브라우저로 춘천 OCI 콘솔을 열자 `Cloud Account Name` 로그인 화면이 표시됐습니다(`35-cloudshell-console-access.png`). 현재 브라우저에는 OCI 콘솔 인증 세션이 없어서 ARM 선택·`uname -m`·Podman 빌드 실행은 하지 못했습니다. **Cloud Shell이 불가능하다는 결론이 아니라 실제 세션 검증이 남은 상태**입니다. 기존 IAM은 변경하지 않았습니다.

후속 실검증은 인증된 콘솔에서 Actions → Architecture → ARM 선택 후 `uname -m`, `podman build --platform linux/arm64`, OCIR push를 확인하는 것입니다. 지금은 성공을 확인한 VM 경로를 유지하고 Cloud Shell은 대화형 대안으로 안내합니다.

## B2: 기존 pull Secret으로 OCIR 인증 재사용

등록 빌더 `sustena-arm`에 기존 `sustena-c-oke` 환경과 `personal-services/ocir-pull` 참조만 저장했습니다. 사용자명·Auth token 입력을 모두 비운 채 Companion에서 a829ff9 소스의 ARM 빌드와 `sustena-verify-auth` 저장소 push가 **SUCCEEDED**였습니다. digest는 `sha256:5b528b6231a98fb3d832d36ffdd0a793d7051a123d2f0d36559ae93fade73176`입니다. Secret·IAM·보안 규칙 변경은 없습니다.

- 저장하는 것은 환경·Namespace·Secret 이름이며 인증값은 실행마다 읽습니다. 레지스트리 호스트 불일치, 잘못된 인증 형식, 복수 동일 호스트 항목은 거부합니다. 비밀 버퍼와 실행 임시 인증 파일을 정리합니다.
- 인증값 재입력 없이 동작한 증거: `.tools/verification/screenshots/36-registered-auth-preview.png`, `37-registered-auth-result.png`.
- `./mvnw verify` **383개 통과**. 호스트 선택·오류 비밀값 미노출·버퍼 초기화와 기존 빌드 순서/실패 정리 테스트가 포함됩니다.
- 검증 저장소는 다음 운영 서비스 흐름 검증에 재사용한 뒤 삭제합니다. 운영 서비스 이미지는 이 단계에서 변경하지 않았습니다.

## B3: 등록 서비스에서 커밋 → 빌드 → 배포

운영 `sustena-c`를 첫 서비스로 등록했습니다. 소스·브랜치, ARM 빌더, 이미지 저장소, OKE 환경·조회 연결, 배포 구성, 기존 NodePort 31083과 해당 보안 목록 규칙을 참조로 저장합니다. 이 등록은 운영 이미지나 보안 규칙을 바꾸지 않습니다.

별도 `sustena-verify-service`에서 화면으로 a829ff9 커밋 선택 → 등록 Secret 인증으로 ARM 빌드·push → 생성 digest의 배포 미리보기 → 기존 파이프라인 명세 갱신·배포 → 앱 상태 조회를 완료했습니다. 빌드와 OCI 배포 모두 `SUCCEEDED`, 두 ARM 노드의 Pod는 **2/2 Ready**였습니다. digest는 B2와 같은 `sha256:5b528b6231a98fb3d832d36ffdd0a793d7051a123d2f0d36559ae93fade73176`이며 테스트 명령은 Python AST 구문 검사입니다. 전체 앱 테스트를 실행한 것은 아닙니다.

- 배포 미리보기는 현재 Deployment 설정을 보존하며 Service·Namespace 객체를 포함하지 않습니다. 파이프라인 변경 감지와 산출물 etag 검사, 공통 배포 사전 검사, 세션·범위에 묶인 일회용 확인을 거칩니다. 응답이 불확실하면 자동 재실행하지 않습니다.
- 첫 등록 시 초기 배포가 아직 접수되지 않아 Deployment 조회가 실패했습니다. OCI 이력이 0건임을 확인한 뒤 초기 배포를 실행했고, 완료 후 등록이 성공했습니다. 대기 중 Deployment가 없을 때의 안내와 화면을 벗어난 실행 결과 복귀는 B4에서 보강합니다.
- 운영 Deployment·Service의 전체 spec과 ConfigMap 데이터는 사전 백업과 동일하며 운영 Ready 2를 재확인했습니다.
- 스크린샷: `38-production-service-registered.png`, `41-verification-service-registered.png`, `42-service-build-result.png`, `43-service-deploy-preview.png`, `44-service-deploy-result.png`, `45-service-ready.png`.
- `./mvnw verify`: **390개 통과**, 실패·오류·건너뜀 0. 검증용 B2/B3 리소스 정리 결과는 아래 B4에 기록했습니다.


## B4: 실제 사용 흐름 보완과 최종 정리

실행 화면을 벗어난 뒤에도 서비스를 선택해 최근 실행으로 돌아갈 수 있습니다. 최근 시작·조회한 실행 20개를 사용자·리전·구획별로 저장하며, 링크를 열면 기존 빌드·배포 실행기의 현재 상태를 다시 조회합니다.

이번 실제 사용에서 확인해 고친 항목:

- 서비스에 보관한 배포 구성을 **이 서비스 구성으로 편집**으로 복원합니다. 같은 OKE 환경에 여러 앱을 구성한 뒤에도 다른 앱의 마지막 입력을 잘못 이어 쓰지 않도록 했습니다. 실제 `sustena-c` 구성을 복원했고 클라우드 변경은 없었습니다.
- ARM 빌더 목록에 호스트와 인증 방식·Secret 이름 참조를 보여 주며 운영 서비스로 돌아갈 수 있게 했습니다. 인증값은 표시하지 않습니다.
- 초기 Deployment가 있어야 서비스 등록이 가능하다는 안내와, 실패 시 배포 이력·사설 API·빌더 인증·OCI 연결로 이동하는 경로를 추가했습니다. 오류 화면에 공통 스타일을 적용했습니다.
- OCI Git 사용자명에 도메인을 포함하는 예시를 소스 push 입력에 추가했습니다.
- 서비스 등록 해제를 추가했습니다. 로컬 등록만 해제하며 클라우드 자원과 기존 실행은 삭제·취소하지 않습니다. 테스트는 해제 후 배포 계획 거부, 이력 보존, 구성 복원 시 클라우드 변경 없음, 재시작 후 이력·사용자 범위 분리를 확인합니다.

전체 `./mvnw verify` **392개 통과**, 실패·오류·건너뜀 0. 실제 서버 재시작 후 저장된 서비스·빌더를 사용했고 실행 복귀, 인증 참조 표시, 운영 구성 복원, 검증 서비스 등록 해제를 화면으로 확인했습니다. 스크린샷은 `46-service-history.png`, `47-builder-auth-reference.png`, `48-production-configuration-restored.png`, `49-production-service-retained.png`입니다.

최종 정리: B2/B3의 `sustena-verify-service` Deployment·ClusterIP Service·Pod, 같은 이름의 DevOps 파이프라인·단계·명세, `sustena-verify-auth` OCIR 저장소와 VM 이미지 태그 두 개를 삭제했습니다. 이름으로 재조회한 검증용 OCI·Kubernetes 리소스는 **0개**입니다. 검증 서비스 등록과 두 검증 OKE 조회 연결도 해제했습니다. 운영 Ready는 Sustena 2·Knowledge MCP 2·Finance MCP 1이며, 운영 Deployment·Service·ConfigMap 비교 결과 변경 없음입니다. B 단계에서는 IAM·보안 규칙을 생성하거나 수정하지 않았습니다.

운영 `sustena-c` 등록, `sustena-arm` 빌더의 기존 Secret 참조, `sustena-c-companion` 파이프라인·명세는 유지합니다. 로컬 설정은 이 작업 트리의 `.tools/verification/data`에 있으므로 이어 사용할 때 같은 `--companion.data-dir`을 지정해야 합니다. 비밀값을 포함할 수 있는 런타임 파일과 백업은 Git에서 제외합니다. 검증용 서버와 브라우저는 작업 종료 때 중지합니다.

남은 한계는 B1에 기록한 실제 Cloud Shell ARM 이미지 빌드·Functions 내부 이미지 생성 미검증, IAM이 없는 환경의 생성·전파 과정 미검증, NodePort 검증용 포트의 외부 HTTP 미검증, 이번 ARM 빌드 테스트가 AST 검사에 한정된다는 점입니다. 현재 성공을 확인한 자동 빌드 경로는 ARM VM입니다.

## 초기 검증 결론

- Companion 화면으로 끝까지 간 단계: API 키·세션 프로필 연결, DevOps 프로젝트·코드 저장소·로그·빌드 파이프라인·빌드 명세·빌드 단계·OCIR 전달 생성, 빌드 실행과 로그 확인, OKE 배포 환경(사설 엔드포인트) 생성, OKE 배포 파이프라인 저장과 실행, 앱 상태·노드·Pod 로그 조회.
- Companion만으로는 서비스가 뜨지 않았습니다. 막힌 지점은 다섯 곳입니다.
  1. 파이프라인 리소스 주체용 IAM(동적 그룹·정책)이 없으면 빌드가 명세 조회 단계에서 실패. Companion은 사용자 그룹 정책 예시만 보여 줍니다.
  2. OCI Managed Build 이미지가 x86 전용이라 ARM64 이미지를 빌드할 수 없습니다.
  3. 배포 명세의 이미지 주소가 `yny.ocir.io`(리전 키)라 `ap-chuncheon-1.ocir.io`용 pull Secret으로 인증되지 않습니다.
  4. Secret을 파일 볼륨으로 붙이는 입력(ADB 지갑)이 없고, 프로브에 Host 헤더를 넣을 수 없습니다.
  5. 서비스 노출은 ClusterIP와 기존 Ingress 연결뿐이라 NodePort를 만들 수 없습니다.
- 사설 API 클러스터는 Companion 앱 서버가 API에 직접 닿아야 참조 확인·상태 조회가 됩니다. 이번에는 kubectl 실행 파일 설정(`companion.kubectl`)을 SSH 터널 래퍼로 바꿔 우회했습니다.

우선순위 제안:

1. 파이프라인 IAM 사전 점검과 생성 명령 제공(아래 [IAM](#파이프라인을-처음-쓸-때-필요한-iam)).
2. 배포 명세 이미지 주소를 pull Secret의 레지스트리 호스트와 맞추기(리전 이름 형식 선택 또는 점검).
3. Secret 볼륨 마운트·프로브 헤더·보안 컨텍스트 입력.
4. 사설 API용 접속 주소 재정의(server·tls-server-name)를 배포 구성 화면에도 적용.
5. NodePort Service 생성(포트 지정, 기존 포트 충돌 검사).
6. ARM 빌드 경로(ARM 러너 지원 확인 또는 등록된 ARM VM 빌더).

## 단계별 결과

스크린샷은 비공개 경로 `~/.local/state/oke-joungminkokor/sustena/companion-shots/`에 있습니다(번호는 파일 이름 앞부분).

| 단계 | Companion에서 한 일 | 결과 | 메운 방법 |
|---|---|---|---|
| 프로필 연결 | 새 API 키 프로필 `JOUNGMINKOKOR_API`, 세션 프로필 `JOUNGMINKOKOR` 연결(01, 02) | 성공. 세션 프로필은 이번 작업의 보강 커밋 `7b9ee67` 이후 연결됨 | 보강 전에는 `user`가 없는 세션 프로필을 SDK 공급자가 읽지 못함(코드 확인, 보강 전 화면 재현은 안 함) |
| DevOps 준비 | 알림 토픽 `sustena-devops`, 프로젝트 `sustena`, 코드 저장소 `new-sustena`, 로그 그룹·프로젝트 로그 생성(08–12) | 성공 | — |
| 소스 | OCI 코드 저장소에 소스 push | Companion에 Git push 기능 없음. 저장소 자동 초기 커밋과 충돌해 `main`은 non-fast-forward 거부 | Git CLI로 `gitea-main`, `companion-build` 브랜치에 push(강제 push 없음) |
| 빌드 명세 | 명세 작성 화면에서 Python·ARM64·컨테이너 이미지·Dockerfile 선택(14, 15) | YAML 생성 성공. 화면 안내의 "Git 저장" 버튼은 이 구성(OCI 코드 저장소)에서 보이지 않음 | 생성된 YAML을 `deploy/oci/build_spec.yaml`로 커밋 |
| 빌드 단계·전달 | 빌드 단계 `build-arm64`, OCIR 산출물 `sustena-ocir`, Registry 전달 연결(16–19) | 성공. 빌드 이미지 선택지는 `OL7_X86_64_STANDARD_10`, `OL8_X86_64_STANDARD_10`뿐 | — |
| 빌드 실행 1·2 | `companion-arm64-1`, `-2` 실행(20–22, 30) | FAILED: IAM 없음(아래 오류 1). 화면의 단계 표시는 실패 후에도 `ACCEPTED`로 남음 | 오너 승인 후 동적 그룹·정책 생성 |
| 빌드 실행 3 | `companion-arm64-3` 실행(31) | FAILED: `Build architecture: x86_64`, `Architecture mismatch: use a matching build environment` | Trilium VM(aarch64)에서 rootless podman 빌드·push. `oke/sustena:a829ff9` |
| OKE 배포 환경 | 클러스터 사양 확인, 사설 접속 서브넷 `oke-shared-workers`로 환경 `sustena-c-oke` 생성(25–29) | 성공(CREATING → ACTIVE 약 3분) | — |
| 배포 구성 | 이미지 버전(a829ff9 digest)·ARM64·복제 2·포트 8845·`/readyz`·`/healthz`·사설 엔드포인트 입력(33–36) | Namespace 리소스 조회 실패 → Secret·ConfigMap 선택 목록이 비고 저장 버튼 비활성("필수 참조 확인 후 다시 구성하세요") | `companion.kubectl`을 SSH 터널 래퍼로 바꿔 재시작. 한 번은 secrets 조회가 시간 초과로 "미확인", 재시도에서 확인됨 |
| 배포 실행 1 | 파이프라인 `sustena-c-oke` 실행(37–41) | FAILED(Check deployment status): `ImagePullBackOff`(아래 오류 3) | `yny.ocir.io`용 pull Secret `ocir-pull-yny`를 기존 인증으로 별도 생성(기존 `ocir-pull`은 변경 안 함) |
| 배포 실행 2 | 파이프라인 `sustena-c-oke-2`(pull Secret `ocir-pull-yny`) 실행(42) | 이미지 pull 성공, 컨테이너 재시작 반복: `ValueError: Wallet directory must contain tnsnames.ora` | CLI `kubectl patch`로 지갑 Secret 볼륨, `/tmp` emptyDir, 보안 컨텍스트, 프로브 Host 헤더 추가. 패치 후 롤아웃이 OCI 상태 확인 시간 안에 끝나 실행 2는 SUCCEEDED로 기록됨 |
| 노출 | 구성 화면의 Service 선택지 확인 | ClusterIP와 기존 Ingress 연결만 있음. NodePort 입력 없음 | CLI로 NodePort Service `sustena-c-nodeport`(31083), 워커 보안 목록에 Trilium 사설 IP/32 → 31083 규칙 추가(변경 전 규칙 백업), Trilium nginx upstream을 31083으로 전환(`.bak` 보관) |
| 운영 확인 | OKE 조회 연결 등록, 앱 상태·노드·경고 이벤트·Pod 로그 조회(43–47) | 성공(터널 래퍼 사용). 첫 등록 중 Mac 절전으로 끊겨 같은 앱이 두 번 등록됨 | — |

## 파이프라인을 처음 쓸 때 필요한 IAM

이번 테넌시에는 `Tenant Admin Policy` 하나만 있었고 동적 그룹과 거부 정책은 없었습니다. 오너 승인(2026-11-25) 후 OCI CLI로 아래를 만들었습니다. 범위는 테넌시 루트입니다(DevOps 리소스를 루트 구획에 만들었기 때문). 별도 구획을 쓰면 `in compartment <구획>`으로 좁히는 편이 좋습니다.

동적 그룹 `sustena-devops`:

```text
ALL {resource.compartment.id = '<tenancy>',
     ANY {resource.type = 'devopsbuildpipeline',
          resource.type = 'devopsdeploypipeline',
          resource.type = 'devopsrepository'}}
```

정책 `sustena-devops`(테넌시):

| 정책 문장 | 필요한 이유 | 이번에 확인한 것 |
|---|---|---|
| `Allow dynamic-group sustena-devops to manage devops-family in tenancy` | 빌드 파이프라인이 코드 저장소에서 소스·빌드 명세를 읽고 빌드 실행·전달을 기록 | 없을 때 오류 1. 추가 후 소스 다운로드·명세 파싱 성공 |
| `Allow dynamic-group sustena-devops to manage repos in tenancy` | Registry 전달 단계의 OCIR push | 이번 빌드는 아키텍처 검사에서 멈춰 전달까지 가지 않음. 미확인 |
| `Allow dynamic-group sustena-devops to read all-artifacts in tenancy` | 배포 파이프라인이 DevOps 산출물(인라인 Kubernetes 명세)을 읽음 | 배포 실행에서 명세 적용 성공. 없을 때의 오류는 미확인 |
| `Allow dynamic-group sustena-devops to use ons-topics in tenancy` | 빌드·배포 이벤트를 프로젝트 알림 토픽으로 보냄 | 없을 때의 오류는 미확인 |
| `Allow dynamic-group sustena-devops to manage cluster-family in tenancy` | 배포 파이프라인이 OKE에 명세를 적용하고 롤아웃 상태를 확인. 공용 클러스터 전체에 미치는 넓은 권한 | 배포 실행에서 적용·상태 확인 성공. 없을 때의 오류는 미확인 |
| `Allow dynamic-group sustena-devops to use virtual-network-family in tenancy` | 사설 API 클러스터에 접속하려고 배포 환경의 사설 엔드포인트(VNIC)를 워커 서브넷에 만듦 | 배포 실행에서 사설 엔드포인트 경유 적용 성공. 없을 때의 오류는 미확인 |

정책을 만든 직후(약 40초 뒤) 실행한 빌드 2도 오류 1로 실패했고, 약 6분 뒤 실행한 빌드 3에서 반영됐습니다. 정책 전파 대기가 필요합니다.

사용자(프로필) 쪽 권한은 이번 프로필 사용자가 관리자 그룹이라 따로 만들지 않았습니다.

### 실제로 본 오류 원문

1. 동적 그룹·정책 없음(빌드 1·2, 22번 화면):

   ```text
   Unable to fetch build_spec file deploy/oci/build_spec.yaml due to RelatedResourceNotAuthorizedOrNotFound. Please check if dynamic groups and the corresponding policies are properly configured.
   Example of Required Policies :
    "Allow dynamic-group <buildPipelineDynamicGroup> to manage devops-family in tenancy"
   "Allow dynamic-group <externalConnectionDynamicGroup> to read secret-family in tenancy"
   ```

2. x86 빌드 러너에서 ARM 명세 실행(빌드 3, 31번 화면):

   ```text
   EXEC: Build architecture: x86_64
   EXEC: Architecture mismatch: use a matching build environment
   Step 'Build and test' failed with exit code: '1', please check the commands on the spec file.
   ```

3. 이미지 주소와 pull Secret 호스트 불일치(배포 1, kubelet 이벤트):

   ```text
   Failed to pull image "yny.ocir.io/<namespace>/oke/sustena@sha256:…": … reading manifest … in yny.ocir.io/<namespace>/oke/sustena: denied: Anonymous users are only allowed read access on public repos
   ```

4. 지갑 볼륨 없음(배포 2, 컨테이너 직전 로그):

   ```text
   ValueError: Wallet directory must contain tnsnames.ora
   ```

5. 사설 API에 닿지 않을 때의 참조 확인(배포 구성, 36번 화면): `configmaps/sustena-config 미확인 · 목록 조회 불가 · configmaps list 권한과 API 연결 확인`, 이어서 `필수 참조 확인 후 다시 구성하세요.` 리소스 조회 응답은 `"services 조회 실패 · list 권한·API 연결·500개 한도 확인"`.

### Companion이 지금 해 주는 것과 해 주지 않는 것

- 해 주는 것: 빌드 실행 결과 화면에 OCI 오류 원문 표시. "권한 설정" 안내에 사용자 그룹용 정책 예시(`Allow group id <GROUP_OCID> to read devops-family …`, `… manage devops-build-run … where request.permission = 'DEVOPS_BUILD_RUN_CREATE'`, 로그 조회 등)와 콘솔 작업 순서 표시. 화면 문구대로 "앱은 정책을 변경하지 않습니다".
- 해 주지 않는 것: 파이프라인 리소스 주체용 동적 그룹·정책 예시, 현재 정책에 필요한 문장이 있는지 점검, 동적 그룹·정책 생성. 실행 전 "실행 권한 · 미확인"으로만 표시되고 실제 실패는 실행 후에 알 수 있습니다.

### 보강 제안

- 파이프라인 생성 직후 또는 실행 전에 테넌시·구획 정책을 읽어 위 문장(또는 동등한 상위 권한)이 있는지 점검하고, 빠진 줄만 보여 주기.
- 동적 그룹 규칙과 정책 문장을 현재 구획 OCID로 채운 OCI CLI 명령 복사 버튼. 권한이 있는 사용자에게는 미리보기 후 생성 버튼(기존 정책 수정 없이 새 정책으로).
- 정책 생성 직후 전파 대기를 안내하고, 실패가 `RelatedResourceNotAuthorizedOrNotFound`이면 IAM 점검 화면으로 연결.
- 빌드 실행이 FAILED이면 단계 표시도 FAILED·미실행으로 갱신(지금은 `ACCEPTED`로 남음).

## ARM64 이미지 빌드

- 확인한 사실: OCI Java SDK 3.94.1의 `BuildStage.Image` 값은 `OL7_X86_64_STANDARD_10`, `OL8_X86_64_STANDARD_10` 두 가지이고, OCI CLI(`oci devops build-pipeline-stage create-build-stage --image`) 모델도 같습니다. 실제 러너의 `uname -m`은 `x86_64`였습니다.
- Companion 명세는 아키텍처가 다르면 의도대로 중단합니다(교차 빌드 미지원). 그래서 OKE A1 노드용 이미지는 Companion 빌드 경로로 만들 수 없습니다.
- 지금 쓰는 우회: Trilium VM(aarch64)에서 `podman build --platform linux/arm64` → `podman push --digestfile` → Companion 배포 구성의 이미지 버전 목록에서 그 digest 선택. 이 경로에서 테스트(155개)는 같은 VM의 `python:3.13-slim` 컨테이너에서 실행했습니다.
- 보강 제안: (1) OCI가 ARM 러너를 지원하는지 주기적으로 확인하는 점검과, 지원 시 명세 템플릿에 반영. (2) 등록한 ARM VM을 원격 빌더로 쓰는 실행기(소스 전달·빌드·push·digest 기록을 한 화면에서). (3) x86 러너에서 buildx·QEMU 교차 빌드 선택지(느리고 결과 검증이 더 필요함을 명시).

## 배포 구성의 나머지 공백

- 이미지 주소: 배포 명세와 산출물 경로가 `yny.ocir.io/…`(리전 키)로 만들어집니다. 클러스터의 기존 pull Secret이 `ap-chuncheon-1.ocir.io`만 갖고 있으면 인증이 실패합니다. 선택한 pull Secret의 레지스트리 호스트를 읽어 이미지 주소와 맞는지 점검하거나, 주소 형식을 고를 수 있어야 합니다.
- 생성되는 Deployment에 없는 항목: Secret·ConfigMap 파일 볼륨, emptyDir, Pod·컨테이너 보안 컨텍스트(`runAsNonRoot`, `readOnlyRootFilesystem`, `capabilities.drop`), `automountServiceAccountToken: false`, 프로브 HTTP 헤더, 프로브 시간 제한. Host 헤더를 검사하는 앱(Starlette `TrustedHostMiddleware`)은 헤더 없이 프로브가 실패합니다. 명세에 Namespace 객체도 함께 들어가므로 공용 Namespace에 배포할 때 소유권이 섞이지 않는지 안내가 필요합니다.
- CLI 패치는 다음 Companion 배포 실행에서 덮어써집니다. 지금 운영 중인 `sustena-c`를 Companion으로 다시 배포하면 지갑 볼륨이 빠져 같은 오류 4가 납니다. 위 입력이 보강되기 전에는 재배포 후 같은 패치(`kubectl patch deploy sustena-c --type strategic --patch-file …`)를 다시 적용해야 합니다.
- 노출: NodePort Service 생성과 포트 지정이 없습니다. 보안 목록 규칙 추가·제거도 없습니다.
- 사설 API: OKE 조회 연결(상태·로그)에는 내부적으로 server·tls-server-name 재정의가 있지만 화면 입력이 없고, 배포 구성의 Namespace 리소스 조회에는 적용되지 않습니다. 앱 서버가 사설 API에 직접 닿지 않으면 참조 확인이 막혀 저장할 수 없습니다. 이번 우회 래퍼는 `--server`가 없으면 `--server=https://127.0.0.1:<터널 포트> --tls-server-name=<API 사설 IP>`를 붙여 실제 kubectl을 실행합니다.
- 참조 확인 시간 제한: kubectl 한 번에 8초 제한이고 5종 조회를 동시에 실행합니다. 매 호출마다 `oci ce cluster generate-token`이 돌아 터널 경유 시 한 번 시간 초과가 났습니다. 재시도 버튼이나 더 긴 제한이 필요합니다.

## 이번 작업의 Companion 코드 변경

- `7b9ee67` feat: OCI 세션(security_token) 프로필 지원 — `security_token_file`이 있는 프로필을 `ST$<token>` 키 ID로 서명하고, 토큰 파일을 요청마다 다시 읽음. OKE kubeconfig의 `--auth` 값도 프로필에 맞춤. `./mvnw verify` 330개 통과. Gitea `joungmin/oci-companion`(비공개) 브랜치 `devmrko/sustena-deploy`에 push, GitHub에는 push하지 않음.

## 확인 방법과 한계

- 오류 원문은 Companion 빌드·배포 결과 화면, OCI CLI `build-run get`·`deployment get`, kubelet 이벤트와 컨테이너 직전 로그에서 옮겼습니다. 이미지 digest·OCID는 줄였습니다.
- 위 IAM 표에서 "없을 때의 오류는 미확인"인 줄은 정책을 한 번에 만들어 개별 효과를 나눠 보지 않았다는 뜻입니다. 최소 권한 조합은 검증하지 않았습니다.
- ARM 러너 미지원은 SDK·CLI 모델과 실제 러너 1회 실행으로 확인했습니다. OCI 콘솔에서 다른 이미지가 노출되는지는 보지 않았습니다.
- 실제 Google 로그인과 X-Forwarded-For의 실제 클라이언트 IP 반영은 사람이 확인해야 합니다(앱 접근 로그 없음).


## 2026-11-26 ARM Function 이미지 후속 검증

Function에서 Sustena a829ff9의 테스트 155개, ARM64 레이어 생성, OCIR push와 최초 별도 배포 2/2 Ready·readyz 200을 확인했습니다. 등록 빌더에서 Function을 선택할 수 있고 운영 기본 빌더를 Function으로, VM을 대체 경로로 유지했습니다. [실제 시간·오류·유지 자원](../function-container-image-validation.md)을 참고하세요.

새 오류: 등록 서비스 재배포는 OCI `SUCCEEDED`와 달리 최신 built digest가 실제 Deployment에 적용되지 않았습니다. 이전 artifact 실행 스냅샷이 사용됐고 새 artifact/stage 참조 실험도 실패해 되돌렸습니다. 이 재배포 공백은 남아 있습니다. Function 이미지 빌드 성공과 최신 이미지 재배포 성공을 구분합니다.
