# VM 배포 구성

기존 VM 환경과 Generic Artifact의 고정 버전을 선택해 OCI DevOps 배포 파이프라인을 생성한다. 앱이 꺼져도 OCI에서 명세와 실행 이력을 관리한다.

## 화면

- 배포 → 환경 선택 → 배포본·설정 구성 (`/deployments/configure`). 기존 파이프라인 실행 화면에서도 새 구성을 열 수 있다.
- 글로벌 프로파일·리전·구획으로 환경·불변 저장소·배포본 조회.
- VM별 OCPU, vCPU, 메모리, 로컬 디스크, 연결된 부트·블록 볼륨의 할당량 조회. 읽기 실패는 미확인으로 표시한다. OS 여유 공간이나 파일시스템 크기로 해석하지 않는다.
- 이름, 설명, 디렉터리, 실행 계정, 제한 시간, 동시 배포 수 입력.
- 중지·설치·시작·상태 확인 명령. 설치·상태 확인은 필수. 일반 변수와 Vault Secret 참조를 별도 입력.
- 생성 전 고정 버전·SHA-256·생성할 YAML 확인. 생성 후 기존 실행 화면으로 이동한다.

## OCI 매핑

| 입력/행동 | OCI 기능 |
| --- | --- |
| 파일 배포본 | GenericDeployArtifactSource, 고정 경로·버전, argumentSubstitutionMode NONE |
| 명령·변수·디렉터리 | DeploymentSpec + InlineDeployArtifactSource, files / steps / env.variables / env.vaultVariables |
| 실행 계정·제한 시간 | deployment spec runAs / timeoutInSeconds |
| 실패 시 명령 | steps.onFailure (이전 버전 자동 복구가 아님) |
| VM 배포 | ComputeInstanceGroupDeployStage |
| 동시 배포 수 | ComputeInstanceGroupLinearRolloutPolicyByCount.batchCount |
| 배치 간격 | OCI 기본값 사용 (실제 조회 60초) |

생성 순서는 파이프라인 → 파일 참조 → 명세 → VM 단계이다. 이 호출들은 배포를 실행하지 않는다. 세션의 일회용 검토 티켓과 재조회 fingerprint로 프로파일·범위·환경·파일 변경을 검증한다. API 일부 실패 시 확인된 ID와 OCI 요청 정보를 표시하고 자동 삭제·재생성하지 않는다.

현재 명시적 VM ID 환경과 불변 Generic 저장소를 지원한다. 쿼리 선택 환경, 새 OKE/Container/Functions 배포 구성, VM 생성·사양 변경은 이 화면의 구현 범위가 아니다. 실행은 기존 파이프라인 실행 검증을 사용하며 중지 VM은 차단한다. 런타임, Run Command 에이전트, 실행 계정과 서비스 IAM 권한은 별도 준비해야 한다.

## 근거

- [OCI deployment specification](https://docs.oracle.com/en-us/iaas/Content/devops/using/deployment_specs.htm)
- [Instance group deployment](https://docs.oracle.com/en-us/iaas/Content/devops/using/deploy_instancegroups.htm)
- [Rollout policy SDK](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/devops/models/oci.devops.models.ComputeInstanceGroupRolloutPolicy.html)
- [Volume expansion](https://docs.oracle.com/en-us/iaas/Content/Block/Tasks/update-online-resize-block-boot-volume.htm)
