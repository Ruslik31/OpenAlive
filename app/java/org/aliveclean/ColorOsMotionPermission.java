package org.aliveclean;

import android.app.AppOpsManager;
import android.content.Context;
import java.lang.reflect.InvocationTargetException;

/** ColorOS's device-motion permission, granted only to our primary-user package. */
final class ColorOsMotionPermission {
    private static final String PACKAGE="org.aliveclean";
    private static final String OP="android:direction_sensors";

    /** False means this ROM does not expose the vendor operation. */
    static boolean allow(Context context)throws Exception {
        if(android.os.Process.myUid()!=1000)throw new SecurityException("Requires system UID");
        final int operation;
        try{
            operation=(Integer)AppOpsManager.class.getMethod("strOpToOp",String.class).invoke(null,OP);
        }catch(InvocationTargetException error){
            if(error.getCause() instanceof IllegalArgumentException)return false;
            throw error;
        }
        int uid=context.getPackageManager().getApplicationInfo(PACKAGE,0).uid;
        if(uid/100000!=0)throw new SecurityException("Requires primary-user package");
        AppOpsManager ops=context.getSystemService(AppOpsManager.class);
        if(ops==null)throw new IllegalStateException("AppOps unavailable");
        // Use the ROM's name-to-number mapping; vendor op numbers vary by release.
        AppOpsManager.class.getMethod("setMode",int.class,int.class,String.class,int.class)
                .invoke(ops,operation,uid,PACKAGE,AppOpsManager.MODE_ALLOWED);
        if(ops.checkOpNoThrow(OP,uid,PACKAGE)!=AppOpsManager.MODE_ALLOWED)
            throw new IllegalStateException("Device-motion permission verification failed");
        return true;
    }
}
