package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.kitchenos.common.Ui;

/** One quiet seam and chevron, shared by the collapsed shelf and expanding sheet. */
final class FunctionHandle extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float expansion;
    FunctionHandle(Context context){super(context);setClickable(true);setFocusable(true);setContentDescription("Alle Funktionen öffnen");}
    void expansion(float value){expansion=value;setContentDescription(value>.5f?"Funktionsübersicht schließen":"Alle Funktionen öffnen");invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,unit=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(unit);
        paint.setShader(new LinearGradient(0,0,getWidth(),0,new int[]{Color.TRANSPARENT,Ui.LINE,Ui.LINE,Color.TRANSPARENT},new float[]{0,.3f,.7f,1},Shader.TileMode.CLAMP));
        canvas.drawLine(0,cy,cx-28*unit,cy,paint);canvas.drawLine(cx+28*unit,cy,getWidth(),cy,paint);paint.setShader(null);
        paint.setColor(isPressed()||isSelected()||isFocused()?Ui.ACCENT:Ui.MUTED);paint.setStrokeWidth(2*unit);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        float direction=1-2*expansion;Path arrow=new Path();arrow.moveTo(cx-8*unit,cy+3*unit*direction);arrow.lineTo(cx,cy-3*unit*direction);arrow.lineTo(cx+8*unit,cy+3*unit*direction);canvas.drawPath(arrow,paint);
    }
}
