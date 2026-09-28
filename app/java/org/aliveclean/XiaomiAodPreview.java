package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import java.lang.reflect.*;

/** The original AOD renderer without its remote provider, settings or services. */
final class XiaomiAodPreview {
    static Bitmap render(Context host,Bundle metadata,int width,int height)throws Exception {
        Renderer renderer=new Renderer(host,metadata);
        View view=renderer.view;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;
    }
    static final class Renderer {
        final View view,content;
        private final Object renderer;
        private final Context host;
        Renderer(Context host,Bundle metadata)throws Exception {
        this.host=host;
        OfficialHyperOsUi ui=OfficialHyperOsUi.openSuperWallpaper(host);ClassLoader loader=ui.getClassLoader();
        // Supply only resources. Never run the vendor Application/onCreate,
        // services, screen-state observers or independent wake scheduling.
        loader.loadClass("com.miui.aod.AODApplication").getField("sInstance").set(null,ui);
        loader.loadClass("com.miui.aod.AODApplication").getField("sysuiContext").set(null,ui);
        Class<?> style=loader.loadClass("com.miui.aod.common.StyleInfo");
        Object info=loader.loadClass("com.miui.aod.category.SuperWallpaperCategoryInfo").getConstructor().newInstance();
        XiaomiUi.call(info,"setData",new Class[]{float.class,float.class,float.class,float.class,int.class},
                metadata.getFloat("clock_position_x"),metadata.getFloat("clock_position_y"),
                metadata.getFloat("dual_clock_position_x_anchor_right"),metadata.getFloat("dual_clock_position_y"),metadata.getInt("support_change_with_time",0));
        XiaomiUi.call(info,"switchOnDateAndTime",new Class[]{boolean.class},false);
        XiaomiUi.call(info,"switchOnNotificationIcon",new Class[]{boolean.class},false);
        XiaomiUi.call(info,"switchOnBatteryIcon",new Class[]{boolean.class},false);
        XiaomiUi.call(info,"setLunarSwitchOn",new Class[]{boolean.class},false);
        renderer=loader.loadClass("com.miui.aod.AODStyleController").getConstructor(int.class).newInstance(5);
        int layout=ui.getResources().getIdentifier("thumbnail_real_aod_layout","layout","com.miui.aod");
        view=LayoutInflater.from(ui).inflate(layout,null,false);
        View container=view.findViewById(ui.getResources().getIdentifier("clock_container","id","com.miui.aod"));
        XiaomiUi.call(renderer,"inflateView",new Class[]{View.class,style},container,info);
        update();
        View bg=view.findViewById(ui.getResources().getIdentifier("aod_bg","id","com.miui.aod"));if(bg!=null)bg.setVisibility(View.INVISIBLE);
        content=view.findViewById(ui.getResources().getIdentifier("single_clock_container","id","com.miui.aod"));
        if(content==null)throw new IllegalStateException("Original Xiaomi AOD clock content missing");
        // GONE views still receive attachment callbacks. The original template
        // embeds battery/notification controllers even with their options off;
        // detach those branches so ColorOS remains their sole owner.
        for(String id:new String[]{"battery_container","aod_icons_container"}){
            View unused=view.findViewById(ui.getResources().getIdentifier(id,"id","com.miui.aod"));
            if(unused!=null&&unused.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)unused.getParent()).removeView(unused);
        }
        }
        void update()throws Exception {XiaomiUi.call(renderer,"handleUpdateTime",new Class[]{boolean.class},android.text.format.DateFormat.is24HourFormat(host));}
    }
}
