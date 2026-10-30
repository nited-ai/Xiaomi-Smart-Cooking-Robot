package com.kitchenos.connect.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Counterclockwise mixing mark; the legacy class name keeps existing callers compatible. */
final class WheelSpoonIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path head=new Path();private int alpha=255;
    WheelSpoonIcon(){
        double a=Math.toRadians(-165);float x=32+21*(float)Math.cos(a),y=32+21*(float)Math.sin(a),dx=(float)Math.sin(a),dy=-(float)Math.cos(a);
        head.moveTo(x-8*dx-6*dy,y-8*dy+6*dx);head.lineTo(x,y);head.lineTo(x-8*dx+6*dy,y-8*dy-6*dx);
    }
    @Override public void draw(Canvas canvas){
        int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/64f,getBounds().height()/64f);
        paint.setColor(0xff63c49a);paint.setAlpha(alpha);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(11,11,53,53,135,-300,false,paint);canvas.drawPath(head,paint);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
