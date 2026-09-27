package org.aliveclean;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Isolated backdrop test; never writes clock, wallpaper or SystemUI settings. */
public final class VivoClockMaterialProbe extends Instrumentation {
    private boolean production;
    public static class ProbeActivity extends Activity {
        FrameLayout root;
        View backdrop, material;
        volatile Throwable failure;
        Bitmap glyphs;
        RenderEffect combined;
        boolean nativeVivo;
        NativeVivoClockMaterial productionMaterial;
        String nativeStatus="";
        String vibrancySource;
        int backdropColor=0xff2040b0;
        final Paint backdropPaint=new Paint(3);
        void drawBackdrop(Canvas canvas){
            int w=root.getWidth(),h=root.getHeight();
            canvas.drawColor(backdropColor);
            backdropPaint.setShader(new LinearGradient(0,0,w,h,
                new int[]{0xfff09a43,0xff2339aa,0xff70dbbc},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(w*.48f,h*.43f,w*.5f,backdropPaint);
            backdropPaint.setShader(null);backdropPaint.setColor(0x90ffffff);
            for(int y=0;y<h;y+=46)canvas.drawRect(0,y,w,y+5,backdropPaint);
        }
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            setShowWhenLocked(true);setTurnScreenOn(true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.BLACK);
            root=new FrameLayout(this);
            backdrop=new View(this){
                @Override protected void onDraw(Canvas canvas){drawBackdrop(canvas);}
            };
            root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
            material=new View(this){@Override protected void onDraw(Canvas c){drawBackdrop(c);}};
            root.addView(material,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
            root.post(()->{try{configure();}catch(Throwable e){failure=e;}});
        }
        void configure()throws Exception {
            int w=root.getWidth(),h=root.getHeight();
            try{View.class.getMethod("setLiquidGlassEffect",float.class);nativeVivo=true;}catch(NoSuchMethodException ignored){}
            glyphs=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(glyphs);Paint p=new Paint(3);p.setColor(Color.WHITE);
            p.setTypeface(new Typeface.Builder(getAssets(),"vivoSansClockHAVF-OS7.ttf")
                    .setFontVariationSettings("'wght' 850, 'ytde' 80").build());
            p.setTextSize(w*.37f);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("00:53",w*.5f,h*.42f,p);
            String source;
            try(InputStream in=getAssets().open("shader_vibrancy_effect.agsl")){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>0)bytes.write(buf,0,n);source=bytes.toString("UTF-8");
            }
            vibrancySource=source;
            if(getIntent().getBooleanExtra("production",false)){
                // Imported clock views own a foreign APK's AssetManager. The
                // material must explicitly use the module assets, not this one.
                Context foreign=new ContextWrapper(this){@Override public android.content.res.AssetManager getAssets(){return android.content.res.Resources.getSystem().getAssets();}};
                TextView text=new TextView(foreign);text.setText("00:53");text.setTextColor(Color.WHITE);
                text.setTypeface(p.getTypeface());text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,w*.37f);
                text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);
                root.removeView(material);material=text;root.addView(material,new FrameLayout.LayoutParams(-1,-1));
                productionMaterial=new NativeVivoClockMaterial(material,getAssets());
                material.post(()->{
                    glyphs.eraseColor(Color.TRANSPARENT);material.draw(new Canvas(glyphs));
                    productionFrame();
                });return;
            }
            if(nativeVivo){
                TextView text=new TextView(this);text.setText("00:53");text.setTextColor(Color.WHITE);
                text.setTypeface(p.getTypeface());text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,w*.37f);
                text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);
                root.removeView(material);material=text;root.addView(material,new FrameLayout.LayoutParams(-1,-1));
                nativeStatus="glassSupported="+Class.forName("android.view.blur.IVivoBlurRender")
                    .getMethod("isSupportGlassEffect").invoke(null);
                Object enabled=View.class.getMethod("setMaterial",int.class,int.class).invoke(material,0,1);
                nativeStatus+=" materialEnabled="+enabled;
                View.class.getMethod("chooseMaterialBlurIteration",int.class).invoke(material,4);
                View.class.getMethod("setMaterialCustomized",float.class,float.class,int.class,float.class,int.class,int.class,float.class,int.class,float.class,int.class)
                    .invoke(material,.09f,10f,0xff46b2ff,.66f,0,0xff000000,0f,0,1f,0);
                View.class.getMethod("setLiquidGlassEffect",float.class).invoke(material,1f);
                View.class.getMethod("setLiquidGlassRimLight",Integer.class,Float.class).invoke(material,Integer.valueOf(3),Float.valueOf(1f));
                material.invalidate();
                return;
            }
            TextView maskText=new TextView(this);maskText.setText("00:53");maskText.setTextColor(Color.WHITE);
            maskText.setTypeface(p.getTypeface());maskText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,w*.37f);
            maskText.setGravity(Gravity.CENTER);maskText.setIncludeFontPadding(false);
            maskText.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
            maskText.layout(0,0,w,h);glyphs.eraseColor(Color.TRANSPARENT);maskText.draw(new Canvas(glyphs));
            configurePortableGlass(w,h);
        }
        void productionFrame(){
            Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            drawBackdrop(new Canvas(bitmap));
            productionMaterial.update(bitmap,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels));
        }
        @Override public void onDestroy(){if(productionMaterial!=null)productionMaterial.close();super.onDestroy();}
        void configurePortableGlass(int w,int h)throws Exception{
            String source;
            try(InputStream in=getAssets().open("liquid_glass.agsl")){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>0)bytes.write(buf,0,n);source=bytes.toString("UTF-8");
            }
            RuntimeShader shader=new RuntimeShader(source);
            shader.setInputShader("uContext",new BitmapShader(glyphs,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
            shader.setInputBuffer("sdfTex",new BitmapShader(VivoGlyphDistanceField.create(glyphs),Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
            shader.setFloatUniform("texSize",(float)w,(float)h);
            shader.setFloatUniform("sdfTexSize",(float)w,(float)h);
            shader.setFloatUniform("uBackgroundSize",(float)w,(float)h);
            shader.setFloatUniform("uContextSize",(float)w,(float)h);
            shader.setIntUniform("uEdgeLightType",1);
            shader.setFloatUniform("uGeneratorLight1",-1f,1f,0f);
            shader.setFloatUniform("uGeneratorLight2",1f,-1f,0f);
            shader.setFloatUniform("uLightIndensity",1f);shader.setFloatUniform("uGlobalIntensity",1f);
            shader.setFloatUniform("uMaskColor",1f,1f,1f,1f);
            // Vivo's material stage tints/saturates the sampled background before
            // the glass stage. Reuse its vibrancy implementation for that input.
            RuntimeShader background=new RuntimeShader(vibrancySource);
            Bitmap white=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);
            background.setInputShader("uContext",new BitmapShader(white,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
            background.setIntUniform("uType",1);background.setIntUniform("isDisplayP3",0);
            background.setFloatUniform("uSaturation",10f);background.setFloatUniform("uDimmingRatio",1f);
            background.setFloatUniform("uMask_1",70f/255f,178f/255f,1f);background.setFloatUniform("uMask_2",0f,0f,0f);
            background.setFloatUniform("uOpacity",.66f,0f,0f);background.setIntUniform("uFusion",0,0,0);
            RenderEffect sampled=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(background,"uBackground"),
                RenderEffect.createBlurEffect(9f,9f,Shader.TileMode.CLAMP));
            material.setRenderEffect(RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader,"uBackground"),sampled));
            material.invalidate();
        }
        void configurePortableBlur(){
            RuntimeShader shader=new RuntimeShader(vibrancySource);
            shader.setInputShader("uContext",new BitmapShader(glyphs,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
            shader.setIntUniform("uType",1);shader.setIntUniform("isDisplayP3",0);
            shader.setFloatUniform("uSaturation",50f);shader.setFloatUniform("uDimmingRatio",1f);
            shader.setFloatUniform("uMask_1",1f,1f,1f);shader.setFloatUniform("uMask_2",0f,0f,0f);
            shader.setFloatUniform("uOpacity",.4f,0f,0f);shader.setIntUniform("uFusion",0,0,0);
            RenderEffect color=RenderEffect.createRuntimeShaderEffect(shader,"uBackground");
            RenderEffect blur=RenderEffect.createBlurEffect(40f,40f,Shader.TileMode.CLAMP);
            combined=RenderEffect.createChainEffect(color,blur);
            material.setRenderEffect(combined);
            material.invalidate();
        }
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);production=args!=null&&"true".equals(args.getString("production"));start();}
    private String inspectColorOsFrameSource(ProbeActivity a){
        Object[] factory={null};Class<?>[] type={null};Throwable[] error={null};
        try{
            getUiAutomation().adoptShellPermissionIdentity("oplus.permission.OPLUS_COMPONENT_SAFE");
            runOnMainSync(()->{try{
                Context code=a.createPackageContext("com.android.systemui",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
                type[0]=code.getClassLoader().loadClass("com.oplus.posteffect.BlurBitmapFactory");
                Object viewRoot=View.class.getMethod("getViewRootImpl").invoke(a.root);
                Object surface=viewRoot.getClass().getMethod("getSurfaceControl").invoke(viewRoot);
                factory[0]=type[0].getConstructor(Context.class,Class.forName("android.view.SurfaceControl"))
                    .newInstance(a.getApplicationContext(),surface);
                Object params=type[0].getMethod("getBlurParams").invoke(factory[0]);
                params.getClass().getMethod("setBlurRadius",int.class).invoke(params,9);
                type[0].getMethod("setBlurParams",params.getClass()).invoke(factory[0],params);
            }catch(Throwable e){error[0]=e;}});
            if(error[0]!=null)throw new IllegalStateException(error[0]);
            for(int i=0;i<12;i++){
                Thread.sleep(200);
                Bitmap[] bitmap={null};
                runOnMainSync(()->{try{bitmap[0]=(Bitmap)type[0].getMethod("getBitmap").invoke(factory[0]);}catch(Throwable e){error[0]=e;}});
                if(error[0]!=null)throw new IllegalStateException(error[0]);
                if(bitmap[0]!=null&&!bitmap[0].isRecycled()){
                    try(FileOutputStream out=new FileOutputStream(new File(a.getFilesDir(),"live-source.png"))){bitmap[0].compress(Bitmap.CompressFormat.PNG,100,out);}
                    return "FRAME_SOURCE_CAPTURED size="+bitmap[0].getWidth()+"x"+bitmap[0].getHeight();
                }
            }
            return "FRAME_SOURCE_NO_BUFFER";
        }catch(Throwable e){StringWriter text=new StringWriter();e.printStackTrace(new PrintWriter(text));return "FRAME_SOURCE_UNAVAILABLE "+text;}
        finally{
            if(factory[0]!=null)runOnMainSync(()->{try{type[0].getMethod("release",boolean.class).invoke(factory[0],true);}catch(Throwable ignored){}});
            getUiAutomation().dropShellPermissionIdentity();
        }
    }
    @Override public void onStart(){
        Bundle result=new Bundle();ProbeActivity activity=null;
        try{
            activity=(ProbeActivity)startActivitySync(new Intent(getTargetContext(),ProbeActivity.class).putExtra("production",production).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(900);
            if(activity.failure!=null)throw new IllegalStateException("Backdrop setup",activity.failure);
            ProbeActivity a=activity;
            if(production){
                long end=SystemClock.uptimeMillis()+8000;
                while((a.productionMaterial==null||!a.productionMaterial.applied())&&SystemClock.uptimeMillis()<end)Thread.sleep(100);
                Thread.sleep(1000);
                if(a.productionMaterial==null||!a.productionMaterial.applied())throw new AssertionError("Initial material never rendered");
            }
            if(a.nativeVivo||production)runOnMainSync(()->{
                // Software draw records the TextView's real laid-out glyph mask,
                // not the differently positioned Canvas glyphs used on ColorOS.
                a.glyphs.eraseColor(Color.TRANSPARENT);
                a.material.draw(new Canvas(a.glyphs));
            });
            Bitmap first=capture(activity,"blue.png");
            runOnMainSync(()->{a.backdropColor=0xffb03020;a.backdrop.invalidate();a.material.invalidate();if(a.productionMaterial!=null)a.productionFrame();});
            Thread.sleep(350);Bitmap second=capture(activity,"red.png");
            int hits=0,changed=0,bright=0;
            // The input glyph bitmap remains identical across both captures.
            // Compare glyph pixels, not the changing background outside them.
            for(int y=0;y<a.glyphs.getHeight();y+=3)for(int x=0;x<a.glyphs.getWidth();x+=3){
                if(Color.alpha(a.glyphs.getPixel(x,y))<250)continue;
                hits++;int c1=first.getPixel(x,y),c2=second.getPixel(x,y);
                if(Math.abs(Color.red(c1)-Color.red(c2))>40)changed++;
                if(Color.red(c1)>70&&Color.green(c1)>85)bright++;
            }
            if(!a.nativeVivo&&(hits<100||changed<hits*.15))
                throw new AssertionError("Live glyph material failed hits="+hits+" changed="+changed+" blended="+bright);
            if(production){
                if(!a.productionMaterial.applied())throw new AssertionError("Production material not applied");
                runOnMainSync(()->{((TextView)a.material).setText("18:30");a.productionMaterial.invalidateGlyph();a.productionFrame();});
                Thread.sleep(600);capture(activity,"minute.png");
                runOnMainSync(()->{a.productionMaterial.mode(false);a.productionFrame();});
                Thread.sleep(600);Bitmap blurFirst=capture(activity,"blur.png");
                Bitmap plain=Bitmap.createBitmap(a.root.getWidth(),a.root.getHeight(),Bitmap.Config.ARGB_8888);
                runOnMainSync(()->{a.glyphs.eraseColor(Color.TRANSPARENT);a.material.draw(new Canvas(a.glyphs));a.drawBackdrop(new Canvas(plain));});
                int visible=0;
                for(int y=0;y<plain.getHeight();y+=3)for(int x=0;x<plain.getWidth();x+=3){
                    if((a.glyphs.getPixel(x,y)>>>24)>240){int c=blurFirst.getPixel(x,y),p=plain.getPixel(x,y);
                        if(Math.abs(Color.red(c)-Color.red(p))+Math.abs(Color.green(c)-Color.green(p))+Math.abs(Color.blue(c)-Color.blue(p))>30)visible++;
                    }
                }
                if(visible<1000)throw new AssertionError("Ordinary blur glyph disappeared: "+visible);
                runOnMainSync(()->{a.backdropColor=0xff20a070;a.backdrop.invalidate();a.productionFrame();});
                Thread.sleep(350);Bitmap blurSecond=capture(activity,"blur-live.png");
                int blurChanges=0;
                for(int y=0;y<blurFirst.getHeight();y+=3)for(int x=0;x<blurFirst.getWidth();x+=3){
                    if((a.glyphs.getPixel(x,y)>>>24)>240&&blurFirst.getPixel(x,y)!=blurSecond.getPixel(x,y))blurChanges++;
                }
                if(blurChanges<100)throw new AssertionError("Ordinary blur did not follow live wallpaper: "+blurChanges);
                for(boolean glass:new boolean[]{true,false}){
                    runOnMainSync(()->{
                        a.backdrop.setVisibility(View.INVISIBLE);a.root.setBackgroundColor(Color.BLACK);
                        ((TextView)a.material).setText("18\n30");
                        ((TextView)a.material).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,a.root.getWidth()*.20f);
                        a.material.setPivotX(0);a.material.setPivotY(0);a.material.setScaleX(.55f);a.material.setScaleY(.55f);
                        a.material.setTranslationX(a.root.getWidth()*.2f);
                        a.productionMaterial.invalidateGlyph();a.productionMaterial.mode(glass);a.productionFrame();
                    });
                    Thread.sleep(1000);
                    long readyEnd=SystemClock.uptimeMillis()+5000;
                    while(!a.productionMaterial.applied()&&SystemClock.uptimeMillis()<readyEnd)Thread.sleep(50);
                    if(!a.productionMaterial.applied())throw new AssertionError("Scaled material not ready glass="+glass);
                    Bitmap scaled=capture(a,glass?"scaled-glass.png":"scaled-blur.png");
                    runOnMainSync(()->{
                        a.glyphs.eraseColor(Color.TRANSPARENT);Canvas canvas=new Canvas(a.glyphs);
                        canvas.translate(a.material.getTranslationX(),a.material.getTranslationY());canvas.scale(.55f,.55f);a.material.draw(canvas);
                    });
                    int expected=0,drawn=0,lower=0,lowerDrawn=0;
                    for(int y=0;y<scaled.getHeight();y+=2)for(int x=0;x<scaled.getWidth();x+=2){
                        if((a.glyphs.getPixel(x,y)>>>24)<240)continue;
                        expected++;int c=scaled.getPixel(x,y);boolean visiblePixel=Color.red(c)+Color.green(c)+Color.blue(c)>45;
                        if(visiblePixel)drawn++;
                        if(y>a.material.getHeight()*.55f*.5f){lower++;if(visiblePixel)lowerDrawn++;}
                    }
                    if(expected<100||drawn<expected*.95||lower<100||lowerDrawn<lower*.95)
                        throw new AssertionError("Scaled stacked clock clipped glass="+glass+" coverage="+drawn+"/"+expected+" lower="+lowerDrawn+"/"+lower);
                }
                // An inner layout change does not necessarily resize the face.
                // Do not manually invalidate its mask: this is the preview path.
                for(boolean glass:new boolean[]{true,false}){
                    runOnMainSync(()->{
                        TextView text=(TextView)a.material;
                        text.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
                        text.setPadding(0,glass?100:240,0,0);
                        a.productionMaterial.mode(glass);a.productionFrame();
                    });
                    Thread.sleep(900);
                    Bitmap moved=capture(a,glass?"relayout-glass.png":"relayout-blur.png");
                    runOnMainSync(()->{a.glyphs.eraseColor(Color.TRANSPARENT);Canvas canvas=new Canvas(a.glyphs);canvas.translate(a.material.getTranslationX(),a.material.getTranslationY());canvas.scale(.55f,.55f);a.material.draw(canvas);});
                    int expected=0,filled=0;
                    for(int y=0;y<moved.getHeight();y+=2)for(int x=0;x<moved.getWidth();x+=2){if(Color.alpha(a.glyphs.getPixel(x,y))<240)continue;expected++;int c=moved.getPixel(x,y);if(Color.red(c)+Color.green(c)+Color.blue(c)>45)filled++;}
                    if(expected<100||filled<expected*.95)throw new AssertionError("Live relayout clipped glass="+glass+" coverage="+filled+"/"+expected);
                }
                String timing=benchmark(a,true)+benchmark(a,false);
                result.putString("stream","VIVO_PRODUCTION_GLASS_CAPTURED glyphs="+hits+" changed="+changed+" blended="+bright+"\nVIVO_PRODUCTION_BLUR_CAPTURED changed="+blurChanges+"\nSCALED_STACKED_GLYPHS_OK glass=true blur=true\nLIVE_RELAYOUT_GLYPHS_OK glass=true blur=true\n"+timing);
                finish(Activity.RESULT_OK,result);return;
            }
            if(a.nativeVivo){
                runOnMainSync(()->{try{
                    View.class.getMethod("clearLiquidGlassEffect").invoke(a.material);
                    View.class.getMethod("setMaterial",int.class,int.class).invoke(a.material,0,1);
                    View.class.getMethod("setMaterialCustomized",float.class,float.class,int.class,float.class,int.class,int.class,float.class,int.class,float.class,int.class)
                        .invoke(a.material,.37f,50f,0xff000000,0f,0,0xff000000,0f,0,1f,0);
                    RuntimeShader shader=new RuntimeShader(a.vibrancySource);
                    shader.setIntUniform("uType",1);shader.setIntUniform("isDisplayP3",0);
                    shader.setFloatUniform("uSaturation",50f);shader.setFloatUniform("uDimmingRatio",1f);
                    shader.setFloatUniform("uMask_1",1f,1f,1f);shader.setFloatUniform("uMask_2",0f,0f,0f);
                    shader.setFloatUniform("uOpacity",.4f,0f,0f);shader.setIntUniform("uFusion",0,0,0);
                    View.class.getMethod("setVibrancyEffect",RuntimeShader.class,int.class).invoke(a.material,shader,1);
                    View.class.getMethod("setVibrancyScaleFix",float.class).invoke(a.material,1f);
                    a.material.invalidate();
                }catch(Exception e){a.failure=e;}});
                if(a.failure!=null)throw new IllegalStateException(a.failure);
                Thread.sleep(350);capture(activity,"blur.png");
            }else{
                runOnMainSync(a::configurePortableBlur);
                Thread.sleep(350);capture(activity,"blur.png");
            }
            runOnMainSync(()->{
                a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                a.backdrop.setVisibility(View.GONE);
                a.material.invalidate();
            });
            Thread.sleep(900);capture(activity,"wallpaper.png");
            String frameSource=a.nativeVivo?"":inspectColorOsFrameSource(a);
            result.putString("stream",(a.nativeVivo?"VIVO_NATIVE_GLASS_CAPTURED":"VIVO_PORTABLE_SHADER_CAPTURED")+" "+a.nativeStatus+" glyphs="+hits+" changed="+changed+" blended="+bright+"\n"+frameSource+"\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));result.putString("stream",s.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){ProbeActivity a=activity;runOnMainSync(a::finish);}}
    }
    private Bitmap capture(ProbeActivity activity,String name)throws Exception{
        Bitmap window=Bitmap.createBitmap(activity.getWindow().getDecorView().getWidth(),activity.getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
        CountDownLatch done=new CountDownLatch(1);int[] status={-1};
        PixelCopy.request(activity.getWindow(),window,r->{status[0]=r;done.countDown();},new Handler(Looper.getMainLooper()));
        if(!done.await(5,TimeUnit.SECONDS)||status[0]!=PixelCopy.SUCCESS)throw new IOException("PixelCopy "+status[0]);
        int[] pos=new int[2];runOnMainSync(()->activity.root.getLocationInWindow(pos));
        Bitmap content=Bitmap.createBitmap(window,pos[0],pos[1],activity.root.getWidth(),activity.root.getHeight());
        try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),name))){content.compress(Bitmap.CompressFormat.PNG,100,out);}
        return content;
    }
    private String benchmark(ProbeActivity a,boolean glass)throws Exception{
        java.util.ArrayList<Long> times=new java.util.ArrayList<>();
        CountDownLatch finished=new CountDownLatch(1);
        Bitmap frame=Bitmap.createBitmap(96,192,Bitmap.Config.ARGB_8888);
        Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->times.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
        runOnMainSync(()->{
            a.material.setScaleX(1f);a.material.setScaleY(1f);a.material.setTranslationX(0);
            a.productionMaterial.mode(glass);
            a.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(Looper.getMainLooper()));
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){
                int count;
                public void doFrame(long time){
                    frame.eraseColor(Color.rgb(60+count%120,90,140));
                    a.productionMaterial.update(frame,new RectF(0,0,a.getResources().getDisplayMetrics().widthPixels,a.getResources().getDisplayMetrics().heightPixels));
                    if(++count<120)Choreographer.getInstance().postFrameCallback(this);else finished.countDown();
                }
            });
        });
        if(!finished.await(10,TimeUnit.SECONDS))throw new AssertionError("Continuous material frames stalled");
        long[][] measured={null};
        runOnMainSync(()->{a.getWindow().removeOnFrameMetricsAvailableListener(listener);measured[0]=new long[times.size()];for(int i=0;i<times.size();i++)measured[0][i]=times.get(i);});
        long[] samples=measured[0];java.util.Arrays.sort(samples);
        if(samples.length<30)throw new AssertionError("Insufficient frame metrics "+samples.length);
        double p95=samples[(int)((samples.length-1)*.95)]/1e6;
        if(p95>50)throw new AssertionError("Material frame time glass="+glass+" p95="+p95+"ms");
        return "MATERIAL_FRAME_TIMING glass="+glass+" frames="+samples.length+" p95_ms="+p95+"\n";
    }
}
