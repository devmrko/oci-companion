# OKE 환경과 등록 앱 운영 화면

운영자는 Companion에서 VM·OKE 배포 대상을 찾고, 등록한 앱의 상태를 확인한 뒤 Langfuse 등 전문 도구로 이동한다. 에이전트 실행과 데이터 저장은 Companion 없이도 계속된다.

2026-11-08 설계: 기존 환경 조회에 OKE 유형·cluster ID를 추가한다. OKE 상세를 볼 때 Compute 권한을 요구하지 않는다. 20개씩 페이지를 나누고 환경 ACTIVE를 앱 정상으로 표현하지 않는다. 생성 기능은 기존 VM 연결만 지원한다.

앱 연결은 기존 AgentConnectionRegistry를 사용하며 이름·환경·loopback 터널 URL·관리자가 준비한 인증 참조를 등록한다. 주체·리전·구획 범위와 CSRF를 검사한다. 목록은 20개씩 표시하며 선택한 연결의 /health만 조회한다. 상태·버전·조회 시각을 표시하고 오류 때 UP을 유지하지 않는다. 화면 자동 갱신은 선택한 앱에 대해 30초 간격, 기본 끔이다. 창이 닫히면 수집하지 않으며 이력 저장·알림은 OCI Monitoring/Langfuse 등 전문 도구 영역이다. 외부 운영 도구 화면으로 연결하고 trace 저장소를 새로 만들지 않는다.

완료 기준: OKE 목록·상세 렌더링과 scope 거부, VM 조회 미호출, 앱 연결의 범위·페이지·오류 처리 테스트, 실제 OKE 앱 등록 및 화면 UP/0.6.0 확인. 운영 VPN/HTTPS 상시 연결과 서버측 알림은 별도 후속이다.

## 사용 방법

1. 배포 또는 배포 환경에서 VM·OKE 대상을 선택한다. OKE 상세에는 cluster ID가 표시되며, 준비된 파이프라인 실행 화면에서 최종 대상을 다시 확인한다. 현재 환경 선택이 특정 파이프라인을 자동 선택하지는 않는다.
2. 에이전트 실행 → 등록 앱 상태·연결 관리에서 앱 연결을 등록한다. 관리자가 `.tools/agent-credentials.json`에 현재 profile/tenancy/user/region/compartment와 인증 파일 참조를 준비한다. 토큰 파일은 0600 권한으로 저장하고 Git에 넣지 않는다. URL에는 비밀값을 넣지 않는다.
3. 상태 조회에서 UP·버전·조회 시각을 확인한다. 필요하면 30초 갱신을 켠다. Langfuse·외부 운영 도구 버튼으로 상세 추적 화면에 이동한다. 연결된 도구는 자체 인증을 사용한다.

개발 환경에서 HTTP는 loopback 터널로만 허용한다. 인증 참조 등록은 관리자 파일 설정이며 UI에서 비밀 토큰을 발급하거나 권한을 부여하지 않는다. 현재 화면은 연결 추가·조회만 제공하며 등록 삭제·수정 UI는 후속이다. JSON 레지스트리는 단일 Companion 프로세스용이다.

## 2026-11-09 확인 결과

Maven verify 137건, 실패·오류 0. OKE 환경 상세의 compartment 차단, Compute 조회 미호출, 21개 환경의 20+1 페이지 분리, 데모 연결 제한 및 CSRF, health 실패 시 이전 UP 상태를 유지하지 않는 것을 검증했다. 새 빌드를 127.0.0.1:8091에 반영했다. 실제 서울 구획 목록에서 launchpad-service-desk-oke가 OKE 클러스터로 표시됐고 화면에서 앱 연결을 등록했다.

기존 관측 VM이 STOPPED여서 SSH 터널과 OKE 앱 연결이 중단된 것을 발견했다. 현재 화면은 이를 조회 실패로 표시했다. 이 환경의 로컬 터널은 운영 상시 연결을 보장하지 않는다. 운영에서는 VPN 또는 사설망에 배치한 Companion, 대상 앱 readiness, OCI Monitoring 알람, 전문 관측 도구의 수집 상태를 함께 설계해야 한다.

