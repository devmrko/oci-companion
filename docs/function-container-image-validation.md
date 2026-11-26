# ARM Function에서 Sustena 이미지 생성·배포 검증

2026-11-26, `JOUNGMINKOKOR_API` / 춘천에서 **ARM Function 내부의 테스트·컨테이너 이미지 생성·OCIR push에 성공**했습니다. Function 결과 이미지로 만든 별도 `sustena-verify-function` Deployment는 **2/2 Ready**, Service 경유 `/readyz`는 **HTTP 200**이었습니다. 운영 `sustena-c`는 이번 검증에서 변경하지 않았습니다.

기존 [ARM Function Maven 검증](arm-function-build-validation.md)의 성공 경로를 이미지 생성까지 확장한 결과입니다. 컨테이너 데몬이 없다는 이유만으로 이미지 생성까지 불가능하다고 판단할 수 없으며, 이번에는 레이어 추가 방식으로 확인했습니다.

## 실제 실행 위치와 결과

- 소스: Sustena `a829ff928af94ea1275e27170fdf6b0d067429b0`.
- 실제 Function: ARM 앱 `sustena-verify-function-image`의 `image-builder`, 3GiB, 동기 호출 최대 300초, Provisioned Concurrency 없음.
- probe: `aarch64`, UID **1000**, Python **3.13.18**, 임시 공간 약 768MiB. `uv`, `crane` 있음, Docker·Podman 없음.
- Function 안에서 잠긴 의존성·앱 설치 → 전체 pytest → 개발 의존성 제거 → 약 75MB 설치 파일의 tar 레이어 → `crane append/mutate` → OCIR push.
- 테스트 **155개**, 실패·오류·건너뜀 **0**. Function 처리 **149.65초**, 동기 API 호출 전체 **149.78초**.
- 최초 성공 digest: `sha256:1e16866415d53b948e2118f94d824a4df288c850c0567da8b8baa19f5baf968e`.
- Companion 화면의 별도 호출도 성공했습니다. 소스 전달·호출·OCIR 확인까지 **158.48초**, digest `sha256:6aab26c002e862b216fb584ea02bc33aef07aa3a439acc16b79fa668f294f470`.

VM은 최초 Function 실행 이미지를 준비하는 데만 사용했습니다. 해당 이미지에는 Python·fdk·uv·crane 및 잠긴 의존성을 넣었습니다. 위 Sustena 결과 이미지는 Function 내부에서 만들었으며, Companion의 호출 경로는 VM을 사용하지 않고 서버의 crane으로 소스 묶음만 전달합니다.

기반 이미지는 ARM64 Python 3.13 digest `sha256:23b1477718ab33f0a4ce93553ba085a09ac7ea65171c7f4af028a9aca223ab5d`로 고정했습니다. crane v0.20.6의 Linux/Darwin ARM64 아카이브는 공식 릴리스 체크섬과 비교했습니다.

## 실패에서 보완한 내용

| 시도 | 시간 | 실제 오류와 조치 |
|---|---|---|
| Function 생성 | — | `400 InvalidParameter: Invalid Invalid Image - Image is not a valid docker image name`; API 이미지 태그와 `imageDigest`를 별도 필드로 지정하자 생성 성공 |
| 첫 빌드 | 호출 5.08초, 핸들러 4.74초 | `/tmp/.../pydantic_core/_pydantic_core.cpython-313-aarch64-linux-gnu.so: failed to map segment from shared object`; 실행 이미지에 동일 잠금 파일의 의존성을 준비하고 앱 소스는 그 인터프리터로 테스트 |
| 두 번째 빌드 | 호출 135.77초, 핸들러 131.05초 | `FileNotFoundError: .../docs/design/exercise/assets/0006/evidence.md`; 테스트 154개 통과·1개 실패. 소스 묶음에 테스트 참조 문서도 포함 |
| 세 번째 빌드 | 호출 149.78초 | 155개 테스트·레이어 생성·push 성공 |

실패한 테스트 호출은 push 단계로 진행하지 않았습니다. 레이어 추가 방식으로 성공했으므로 kaniko·buildah는 시도하지 않았으며, 그 도구의 Functions 지원 여부도 이번 결과로 판단하지 않습니다.

초기 OKE 배포 구성 생성 직후 실행 요청은 Companion의 `CLOUD` 오류 화면으로 끝났습니다. OCI 실행 이력이 0건임을 확인한 뒤 대상을 다시 조회해 새 확인 티켓으로 요청했고 `SUCCEEDED`가 됐습니다. 최초 요청 실패의 정확한 원인은 확정하지 않았습니다.

## Companion에서 사용하는 방법과 제한

`ARM 이미지 빌드 → ARM 빌더 등록`에서 **ARM Function · Sustena 레이어**를 선택합니다. `sustena-layer-v1` 계약의 기존 Function과 OKE pull Secret 참조를 선택하고, 운영 서비스 등록에서 해당 빌더를 고릅니다. 소스 저장소·커밋 검증, 인증 조회, 실행 기록, OCIR digest 재확인, 기존 구성 유지 재배포는 공통 코드로 이어집니다. VM 빌더는 대체 경로로 유지합니다.

