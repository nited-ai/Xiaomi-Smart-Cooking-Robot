package com.kitchenos.connect.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.kitchenos.common.Ui;

/** Original geometric interface glyphs, consistent 24-unit grid and rounded 1.8-unit stroke. */
public final class InterfaceIcon extends Drawable {
    private final String id;private final int color;private final Paint p=new Paint(3);private Canvas c;private int alpha=255;
    public InterfaceIcon(String id){this(id,Ui.INK);}public InterfaceIcon(String id,int color){this.id=id;this.color=color;}
    private void path(float... points){Path a=new Path();a.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)a.lineTo(points[i],points[i+1]);c.drawPath(a,p);}
    @Override public void draw(Canvas canvas){c=canvas;int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setAlpha(alpha);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(id.equals("chevron-down")){c.rotate(90,12,12);IconGlyphs.draw(c,p,"chevron-right");c.restoreToCount(save);return;}
        if(id.equals("send")){path(4,4,21,12,4,20,7,12,4,4);path(7,12,21,12);c.restoreToCount(save);return;}
        if(id.equals("mail")){c.drawRoundRect(2,5,22,19,2,2,p);path(2,6,12,13,22,6);c.restoreToCount(save);return;}
        if(id.equals("sun")){c.drawCircle(12,12,4,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*7.5f,12+(float)Math.sin(a)*7.5f,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}c.restoreToCount(save);return;}
        if(id.equals("moon")){Path moon=new Path();moon.moveTo(20.5f,14);moon.cubicTo(17,15,13,13,11,10);moon.cubicTo(9,7,9,4,10,2.5f);moon.cubicTo(4,3.5f,1,9,3.5f,15);moon.cubicTo(6,22,16,24,20.5f,14);c.drawPath(moon,p);c.restoreToCount(save);return;}
        if(id.equals("ingredients")){c.drawCircle(5,6,1,p);c.drawCircle(5,12,1,p);c.drawCircle(5,18,1,p);path(10,6,21,6);path(10,12,21,12);path(10,18,21,18);c.restoreToCount(save);return;}
        if(id.equals("pan")){c.drawOval(2,9,16,16,p);c.drawArc(2,10,16,20,0,180,false,p);path(2,12.5f,2,15);path(16,12.5f,16,15);path(15,10,21,4,23,6,17,13);c.restoreToCount(save);return;}
        if(id.equals("scale")){p.setStrokeWidth(2.1f);path(12,3,12,21);path(7,21,17,21);path(3,7,21,7);path(5,7,2,14,8,14,5,7);path(19,7,16,14,22,14,19,7);c.restoreToCount(save);return;}
        if(id.equals("backspace")){path(9,5,3,12,9,19,22,19,22,5,9,5);path(18,12,10,12,13,9);path(10,12,13,15);c.restoreToCount(save);return;}
        if(id.equals("oven-top-bottom")){c.drawRoundRect(3,3,21,21,2,2,p);path(7,7,17,7);path(7,17,17,17);c.restoreToCount(save);return;}
        if(id.equals("dimensions")){c.drawRoundRect(7,7,20,20,1,1,p);path(7,3,20,3);path(7,1.5f,7,4.5f);path(20,1.5f,20,4.5f);path(3,7,3,20);path(1.5f,7,4.5f,7);path(1.5f,20,4.5f,20);c.restoreToCount(save);return;}
        String glyph;
        switch(id){case "layout-grid":case "sun":case "moon":case "fan":glyph=id;break;case "home":glyph="house";break;case "recipes":glyph="book-open";break;case "modes":glyph="layout-grid";break;case "pantry":glyph="archive";break;case "plan":glyph="calendar-check";break;case "back":glyph="chevron-left";break;case "next":glyph="chevron-right";break;case "close":glyph="x";break;case "microphone":glyph="mic";break;case "send":glyph="arrow-up";break;case "temperature":glyph="thermometer";break;case "clock":glyph="clock-3";break;case "calories":glyph="flame";break;case "timer":glyph="timer";break;case "chevron-down":glyph="chevron-down";break;case "person":glyph="user-round";break;case "favorite":case "favorite-filled":glyph="heart";break;case "photo":glyph="camera";break;case "more":glyph="ellipsis";break;case "settings":glyph="sliders-horizontal";break;case "share":glyph="share-2";break;case "shift":glyph="arrow-big-up";break;case "backspace":glyph="delete";break;case "oven":case "air-fryer":glyph="microwave";break;case "scale":glyph="weight";break;case "circle":case "search":case "menu":case "check":case "plus":case "wifi":case "arrow-right":glyph=id;break;case "pan":glyph="cooking-pot";break;default:glyph="info";}
        if(id.equals("favorite-filled"))p.setStyle(Paint.Style.FILL_AND_STROKE);
        IconGlyphs.draw(c,p,glyph);
        c.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
    @Override public void setAlpha(int a){alpha=Math.max(0,Math.min(255,a));invalidateSelf();}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