### 중지된 의존 리소스의 복구 순서

실제 확인에서 관측 VM, OKE 노드, 메모리 ADB가 모두 STOPPED였다. 중지 이유는 확인하지 않았으므로 예약 작업 때문이라고 단정하지 않는다. 기존 검증용 리소스만 시작했다. 노드 Ready만으로 앱 Ready가 되지 않았고, ADB 시작도 필요했다. SSH 규칙은 현재 클라이언트 /32와 일치했으며 추가로 개방하지 않았다. 노드 시작 직후 CSI 드라이버 등록 전 발생한 mount 오류는 재시도 과정에서 해소됐다.

후속 기능 명세: 연결에 선택적인 의존 OCI resource ID를 등록하고, 사용자 권한 범위에서 상태를 읽어 앱 health 실패와 함께 보여준다. 예: OKE node NotReady, ADB STOPPED, 관리 터널 unavailable을 구분한다. 조회 실패를 리소스 중지로 단정하지 않으며, 자동 시작은 별도의 명시적 운영 정책을 필요로 한다. 이번에는 진단·복구를 운영자가 수행했고 이 자동 진단 기능 자체를 구현했다고 표시하지 않는다.

복구 완료 확인(2026-11-09 KST): OKE Pod 1/1 Ready, 기존 50GiB PVC Bound를 확인했다. 포트포워딩을 재연결한 뒤 실제 Companion 등록 화면에 `UP · 버전 0.6.0`이 표시됐다. 30초 자동 갱신을 켠 상태에서 조회 시각이 갱신되는 것도 확인했다. 모니터링 주소는 이 Mac의 127.0.0.1:18092 터널이며, 이 검증은 운영 상시 연결 완료를 뜻하지 않는다.

## 의존 리소스 상태 조회 설계 (2026-11-09)

앱 연결마다 VM·ADB·OKE OCID를 최대 8개 등록한다. 현재 사용자·리전·구획에서 GET으로 소속을 확인한 참조만 저장한다. 기존 연결 JSON의 누락된 dependencies는 빈 목록으로 읽는다. 추가·삭제는 CSRF와 현재 범위를 검사한다. 클라우드 리소스를 변경하거나 자동 시작하지 않는다.

상태와 조회 시각, 확인할 항목을 표시한다. 권한 부족·없음·통신 오류는 조회 불가로 표시하며 STOPPED로 추정하지 않는다. 다른 구획의 이름·상태는 노출하지 않는다. OKE ACTIVE는 제어부 상태일 뿐 노드·Pod 정상 증거가 아니다. VM RUNNING도 앱 정상 증거가 아니다. 운영자가 지정한 의존 관계는 장애 원인을 증명하지 않는다. 선택한 앱의 화면 갱신 때만 조회하며 연결/읽기 타임아웃과 재시도 없음으로 제한한다.

검증: 기존 JSON 호환, 유형/OCID 일치, 중복·개수 제한, 범위 차단, 조회 오류·중지·정상 구분, 화면과 실제 OCI 상태를 확인한다. 노드 Ready/Pod/네트워크 진단과 서버측 이력·알림은 후속이다.

### 사용법과 검증 결과

등록 앱 상태에서 앱을 선택한 뒤 ‘연결된 OCI 리소스’에 유형·표시 이름·OCID를 입력한다. 등록 시 현재 OCI 프로파일로 소속 구획을 확인한다. 참조 해제는 Companion 설정만 변경한다. 기존 인증 참조와 앱 주소에는 영향이 없다. 각 조회에는 GetInstance, GetAutonomousDatabase, GetCluster 권한이 필요하며 Companion이 IAM 권한을 자동 부여하지 않는다. 현재는 같은 선택 리전·구획의 리소스만 지원한다.

2026-11-09 KST: Maven verify 141건, 실패·오류 0. 신규 테스트는 구획 불일치 시 상태 비공개, 조회 오류와 STOPPED 구분, OKE ACTIVE의 의미, 기존 JSON 호환, 중복·최대 8개 제한과 유형/OCID 검증을 포함한다. 중지·권한 실패 조건은 모의 조회로 검사했으며 실제 리소스를 중지하지 않았다.

