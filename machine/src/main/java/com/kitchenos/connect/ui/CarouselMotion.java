package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Frame-clock motion. Touch, knob selection and obscured windows hold the current position. */
final class CarouselMotion implements Choreographer.FrameCallback {
    private static final class Interaction {boolean held;long resumeAt;}
    private static final java.util.WeakHashMap<android.os.IBinder,Interaction> WINDOWS=new java.util.WeakHashMap<>();
    private static Interaction interaction(View view){android.os.IBinder token=view.getWindowToken();if(token==null)return null;Interaction value=WINDOWS.get(token);if(value==null){value=new Interaction();WINDOWS.put(token,value);}return value;}
    static void touchWindow(View view,int action){
        Interaction value=interaction(view);if(value==null)return;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE)value.held=true;
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){value.held=false;value.resumeAt=SystemClock.uptimeMillis()+1800;}
    }
    private final ViewGroup view;
    private final float pixelsPerSecond;
    private final BooleanSupplier ready;
    private final Consumer<Float> advance;
    private final AccessibilityManager accessibility;
    private Consumer<Float> frameStep;
    private boolean immediateResume;
    private boolean enabled=true,posted,touching;
    private long previousFrame,resumeAt;

    CarouselMotion(ViewGroup view,float dpPerSecond,BooleanSupplier ready,Consumer<Float> advance){
        this.view=view;this.ready=ready;this.advance=advance;
        pixelsPerSecond=dpPerSecond*view.getResources().getDisplayMetrics().density;
        accessibility=(AccessibilityManager)view.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    }
    void enabled(boolean value){enabled=value;refresh();}
    void frameStep(Consumer<Float> step){frameStep=step;}
    void continuousAfterTouch(){immediateResume=true;}
    void stop(){Choreographer.getInstance().removeFrameCallback(this);posted=false;previousFrame=0;if(touching)touchWindow(view,MotionEvent.ACTION_CANCEL);touching=false;}
    void refresh(){
        boolean active=enabled&&view.isAttachedToWindow()&&view.isShown()&&view.getWindowVisibility()==View.VISIBLE&&view.hasWindowFocus();
        if(!active){stop();return;}
        if(!posted){posted=true;Choreographer.getInstance().postFrameCallback(this);}
    }
    void touch(int action){
        touchWindow(view,action);
        if(action==MotionEvent.ACTION_DOWN){touching=true;previousFrame=0;}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;interact();}
    }
    void interact(){resumeAt=SystemClock.uptimeMillis()+1800;Interaction value=interaction(view);if(value!=null)value.resumeAt=resumeAt;previousFrame=0;}
    private boolean selected(View child){
        if(child.isSelected())return true;
        if(child instanceof ViewGroup)for(int i=0;i<((ViewGroup)child).getChildCount();i++)if(selected(((ViewGroup)child).getChildAt(i)))return true;
        return false;
    }
    @Override public void doFrame(long frameTimeNanos){
        posted=false;
        Interaction interaction=interaction(view);long now=SystemClock.uptimeMillis();
        if(!touching&&(immediateResume||now>=resumeAt)&&(interaction==null||!interaction.held&&(immediateResume||now>=interaction.resumeAt))&&ready.getAsBoolean()&&ValueAnimator.areAnimatorsEnabled()
                &&(accessibility==null||!accessibility.isTouchExplorationEnabled())&&!selected(view)){
            // Never catch up after suspend, layout work or a late frame with a visible jump.
            if(previousFrame!=0){float seconds=Math.min(.05f,(frameTimeNanos-previousFrame)/1_000_000_000f);if(frameStep!=null)frameStep.accept(seconds);else advance.accept(pixelsPerSecond*seconds);}
            previousFrame=frameTimeNanos;
        }else previousFrame=0;
        refresh();
    }
}
