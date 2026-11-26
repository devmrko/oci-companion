# 기존 설정을 이용한 새 서비스 등록 검증

`운영 서비스 → 새 서비스 자동 설정`에서 소스에 연결된 기존 설정을 선택하면 현재 Deployment의 볼륨·보안·프로브·pull Secret과 등록 빌더를 읽습니다. 클러스터·Namespace와 새 이름을 확인하고 미리보기에서 생성하면 전용 환경·파이프라인·artifact·stage를 준비하고 배포합니다. 실제 Pod digest와 Ready 확인 후 조회 연결과 운영 서비스 등록을 완료합니다.

2026-11-26 `JOUNGMINKOKOR_API`의 춘천 리전에서 `companion-verify-onboard`를 생성했습니다. 설정 근거는 `sustena-c`였으며 운영 리소스는 읽기만 했습니다. 기존 NodePort 31083을 복제하지 않고 새 ClusterIP Service를 생성했습니다. Secret·ConfigMap은 같은 Namespace의 기존 참조를 사용하며 값은 저장하지 않습니다.

## 실제 결과

- 새 OKE 배포 환경, pipeline, manifest artifact, stage를 화면의 생성 흐름에서 생성했습니다.
- 기존 pipeline과 새 pipeline의 IAM을 자동 점검했으며 새 정책·동적 그룹·보안 규칙을 만들지 않았습니다.
- API 조회는 기존 클러스터의 접속 설정·터널을 재사용했습니다. 별도 kubectl 래퍼는 사용하지 않았습니다.
- 두 검증 Pod가 `d52337647c0c…`를 실행하고 2/2 Ready였으며, 임시 포트 포워딩으로 readyz HTTP 200을 확인했습니다.
- 서비스 상태는 REGISTERED였고, 생성한 조회 연결에서 두 Pod와 ARM64 노드를 확인했습니다.
- 스크린샷: `.tools/verification/screenshots/75-onboarding-preview.png`, `76-onboarding-registered.png`.

환경 생성은 최초 60초 제한을 넘어 `OCI 변경 작업 완료 대기 시간 초과`로 멈췄습니다. 생성한 환경 참조를 보존했으며 ACTIVE 확인 후 **기존 환경에서 계속**으로 재개했습니다. 환경 생성 대기는 180초로 늘렸습니다. 완료 여부가 불확실한 일반 실패에는 자동 재실행을 제공하지 않습니다.

재시작 후 미리보기 재검증에서 JSON 키 순서 차이가 설정 변경으로 오인됐습니다. YAML 문서의 의미를 비교하도록 수정했으며 필드·리소스 버전·등록 정보·IAM 변경은 계속 차단합니다. 수정 후 같은 환경에서 새 파이프라인 생성과 배포가 완료됐습니다.

## 검증 범위와 남은 입력

이번 자동 흐름은 **이미 등록된 소스·서비스를 설정 근거로 새 이름의 서비스를 만드는 경우**입니다. 같은 Namespace에서 기존 파일·인증 참조를 재사용합니다. 설정 근거가 전혀 없는 새 소스, 다른 클러스터나 Namespace로의 이관, 신규 Secret 생성, 새로운 NodePort 외부 노출은 상세 설정이 필요합니다. 임의의 소스만 선택하면 첫 설정이 모두 끝나는 범용 자동화까지 완료한 것은 아닙니다.

전체 `./mvnw verify`는 413개 중 412개 통과, 실제 OCI 전용 테스트 1개 기본 skip입니다. 새 테스트는 clone 대상·selector 분리, 볼륨·프로브·보안·인증 참조 보존, 비밀 환경값·tag 이미지 거부, IAM 부족 차단, 변경 감지, 시간 초과의 재개 범위, 명세 표현 순서 독립 비교를 확인합니다.

## 정리

검증 Deployment·Pod·ClusterIP Service, 전용 DevOps environment·pipeline·stage·artifact를 모두 삭제했습니다. 검증 서비스 등록과 조회 연결·kubeconfig도 해제했습니다. 기존 `sustena-c`와 공유 project·기존 OKE environment·빌더·Secret은 유지했습니다.

## Dockerfile만 있는 새 소스의 첫 배포 — 2026-11-26 후속

실행 중인 앱의 설정이 없는 소스도 같은 화면에서 필요한 값을 채워 첫 서비스를 만들 수 있도록 보강했습니다. 배포 YAML → 최종 Dockerfile 단계 → 앱 설정 순으로 포트·프로브·replica·pull Secret 이름을 읽고 각 값의 파일·필드 출처를 표시합니다. 비는 필수값만 입력받으며 Secret은 현재 Namespace의 기존 이미지 인증 Secret 중에서 고릅니다.

