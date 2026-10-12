package com.iamzz.localsendwatch;
public final class TransferCoreTest {public static void main(String[] args){
String[] bad={"../x","a/b","a\\b",".","..","/x","a:b","bad\u0000name","bad\nname",".localsend-test"};
for(String s:bad)if(ReceiverEngine.safeName(s))throw new AssertionError(s);
if(!ReceiverEngine.safeName("中文 APK.apk")||!ReceiverEngine.safeName("sample.txt"))throw new AssertionError();
if(!TransferCore.constant("abc","abc")||TransferCore.constant("abc","abd")||TransferCore.constant(null,"a"))throw new AssertionError();
if(!ReceiverEngine.duplicateName("hello.apk",2).equals("hello (2).apk"))throw new AssertionError();
System.out.println("PASS 16 core assertions");}}
