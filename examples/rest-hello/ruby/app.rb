require 'webrick'
require 'json'
class Hello < WEBrick::HTTPServlet::AbstractServlet
  def service(req, res)
    res['Content-Type'] = 'application/json'
    res.status = req.request_method != 'GET' ? 405 : req.path != '/hello' ? 404 : 200
    res.body = JSON.generate(res.status == 200 ? {message: 'hello', language: 'ruby'} : {error: res.status == 404 ? 'not found' : 'method not allowed'})
  end
end
server = WEBrick::HTTPServer.new(Port: 8080, BindAddress: '0.0.0.0', AccessLog: [])
server.mount '/', Hello
trap('INT') { server.shutdown }
trap('TERM') { server.shutdown }
server.start
