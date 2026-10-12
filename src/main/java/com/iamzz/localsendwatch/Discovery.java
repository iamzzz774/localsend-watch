package com.iamzz.localsendwatch;
import java.net.*;import java.io.*;import org.json.*;
public final class Discovery implements Closeable {
 private final ReceiverEngine engine;private final InetAddress group;private final NetworkInterface iface;private MulticastSocket socket;private volatile boolean running;private long lastReply;
 public Discovery(ReceiverEngine engine,NetworkInterface iface)throws Exception{this.engine=engine;this.iface=iface;group=InetAddress.getByName("224.0.0.167");}
 public void start()throws Exception{socket=new MulticastSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(53317));socket.setNetworkInterface(iface);socket.setTimeToLive(1);socket.setSoTimeout(1000);socket.joinGroup(new InetSocketAddress(group,53317),iface);running=true;
  new Thread(new Runnable(){public void run(){long last=0;byte[] buf=new byte[8192];while(running){try{if(System.currentTimeMillis()-last>10000){send(true);last=System.currentTimeMillis();}DatagramPacket packet=new DatagramPacket(buf,buf.length);socket.receive(packet);JSONObject o=new JSONObject(new String(packet.getData(),packet.getOffset(),packet.getLength(),"UTF-8"));if(o.optBoolean("announce",false)&&!o.optString("fingerprint").equals(engine.info(false).getString("fingerprint"))&&System.currentTimeMillis()-lastReply>500){send(false);lastReply=System.currentTimeMillis();}}catch(SocketTimeoutException ignored){}catch(Exception ignored){}}}},"localsend-discovery").start();}
 private void send(boolean announce)throws Exception{JSONObject o=engine.info(false);o.put("announce",announce);byte[] b=o.toString().getBytes("UTF-8");socket.send(new DatagramPacket(b,b.length,group,53317));}
 public void close(){running=false;if(socket!=null)socket.close();}
}
