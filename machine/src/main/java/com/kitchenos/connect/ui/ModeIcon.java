package com.kitchenos.connect.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.kitchenos.common.Ui;

/** Appliance function pictograms, drawn in the Kalomatik stroke/color system. */
public final class ModeIcon extends Drawable {
    private final String mode;private final Paint p=new Paint(3);private Canvas c;private int alpha=255;
    public ModeIcon(String mode){this.mode=mode;}
    private void line(float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,p);}
    private void circle(float x,float y,float r){c.drawCircle(x,y,r,p);}
    private void round(float l,float t,float r,float b,float radius){c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void arc(float l,float t,float r,float b,float start,float sweep){c.drawArc(l,t,r,b,start,sweep,false,p);}
    private void pot(){round(12,25,52,51,6);line(8,29,12,29);line(52,29,56,29);line(11,22,53,22);round(26,16,38,22,3);}
    private void steam(){for(int x:new int[]{22,32,42}){Path a=new Path();a.moveTo(x,19);a.cubicTo(x-7,13,x+7,11,x,5);c.drawPath(a,p);}}
    private void drop(float x,float y){Path path=new Path();path.moveTo(x,y-11);path.cubicTo(x+16,y+3,x+9,y+13,x,y+13);path.cubicTo(x-9,y+13,x-16,y+3,x,y-11);c.drawPath(path,p);}
    private void egg(float x,float top){Path shape=new Path();shape.moveTo(x,top);shape.cubicTo(x+7,top,x+12,top+13,x+12,top+22);shape.cubicTo(x+12,top+36,x-12,top+36,x-12,top+22);shape.cubicTo(x-12,top+13,x-7,top,x,top);shape.close();c.drawPath(shape,p);}
    private void blade(){int save=c.save();for(int i=0;i<4;i++){Path b=new Path();b.moveTo(29,29);b.lineTo(37,10);b.lineTo(48,7);b.lineTo(40,27);b.lineTo(35,35);c.drawPath(b,p);c.rotate(90,32,32);}p.setStyle(Paint.Style.FILL);circle(32,32,6);p.setStyle(Paint.Style.STROKE);c.restoreToCount(save);}
    @Override public void draw(Canvas canvas){c=canvas;int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/64f,getBounds().height()/64f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4.4f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Ui.ACCENT);p.setAlpha(alpha);
        if(java.util.Arrays.asList("stir","whip","water","puree","steam","blade","chop","mix","rice","lidless").contains(mode)){p.setStrokeWidth(4f);AppliancePictograms.draw(c,p,mode.equals("chop")?"blade":mode);c.restoreToCount(save);return;}
        if(mode.equals("scale")){c.scale(64f/24,64f/24);p.setStrokeWidth(1.8f);KitchenGlyphs.draw(c,p,"scale");c.restoreToCount(save);return;}
        String glyph=null;switch(mode){case "manual":glyph="sliders-horizontal";break;case "eggs":glyph="egg";break;case "dough":glyph="croissant";break;case "mix":glyph="blend";break;case "puree":glyph="soup";break;case "emulsify":glyph="droplets";break;case "grind":glyph="nut";break;case "ice":glyph="snowflake";break;case "thicken":case "warm":glyph="soup";break;case "caramel":case "fry":glyph="flame";break;case "slow":glyph="timer";break;case "sousvide":glyph="thermometer";break;case "ferment":glyph="sprout";break;case "clean":glyph="bubbles";break;}
        if(glyph!=null){c.scale(64f/24,64f/24);p.setStrokeWidth(1.8f);IconGlyphs.draw(c,p,glyph);c.restoreToCount(save);return;}
        switch(mode){
            case "lidless":round(12,27,52,53,6);line(8,31,12,31);line(52,31,56,31);c.drawOval(12,23,52,31,p);steam();break;
            case "manual":round(9,9,55,55,10);for(int x:new int[]{21,43}){line(x,18,x,46);circle(x,x==21?27:38,5);}break;
            case "scale":p.setStrokeWidth(4);line(32,8,32,56);line(20,56,44,56);line(8,18,56,18);Path scales=new Path();scales.moveTo(14,18);scales.lineTo(5,39);scales.lineTo(23,39);scales.close();scales.moveTo(50,18);scales.lineTo(41,39);scales.lineTo(59,39);scales.close();c.drawPath(scales,p);break;
            case "eggs":{int eggSave=c.save();c.rotate(-14,23,34);egg(23,12);c.restoreToCount(eggSave);c.save();c.rotate(12,43,34);egg(43,20);c.restore();break;}
            case "water":{Path bowl=new Path();bowl.moveTo(12,25);bowl.lineTo(17,52);bowl.quadTo(32,59,47,52);bowl.lineTo(52,25);c.drawPath(bowl,p);c.drawOval(12,20,52,30,p);round(51,28,60,46,4);Path water=new Path();water.moveTo(17,37);water.cubicTo(25,31,27,43,35,37);water.cubicTo(41,32,44,39,48,36);c.drawPath(water,p);circle(25,46,2);circle(38,46,2);steam();break;}
            case "rice":arc(10,15,54,62,0,180);line(10,38,54,38);for(int x:new int[]{20,30,40}){line(x,28,x+5,24);line(x+3,35,x+8,31);}break;
            case "dough":round(14,24,50,43,6);line(7,33,14,33);line(50,33,57,33);arc(23,39,41,56,0,180);break;
            case "steam":round(12,26,52,40,4);round(12,40,52,53,4);line(10,24,54,24);steam();break;
            case "turbo":case "chop":case "blade":blade();break;
            case "mix":round(21,9,46,42,5);line(24,42,18,55);line(43,42,49,55);line(18,55,49,55);line(21,15,46,15);line(27,27,39,33);line(39,27,27,33);break;
            case "puree":arc(9,18,55,61,0,180);line(9,39,55,39);line(32,8,32,36);round(25,29,39,38,3);break;
            case "stir":{int layer=c.save();c.rotate(-12,32,32);c.drawOval(5,20,29,44,p);Path handle=new Path();handle.moveTo(27,28);handle.lineTo(56,28);handle.cubicTo(61,28,61,36,56,36);handle.lineTo(27,36);c.drawPath(handle,p);c.restoreToCount(layer);break;}
            case "whip":p.setStyle(Paint.Style.FILL);round(28,2,36,27,4);p.setStyle(Paint.Style.STROKE);Path whisk=new Path();whisk.moveTo(29,24);whisk.cubicTo(18,34,17,43,21,50);whisk.cubicTo(26,59,38,59,43,50);whisk.cubicTo(47,43,46,34,35,24);c.drawPath(whisk,p);c.drawOval(26,24,38,55,p);line(32,24,32,55);break;
            case "emulsify":drop(24,20);drop(43,40);line(14,52,25,52);line(46,11,54,11);line(50,7,50,15);break;
            case "grind":round(22,13,42,25,4);line(26,25,19,49);line(38,25,45,49);line(19,49,45,49);circle(32,8,3);for(int x:new int[]{24,32,40})circle(x,56,1);break;
            case "ice":round(9,26,32,49,4);round(31,12,54,35,4);line(14,32,20,31);line(36,18,43,17);break;
            case "warm":arc(10,24,54,62,0,180);line(10,43,54,43);steam();break;
            case "slow":{int layer=c.save();Path clock=new Path();clock.addCircle(43,43,14,Path.Direction.CW);c.clipOutPath(clock);pot();c.restoreToCount(layer);circle(43,43,13);line(43,43,43,34);line(43,43,49,46);break;}
            case "sousvide":round(12,9,42,55,4);line(12,16,42,16);c.drawOval(20,26,35,44,p);line(51,19,51,44);circle(51,49,5);break;
            case "ferment":round(19,15,45,55,5);round(17,9,47,17,3);line(21,33,43,33);circle(27,43,1);circle(37,40,1);break;
            case "clean":drop(28,23);line(43,37,54,48);round(35,44,50,56,3);line(38,49,34,54);line(43,51,39,57);break;
            default:pot();
        }c.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth(){return 64;}
    @Override public int getIntrinsicHeight(){return 64;}
    @Override public void setAlpha(int alpha){this.alpha=Math.max(0,Math.min(255,alpha));invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
