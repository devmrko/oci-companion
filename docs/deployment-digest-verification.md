# 배포 성공을 실제 실행 이미지로 확인하기

Companion은 OKE Deployment 배포에서 OCI 실행 완료와 앱 이미지 적용을 구분합니다. 배포 전에 기대 digest를 기록하고, 완료 후 해당 Deployment 소유 Pod의 `imageID`와 Ready 상태가 모두 일치해야 성공으로 표시합니다. 이전 이미지가 Ready여도 성공으로 표시하지 않습니다.

## 2026-11-26 확인한 원인

운영 리소스는 이번 작업에서 읽기만 했습니다. 이전 승인 배포의 OCI work request, 실행 로그의 artifact snapshot, 당시 Deployment와 Pod 기록을 대조했습니다. 아래 시각은 UTC입니다.

| 사건 | 2026-11-25 시각 | 관찰 |
|---|---|---|
| artifact 변경 접수 | 20:15:33.285 | 비동기 변경 작업 시작 전 |
| 운영 배포 생성 | 20:15:35.237 | 변경 완료보다 먼저 실행 |
| artifact 변경 작업 시작 | 20:15:39.240 | 배포 생성 이후 |
| artifact 변경 완료 | 20:15:39.653 | 뒤늦게 SUCCEEDED |

실행 snapshot과 두 Pod는 이전 `d822e917…`를 사용했고, 기대 이미지는 `d5233764…`였습니다. 변경 API 응답과 GET 내용만 확인하고 **비동기 work request 완료를 기다리지 않은 것이 원인**입니다. 새 artifact를 만들어도 stage 포인터 변경 완료를 기다리지 않으면 같은 문제가 생깁니다. 별도 실험에서 stage 변경은 20:25:51.763에 접수되어 20:26:03.479에 완료됐고, 그 뒤 시작한 실행은 새 artifact B와 기대 digest를 사용했습니다.

근거 원본은 로컬 `.tools/digest-verification/production-timing.json`, `waited-B-log.txt`와 이전 `.tools/verification/production-function-execution-log.txt`입니다. 문서에는 OCID·토큰을 기록하지 않습니다.

## 구현

- 재배포마다 digest가 고정된 새 inline manifest artifact를 생성합니다. 이전 artifact는 실행 이력 보존을 위해 수정하거나 삭제하지 않습니다.
- artifact 생성과 stage 참조 변경의 work request가 SUCCEEDED인지 확인한 뒤 반환합니다. stage 갱신에는 ETag 조건을 사용합니다. 신규 pipeline·stage 생성도 완료를 기다립니다.
- 실패·취소·완료 확인 시간 초과에서는 실행하지 않습니다. OCI 오류를 식별자 마스킹 후 표시합니다. 완료 대기는 요청별 60초이며, 시간 초과 후 현재 작업 상태를 확인하고 다시 미리보기해야 합니다.
- 실행 직전 승인 대상과 digest를 사용자·프로필·테넌시·구획별 저장소에 기록합니다. 실행 후 현재 파이프라인 내용으로 기대값을 다시 만들지 않습니다.
- Deployment → ReplicaSet → Pod 소유 관계, 세대 반영, 복제본 수, Ready, 일반·init 컨테이너 imageID를 확인합니다. 조회 실패나 기대 기록이 없는 과거 실행은 UNVERIFIED입니다.
- 등록 서비스는 빌드 결과 digest도 독립적으로 비교하므로 이전 artifact를 기대값으로 잘못 선택해도 빌드 성공 이미지와 다른 Pod는 실패합니다.
- 최초 확정 결과는 실행별 보존합니다. 다음 배포 때문에 과거 실행 결과가 바뀌지 않습니다. 이는 현재 앱 건강 상태를 계속 보장하는 기록은 아닙니다.
- 기존 Deployment 재사용 시 실제 보존되는 imagePullSecrets의 호스트로 이미지 주소를 맞춥니다. 입력에 저장된 다른 Secret 호스트를 적용하는 오류를 방지합니다.
- 화면은 OCI 실행 상태와 실제 검증 결과를 따로 보여 주고 기대/실제 digest를 나란히 표시합니다.

## 별도 서비스 실제 검증

프로필 `JOUNGMINKOKOR_API`, 춘천 리전, `personal-services/companion-verify-digest`에서 수행했습니다. 기존 운영 Deployment·Service·IAM·보안 규칙·Secret은 수정하지 않았습니다.

| 실험 | OCI | Companion | 실제 결과 |
|---|---|---|---|
| 기대 B를 기록한 뒤 의도적으로 이전 A artifact 실행 | SUCCEEDED | FAILED · 이미지 불일치 | 두 Pod가 이전 d822e917… 실행 |
| 새 B artifact 생성 → 생성 완료 → stage 변경 완료 → 실제 실행 API | SUCCEEDED | SUCCEEDED | 두 Pod가 d5233764… 실행, 2/2 Ready |

명시적으로 활성화한 실제 통합 테스트는 183.1초에 통과했습니다(05:37:07–05:40:10 KST). 별도 포트 포워딩으로 `/readyz` HTTP 200도 확인하고 포워딩을 닫았습니다. 화면 증거:

- `.tools/verification/screenshots/70-digest-mismatch-failed.png`
- `.tools/verification/screenshots/71-digest-match-ready.png`

초기 검증 manifest의 호스트와 기존 pull Secret이 달라 ImagePullBackOff가 발생했고, 실제 Secret과 같은 `yny.ocir.io`로 수정했습니다. 공유 클러스터 CPU 여유 부족으로 검증 Deployment의 rolling surge가 Pending이 되어, 실패한 검증 bootstrap ReplicaSet만 제거하고 검증 Deployment에만 Recreate 전략을 사용했습니다. 운영 전략은 변경하지 않았습니다.

## 자동 테스트와 적용 범위

`./mvnw verify`: 408개 중 407개 통과, 실제 OCI 테스트 1개는 기본 비활성화로 건너뜁니다. 해당 테스트는 이번 검증에서 별도로 활성화하여 1개 통과했습니다. 단위 테스트는 비동기 완료 순서·오류·시간 초과, 소유 Pod 구분, 실제 digest 불일치, 기대값 저장과 사용자 범위, 빌드 digest 독립 대조, 실제 pull Secret 보존을 확인합니다.

현재 검증 대상은 digest가 고정된 OKE Deployment입니다. Job 전용 배포, 증거가 없는 기존 실행은 성공 미확인으로 남습니다. 다중 아키텍처 index digest와 런타임 child digest 변환은 지원하지 않으므로 단일 플랫폼 manifest digest를 선택해야 합니다. VM 배포는 기존 판정을 유지합니다. 불일치 판정은 자동 롤백을 실행하지 않습니다.

운영 반영은 별도 직접 승인 전까지 중단합니다. 처음 설정 자동화와 빌더 자동화는 이 단계 보고 후 진행할 후속 범위입니다.

## 정리 결과

검증 Deployment·Pod, 검증 DevOps stage·pipeline, 검증 artifact 4개를 삭제했습니다. 공용 project·OKE environment와 기존 이미지 저장소는 유지했습니다. 운영 `sustena-c` Deployment spec, `sustena-c-nodeport` Service spec, `sustena-config` 데이터가 이전 백업과 동일하고 운영 Ready 2개임을 읽기 전용으로 확인했습니다. 최종 전체 verify도 407개 통과·실제 테스트 1개 기본 skip으로 완료했습니다.
