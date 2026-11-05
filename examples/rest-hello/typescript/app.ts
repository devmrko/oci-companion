import { createServer } from 'node:http';
createServer((req, res) => {
  const path = new URL(req.url ?? '/', 'http://localhost').pathname;
  const status = req.method !== 'GET' ? 405 : path !== '/hello' ? 404 : 200;
  const body = status === 200 ? { message: 'hello', language: 'typescript' } : { error: status === 404 ? 'not found' : 'method not allowed' };
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}).listen(8080, '0.0.0.0');
