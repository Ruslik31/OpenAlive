package org.aliveclean;

import android.graphics.Bitmap;
import android.util.Half;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Probe-only adapter for the original shader's signed distance / normal input. */
final class VivoGlyphDistanceField {
    static Bitmap create(Bitmap mask) {
        int w=mask.getWidth(),h=mask.getHeight(),n=w*h;
        int[] pixels=new int[n];mask.getPixels(pixels,0,w,0,0,w,h);
        float[] inside=distance(pixels,w,h,true),outside=distance(pixels,w,h,false);
        for(int i=0;i<n;i++)inside[i]=(float)Math.sqrt(inside[i])-(float)Math.sqrt(outside[i]);
        ByteBuffer data=ByteBuffer.allocateDirect(n*8).order(ByteOrder.nativeOrder());
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            float dx=(inside[y*w+Math.min(w-1,x+1)]-inside[y*w+Math.max(0,x-1)])*.5f;
            float dy=(inside[Math.min(h-1,y+1)*w+x]-inside[Math.max(0,y-1)*w+x])*.5f;
            data.putShort(Half.toHalf(inside[i]));data.putShort(Half.toHalf(dx));
            data.putShort(Half.toHalf(dy));data.putShort(Half.toHalf(1f));
        }
        data.rewind();Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.RGBA_F16);
        result.copyPixelsFromBuffer(data);return result;
    }
    private static float[] distance(int[] pixels,int w,int h,boolean featureInside){
        float[] image=new float[pixels.length];
        for(int i=0;i<image.length;i++)image[i]=((pixels[i]>>>24)>=128)==featureInside?0f:1e12f;
        int count=Math.max(w,h);float[] f=new float[count],d=new float[count],z=new float[count+1];int[] v=new int[count];
        for(int y=0;y<h;y++){
            System.arraycopy(image,y*w,f,0,w);line(f,d,v,z,w);System.arraycopy(d,0,image,y*w,w);
        }
        for(int x=0;x<w;x++){
            for(int y=0;y<h;y++)f[y]=image[y*w+x];line(f,d,v,z,h);
            for(int y=0;y<h;y++)image[y*w+x]=d[y];
        }
        return image;
    }
    private static void line(float[] f,float[] d,int[] v,float[] z,int n){
        int k=0;v[0]=0;z[0]=Float.NEGATIVE_INFINITY;z[1]=Float.POSITIVE_INFINITY;
        for(int q=1;q<n;q++){
            double s;
            do{
                int p=v[k];s=((double)f[q]+(double)q*q-f[p]-(double)p*p)/(2.0*(q-p));
                if(s>z[k])break;k--;
            }while(k>=0);
            ++k;v[k]=q;z[k]=(float)s;z[k+1]=Float.POSITIVE_INFINITY;
        }
        k=0;
        for(int q=0;q<n;q++){
            while(z[k+1]<q)k++;
            int delta=q-v[k];d[q]=delta*delta+f[v[k]];
        }
    }
}
