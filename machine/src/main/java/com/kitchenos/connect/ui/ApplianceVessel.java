package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import com.kitchenos.common.Ui;

/** Original, schematic accessory artwork. A fill fraction is illustrative, never a measured volume. */
public final class ApplianceVessel extends Drawable {
    public enum Part { BOWL, CLOSED_BOWL, LID, STEAMER, BASKET, KNIFE, STIRRER, SPATULA }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Part part;
    private float fill;
    private int alpha=255;
    public ApplianceVessel(Part part){this(part,0);}
    public ApplianceVessel(Part part,float fillFraction){if(part==null)throw new IllegalArgumentException("part");this.part=part;setFillFraction(fillFraction);}
    public float fillFraction(){return fill;}
    public void setFillFraction(float fraction){if(Float.isNaN(fraction)||Float.isInfinite(fraction))throw new IllegalArgumentException("fillFraction");fill=Math.max(0,Math.min(1,fraction));invalidateSelf();}
    /** Short one-off transition; caller keeps normal ingredient amounts and safety limits in text. */
    public void animateFillTo(View owner,float fraction){
        if(Float.isNaN(fraction)||Float.isInfinite(fraction))throw new IllegalArgumentException("fillFraction");
        float end=Math.max(0,Math.min(1,fraction));
        if(!owner.isAttachedToWindow()||Settings.Global.getFloat(owner.getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)<=0){setFillFraction(end);return;}
        ValueAnimator animator=ValueAnimator.ofFloat(fill,end);animator.setDuration(360);animator.addUpdateListener(a->{if(owner.isAttachedToWindow())setFillFraction((float)a.getAnimatedValue());else animator.cancel();});animator.start();
    }
    public static ImageView view(Context context,Part part,float fraction){ImageView image=new ImageView(context);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(new ApplianceVessel(part,fraction));image.setContentDescription(label(part)+" · schematische Darstellung"+(part==Part.BOWL&&fraction>0?", beispielhafter Füllstand":""));return image;}
    public static String label(Part part){switch(part){case LID:return "Deckel";case STEAMER:return "Dampfaufsatz";case BASKET:return "Gareinsatz";case KNIFE:return "Messer";case STIRRER:return "Rühraufsatz";case SPATULA:return "Spatel";default:return "Mixtopf";}}
    private void color(int color){p.setShader(null);p.setColor(color);p.setAlpha(alpha);p.setStyle(Paint.Style.FILL);}
    private void metal(float left,float right,boolean dark){p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);p.setShader(new LinearGradient(left,0,right,0,new int[]{dark?0xff646e6b:0xff929b98,0xfff0f2ee,0xffa8b1ad,0xffe5e9e4,dark?0xff4c5652:0xff737d77},new float[]{0,.24f,.53f,.77f,1},Shader.TileMode.CLAMP));}
    private void line(Canvas c,Path path,int color,float width){color(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    @Override public void draw(Canvas canvas){
        int save=canvas.save();float scale=Math.min(getBounds().width()/600f,getBounds().height()/400f)*(part==Part.KNIFE?1.65f:1f);canvas.translate(getBounds().exactCenterX()-300*scale,getBounds().exactCenterY()-200*scale);canvas.scale(scale,scale);
        color(Ui.dark?0x36000000:0x14000000);cOval(canvas,135,335,465,370);
        switch(part){case BOWL:bowl(canvas);break;case CLOSED_BOWL:bowl(canvas);canvas.save();canvas.translate(85,-20);canvas.scale(.72f,.55f);lid(canvas);canvas.restore();break;case LID:lid(canvas);break;case STEAMER:steamer(canvas);break;case BASKET:basket(canvas);break;case KNIFE:knife(canvas);break;case STIRRER:stirrer(canvas);break;case SPATULA:spatula(canvas);break;}
        canvas.restoreToCount(save);
    }
    private void cOval(Canvas c,float l,float t,float r,float b){c.drawOval(l,t,r,b,p);}
    private void bowl(Canvas c){
        // C3 reference: open cylindrical black vessel, stainless interior, single handle on the right.
        color(0xff303633);c.drawRoundRect(411,127,486,296,25,25,p);color(0xffe8ece6);c.drawRoundRect(428,149,466,271,14,14,p);
        Path body=new Path();body.moveTo(172,102);body.cubicTo(172,104,181,285,196,320);body.cubicTo(220,357,379,357,405,320);body.cubicTo(419,282,429,108,428,102);body.close();p.setShader(new LinearGradient(172,0,428,0,new int[]{0xff202824,0xff4d514d,0xff242a26},null,Shader.TileMode.CLAMP));p.setAlpha(alpha);c.drawPath(body,p);
        Path interior=new Path();interior.moveTo(183,110);interior.lineTo(204,293);interior.cubicTo(214,335,386,335,397,293);interior.lineTo(417,110);interior.close();
        metal(183,417,true);c.drawPath(interior,p);
        color(0xff47534e);cOval(c,181,75,420,143);metal(183,417,false);cOval(c,184,79,417,140);color(0xff525e58);cOval(c,191,86,410,132);
        color(0xffd7e0d7);Path blades=new Path();blades.moveTo(300,285);blades.lineTo(260,268);blades.lineTo(247,279);blades.lineTo(293,296);blades.lineTo(277,311);blades.lineTo(292,314);blades.lineTo(307,297);blades.lineTo(346,313);blades.lineTo(354,300);blades.lineTo(313,286);blades.lineTo(329,269);blades.lineTo(315,265);blades.close();c.drawPath(blades,p);color(0xff67776b);cOval(c,291,279,309,297);
        if(fill>0){int waterSave=c.save();c.clipPath(interior);float y=304-fill*182;Path water=new Path();water.moveTo(175,y);water.lineTo(430,y);water.lineTo(430,337);water.lineTo(175,337);water.close();p.setShader(new LinearGradient(0,y,0,333,new int[]{0xff84b9b4,0xff3d7776},null,Shader.TileMode.CLAMP));p.setAlpha(alpha);c.drawPath(water,p);color(0xffa8d6ce);cOval(c,183,y-15,417,y+15);color(0x35f2f3ee);cOval(c,211,y-9,357,y+3);c.restoreToCount(waterSave);}
        // Graduations convey a proportion only. Never display invented liters or a MAX line.
        color(0xffe9eee7);p.setStrokeWidth(2);for(int i=1;i<=3;i++)c.drawLine(370,304-i*45,386,304-i*45,p);
        Path rim=new Path();rim.moveTo(181,109);rim.cubicTo(192,153,414,153,420,109);line(c,rim,0xffd5ddd5,5);
        color(0xff242b25);c.drawRoundRect(217,325,386,353,14,14,p);color(0xff4e5950);c.drawRoundRect(238,330,364,334,2,2,p);
        color(0x28ffffff);c.drawRoundRect(192,156,199,271,3,3,p);
    }
    private void lid(Canvas c){
        color(0xff303a35);cOval(c,126,180,474,294);p.setShader(new LinearGradient(0,132,0,269,new int[]{0xff6d7871,0xff252d28},null,Shader.TileMode.CLAMP));cOval(c,126,144,474,264);color(0xffa2aaa3);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);cOval(c,139,153,461,254);p.setStyle(Paint.Style.FILL);
        color(0xff18221c);cOval(c,249,166,351,215);color(0xff89988c);cOval(c,255,163,345,200);color(0xffd4dcd3);c.drawRoundRect(275,107,325,179,15,15,p);color(0xffedf1e8);cOval(c,275,99,325,125);
    }
    private void steamer(Canvas c){
        color(0xff354139);Path body=new Path();body.moveTo(131,154);body.lineTo(160,286);body.quadTo(300,354,440,286);body.lineTo(469,154);body.close();c.drawPath(body,p);color(0xff6d7b70);cOval(c,130,106,470,205);color(0xff29382c);cOval(c,147,119,453,188);color(0xff526451);cOval(c,160,130,440,184);
        color(0xff9bad96);p.setStrokeWidth(3);for(int row=0;row<3;row++)for(int col=0;col<11;col++){float x=192+col*21,y=142+row*12;c.drawLine(x,y,x+8,y,p);}color(0xff52614f);c.drawRoundRect(101,151,151,191,12,12,p);c.drawRoundRect(449,151,499,191,12,12,p);
        Path steam=new Path();steam.moveTo(269,92);steam.cubicTo(242,63,294,47,273,21);line(c,steam,0x6684a28b,6);steam.reset();steam.moveTo(327,88);steam.cubicTo(300,59,352,44,331,18);line(c,steam,0x6684a28b,6);
    }
    private void basket(Canvas c){
        Path body=new Path();body.moveTo(174,133);body.lineTo(203,295);body.quadTo(300,339,397,295);body.lineTo(426,133);body.close();metal(174,426,true);c.drawPath(body,p);color(0xff3f4b41);cOval(c,174,96,426,170);color(0xff9eada0);cOval(c,184,104,416,159);color(0xff3f4b41);cOval(c,193,109,407,154);
        color(0xff47564a);for(int row=0;row<5;row++)for(int col=0;col<8;col++){float x=211+col*25,y=181+row*22;c.drawRoundRect(x,y,x+11,y+13,4,4,p);}Path handle=new Path();handle.moveTo(228,114);handle.cubicTo(216,28,385,28,373,113);line(c,handle,0xff9eaaa0,12);
    }
    private void knife(Canvas c){
        c.save();c.rotate(-17,300,208);Path blade=new Path();blade.moveTo(286,198);blade.cubicTo(207,119,151,169,135,128);blade.cubicTo(161,228,235,257,296,223);blade.close();metal(130,330,false);c.drawPath(blade,p);c.rotate(180,300,208);c.drawPath(blade,p);c.restore();
        color(0xff47534b);cOval(c,264,204,336,247);metal(272,328,true);c.drawRoundRect(272,149,328,228,13,13,p);color(0xffe1e7df);cOval(c,272,136,328,168);color(0xff68746b);cOval(c,284,145,316,158);
    }
    private void stirrer(Canvas c){
        color(0xff48574a);c.drawRoundRect(282,90,318,305,17,17,p);Path wing=new Path();wing.moveTo(281,133);wing.cubicTo(221,121,177,138,158,228);wing.cubicTo(192,233,245,269,280,289);wing.close();c.drawPath(wing,p);c.save();c.scale(-1,1,300,0);c.drawPath(wing,p);c.restore();
        color(0xff9baa98);p.setStrokeWidth(5);for(int i=0;i<4;i++){float y=158+i*27;c.drawLine(200,y,263,y+34,p);c.drawLine(337,y+34,400,y,p);}color(0xffb3beb0);cOval(c,283,82,317,105);
    }
    private void spatula(Canvas c){
        c.save();c.rotate(32,300,205);Path head=new Path();head.moveTo(247,85);head.quadTo(300,64,353,85);head.lineTo(345,196);head.quadTo(300,218,255,196);head.close();color(0xff758c70);c.drawPath(head,p);color(0xffb0bda8);p.setStrokeWidth(3);c.drawLine(261,88,268,185,p);color(0xff354735);c.drawRoundRect(285,180,315,337,14,14,p);color(0xff81917a);cOval(c,290,308,310,328);c.restore();
    }
    @Override public void setAlpha(int alpha){this.alpha=Math.max(0,Math.min(255,alpha));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 600;}
    @Override public int getIntrinsicHeight(){return 400;}
}
