package org.aliveclean;

import android.app.Instrumentation;
import android.opengl.*;
import android.os.*;
import java.nio.ByteBuffer;

/** Read the real renderer's RGBA output without background-vsync throttling. */
public final class BubbleSurfaceInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();StringBuilder log=new StringBuilder();boolean ok=false;
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try{
            int[] version=new int[2],count=new int[1];EGLConfig[] configs=new EGLConfig[1];
            if(!EGL14.eglInitialize(display,version,0,version,1))throw new AssertionError("EGL initialize");
            int[] attributes={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)||count[0]==0)throw new AssertionError("RGBA config");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,360,EGL14.EGL_HEIGHT,792,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new AssertionError("EGL current");
            ByteBuffer pixels=ByteBuffer.allocateDirect(360*792*4);
            for(int variant=201;variant<=205;variant++){
                int minimum=255;
                for(boolean dark:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
                    BubbleRenderer renderer=new BubbleRenderer(getTargetContext().getAssets(),variant,dark,360,792,mode,false);
                    try{for(int frame=0;frame<3;frame++){
                        renderer.motion().advance(1_000_000_000L+frame*500_000_000L,1);
                        renderer.render();pixels.clear();GLES30.glReadPixels(0,0,360,792,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                        if(GLES30.glGetError()!=GLES30.GL_NO_ERROR)throw new AssertionError("Readback failed");
                        for(int p=3;p<pixels.capacity();p+=4)minimum=Math.min(minimum,pixels.get(p)&255);
                    }}finally{renderer.close();}
                }
                log.append("variant=").append(variant).append(" minAlpha=").append(minimum).append(" frames=18\n");
                if(minimum!=255)throw new AssertionError("Translucent wallpaper output");
            }
            ok=true;log.append("BUBBLE_OPAQUE_OK");
        }catch(Throwable e){log.append(android.util.Log.getStackTraceString(e));}
        finally{
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
        result.putString("stream",log.toString());finish(ok?-1:1,result);
    }
}
