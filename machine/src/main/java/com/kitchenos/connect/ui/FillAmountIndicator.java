package com.kitchenos.connect.ui;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Amount guide beside the original vessel photo; no claim of a measured water height. */
public final class FillAmountIndicator extends View {
    private final Paint paint=new Paint(3);private final float fraction;private final int milliliters;
    public FillAmountIndicator(Context context,int ml,int capacity){super(context);milliliters=ml;fraction=Math.max(0,Math.min(1,ml/(float)capacity));setContentDescription(ml+" Milliliter Wasser · Mengenanzeige schematisch");}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight(),left=w*.35f,right=w*.65f,top=h*.08f,bottom=h*.75f;paint.setColor(0xffcbd4cc);canvas.drawRoundRect(left,top,right,bottom,12,12,paint);paint.setColor(0xff488780);canvas.drawRoundRect(left,bottom-(bottom-top)*fraction,right,bottom,12,12,paint);paint.setColor(0xff35423a);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(w*.25f);canvas.drawText(milliliters+" ml",w/2,h*.93f,paint);}
}
