package com.iamzz.localsendwatch;
public final class WatchUi {
 public static int[] bounds(int w,int h){int d=Math.min(w,h);int width=(int)(d*.72),height=(int)(d*.68);return new int[]{(w-width)/2,(h-height)/2,width,height};}
 public static int[] homeBounds(int w,int h){int d=Math.min(w,h),width=(int)(d*.60),height=(int)(d*.80);return new int[]{(w-width)/2,(h-height)/2,width,height};}
 public static String name(String s){for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))throw new IllegalArgumentException("名称不能含控制字符");s=s.trim();if(s.length()==0||s.codePointCount(0,s.length())>32)throw new IllegalArgumentException("名称须为 1–32 个字符");for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))throw new IllegalArgumentException("名称不能含控制字符");return s;}
 public static String state(String id,String status){if(id!=null)return "pending";if(status.startsWith("已保存："))return "result";if(status.contains(" / ")||status.startsWith("已接受"))return "transfer";return "home";}
}
