package com.kitchenos.connect.ui;

import android.app.Activity;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

/** Bounded startup diagnostics; contains timings only, never account or recipe data. */
final class StartupTrace {
    static void mark(String stage,long activityStart) {
        long now=SystemClock.uptimeMillis();
        Log.i("KalomatikStartup",stage+" activityMs="+(now-activityStart)
                +" processMs="+(now-Process.getStartUptimeMillis()));
    }

    static void afterFirstDraw(Activity activity,long activityStart,Runnable refresh) {
        View decor=activity.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            private boolean posted;
            @Override public void onDraw() {
                if(posted)return;
                posted=true;
                // Removal during onDraw is forbidden. Post after this traversal so
                // catalogue responses cannot rebuild the UI before its first frame.
                decor.post(()->{
                    if(decor.getViewTreeObserver().isAlive())
                        decor.getViewTreeObserver().removeOnDrawListener(this);
                    if(activity.isDestroyed()||activity.isFinishing())return;
                    mark("first-draw",activityStart);
                    refresh.run();
                });
            }
        });
    }

    private StartupTrace() {}
}
