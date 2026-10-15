import pathlib,subprocess,tempfile,ssl,http.client,json,hashlib,queue,threading,urllib.parse,os,time,socket
ROOT=pathlib.Path(__file__).resolve().parents[1]
# Host-only org.json matches Android's framework parser API; never shipped in APK.
jar=ROOT/'lib/json-host.jar'
if not jar.exists():
 import urllib.request
 jar.parent.mkdir(parents=True,exist_ok=True)
 jar.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30).read())
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed', 'Host dependency checksum mismatch'
BUILD=ROOT/'build/host';BUILD.mkdir(parents=True,exist_ok=True)
java=ROOT/'src/main/java/com/iamzz/localsendwatch'
sources=[java/(x+'.java') for x in ['SwipeBack','WatchUi','TransferCore','TlsIdentity','ReceiverEngine','Discovery']]+list((ROOT/'src/test/java').rglob('*.java'))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(ROOT/'lib/json-host.jar'),'-d',str(BUILD)]+[str(s) for s in sources],check=True)
subprocess.run(['java','-cp',str(BUILD)+':'+str(ROOT/'lib/json-host.jar'),'com.iamzz.localsendwatch.TransferCoreTest'],check=True)
subprocess.run(['java','-cp',str(BUILD)+':'+str(ROOT/'lib/json-host.jar'),'com.iamzz.localsendwatch.WatchUiTest'],check=True)
subprocess.run(['java','-cp',str(BUILD)+':'+str(ROOT/'lib/json-host.jar'),'com.iamzz.localsendwatch.SwipeBackTest'],check=True)
with tempfile.TemporaryDirectory(prefix='localsend-test-',dir=os.environ.get('TMPDIR',ROOT/'build')) as tmp:
 proc=subprocess.Popen(['java','-cp',str(BUILD)+':'+str(ROOT/'lib/json-host.jar'),'com.iamzz.localsendwatch.HostReceiver',tmp],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
 output=queue.Queue()
 def reader():
  for line in proc.stdout:output.put(line.strip())
 threading.Thread(target=reader,daemon=True).start()
 ready=None
 while ready is None:
  line=output.get(timeout=15)
  if line.startswith('READY '):ready=line.split()
 port=int(ready[1]);fingerprint=ready[2];context=ssl._create_unverified_context()
 checks=[]
 def check(value,label):
  assert value,label;checks.append(label)
 def req(path,body=b'',method='POST',chunked=False):
  c=http.client.HTTPSConnection('127.0.0.1',port,context=context,timeout=15)
  if isinstance(body,dict):body=json.dumps(body,ensure_ascii=False).encode()
  c.request(method,path,body=iter([body[:3],body[3:]]) if chunked else body,headers={'Content-Type':'application/json'},encode_chunked=chunked)
  r=c.getresponse();data=r.read();status=r.status;c.close();return status,json.loads(data) if data else None
 def meta(name,data=b'abc',alias='ACCEPT',sha=None,size=None):return {'info':{'alias':alias,'version':'2.0','fingerprint':'sender'},'files':{'f':{'id':'f','fileName':name,'size':len(data) if size is None else size,'fileType':'application/octet-stream','sha256':sha}}}
 def upload(result,data,token=None,chunked=False):return req('/api/localsend/v2/upload?'+urllib.parse.urlencode({'sessionId':result['sessionId'],'fileId':'f','token':result['files']['f'] if token is None else token}),data,chunked=chunked)[0]
 try:
  c=http.client.HTTPSConnection('127.0.0.1',port,context=context);c.connect();check(hashlib.sha256(c.sock.getpeercert(binary_form=True)).hexdigest()==fingerprint,'TLS certificate fingerprint matches');c.close()
  check(req('/api/localsend/v2/info',method='GET')[1]['protocol']=='https','HTTPS info')
  check(req('/api/localsend/v2/register',{'alias':'phone'})[0]==200,'Register')
  check(req('/api/localsend/v2/prepare-upload',meta('拒绝.txt',alias='REJECT'))[0]==403,'Explicit reject')
  for name in ['../escape','a/b','/absolute','a\\b','..','bad\x00name']:
   check(req('/api/localsend/v2/prepare-upload',meta(name))[0]==400,'Reject unsafe '+repr(name))
  # No fixed single-file/batch byte cap: disk availability remains authoritative.
  s,large_meta=req('/api/localsend/v2/prepare-upload',meta('large-metadata',size=513*1024*1024+17))
  check(s==200,'Metadata above both old 256 MiB and 512 MiB limits accepted')
  req('/api/localsend/v2/cancel?sessionId='+large_meta['sessionId'])
  check(req('/api/localsend/v2/prepare-upload',meta('disk-full',size=pathlib.Path(tmp).stat().st_size+2**63-1))[0]==400,'Reject unrepresentable integer size')
  check(req('/api/localsend/v2/prepare-upload',meta('disk-full',size=2**63-1))[0]==507,'Reject insufficient disk without overflow')
  overflow=meta('overflow-a',size=2**63-1)
  overflow['files']['g']={'id':'g','fileName':'overflow-b','size':1}
  check(req('/api/localsend/v2/prepare-upload',overflow)[0]==400,'Reject overflowing batch total')
  data=os.urandom(2*1024*1024+17);status,result=req('/api/localsend/v2/prepare-upload',meta('测试文件.apk',data,sha=hashlib.sha256(data).hexdigest()))
  check(status==200,'Approve metadata')
  check(req('/api/localsend/v2/prepare-upload',meta('busy'))[0]==409,'Reject concurrent session')
  check(upload(result,b'x',token='wrong')==403,'Reject invalid upload token')
  check(upload(result,b'x')==400,'Reject wrong content length')
  check(upload(result,data)==200,'Streaming binary upload')
  saved=pathlib.Path(tmp)/'received/测试文件.apk';check(saved.read_bytes()==data,'Received exact bytes/hash')
  s,r=req('/api/localsend/v2/prepare-upload',meta('测试文件.apk',b'next'));check(upload(r,b'next',chunked=True)==200,'Chunked upload')
  check(saved.read_bytes()==data and (saved.parent/'测试文件 (1).apk').read_bytes()==b'next','No overwrite on duplicate')
  s,r=req('/api/localsend/v2/prepare-upload',meta('zero.txt',b''));check(upload(r,b'')==200,'Zero-byte file')
  s,r=req('/api/localsend/v2/prepare-upload',meta('checksum',b'abc',sha='0'*64));check(upload(r,b'abc')==422,'Checksum mismatch')
  check(not (saved.parent/'checksum').exists(),'Bad hash never published')
  check(req('/api/localsend/v2/cancel?sessionId='+r['sessionId'])[0]==200,'Cancel authenticated session')
  check(upload(r,b'abc')==403,'Expired cancelled token rejected')
  resultq=queue.Queue()
  threading.Thread(target=lambda:resultq.put(req('/api/localsend/v2/prepare-upload',meta('pending',alias='WAIT'))),daemon=True).start()
  sid=None
  while sid is None:
   line=output.get(timeout=15)
   if line.startswith('PENDING ') and line.endswith(' WAIT'):sid=line.split()[1]
  check(resultq.empty(),'No upload tokens before human decision')
  proc.stdin.write('reject '+sid+'\n');proc.stdin.flush();check(resultq.get(timeout=15)[0]==403,'Manual confirmation rejected')
  # Partial socket closes: do not publish, delete temp, cancellation clears reservation.
  s,r=req('/api/localsend/v2/prepare-upload',meta('partial',b'abcdef'))
  raw=context.wrap_socket(socket.create_connection(('127.0.0.1',port)),server_hostname='localhost')
  path='/api/localsend/v2/upload?'+urllib.parse.urlencode({'sessionId':r['sessionId'],'fileId':'f','token':r['files']['f']})
  raw.sendall(('POST '+path+' HTTP/1.1\r\nHost: localhost\r\nContent-Length: 6\r\n\r\n').encode()+b'abc');raw.close()
  for _ in range(50):
   if not list(saved.parent.glob('.localsend-*.part')):break
   time.sleep(.02)
  check(not (saved.parent/'partial').exists(),'Interrupted file never published')
  req('/api/localsend/v2/cancel?sessionId='+r['sessionId']);check(not list(saved.parent.glob('.localsend-*.part')),'Partial files cleaned')
  # Real >512 MiB transfer with bounded RAM; verify saved size and streaming SHA-256.
  large_size=513*1024*1024+17
  block=b'LocalSend-large-file-regression' * 2048
  expected=hashlib.sha256()
  remaining=large_size
  while remaining:
   chunk=block[:min(len(block),remaining)];expected.update(chunk);remaining-=len(chunk)
  s,large=req('/api/localsend/v2/prepare-upload',meta('large-real.bin',size=large_size,sha=expected.hexdigest()))
  check(s==200,'Approve real >512 MiB file')
  connection=http.client.HTTPSConnection('127.0.0.1',port,context=context,timeout=120)
  path='/api/localsend/v2/upload?'+urllib.parse.urlencode({'sessionId':large['sessionId'],'fileId':'f','token':large['files']['f']})
  connection.putrequest('POST',path);connection.putheader('Content-Length',str(large_size));connection.endheaders()
  remaining=large_size
  while remaining:
   chunk=block[:min(len(block),remaining)];connection.send(chunk);remaining-=len(chunk)
  response=connection.getresponse();response.read();check(response.status==200,'Real >512 MiB HTTPS upload accepted');connection.close()
  large_saved=pathlib.Path(tmp)/'received/large-real.bin'
  actual=hashlib.sha256()
  with large_saved.open('rb') as stream:
   while True:
    chunk=stream.read(65536)
    if not chunk:break
    actual.update(chunk)
  check(large_saved.stat().st_size==large_size and actual.hexdigest()==expected.hexdigest(),'Large file size and SHA-256 verified from disk')
  print('Large-file verified bytes:',large_size)
  large_saved.unlink()
  # TLS identity persists and differs for each install.
  proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=10)
  p2=subprocess.Popen(['java','-cp',str(BUILD)+':'+str(ROOT/'lib/json-host.jar'),'com.iamzz.localsendwatch.HostReceiver',tmp],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  r2=p2.stdout.readline().split();check(r2[2]==fingerprint,'Persistent per-install identity');p2.communicate('stop\n',timeout=10)
  print('PASS',len(checks),'integration checks (same production HTTPS engine; simulated LocalSend sender)')
  for label in checks:print(' -',label)
 finally:
  if proc.poll() is None:
   proc.stdin.write('stop\n');proc.stdin.flush()
   try:proc.wait(timeout=10)
   except subprocess.TimeoutExpired:proc.kill()
  err=proc.stderr.read()
  if err:print(err)
