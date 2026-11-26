# 운영 빌더 결과 복구·캐시 정리 적용

운영 ARM Function이 빌드 완료 증거와 캐시 사용 이력을 남기도록 템플릿을 갱신하고, 성공 빌드 뒤 안전한 캐시 정리를 실행하도록 활성화했습니다. 기존 Sustena 앱은 재배포하지 않았습니다.

## 승인과 반영 · 2026-11-26

오너의 “1번해 2번도 하고” 지시에 따라 PR #64 병합, 운영 템플릿 적용, 자동 정리 활성화와 재검증을 진행했습니다. [PR #64](https://github.com/devmrko/oci-companion/pull/64)의 병합 커밋은 `0c658f7fae2a9d91fea6000e182ac84ced0f687d`이며 GitHub·Gitea main이 일치합니다. Gitea는 fast-forward로만 갱신했습니다. PR과 병합 후 main의 CI가 모두 통과했습니다.

별도로 요청된 `devmrko/function-cache-enablement` 브랜치도 GitHub에 push했습니다. 해당 `854bc0e`는 PR #64에 이미 포함됐으므로 main에 추가 병합할 변경은 없습니다.

## 운영 빌더 변경

- 적용 전 등록 빌더의 실행 중·미확인 기록이 없고, Function 호출 지표의 최근 6분 구간에 호출이 없음을 확인했습니다. 외부 호출 전체를 실시간으로 잠그는 보장은 아닙니다.
- 기존 Function 이미지·설정·Application 설정을 비공개 `.tools/production-builder-rollout/function-backup.json`에 백업했습니다. 유지보수 잠금과 OCI etag로 동시 변경을 막고 새 템플릿을 적용했습니다.
- 기존 설정 중 `RUNTIME_BASE_IMAGE`만 새 고정 이미지로 바꾸었습니다. 자동 의존성 옵션, 메모리 3072MiB, 제한 시간 300초와 나머지 Function/Application 설정은 그대로입니다.
- 새 기반 이미지: `sha256:b73bd2656597fe411bf9cc4fb7cd60bea28566bf646de4d5a4a28d649ea6092e`.
- 최종 실행 런타임: `sha256:66fc85d82f8fd8073ed98ab6931b159342903ec27237dc5f74e569258fd2d5db`.
- 실제 probe로 ACTIVE·aarch64·소스와 같은 dependencyLock을 확인했습니다. 백업 복구는 필요하지 않았습니다.

## Companion 화면에서 확인한 빌드

등록 빌더 `sustena-function`, 기존 OCI 소스 저장소 `new-sustena`, 소스 커밋 `a829ff928af94ea1275e27170fdf6b0d067429b0`를 사용했습니다. 기존 인증 Secret은 읽기만 했습니다. 두 결과는 운영 앱과 다른 `rollout-verify-*` 태그로 만들었으며 앱 배포는 하지 않았습니다.

| 검증 | 첫 빌드 | 같은 커밋 재빌드 |
| --- | --- | --- |
| 전체 소요 시간 | 160.62초 | 139.66초 |
| Function 의존성 준비 | 14.88초 | 추가 전환 없음 |
| Function 앱 처리 | 131.72초 | 132.04초 |
| 앱 테스트 | 155개 통과 | 155개 통과 |
| 캐시 | 기존 의존성 재사용·새 런타임 생성 | 캐시 적중·사용 시점 갱신 |

첫 이미지 digest는 `sha256:9bc726f3107d4f0ae497ae7683795f589735865045a3585abab4e06ad870bead`, 재빌드 digest는 `sha256:16203e20354390501085943f03b932f164aa867588c169d765a9eaec374cca86`입니다. 두 이미지를 digest로 조회해 실행 ID·커밋·테스트 성공 라벨과 linux/arm64를 확인했습니다.

## 자동 정리의 실제 상태

캐시 미리보기에서 삭제 후보 0개를 확인하고 사용자 확인 절차로 자동 정리를 켰습니다. 재빌드 후에도 활성 상태와 보존 이유를 화면에서 확인했습니다. 새 런타임 캐시 1개는 생성 이력이 기록됐고, 기존 의존성 캐시 1개는 사용 시점만 기록돼 자동 삭제 소유 대상으로 편입되지 않았습니다.

자동 정리는 이 저장소의 성공 빌드 종료 시 수행합니다. 최근 의존성 3종·사용 후 30일·생성 후 7일·현재 참조 보호가 적용됩니다. 이번 운영 적용에서 정책상 삭제할 오래된 캐시는 없었으므로 자동 삭제된 캐시는 없습니다. 실행 중/미확인 작업이나 조회 실패 시 삭제하지 않습니다. 독립 호스트·다른 리전과 공유하는 캐시 저장소에 대한 분산 조정은 제공하지 않습니다.

등록 서비스와 정책이 저장된 Companion 데이터 디렉터리는 `.tools/verification/data`입니다. 같은 카탈로그를 사용하려면 재시작 시 `COMPANION_DATA_DIR` 또는 `--companion.data-dir`로 이 디렉터리를 지정해야 합니다. 별도 데이터 디렉터리에는 이 정책이 복사되지 않습니다. 시간 기반 정리 데몬은 설치하지 않았습니다.

## 정리와 운영 보존

검증용 소스·결과·중간 이미지 8개를 삭제하고 삭제 완료를 확인했습니다. 새 기반 이미지와 현재 런타임 2개, 모든 기존 이미지와 복구용 이전 런타임을 보존했습니다. IAM·보안 규칙·기존 Secret은 변경하지 않았습니다.

운영 `sustena-c`의 기존 `d52337647c0c…` digest와 두 Pod의 imageID, 2/2 Ready, readyz HTTP 200, Deployment·Service·ConfigMap 설정을 확인했습니다. NodePort 31083·Trilium nginx·기존 Sustena·Knowledge MCP·Finance MCP는 변경하지 않았습니다.

## 증거와 검증 범위

- `.tools/verification/screenshots/95-production-cache-policy-preview.png`: 활성화 전 미리보기.
- `96-production-build-1-preview.png`, `97-production-build-2-preview.png`: 두 검증 빌드의 미리보기.
- `98-production-build-1-result.png`, `99-production-build-2-result.png`: 두 빌드 성공 화면.
- `100-production-cache-policy-enabled.png`: 재빌드 후 자동 정리 활성·현재 캐시 보존 화면.

위 스크린샷은 모두 `.tools/verification/screenshots/` 아래에 있습니다. 상세 실행 기록은 비공개 `.tools/production-builder-rollout/acceptance.json`, `ui-final.json`, `resources.json`에 있습니다. 비밀값·전체 리소스 식별자는 문서에 포함하지 않습니다.

코드는 병합된 검증 버전 그대로이며 이번 변경은 운영 적용 기록입니다. 해당 버전은 전체 Maven 446개 중 441개 통과·선택 실행 5개 제외, Python 11개 통과 및 main CI 성공을 확인했습니다. 문서 가이드의 지식 DB 원문은 연결 오류로 조회하지 못했으며, 이 기록은 실제 실행 증거와 저장소 원문을 기준으로 작성했습니다.
