package org.aliveclean.xiaomifidelity;

import android.app.*;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Runs against the signed release APK. Does not apply a wallpaper or change settings. */
public final class XiaomiFidelityTest extends Instrumentation {
    private Context app;private final StringBuilder log=new StringBuilder();private int checks;
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;log.append("PASS ").append(name).append('\n');}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(target instanceof Class?null:target,args);}catch(InvocationTargetException e){throw new Exception(e.getCause());}
    }
    private static void call(Object target,String name)throws Exception{invoke(target,name,new Class[0]);}
    private static ValueAnimator animator(Object host,String name)throws Exception{return (ValueAnimator)field(host,name);}
    private static void show(Object host,ViewGroup root,View time,View date)throws Exception{invoke(host,"show",new Class[]{ViewGroup.class,View.class,View.class},root,time,date);}
    private static void alpha(Object host,float value)throws Exception{invoke(host,"contentAlpha",new Class[]{float.class},value);}
    private static void enter(Object host,boolean animate)throws Exception{invoke(host,"enterXiaomi",new Class[]{boolean.class},animate);}
    private void near(float actual,float expected,String name){check(Math.abs(actual-expected)<.002f,name+" "+actual);}
    private void transitions(Object host,Bundle metadata,ViewGroup root,View time,View date)throws Exception{
        invoke(host,"configure",new Class[]{Bundle.class},metadata);show(host,root,time,date);
        View face=(View)field(host,"clock");enter(host,true);alpha(host,1);
        ValueAnimator incoming=animator(host,"enterFade");
        check(incoming!=null&&incoming.getDuration()==1000,"original AOD entry duration");
        check(animator(host,"stockFade")==null,"no added lock clock animation on sleep");
        near(face.getAlpha(),0,"entry starts transparent");near(time.getTransitionAlpha(),0,"stock clock keeps existing AOD suppression");
        incoming.setCurrentPlayTime(500);
        near(face.getAlpha(),.25f,"original accelerated AOD midpoint");near(time.getTransitionAlpha(),0,"AOD entry does not fade native clock");
        show(host,root,time,date);alpha(host,1);
        check(animator(host,"enterFade")==incoming&&animator(host,"stockFade")==null,"reconcile keeps only AOD animator");
        incoming.end();near(face.getAlpha(),1,"AOD settles opaque");near(time.getTransitionAlpha(),0,"stock clock remains suppressed");
        check((Boolean)field(host,"owning"),"entry retains AOD ownership");
        alpha(host,0);check(animator(host,"enterFade")==null,"black display has no entry animator");near(face.getAlpha(),0,"native OFF mask hides clock");
        alpha(host,.5f);incoming=animator(host,"enterFade");check(incoming!=null,"tap to AOD restarts entry");incoming.setCurrentPlayTime(500);
        near(face.getAlpha(),.125f,"native mask multiplies the entry alpha");alpha(host,.8f);
        check(animator(host,"enterFade")==incoming,"native mask updates do not restart entry");near(face.getAlpha(),.2f,"native mask remains authoritative");
        incoming.end();alpha(host,1);call(host,"leaveXiaomi");
        ValueAnimator wake=animator(host,"fade");
        check(wake!=null&&wake.getDuration()==500&&animator(host,"stockFade")==null,"wake animates only AOD face");
        near(time.getTransitionAlpha(),.73f,"wake immediately releases native time");near(date.getTransitionAlpha(),.61f,"wake immediately releases native date");
        wake.setCurrentPlayTime(250);near(face.getAlpha(),.5f,"AOD wake midpoint");near(time.getTransitionAlpha(),.73f,"AOD fade leaves native time unchanged");
        call(host,"leaveXiaomi");check(animator(host,"fade")==wake&&animator(host,"stockFade")==null,"reconcile does not restart wake");
        // Reverse an interrupted wake. Old callbacks must not remove the face.
        show(host,root,time,date);enter(host,true);alpha(host,1);
        near(face.getAlpha(),.5f,"resleep preserves AOD opacity");near(time.getTransitionAlpha(),0,"resleep masks stock clock without an animator");
        wake.end();check(field(host,"clock")==face&&(Boolean)field(host,"owning"),"stale wake callbacks cannot detach resleep face");
        near(time.getTransitionAlpha(),0,"stale AOD callback cannot reveal clock");
        animator(host,"enterFade").end();
        call(host,"leaveUnlocked");wake=animator(host,"fade");near(time.getTransitionAlpha(),0,"direct authentication keeps existing lock mask");
        call(host,"frameReady");check(animator(host,"stockFade")==null,"Xiaomi ignores LiuGuang lock reveal callbacks");
        wake.end();check(field(host,"clock")==null&&(Boolean)field(host,"holdingUnlock"),"unlock mask outlives AOD face");
        call(host,"unlockFinished");near(time.getTransitionAlpha(),.73f,"unlock restores native time baseline");near(date.getTransitionAlpha(),.61f,"unlock restores native date baseline");
        // Waking before entry became visible must still release the stock mask.
        show(host,root,time,date);enter(host,true);alpha(host,0);call(host,"leaveXiaomi");
        check(field(host,"clock")==null&&animator(host,"stockFade")==null,"invisible AOD leaves no clock animator");
        near(time.getTransitionAlpha(),.73f,"invisible AOD immediately releases native time");
        show(host,root,time,date);enter(host,true);alpha(host,1);
        call(host,"hide");check(animator(host,"enterFade")==null&&animator(host,"stockFade")==null&&animator(host,"fade")==null,"detach cancels all clock animation");
        near(time.getTransitionAlpha(),.73f,"detach restores stock alpha");
        show(host,root,time,date);enter(host,false);alpha(host,1);near(((View)field(host,"clock")).getAlpha(),1,"nonanimated restore is immediately visible");
        check(animator(host,"enterFade")==null&&animator(host,"stockFade")==null,"nonanimated restore adds no animator");
        call(host,"leaveXiaomi");animator(host,"fade").end();
        check(field(host,"clock")==null&&!(Boolean)field(host,"holdingClock"),"normal wake releases face and native mask");near(time.getTransitionAlpha(),.73f,"normal wake restores native alpha");
        invoke(host,"configure",new Class[]{Bundle.class},(Object)null);show(host,root,time,date);
        invoke(host,"leave",new Class[]{boolean.class},true);check(animator(host,"stockFade")==null,"LiuGuang still waits for submitted frame");
        call(host,"frameReady");check(animator(host,"stockFade").getDuration()==167,"LiuGuang reveal duration unchanged");call(host,"hide");
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();Activity activity=null;try{
        app=getTargetContext();ClassLoader loader=app.getClassLoader();Class<?> packs=loader.loadClass("org.aliveclean.XiaomiPacks"),packType=loader.loadClass("org.aliveclean.XiaomiPacks$Pack");
        List<?> all=(List<?>)invoke(packs,"all",new Class[]{Context.class},app);check(all.size()==6,"six families");
        for(Object pack:all){
            File apk=(File)invoke(packs,"find",new Class[]{Context.class,packType},app,pack);check(apk!=null,"verified pack "+field(pack,"id"));
            PackageInfo parsed=app.getPackageManager().getPackageArchiveInfo(apk.getPath(),PackageManager.GET_SERVICES|PackageManager.GET_META_DATA);
            Bundle expected=null;for(ServiceInfo s:parsed.services)if(!s.name.contains("Preview")&&s.metaData!=null&&s.metaData.getBoolean("is_super_wallpaper"))expected=s.metaData;
            check(expected!=null,"original manifest "+field(pack,"id"));Bundle actual=(Bundle)field(pack,"clock");
            for(String key:new String[]{"clock_position_x","clock_position_y","dual_clock_position_x_anchor_right","dual_clock_position_y"})check(actual.getFloat(key)==expected.getFloat(key),field(pack,"id")+" original "+key);
            Class<?> home=loader.loadClass("org.aliveclean.XiaomiHomePreview");
            android.content.res.Resources resources=(android.content.res.Resources)invoke(packs,"resources",new Class[]{Context.class,packType,File.class},app,pack,apk);
            for(int mode=0;mode<3;mode++)for(int land=0;land<(mode==2?(Integer)field(pack,"lands"):1);land++)for(boolean dark:new boolean[]{false,true}){
                int id=(Integer)invoke(home,"resource",new Class[]{Bundle.class,int.class,int.class,boolean.class},expected,mode,land,dark);
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeResource(resources,id,opts);
                check(id!=0&&opts.outWidth>0&&opts.outHeight>0,field(pack,"id")+" original card "+mode+"/"+land+"/"+dark);
            }
            Class<?> preview=loader.loadClass("org.aliveclean.XiaomiPreviewActivity");
            for(int mode=0;mode<3;mode++){
                Intent open=(Intent)invoke(preview,"intent",new Class[]{Context.class,packType,int.class},app,pack,mode);
                check(open.getIntExtra("scene",-1)==mode&&app.getPackageManager().resolveActivity(open,0)!=null,field(pack,"id")+" card opens original stage "+mode);
            }
        }
        activity=startActivitySync(new Intent().setClassName(app,"org.aliveclean.HomeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Activity screen=activity;Throwable[] error={null};
        runOnMainSync(()->{FrameLayout root=new FrameLayout(screen);((ViewGroup)screen.getWindow().getDecorView()).addView(root,new ViewGroup.LayoutParams(-1,-1));
            try{
                Class<?> uiClass=loader.loadClass("org.aliveclean.OfficialHyperOsUi");
                Context normal=(Context)invoke(uiClass,"open",new Class[]{Context.class},screen);
                int hostDpi=screen.getResources().getDisplayMetrics().densityDpi;
                int originalDpi=normal.getResources().getDisplayMetrics().densityDpi;
                int dimension=normal.getResources().getIdentifier("super_wallpaper_time_text_size","dimen","com.miui.aod");
                float before=dimension==0?0:normal.getResources().getDimension(dimension);
                Context adjusted=(Context)invoke(uiClass,"openSuperWallpaper",new Class[]{Context.class},screen);
                int aodDpi=adjusted.getResources().getDisplayMetrics().densityDpi;
                check(aodDpi>=120&&aodDpi<=1000,"valid original AOD density "+aodDpi);
                check(screen.getResources().getDisplayMetrics().densityDpi==hostDpi,"host density unchanged "+hostDpi);
                check(normal.getResources().getDisplayMetrics().densityDpi==originalDpi,"other HyperOS clocks retain density");
                check(dimension==0||normal.getResources().getDimension(dimension)==before,"other clock dimensions unchanged");
                check(adjusted.getResources().getConfiguration().smallestScreenWidthDp==normal.getResources().getConfiguration().smallestScreenWidthDp,"MIUIX retains window qualifiers");
                Class<?> hostClass=loader.loadClass("org.aliveclean.AodClockHost");Constructor<?> ctor=hostClass.getDeclaredConstructor();ctor.setAccessible(true);Object host=ctor.newInstance();
                TextView time=new TextView(screen),date=new TextView(screen);root.addView(time);root.addView(date);time.setTransitionAlpha(.73f);date.setTransitionAlpha(.61f);
                for(Object pack:all){
                    Bundle metadata=(Bundle)field(pack,"clock");invoke(host,"configure",new Class[]{Bundle.class},metadata);
                    invoke(host,"show",new Class[]{ViewGroup.class,View.class,View.class},root,time,date);
                    View clock=(View)field(host,"clock");check(clock!=null&&clock.getClass().getSimpleName().equals("XiaomiAodClockView"),field(pack,"id")+" original runtime clock attached");
                    clock.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(3168,View.MeasureSpec.EXACTLY));clock.layout(0,0,1440,3168);
                    int bottom=(Integer)invoke(clock,"notificationTop",new Class[0]);check(bottom>500&&bottom<2200,"clock content bounds "+bottom);
                    Bitmap image=Bitmap.createBitmap(1440,3168,Bitmap.Config.ARGB_8888);clock.draw(new Canvas(image));int pixels=0;for(int y=0;y<3168;y+=3)for(int x=0;x<1440;x+=3)if(Color.alpha(image.getPixel(x,y))>0)pixels++;
                    check(pixels>100&&pixels<100000,"visible original clock glyphs "+pixels);
                    if(field(pack,"id").equals("moon"))try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),"xiaomi-live-aod.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
                    check(time.getTransitionAlpha()==0&&date.getTransitionAlpha()==0,"only native clock leaves masked");
                    invoke(host,"tick",new Class[]{long.class},System.currentTimeMillis()+60000);
                    invoke(host,"hide",new Class[0]);check(time.getTransitionAlpha()==.73f&&date.getTransitionAlpha()==.61f,"native alpha restored");check(root.getChildCount()==2,"original face detached");
                }
                transitions(host,(Bundle)field(all.get(0),"clock"),root,time,date);
                invoke(host,"configure",new Class[]{Bundle.class},(Object)null);invoke(host,"show",new Class[]{ViewGroup.class,View.class,View.class},root,time,date);
                check(field(host,"clock").getClass().getSimpleName().equals("AodClockView"),"LiuGuang face remains independent");invoke(host,"hide",new Class[0]);
            }catch(Throwable t){error[0]=t;}finally{((ViewGroup)root.getParent()).removeView(root);}
        });if(error[0]!=null)throw new AssertionError("clock integration",error[0]);
        result.putString("stream",log+"Checks="+checks+"\n");finish(-1,result);
    }catch(Throwable t){result.putString("stream",log+android.util.Log.getStackTraceString(t));finish(0,result);}finally{if(activity!=null){Activity done=activity;runOnMainSync(done::finish);}}}
}