새 빌드를 로컬 8091에 반영하고 실제 Edge 화면에서 서울 Joungmin.Ko 구획의 Service Desk · OKE 연결에 노드 VM, 메모리 ADB, OKE 클러스터를 등록했다. 앱 UP/0.6.0, VM RUNNING, ADB AVAILABLE, OKE ACTIVE와 각각의 조회 시각을 확인했다. 구현은 SDK의 읽기 API와 로컬 참조 저장만 사용하며 리소스를 시작하거나 변경하지 않는다.

한계: 화면 조회 방식이며 서버측 상시 수집·장애 원인 분석·자동 복구가 아니다. 노드의 Kubernetes Ready, Pod 상태, DB 쿼리 성공, Langfuse 수집 성공은 이 표시만으로 확인되지 않는다. 선택적 30초 갱신은 화면을 닫으면 종료된다. 의존 리소스는 순차 조회하며 각 요청의 연결 2초/읽기 3초 타임아웃과 재시도 없음으로 제한한다. 운영에서는 OCI Monitoring 알람 및 전문 도구 연결을 추가해야 한다.

## OKE Deployment·Pod 조회 설계 (2026-11-09)

운영자는 클러스터 ACTIVE와 별개로 앱의 배포 반영 여부, 준비된 복제본 수, Pod의 Ready·재시작 횟수·대기 이유를 확인한다. Companion은 전문 모니터링 저장소를 만들지 않고 관리자가 지정한 Kubernetes 연결을 읽기 전용으로 사용한다.

관리자 설정 `.tools/oke-workloads.json`은 앱 연결 ID, 사용자/profile/tenancy/리전/구획, cluster OCID, kubeconfig 절대 경로, namespace, Deployment 이름을 묶는다. 선택적인 server·tlsServerName은 사설 API 터널용이며 TLS 검증을 끄지 않는다. 사용자 입력으로 실행 파일·kubeconfig·명령을 받지 않는다. kubeconfig는 인증 플러그인 실행 권한이 있으므로 신뢰한 관리자만 관리한다. 기존 OCI 자격 증명을 재사용하는 개발 검증이며 운영에는 해당 namespace의 deployments/replicasets/pods get/list 전용 Kubernetes RBAC를 권장한다. Companion이 RBAC/IAM을 변경하지 않는다.

앱 scope와 등록된 OKE 의존 참조, 실제 OCI 클러스터 구획을 확인한 뒤 고정 kubectl get 명령만 실행한다. Deployment UID → ReplicaSet owner UID → Pod owner UID로 연결해 다른 앱 Pod를 제외한다. 셸은 사용하지 않고 요청/프로세스 시간과 JSON 크기를 제한한다. 응답 원문, 환경 변수, Secret, 로그, 오류 원문을 UI나 파일에 기록하지 않는다. 화면에는 허용한 상태 필드만 표시한다. Pod 표는 20개씩 표시한다.

미관측 generation, 준비 수 부족, 진행 실패는 정상 완료로 표시하지 않는다. 0개로 축소한 앱은 중지로 표시한다. 읽기 실패는 조회 불가이며 이전 정상 상태를 재사용하지 않는다. 클러스터 노드 Ready, 로그·이벤트·알림, 자동 복구, 연결 설정 UI는 후속이다. 완료 기준은 범위/소유자 필터·페이지·실패 상태 테스트와 실제 OKE Service Desk 화면 검증이다.

### OKE 조회 연결 설정

[관리자 설정 예시](../examples/oke-workloads.json)를 `.tools/oke-workloads.json`으로 복사한 뒤 값을 바꾼다. appId는 등록 앱 화면 URL의 selected 값이며, profile/tenancy/user/region/compartment는 해당 연결 범위와 정확히 같아야 한다. clusterId는 그 앱에 등록한 OKE 의존 참조와 같아야 한다. namespace·deployment·신뢰한 kubeconfig 절대 경로는 필수다. 다른 앱은 별도 항목을 추가한다. 파일은 관리자만 수정할 수 있도록 관리하고 비밀 설정은 Git에 넣지 않는다.

