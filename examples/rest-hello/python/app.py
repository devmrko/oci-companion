from http.server import BaseHTTPRequestHandler, HTTPServer
import json
class Handler(BaseHTTPRequestHandler):
    def respond(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        if self.path.split('?')[0] == '/hello':
            self.respond(200, {'message': 'hello', 'language': 'python'})
        else:
            self.respond(404, {'error': 'not found'})
    def do_POST(self):
        self.respond(405, {'error': 'method not allowed'})
if __name__ == '__main__':
    HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
