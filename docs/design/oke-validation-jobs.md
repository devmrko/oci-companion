# OKE 검증 Job 실행 지원

배포 담당자가 준비된 검증 프로그램을 Companion에서 실행할 수 있게 한다. 앱 배포 완료와 업무 검사 통과는 서로 다른 결과다. 특정 Launchpad 앱 코드나 Langfuse 저장소에 Companion을 결합하지 않는다.

## 이번 변경의 계약

- 기존 단일 OCI DevOps OKE 배포 단계의 인라인 명세에서 batch/v1 Job을 추가로 허용한다.
- namespace·고정 digest·ServiceAccount·참조 Secret 이름·실행 기한·재시도·정리 TTL을 실행 전 표시하고 기존 fingerprint/ticket/CSRF 검사를 유지한다.
- Job은 단일 실행, restartPolicy Never, 실행 기한 1~600초, backoffLimit 0~2, TTL 60~86400초로 제한한다. 비루트·읽기 전용 컨테이너와 CPU/메모리 limits를 요구하며 host 접근·privileged·권한 상승을 거부한다.
- Secret 값은 화면에 표시하지 않는다. 참조 이름만 검사·표시한다. 이미지 코드와 명령 자체의 안전성, IAM·RBAC·NetworkPolicy는 운영자가 관리한다. 이 검사는 Kubernetes admission policy를 대체하지 않는다.
- SUCCEEDED는 OCI 배포 상태다. Job 완료 및 검사 결과를 확인하지 않은 경우 업무 검증은 `미확인`으로 표시한다. 현재 변경은 Job 상태/검사 JSON 자동 수집까지 제공하지 않는다.
- 매번 고유한 Job 이름을 준비한다. 이미 완료된 같은 이름의 Job을 다시 apply한다고 새 검사가 실행되지는 않는다. 현재 Companion은 명세의 이름을 자동 변경하거나 기존 Job을 삭제하지 않는다.

## 확인 방법

허용된 Job의 계획 표시, 기한·횟수 누락/초과, namespace 위반, mutable image, 위험한 Pod 옵션, Secret 참조 표시를 단위 테스트한다. 웹 테스트는 Job 미리보기와 배포/업무 검증 상태 구분 및 기존 일회성 ticket 보호를 확인한다. 실제 cloud 실행은 별도 증거가 있을 때만 검증 완료로 기록한다.

다음 단계는 실행별 Job UID·이름·명세 digest를 보관하고 읽기 전용 Kubernetes 연결로 종료 상태를 수집하는 것이다. 검사 결과 JSON은 버전이 있는 일반 계약으로 만들고, 오래된 Job 결과를 새 배포의 성공 근거로 사용하지 않는다.

참고: [Kubernetes Job](https://kubernetes.io/docs/concepts/workloads/controllers/job/) — 실행 기한, 재시도, 완료 상태와 TTL의 의미를 따른다.

## 실제 OCI 검증 (2026-11-09)

Hello Agent의 기존 전용 파이프라인에 고유 이름의 검증 Job을 준비하고 Companion 화면에서 실행했다. 미리보기에서 최대 240초·재시도 0·TTL 3600·ServiceAccount·Secret 참조를 확인했다. OCI 배포 SUCCEEDED와 Kubernetes Job Complete를 확인했고, 앱 인증·OCI 모델/도구·Langfuse 입력/출력 검사 JSON이 PASS였다. 추가 IAM 없이 stdout 모드를 사용했다.

이 결과는 운영자 도구로 수집했다. Companion 화면은 업무 검증을 미확인으로 표시하며 자동 수집한 것으로 주장하지 않는다. 원본 앱 파이프라인 설정은 검증 뒤 복원했다. 실행 증거는 독립 Launchpad 저장소 `examples/hello-agent/verification/companion-job-2026-11-09.json`에 있다. Companion 전체 테스트 159개 통과. 새 고객 환경의 자동 설치·운영 인증까지 검증한 것은 아니다.
