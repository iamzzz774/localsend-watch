package com.iamzz.localsendwatch;
import android.app.*;import android.content.*;import android.os.*;import android.net.*;import android.net.wifi.*;import java.net.*;import java.io.*;import java.util.*;
public final class ReceiverService extends Service {
 static volatile String addressText="未连接";static volatile ReceiverService active;static volatile String status="未启动接收",pendingId,pendingText;ReceiverEngine engine;Discovery discovery;PowerManager.WakeLock wake;WifiManager.MulticastLock multicast;volatile boolean destroyed;
 public IBinder onBind(Intent i){return null;}
 public static boolean running(Context c){return active!=null;}
 static File received(Context c){File base=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(base==null)base=new File(c.getFilesDir(),"Downloads");File dir=new File(base,"LocalSend");dir.mkdirs();return dir;}
 public void onCreate(){super.onCreate();active=this;status="正在启动…";
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("recv","LocalSend 接收",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(7,new Notification.Builder(this,"recv").setContentTitle("LocalSend 手表接收中").setContentText("点按查看传输；应用内可停止接收").setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(pi).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
  new Thread(new Runnable(){public void run(){try{startReceiver();}catch(Exception e){status="启动失败："+e.getMessage();stopSelf();}}},"localsend-start").start();
 }
 private synchronized void startReceiver()throws Exception{
  if(destroyed)return;
  ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);InetAddress address=null;
  for(Network network:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(network);LinkProperties lp=cm.getLinkProperties(network);if(cap==null||lp==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;for(LinkAddress link:lp.getLinkAddresses()){InetAddress a=link.getAddress();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress())address=a;}if(address!=null)break;}
  if(address==null)throw new IOException("请先连接 Wi-Fi（与手机同一局域网）");
  TlsIdentity identity=new TlsIdentity(new File(getFilesDir(),"tls"));
  engine=new ReceiverEngine(received(this),identity,getSharedPreferences("settings",0).getString("name","LocalSend 手表"),new ReceiverEngine.Events(){public void pending(String id,String alias,String ip,List<String> names,long total){pendingText="来自："+alias+"\n"+ip+"\n"+names.size()+" 个文件 · "+total+" 字节\n"+join(names);pendingId=id;status="等待接收确认（两分钟内）";}public void update(String value){pendingId=null;pendingText=null;status=value;}});
  engine.start(address,53317);
  PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LocalSendWatch:receive");wake.setReferenceCounted(false);wake.acquire();
  WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wm!=null){multicast=wm.createMulticastLock("localsend-watch");multicast.setReferenceCounted(false);multicast.acquire();}
  String ip=address.getHostAddress();addressText=ip+":53317";status="接收中\n"+ip+":53317\n手机设备列表选择“LocalSend 手表”";
  try{discovery=new Discovery(engine,NetworkInterface.getByInetAddress(address));discovery.start();}catch(Exception e){status+="\n自动发现失败，请用手机手动输入此 IP";}
 }
 static String join(List<String> names){StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(names.size(),6);i++)b.append(names.get(i)).append('\n');if(names.size()>6)b.append("…");return b.toString();}
 public static void decide(boolean accept){ReceiverService s=active;String id=pendingId;if(s!=null&&s.engine!=null&&id!=null)s.engine.decide(id,accept);pendingId=null;pendingText=null;}
 public static void cancel(){ReceiverService s=active;if(s!=null&&s.engine!=null)s.engine.cancelCurrent();pendingId=null;pendingText=null;status="传输已取消，仍在接收";}
 public int onStartCommand(Intent i,int f,int id){return START_NOT_STICKY;}
 public synchronized void onDestroy(){destroyed=true;if(discovery!=null)discovery.close();if(engine!=null)engine.close();if(wake!=null&&wake.isHeld())wake.release();if(multicast!=null&&multicast.isHeld())multicast.release();if(active==this)active=null;pendingId=null;pendingText=null;stopForeground(true);super.onDestroy();}
}
