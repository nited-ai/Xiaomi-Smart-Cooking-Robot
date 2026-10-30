package com.kitchenos.connect.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import java.util.function.IntConsumer;

/** Finite recipe row. Native scrolling tracks the finger; a row never hands off at its ends. */
final class RecipeShelf extends HorizontalScrollView {
    private final LinearLayout cards;
    private float downX,downY;private boolean horizontal,vertical;private final int stride;
    private final IntConsumer changed;
    RecipeShelf(Context context,int cardWidth,IntConsumer changed){
        super(context);this.changed=changed;stride=cardWidth+Ui.dp(context,12);setHorizontalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setFillViewport(false);
        cards=new LinearLayout(context);addView(cards,new LayoutParams(-2,-1));setTag("recipes.shelf");
    }
    void card(View view,int width){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(width,-1);size.rightMargin=Ui.dp(getContext(),12);cards.addView(view,size);}
    void restore(int position){post(()->scrollTo(Math.max(0,position),0));}
    void move(int direction){int next=Math.max(0,Math.min(cards.getChildCount()-1,Math.round((float)getScrollX()/stride)+direction));smoothScrollTo(next*stride,0);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        // Let the vertical parent see the first moving sample. Blocking it on DOWN
        // delays handoff by another touch packet (40 ms at 25 Hz).
        if(action==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();horizontal=vertical=false;}
        if(action==MotionEvent.ACTION_MOVE&&!horizontal&&!vertical){float dx=Math.abs(e.getX()-downX),dy=Math.abs(e.getY()-downY);int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(dy>slop&&dy>dx*1.25f)vertical=true;else if(dx>slop&&dx>dy*1.25f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);}}
        boolean handled=super.dispatchTouchEvent(e);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
        return handled;
    }
    // Keep native velocity tracking, deceleration and touch-to-stop. Never replace
    // fling with a snap: that discards both the release speed and repeated swipes.
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return !vertical&&super.onInterceptTouchEvent(event);}
    @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){super.onScrollChanged(l,t,oldl,oldt);changed.accept(l);}
}
