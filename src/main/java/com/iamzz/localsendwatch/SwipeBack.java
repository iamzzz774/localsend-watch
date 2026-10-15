package com.iamzz.localsendwatch;

/** Single-pointer right swipe policy. Coordinates are dp, timestamps monotonic ms. */
public final class SwipeBack {
 private float startX,startY;
 private long startTime;
 private boolean eligible,claimed;
 public void begin(float x,float y,long time,boolean enabled){reset();startX=x;startY=y;startTime=time;eligible=enabled;}
 public boolean move(float x,float y,long time){
  if(!eligible)return claimed;
  float dx=x-startX,dy=Math.abs(y-startY);
  if(time-startTime>1500||time<startTime){abort();return claimed;}
  if(!claimed){
   // Lock vertical/left/diagonal intent before taking the stream from buttons/ScrollView.
   if((dy>=12&&dy>Math.abs(dx))||dx<=-12||(dx>=24&&dx<1.5f*dy)){abort();return false;}
   if(dx>=24&&dx>=1.5f*dy)claimed=true;
  }
  return claimed;
 }
 public boolean finish(float x,float y,long time){
  move(x,y,time);
  float dx=x-startX,dy=Math.abs(y-startY);
  boolean back=eligible&&claimed&&dx>=56&&dx>=1.5f*dy;
  reset();return back;
 }
 public boolean claimed(){return claimed;}
 public void abort(){eligible=false;}
 public void reset(){eligible=false;claimed=false;}
}
