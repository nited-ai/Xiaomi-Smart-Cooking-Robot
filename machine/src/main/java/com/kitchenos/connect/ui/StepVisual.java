package com.kitchenos.connect.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.kitchenos.common.Ui;

/** Instruction illustration, deliberately distinct from a photograph of a finished dish. */
public final class StepVisual extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    public StepVisual(String kind){this.kind=kind;}
    private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
    @Override public void draw(Canvas target){
        Canvas c=target;int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/720f,getBounds().height()/210f);c.drawColor(Ui.CARD);
        p.setColor(Ui.dark?0xff303a32:0xffe9f0e8);p.setStyle(Paint.Style.FILL);c.drawOval(160,145,560,190,p);
        p.setColor(Ui.ACCENT);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        if(kind.equals("weigh")){
            c.drawRoundRect(240,118,480,170,14,14,p);c.drawRoundRect(324,136,396,157,5,5,p);c.drawArc(263,25,457,130,0,180,false,p);line(c,263,77,457,77);line(c,292,46,292,66);line(c,339,32,339,56);line(c,386,43,386,64);line(c,427,34,427,59);
        }else if(kind.equals("machine")){
            c.drawRoundRect(265,67,455,165,22,22,p);c.drawArc(270,35,450,85,180,180,false,p);line(c,250,69,470,69);c.drawCircle(360,118,25,p);line(c,360,102,360,119);line(c,360,119,371,125);line(c,280,95,280,141);line(c,440,95,440,141);c.drawArc(338,9,382,39,190,160,false,p);
        }else if(kind.equals("rest")){
            c.drawCircle(360,99,67,p);line(c,360,51,360,101);line(c,360,101,391,113);line(c,343,20,377,20);line(c,360,20,360,31);
        }else if(kind.equals("finish")){
            c.drawOval(252,51,468,161,p);c.drawOval(275,65,445,143,p);line(c,221,55,221,151);line(c,208,55,208,83);line(c,234,55,234,83);line(c,208,83,234,83);line(c,496,55,496,151);c.drawOval(485,48,507,89,p);p.setStyle(Paint.Style.FILL);p.setColor(0xff89a884);c.drawOval(310,83,360,122,p);p.setColor(0xffeea35d);c.drawOval(361,79,408,117,p);
        }else{
            c.drawRoundRect(220,59,500,161,16,16,p);c.drawCircle(240,79,5,p);line(c,370,40,427,101);line(c,427,101,405,122);line(c,405,122,348,61);line(c,348,61,370,40);c.drawCircle(299,112,17,p);c.drawCircle(332,123,12,p);line(c,278,143,320,143);
        }
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int value){p.setAlpha(value);}
    @Override public void setColorFilter(ColorFilter value){p.setColorFilter(value);}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
