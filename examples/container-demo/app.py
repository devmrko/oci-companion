from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import platform

def payload():
    return {"service": "oci-companion-container-demo", "architecture": platform.machine(), "status": "ok"}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(payload()).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
