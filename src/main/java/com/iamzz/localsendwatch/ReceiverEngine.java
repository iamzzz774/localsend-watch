package com.iamzz.localsendwatch;
import java.io.*;import java.net.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import javax.net.ssl.*;import org.json.*;
/** The identical bounded TLS receiver runs on Android and in the JVM integration suite. */
public final class ReceiverEngine implements Closeable {
 public interface Events {void pending(String id,String alias,String ip,List<String> names,long total);void update(String status);}
 private final Events events; private final File folder; private final TlsIdentity identity;private volatile String alias;
 private SSLServerSocket server;private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,6,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<Socket>());
 private final SecureRandom random=new SecureRandom();private volatile boolean running; private Session session; private int port;
 static final long SPACE_RESERVE=16L*1024*1024;
 static final class Entry {String id,name,hash,token;long size;boolean writing,done;File tmp;Socket socket;}
 static final class Session {String id,ip;long expiry;List<Entry> entries;int decision;boolean cancelled;}
 static final class Error extends Exception {final int code; Error(int c){code=c;}}
 public ReceiverEngine(File folder,TlsIdentity identity,String alias,Events events){this.folder=folder;this.identity=identity;this.alias=alias;this.events=events;folder.mkdirs();File[] stale=folder.listFiles();if(stale!=null)for(File f:stale)if(f.isFile()&&f.getName().startsWith(".localsend-")&&f.getName().endsWith(".part"))f.delete();}
 public synchronized void start(InetAddress bind,int desiredPort)throws IOException{
  if(running)return;server=(SSLServerSocket)identity.context.getServerSocketFactory().createServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(bind,desiredPort));port=server.getLocalPort();server.setEnabledProtocols(new String[]{"TLSv1.2"});running=true;
  new Thread(new Runnable(){public void run(){while(running)try{final Socket socket=server.accept();socket.setSoTimeout(30000);sockets.add(socket);try{workers.execute(new Runnable(){public void run(){handle(socket);}});}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}}catch(IOException e){if(running)events.update("监听错误："+e.getMessage());}}},"localsend-tls").start();
 }
 public int port(){return port;}
 public void setAlias(String value){alias=WatchUi.name(value);}
 public JSONObject info(boolean announce)throws JSONException {JSONObject o=new JSONObject();o.put("alias",alias).put("version","2.0").put("deviceModel","Android Watch").put("deviceType","mobile").put("fingerprint",identity.fingerprint).put("port",port).put("protocol","https").put("download",false);if(announce)o.put("announce",true);return o;}
 public synchronized void decide(String id,boolean accept){if(session!=null&&session.id.equals(id)&&session.decision==0){session.decision=accept?1:-1;notifyAll();}}
 public synchronized void cancelCurrent(){cancelLocked();}
 private void cancelLocked(){if(session!=null){session.cancelled=true;session.decision=-1;for(Entry e:session.entries){if(e.socket!=null)try{e.socket.close();}catch(IOException ignored){}if(e.tmp!=null)e.tmp.delete();}session=null;notifyAll();}}
 private String token(){byte[] b=new byte[24];random.nextBytes(b);return TlsIdentity.hexString(b);}
 private void handle(Socket socket){try {
  BufferedInputStream in=new BufferedInputStream(socket.getInputStream(),65536);OutputStream out=socket.getOutputStream();
  String first=line(in,4096);String[] bits=first.split(" ");if(bits.length!=3)throw new Error(400);String verb=bits[0];URI uri=new URI(bits[1]);if(uri.isAbsolute())throw new Error(400);
  Map<String,String> headers=new HashMap<String,String>();int used=0;String l;while(!(l=line(in,4096)).isEmpty()){used+=l.length();if(used>16384)throw new Error(400);int colon=l.indexOf(':');if(colon<1)throw new Error(400);String k=l.substring(0,colon).toLowerCase(Locale.US);if(headers.put(k,l.substring(colon+1).trim())!=null)throw new Error(400);}
  boolean chunked="chunked".equalsIgnoreCase(headers.get("transfer-encoding"));if(headers.containsKey("transfer-encoding")&&!chunked)throw new Error(400);if(chunked&&headers.containsKey("content-length"))throw new Error(400);
  long length=headers.containsKey("content-length")?Long.parseLong(headers.get("content-length")):0;if(length<0)throw new Error(400);
  InputStream body=chunked?new Chunks(in):new Limited(in,length);
  String path=uri.getPath();Map<String,String> query=query(uri.getRawQuery());String ip=socket.getInetAddress().getHostAddress();
  if(path.equals("/api/localsend/v2/info")&&verb.equals("GET")){respond(out,200,info(false));return;}
  if(path.equals("/api/localsend/v2/register")&&verb.equals("POST")){metadata(body);respond(out,200,info(false));return;}
  if(!verb.equals("POST"))throw new Error(404);
  if(path.equals("/api/localsend/v2/prepare-upload")){JSONObject req=new JSONObject(new String(metadata(body),"UTF-8"));respond(out,200,prepare(req,ip));return;}
  if(path.equals("/api/localsend/v2/upload")){upload(query,ip,body,length,chunked,socket);respond(out,200,null);return;}
  if(path.equals("/api/localsend/v2/cancel")){synchronized(this){if(session==null||!session.id.equals(query.get("sessionId"))||!session.ip.equals(ip))throw new Error(403);cancelLocked();}events.update("传输已取消");respond(out,200,null);return;}
  throw new Error(404);
 }catch(Error e){try{respond(socket.getOutputStream(),e.code,null);}catch(Exception ignored){}}
 catch(Exception e){try{respond(socket.getOutputStream(),400,null);}catch(Exception ignored){} }
 finally{sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}}
 private JSONObject prepare(JSONObject request,String ip)throws Exception{
  JSONObject files=request.getJSONObject("files");if(files.length()<1||files.length()>64)throw new Error(400);
  List<Entry> entries=new ArrayList<Entry>();List<String> names=new ArrayList<String>();Set<String> unique=new HashSet<String>();long total=0;
  for(Iterator<String> it=files.keys();it.hasNext();){String id=it.next();JSONObject f=files.getJSONObject(id);Entry e=new Entry();e.id=id;e.name=f.getString("fileName");Object size=f.get("size");if(!(size instanceof Number)||size instanceof Float||size instanceof Double)throw new Error(400);try{e.size=Long.parseLong(size.toString());}catch(NumberFormatException invalidSize){throw new Error(400);}if(!id.equals(f.getString("id"))||id.length()>256||!safeName(e.name)||!unique.add(e.name)||e.size<0)throw new Error(400);e.hash=f.isNull("sha256")?null:f.optString("sha256",null);if(e.hash!=null&&!e.hash.matches("(?i)[0-9a-f]{64}"))throw new Error(400);e.token=token();if(e.size>Long.MAX_VALUE-total)throw new Error(400);total+=e.size;entries.add(e);names.add(e.name);}
  long available=folder.getUsableSpace();if(available<SPACE_RESERVE||total>available-SPACE_RESERVE)throw new Error(507);
  Session s=new Session();s.id=token();s.ip=ip;s.entries=entries;s.expiry=System.currentTimeMillis()+120000;
  synchronized(this){if(session!=null&&session.expiry<System.currentTimeMillis())cancelLocked();if(session!=null)throw new Error(409);session=s;}
  try{events.pending(s.id,request.getJSONObject("info").optString("alias","手机"),ip,names,total);}catch(Exception e){synchronized(this){if(session==s)cancelLocked();}throw new Error(400);}
  synchronized(this){while(running&&session==s&&s.decision==0&&System.currentTimeMillis()<s.expiry)wait(Math.min(1000,Math.max(1,s.expiry-System.currentTimeMillis())));if(!running||session!=s||s.cancelled||s.decision!=1){if(session==s)cancelLocked();events.update("接收请求已拒绝或过期");throw new Error(403);}s.expiry=System.currentTimeMillis()+600000;}
  JSONObject result=new JSONObject();JSONObject tokens=new JSONObject();for(Entry e:entries)tokens.put(e.id,e.token);result.put("sessionId",s.id).put("files",tokens);events.update("已接受，等待文件");return result;
 }
 static boolean safeName(String n){return n!=null&&!n.isEmpty()&&n.length()<=200&&!n.equals(".")&&!n.equals("..")&&!n.contains("/")&&!n.contains("\\")&&!n.contains(":")&&!n.startsWith(".localsend-")&&!n.matches(".*[\\x00-\\x1f\\x7f].*");}
 private void upload(Map<String,String> q,String ip,InputStream body,long length,boolean chunked,Socket socket)throws Exception{
  final Session s;final Entry e;
  synchronized(this){s=session;if(s==null||s.cancelled||s.expiry<System.currentTimeMillis()||!s.id.equals(q.get("sessionId"))||!s.ip.equals(ip)||s.decision!=1)throw new Error(403);Entry found=null;for(Entry item:s.entries)if(item.id.equals(q.get("fileId")))found=item;e=found;if(e==null||!TransferCore.constant(e.token,q.get("token")))throw new Error(403);if(e.writing||e.done)throw new Error(409);if(!chunked&&length!=e.size)throw new Error(400);e.writing=true;e.socket=socket;e.tmp=new File(folder,".localsend-"+token()+".part");}
  boolean completed=false;String savedName=e.name;try{
   MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0,last=0;byte[] buffer=new byte[65536];
   try(FileOutputStream out=new FileOutputStream(e.tmp)){int n;while((n=body.read(buffer))!=-1){synchronized(this){if(s.cancelled||session!=s)throw new Error(409);s.expiry=System.currentTimeMillis()+600000;}if(n>e.size-count)throw new Error(400);count+=n;out.write(buffer,0,n);digest.update(buffer,0,n);if(System.currentTimeMillis()-last>200){events.update(e.name+"\n"+count+" / "+e.size+" 字节");last=System.currentTimeMillis();}}if(count!=e.size)throw new Error(400);out.getFD().sync();}
   if(e.hash!=null&&!e.hash.equalsIgnoreCase(TlsIdentity.hexString(digest.digest())))throw new Error(422);
   synchronized(this){if(s.cancelled||session!=s)throw new Error(409);File dst=new File(folder,e.name);int i=1;while(dst.exists())dst=new File(folder,duplicateName(e.name,i++));if(!dst.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))throw new Error(400);if(!e.tmp.renameTo(dst))throw new Error(500);savedName=dst.getName();e.done=true;completed=true;boolean all=true;for(Entry item:s.entries)all&=item.done;if(all)session=null;}
   events.update("已保存："+savedName);
  }finally{synchronized(this){e.writing=false;e.socket=null;if(!completed&&e.tmp!=null)e.tmp.delete();}}
 }
 static String duplicateName(String name,int i){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot)+" ("+i+")"+name.substring(dot):name+" ("+i+")";}
 static byte[] metadata(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] v=new byte[4096];int n;while((n=in.read(v))!=-1){if(b.size()+n>131072)throw new Error(400);b.write(v,0,n);}return b.toByteArray();}
 static String line(InputStream in,int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c==10){byte[] a=b.toByteArray();if(a.length==0||a[a.length-1]!=13)throw new IOException("invalid CRLF");return new String(a,0,a.length-1,"US-ASCII");}if(b.size()>=max)throw new IOException("line too long");b.write(c);}throw new EOFException();}
 static Map<String,String> query(String q)throws Exception{Map<String,String> m=new HashMap<String,String>();if(q!=null)for(String pair:q.split("&")){String[] a=pair.split("=",2);if(a.length!=2)throw new Error(400);String k=URLDecoder.decode(a[0],"UTF-8"),v=URLDecoder.decode(a[1],"UTF-8");if(m.put(k,v)!=null)throw new Error(400);}return m;}
 static void respond(OutputStream out,int status,JSONObject obj)throws Exception{byte[] bytes=obj==null?new byte[0]:obj.toString().getBytes("UTF-8");out.write(("HTTP/1.1 "+status+" Response\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes("US-ASCII"));out.write(bytes);out.flush();}
 static final class Limited extends InputStream{final InputStream in;long remaining;Limited(InputStream in,long n){this.in=in;remaining=n;}public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int off,int n)throws IOException{if(remaining==0)return -1;int got=in.read(b,off,(int)Math.min(n,remaining));if(got<0)throw new EOFException();remaining-=got;return got;}}
 static final class Chunks extends InputStream{final InputStream in;long remaining;boolean end,needCRLF;Chunks(InputStream i){in=i;}public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int off,int n)throws IOException{if(end)return -1;if(remaining==0){if(needCRLF&&!line(in,2).isEmpty())throw new IOException("chunk CRLF");String s=line(in,128).split(";",2)[0];try{remaining=Long.parseLong(s,16);}catch(Exception e){throw new IOException("chunk size");}if(remaining<0)throw new IOException("chunk bound");if(remaining==0){int total=0;String trailer;while(!(trailer=line(in,4096)).isEmpty()){total+=trailer.length();if(total>8192)throw new IOException("trailers");}end=true;return -1;}needCRLF=true;}int got=in.read(b,off,(int)Math.min(n,remaining));if(got<0)throw new EOFException();remaining-=got;return got;}}
 public void close(){running=false;synchronized(this){cancelLocked();}try{if(server!=null)server.close();}catch(IOException ignored){}synchronized(sockets){for(Socket s:sockets)try{s.close();}catch(IOException ignored){}sockets.clear();}workers.shutdownNow();}
}
