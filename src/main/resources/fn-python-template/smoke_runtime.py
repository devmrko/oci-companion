import datetime,http.client,json,socket,time,uuid,sys
class UnixHTTP(http.client.HTTPConnection):
    def connect(self):
        self.sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
        self.sock.settimeout(5)
        self.sock.connect('/tmp/fdk-smoke.sock')
last=None
for attempt in range(30):
    try:
        connection=UnixHTTP('localhost',timeout=5)
        deadline=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=60)).isoformat().replace('+00:00','Z')
        connection.request('POST','/',json.dumps({'action':'probe'}),{'Content-Type':'application/json','Fn-Call-Id':str(uuid.uuid4()),'Fn-Deadline':deadline})
        response=connection.getresponse();body=response.read()
        assert response.status==200,(response.status,body)
        result=json.loads(body);assert result['contract']=='python-v1',result
        if len(sys.argv)>1:assert result['arch']==sys.argv[1],result
        print('FDK default entrypoint probe passed',json.dumps(result));break
    except (OSError,http.client.HTTPException) as error:
        last=error;time.sleep(1)
else:raise RuntimeError('FDK startup failed: '+str(last))
