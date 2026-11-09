# OKE PVC·비루트 쓰기 검증

2026-11-09. 기존 OKE 클러스터의 별도 `companion-storage-validation` Namespace에서 검증했습니다. 다른 Namespace와 노드는 변경하지 않았습니다.

## 준비와 화면

Kubernetes 네이티브 방식으로 같은 Namespace에 OCIR imagePullSecret과 50Gi `oci-bv` PVC를 준비했습니다. 비밀값은 메모리에서 Kubernetes API로만 전달했으며 화면·Git·검증 파일에 기록하지 않았습니다.

앱 화면에서 기존 환경·사설 OCIR hello-python digest 이미지·x86·1 replica·250m CPU·256Mi 메모리·`/hello` readiness를 선택했습니다. 기존 PVC 이름, `/data` 마운트, imagePullSecret 이름을 입력하고 YAML을 확인한 뒤 OCI 파이프라인을 저장·실행했습니다.

## 발견한 문제와 수정

첫 배포는 성공하고 Pod도 Ready였지만 비루트 UID 10001의 `/data` 파일 쓰기는 Permission denied였습니다. 배포 성공과 데이터 쓰기 가능 여부를 구분해야 합니다.

화면에 선택 항목 **볼륨 접근 그룹**을 추가했습니다. 입력값은 Pod의 `securityContext.fsGroup`과 `fsGroupChangePolicy: OnRootMismatch`로 전달합니다. 기본값은 없고, 컨테이너 실행 사용자는 바꾸지 않습니다. PVC 없는 그룹 설정·음수·상한 초과는 거부합니다. [Kubernetes 네이티브 보안 설정](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)을 사용하며 볼륨 드라이버의 지원이 필요합니다.

새 화면에서 10001을 지정하고 다운로드 YAML과 390/768/1440px 레이아웃을 확인했습니다. 검증 전용 단계에 해당 명세를 연결하고 실행은 앱 화면에서 진행했습니다.

## 결과

- `fsGroup: 10001` 적용 후 같은 비루트 앱에서 `/data` 파일 쓰기와 `/hello` JSON 응답을 확인했습니다.
- 별도 v2 명세를 전용 단계에 연결하고 화면에서 재배포했습니다. Pod UID가 달라졌고 새 Pod가 Ready가 된 뒤 같은 파일 내용이 유지됐습니다.
- 파일은 컨테이너 임시 디스크가 아닌 같은 50Gi PVC에 저장했습니다.
- Java 테스트 199개, 실제 OCI 조회 5개 화면과 390/768/1440px 레이아웃 검증을 통과했습니다.

로컬 증거: `.tools/oke-storage-data.json`의 교체 전후 Pod UID와 파일 일치 결과, `oke-storage-v2-ui.txt/png`의 실제 실행 로그. 계정 참조와 전체 로그는 공개 저장소에 올리지 않습니다.

## 정리

검증 Namespace와 PVC/PV 삭제, OCI 볼륨 `TERMINATED`, 전용 검증 파이프라인 IAM 정책 삭제를 확인했습니다. 배포 이력은 남겼으며, 재실행하려면 Namespace·PVC·인증과 권한을 다시 준비해야 합니다.

## 검증 한계

이 작업은 기존 PVC 연결과 파일 유지 검증입니다. 데이터베이스 일관성 백업, 스냅샷 복원, 노드·가용영역 장애 복구를 대신하지 않습니다. 실행·로그는 화면으로 확인하고 Pod UID·파일 내용·볼륨 정리는 Kubernetes와 OCI API로 교차 확인합니다.
