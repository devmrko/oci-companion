# Pinpoint 운영 구성

평가 ZIP을 그대로 운영에 사용하지 않습니다. 공식 차트의 `values-production.yaml`을 기준으로 환경별 값을 관리합니다. Companion은 저장·분석 서버를 대신하지 않습니다.

| 항목 | 설정 위치 | 완료 기준 |
| --- | --- | --- |
| Web·Collector | Helm replicas, resources, PDB | 2개 이상 복제본, 실제 부하 측정, Pod 종료 중 수집·조회 확인 |
| 저장소 | 외부 HBase/HDFS·ZooKeeper, MySQL, Redis, Kafka, Pinot | 장애 복구·백업 복원 확인. 평가용 단일 HBase는 HA가 아님 |
| 용량·보존 | HBase TTL, Kafka retention, Pinot retention | 요청량·샘플링·보존 기간 기준 산정, 디스크·consumer lag 감시 |
| 로그인·TLS | Web ingress, login Secret | HTTPS 로그인 확인, 관리 계정 교체, 비공개 Collector 접근 |
| 통신 | NetworkPolicy·NSG, Collector Service | Java TCP 9991·9992·9993, OTLP HTTP 9997. Web 주소와 구분 |
| 인증값 | Secret 참조 | Git·일반 환경변수 다운로드에 비밀값 미포함 |
| 업그레이드 | 차트·이미지 버전, 백엔드 스키마 | 사전 백업·복구 시험, 스키마 호환성 확인 |

공식 차트의 외부 백엔드 지원 범위와 TLS·인증 제약을 먼저 확인합니다. replicas만 늘려 HA 완료로 판단하지 않습니다.

## Java 실시간 진단

Web·Collector의 `pinpoint.modules.realtime.enabled`, Web의 `config.enable.activeThreadDump`, Redis 연결을 확인합니다. 네이티브 Java 에이전트가 필요합니다.

Pinpoint 서비스맵에서 앱 → 시간 범위 옆 ▶ → 서버 행의 덤프 버튼 → 처리 중 요청을 선택합니다. 요청이 끝나면 해당 활성 요청의 덤프는 수집할 수 없습니다. 테스트는 인증된 지연 요청으로 수행하고 운영 요청을 임의로 지연시키지 않습니다.

## Python 요청 추적

OpenTelemetry 계측으로 `/hello` 등 실제 요청을 보내고 Pinpoint에서 서비스·요청·응답시간을 확인합니다. HTTP 수신 성공만으로 화면 저장 성공을 판단하지 않습니다. Java 활성 스레드 덤프와 다른 기능입니다.

## 공식 구성

- https://github.com/pinpoint-apm/pinpoint-kubernetes/blob/master/values-production.yaml
- https://github.com/pinpoint-apm/pinpoint-kubernetes/blob/master/docs/PRODUCTION.md
- https://github.com/pinpoint-apm/pinpoint-apm.github.io/blob/main/documents/realtime.md

2026-11-22 검증: OKE 차트 4.0.1에서 Java 21 활성 요청 덤프와 Python OpenTelemetry HTTP 요청 30건의 서비스맵·응답시간 집계를 확인했습니다. 운영 HA·장애 복구 부하 시험은 포함하지 않습니다.
