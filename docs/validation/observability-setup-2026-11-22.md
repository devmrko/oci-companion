# 관측 설치·연동 검증

2026-11-22, 로컬 DEFAULT / ap-seoul-1 / Joungmin.Ko. OCI 리소스 생성 없음.

- Java 자동 테스트 315개 통과.
- 브라우저: Pinpoint 설정 저장, Grafana로 변경, 재조회, 환경변수 다운로드, 분석 화면 새 탭 열기 확인.
- 기존 앱 등록 JSON 호환, 다른 구획 접근 거절, OKE 등록 수정 후 관측 설정 유지 테스트.
- URL의 스크립트 스킴·사용자정보·쿼리·앵커 거절 테스트.
- Grafana 공식 개발용 이미지 digest `sha256:1b852b77d7bcadb7699bfe7c78a51d059dff85168de674728cf7abf4474645e5` 실행. 로컬 loopback 13000(UI), 14318(OTLP), 13200(검증용 Tempo API).
- Java 예제에 OpenTelemetry Java agent 2.34.0 적용. 실제 `/hello` 12건 전송. Grafana Explore/Tempo에서 목록과 개별 요청 상세 확인. 한 요청은 약 71.67ms. JVM 및 HTTP 메트릭 원본 수집 확인.
- Python Flask 예제에 공식 자동 계측 적용. 실제 `/hello` 7건 전송. Grafana Explore/Tempo에서 목록과 요청 선택 확인. 의존성은 예제 requirements.txt에 고정.
- 검색 반영 지연 관찰: 초기 검색 0건 이후 7건 조회. 설정 저장 또는 최초 빈 검색을 수집 성공/실패로 단정하지 않음.

## 한계

Grafana는 로컬 검증용이며 운영 설치가 아니다. SigNoz·Pinpoint의 실제 설치·수집, 네이티브 Pinpoint 스레드 덤프, Python 프로파일·덤프는 이번 검증에 포함하지 않았다. 설치 자동 실행·수집 상태 API·인증 Secret 자동 주입은 미구현이다. 원격 브라우저에서 로컬 Grafana를 열려면 SSH 터널이 필요하다.

검증 데이터는 앱 예제에서 생성한 실제 요청이며 사용자 트래픽이 아니다. UI에 샘플 수치를 하드코딩하지 않는다. 컨테이너 삭제 시 로컬 검증 데이터는 삭제된다.
