package org.aliveclean;

import android.content.Context;
import android.widget.FrameLayout;

/** A clock face owned by the existing native AOD display window. */
abstract class AodClockFace extends FrameLayout {
    AodClockFace(Context context){super(context);}
    abstract void active(boolean value);
    abstract void tick(long now);
    abstract void refresh(long now);
    abstract int notificationTop();
}
