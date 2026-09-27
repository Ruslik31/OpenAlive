package org.aliveclean;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import java.io.*;
import java.util.*;

/** Original layout/font loading and bounds checks, without changing the system clock. */
public final class NativeVivoClocksInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};StringBuilder report=new StringBuilder();
        runOnMainSync(()->{
            try{
                Context host=getTargetContext();OfficialVivoClockUi source=new OfficialVivoClockUi(host);
                NativeVivoClockStyles.Style[] styles=NativeVivoClockStyles.all(host);
                Bitmap sheet=Bitmap.createBitmap(1080,((styles.length+3)/4)*400,Bitmap.Config.ARGB_8888);Canvas grid=new Canvas(sheet);grid.drawColor(0xff20242a);
                Paint caption=new Paint(3);caption.setColor(Color.WHITE);caption.setTextSize(20);
                int width=host.getResources().getDisplayMetrics().widthPixels;
                Calendar time=Calendar.getInstance();time.set(2026,8,27,18,30,0);
                int index=0;
                for(NativeVivoClockStyles.Style style:styles){
                    for(boolean aod:new boolean[]{false,true}){
                        try(FileOutputStream progress=new FileOutputStream(new File(host.getFilesDir(),"vivo-layout-progress.txt"))){progress.write((style.key+" aod="+aod+"\n"+report).getBytes("UTF-8"));}
                        NativeVivoClockFace face=new NativeVivoClockFace(host,source,style,aod,false);
                        face.update(time.getTimeInMillis(),time.getTimeZone(),true);
                        face.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                        face.layout(0,0,width,face.getMeasuredHeight());RectF bounds=new RectF();face.numberBounds(bounds);
                        if(bounds.width()<width*.1f||bounds.height()<30||bounds.left<-.5f||bounds.right>width+.5f||bounds.top<-.5f||bounds.bottom>face.getHeight()+.5f)throw new AssertionError(style.key+" aod="+aod+" invalid "+bounds+" height="+face.getHeight()+" "+face.geometryReport());
                        report.append(style.key).append(aod?" AOD ":" lock ").append(bounds).append(" height=").append(face.getHeight()).append('\n');
                        if(!aod&&(style.key.equals("s7-4x4")||style.group==5||style.group==6))report.append(face.geometryReport()).append('\n');
                        if(!aod){
                            Bitmap image=Bitmap.createBitmap(width,face.getHeight(),Bitmap.Config.ARGB_8888);face.draw(new Canvas(image));
                            File file=new File(host.getFilesDir(),"vivo-"+style.key+".png");try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}
                            float scale=Math.min(250f/width,350f/image.getHeight());int x=(index%4)*270,y=(index/4)*400;
                            grid.save();grid.translate(x+10,y+35);grid.scale(scale,scale);grid.drawBitmap(image,0,0,null);grid.restore();grid.drawText(style.key,x+10,y+23,caption);image.recycle();
                        }
                    }
                    index++;
                }
                try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"vivo-clock-sheet.png"))){sheet.compress(Bitmap.CompressFormat.PNG,100,out);}
                report.append("VIVO_LAYOUTS_PASS ").append(styles.length).append(" configurations, lock and AOD\n");
            }catch(Throwable e){failure[0]=e;}
        });
        result.putString("stream",failure[0]==null?report.toString():android.util.Log.getStackTraceString(failure[0]));finish(failure[0]==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
