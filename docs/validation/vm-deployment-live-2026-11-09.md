# VM 실제 배포·복구 검증 — 2026-11-09

OCI Companion 화면에서 v1 배포 → v2 배포 → v1 파이프라인 재실행을 수행했다. 세 실행 모두 OCI SUCCEEDED와 실제 HTTP 응답 버전을 화면의 OCI Logging 로그에서 확인했다. 자동 롤백 정책이 아니라 이전 버전을 명시적으로 다시 배포하는 복구이다.

## 환경과 실행

- DEFAULT / ap-seoul-1 / Joungmin.Ko, 실제 OCI SDK 및 Chromium 사용. 운영 구현에 mock 없음.
- 대상: `oci-agent-launchpad-demo-vm-ol9`, 별도 사용자 경로 `/var/tmp/companion-hello`. 기존 앱 파일·서비스 설정 변경 없음.
- 예제: `examples/deployment-hello/app.py`, 표준 라이브러리 HTTP 서버. 127.0.0.1:8080만 사용. HTTP 요청은 VM의 네이티브 배포 상태 확인 명령이 수행했고 응답을 OCI 로그에 출력했다. 외부 공개 URL 브라우저 접속 검증은 아니다.
- v1/v2 패키지는 로컬에서 생성해 기존 불변 Generic Artifact 저장소에 등록했다. 이 기록은 새 OCI 빌드 실행을 포함하지 않는다.
- 배포 환경·파이프라인 생성·실행은 `scripts/browser-vm-live.cjs`가 실제 UI 입력과 버튼 클릭으로 수행했다. SDK 보조 작업은 VM 준비·범위를 제한한 IAM 정책·파일 업로드·상태 재조회에 사용했다.
- 앱이 중지되어도 OCI에 설정·이력·로그가 유지된다. 검증 중 앱 서버를 재시작한 후에도 이력을 조회했다.

| 순서 | 기대 HTTP 버전 | OCI 상태 | 화면 로그 수 | 실행 ID |
| --- | --- | --- | --- | --- |
| 1 | v1 | SUCCEEDED | 42 | `ocid1.devopsdeployment.oc1.ap-seoul-1.amaaaaaavsea7yiaahmzgxxvunr5bcjsmi7d2fhxlrrrw2z4phvg5jikbzka` |
| 2 | v2 | SUCCEEDED | 39 | `ocid1.devopsdeployment.oc1.ap-seoul-1.amaaaaaavsea7yialyv4nukmerh34nohyqxwkp6mlcocuc6tm7ga63z6iyqq` |
| 3 | v1 | SUCCEEDED | 42 | `ocid1.devopsdeployment.oc1.ap-seoul-1.amaaaaaavsea7yiav5qj6eds77hbovn5vper4ns26xmcd7eb4tz6ihxsh47a` |

고정 버전 파이프라인:

- v1: `ocid1.devopsdeploypipeline.oc1.ap-seoul-1.amaaaaaavsea7yiaeyqjjsmfytiirqgo34vqpm3xath2tyg3x2gjmmuow2da`
- v2: `ocid1.devopsdeploypipeline.oc1.ap-seoul-1.amaaaaaavsea7yiahdikexdfwcr2ktbhiseiedtkavlhw2a25rr6rmdjjgeq`

## 발견·수정

첫 UI 배포의 Install 단계에서 브라우저 textarea가 전송한 CRLF가 셸 변수·경로에 포함돼 cp가 실패했다. `DeploymentSpecifications.step`에서 CRLF와 단독 CR을 LF로 바꾸고 실패 처리 명령에도 적용했다. 수정본을 화면에서 새로 생성했으며 네이티브 명세의 CR이 0개임을 재조회했다. 그 뒤 위 세 번의 배포가 성공했다. 실패 이력은 OCI에 남겨두었다.

초기 VM은 에이전트 준비·접속 조건이 맞지 않았고 신규 VM 생성 요청은 NotAuthorizedOrNotFound로 거절돼, 기존 데모 VM을 재사용했다. 시작 직후 Run Command 상태 갱신과 실행 수신에 지연이 있었으므로 RUNNING 또는 플러그인 설정만으로 즉시 준비 완료를 단정하지 않는다.

## 화면·자동 테스트

- Java 테스트 166개 통과. CRLF 회귀, 명세·범위·티켓·부분 생성 및 로그 HTML 이스케이프 포함.
- 이력·단계·VM별 작업 상태·OCI Logging 로그·이전 로그 페이지 탐색 구현.
- 실제 기존 배포 로그 37건 조회, 390/768/1440px 가로 넘침 없음, 브라우저 오류 없음.
- 실행 중 실제 자동 갱신 후에도 동일 실행 ID 유지 확인. 완료 시 자동 갱신 중지.
- 로그·이력 표는 기존 공통 `table-scroll--bounded` 클래스를 사용한다.

## 범위

VM 파일 배포 한 시나리오 검증이다. CPU·메모리·볼륨 크기 변경, 새 VM 프로비저닝 성공, 새 OKE/Container Instances 배포 구성, DB 데이터 복구 또는 자동 롤백 성공을 의미하지 않는다.