Companion 시작 옵션 `--companion.kubectl=/absolute/path/to/kubectl`로 설치된 실행 파일을 지정한다. 설정 파일 경로는 `--companion.oke-workloads=/absolute/path/to/oke-workloads.json`으로 변경할 수 있다. kubectl과 kubeconfig 인증 플러그인(OKE는 OCI CLI)이 Companion 서버에서 실행 가능해야 한다. kubeconfig는 임의 명령을 실행할 수 있으므로 외부 고객이 올린 파일을 그대로 사용하지 않는다.

직접 사설망으로 접근하면 server/tlsServerName을 비워 kubeconfig의 TLS 설정을 사용한다. 로컬 개발용 터널이면 server에 `https://127.0.0.1:<터널 포트>`, tlsServerName에 인증서가 검증하는 원래 API 호스트를 설정한다. 이 설정은 터널을 만들거나 TLS 검증을 해제하지 않는다. kubeconfig의 인증 주체·클러스터·CA·현재 context가 등록한 대상과 일치하는지는 관리자가 확인해야 한다. Companion의 OCI 구획 검사는 Kubernetes RBAC 검사를 대신하지 않는다.

조회는 deployment get, namespace 내 replicasets/pods list를 사용한다. 각 kubectl 호출은 요청 5초·출력 대기 8초, JSON 최대 2MB로 제한하며 초과/실패는 조회 불가다. 큰 namespace의 서버측 선택 조회는 후속이다. 전체 응답을 저장하지 않고 해당 Deployment 소유 Pod의 허용된 상태 필드만 표시한다. ReplicaSet과 Pod는 별도 API 조회이므로 원자적 스냅샷이 아니다. 롤아웃 도중 일시적 불일치는 다음 갱신에서 다시 확인한다.

2026-11-09 KST 실제 Edge 검증: 앱 UP/0.6.0, Deployment 준비 완료, 목표 1/Ready 1/최신 배포본 1, Pod Running·Ready와 재시작 2회를 확인했다. 이번 조회에서 재시작이 발생했다는 의미가 아니라 기존 Pod의 누적 횟수다. 로그·이벤트·클러스터 노드 Ready 및 상시 알람은 아직 연동하지 않았다.

최종 검증: Maven verify 149건 통과(실패·오류 0). 새 테스트는 소유 Pod 필터, NotReady, generation 미반영, 0개 축소, 최신 복제본 미준비, 대상 Deployment 불일치, scope 차단 시 kubectl 미실행, 조회 실패 시 이전 정상 상태 제거, Pod 21개를 20+1로 나누고 범위를 벗어난 페이지를 제한하는 경우를 포함한다.

## 앱 사용 노드 상태 조회 설계 (2026-11-09)

Pod가 Ready여도 노드가 NotReady이거나 DiskPressure/MemoryPressure 상태일 수 있으므로 별도 표로 표시한다. 관리자 옵션 `inspectNodes`는 기본 false다. 활성화하면 해당 Deployment 소유 Pod의 spec.nodeName만 모아 중복 제거하고 20개씩 조회한다. 전체 노드 목록은 조회하지 않는다. 노드는 클러스터 범위 리소스여서 별도의 nodes get RBAC 권한이 필요하며 Companion이 권한을 부여하지 않는다.

노드 조회 실패는 앱·Pod 정상 조회를 지우지 않고 별도 조회 불가로 표시한다. Ready, DiskPressure, MemoryPressure, PIDPressure, NetworkUnavailable 조건과 스케줄링 차단 여부만 표시한다. 없는 조건은 Unknown이며 정상으로 추정하지 않는다. 이는 사용량 측정이 아니므로 디스크 여유 GB나 메모리 사용률은 알 수 없다. 노드 이름은 신뢰한 API 응답에서도 형식을 검증하고 셸 없이 고정 get 명령의 개별 인수로 전달한다. 페이지마다 대상 이름을 지정한 한 번의 kubectl 호출로 기존 시간·출력 크기 제한을 적용한다.