- 이 경로는 고정된 Sustena 레시피이며 임의 Dockerfile의 `RUN`이나 입력한 VM 테스트 명령을 실행하지 않습니다. 전체 pytest를 실행합니다.
- Function 이미지의 `uv.lock` 해시와 소스 잠금 파일이 다르면 실행을 거부합니다. 의존성 변경에는 Function 실행 이미지 갱신이 필요합니다. 임시 디렉터리에서 새 네이티브 의존성을 실행하는 일반 빌더는 아닙니다.
- Companion 서버에는 crane이 필요하며 `companion.crane` 경로로 지정합니다. 소스 전달용 이미지 태그에는 별도 보존·정리 정책이 필요합니다.
- Function의 단일 출력 저장소·소스 저장소와 구획·ARM shape·계약·설정 버전을 확인합니다. Function 호출은 자동 재시도하지 않습니다. 결과를 알 수 없으면 OCI·OCIR을 확인해야 합니다.
- 기존 `personal-services/ocir-pull`은 읽기만 했습니다. 인증값은 호출 중에만 전달했고 source/test 프로세스 환경·이미지·커밋에 넣지 않았습니다. 소스 다운로드 인증 파일은 테스트 전에, push 인증 파일은 push 종료 때 삭제했습니다.
- 새 IAM·동적 그룹·보안 규칙은 만들거나 수정하지 않았습니다. 기존 사설 서브넷의 NAT 경로를 사용했습니다.

## 근거

로컬 `.tools/function-image/`에 `probe-result.json`, 실패 원문 `attempt-1-noexec.json`, `attempt-2-missing-evidence.json`, 성공 `build-result.json`, `companion-ui-result.json`, `readyz-result.json`을 보관합니다. `.tools/verification/screenshots/`의 50–58번 화면은 Function 등록·빌드·별도 배포·서비스 연결 과정입니다. 전체 식별자·인증값을 문서에 기록하지 않습니다.

구현 설계는 [Function 이미지 경로](design/function-container-images.md), 준비 파일은 `src/main/resources/fn-image-template/`에 있습니다. 등록 서비스 재배포와 자원 정리 결과는 아래에 기록합니다.


## 재배포 digest 불일치에서 보완한 내용

등록 서비스의 첫 Function 빌드는 152.33초에 성공했지만, OCI가 재배포를 `SUCCEEDED`로 표시한 뒤 실제 Deployment에는 최초 이미지 digest가 남았습니다. 최신 실행 ID로 조회한 OCI 로그의 `Artifact snapshot`에도 이전 digest가 들어 있었습니다. 반면 `GetDeployArtifact`의 현재 내용은 새 digest였습니다. 따라서 OCI 성공 상태와 Ready 개수만으로 이미지 교체까지 성공했다고 판단할 수 없었습니다.

새 artifact 생성 후 stage 참조를 바꾸는 실험에서도 최신 실행 스냅샷이 이전 artifact를 사용했습니다. 실제 Deployment는 직전 Function 결과인 `4c165bb4…`로 바뀌었지만 해당 실행에서 선택한 `dd2d3489…`와 달랐습니다. 효과가 입증되지 않은 구현 변경은 되돌렸습니다. 최종 2/2 Ready·readyz HTTP 200은 확인했으나 **최신 digest 적용은 실패**했으므로 등록 서비스 재배포가 해결됐다고 보고하지 않습니다. OCI 내부 전파·스냅샷 원인은 미확정입니다.

