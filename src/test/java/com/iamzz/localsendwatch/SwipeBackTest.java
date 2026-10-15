package com.iamzz.localsendwatch;

public final class SwipeBackTest {
 private static int checks;
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  SwipeBack s=new SwipeBack();
  s.begin(40,80,0,true);check(s.move(70,82,100),"claim horizontal drag");check(s.finish(110,84,250),"right swipe returns");check(!s.finish(110,84,260),"once only");
  s.begin(40,80,0,true);check(!s.move(42,85,40),"tap jitter stays with child");check(!s.finish(42,85,80),"tap not back");
  s.begin(40,80,0,true);check(!s.move(42,105,70),"vertical scroll not intercepted");check(!s.finish(140,106,200),"vertical-first cannot turn into back");
  s.begin(120,80,0,true);check(!s.move(85,80,70),"left swipe not intercepted");check(!s.finish(50,80,200),"left swipe not back");
  s.begin(40,80,0,true);check(s.move(70,80,100),"short swipe is consumed");check(!s.finish(95,80,200),"distance below 56dp not back");
  s.begin(40,80,0,true);check(s.finish(96,80,200),"56dp threshold inclusive, no MOVE");
  s.begin(40,80,0,true);check(!s.finish(96,80,1501),"slow drag not back");
  s.begin(40,80,0,true);s.move(80,80,100);s.abort();check(s.claimed(),"cancelled claimed stream stays consumed");check(!s.finish(130,80,200),"multitouch/cancel not back");
  s.begin(40,80,0,false);check(!s.finish(140,80,200),"disabled gesture preserves text editing");
  s.begin(40,80,0,true);check(!s.move(70,104,70),"diagonal gesture not intercepted");check(!s.finish(150,105,200),"diagonal lock retained");
  s.begin(40,80,0,true);s.move(80,80,100);check(!s.finish(45,80,200),"reversed swipe not back");
  s.begin(40,80,0,true);s.move(80,80,100);s.reset();check(!s.claimed(),"page change/lifecycle resets ownership");check(!s.finish(150,80,200),"reset cannot trigger");
  s.begin(40,80,0,true);check(!s.finish(100,150,200),"vertical end deviation rejected");
  System.out.println("PASS "+checks+" swipe-back assertions");
 }
}
