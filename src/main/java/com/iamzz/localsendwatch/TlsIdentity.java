package com.iamzz.localsendwatch;
import java.io.*;import java.security.*;import java.security.cert.*;import java.math.BigInteger;import java.text.SimpleDateFormat;import java.util.*;import javax.net.ssl.*;
public final class TlsIdentity {
 public final SSLContext context; public final String fingerprint;
 public TlsIdentity(File privateDir)throws Exception {
  privateDir.mkdirs(); File keyFile=new File(privateDir,"identity.pk8"),certFile=new File(privateDir,"identity.der");
  PrivateKey key; X509Certificate cert;
  if(keyFile.isFile()&&certFile.isFile()) {
   key=KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(read(keyFile)));
   cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(read(certFile)));
  } else {
   KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair pair=g.generateKeyPair();key=pair.getPrivate();
   byte[] algo=seq(hex("06092a864886f70d01010b"),hex("0500"));
   byte[] name=seq(tag(0x31,seq(hex("0603550403"),tag(0x0c,"LocalSend Watch".getBytes("UTF-8")))));
   long now=System.currentTimeMillis(); byte[] validity=seq(time(now-86400000L),time(now+315360000000L));
   byte[] tbs=seq(tag(0xa0,tag(2,new byte[]{2})),tag(2,new BigInteger(128,new SecureRandom()).add(BigInteger.ONE).toByteArray()),algo,name,validity,name,pair.getPublic().getEncoded());
   Signature s=Signature.getInstance("SHA256withRSA");s.initSign(key);s.update(tbs);byte[] signed=s.sign();
   byte[] bits=new byte[signed.length+1];System.arraycopy(signed,0,bits,1,signed.length);
   byte[] der=seq(tbs,algo,tag(3,bits));cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));cert.verify(pair.getPublic());
   save(keyFile,key.getEncoded()); save(certFile,der);
  }
  fingerprint=hexString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
  KeyStore ks=KeyStore.getInstance(KeyStore.getDefaultType());ks.load(null,null);ks.setKeyEntry("local",key,"local".toCharArray(),new java.security.cert.Certificate[]{cert});
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"local".toCharArray());
  context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),null,new SecureRandom());
 }
 static byte[] read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] a=new byte[4096];int n;while((n=in.read(a))!=-1)b.write(a,0,n);return b.toByteArray();}}
 static void save(File f,byte[] a)throws IOException{File tmp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(a);out.getFD().sync();}if(!tmp.renameTo(f))throw new IOException("identity persist failed");}
 static byte[] tag(int t,byte[] a)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(t);int n=a.length;if(n<128)b.write(n);else {int bytes=n>65535?3:n>255?2:1;b.write(128|bytes);for(int i=bytes-1;i>=0;i--)b.write(n>>(8*i));}b.write(a);return b.toByteArray();}
 static byte[] seq(byte[]...a)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] v:a)b.write(v);return tag(0x30,b.toByteArray());}
 static byte[] hex(String x){byte[] a=new byte[x.length()/2];for(int i=0;i<a.length;i++)a[i]=(byte)Integer.parseInt(x.substring(i*2,i*2+2),16);return a;}
 static byte[] time(long n)throws IOException{SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return tag(0x18,f.format(new Date(n)).getBytes("US-ASCII"));}
 public static String hexString(byte[] a){StringBuilder b=new StringBuilder();for(byte n:a)b.append(String.format(Locale.US,"%02x",n&255));return b.toString();}
}
