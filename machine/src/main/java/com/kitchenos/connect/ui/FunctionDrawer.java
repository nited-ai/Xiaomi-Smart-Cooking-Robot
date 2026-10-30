package com.kitchenos.connect.ui;

import android.animation.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;

/** Bottom sheet with a dedicated handle, so scrolling its list cannot dismiss it. */
final class FunctionDrawer extends FrameLayout {
    private final LinearLayout panel;
    private final Runnable closed;
    private final int travel;
    private final View origin,list;
    private final View originHandle;
    private final FunctionHandle handle;
    private float startY,startOffset;
    private ValueAnimator animation;
    private boolean disposed;
    FunctionDrawer(Context context,View list,int height,View origin,Runnable closed){
        super(context);this.closed=closed;this.origin=origin;this.list=list;originHandle=origin==null?null:origin.findViewWithTag("navigation.functions");if(originHandle!=null)originHandle.setVisibility(INVISIBLE);travel=Math.max(1,height);setTag("navigation.functions.sheet");setClickable(true);
        panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Ui.BG);addView(panel,new LayoutParams(-1,-1));
        handle=new FunctionHandle(context);handle.setTag("navigation.functions.close");handle.setOnClickListener(v->close());panel.addView(handle,new LinearLayout.LayoutParams(-1,Ui.dp(context,42)));
        handle.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startY=e.getRawY();startOffset=panel.getTranslationY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){position(startOffset+e.getRawY()-startY);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){if(Math.abs(e.getRawY()-startY)<Ui.dp(context,8))v.performClick();else settle(panel.getTranslationY()>travel*.22f);return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){settle(false);return true;}return true;});
        ScrollView scroll=new ScrollView(context);scroll.setTag("functions.overview.scroll");scroll.setVerticalFadingEdgeEnabled(false);scroll.setClipToPadding(false);scroll.addView(list);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));position(travel);
    }
    void dragOpen(float distance){position(travel-Math.max(0,distance));}
    void releaseOpen(boolean commit){settle(!commit||travel-panel.getTranslationY()<Math.min(travel*.22f,Ui.dp(getContext(),100)));}
    void open(){settle(false);}
    void close(){settle(true);}
    void dispose(){if(disposed)return;disposed=true;if(animation!=null){animation.removeAllListeners();animation.cancel();}if(origin!=null)origin.setAlpha(1);if(originHandle!=null)originHandle.setVisibility(VISIBLE);if(getParent()!=null)((android.view.ViewGroup)getParent()).removeView(this);closed.run();}
    private void position(float value){float offset=Math.max(0,Math.min(travel,value)),progress=1-offset/travel;panel.setTranslationY(offset);handle.expansion(progress);list.setAlpha(progress);panel.getBackground().setAlpha(Math.round(255*progress));if(origin!=null)origin.setAlpha(1-progress);}
    private void settle(boolean close){if(disposed)return;if(animation!=null){animation.removeAllListeners();animation.cancel();}animation=ValueAnimator.ofFloat(panel.getTranslationY(),close?travel:0);animation.setDuration(ValueAnimator.areAnimatorsEnabled()?200:0);animation.setInterpolator(new android.view.animation.DecelerateInterpolator());animation.addUpdateListener(a->position((float)a.getAnimatedValue()));animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){animation=null;if(close)dispose();}});animation.start();}
}
