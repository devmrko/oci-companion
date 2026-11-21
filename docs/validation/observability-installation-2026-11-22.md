# 관측 도구 설치·연동 검증 — 2026-11-22

## 제공 범위

Companion은 공식 설치 구성 ZIP, 앱별 연결 설정, 계측 환경변수를 제공합니다.
설치는 대상 환경에서 수행하며 분석은 각 제품 UI를 사용합니다.
설정 저장은 실제 수집 성공을 의미하지 않습니다.

- SigNoz: Foundry 0.5.0, SigNoz 0.151.0, Collector 0.144.18. Docker 평가 구성.
- Pinpoint: 공식 Kubernetes 차트 4.0.1, Helm 3, amd64, 기본 StorageClass. 평가 구성.
- 생성 파일은 `ObservabilitySetup.installationFiles` 서비스 인터페이스로 제공합니다.

## 실제 검증

- SigNoz Docker 컨테이너 정상 기동. Java·Python 각각 실제 HTTP 요청 10회 이상 수집.
- SigNoz Traces Explorer에서 두 서비스와 GET /hello 확인. Java 요청 상세 HTTP 200, 11.62ms 확인.
- Pinpoint를 기존 OKE의 독립 namespace에 설치. Collector/Web/HBase/MySQL/Redis/Kafka/Pinot/ZooKeeper 정상 기동.
- Java 21·Spring Boot 4.1.1 예제에 네이티브 4.0.1 에이전트 연결. 최종 설정으로 HTTP 200 요청 100회 실행.
- Pinpoint 서비스 목록, 서비스맵 요청 집계(Total 1, Error 0), Inspector JVM 지표 그래프를 브라우저에서 확인. 샘플링 기본값 1/20이므로 요청 수와 저장 추적 수는 다릅니다.
- Pinpoint 스레드 덤프와 OTLP Python 수신은 이번 검증 범위에 포함하지 않습니다.

## 실제 설치에서 수정한 항목

1. Foundry 생성 OpAMP 주소가 Postgres를 가리켜 수집되지 않음. 공식 casting patch로 SigNoz Web의 4320/v1/opamp로 보정.
2. OKE CRI-O가 축약 이미지 이름을 거부. Helm global image registry 및 기본 Kustomize post-renderer로 docker.io 명시. ConfigMap 내부 초기화 Job에도 적용.
3. Pinpoint 4는 serviceName 필수. DEFAULT 그룹을 지정하며 인스턴스 표시 이름은 agentName 사용. agentId는 4.x가 자동 생성.
4. Pinpoint 구성에는 Secret이 포함되므로 렌더링 파일은 umask 077로 생성.

## 운영 제한

최소 차트는 운영 HA 구성이 아닙니다. 평가용 HBase와 동결된 bitnamilegacy 이미지를 포함합니다.
운영은 공식 production 구성, 관리형/별도 저장소, 인증·TLS·보존 기간·백업을 적용해야 합니다.
로컬 분석 UI는 loopback으로만 열었습니다. 다른 기기에서는 터널 또는 별도 접근 구성이 필요합니다.

## 원문

- https://signoz.io/docs/install/docker/
- https://github.com/SigNoz/foundry
- https://github.com/pinpoint-apm/pinpoint-kubernetes
- https://github.com/pinpoint-apm/pinpoint/releases/tag/v4.0.0
