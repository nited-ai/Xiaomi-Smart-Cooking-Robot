package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.widget.ScrollView;
import com.kitchenos.common.Ui;

/** Quiet, persistent position marker; uses the same native scroll physics for touch and knob. */
public final class KitchenScroll extends ScrollView {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Runnable motionListener;
    public void onMotion(Runnable listener){motionListener=listener;}
    public KitchenScroll(Context context){super(context);setFillViewport(true);setVerticalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setDefaultFocusHighlightEnabled(false);setSmoothScrollingEnabled(true);}
    @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){super.onScrollChanged(l,t,oldl,oldt);if(motionListener!=null&&t!=oldt)motionListener.run();invalidate();}
    @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(getChildCount()==0)return;float density=getResources().getDisplayMetrics().density,view=getHeight(),content=getChildAt(0).getHeight();if(content<=view+2)return;float margin=14*density,track=view-2*margin,thumb=Math.max(38*density,track*view/content),top=margin+(track-thumb)*getScrollY()/(content-view);paint.setColor(Ui.LINE);canvas.drawRoundRect(getWidth()-6*density,getScrollY()+margin,getWidth()-3*density,getScrollY()+view-margin,2*density,2*density,paint);paint.setColor(Ui.MUTED);canvas.drawRoundRect(getWidth()-6*density,getScrollY()+top,getWidth()-3*density,getScrollY()+top+thumb,2*density,2*density,paint);}
}
