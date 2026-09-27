package org.aliveclean;

import java.util.Random;

/** Offline geometry checks, including extreme photo/display ratios and gesture bounds. */
public final class PhotoViewportTest {
    private static void near(float a,float b){if(Math.abs(a-b)>0.0001f)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        Random random=new Random(90427);
        for(int[] size:new int[][]{{4000,3000,1440,3168},{3000,4000,1080,2400},{900,5000,1440,3168},{8000,1000,2400,1080},{1920,1080,1080,1920}}){
            PhotoViewport p=new PhotoViewport();p.dimensions(size[0],size[1],size[2],size[3]);
            for(int i=0;i<10000;i++){
                if(i%2==0)p.scale(.5f+random.nextFloat(),random.nextFloat(),random.nextFloat());
                else p.drag(random.nextFloat()*2-1,random.nextFloat()*2-1);
                if(p.left()<-.0001f||p.top()<-.0001f||p.left()+p.width()>1.0001f||p.top()+p.height()>1.0001f)throw new AssertionError("Empty crop edge");
                near((p.width()*size[0])/(p.height()*size[1]),(float)size[2]/size[3]);
                PhotoViewport copy=new PhotoViewport();copy.restore(p.centerX(),p.centerY(),p.zoom());copy.dimensions(size[0],size[1],size[2],size[3]);
                near(copy.left(),p.left());near(copy.top(),p.top());near(copy.zoom(),p.zoom());
            }
            p.reset();near(p.centerX(),.5f);near(p.centerY(),.5f);near(p.zoom(),1);
        }
        PhotoViewport p=new PhotoViewport();p.dimensions(3000,3000,1000,2000);
        float before=p.left()+.3f*p.width();p.scale(2,.3f,.5f);near(before,p.left()+.3f*p.width());
        p.restore(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN);near(p.zoom(),1);near(p.centerX(),.5f);
        System.out.println("PHOTO_VIEWPORT_OK: 50000 bounded gestures, aspect ratios, anchor, restore and reset");
    }
}
