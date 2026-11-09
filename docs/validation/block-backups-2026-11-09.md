# 블록 볼륨 백업·복원 실검증

2026-11-09. DEFAULT / ap-seoul-1 / 선택 구획의 별도 50GB 검증 볼륨을 사용했습니다. 실제 화면에서 OCI SDK 작업을 실행했으며, 파일 내용은 Kubernetes API로 교차 확인합니다.

## 구현 범위

백업·복구 메뉴는 OCI 블록 볼륨·백업 목록을 조회하고 전체/증분 백업, 기존 백업에서 새 볼륨 복원을 제공합니다. 기본 보존은 무기한입니다. 복원한 볼륨은 자동 연결하거나 마운트하지 않습니다. 부트 볼륨·볼륨 그룹·정기 정책·보존 잠금은 OCI 콘솔에서 관리합니다.

미리보기는 쓰기를 하지 않습니다. 실행 시 프로파일·리전·구획, 원본 ETag·상태를 다시 확인합니다. CSRF 검사와 일회용 확인 요청, OCI 재시도 토큰을 적용합니다. 결과가 불명확하면 목록에서 확인하도록 안내합니다.

## 환경 제약과 검증 방법

기존 VM의 Run Command는 처음 ACCEPTED에 머물렀습니다. 이후 읽기 점검은 실행됐지만 `ocarun`의 sudo 권한이 없어 FAILED로 종료됐습니다. 원격 디스크 변경은 실행하지 않았습니다. 새 VM 생성은 OCI 404 NotAuthorizedOrNotFound로 거절됐습니다. 과거 이미지와 현재 조회한 이미지 모두 AVAILABLE이어서 이미지 폐기로 단정하지 않았습니다. 다른 프로파일은 다른 테넌시여서 전환하지 않았습니다. 기존 VM은 STOPPED로 복원하고 검증용 빈 볼륨·임시 실행 권한을 분리·제거했습니다.

파일 검증에는 접근 가능한 기존 OKE 클러스터의 별도 Namespace를 사용했습니다. OCI CSI 정적 PV로 이 검증 볼륨만 연결했습니다. 클러스터 노드·기존 워크로드·네트워크 규칙은 변경하지 않았습니다.

## 확인한 결과

- 비루트 Pod에서 4MiB 파일을 기록하고 SHA-256을 저장했습니다. sync 후 Pod를 종료해 쓰기를 멈췄습니다.
- 화면에서 전체 백업을 생성하고 OCI `AVAILABLE` 상태를 확인했습니다.
- 화면에서 별도 볼륨 복원을 실행했습니다. 복원 Pod의 파일 SHA-256이 백업 전 원본과 일치했습니다.
- VM 사양 화면에서 OKE 노드에 연결된 검증 볼륨만 50→51GB로 확장했습니다. CPU·메모리·부트 볼륨은 변경하지 않았습니다.
- 해당 PVC만 51Gi로 조정하자 CSI가 ext4 파일시스템을 확장했습니다. 파일시스템 크기 52,521,566,208 → 53,578,465,280바이트, 파일 해시 유지, 복원 볼륨은 50Gi 유지 확인. 일반 VM에서 수동 rescan/resize를 실행한 검증은 아닙니다.
- Java 테스트 205개 통과. 실제 목록·백업 화면의 390/768/1440px 레이아웃 검증 통과.

이 검증은 작은 일반 파일 기준이며 데이터베이스의 애플리케이션 일관성이나 재해복구 검증은 아닙니다.

참고: [OCI 백업 생성](https://docs.oracle.com/en-us/iaas/Content/Block/Tasks/create-bv-backup.htm), [볼륨 확장](https://docs.oracle.com/en-us/iaas/Content/Block/Tasks/resizingavolume.htm), [기존 볼륨의 CSI 연결](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_topic-Provisioning_PVCs_on_BV-PV_Migrating-from-FlexVolume.htm).

로컬 증거: `.tools/storage-data-verification.json`, `storage-backup-ui.png`, `storage-restore-ui.png`, `storage-expand-ui.png`, `storage-pvc-final.txt`. 비밀값과 전체 계정 로그는 공개 저장소에 포함하지 않습니다.

## 정리

전용 Namespace·PV 두 개 삭제, 원본·복원 볼륨과 백업 TERMINATED, 기존 VM STOPPED를 확인했습니다. 임시 VM은 생성되지 않았습니다. 임시 실행 권한을 제거했고 읽기 명령은 FAILED로 종료되어 대기 중인 작업이 없습니다.
