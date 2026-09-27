package org.aliveclean;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;

/** Isolated pixel/export and real Activity tests; does not touch wallpaper preferences. */
public final class PhotoCropInstrumentation extends Instrumentation {
    public static final class Host extends Activity {
        static volatile Intent returned;static volatile int result;
        @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);returned=data;result=code;}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private File fixture(String name,boolean jpeg)throws Exception{
        File f=new File(getTargetContext().getFilesDir(),name);
        Bitmap b=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);Paint p=new Paint();c.drawColor(Color.BLUE);p.setColor(Color.RED);c.drawRect(0,0,400,400,p);
        try(FileOutputStream out=new FileOutputStream(f)){b.compress(jpeg?Bitmap.CompressFormat.JPEG:Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}return f;
    }
    private Object field(Object instance,String name)throws Exception{Field f=PhotoCropActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    private void touch(View view,int action,float... xy){
        int count=xy.length/2;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[i*2];coords[i].y=xy[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(1000,1000+eventStep++*30,action,count,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try{view.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    private int eventStep;
    @Override public void onStart(){Bundle result=new Bundle();PhotoCropActivity activity=null;try{
        File f=fixture("photo-export.image",false);PhotoViewport p=new PhotoViewport();p.dimensions(800,400,144,316);
        p.drag(100,0);Bitmap left=PhotoCropActivity.export(f,p,144,316);
        check(left.getWidth()==144&&left.getHeight()==316,"Wrong output aspect/size");check(left.getPixel(72,158)==Color.RED,"Pan left export");left.recycle();
        p.drag(-100,0);Bitmap right=PhotoCropActivity.export(f,p,144,316);check(right.getPixel(72,158)==Color.BLUE,"Pan right export");right.recycle();
        p.restore(.25f,.5f,8);Bitmap zoom=PhotoCropActivity.export(f,p,144,316);check(zoom.getPixel(72,158)==Color.RED,"Zoom export");zoom.recycle();f.delete();
        File rotated=fixture("photo-rotated.jpg",true);ExifInterface exif=new ExifInterface(rotated.getPath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
        Bitmap oriented=ImageDecoder.decodeBitmap(ImageDecoder.createSource(rotated));check(oriented.getWidth()==400&&oriented.getHeight()==800,"EXIF orientation");oriented.recycle();
        p.dimensions(400,800,144,316);p.reset();Bitmap portrait=PhotoCropActivity.export(rotated,p,144,316);
        check(Color.red(portrait.getPixel(72,30))>240&&Color.blue(portrait.getPixel(72,286))>240,"EXIF crop changed orientation");portrait.recycle();rotated.delete();
        File source=fixture("photo-ui.image",false);
        activity=(PhotoCropActivity)startActivitySync(new Intent(getTargetContext(),PhotoCropActivity.class).putExtra("photo",source.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        PhotoCropActivity a=activity;boolean[] ready={false};long end=SystemClock.uptimeMillis()+10000;
        while(!ready[0]&&SystemClock.uptimeMillis()<end){runOnMainSync(()->{try{ready[0]=field(a,"bitmap")!=null&&((View)field(a,"cropView")).getHeight()>0;}catch(Exception e){throw new AssertionError(e);}});if(!ready[0])SystemClock.sleep(50);}
        check(ready[0],"Activity did not load");
        runOnMainSync(()->{try{
            PhotoCropActivity.CropView view=(PhotoCropActivity.CropView)field(a,"cropView");Point size=new Point();a.getWindowManager().getDefaultDisplay().getRealSize(size);
            check(Math.abs(view.frame.width()/view.frame.height()-(float)size.x/size.y)<.0001f,"Crop uses content instead of full display");
            PhotoViewport crop=(PhotoViewport)field(a,"viewport");float x=view.frame.centerX(),y=view.frame.centerY(),span=view.frame.height()/6;
            touch(view,MotionEvent.ACTION_DOWN,x,y-span);touch(view,MotionEvent.ACTION_POINTER_DOWN|(1<<8),x,y-span,x,y+span);
            for(int i=1;i<=8;i++)touch(view,MotionEvent.ACTION_MOVE,x,y-span*(1+i*.2f),x,y+span*(1+i*.2f));
            check(crop.zoom()>1.5f,"Pinch did not zoom: "+crop.zoom()+" span="+view.scale.getCurrentSpan()+" previous="+view.scale.getPreviousSpan()+" progress="+view.scale.isInProgress()+" frame="+view.frame);float oldX=crop.centerX();
            touch(view,MotionEvent.ACTION_POINTER_UP|(1<<8),x,y-span*2.6f,x,y+span*2.6f);
            touch(view,MotionEvent.ACTION_MOVE,x,y-span*2.6f);check(Math.abs(crop.centerX()-oldX)<.0001f,"Pointer removal jumps crop");
            touch(view,MotionEvent.ACTION_MOVE,x+span,y-span*2.6f);check(crop.centerX()<oldX,"Drag did not pan");touch(view,MotionEvent.ACTION_UP,x+span,y-span*2.6f);
        }catch(Exception e){throw new AssertionError(e);}});
        getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,new FileOutputStream(new File(getTargetContext().getFilesDir(),"crop-ui.png")));
        runOnMainSync(a::finish);waitForIdleSync();
        end=SystemClock.uptimeMillis()+5000;while(source.exists()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
        check(!source.exists(),"Cancelled input not cleaned");
        Host host=(Host)startActivitySync(new Intent(getTargetContext(),Host.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ActivityMonitor monitor=addMonitor(PhotoCropActivity.class.getName(),null,false);File saveSource=fixture("photo-save.image",false);
        runOnMainSync(()->host.startActivityForResult(new Intent(host,PhotoCropActivity.class).putExtra("photo",saveSource.getName()),31));
        PhotoCropActivity saving=(PhotoCropActivity)waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(saving!=null,"Save activity missing");
        ready[0]=false;end=SystemClock.uptimeMillis()+10000;
        while(!ready[0]&&SystemClock.uptimeMillis()<end){runOnMainSync(()->{try{ready[0]=field(saving,"bitmap")!=null;}catch(Exception e){throw new AssertionError(e);}});if(!ready[0])SystemClock.sleep(50);}
        check(ready[0],"Save photo not loaded");runOnMainSync(()->{try{((View)field(saving,"done")).performClick();}catch(Exception e){throw new AssertionError(e);}});
        end=SystemClock.uptimeMillis()+10000;while(Host.returned==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
        check(Host.result==Activity.RESULT_OK&&Host.returned!=null,"Completion result missing");
        File saved=new File(getTargetContext().getFilesDir(),Host.returned.getStringExtra("photo"));BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(saved.getPath(),dimensions);
        Point display=new Point();host.getWindowManager().getDefaultDisplay().getRealSize(display);check(dimensions.outWidth==display.x&&dimensions.outHeight==display.y,"Saved photo does not match display");saved.delete();runOnMainSync(host::finish);
        result.putString("stream","PHOTO_CROP_OK: full display aspect, export pixels, multitouch/pointer removal, EXIF, cancel and complete result\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null){PhotoCropActivity a=activity;runOnMainSync(a::finish);}}}
}