완료 기준: 실제 앱 Pod의 nodeName 전달, 해당 노드만 조회, 옵션 미설정 시 조회 없음, 노드 권한 실패에도 Pod 유지, 누락/압박 조건 구분, 20+1 페이지 및 실제 OKE 화면 확인이다.

### 노드 조회 사용법·검증 결과

관리자 설정 예시의 `inspectNodes`를 true로 바꾸면 해당 앱의 할당 노드를 조회한다. 기존 설정에서 생략하면 false로 동작한다. 기존 kubeconfig 주체에 nodes get 권한이 없으면 노드 표만 조회 불가이며, Deployment·Pod 결과는 유지한다. 노드 접근은 namespace Role만으로 부여할 수 없는 클러스터 범위 권한이다. 운영에서는 별도 RBAC 검토가 필요하며 이 검증에서 권한을 추가하지 않았다.

노드·Pod 표는 각각 20개씩 페이지를 나눈다. 노드 조회는 현재 페이지의 명시된 이름만 get하며 전체 노드 list를 요청하지 않는다. 할당되지 않은 Pod는 노드 목록에 넣지 않고 동일 노드는 한 번만 조회한다. 조회 완료는 API 응답을 받았다는 뜻이며 노드 정상 판정이 아니다. 조건의 True/False/Unknown을 각각 확인한다.

2026-11-09 KST: Maven verify 156건 통과(실패·오류 0). 실제 조회 경로에서 Pod nodeName 전달과 노드 권한 실패 후 Pod 결과 유지, 옵션 기본 끔, 중복 제거, 잘못된 이름의 실행 차단, 예상하지 않은 노드 응답 숨김, 조건 누락의 Unknown, 21개 노드의 20+1 페이지를 검증했다. 실제 Edge 화면에서 노드 10.80.1.205는 Ready=True, DiskPressure/MemoryPressure/PIDPressure/NetworkUnavailable=False, 새 Pod 배치 미차단이었다. 앱 UP/0.6.0과 Deployment 1/1/1 및 Pod Running·Ready도 유지됐다. 정상 단일 노드 실측이며 실제 장애를 발생시키지는 않았다.

현재 남은 운영 연동: 로그·이벤트, 상시 알람, 디스크 여유 GB·메모리 사용률 수집과 용량 계획 연결이다. 이 정보는 OCI Monitoring/Logging 등 전문 도구를 우선 활용하며 별도 관측 저장소를 Companion에 만들지 않는다.

## 노드 플랫폼 표시

노드 표에 nodeInfo의 CPU 아키텍처·OS와 kubernetes.io/arch·os 라벨을 별도로 표시한다. 알려진 값의 차이는 불일치, 누락은 미확인이다. 일치가 이미지 호환성이나 새 Pod 배치 성공을 보장하지 않는다. 기존 inspectNodes와 nodes get 권한을 재사용한다. [실제 화면 검증](../validation/oke-node-platforms-2026-11-10.md).

## 화면 기반 OKE 조회 연결·경고 이벤트

등록 앱 상태의 OKE 조회 연결에서 클러스터·namespace·Deployment를 선택해 등록·수정한다. 네이티브 OCI kubeconfig와 현재 프로파일을 사용하며 HTTP health 인증 없이 Kubernetes 상태를 조회할 수 있다. 기존 관리자 설정은 유지한다. 현재 소유 Pod의 경고 이벤트를 최대 20개 표시하고 조회 실패를 Pod 상태와 분리한다. [구현·권한·실제 검증](../validation/oke-connections-2026-11-10.md).

## Pod 로그·배포 이력 연결·로컬 해제

등록 앱의 Pod 로그에서 일반·init 컨테이너, 최근 줄 수, 현재/직전 실행을 선택한다. 조회 전후 Pod UID를 확인하고 최대 64KiB만 반환한다. 배포 이력은 기록된 환경 ID를 기준으로 현재 OKE 연결을 찾으며 당시 앱/버전 일치를 추정하지 않는다. OKE 연결 해제는 로컬 등록·현재 생성 kubeconfig만 정리한다. [권한·실제 검증](../validation/oke-logs-2026-11-10.md).
