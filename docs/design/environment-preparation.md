# 배포 환경 준비

2026-11-09. 기존 리소스를 먼저 선택하고 부족한 설정만 OCI에 만든다. 프로파일·리전·구획은 상단 선택을 따른다. 환경 참조 생성, 런타임 생성, 앱 정상 상태를 구분한다.

| 화면 | OCI 작업 | 입력·검증 |
| --- | --- | --- |
| VM 조회 → 사양 변경 | ListShapes, GetInstance, UpdateInstance | 현재 Shape의 CPU·메모리 범위 조회. STOPPED Flex VM만 변경. 메모리/OCPU 비율 검사. 자동 시작 없음. |
| VM → 스토리지 확장 | GetBootVolume/GetVolume, UpdateBootVolume/UpdateVolume | 현재 연결·선택 구획의 AVAILABLE 볼륨만 선택. 증가만 허용. OS 재검색·파일시스템 확장은 별도. |
| OKE 환경 연결 | ListClusters, GetCluster, ListNodePools/GetNodePool, CreateDeployEnvironment | 기존 ACTIVE 클러스터를 프로젝트에 연결. 관리형 노드 풀 사양 조회. 사설 접속은 같은 구획·VCN의 IPv4 서브넷과 선택 NSG. |
| Container Instances | ListContainerInstances/Shapes, CreateContainerInstance | 실제 AD·Shape·서브넷·OCI Registry 이미지 선택. CPU·메모리는 API 범위와 비율 검증. 이미지 digest 고정. |
| Container 상태·로그 | GetContainerInstance, GetContainer, RetrieveLogs | 구획·부모 인스턴스 일치 확인. 네이티브 상태·종료 코드·HTTP 확인 결과와 현재 로그 최대 256KiB. |

## 변경 적용

VM과 Container 생성은 입력 → 미리보기 → OCI 적용 흐름이다. 사용자·리전·구획과 10분 유효한 단일 사용 티켓을 묶는다. VM은 적용 직전 스냅샷을 다시 읽고 ETag 조건부 갱신을 사용한다. Container는 이미지 digest·ETag·Shape 범위·서브넷 조건을 재검증한다. 생성은 OCI retry token을 사용한다. 결과가 불확실하면 재생성 전에 OCI에서 조회하도록 표시한다.

VM 볼륨 축소, 자동 VM 시작, 네트워크 보안 규칙 수정은 수행하지 않는다. 실제 변경 권한·서비스 한도·이미지 제약 판단은 OCI가 수행한다. 스토리지 할당량을 OS 여유 공간으로 표시하지 않는다.

## Container 재사용

생성 요청은 OCI SDK의 직렬화기를 사용해 네이티브 JSON으로 내려받는다. `oci container-instances container-instance create --from-json file://container-instance.json`에 프로파일·리전을 지정해 앱 없이 사용한다. JSON에는 명시적으로 입력한 비밀값 아닌 환경변수와 명령 배열만 넣는다. Registry 인증정보는 저장하지 않는다. 사설 OCIR는 Container Instances 리소스 주체의 IAM 권한을 사용한다. 공용 IP가 없으면 기본 NAT 경로 또는 전체 OCI 서비스 게이트웨이 경로를 미리 검사한다. 사용자 정의 DRG·프록시 경로는 이 도우미의 지원 범위가 아니다. 라우트 존재가 DNS·보안 규칙·IAM 접근 성공을 보장하지는 않는다.

HTTP 상태 확인은 선택 항목이다. 포트·경로를 지정하면 OCI가 15초 간격으로 확인한다. 실패 조치는 NONE으로 설정하며 자동 종료하지 않는다. 기본 임시 저장소 15GB는 이미지·로그·루트 파일시스템이 공유한다. 이 도우미는 FSS·영구 볼륨 연결, 외부 Registry/Vault 인증, 다중 컨테이너를 아직 구성하지 않는다. 현재 OCI 이미지 메타데이터만으로 아키텍처를 확정하지 않으므로 선택한 Shape와 이미지의 CPU 호환 확인을 표시한다.

## 화면 원칙

기존 공통 card, section-stack, form-toolbar, build-field-grid, ui-control, ui-button을 사용한다. 체크박스와 로그 영역도 공통 클래스로 처리한다. 선택한 범위는 POST 미리보기에도 유지한다. 각 화면의 도움말에서 콘솔 위치·CLI·IAM 정책을 제공한다. 클러스터 ACTIVE와 컨테이너 ACTIVE를 앱 정상 응답으로 간주하지 않는다.

## 범위

OKE 클러스터·노드 풀의 신규 생성/수정, OKE 신규 배포 파이프라인 구성, Container 업데이트·자동 교체·자동 롤백은 별도 작업이다. 기존 OKE 파이프라인 실행 기능은 유지한다. VM 사양 변경과 환경 연결 기능은 각각 VmSizing, OkePreparation, ContainerPreparation 인터페이스로 분리한다.

## 근거

- https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/resizinginstances.htm
- https://docs.oracle.com/en-us/iaas/Content/Block/Tasks/resizingavolume.htm
- https://docs.oracle.com/en-us/iaas/Content/devops/using/create_oke_environment.htm
- https://docs.oracle.com/en-us/iaas/Content/container-instances/creating-a-container-instance.htm
- https://docs.oracle.com/en-us/iaas/Content/container-instances/container-instance-shapes.htm
- https://docs.oracle.com/en-us/iaas/Content/container-instances/permissions/policy-reference.htm
