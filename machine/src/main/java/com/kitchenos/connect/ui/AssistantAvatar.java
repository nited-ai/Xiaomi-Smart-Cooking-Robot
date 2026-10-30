package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import com.kitchenos.common.Ui;

/** Native port of the original Kalomatik plate and its blink/gesture timings.
 * Source commit and scope are recorded alongside the OEM artwork in assets/oem/README.md.
 * No separate animation runtime; no background animation when detached or hidden. */
public final class AssistantAvatar extends View {
    public enum Expression { IDLE, ATTENTIVE, THINKING, SPEAKING, CURIOUS, HAPPY, WELCOME, AFFIRMING, UNCERTAIN, PLAYFUL }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path silhouette=new Path();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Expression expression=Expression.IDLE;
    private long start=SystemClock.uptimeMillis(), changed=start;
    private boolean ambient=true, scheduled;
    private Pose previous=new Pose();

    public AssistantAvatar(Context context) {
        super(context);
        silhouette.addCircle(64,64,51,Path.Direction.CW);
        for(float[] bite:BITES) {
            Path cut=new Path();cut.addCircle(bite[0],bite[1],13,Path.Direction.CW);
            silhouette.op(cut,Path.Op.DIFFERENCE);
        }
        setContentDescription("Kalomatik-Assistent");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private static final float[][] BITES={{87,16},{104,27},{116,43}};
    public void setExpression(Expression next) {
        if(next==null) next=Expression.IDLE;
        if(next==expression)return;
        long now=SystemClock.uptimeMillis();previous=pose(now);
        expression=next;changed=now;
        invalidate();schedule();
    }
    public Expression getExpression(){return expression;}
    public void setBusy(boolean busy){setExpression(busy?Expression.THINKING:Expression.IDLE);}
    /** Launcher/header can remain gently alive; an input-specific avatar can disable idle gestures. */
    public void setAmbient(boolean enabled){ambient=enabled;invalidate();schedule();}
    private boolean motionEnabled() {
        try{return Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0;}
        catch(Exception ignored){return true;}
    }
    private boolean visible(){return isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown();}
    private final Runnable frame=new Runnable(){@Override public void run(){scheduled=false;if(visible()&&motionEnabled()){invalidate();schedule();}}};
    private void schedule(){if(main!=null&&!scheduled&&visible()&&motionEnabled()){scheduled=true;main.postDelayed(frame,32);}}
    private void stop(){if(main!=null){main.removeCallbacks(frame);scheduled=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedule();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)schedule();else stop();}
    @Override protected void onVisibilityChanged(View changedView,int visibility){super.onVisibilityChanged(changedView,visibility);if(visibility==VISIBLE)schedule();else stop();}

    private static final class Pose {
        float rotation, y, sx=1, sy=1, gazeX, gazeY, left=1, right=1, joy, glint=25;
    }
    private Pose pose(long now) {
        Pose p=new Pose();if(!motionEnabled())return p;
        Expression current=expression;float age=(now-changed)/1000f;
        // Original ambient uses rests of 3.4–6.2 s between short, non-repeating gestures.
        if(current==Expression.IDLE&&ambient) {
            float cycle=((now-start)%32000L)/1000f;
            if(cycle>=5&&cycle<6.05f){current=Expression.WELCOME;age=cycle-5;}
            else if(cycle>=12&&cycle<13.6f){current=Expression.CURIOUS;age=cycle-12;}
            else if(cycle>=20&&cycle<21.35f){current=Expression.AFFIRMING;age=cycle-20;}
            else if(cycle>=27&&cycle<28.6f){current=Expression.HAPPY;age=cycle-27;}
        }
        switch(current) {
            case THINKING: {
                float t=(float)((1-Math.cos(age*Math.PI*2/6.4))/2);
                p.rotation=-4+8*t;p.sx=.98f;p.gazeX=-5+10*t;p.gazeY=-8+t;
                p.left=1.2f-.52f*t;p.right=.68f+.52f*t;p.glint=25+65*t;break;
            }
            case ATTENTIVE: {
                float f=(age%12)/12;p.left=p.right=1.2f;
                p.y=key(f,new float[]{0,.39f,.43f,.47f,.78f,.82f,.86f,1},new float[]{0,0,3,0,0,2,0,0});p.gazeY=p.y;break;
            }
            case SPEAKING: p.gazeX=(float)Math.sin(age*Math.PI*2/2.8);p.gazeY=-Math.max(0,p.gazeX);p.right=1+.06f*(float)Math.sin(age*Math.PI*2/2.8);break;
            case CURIOUS: {
                float f=Math.min(1,age/1.1f);p.rotation=key(f,new float[]{0,.2f,.52f,.75f,1},new float[]{0,7,-25,-12,-17});
                p.y=key(f,new float[]{0,.2f,.52f,1},new float[]{0,5,-6,0});p.left=.65f;p.right=1.18f;break;
            }
            case WELCOME: {
                float f=Math.min(1,age/1.05f);p.rotation=key(f,new float[]{0,.25f,.55f,.8f,1},new float[]{0,-4,-4,-2,0});
                p.gazeY=key(f,new float[]{0,.25f,.55f,.8f,1},new float[]{0,-2,5,-1,0});p.sy=1-Math.max(0,p.gazeY)*.01f;break;
            }
            case AFFIRMING: {
                float f=Math.min(1,age/1.35f);p.y=key(f,new float[]{0,.22f,.42f,.65f,.84f,1},new float[]{0,7,-1,5,0,0});
                p.gazeY=key(f,new float[]{0,.22f,.42f,.65f,.84f,1},new float[]{0,6,-2,4,-1,0});p.sy=1-Math.max(0,p.y)*.012f;break;
            }
            case HAPPY: {
                float f=Math.min(1,age/1.6f);
                float[] keys={0,.12f,.3f,.4f,.58f,.71f,.82f,.9f,.96f,1};
                p.y=key(f,keys,new float[]{0,5,-18,-20,8,-9,4,-3,1,0});
                p.sx=key(f,keys,new float[]{1,1.13f,.94f,1,1.26f,.91f,1.14f,.97f,1.04f,1});
                p.sy=key(f,keys,new float[]{1,.85f,1.07f,1,.72f,1.12f,.85f,1.05f,.96f,1});p.joy=f<.48f?1:f<.58f?( .58f-f)/.1f:0;break;
            }
            case UNCERTAIN: {float f=Math.min(1,age/1.25f);p.gazeX=key(f,new float[]{0,.18f,.38f,.58f,.78f,1},new float[]{0,-5,5,-3,2,0});p.sx=1-Math.abs(p.gazeX)*.012f;p.left=.78f;break;}
            case PLAYFUL: {float f=Math.min(1,age/1.8f);p.rotation=key(f,new float[]{0,.15f,.78f,1},new float[]{0,-16,360,360});p.left=key(f,new float[]{0,.65f,.78f,.9f,1},new float[]{1,1,.08f,.08f,1});break;}
            default:break;
        }
        // Same 5.8-second blink keyframes as the web character, around the shared eye center.
        if(current!=Expression.HAPPY&&current!=Expression.PLAYFUL){float f=((now-start)%5800L)/5800f;float blink=key(f,new float[]{0,.43f,.45f,.47f,.79f,.81f,.83f,1},new float[]{1,1,.07f,1,1,.07f,1,1});p.left*=blink;p.right*=blink;}
        return p;
    }
    private static float key(float fraction,float[] keys,float[] values){for(int i=1;i<keys.length;i++)if(fraction<=keys[i]){float t=(fraction-keys[i-1])/(keys[i]-keys[i-1]);return values[i-1]+(values[i]-values[i-1])*Math.max(0,t);}return values[values.length-1];}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private Pose blend(Pose p,long now) {
        float t=Math.min(1,(now-changed)/220f);t=1-(1-t)*(1-t);
        if(t>=1)return p;
        p.rotation=mix(previous.rotation,p.rotation,t);p.y=mix(previous.y,p.y,t);p.sx=mix(previous.sx,p.sx,t);p.sy=mix(previous.sy,p.sy,t);p.gazeX=mix(previous.gazeX,p.gazeX,t);p.gazeY=mix(previous.gazeY,p.gazeY,t);p.left=mix(previous.left,p.left,t);p.right=mix(previous.right,p.right,t);p.joy=mix(previous.joy,p.joy,t);return p;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);long now=SystemClock.uptimeMillis();Pose pose=pose(now);if(motionEnabled())pose=blend(pose,now);
        int save=canvas.save();float scale=Math.min(getWidth(),getHeight())/128f;
        canvas.translate((getWidth()-128*scale)/2,(getHeight()-128*scale)/2);canvas.scale(scale,scale);
        canvas.translate(64,64+pose.y);canvas.rotate(pose.rotation);canvas.scale(pose.sx,pose.sy);canvas.translate(-64,-64);
        int body=canvas.save();canvas.clipPath(silhouette);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.ACCENT);canvas.drawCircle(64,64,51,paint);
        paint.setColor(0xfffff8ee);canvas.drawCircle(64,64,41,paint);
        paint.setColor(Ui.ACCENT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);
        for(float[] bite:BITES)canvas.drawCircle(bite[0],bite[1],13,paint);
        paint.setStrokeWidth(2);paint.setAlpha(50);canvas.drawArc(29,29,99,99,pose.glint,65,false,paint);paint.setAlpha(255);canvas.restoreToCount(body);
        canvas.translate(pose.gazeX,pose.gazeY);
        if(pose.joy<1){paint.setStyle(Paint.Style.FILL);paint.setAlpha(Math.round(255*(1-pose.joy)));eye(canvas,42,57,pose.left);eye(canvas,71,86,pose.right);paint.setAlpha(255);}
        if(pose.joy>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setAlpha(Math.round(255*pose.joy));for(float x:new float[]{41,70}){Path joy=new Path();joy.moveTo(x,65);joy.lineTo(x+6,58);joy.quadTo(x+8.5f,55,x+11,58);joy.lineTo(x+17,65);canvas.drawPath(joy,paint);}paint.setAlpha(255);}
        canvas.restoreToCount(save);
    }
    private void eye(Canvas canvas,float left,float right,float scale){int save=canvas.save();canvas.scale(1,Math.max(.07f,scale),(left+right)/2,64);canvas.drawRoundRect(left,51,right,77,5,5,paint);canvas.restoreToCount(save);}
}
