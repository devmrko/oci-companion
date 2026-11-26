# 저장소 배포 설정 가져오기 검증

저장소에 이미 있는 Deployment 설정을 새 서비스의 첫 배포에 사용합니다. 포트·복제 수·경로·pull Secret 외에도 환경변수와 참조, 리소스 요청/제한, 세 종류의 프로브, 파일 볼륨, 보안 컨텍스트를 가져와 설정을 다시 입력하는 일을 줄입니다. 배포 미리보기에서 실제 적용 명세를 확인하고, 실행 후 Pod digest와 Ready로 결과를 판정합니다.

## 고급 Deployment 설정 — 2026-11-26

`JOUNGMINKOKOR_API`의 별도 소스 `companion-verify-advanced`를 Companion 화면에서 빌드·배포·등록했습니다. 소스 커밋은 `6e049a9b420aebe1e74c5ab1eb3190fcd2f6bf01`, 빌드 이미지 digest는 `sha256:7cefbee7030cd792157026ad5cd4a7c7b96a3991c31dbe0fa3e849744c43dce4`입니다. 운영 서비스는 변경하지 않았습니다.

- 결과: OCI 실행 SUCCEEDED, REGISTERED, 두 Pod 모두 기대 digest 일치, 2/2 Ready, `/readyz` HTTP 200.
- live Pod 명세와 미리보기의 환경변수, Secret 참조, 요청 100m/64Mi·제한 250m/128Mi, Host 헤더·timeout 7초, ConfigMap 마운트·emptyDir, UID/fsGroup·읽기 전용 루트 파일시스템·토큰 자동 마운트 설정을 비교해 일치했습니다.
- 검증 서버는 지정 Host·일반 환경변수·별도 테스트 Secret 값·ConfigMap 파일·쓰기 가능한 `/tmp`를 모두 확인해야 readyz 200을 반환합니다. 기존 Secret의 데이터는 복사하거나 수정하지 않았습니다.
- `./mvnw verify`: 426개, 실패/오류 0, 실환경 전용 2개 건너뜀. 가져오기 보존·참조 누락·민감한 인라인 환경변수·hostPath·권한 상승 거부·이미지 재배포 시 일반 환경변수 보존을 테스트했습니다.
- 로컬 증거: `.tools/verification/screenshots/82-advanced-preview.png`, `83-advanced-digest-preview.png`, `84-advanced-registered.png`; `.tools/advanced-import/acceptance.json`. 운영 식별자와 인증 정보가 있는 로컬 상태 파일은 커밋하지 않습니다.

대상 Namespace에서 Secret·ConfigMap·PVC·ServiceAccount의 존재를 먼저 확인하며 데이터는 읽지 않습니다. 민감한 이름의 인라인 환경변수, 키/OCID 형태의 값, 인증 헤더, hostPath, 권한 상승은 자동 가져오기에서 거부합니다. 앱 컨테이너 한 개를 지원하며 여러 Deployment나 init/sidecar는 명시적으로 거부합니다. 인라인 값의 모든 비밀 여부를 자동 판별할 수는 없으므로 비밀값은 항상 Secret 참조로 관리해야 합니다.

검증 중 배포 완료 전에 실행한 `kubectl get deployment companion-verify-advanced`는 `NotFound`를 반환했습니다. OCI 실행 완료 후 같은 조회와 명세/readyz 검증은 통과했습니다. 검증 서비스는 이어지는 Namespace 이관의 대상으로 사용한 뒤 정리했습니다.

## Helm 렌더링 — 2026-11-26

Companion 실행 설정 `companion.helm`에 Helm 실행 파일 경로를 지정하면 OCI 저장소의 단일 루트 차트를 `helm template`으로 렌더링하고 기존 Deployment 가져오기 절차로 읽습니다. 차트의 기본 values를 사용하며 화면에는 `chart/helm-rendered.yaml`처럼 설정의 출처가 표시됩니다. Helm 설치·업그레이드나 Secret 객체 적용 기능은 아닙니다.