[OCI artifact 편집 문서](https://docs.oracle.com/en-us/iaas/Content/devops/using/edit_artifact.htm)는 업데이트 API를 설명하지만 이번 이전 스냅샷 사용 원인을 설명하지 않습니다.

## 유지한 빌드 경로와 정리

오너 지시에 따라 Function 앱 `sustena-verify-function-image`와 `image-builder`는 유지합니다. 앱 이름 변경은 SDK UpdateApplicationDetails에서 지원하지 않아 기존 이름을 유지했습니다. 실행·소스 저장소는 비공개 `companion/sustena-function-builder`, 출력은 기존 `oke/sustena`입니다. Companion 등록 `sustena-c`의 기본 빌더는 `sustena-function`, VM 대체 경로는 `sustena-arm`입니다. 인증은 기존 pull Secret 참조입니다.

기존 ARM VM으로 최신 템플릿을 보내는 작업은 자동 승인 검토에서 거절됐고 실행하지 않았습니다. 대신 검증된 runtime-v2 이미지를 같은 테넌시의 영구 OCIR로 복사한 뒤 템플릿 코드 레이어만 추가했습니다. 이 과정에는 VM 파일 전송이나 의존성 재빌드가 없었습니다. 중단 지시가 도착하기 전에 runtime-v3 갱신과 검증 호출이 시작됐으며, 진행 중 호출은 **153.55초**에 성공으로 종료됐습니다. runtime-v3 digest는 `sha256:d13d0e6485fa841c875e792867ae90b126b21c4c91f8b975eb08c91db0101d4d`, 이 호출의 앱 이미지 digest는 `sha256:83447ba030eb2cfb0a7359b97c985c2082034894208031baacf823538c9605c5`입니다.

검증 OKE Deployment·Service, 검증 DevOps pipeline·artifact, 임시 OCIR 두 저장소는 삭제했습니다. 기존 ARM VM의 이번 `/tmp` 준비 폴더와 runtime-v1/v2 로컬 태그도 삭제했습니다. Function과 영구 실행 이미지는 남깁니다. 영구 소스 저장소의 호출별 전달 태그는 별도 정리 정책이 필요합니다.

최종 코드 `./mvnw verify`는 **395개 통과**, Python 단위 테스트는 **5개 통과**입니다. 스크린샷 50–64는 로컬 `.tools/verification/screenshots/`에 있습니다. 59–61의 OCI 성공·Ready 화면은 최신 digest 적용 성공의 증거가 아닙니다. 최종 digest 불일치와 readyz 결과는 `.tools/function-image/readyz-result.json`에 기록했습니다.


## 운영 재배포 추가 지시와 최초 승인 대기

정리와 구현 push 후 받은 추가 지시에 따라 운영 Deployment·Service·ConfigMap을 비밀값 없이 `.tools/verification/production-function-backup/`에 백업하고 공개 readyz 200을 확인했습니다. 등록 서비스 `sustena-c`에서 운영과 같은 `a829ff9`를 Function으로 빌드했고 **155.98초**에 성공했습니다. 결과는 기존 `oke/sustena` 저장소의 `sha256:d52337647c0c6a9a710d77c4e025a97636caa8f3354a13f9d74d492b013b3098`입니다.

실제 운영 배포 제출은 자동 승인 검토가 거절했습니다. 원래 사용자 지시의 운영 변경 금지와 충돌하고 코디네이터 도구 출력으로 전달된 추가 승인을 직접 사용자 승인으로 인정하지 않는다는 사유입니다. 이 시점에는 **운영 재배포 미실행**이었으며 성공·복구를 주장하지 않았습니다. 빌드 결과와 배포 미리보기는 스크린샷 65–67에 보관했습니다. 이후 오너가 작업 세션에 직접 승인했고 아래 결과로 이어졌습니다.


## 직접 승인 후 운영 배포 결과 — 실패, 기존 상태 유지

2026-11-26 오너가 이 작업 세션에 직접 운영 배포와 실패 시 복구를 승인한 뒤, 등록 서비스에서 위 `d5233764…` digest로 배포를 실행했습니다. OCI는 `SUCCEEDED`였지만 실제 실행 snapshot과 두 Pod의 `imageID`는 모두 이전 이미지였습니다.

| 확인 항목 | 결과 |
|---|---|
| 기대 digest | `sha256:d52337647c0c6a9a710d77c4e025a97636caa8f3354a13f9d74d492b013b3098` |
| 실제 두 Pod imageID digest | `sha256:d822e917de25d58511296d430e1038a4f5da0c6c0847b1ef66947f97741d4cfb` |
| Ready | 2/2 |
| `https://sustena.store/readyz` | HTTP 200 |
| Deployment 설정·Service·ConfigMap | 백업과 동일 |
| 최종 판정 | **실패: 이미지 불일치** |

백업 복구 절차에서 Kubernetes 구성이 이미 백업과 같음을 확인해 불필요한 재적용을 하지 않았습니다. 이번 요청이 갱신한 DevOps manifest는 실행 snapshot에 보관된 이전 manifest로 되돌리고 다시 조회해 일치를 확인했습니다. NodePort 31083·Trilium nginx·보안 규칙·기존 Secret은 변경하지 않았습니다. 기존 운영 이미지는 계속 정상 서비스 중이며 Function 결과 이미지의 운영 적용은 완료되지 않았습니다.

근거는 로컬 `.tools/verification/production-function-acceptance.json`, `production-function-execution-log.txt`, `production-function-backup/`, 스크린샷 `65`–`69`입니다. OCI 상태 화면의 SUCCEEDED를 실제 이미지 교체 성공으로 해석하면 안 됩니다. 다음 작업은 배포 후 실제 Pod digest 대조를 제품 성공 판정에 반영하고 새 manifest artifact의 실행 스냅샷 일치 문제를 해결하는 것입니다.

## 후속: 실행 snapshot 불일치 원인 확정

2026-11-26 후속 조사에서 위 미확정 원인을 artifact·stage 비동기 변경 완료 전 배포 시작으로 확인했습니다. 별도 검증에서 새 artifact 생성·stage 변경 work request 완료를 기다리자 기대 이미지가 적용됐고, Pod imageID 기반 성공 판정도 검증했습니다. [배포 결과 신뢰 검증](deployment-digest-verification.md)에 원본 시각과 결과를 기록했습니다. 운영 적용은 아직 하지 않았습니다.
