package org.aliveclean;
import android.app.ActivityManager;
public class PlatformProviderTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args)throws Exception {
  ActivityManager.Fake f=ActivityManager.SERVICE;
  f.reset();try(PlatformProvider p=new PlatformProvider("test",0)){p.call("set",null,null);}check(f.gets==1&&f.calls==1&&f.releases==1);
  f.reset();f.nulls=2;try(PlatformProvider p=new PlatformProvider("test",0)){p.call("set",null,null);}check(f.gets==3&&f.calls==1&&f.releases==1);
  f.reset();f.nulls=9;try{new PlatformProvider("test",0);throw new AssertionError();}catch(IllegalStateException expected){}check(f.gets==4&&f.releases==0);
  f.reset();f.empty=true;try{new PlatformProvider("test",0);throw new AssertionError();}catch(IllegalStateException expected){}check(f.gets==4&&f.releases==4);
  f.reset();f.denied=true;try{new PlatformProvider("test",0);throw new AssertionError();}catch(SecurityException expected){}check(f.gets==1&&f.releases==0);
  f.reset();f.callFailure=true;try(PlatformProvider p=new PlatformProvider("test",0)){p.call("set",null,null);throw new AssertionError();}catch(IllegalStateException expected){}check(f.gets==1&&f.calls==1&&f.releases==1);
  f.reset();PlatformProvider p=new PlatformProvider("test",0);p.close();p.close();check(f.releases==1);try{p.call("set",null,null);throw new AssertionError();}catch(IllegalStateException expected){}check(f.calls==0);
  f.reset();f.nulls=9;Thread.currentThread().interrupt();try{new PlatformProvider("test",0);throw new AssertionError();}catch(InterruptedException expected){}check(f.gets==1&&f.releases==0);
  System.out.println("PROVIDER_RECONNECT_OK: transient absence, exhausted retry, empty holder cleanup, permission denial, no setter replay, close, interruption");
 }
}
