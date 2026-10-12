package com.iamzz.localsendwatch;
import java.net.*;import java.io.*;import java.util.*;import org.json.*;
public final class DiscoveryTest {
 public static void main(String[] args)throws Exception{
  InetAddress address=InetAddress.getByName(args[0]);NetworkInterface iface=NetworkInterface.getByInetAddress(address);File root=new File(args[1]);
  ReceiverEngine engine=new ReceiverEngine(new File(root,"received"),new TlsIdentity(new File(root,"tls")),"LocalSend discovery test",new ReceiverEngine.Events(){public void pending(String id,String alias,String ip,List<String> n,long size){throw new IllegalStateException("Never accept real file requests in discovery test");}public void update(String s){}});
  engine.start(address,0);Discovery discovery=new Discovery(engine,iface);MulticastSocket client=new MulticastSocket(null);client.setReuseAddress(true);client.bind(new InetSocketAddress(53317));client.setNetworkInterface(iface);client.setTimeToLive(1);client.setSoTimeout(5000);InetAddress group=InetAddress.getByName("224.0.0.167");client.joinGroup(new InetSocketAddress(group,53317),iface);
  try {discovery.start();byte[] buf=new byte[8192];boolean announce=false,reply=false;long until=System.currentTimeMillis()+7000;
   while(System.currentTimeMillis()<until&&(!announce||!reply)){
    JSONObject request=new JSONObject().put("announce",true).put("fingerprint","integration-peer");byte[] req=request.toString().getBytes("UTF-8");client.send(new DatagramPacket(req,req.length,group,53317));
    DatagramPacket packet=new DatagramPacket(buf,buf.length);client.receive(packet);JSONObject data=new JSONObject(new String(packet.getData(),packet.getOffset(),packet.getLength(),"UTF-8"));
    if(!data.optString("fingerprint").equals(engine.info(false).getString("fingerprint")))continue;
    if(!"https".equals(data.getString("protocol"))||data.getInt("port")!=engine.port())throw new AssertionError("invalid production discovery");
    if(data.getBoolean("announce"))announce=true;else reply=true;
   }
   if(!announce||!reply)throw new AssertionError("missing announce/reply");System.out.println("PASS production UDP multicast announce + discovery response");
  } finally {client.close();discovery.close();engine.close();}
 }
}
