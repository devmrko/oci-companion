"""Dependency-free HTTP fixture for native OCI deployment validation."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import json

class Hello(BaseHTTPRequestHandler):
    def do_GET(self):
        ok = self.path == '/hello'
        payload = {'message': 'hello', 'version': Path(__file__).with_name('version.txt').read_text().strip()} if ok else {'error': 'not found'}
        self.send_response(200 if ok else 404)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(payload).encode())

if __name__ == '__main__':
    HTTPServer(('127.0.0.1', 8080), Hello).serve_forever()
