# Grafana 로컬 수집 검증

공식 `grafana/otel-lgtm` 개발용 이미지. 운영 환경용 구성이 아닙니다.

```sh
docker compose up -d
docker compose ps
```

이미 실행한 동일 포트의 검증 컨테이너가 있으면 중복 실행하지 않습니다.

Companion 앱 상태 → 관측 도구 연결:

- 제품: Grafana
- 분석 화면: `http://127.0.0.1:13000`
- OTLP HTTP 수집: `http://127.0.0.1:14318`
- 서비스명: 배포 앱의 `OTEL_SERVICE_NAME`

앱이 다른 VM·Pod에서 실행되면 loopback 대신 앱에서 접근 가능한 수집기를 사용합니다. 관리 화면과 수집 주소는 서로 다른 네트워크 관점입니다.

원격 PC에서 검증 서버의 화면을 보려면 SSH 포트 전달 후 위 주소를 사용합니다. 개발용 기본 인증을 외부에 공개하지 않습니다.

```sh
ssh -N -L 13000:127.0.0.1:13000 user@server
```

Java는 공식 OpenTelemetry Java agent를 별도 설치하고 기존 JVM 옵션에 추가합니다. Companion의 환경변수 다운로드를 사용하며 인증은 대상 환경 Secret으로 주입합니다.

Grafana → Explore → Tempo에서 `{resource.service.name="java-diagnostics"}`를 조회하고 요청을 선택합니다. Prometheus에서는 `http_server_request_duration_seconds_count`와 JVM 메트릭을 조회합니다. 프로파일·덤프는 별도 계측이 필요합니다.

```sh
docker compose down
```

컨테이너 삭제 시 검증 데이터도 삭제됩니다. [원본 구성](https://github.com/grafana/docker-otel-lgtm)
