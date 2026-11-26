# 배포 점검과 조치 화면 검증

배포 전에 누락된 준비 조건과 다음 행동을 확인할 수 있습니다. 점검 항목을 펼치면 조회 근거, 변경 미리보기, 실행 버튼 또는 관리자에게 전달할 안내가 나옵니다. 조회 성공만으로 변경 권한을 인정하지 않습니다.

2026-11-26, `JOUNGMINKOKOR_API`의 춘천 OKE에서 Companion을 실행해 확인했습니다. 검증용 Deployment는 복제 수 0으로 생성해 앱 Pod를 시작하지 않았습니다. 운영 배포는 실행하지 않았습니다.

## 항목별 결과

| 항목 | 구현 및 확인 | 화면 |
| --- | --- | --- |
| 파이프라인 IAM | 기존 정책 충족을 실제 조회했습니다. 생성 권한은 현재 그룹 멤버십과 조건 없는 관리 정책으로 별도 확인합니다. 부족한 문장·명령 복사, 확인 후 생성, 실행 직전 재점검을 연결했습니다. 실제 IAM 생성은 하지 않았습니다. | `103-pipeline-iam-evidence.png` |
| NodePort Service | 전체 Namespace의 포트 충돌을 조회합니다. Kubernetes SelfSubjectAccessReview의 실제 응답으로 생성 권한을 확인합니다. 31083 충돌 차단 후 검증 Service를 새 이름·31234로 생성했습니다. 기존 이름에는 create를 실행하지 않습니다. | `107-nodeport-collision.png`, `108-nodeport-service-preview.png`, `109-nodeport-service-created.png` |
| 워커 보안 규칙 | 실제 보안 목록의 구획에서 관리 정책을 확인했습니다. /32 → 31234 규칙의 미리보기와 관리 권한 확인까지만 진행했습니다. 기존 백업·ETag 확인·규칙 보존 경로를 재사용합니다. 외부 nginx는 설정 예시만 제공합니다. | `104-nodeport-security-preview.png` |
| 보안 기본값·현재 Deployment | defaultMode 0440, emptyDir 64Mi, UID 10001, RuntimeDefault, 권한 상승 금지, enableServiceLinks false, probe별 지연·주기를 추가했습니다. 파일 볼륨에는 읽을 수 있는 fsGroup을 기본 적용합니다. 실제 sustena-c Deployment를 읽어 보안·볼륨·프로브 값을 폼으로 가져왔습니다. 고급 저장소 가져오기에도 enableServiceLinks를 보존합니다. | `102-current-deployment-input.png` |
| 기존 이름 충돌 | sustena-c 이름으로 새 서비스 미리보기를 요청해 “이미 배포된 이름입니다”와 등록 서비스 재배포 링크를 확인했습니다. 생성·배포는 실행하지 않았습니다. | `110-existing-name-redeploy-guide.png` |
| kubectl 경로 | 격리한 로컬 데이터와 잘못된 설정 경로로 시작했습니다. 발견한 `/opt/homebrew/bin/kubectl`을 버튼으로 적용한 뒤 동일 서버에서 조회와 Service 생성이 성공했습니다. 임의 실행 파일 경로는 받지 않습니다. | `105-missing-kubectl-and-arm-builder.png`, `106-kubectl-path-applied.png` |
| 조회 연결 해제 | 검증용 연결의 kubeconfig 경로를 `/private/tmp`로 저장하고 `/tmp` 데이터 경로의 서버에서 해제했습니다. 연결 파일과 kubeconfig 삭제를 확인했습니다. 경로 이탈·파일 심볼릭 링크 거부는 테스트로 확인합니다. | `111-realpath-alias-before-disconnect.png`, `112-realpath-alias-disconnected.png` |
| ARM 빌더 | 실제 등록 목록의 통과 상태와, 격리한 데이터에서 빌더가 없는 안내를 확인했습니다. 배포 구성·ARM 빌드·소스 첫 배포 화면에서 동일 점검 모델을 씁니다. 등록 확인이 실행 성공을 의미하지는 않습니다. | `101-deployment-checks.png`, `105-missing-kubectl-and-arm-builder.png`, `113-arm-builder-registration-guide.png`, `114-source-arm-builder-guide.png` |

화면 파일은 로컬 `.tools/verification/screenshots/`에 있습니다. 전체 식별자를 가렸으며 저장소에는 포함하지 않았습니다.

## 테스트와 정리

- `./mvnw verify`: 461개, 실패 0, 오류 0, 선택적 실제 환경 테스트 5개 제외.
- 새 테스트: 관리 정책의 조건·범위·교체 그룹 이름 거부, 실제 오류 형식 403/404 구분, 미확인 권한으로 생성 거부, 7개 기본값·명시적 덮어쓰기, 현재 Deployment 가져오기, 전역 NodePort 충돌·기존 Service 이름 거부, kubectl 임의 실행 파일 거부, 실제 경로 별칭·이탈 거부.
- 검증용 `companion-check-verify` Deployment와 `companion-check-verify-nodeport` Service를 삭제하고 재조회해 부재를 확인했습니다. 검증용 조회 연결도 삭제했습니다.
- 보호 대상 NodePort 31080·31081·31083을 확인했습니다. sustena-c는 2/2 Ready, readyz 200, 실행 digest `d52337647c0c…`, 기존 Deployment 설정·Service·ConfigMap 동일을 읽기 조회로 확인했습니다.
- 기존 IAM·보안 규칙·Secret, Knowledge/Finance MCP, Trilium nginx에는 변경 요청을 보내지 않았습니다. 운영 Function도 변경하지 않았습니다.

## 남은 범위

- 사용한 실제 프로필에서는 관리 권한이 확인됐습니다. 권한 없는 실제 프로필은 확보하지 않았습니다. 403 NotAuthorized와 모호한 404 NotAuthorizedOrNotFound 구분은 모의 SDK 응답으로 검증했습니다. 후자는 권한 없음으로 단정하지 않습니다.
- 조건부 정책, 도메인 그룹 구문 등 판독하지 못한 관리 정책은 “확인 필요”로 남깁니다. 클래식 그룹 이름은 생성 시점을 확인하며, 이름이 같다는 이유만으로 과거 정책을 인정하지 않습니다. 관리자 안내 복사와 다시 점검을 사용해야 합니다.
- 현재 Deployment 가져오기는 폼이 표현하는 필드를 읽는 기능입니다. 여러 컨테이너·init 컨테이너·민감한 리터럴 환경값은 거부합니다. 서로 다른 requests/limits, 개별 환경변수 참조, subPath, 별도 probe 포트·scheme 등 표현하지 못하는 설정은 경고합니다. 이미지와 Service는 저장된 입력을 유지합니다. 전체 원본 명세를 보존하며 이미지만 바꾸려면 등록 서비스 재배포를 사용합니다.
- 실제 IAM·보안 규칙 생성과 외부 프록시 적용은 이번 검증에서 실행하지 않았습니다. 새 리소스 생성에는 별도 확인이 필요합니다.

설계 근거는 [배포 점검 설계](design/deployment-check-actions.md)입니다. 문서 가이드의 지식 DB 조회는 연결 오류로 실패했으므로 가이드 원문을 읽었다고 주장하지 않습니다.
