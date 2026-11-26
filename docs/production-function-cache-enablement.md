# 운영 Function 빌더 의존성 자동 캐시

잠금 파일이 달라질 때 빌더 이미지를 사람이 다시 준비하지 않도록, 운영 빌더에서 의존성 레이어와 실행 런타임을 자동 생성하고 재사용합니다. 앱 배포와 분리하며 캐시 변경만으로 운영 앱을 재배포하지 않습니다.

## 적용 기록 — 2026-11-26

오너가 작업자 터미널에서 직접 승인한 뒤 진행했습니다. GitHub PR 63은 main의 `667b459`에 병합돼 있었으며 Gitea에는 main이 없어 별도 확인 후 같은 커밋의 새 main만 생성했습니다. 기존 기본 브랜치 `devmrko/sustena-deploy`는 유지했습니다.

운영 Function image·digest·config·메모리 3072MiB·timeout 300초를 `.tools/production-cache-enable/function-backup.json`에 비공개로 백업했습니다. 적용 직전에 기존 image/config와 ACTIVE 상태 및 로컬 ARM 빌드 기록에 진행 중인 항목이 없음을 확인했습니다. 전역의 모든 외부 호출 부재까지 증명하는 것은 아닙니다. 새 코드 레이어 digest는 `sha256:6c25d29853de8da66c2c33440b9f05df80c8d0b6fc2a4b6b5e71d2c3bd8583a7`입니다.

기존 설정을 보존하면서 `AUTOMATIC_DEPENDENCIES=true`, digest로 고정한 `RUNTIME_BASE_IMAGE`를 추가하고 Function 이미지를 변경했습니다. 관리 API의 ACTIVE 이후 실제 probe가 새 코드의 dependencyLock 필드·aarch64 환경을 반환하는 것도 확인했습니다. IAM·보안 규칙·기존 Secret은 수정하지 않았습니다.

## 적용 전 probe 시간 초과

첫 읽기 전용 probe의 클라이언트 read timeout 60초가 만료됐습니다. OCI 지표에 해당 호출의 응답 코드 444와 실행 시간 0이 기록됐고, timeout을 180초로 늘린 재호출은 8.03초에 aarch64·Python 3.13.18로 성공했습니다. [Oracle 문서](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionstroubleshooting_topic-Issues-invoking-functions.htm)는 444를 클라이언트와 Function 간 연결 중단으로 설명합니다. 클라이언트 시간 제한으로 연결이 끝난 것은 확인됐지만 첫 응답 지연이 콜드 스타트 때문인지는 확정하지 못했습니다.

## 캐시 보존 권장 정책

- 현재 Function 이미지, RUNTIME_BASE_IMAGE, IMAGE_BASE와 운영 앱이 참조하는 digest는 기간과 관계없이 보존합니다. 전환 직전 런타임도 복구용으로 최소 7일 보존합니다.
- 잠금 해시별 최근 성공 3세트의 deps/runtime 쌍을 보존합니다. 나머지는 마지막 성공 빌드로 사용한 지 30일을 넘었을 때 주 1회 검토 후 삭제합니다. 이미지 생성일만으로 최근 사용 여부를 추정하지 않습니다. 사용 이력이 없으면 자동 삭제 대상에서 제외합니다.
- 빌드 소스 업로드와 태그 없는 중간 이미지 중 완료된 빌드의 임시 자원은 7일 후 정리합니다. 실패·원인 조사 중인 항목은 14일까지 보존합니다. 검증 전용 결과 이미지는 검증 종료 후 즉시 정리합니다.
- 삭제 전 진행 중인 빌드, 모든 Function과 앱의 참조 digest, 캐시 쌍과 복구 지점을 확인합니다. 공유 레이어를 직접 지우지 않고 미참조 manifest만 삭제합니다.

이는 운영 권장값이며 자동 삭제 작업이나 예약 정책은 이번에 설치하지 않았습니다.

## 검증 결과

운영 소스 커밋 `a829ff928af94ea1275e27170fdf6b0d067429b0`를 별도 `cache-verify-*` 태그 두 개로 빌드하며 운영 앱 배포는 하지 않습니다.

