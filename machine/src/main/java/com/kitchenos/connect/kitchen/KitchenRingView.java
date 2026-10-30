package com.kitchenos.connect.kitchen;

import android.app.Activity;
import android.graphics.*;
import android.view.View;
import com.kitchenos.common.Ui;

/** Original plan-ring geometry; the centre truthfully counts this day's planned dishes. */
public final class KitchenRingView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int count;
    public KitchenRingView(Activity a,int count,Runnable open){super(a);this.count=count;setContentDescription(count+(count==1?" Gericht geplant. Kochplan öffnen":" Gerichte geplant. Kochplan öffnen"));setFocusable(true);setOnClickListener(v->open.run());}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;float radius=Math.min(getWidth(),getHeight())/2f-18*scale;paint.setColor(Ui.LINE);paint.setStyle(Paint.Style.FILL);for(int i=0;i<78;i++){double angle=i*Math.PI*2/78;canvas.drawCircle(cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,2.5f*scale,paint);}paint.setColor(Ui.INK);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(38*scale);canvas.drawText(String.valueOf(count),cx,cy,paint);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setColor(Ui.MUTED);paint.setTextSize(11*scale);canvas.drawText(count==1?"Gericht für diesen Tag geplant":"Gerichte für diesen Tag geplant",cx,cy+24*scale,paint);paint.setColor(Ui.ACCENT);paint.setTextSize(13*scale);canvas.drawText("Kochplan ›",cx,cy+52*scale,paint);}
}
