FROM docker.io/library/python:3.13-slim
WORKDIR /app
COPY <<'PY' /app/server.py
from http.server import BaseHTTPRequestHandler, HTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        good = self.path == '/readyz'
        self.send_response(200 if good else 404)
        self.end_headers()
        self.wfile.write(b'ready\n' if good else b'not found\n')
HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
PY
EXPOSE 8080
HEALTHCHECK CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/readyz')"
CMD ["python", "/app/server.py"]