첫 빌드는 전체 167초, 의존성 준비 25.51초, 앱 처리 131.96초에 성공했습니다. 테스트 155개, 실패/오류 0이며 첫 이미지 digest는 `sha256:5653640809e079b927dac8890e511d6fbad69fc391faf89af1f61ff93a347fb8`입니다. 

재빌드는 전체 **136초**, 앱 처리 131.07초에 성공했습니다. 테스트 **155개**, 실패/오류 0, `dependencyCacheHit=true`이며 재빌드에는 RUNTIME_REQUIRED나 런타임 변경 요청이 없었습니다. 결과 digest는 `sha256:beb8090d109b568230511221aaa5d331150d26881909dc8dcedd4709743bc455`입니다. 두 결과는 검증 태그일 뿐 운영 앱에 배포하지 않았습니다.

최종 빌더는 ACTIVE이며 실제 probe의 잠금 해시 `eead135652eb592a1939e1c786d4badfa424d9973ebad6b9fe2f4a928aff4a41`가 검증 소스의 uv.lock과 일치했습니다. 실제 실행 런타임 digest는 `sha256:91c19f0fc518dbe064bd95b6a1b3cbcc02ec8df782b7944596df848980b8d52e`입니다. 기존 Function의 다른 config·메모리·timeout과 Application config는 백업과 동일했습니다. 실패한 빌드나 전환이 없어 **백업 복구는 필요하지 않았습니다**.

실행은 기존 `FunctionImageBuilds` 코드를 직접 호출하는 승인 전용 일회성 테스트로 했습니다. 테스트 원본·입력·결과는 비공개 `.tools/production-cache-enable/`에 보관했습니다. 주요 결과는 `build-1.json`, `build-2.json`, `final-runtime.json`, 로그 `/tmp/ridley-production-cache-builds.log`입니다.

## 정리와 운영 보존

검증용 소스·결과·중간 이미지 **8개**를 삭제했습니다. 새로 추가한 이미지 중 운영에 필요한 고정 기반·의존성 캐시·현재 런타임 **3개**는 보존했으며 기존 이미지와 저장소는 유지했습니다. 정리 후에도 Function ACTIVE·자동 캐시 활성·실제 잠금 해시 일치를 다시 확인했습니다. 검증용 앱·Namespace·Function·IAM 정책은 새로 생성하지 않았습니다.

운영 `sustena-c`는 기존 `d52337647c0c…` digest의 두 Pod가 Ready이고 `/readyz` HTTP 200입니다. 이미지 외 Deployment 설정, NodePort Service, ConfigMap이 승인된 기존 백업과 동일했습니다. Trilium nginx·기존 Sustena·Knowledge MCP·Finance MCP는 변경하지 않았습니다.

정상 테스트 구성의 `./mvnw verify` 결과는 **433개 중 431개 통과·실환경 opt-in 2개 건너뜀**, 실패/오류 0입니다. 운영 적용 일회성 실제 테스트는 별도로 성공했습니다. 증거 스크린샷 `.tools/verification/screenshots/92-production-function-cache.png`는 실제 로그·JSON을 렌더링한 결과 표이며 Companion UI 화면은 아닙니다. 삭제 목록과 결과는 비공개 `resources.json`, 운영 보존 결과는 `.tools/verification/production-final-readonly-acceptance.json`에 있습니다.

문서 가이드 최신 원문 조회는 Knowledge MCP 연결 오류로 실패했습니다. 위 내용은 이번 실제 실행과 프로젝트 원본 코드·기존 검증 문서를 근거로 기록했으며 별도 지식 저장이 완료됐다고 주장하지 않습니다.

## 후속 보강

빌드 중복 실행과 결과 유실은 [완료 결과 복구](build-result-recovery-validation.md), 캐시 보존·정리는 [캐시 정리 검증](function-cache-maintenance-validation.md)을 참고하세요. 후속 기능은 격리 자원에서 검증했으며 운영 Function 템플릿 갱신과 운영 캐시 자동 삭제 활성화는 별도입니다.

## 이후 운영 적용

2026-11-26 오너 승인 후 운영 템플릿 적용과 자동 정리 활성화를 완료했습니다. [운영 적용 결과](production-builder-recovery-rollout.md)에 실제 빌드·재빌드, 설정 보존과 정리 결과를 기록했습니다. 위 본문의 적용 전 검증 기록은 당시 이력으로 유지합니다.
