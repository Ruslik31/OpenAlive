package org.aliveclean.xiaomifidelity;

import android.app.Instrumentation;
import android.content.*;
import android.content.pm.PackageInfo;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Cold extraction uses a private temporary directory, never the user's packs. */
public final class XiaomiBuiltinTest extends Instrumentation {
    private int checks;private final StringBuilder log=new StringBuilder();
    private Class<?> packs,builtin,packType;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;log.append("PASS ").append(message).append('\n');}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object call(Class<?> type,String name,Class<?>[] signature,Object... args)throws Exception{
        Method method=type.getDeclaredMethod(name,signature);method.setAccessible(true);
        try{return method.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw new RuntimeException(e.getCause());}
    }
    private File prepare(Context c,Object pack)throws Exception{return (File)call(builtin,"prepare",new Class[]{Context.class,packType},c,pack);}
    private void verify(File file,Object pack)throws Exception{call(packs,"verify",new Class[]{File.class,packType},file,pack);}
    private Context isolated(Context app,File directory){return new ContextWrapper(app){@Override public File getFilesDir(){return directory;}};}
    public void onStart(){Bundle result=new Bundle();File testRoot=null;ExecutorService workers=null;int status=-1;
        try{
            Context app=getTargetContext();packs=app.getClassLoader().loadClass("org.aliveclean.XiaomiPacks");builtin=app.getClassLoader().loadClass("org.aliveclean.XiaomiBuiltinPacks");packType=app.getClassLoader().loadClass("org.aliveclean.XiaomiPacks$Pack");
            List<?> all=(List<?>)call(packs,"all",new Class[]{Context.class},app);check(all.size()==6,"six bundled families");
            testRoot=new File(app.getCacheDir(),"xiaomi-builtin-test-"+SystemClock.uptimeMillis());check(testRoot.mkdir(),"isolated empty directory");
            Context cold=isolated(app,testRoot);
            for(Object pack:all){
                String id=(String)field(pack,"id");long start=SystemClock.elapsedRealtime();File file=prepare(cold,pack);verify(file,pack);
                check(file.getCanonicalPath().startsWith(testRoot.getCanonicalPath()+File.separator),id+" reconstructed in empty test storage");
                check(!file.canWrite(),id+" original DEX container read only");
                PackageInfo parsed=app.getPackageManager().getPackageArchiveInfo(file.getPath(),0);
                check(parsed!=null&&parsed.packageName.equals("com.miui.miwallpaper."+id),id+" original manifest valid");
                long modified=file.lastModified();File reused=(File)call(packs,"find",new Class[]{Context.class,packType},cold,pack);
                check(reused.equals(file)&&modified==file.lastModified(),id+" reuse does not rewrite APK");
                log.append("COLD ").append(id).append(" ms=").append(SystemClock.elapsedRealtime()-start).append('\n');
            }
            Object geometry=call(packs,"get",new Class[]{Context.class,String.class},app,"geometry");File good=prepare(cold,geometry);
            Constructor<?> constructor=packType.getDeclaredConstructor(JSONObject.class);constructor.setAccessible(true);
            JSONObject source=new JSONObject().put("id","geometry").put("title","test").put("sha256",field(geometry,"digest")).put("bytes",(Long)field(geometry,"bytes")+1).put("lands",1)
                .put("clock",new JSONObject().put("clock_position_x",.34).put("clock_position_y",.12).put("dual_clock_position_x_anchor_right",.65).put("dual_clock_position_y",.6));
            boolean rejected=false;try{prepare(cold,constructor.newInstance(source));}catch(IOException expected){rejected=true;}
            check(rejected,"mismatched recipe rejected");verify(good,geometry);check(true,"failed preparation preserves valid APK");
            check(good.setWritable(true),"test cache may be corrupted without touching user data");try(FileOutputStream out=new FileOutputStream(good)){out.write(new byte[]{1,2,3});}
            File restored=(File)call(packs,"find",new Class[]{Context.class,packType},cold,geometry);verify(restored,geometry);
            check(restored.equals(good)&&!restored.canWrite(),"corrupt cache repaired from built-in data");
            File concurrent=new File(testRoot,"concurrent");check(concurrent.mkdir(),"concurrent test directory");Context shared=isolated(app,concurrent);
            workers=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
            Callable<File> task=()->{start.await();return prepare(shared,geometry);};Future<File> first=workers.submit(task),second=workers.submit(task);start.countDown();
            File one=first.get(90,TimeUnit.SECONDS),two=second.get(90,TimeUnit.SECONDS);verify(one,geometry);check(one.equals(two),"concurrent callers share complete verified APK");
            for(File folder:new File[]{good.getParentFile(),one.getParentFile()})for(File file:folder.listFiles())check(!file.getName().endsWith(".part"),"no abandoned partial file "+file.getName());
            result.putString("stream",log+"BUILTIN_OK checks="+checks+"\n");
        }catch(Throwable error){status=0;result.putString("stream",log+android.util.Log.getStackTraceString(error));}
        finally{if(workers!=null)workers.shutdownNow();if(testRoot!=null)removeTestFiles(testRoot,testRoot);}
        if(testRoot!=null&&testRoot.exists()){status=0;result.putString("stream",result.getString("stream")+"ERROR temporary test directory remains\n");}
        finish(status,result);
    }
    private static void removeTestFiles(File root,File file){try{
        if(!file.getCanonicalPath().equals(root.getCanonicalPath())&&!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))return;
        if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)removeTestFiles(root,child);}file.delete();
    }catch(IOException ignored){}}
}
