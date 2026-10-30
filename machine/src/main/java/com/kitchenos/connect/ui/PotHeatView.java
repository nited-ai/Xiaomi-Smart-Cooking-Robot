package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** One bounded completion cue, independent of the photographic player. */
public final class PotHeatView extends View {
    public static final long DURATION_MS=2800;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flame=new Path();
    private final Shader fire=new LinearGradient(0,-49,0,49,new int[]{0xffff8b37,0xffff692d,0xffff482b},null,Shader.TileMode.CLAMP);
    private long eventAt=-1;
    private final Runnable expire=()->{setVisibility(INVISIBLE);invalidate();};
    public PotHeatView(Context c){super(c);setTag("temperature.pot-warning");setVisibility(INVISIBLE);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        // Lucide "flame", ISC license in assets/licenses/lucide.txt; narrowed to the vessel interior.
        flame.moveTo(12,3);flame.quadTo(13,7,16,9.5f);flame.quadTo(19,12,19,15);
        flame.arcTo(new RectF(5,8,19,22),0,180);
        flame.arcTo(new RectF(5,10,15,20),180,36.8699f);
        flame.arcTo(new RectF(6,9.5f,11,14.5f),180,-180);
        flame.cubicTo(11,10,9.5f,9,9.5f,7);flame.quadTo(9.5f,5,12,3);flame.close();
        Matrix fit=new Matrix();fit.setScale(3.25f,5.15f);fit.postTranslate(-39,-64.375f);flame.transform(fit);
    }
    public void completion(long at){
        long age=SystemClock.elapsedRealtime()-at;
        if(at<0||age<0||age>=DURATION_MS){removeCallbacks(expire);setVisibility(INVISIBLE);return;}
        if(eventAt!=at){eventAt=at;setContentDescription("Programm beendet. Topf über 150 Grad.");}
        setVisibility(VISIBLE);removeCallbacks(expire);postDelayed(expire,DURATION_MS-age);invalidate();
    }
    @Override protected void onDraw(Canvas c){
        long age=SystemClock.elapsedRealtime()-eventAt;if(eventAt<0||age<0||age>=DURATION_MS)return;
        boolean animate=ValueAnimator.areAnimatorsEnabled();float phase=age/(float)DURATION_MS;
        // Brief visible pulses, softened at their edges, fade as one bounded event.
        float beat=age%440,pulse=beat<180?1:beat<230?1-.94f*(beat-180)/50:beat<390?.06f:.06f+.94f*(beat-390)/50;
        float alpha=(1-phase)*(animate?pulse:.8f);
        float scale=Math.min(getWidth()/300f,getHeight()/252f),bodyWidth=165f*1203/1086;
        c.save();c.translate(getWidth()/2f-(150+bodyWidth*(.1f+.8f*.38f*.04f))*scale,(getHeight()-252*scale)/2);c.scale(scale,scale);c.translate(174,147);
        paint.setShader(fire);paint.setAlpha((int)(alpha*255));c.drawPath(flame,paint);paint.setShader(null);c.restore();
        if(animate&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(50);
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(expire);super.onDetachedFromWindow();}
}
