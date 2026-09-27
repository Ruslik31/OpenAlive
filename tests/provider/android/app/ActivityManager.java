package android.app;
import android.os.*;import android.content.*;
public class ActivityManager {
 public static final Fake SERVICE=new Fake(); public static IActivityManager getService(){return SERVICE;}
 public static class Holder { public IContentProvider provider; Holder(IContentProvider p){provider=p;} }
 public static class Fake implements IActivityManager {
 public int gets,releases,calls,nulls;public boolean empty,denied,callFailure;
 public void reset(){gets=releases=calls=nulls=0;empty=denied=callFailure=false;}
 public Object getContentProviderExternal(String a,int u,IBinder b,String tag){gets++;if(denied)throw new SecurityException("denied");if(gets<=nulls)return null;
 return new Holder(empty?null:(s,x,m,arg,extra)->{calls++;if(callFailure)throw new IllegalStateException("setter failed");return new Bundle();});}
 public void removeContentProviderExternalAsUser(String a,IBinder b,int u){releases++;}
 }
}