Helm v4.3.2로 실제 OCI 소스 커밋 `da3c4994ca306fc0df0a8d1cdae76dcbeca192f0`를 읽어 포트 8080·복제 수 2·readyz 경로와 고급 설정 출처를 확인했습니다. 화면 증거는 `.tools/verification/screenshots/85-helm-source-hints.png`입니다. 실행 환경을 비우고 별도 HOME·Helm 설정·플러그인 경로와 빈 kubeconfig를 사용합니다. `--dry-run=client --no-hooks --skip-tests --skip-schema-validation`로 실행하며 셸·post-renderer·클러스터 접속·의존성 다운로드 옵션은 사용하지 않습니다. 실행 시간 30초, 출력 1MiB 제한을 둡니다.

실제로 아래 명령으로 의존성이 없는 테스트 차트를 렌더링했습니다.

```sh
helm template verify /tmp/ridley-helm-missing --dry-run=client --no-hooks --skip-tests --skip-schema-validation
```

종료 코드 1의 원문: `Error: an error occurred while checking for chart dependencies. You may need to run 'helm dependency build' to fetch missing dependencies: found in Chart.yaml, but missing in charts/ directory: absent`.

화면 오류에는 템플릿이 출력할 수 있는 민감한 values를 포함하지 않고 종료 코드와 해결 방법을 표시합니다. 외부 의존성은 미리 풀어서 차트에 포함해야 합니다. 압축·바이너리 차트 파일, 파일당 256KiB 초과, 여러 루트 차트, 사용자 values 오버라이드 선택은 현재 지원하지 않습니다. 무작위 값이나 현재 시간에 의존해 렌더 결과가 바뀌면 실행 전 재검증에서 중단합니다.

`./mvnw verify`는 429개 중 실패/오류 0, 실환경 전용 2개 건너뜀입니다. 실행 인자·환경 격리·출처·여러 차트 거부·실패 원문의 민감한 값 비노출을 테스트했습니다.

## Namespace 이관 검증

[이관 흐름과 삭제 보호 조건](design/namespace-migration.md)에 따라 기존 서비스 설정 재사용 화면에서 다른 Namespace를 고를 수 있습니다. 대상 참조가 없으면 배포 전에 중단하고, 원본 정리는 대상 Pod의 digest·Ready를 다시 확인한 후 별도 확인으로 실행합니다. 실제 검증 결과는 아래에 기록합니다.

2026-11-26 `personal-services/companion-verify-advanced`를 새 Namespace의 `companion-verify-migrate/companion-verify-moved`로 실제 이관했습니다. 대상 Namespace·별도 pull Secret·테스트용 ConfigMap/Secret은 검증 준비로 새로 만들었고 기존 Secret은 변경하지 않았습니다. 제품 흐름은 이 참조들의 존재만 확인했습니다.

- 대상 2/2 Ready, 두 Pod의 digest `7cefbee7030cd792157026ad5cd4a7c7b96a3991c31dbe0fa3e849744c43dce4` 일치, `/readyz` 200, 고급 Pod 설정과 미리보기 명세 일치.
- 원본 정리 미리보기 → 확인 → UID·resourceVersion 조건부 삭제 → 부재 확인을 거쳐 화면 상태 MIGRATED. 원본 Deployment·Service·Pod가 없는 것을 별도 읽기 전용 조회로 확인했습니다.
- 초기 `kubectl get deployment` 후 바로 readyReplicas를 확인한 검증 스크립트는 `KeyError: 'readyReplicas'`로 실패했습니다. `kubectl rollout status deployment/companion-verify-moved --timeout=60s`로 완료를 기다린 뒤 같은 검증은 통과했습니다. 실제 경고 원문은 `0/2 nodes are available: 2 Insufficient cpu.`와 readiness probe의 `connect: connection refused`였습니다. 운영 워크로드나 노드 설정은 변경하지 않았습니다.
- 화면 증거: `.tools/verification/screenshots/87-migration-preview.png`, `88-migration-target.png`, `89-migration-retirement-preview.png`, `90-migration-completed.png`. 실제 결과: `.tools/advanced-import/migration-acceptance.json`.
- `./mvnw verify`: 433개, 실패/오류 0, 실환경 전용 2개 건너뜀. 대상 검증 실패 시 원본 보존, Namespace 참조 보존, 외부 노출·Ingress·추가 Service·잘못된 원본 식별자 거부를 검증했습니다.

