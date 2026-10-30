package com.kitchenos.connect.ui;

import android.animation.*;
import android.view.*;
import android.widget.FrameLayout;

/** Two real, already measured pages follow the finger. No bitmap or placeholder page. */
final class RootPageDrag {
    private final View outgoing,incoming;
    private final FrameLayout host;
    private final Runnable committed,cancelled;
    private final int width,direction;
    private ValueAnimator animation;
    private boolean finished;
    private float offset;
    RootPageDrag(FrameLayout host,View outgoing,View incoming,int direction,Runnable committed,Runnable cancelled){
        this.host=host;this.outgoing=outgoing;this.incoming=incoming;this.direction=direction;this.committed=committed;this.cancelled=cancelled;
        width=Math.max(1,host.getWidth());int height=Math.max(1,host.getHeight());
        if(outgoing.getParent()!=null)((android.view.ViewGroup)outgoing.getParent()).removeView(outgoing);
        host.addView(outgoing,new FrameLayout.LayoutParams(-1,-1));
        incoming.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));incoming.layout(0,0,width,height);
        outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        incoming.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);drag(0);
    }
    void drag(float value){if(finished||animation!=null)return;offset=direction<0?Math.max(-width,Math.min(0,value)):Math.min(width,Math.max(0,value));place();}
    float offset(){return offset;}
    boolean settling(){return animation!=null;}
    private void place(){outgoing.setTranslationX(offset);incoming.setTranslationX(offset-direction*width);}
    void release(boolean commit){release(commit,0);}
    void release(boolean commit,float velocity){
        if(finished||animation!=null)return;
        settle(commit&&RootSwipeDecision.accept(offset,width,direction,velocity,host.getResources().getDisplayMetrics().density));
    }
    void complete(){if(!finished&&animation==null)settle(true);}
    private void settle(boolean accepted){
        animation=ValueAnimator.ofFloat(offset,accepted?direction*width:0);
        animation.setDuration(ValueAnimator.areAnimatorsEnabled()?200:0);animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.addUpdateListener(a->{offset=(float)a.getAnimatedValue();place();});
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){finish(accepted);}});animation.start();
    }
    void abort(){if(finished)return;if(animation!=null){animation.removeAllListeners();animation.cancel();}finish(false);}
    private void finish(boolean accepted){if(finished)return;finished=true;host.removeView(outgoing);outgoing.setTranslationX(0);incoming.setTranslationX(0);incoming.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);if(accepted)committed.run();else cancelled.run();}
}
