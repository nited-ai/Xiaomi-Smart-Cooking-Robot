package com.kitchenos.connect.ui;

import android.content.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.*;

/** C3 firmware consumes F3 when automatic display sleep is enabled, but always
 * broadcasts its release. Route that trusted signal through the focused window. */
public final class C3RotaryBroadcast {
    static final String ACTION="android.intent.action.IR_F3_Event";
    private static final String INJECT_EVENTS="android.permission.INJECT_EVENTS";
    private static final ThreadLocal<Boolean> delivering=new ThreadLocal<>();
    private static String firmwareAction(Context context){
        int id=context.getResources().getIdentifier("cvte_ir_f3_event","string","android");
        if(id==0)return "";
        try{return ACTION.equals(context.getString(id))?ACTION:"";}catch(Exception ignored){return "";}
    }
    public static void bind(Window window){if(window!=null)bind(window,firmwareAction(window.getContext()));}
    // Explicit action also lets emulator instrumentation exercise the real broadcast path.
    static void bind(Window window,String action){
        if(window==null||!ACTION.equals(action))return;
        View decor=window.getDecorView();
        if(decor.getTag(com.kitchenos.connect.R.id.c3_rotary_binding)!=null)return;
        Binding binding=new Binding(window);decor.setTag(com.kitchenos.connect.R.id.c3_rotary_binding,binding);
        decor.addOnAttachStateChangeListener(binding);if(decor.isAttachedToWindow())binding.onViewAttachedToWindow(decor);
    }
    static boolean consumesRawClockwise(Window window){
        return !Boolean.TRUE.equals(delivering.get())&&window!=null&&
            window.getDecorView().getTag(com.kitchenos.connect.R.id.c3_rotary_binding)!=null;
    }
    private static final class Binding extends BroadcastReceiver implements View.OnAttachStateChangeListener {
        private final Window window;private boolean registered;private long lastEventTime;
        Binding(Window window){this.window=window;}
        @Override public void onViewAttachedToWindow(View view){
            if(registered)return;Context context=window.getContext();IntentFilter filter=new IntentFilter(ACTION);
            // Only the system (or privileged input-injection tools) may send knob input.
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(this,filter,INJECT_EVENTS,null,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(this,filter,INJECT_EVENTS,null);
            registered=true;
        }
        @Override public void onViewDetachedFromWindow(View view){if(registered){window.getContext().unregisterReceiver(this);registered=false;}}
        @Override public void onReceive(Context context,Intent intent){
            View decor=window.getDecorView();
            if(!ACTION.equals(intent.getAction())||!registered||!decor.isShown()||!decor.hasWindowFocus()||StandbyController.get(context).blocksInput())return;
            Window.Callback callback=window.getCallback();if(callback==null)return;
            long now=Math.max(SystemClock.uptimeMillis(),lastEventTime+1);lastEventTime=now;Boolean previous=delivering.get();delivering.set(true);
            try{callback.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_F3,0));}
            finally{if(previous==null)delivering.remove();else delivering.set(previous);}
        }
    }
    private C3RotaryBroadcast(){}
}
