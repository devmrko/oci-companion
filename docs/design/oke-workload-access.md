# OKE 앱 OCI 권한 점검

화면: 배포 → OKE 배포 구성 → 앱 설정·실행 권한 → 앱 OCI 권한 점검.
직접 경로: `/deployments/oke/access`.

## 범위

- Enhanced OKE 클러스터의 기존 ServiceAccount 선택.
- 현재 구획의 기존 Object Storage 버킷 선택.
- Workload Identity로 `ListObjects(limit=1)` 호출.
- 필요한 IAM 정책 표시, 표준 Kubernetes Job JSON 다운로드.
- Job 실행·결과 조회·삭제. 기존 앱 배포는 변경하지 않음.

| 단계 | OCI/Kubernetes 기능 | 조건 |
|---|---|---|
| 대상 조회 | DevOps 배포 환경, OKE 클러스터, ServiceAccount, 버킷 조회 | 사용자 조회 권한 |
| 정책 확인 | `inspect objects`, Workload Identity 조건 | 클러스터·Namespace·ServiceAccount·버킷 제한 |
| 정책 적용 | OCI Console → Identity & Security → Policies | 관리자 수동 적용, 구획 또는 상위 구획 |
| 실행 | `batch/v1 Job`, OCI Python SDK signer | Kubernetes Job create 및 Pod 실행 가능 |
| 결과 | Job·Pod 상태와 마지막 구조화 로그 | Job get, Pod list, pods/log get |
| 삭제 | 해당 검증 Job 삭제 | Job delete |

## 서비스 계약

`OkeWorkloadAccess`: `options`, `prepare`, `execute`, `result`, `remove`.
웹과 향후 자연어 도구는 같은 인터페이스를 사용한다. 구현체는 OCI SDK와
설정된 kubectl만 호출한다. 실행 직전 대상과 생성 명세를 다시 검증한다.
웹 실행 표는 세션·OCI 사용자·프로파일·리전·구획에 연결되며 1회 실행,
1시간 만료, 세션당 16개로 제한한다. OCI 인증 키는 Pod에 전달하지 않는다.

## 검증 Job

Oracle 공식 OCI CLI 컨테이너의 Python SDK를 사용한다. CLI 인증 방식은 쓰지 않는다.
이미지는 다중 아키텍처 digest로 고정한다. SDK 인증은
`get_oke_workload_identity_resource_principal_signer()`이며 실행당 하나만 생성한다.

- CPU 요청 100m / 상한 500m, 메모리 요청 128Mi / 상한 256Mi.
- 실행 제한 120초, 자동 재시도 없음, 종료 후 TTL 3600초.
- 권한 상승 금지, Linux capabilities 제거.
- 로그: 스키마 버전·작업·상태·HTTP 코드만 출력.
- 객체 이름·내용·토큰·원문 예외는 기록하지 않는다.
- 애플리케이션 로그 저장소를 별도로 만들지 않는다. Kubernetes Job/Pod가 원본이다.

## 결과 해석

`GRANTED`는 실제 `ListObjects` HTTP 200이다. Job 성공 상태와 별개다.
401/403/404는 권한 부족 또는 대상 없음으로 표시한다. IAM 전파 후에는 새 Job으로
검증한다. 실행 요청 결과가 불명확하면 같은 Job을 조회하며 자동 재실행하지 않는다.

이 검사는 선택한 ServiceAccount를 사용하는 **별도 검증 Pod**의 결과다.
기존 앱의 SDK 구성·NetworkPolicy·Secret/Vault 읽기·객체 쓰기 권한을 보장하지 않는다.
새 Namespace, ServiceAccount, 버킷 생성은 기존 Kubernetes/OCI 관리 도구로 준비한다.

## 공식 근거

- [OKE Workload Identity](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm)
- [Oracle OCI CLI 컨테이너](https://github.com/oracle/docker-images/tree/main/OracleCloudInfrastructure/oci-cli)