기존 `sustena-c` 등록 정보는 클러스터·Namespace·사설 API 연결의 근거로만 사용했습니다. 그 앱의 이미지·볼륨·환경값은 복사하지 않았습니다. 새 OCI 소스 `companion-verify-dockerfile`, 브랜치 `companion-verify`, 커밋 `bb9f5731e3199b6c602c941bf494147a9333d6fb`에는 **Dockerfile 한 개만** 있습니다. 동일 파일을 [재현용 fixture](../src/test/resources/onboarding/Dockerfile)로 보관했습니다.

| 단계 | 실제 결과 |
|---|---|
| 소스 등록 | Companion 소스 화면에서 새 OCI 검증 저장소 등록 |
| 자동 입력 | `8080`의 출처 `Dockerfile · EXPOSE`, `/readyz`의 출처 `Dockerfile · HEALTHCHECK` 표시 |
| 누락값 | 기존 `ocir-pull` 선택, replica `2` 입력 |
| 첫 빌드 | 기존 ARM VM 빌더에서 Dockerfile 내 Python 구문 검사와 ARM64 이미지 build/push, **11.14초 SUCCEEDED** |
| 첫 배포 | 새 environment·pipeline·artifact·stage·Deployment·ClusterIP 생성, Namespace·IAM·보안 규칙·Secret 변경 없음 |
| 완료 확인 | OCI SUCCEEDED + 실제 Pod 2개 imageID 일치 + 2/2 Ready + REGISTERED |
| readyz | 임시 로컬 포트 연결로 HTTP 200; 외부 NodePort를 새로 열지 않음 |
| 재시작 | 로컬 Companion 재시작 후에도 REGISTERED·Pod digest 검증 기록 유지 |

빌드 및 두 Pod의 digest는 `sha256:89ad65b49b604ff81cc7de2b0d54d2dac06f78a78872d1beff4b748536aba891`입니다. 실제 결과는 `.tools/source-onboarding/build-result.json`, `acceptance.json`에 있습니다. 이 첫 설정 검증은 VM 빌더를 사용했으며, Function의 의존성 자동화는 [별도 실제 검증](function-dependency-cache-validation.md)에서 확인했습니다.

스크린샷은 `.tools/verification/screenshots/`의 `78-source-dockerfile-hints.png`, `79-source-dockerfile-preview.png`, `80-source-digest-preview.png`, `81-source-dockerfile-registered.png`입니다. 모두 실제 Companion 화면입니다.

### 발견한 오류와 수정

검증 소스 준비에서 OCIR용 namespace 접두사를 Git 인증에도 사용해 `fatal: Authentication failed`가 발생했습니다. [Oracle Git HTTPS 인증 문서](https://docs.oracle.com/en-us/iaas/Content/devops/using/https_auth.htm)에 따라 tenancy 이름 접두사로 바꾸고 기존 토큰을 메모리에서만 재사용했습니다. 이후 기본 브랜치의 초기 커밋과 충돌하여 `Updates were rejected because the remote contains work that you do not have locally`가 나왔습니다. 새 `companion-verify` 브랜치에 보내 해결했으며 강제 push는 하지 않았습니다.

첫 화면 조회는 `소스 버전 조회 실패 · 접근 권한·공개 여부·API 조회 한도를 확인하세요. 커밋 SHA 직접 입력도 가능합니다.`로 실패했습니다. OCI 브랜치 목록의 `Ref.commit`이 빈 값이라는 기존 인터페이스 동작을 확인하고, 브랜치를 확인한 뒤 공통 `SourceVersions.commits`에서 실제 커밋을 읽도록 수정했습니다. 이 응답 형태의 회귀 테스트를 추가했습니다.

### 최종 검증과 정리

전체 `./mvnw verify`는 **423개 중 421개 통과, 실제 OCI opt-in 2개 기본 skip**입니다. 저장소 우선순위·출처, Helm/변수 미실행, Secret 값 미추출, 기존 Secret 선택 제한, 누락값 검증, 중복 빌드 제출 방지, OCI 브랜치 응답을 검사합니다.

새 Deployment·Pod·Service, DevOps environment·pipeline·stage·artifact, 검증 OCI 코드·이미지 저장소, ARM 빌더의 이번 검증 이미지, 로컬 서비스·조회 연결·kubeconfig·소스 등록을 정리했습니다. 실행 기록과 로컬 증거는 보존했습니다. 운영 `sustena-c`는 정리 후에도 두 Pod의 `d52337647c0c…` digest·2/2 Ready·외부 readyz 200·기존 설정 보존을 다시 확인했습니다.

남은 한계는 다른 Namespace 이관, Helm 표현식 렌더링, 전체 Kubernetes 명세의 고급 설정 자동 가져오기입니다. 현재 추출하는 네 필드 외의 파일 볼륨·환경 변수·보안 컨텍스트는 상세 설정을 사용해야 합니다. 기존 클러스터·Namespace 접속 연결과 등록 빌더·출력 저장소를 선택하는 방식이며 새 IAM·Secret을 자동 생성하지 않습니다.
