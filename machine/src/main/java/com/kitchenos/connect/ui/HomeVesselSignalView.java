package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlState;

/** Shared native overlay above either vessel renderer. Never issues a command. */
public final class HomeVesselSignalView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hit=new RectF();
    private final Path path=new Path();
    private HomeVesselSignal signal=HomeVesselSignal.from(new ControlState(),0);
    private long phaseAt;
    private boolean pressed;
    private boolean lidOpen;
    public HomeVesselSignalView(Context context){
        super(context);setTag("home.bowl.signal");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);setContentDescription(signal.description());
    }
    public void update(ControlState state,long now){
        HomeVesselSignal next=HomeVesselSignal.from(state,now);
        boolean open=state.coverVerified&&state.coverOpen==1;
        if(next.kind==signal.kind&&next.error==signal.error&&open==lidOpen&&next.description().equals(signal.description()))return;
        lidOpen=open;
        signal=next;phaseAt=now;setContentDescription(signal.description());
        setImportantForAccessibility(signal.kind==HomeVesselSignal.Kind.NONE?IMPORTANT_FOR_ACCESSIBILITY_NO:IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(signal.kind!=HomeVesselSignal.Kind.NONE);invalidate();
    }
    private boolean canAnimate(){return isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&ValueAnimator.areAnimatorsEnabled();}
    @Override protected void onDraw(Canvas canvas){
        hit.setEmpty();if(signal.kind==HomeVesselSignal.Kind.NONE)return;
        float scale=Math.min(getWidth()/300f,getHeight()/252f),left=(getWidth()-300*scale)/2,top=(getHeight()-252*scale)/2;
        int saved=canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);
        boolean checking=signal.kind==HomeVesselSignal.Kind.CHECKING;
        int color=checking?Ui.ACCENT:signal.fault()?0xffef655a:Ui.MUTED;
        float pulse=checking&&canAnimate()?.5f+.5f*(float)Math.sin((SystemClock.elapsedRealtime()-phaseAt)*Math.PI/950):.5f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);
        if(checking){
            paint.setAlpha((int)(65+65*pulse));float spread=3*pulse;
            canvas.drawRoundRect(77-spread,66-spread,240+spread,231+spread,37,37,paint);
            paint.setAlpha(220);canvas.drawCircle(226,154,3,paint);
        }else{
            if(signal.kind==HomeVesselSignal.Kind.LID){paint.setAlpha(180);float lift=lidOpen?46:0;canvas.drawOval(75,63-lift,244,87-lift,paint);}
            if(signal.kind==HomeVesselSignal.Kind.POT){
                paint.setPathEffect(new DashPathEffect(new float[]{5,5},0));paint.setAlpha(180);
                path.reset();path.moveTo(94,91);path.lineTo(108,224);path.quadTo(149,236,190,224);path.lineTo(206,91);canvas.drawPath(path,paint);paint.setPathEffect(null);
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.CARD);paint.setAlpha(245);canvas.drawCircle(224,157,18,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setAlpha(255);canvas.drawCircle(224,157,17,paint);
            canvas.save();canvas.translate(224,157);drawIcon(canvas);canvas.restore();
        }
        paint.setAlpha(255);paint.setPathEffect(null);canvas.restoreToCount(saved);
        if(!checking)hit.set(left+199*scale,top+132*scale,left+249*scale,top+182*scale);
        if(checking&&canAnimate())postInvalidateDelayed(50);
    }
    private void drawIcon(Canvas c){
        switch(signal.kind){
            case THERMAL:
                c.drawRoundRect(-2,-10,2,3,2,2,paint);c.drawCircle(0,7,4,paint);c.drawLine(5,-7,8,-7,paint);break;
            case MOTOR:
                c.drawCircle(0,0,2,paint);for(int i=0;i<4;i++){c.save();c.rotate(i*90);c.drawLine(2,-2,9,-7,paint);c.drawLine(9,-7,7,1,paint);c.restore();}break;
            case SCALE:
                c.drawRoundRect(-9,-7,9,9,3,3,paint);c.drawArc(-5,-4,5,5,195,150,false,paint);c.drawLine(0,1,3,-2,paint);break;
            case CONNECTION:
                c.drawArc(-10,-10,10,10,220,100,false,paint);c.drawArc(-6,-6,6,6,220,100,false,paint);c.drawLine(-8,9,8,-8,paint);break;
            case LID:
                c.drawArc(-9,-5,9,7,180,180,false,paint);c.drawLine(-10,2,10,2,paint);c.drawLine(-2,-8,2,-8,paint);break;
            case POT:
                c.drawLine(-8,-4,-6,8,paint);c.drawLine(-6,8,6,8,paint);c.drawLine(6,8,8,-4,paint);c.drawLine(-9,-5,9,-5,paint);c.drawLine(0,-13,0,-7,paint);c.drawLine(-3,-10,0,-7,paint);c.drawLine(0,-7,3,-10,paint);break;
            case STOP_UNCONFIRMED:
                c.drawRect(-7,-7,7,7,paint);break;
            default:
                path.reset();path.moveTo(0,-10);path.lineTo(10,8);path.lineTo(-10,8);path.close();c.drawPath(path,paint);c.drawLine(0,-3,0,1,paint);c.drawPoint(0,5,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=hit.contains(event.getX(),event.getY());return pressed;}
        if(!pressed)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_UP){pressed=false;if(hit.contains(event.getX(),event.getY()))performClick();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)pressed=false;
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(signal.kind!=HomeVesselSignal.Kind.NONE&&signal.kind!=HomeVesselSignal.Kind.CHECKING)
        new AlertDialog.Builder(getContext()).setMessage(signal.description()).setPositiveButton("Verstanden",null).show();return true;}
}
