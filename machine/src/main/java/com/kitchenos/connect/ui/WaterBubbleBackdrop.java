package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Quiet decorative water motion, independent of sensors and cooking state. */
public final class WaterBubbleBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean visibleToUser;
    public WaterBubbleBackdrop(Context context){
        super(context);setTag("home.water.bubbles");setClickable(false);setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);visibleToUser=visible;if(visible)invalidate();}
    @Override protected void onDetachedFromWindow(){visibleToUser=false;super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        boolean moving=ValueAnimator.areAnimatorsEnabled();
        double seconds=moving?SystemClock.elapsedRealtime()/1000d:0;
        float density=getResources().getDisplayMetrics().density;
        float width=getWidth(),height=Math.min(getHeight(),360*density);
        for(int i=0;i<22;i++){
            double phase=(seconds/(7.8+(i%6)*1.1)+i*.61803398875)%1;
            float x=(.07f+((i*37)%89)/100f)*width+(float)Math.sin(seconds*.62+i*2.3)*8*density;
            float y=(float)(height*(1.06-phase*1.2));
            float radius=(2.5f+(i%5)*1.05f)*density;
            float fade=(float)(Math.sin(Math.PI*phase)*Math.sin(Math.PI*phase));
            // Low contrast behind the large value; brighter in the open space around it.
            boolean behindValue=x>width*.28f&&x<width*.72f&&y>90*density&&y<245*density;
            paint.setColor(0xff7eb3b6);paint.setAlpha((int)((behindValue?13:46)*fade));
            canvas.drawCircle(x,y,radius,paint);
            paint.setColor(0xffd9eeee);paint.setAlpha((int)((behindValue?12:65)*fade));
            canvas.drawCircle(x-radius*.28f,y-radius*.3f,Math.max(.7f*density,radius*.2f),paint);
        }
        if(moving&&visibleToUser&&isShown())postInvalidateDelayed(40);
    }
}
