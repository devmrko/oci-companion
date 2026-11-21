# Pinpoint 4.0.1 평가 환경

필수: Kubernetes, Helm 3, kubectl, x86 노드, 기본 StorageClass.
최소 평가 구성의 요청량: 약 8.3GiB 메모리와 2 CPU. 초기화 Job·클러스터 기본 서비스 여유분도 필요합니다.
ARM 전용 클러스터에서는 공식 HBase·Quickstart 이미지를 그대로 실행할 수 없습니다.
운영용이 아닙니다. OKE 설치와 Java 앱 등록을 확인했습니다.

1. `kubectl config current-context`로 대상 클러스터를 확인합니다.
2. `sh prepare.sh`로 버전 고정 차트와 최소 구성, rendered.yaml을 준비합니다.
3. CPU·메모리·노드 아키텍처·스토리지와 렌더링 결과를 확인합니다.
OKE의 CRI-O 이미지 이름 검증을 위해 기본 Kustomize로 docker.io 주소를 명시합니다.

4. 스크립트가 출력한 helm 명령을 실행합니다. 전용 pinpoint namespace를 사용합니다.
5. `kubectl port-forward -n pinpoint service/pinpoint-web 18081:8080` 후 http://127.0.0.1:18081 을 엽니다.

권한: namespace 및 차트 리소스 생성·조회, PVC 생성, Pod 로그·포트 전달. OCI IAM만으로 Kubernetes RBAC가 부여되지는 않습니다.

Java 네이티브 에이전트: 인스턴스별 Agent Name를 지정하고 Collector TCP 9991·9992·9993으로 연결합니다. OpenTelemetry Java agent와 중복 적용하지 않습니다.
OTLP 추적: 기본 HTTP 포트 9997, 경로 /v1/traces. Companion에는 http://수집호스트:9997 기본 주소를 입력합니다. Web 주소와 다릅니다.
실시간 요청·스레드 진단은 Pinpoint Java 에이전트와 실시간 모듈·Redis 구성을 확인합니다. Python OTLP 추적을 동일한 덤프 지원으로 해석하지 않습니다.

종료: `helm uninstall pinpoint -n pinpoint`. PVC는 별도로 남을 수 있으므로 임의 삭제하지 않습니다.

공식 원문: https://github.com/pinpoint-apm/pinpoint-kubernetes
운영: https://github.com/pinpoint-apm/pinpoint-kubernetes/blob/master/docs/PRODUCTION.md
계측: https://github.com/pinpoint-apm/pinpoint-kubernetes/blob/master/docs/AGENTS.md

Pinpoint 4의 필수 서비스 그룹은 `DEFAULT`로 생성합니다. 다른 그룹은 Pinpoint Web에 먼저 등록한 뒤 `-Dpinpoint.serviceName`을 변경하세요. 앱 이름은 `applicationName`, 인스턴스 이름은 `agentName`입니다. 4.x의 agentId는 자동 생성됩니다.
