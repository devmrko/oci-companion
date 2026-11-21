# SigNoz 평가 환경

필수: Docker 엔진(메모리 4GiB 이상), Docker Compose, 공식 Foundry 0.5.0.
운영 배포용이 아닙니다. 기존 설치와 별도 폴더에서 실행하세요.

1. https://github.com/SigNoz/foundry/releases/tag/v0.5.0 에서 OS·아키텍처에 맞는 foundryctl을 설치합니다.
2. `sh install.sh`로 사전 점검과 Compose 생성을 수행합니다.
3. 생성 파일을 검토하고 `docker compose -f pours/deployment/compose.yaml up -d`로 실행합니다.
4. `docker compose -f pours/deployment/compose.yaml ps`로 상태를 확인합니다.
5. http://127.0.0.1:18080 에서 로컬 계정을 등록합니다.

OTLP HTTP 주소: http://127.0.0.1:24318
분석 주소: http://127.0.0.1:18080
외부 서버의 앱은 loopback 주소로 전송할 수 없습니다. 운영 수집 주소·TLS·인증을 별도로 설정하세요.

Companion에서 제품 SigNoz와 주소를 저장한 후 계측 환경변수를 앱에 적용합니다.
앱 요청 발생 → SigNoz Services에서 서비스 선택 → Traces에서 요청 상세를 확인합니다.
설치 완료와 실제 수집 성공은 별개입니다.

종료: `docker compose -f pours/deployment/compose.yaml down`.
볼륨은 유지합니다. 데이터 삭제는 필요한 경우에만 별도 수행하세요.

공식 설치: https://signoz.io/docs/install/docker/
설치 도구가 생성하는 기본 DB 계정은 내부 평가용입니다. 운영에는 비밀정보·보존기간·백업·접근제어를 설정하세요.