Helm 소스도 ARM 빌드에 성공해 같은 앱 digest를 얻고 적용 명세 미리보기까지 확인했습니다(`86-helm-built-preview.png`). Helm 전용 서비스는 배포하지 않았으며, 실제 런타임 설정 검증은 고급 명세 서비스와 그 Namespace 이관에서 수행했습니다.


## 운영 Function 자동 캐시 적용안 — 승인 전 계획

아래는 적용 전 승인에 사용한 계획입니다. 이후 직접 승인을 받아 수행한 결과는 [운영 Function 캐시 적용 기록](production-function-cache-enablement.md)에 별도로 기록합니다.

1. 현재 Function image·digest·config·메모리·timeout을 비공개 백업합니다. 기존 설정 전체를 보존하고 변경할 항목만 별도 미리보기로 비교합니다.
2. 검증한 `fn-image-template`의 `func.py`, `image_layer.py`, `dependency_cache.py`를 포함하는 ARM 런타임 이미지를 별도 새 태그로 만들고 digest를 고정합니다. `SOURCE_REPOSITORY` 안의 `RUNTIME_BASE_IMAGE`를 해당 고정 digest로 설정하고 `IMAGE_BASE`도 검증한 기반 digest를 유지합니다.
3. 운영 빌더 Function에 `AUTOMATIC_DEPENDENCIES=true`와 `RUNTIME_BASE_IMAGE`를 적용하고, 관리 API의 ACTIVE뿐 아니라 실제 런타임 probe로 새 코드·aarch64·잠금 해시를 확인합니다. 진행 중인 빌드가 없을 때 변경하며 새 IAM이 필요하면 먼저 승인을 받습니다.
4. 별도 앱 이름으로 첫 빌드와 같은 커밋 재빌드를 검증합니다. 실패하면 백업 image와 config로 빌더를 복구하고 실행 probe를 다시 확인합니다. 운영 앱 배포는 별도 결정이며 이 설정 변경이 자동으로 배포하지는 않습니다.

영향: 잠금 파일이 바뀐 첫 빌드에 의존성 설치·OCIR 캐시 저장·빌더 Function 이미지 전환과 전파 대기가 추가됩니다. 기존 실검증에서는 첫 빌드 180초, 캐시 재사용 144초였으며 시간은 소스·네트워크에 따라 달라집니다. OCIR 저장량과 Function 실행 비용이 늘고, 동일 빌더의 런타임이 변경되므로 동시 빌드를 피해야 합니다. 캐시 삭제 정책과 실패한 전환의 운영 절차도 정해야 합니다. [앞선 실제 캐시 검증](function-dependency-cache-validation.md)의 범위와 오류를 함께 참고하세요.


## 최종 정리와 운영 보존

검증용 원본·이관 대상 워크로드, 새 Namespace와 테스트 Secret/ConfigMap, 서비스 등록·조회 연결·소스 등록, 두 OCI 환경·파이프라인·stage·artifact, ARM VM의 검증 태그 두 개를 삭제했습니다. 공유 프로젝트와 기존 정책·보안 규칙·Secret은 유지했습니다. 클러스터 정리 첫 스크립트는 Namespace 삭제 후 `Fixture cleanup failed`로 중단됐으며, 남은 정상 조회 연결로 정확히 두 테스트 자원을 삭제하고 부재를 재확인했습니다. 정리 로그는 `/tmp/ridley-import-clean-*.log`, 화면은 `91-import-cleaned.png`입니다.

운영 읽기 전용 검증은 기존 `d52337647c0c…` digest를 실행하는 두 Ready Pod, readyz 200, 이미지 외 Deployment 설정·NodePort Service·ConfigMap 동일을 확인했습니다. 이 검증 당시 운영 Function 자동 캐시는 적용하지 않았습니다. 이후 직접 승인에 따른 활성화·첫 빌드·재빌드 결과는 [운영 Function 캐시 적용 기록](production-function-cache-enablement.md)에 있습니다. Knowledge MCP 연결 오류로 문서 가이드 최신 원문과 자동 지식 저장은 수행하지 못했고, 이 문서는 코드·실검증의 원본 증거를 기준으로 작성했습니다.
