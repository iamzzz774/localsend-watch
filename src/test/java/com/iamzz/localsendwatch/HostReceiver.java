package com.iamzz.localsendwatch;
import java.io.*;import java.net.*;import java.util.*;
public final class HostReceiver {
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);final ReceiverEngine[] holder=new ReceiverEngine[1];
  TlsIdentity identity=new TlsIdentity(new File(root,"tls"));
  ReceiverEngine receiver=new ReceiverEngine(new File(root,"received"),identity,"LocalSend Test Watch",new ReceiverEngine.Events(){public void pending(String id,String alias,String ip,List<String> names,long total){System.out.println("PENDING "+id+" "+alias);System.out.flush();if(!alias.equals("WAIT"))holder[0].decide(id,!alias.equals("REJECT"));}public void update(String s){System.out.println("STATE "+s.replace('\n',' '));System.out.flush();}});
  holder[0]=receiver;receiver.start(InetAddress.getByName("127.0.0.1"),0);System.out.println("READY "+receiver.port()+" "+identity.fingerprint);System.out.flush();
  BufferedReader in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null){if(line.equals("stop"))break;if(line.equals("cancel"))receiver.cancelCurrent();if(line.startsWith("accept "))receiver.decide(line.substring(7),true);if(line.startsWith("reject "))receiver.decide(line.substring(7),false);}receiver.close();
 }
}
