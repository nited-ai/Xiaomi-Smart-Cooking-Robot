package com.kitchenos.connect.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import com.kitchenos.common.Ui;

/** Curved four-blade outline with a center hole, without motion marks. */
final class TopViewBladeIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blade=new Path();
    private int alpha=255;
    TopViewBladeIcon(){
        blade.moveTo(7,22);blade.lineTo(23,17);
        blade.cubicTo(35,25,40,20,44,5);blade.lineTo(49,23);
        blade.cubicTo(41,35,46,40,61,44);blade.lineTo(43,49);
        blade.cubicTo(31,41,26,46,22,61);blade.lineTo(17,43);
        blade.cubicTo(25,31,20,26,7,22);blade.close();
    }
    @Override public void draw(Canvas canvas){
        int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/64f,getBounds().height()/64f);
        paint.setColor(Ui.ACCENT);paint.setAlpha(alpha);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(blade,paint);canvas.drawCircle(34,33,5,paint);canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
