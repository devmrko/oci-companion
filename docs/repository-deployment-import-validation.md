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

검증 중 배포 완료 전에 실행한 `kubectl get deployment companion-verify-advanced`는 `NotFound`를 반환했습니다. OCI 실행 완료 후 같은 조회와 명세/readyz 검증은 통과했습니다. 검증 서비스는 이어지는 Namespace 이관의 대상으로 유지하며 전체 검증 종료 후 정리합니다.

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
