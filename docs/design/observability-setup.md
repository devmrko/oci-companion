# 관측 도구 설치·연동

2026-11-22 사용자 승인: Grafana·SigNoz·Pinpoint를 선택해 설치하거나 기존 서버에 연결한다. APM 차트·추적 저장소·분석기는 Companion에서 구현하지 않는다.

## 화면 흐름

앱 상태 → 관측 도구 연결 → 도구 선택 → 공식 설치 안내(선택) → 연결 설정 → 계측 → 제품 화면에서 수집 확인.

- 저장: 제품, 분석 URL, OTLP HTTP 수집 URL, 서비스명, Java/Python.
- 기존 앱 등록 JSON 재사용. 프로파일·테넌시·사용자·리전·구획별로 분리.
- 설정 저장과 수집 성공은 별개. 임의 HTTP 응답으로 수집 성공을 판정하지 않는다.
- 인증은 배포 환경의 Secret 및 제품 로그인으로 처리. URL에 인증정보·쿼리·앵커를 받지 않는다.
- Java 서비스 경계는 `ObservabilitySetup`. 웹과 향후 도구 호출에서 같은 인터페이스 사용.
- Java Foundation의 HTTP 공통 코드와 프로젝트 Registry를 확인했다. 이번 변경은 원격 HTTP 클라이언트를 추가하지 않고 기존 Registry를 재사용한다.

## 제품별 구분

| 제품 | 설치·수집 | 진단 |
|---|---|---|
| Grafana | Cloud 또는 자체 저장소·Collector 구성. Grafana만 설치하면 추적 저장소가 생기지 않음 | Pyroscope 등 별도 구성. 전체 덤프와 다름 |
| SigNoz | 공식 Foundry/배포 문서 사용 | 언어·수집기별 지원 확인 |
| Pinpoint | Collector·Web·저장소 구성. OTLP 추적은 4.x 기준 | Java 활성 요청·스레드 덤프는 네이티브 에이전트와 실시간 모듈·Redis 필요 |

OTel Java 에이전트와 Pinpoint Java 에이전트를 중복 적용하지 않는다. Python의 추적 지원을 Java와 동일한 덤프 지원으로 표시하지 않는다.

## 원문

- [Grafana 설치](https://grafana.com/docs/grafana/latest/setup-grafana/installation/)
- [Grafana 개발용 OTLP 통합 구성](https://github.com/grafana/docker-otel-lgtm): 운영용 설치와 구분.
- [SigNoz 설치](https://signoz.io/docs/install/docker/): 현재 문서는 Foundry를 안내하며 기존 deploy Compose 방식은 폐기 안내.
- [Pinpoint 설치](https://pinpoint-apm.gitbook.io/pinpoint/getting-started/installation): 이전 pinpoint-docker 저장소는 보관 상태. 버전 호환 확인 후 사용.
- [Pinpoint 실시간 요청·덤프](https://github.com/pinpoint-apm/pinpoint-apm.github.io/blob/main/documents/realtime.md)

## 남은 범위

제품별 설치 자동 실행, 버전별 운영 템플릿 검증, 인증 참조의 배포 자동 주입, 수집 상태 API 확인, 서비스·시간 범위를 담은 제품별 상세 링크. 현재 화면은 설정 저장·공식 설치/계측 안내·환경변수 다운로드·분석 화면 이동을 제공한다.
