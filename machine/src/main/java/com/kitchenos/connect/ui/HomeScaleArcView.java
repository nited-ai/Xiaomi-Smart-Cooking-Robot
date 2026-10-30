package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import com.kitchenos.common.Ui;

/** 0–2.5 kg dial. Only the moving gram label accepts the deliberate tare tap. */
public final class HomeScaleArcView extends View {
    private final RectF readingHit=new RectF();private boolean tareAvailable;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] tickPoints=new float[51*4];
    private final Shader[] tickShaders=new Shader[51];
    private int geometryWidth=-1,geometryHeight=-1,geometryColor;
    private float geometryDensity,cx,cy,rx;
    private boolean measured;private float target,shown;private long frame;
    private int grams;private String readingLabel="";private float labelSize=28;
    private final Typeface labelFace=Typeface.create("sans-serif",Typeface.BOLD);
    public HomeScaleArcView(Context context){super(context);setTag("home.scale.arc");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Waage. Kein aktueller Messwert");}
    public void tareAvailable(boolean available){tareAvailable=available;setContentDescription(measured?"Waage: "+grams+" Gramm"+(available?". Zum Tarieren antippen":""):"Waage. Kein aktueller Messwert");}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&(!measured||!tareAvailable||!readingHit.contains(event.getX(),event.getY())))return false;return super.onTouchEvent(event);}
    public void reading(Integer grams){
        boolean next=grams!=null;float value=next?HomeWeightVisuals.dialFraction(grams):0;
        if(next==measured&&value==target&&(!next||grams==this.grams))return;
        if(!measured||!next||!ValueAnimator.areAnimatorsEnabled())shown=value;
        if(next){this.grams=grams;readingLabel=grams+" g";}
        setContentDescription(next?"Waage: "+grams+" Gramm"+(tareAvailable?". Zum Tarieren antippen":""):"Waage. Kein aktueller Messwert");
        measured=next;target=value;invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long now=SystemClock.uptimeMillis();
        boolean animate=isShown()&&ValueAnimator.areAnimatorsEnabled();
        float sizeTarget=measured&&HomeWeightVisuals.nearDialLandmark(grams)?36:28;
        if(animate&&frame>0){long elapsed=Math.min(64,now-frame);shown+=(target-shown)*(1-(float)Math.exp(-elapsed/65f));labelSize+=(sizeTarget-labelSize)*(1-(float)Math.exp(-elapsed/90f));}else{shown=target;labelSize=sizeTarget;}
        frame=now;float d=getResources().getDisplayMetrics().density;prepareGeometry(d);
        float ry=rx;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        // The outer tips stay legible; ticks fade toward the inside of the bowl.
        for(int i=0;i<=50;i++){
            int point=i*4;paint.setStrokeWidth((i%5==0?1.5f:1)*d);paint.setShader(tickShaders[i]);
            canvas.drawLine(tickPoints[point],tickPoints[point+1],tickPoints[point+2],tickPoints[point+3],paint);
        }
        paint.setShader(null);paint.setColor(Ui.LINE);paint.setStrokeWidth(d);
        canvas.drawArc(cx-rx,cy-ry,cx+rx,cy+ry,180,180,false,paint);
        double markAngle=Math.PI+shown*Math.PI;float markCos=(float)Math.cos(markAngle),markSin=(float)Math.sin(markAngle);
        float labelX=cx+(rx-52*d)*markCos,labelY=cy+(ry-52*d)*markSin;
        paint.setTypeface(labelFace);paint.setTextSize(labelSize*d);float readingWidth=paint.measureText(readingLabel);
        // Keep the text anchored on the same arc, aligned inward at its ends.
        // This leaves the zero/2500 indicator outside the word instead of
        // striking horizontally through the weight.
        labelX-=markCos*readingWidth/2;
        labelX=Math.max(readingWidth/2+3*d,Math.min(getWidth()-readingWidth/2-3*d,labelX));
        paint.setTextAlign(Paint.Align.CENTER);readingHit.setEmpty();
        paint.setStyle(Paint.Style.STROKE);
        if(measured){float cs=markCos,sn=markSin;
            paint.setColor(Ui.ACCENT);paint.setStrokeWidth(4.5f*d);
            if(grams<=HomeWeightVisuals.DIAL_MAX_GRAMS){int markSave=canvas.save();canvas.clipOutRect(labelX-readingWidth/2-4*d,labelY-labelSize*d*.6f,labelX+readingWidth/2+4*d,labelY+labelSize*d*.6f);
            canvas.drawLine(cx+(rx-26*d)*cs,cy+(ry-26*d)*sn,cx+(rx+3*d)*cs,cy+(ry+3*d)*sn,paint);
            canvas.restoreToCount(markSave);}
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(labelSize*d);paint.setColor(Ui.ACCENT);
            canvas.drawText(readingLabel,labelX,labelY-(paint.ascent()+paint.descent())/2,paint);
            float hitWidth=Math.max(64*d,readingWidth+24*d);readingHit.set(labelX-hitWidth/2,labelY-28*d,labelX+hitWidth/2,labelY+28*d);
        }
        if(animate&&(Math.abs(target-shown)>.00005f||Math.abs(sizeTarget-labelSize)>.01f))postInvalidateOnAnimation();
    }
    private void prepareGeometry(float density){
        int width=getWidth(),height=getHeight(),color=Ui.MUTED;
        if(width==geometryWidth&&height==geometryHeight&&density==geometryDensity&&color==geometryColor)return;
        geometryWidth=width;geometryHeight=height;geometryDensity=density;geometryColor=color;
        cx=width/2f;cy=height-68*density;
        // Keep equal radii: this is the same upright semicircle, not a floor ellipse.
        rx=Math.max(0,width*.495f);
        int tip=Color.argb(115,Color.red(color),Color.green(color),Color.blue(color));
        for(int i=0;i<=50;i++){
            double angle=Math.PI+i*Math.PI/50;float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle),length=(i%5==0?24:13)*density;
            int point=i*4;float x=cx+rx*cs,y=cy+rx*sn,ix=cx+(rx-length)*cs,iy=cy+(rx-length)*sn;
            tickPoints[point]=ix;tickPoints[point+1]=iy;tickPoints[point+2]=x;tickPoints[point+3]=y;
            // Native shaders and their arrays are allocated only on geometry/theme changes.
            tickShaders[i]=new LinearGradient(ix,iy,x,y,new int[]{Color.TRANSPARENT,tip},null,Shader.TileMode.CLAMP);
        }
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);geometryWidth=-1;
        float d=getResources().getDisplayMetrics().density;prepareGeometry(d);
        // Home starts below the corner buttons. Let only the passive dial reach
        // into that free upper area; the vessel and number wheels stay in place.
        setTranslationY(-(cy-rx+(HomeNumberControls.TOP_SPACE-HomeNumberControls.SCALE_TOP)*d));
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);frame=0;}
    @Override protected void onDetachedFromWindow(){frame=0;super.onDetachedFromWindow();}
}
