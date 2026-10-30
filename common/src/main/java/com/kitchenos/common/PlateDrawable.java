package com.kitchenos.common;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Static native rendering of Kalomatik UI Kit 0.1.0 assistant-plate.svg (128-unit viewBox). */
public final class PlateDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean launcher;
    public PlateDrawable(){this(false);}
    public PlateDrawable(boolean launcher){this.launcher=launcher;}
    @Override public void draw(Canvas canvas){
        int save=canvas.save();Rect bounds=getBounds();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/(launcher?102f:128f),bounds.height()/(launcher?102f:128f));if(launcher)canvas.translate(-13,-13);
        int orange=launcher?Ui.ACCENT:0xfff26835;
        Path silhouette=new Path();silhouette.addCircle(64,64,51,Path.Direction.CW);
        float[][] bites={{87,16},{104,27},{116,43}};
        for(float[] bite:bites){Path cut=new Path();cut.addCircle(bite[0],bite[1],13,Path.Direction.CW);silhouette.op(cut,Path.Op.DIFFERENCE);}
        canvas.clipPath(silhouette);paint.setStyle(Paint.Style.FILL);paint.setColor(orange);canvas.drawCircle(64,64,51,paint);
        paint.setColor(launcher?(Ui.dark?0xff212422:0xfff5f5f3):0xfffff8ee);canvas.drawCircle(64,64,41,paint);
        paint.setColor(orange);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);for(float[] bite:bites)canvas.drawCircle(bite[0],bite[1],13,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(42,51,57,77,5,5,paint);canvas.drawRoundRect(71,51,86,77,5,5,paint);
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
