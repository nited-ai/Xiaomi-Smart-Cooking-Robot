package com.kitchenos.connect.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.kitchenos.common.Ui;

/** Long cooking spoon with a tapered working end, tilted clockwise 24 degrees. */
public final class CookingToolIcon extends Drawable {
    private final boolean spoon;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private int alpha=255;
    public CookingToolIcon(boolean spoon){this.spoon=spoon;}
    @Override public void draw(Canvas canvas){
        if(!spoon){ModeIcon knife=new ModeIcon("blade");knife.setBounds(getBounds());knife.setAlpha(alpha);knife.setColorFilter(new PorterDuffColorFilter(0xffef655a,PorterDuff.Mode.SRC_IN));knife.draw(canvas);return;}
        int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/40f,getBounds().height()/40f);canvas.rotate(24,20,20);
        paint.setColor(0xff63c49a);paint.setAlpha(alpha);paint.setStyle(Paint.Style.FILL);
        Path path=new Path();path.moveTo(18.9f,3);path.quadTo(20,1.4f,21.1f,3);path.lineTo(21.3f,23);path.cubicTo(21.5f,26,24.3f,28.5f,24.8f,32);path.lineTo(25,34.5f);path.quadTo(25.2f,37.5f,23,38);path.quadTo(20,38.8f,17,38);path.quadTo(14.8f,37.5f,15,34.5f);path.lineTo(15.2f,32);path.cubicTo(15.7f,28.5f,18.5f,26,18.7f,23);path.close();canvas.drawPath(path,paint);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
