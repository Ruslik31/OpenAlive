package org.aliveclean;

import android.graphics.*;
import android.view.View;
import java.io.*;
import java.util.concurrent.*;

/** Vivo's extracted glass shader, using the native host's live wallpaper stream. */
final class NativeVivoClockMaterial implements AutoCloseable {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"OpenAliveClockGlyph");t.setDaemon(true);return t;
    });
    private final View target;
    private final android.content.res.AssetManager assets;
    private RuntimeShader glass,background;
    // This branch reads the current RenderNode, not a cached software mask.
    private final RenderEffect liveGlyph=RenderEffect.createOffsetEffect(0f,0f);
    private Bitmap field,wallpaper,glyph;
    private final Rect fieldBounds=new Rect();
    private Future<?> glyphTask;
    private RectF crop=new RectF();
    private int generation,width,height;
    private int imageGeneration;
    private boolean dirty=true;
    private boolean drawingFrame;
    private boolean closed,pending;
    private boolean glassMode=true;
    private long layoutKey;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener layout=this::layoutChanged;
    private void layoutChanged(){
        if(closed)return;
        long key=geometryKey(target,true);
        if(key!=layoutKey){layoutKey=key;invalidateGlyph();target.invalidate();}
    }
    private final android.view.ViewTreeObserver.OnPreDrawListener preDraw=this::prepareFrame;
    private boolean prepareFrame(){
        if(!closed&&dirty&&wallpaper!=null&&!target.isLayoutRequested()){
            drawingFrame=true;
            try{update(wallpaper,new RectF(crop));}finally{drawingFrame=false;}
        }
        return true;
    }
    NativeVivoClockMaterial(View target)throws IOException {
        this(target,target.getContext().getAssets());
    }
    NativeVivoClockMaterial(View target,android.content.res.AssetManager assets)throws IOException {
        this.target=target;
        this.assets=assets;
        glass=new RuntimeShader(source("liquid_glass.agsl").replace(
                "normalize(float3(s.norm.x, -s.norm.y, 0.0))",
                "float3(s.norm.x, -s.norm.y, 0.0)"));
        // Keep Vivo's saturation and fusion functions. Adapt its region-gray
        // calculator on the GPU so hardware wallpaper buffers need no readback.
        String vibrancy=source("shader_vibrancy_effect.agsl").replace("half4 main(in float2 uv)","half4 originalMain(in float2 uv)");
        background=new RuntimeShader(vibrancy+"\nuniform float2 sampleSize; uniform int glassMode; uniform shader region;"
                +"half4 main(float2 p){half4 image=uBackground.eval(p);float gray=0;"
                +"for(int y=0;y<3;y++){for(int x=0;x<3;x++){gray+=dot(region.eval(sampleSize*(float2(x,y)+.5)/3.).rgb,float3(.299,.587,.114));}}"
                +"gray=gray*255./9.;float opacity;"
                +"if(glassMode==1){opacity=gray>216.?0.28:gray<178.?0.28-gray/177.*0.03:0.25-(gray-178.)/38.*0.08;opacity=clamp(opacity,.17,.28);}"
                +"else{opacity=gray>216.?.6:gray>178.?.32+.06*gray/216.:.7-.32*gray/177.;}"
                +"half3 color=saturation(image);color=fusionCompute(color,gray>216.?half3(0):half3(1),opacity,gray>216.?11:10);"
                +"return half4(saturate(color)*image.a,image.a);}");
        RuntimeShader white=new RuntimeShader("half4 main(float2 p){return half4(1);}");
        background.setInputShader("uContext",white);
        glass.setInputShader("uContext",white);
        background.setIntUniform("uType",1);background.setIntUniform("isDisplayP3",0);
        // Auto (-2) material, not the catalog's optional fixed blue color.
        background.setFloatUniform("uSaturation",10f);background.setFloatUniform("uDimmingRatio",1f);
        background.setFloatUniform("uMask_1",1f,1f,1f);
        background.setFloatUniform("uMask_2",0f,0f,0f);
        background.setFloatUniform("uOpacity",0f,0f,0f);background.setIntUniform("uFusion",10,0,0);
        glass.setIntUniform("uEdgeLightType",1);
        glass.setFloatUniform("uGeneratorLight1",-1f,1f,0f);glass.setFloatUniform("uGeneratorLight2",1f,-1f,0f);
        glass.setFloatUniform("uLightIndensity",1f);glass.setFloatUniform("uGlobalIntensity",1f);
        glass.setFloatUniform("uMaskColor",1f,1f,1f,1f);
        target.getViewTreeObserver().addOnPreDrawListener(preDraw);
        target.getViewTreeObserver().addOnGlobalLayoutListener(layout);
    }
    private static long geometryKey(View view,boolean root){
        long key=view.getWidth();key=31*key+view.getHeight();
        // Moving/scaling the complete face changes wallpaper sampling, not its
        // local glyph field. Avoid rebuilding SDF during scene translations.
        if(!root){key=31*key+view.getLeft();key=31*key+view.getTop();
            key=31*key+Float.floatToIntBits(view.getScaleX());key=31*key+Float.floatToIntBits(view.getScaleY());
            key=31*key+Float.floatToIntBits(view.getTranslationX());key=31*key+Float.floatToIntBits(view.getTranslationY());}
        key=31*key+view.getScrollX();key=31*key+view.getScrollY();key=31*key+view.getVisibility();
        if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;
            key=31*key+text.getText().toString().hashCode();key=31*key+Float.floatToIntBits(text.getTextSize());
            key=31*key+text.getBaseline();key=31*key+text.getGravity();
        }
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)key=31*key+geometryKey(group.getChildAt(i),false);
        }
        return key;
    }
    private String source(String name)throws IOException {
        try(InputStream in=assets.open("native-clock/vivo/"+name);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            return out.toString("UTF-8");
        }
    }
    void invalidateGlyph(){
        generation++;field=null;glyph=null;pending=false;dirty=true;
        if(glyphTask!=null){glyphTask.cancel(true);glyphTask=null;}
    }
    void mode(boolean glassMode){if(this.glassMode!=glassMode){this.glassMode=glassMode;dirty=true;}}
    void wallpaperChanged(){dirty=true;}
    boolean needsGlyph(){return glassMode&&glyph==null;}
    void update(Bitmap image,RectF rect){
        if(closed||image==null||image.isRecycled())return;
        int w=target.getWidth(),h=target.getHeight();
        if(w<=0||h<=0||target.isLayoutRequested()){wallpaper=image;crop.set(rect);dirty=true;return;}
        if(!dirty&&wallpaper==image&&imageGeneration==image.getGenerationId()&&crop.equals(rect)&&w==width&&h==height)return;
        wallpaper=image;imageGeneration=image.getGenerationId();crop.set(rect);
        if(w!=width||h!=height){width=w;height=h;invalidateGlyph();}
        // A face's layout callback can run before its parent has completed the
        // layout pass. Capture SDF geometry only at pre-draw. Ordinary blur needs
        // no CPU glyph bitmap; both materials use live RenderNode coverage.
        if(glassMode&&glyph==null){
            if(!drawingFrame){dirty=true;apply();target.invalidate();return;}
            captureGlyph();
        }
        if(glassMode&&field==null&&!pending)prepareGlyph();
        apply();dirty=false;
    }
    private void captureGlyph(){
        // Keep the complete glyph mask at view resolution, including the lower
        // half of stacked clocks. The GPU blur samples wallpaper, never glyphs.
        glyph=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        target.draw(new Canvas(glyph));
    }
    private void prepareGlyph(){
        // RenderEffect is a hardware composition property; software draw yields
        // unmodified glyph alpha. Bound the CPU distance transform and run it
        // only on geometry/minute changes, never on wallpaper frames.
        Bitmap mask=glyph;
        final int token=generation;pending=true;
        glyphTask=WORKER.submit(()->{
            Bitmap result;
            Rect bounds=new Rect();
            try{
                int w=mask.getWidth(),h=mask.getHeight();int[] row=new int[w];
                int left=w,top=h,right=0,bottom=0;
                for(int y=0;y<h;y++){
                    if(Thread.currentThread().isInterrupted())return;
                    mask.getPixels(row,0,w,0,y,w,1);
                    for(int x=0;x<w;x++)if((row[x]>>>24)!=0){left=Math.min(left,x);right=Math.max(right,x+1);top=Math.min(top,y);bottom=y+1;}
                }
                if(right<=left||bottom<=top){target.post(()->{if(token==generation)pending=false;});return;}
                bounds.set(Math.max(0,left-24),Math.max(0,top-24),Math.min(w,right+24),Math.min(h,bottom+24));
                Bitmap cropped=Bitmap.createBitmap(mask,bounds.left,bounds.top,bounds.width(),bounds.height());
                try{result=VivoGlyphDistanceField.create(cropped);}
                finally{if(cropped!=mask)cropped.recycle();}
            }
            catch(RuntimeException failure){target.post(()->{if(token==generation)pending=false;});return;}
            if(Thread.currentThread().isInterrupted()){result.recycle();return;}
            target.post(()->{
                if(closed||token!=generation){result.recycle();return;}
                pending=false;field=result;fieldBounds.set(bounds);apply();
            });
        });
    }
    private void apply(){
        if(closed||wallpaper==null||wallpaper.isRecycled())return;
        BitmapShader image=new BitmapShader(wallpaper,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        image.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        android.util.DisplayMetrics dm=target.getResources().getDisplayMetrics();
        Matrix map=new Matrix();
        float sx=(float)width/Math.max(1f,crop.width()),sy=(float)height/Math.max(1f,crop.height());
        map.setScale((float)dm.widthPixels/wallpaper.getWidth()*sx,(float)dm.heightPixels/wallpaper.getHeight()*sy);
        map.postTranslate(-crop.left*sx,-crop.top*sy);image.setLocalMatrix(map);
        // setInputShader snapshots child uniforms. Bind from leaves upward only
        // after every child's parameters have been updated for this frame.
        background.setInputShader("region",image);
        background.setIntUniform("glassMode",glassMode?1:0);
        background.setFloatUniform("sampleSize",(float)width,(float)height);
        background.setFloatUniform("uSaturation",glassMode?10f:50f);
        // Native separable GPU blur is evaluated once into an intermediate,
        // rather than re-running an 81-tap blur for every refraction lookup.
        RenderEffect sampled=RenderEffect.createChainEffect(
                RenderEffect.createBlurEffect(glassMode?9f:100f,glassMode?9f:100f,Shader.TileMode.CLAMP),
                RenderEffect.createShaderEffect(image));
        RenderEffect colored=RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(background,"uBackground"),sampled);
        if(!glassMode||field==null){
            target.setRenderEffect(RenderEffect.createBlendModeEffect(colored,liveGlyph,BlendMode.DST_IN));target.invalidate();return;
        }
        BitmapShader sdf=new BitmapShader(field,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        sdf.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        Matrix size=new Matrix();size.setTranslate(fieldBounds.left,fieldBounds.top);sdf.setLocalMatrix(size);
        glass.setInputBuffer("sdfTex",sdf);
        glass.setFloatUniform("texSize",(float)width,(float)height);
        glass.setFloatUniform("sdfTexSize",(float)width,(float)height);
        glass.setFloatUniform("uBackgroundSize",(float)width,(float)height);
        glass.setFloatUniform("uContextSize",(float)width,(float)height);
        // SDF supplies surface normals only. Use the live view for coverage:
        // cached software captures can have an earlier nested layout, and
        // resampling their alpha also degrades the original font's AA.
        RenderEffect surface=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(glass,"uBackground"),colored);
        target.setRenderEffect(RenderEffect.createBlendModeEffect(surface,liveGlyph,BlendMode.DST_IN));
        target.invalidate();
    }
    boolean applied(){return wallpaper!=null&&(!glassMode||field!=null)&&!closed;}
    @Override public void close(){closed=true;invalidateGlyph();if(target.getViewTreeObserver().isAlive()){target.getViewTreeObserver().removeOnPreDrawListener(preDraw);target.getViewTreeObserver().removeOnGlobalLayoutListener(layout);}target.setRenderEffect(null);wallpaper=null;glass=null;background=null;}
}
