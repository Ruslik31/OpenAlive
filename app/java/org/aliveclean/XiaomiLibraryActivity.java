package org.aliveclean;

import android.app.*;
import android.content.*;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Original Xiaomi list shell and row widgets. Only the local data source is replaced. */
public final class XiaomiLibraryActivity extends Activity {
    private XiaomiUi ui; private XiaomiPacks.Pack importing; private boolean busy;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null&&state.containsKey("import"))try{importing=XiaomiPacks.get(this,state.getString("import"));}catch(Exception ignored){}
        new Thread(()->{try{XiaomiUi.bundle(this);runOnUiThread(()->{if(!isDestroyed())showList();});}catch(Exception e){runOnUiThread(()->failure(e));}},"XiaomiUiLoad").start();
    }
    private void showList(){try{
        ui=new XiaomiUi(this);ui.theme.applyStyle(ui.id("style","superWallpaperList.NoBlur"),true);
        TypedArray bg=ui.obtainStyledAttributes(new int[]{android.R.attr.windowBackground});getWindow().setBackgroundDrawable(bg.getDrawable(0));bg.recycle();
        View shell=ui.inflate("miuix_appcompat_screen_action_bar"),list=ui.inflate("activity_super_wallpaper_list");
        ((ViewGroup)shell.findViewById(android.R.id.content)).addView(list,new ViewGroup.LayoutParams(-1,-1));
        View bar=ui.find(shell,"action_bar");XiaomiUi.call(bar,"setDisplayOptions",new Class[]{int.class},12);
        XiaomiUi.call(bar,"setTitle",new Class[]{CharSequence.class},ui.getString(ui.id("string","super_wallpaper_list_label")));
        XiaomiUi.call(bar,"setExpandState",new Class[]{int.class},2);XiaomiUi.call(bar,"setWindowCallback",new Class[]{Window.Callback.class},this);
        View recycler=ui.find(list,"super_wallpaper_list");Class<?> lm=ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$LayoutManager");
        Object layout=ui.loader.loadClass("androidx.recyclerview.widget.LinearLayoutManager").getConstructor(Context.class,int.class,boolean.class).newInstance(ui,1,false);
        XiaomiUi.call(recycler,"setLayoutManager",new Class[]{lm},layout);
        List<XiaomiPacks.Pack> packs=XiaomiPacks.all(this);
        java.util.function.BiConsumer<View,Integer> binder=(v,index)->{try{
            XiaomiPacks.Pack pack=packs.get(index);Drawable image;try(InputStream in=getAssets().open("xiaomi/"+pack.id+".banner")){image=Drawable.createFromStream(in,pack.id);}
            final Drawable banner=image;Class<?> fn=ui.loader.loadClass("kotlin.jvm.functions.Function1");Object unit=ui.loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            Object setImage=Proxy.newProxyInstance(ui.loader,new Class[]{fn},(o,m,a)->{if(m.getName().equals("invoke"))((ImageView)a[0]).setImageDrawable(banner);return unit;});
            Object click=Proxy.newProxyInstance(ui.loader,new Class[]{fn},(o,m,a)->{if(m.getName().equals("invoke"))open(pack);return unit;});
            XiaomiUi.call(v,"k",new Class[]{boolean.class,boolean.class,boolean.class,fn,fn,String.class,String.class},false,false,true,setImage,click,pack.title,"");
            Class<?> complete=ui.loader.loadClass("com.android.thememanager.settings.superwallpaper.widget.DownloadViewHolderInterface$Companion$DownloadState$Complete");Object singleton=null;
            for(Field f:complete.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==complete){f.setAccessible(true);singleton=f.get(null);}
            XiaomiUi.call(v,"toq",new Class[]{complete.getSuperclass()},singleton);
        }catch(Exception e){failure(e);}};
        Object adapter=ui.loader.loadClass("org.aliveclean.XiaomiListAdapter").getConstructor(Context.class,int.class,int.class,java.util.function.BiConsumer.class).newInstance(ui,ui.id("layout","super_wallpaper_item_apk"),packs.size(),binder);
        XiaomiUi.call(recycler,"setAdapter",new Class[]{ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter")},adapter);setContentView(shell);
    }catch(Exception e){failure(e);}}
    private void open(XiaomiPacks.Pack pack){if(busy)return;busy=true;
        new Thread(()->{try{File apk=XiaomiPacks.find(this,pack);runOnUiThread(()->{busy=false;if(isDestroyed())return;if(apk==null)choosePack(pack);else launch(pack);});}catch(Exception e){runOnUiThread(()->{busy=false;failure(e);});}},"XiaomiPackCheck").start();
    }
    private void launch(XiaomiPacks.Pack p){startActivity(new Intent().setClassName(this,"org.aliveclean.XiaomiPreviewActivity$"+Character.toUpperCase(p.id.charAt(0))+p.id.substring(1)));}
    private void choosePack(XiaomiPacks.Pack p){importing=p;new AlertDialog.Builder(this).setTitle("导入"+p.title).setMessage("选择已保存的小米原版壁纸 APK。导入后即可离线使用，无需安装原版应用。").setNegativeButton("取消",null).setPositiveButton("选择壁纸包",(d,w)->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),1);}catch(RuntimeException e){failure(e);}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=1||result!=RESULT_OK||data==null||data.getData()==null||importing==null)return;
        XiaomiPacks.Pack pack=importing;busy=true;Toast.makeText(this,"正在校验壁纸包",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{XiaomiPacks.importPack(this,pack,data.getData());runOnUiThread(()->{busy=false;if(!isDestroyed())launch(pack);});}catch(Exception e){runOnUiThread(()->{busy=false;failure(e);});}},"XiaomiPackImport").start();
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(importing!=null)b.putString("import",importing.id);}
    @Override public boolean onMenuItemSelected(int feature,MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onMenuItemSelected(feature,item);}
    private void failure(Exception e){String reason=XiaomiFailure.describe(this,"Xiaomi catalog",e);android.util.Log.e("OpenAliveXiaomi","Xiaomi catalog",e);if(!isDestroyed())new AlertDialog.Builder(this).setTitle("小米壁纸暂时无法打开").setMessage(reason).setPositiveButton("确定",null).show();}
}
