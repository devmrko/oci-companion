# Spring Boot 진단 예제

Companion 없이 실행되는 HTTP `/hello`와 표준 Actuator 예제다.
Java 21 이상과 Maven이 필요하다. 관리 API는 loopback에만 바인딩한다.

1. `DIAGNOSTICS_TOKEN` 환경변수에 32자 이상의 임의 값을 설정한다. 값을 커밋하지 않는다.
2. `mvn package` 후 `java -jar target/runtime-diagnostics-1.0.0.jar`를 실행한다.
3. `http://127.0.0.1:8093/hello`를 호출한다.
4. `/actuator/metrics`, `/actuator/threaddump`는 Bearer 인증으로 조회한다.

Companion 관리자가 동일 토큰의 파일 참조를 프로파일·리전·구획별 인증 목록에 등록한다.
화면의 등록 앱 상태 → 앱 연결 등록에서 `Java · Spring Boot Actuator`,
`http://127.0.0.1:8093`, 해당 인증 참조를 선택한다. 메트릭·진단에서 실제 값을 조회한다.

포트 변경: `--server.port=<port>`. 원격 앱은 인증된 SSH/Kubernetes 포트 전달로 연결한다.
외부 공개용 인증 서버 구현이 아닌 내부 검증 예제다. 운영은 기존 앱 인증·관리망 정책을 사용한다.
힙 덤프 엔드포인트는 노출하지 않는다.

인증 참조 형식 (`.tools/agent-credentials.json` 배열에 추가, 기존 항목 유지):

```json
{
  "id": "java-diagnostics",
  "label": "Java 관리 API",
  "tokenFile": "/absolute/private/path/token",
  "profile": "DEFAULT",
  "tenancy": "<현재 테넌시 OCID>",
  "user": "<현재 사용자 OCID>",
  "region": "<리전>",
  "compartment": "<구획 OCID>"
}
```

토큰 파일은 소유자만 읽도록 설정한다. 파일 내용은 `Bearer ` 접두사 없는 토큰이다.
`companion.agent-credentials` 설정으로 인증 목록 파일 경로를 바꿀 수 있다.
