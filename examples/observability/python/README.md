# Python 수집 예제

Flask 개발 서버를 공식 OpenTelemetry 자동 계측으로 실행합니다. Companion 의존성은 없습니다.

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
OTEL_SERVICE_NAME=python-diagnostics \
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:14318 \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
.venv/bin/opentelemetry-instrument .venv/bin/python app.py
```

`http://127.0.0.1:8094/hello`를 호출하고 Grafana → Explore → Tempo에서 `{resource.service.name="python-diagnostics"}`를 조회합니다. 검색 반영에는 시간이 걸릴 수 있습니다. 운영 배포에서는 개발 서버 대신 해당 앱의 WSGI/ASGI 실행 구성을 사용합니다.
