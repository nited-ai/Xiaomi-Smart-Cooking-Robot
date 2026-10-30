package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlState;
import java.io.InputStream;

/** Three independent product layers. Every animation is an instruction, never a sensor or command. */
public final class HomeVesselView extends View {
    private static final String ROOT="visuals/home-vessel/";
    private static final java.util.Map<String,Bitmap> IMAGES=new java.util.HashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Layer bowl,lid,cup;
    private final RectF cupBounds=new RectF();
    private HomeVesselState state=HomeVesselState.from(new ControlState(),0);
    private HomeVesselState.Lid visualLid=HomeVesselState.Lid.UNKNOWN;
    private float lidLift=.25f,cupLift=1;
    private ValueAnimator lidMotion,cupMotion,cupPulse,startFlash;private long cupStartAt;private boolean cupPrompt;private float cupAlpha=1;
    private boolean initialized,reverse,stirrer;
    // Matches heatStops in home-vessel-photo.js: temperature, RGB, bottom opacity, original coverage temperature.
    private static final float[][] HEAT_STOPS={{35,255,190,80,0,35},{45,255,185,65,.08f,60},{60,255,158,45,.18f,80},{80,255,120,30,.32f,100},{100,246,77,25,.48f,130},{130,232,48,22,.64f,160},{160,222,29,19,.76f,180},{180,198,22,16,.80f,180}};
    private int temperature;
    public void temperature(int value){if(temperature!=value){temperature=value;invalidate();}}
    private final HomeWeighingPreview weighing=new HomeWeighingPreview(this);
    public void fillStep(int step){weighing.level(step);}
    public void fillFraction(float fraction){weighing.level(fraction<0?-1:fraction*20);}
    public boolean hasWeighingPreview(){return weighing.visible();}
    public float previewFraction(){return weighing.fraction();}
    public float previewOpening(){return weighing.aperture();}
    private static final class Layer {
        final Bitmap bitmap;final Rect bounds;
        Layer(Context c,String name){
            synchronized(IMAGES){Bitmap found=IMAGES.get(name);if(found==null){try(InputStream in=c.getAssets().open(ROOT+name+".png")){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;found=BitmapFactory.decodeStream(in,null,opts);}catch(java.io.IOException ignored){}if(found==null)found=PublicArtwork.layer(name);IMAGES.put(name,found);}bitmap=found;}
            int w=bitmap.getWidth(),h=bitmap.getHeight(),left=w,right=0,top=h,bottom=0;int[] row=new int[w];
            for(int y=0;y<h;y++){bitmap.getPixels(row,0,w,0,y,w,1);for(int x=0;x<w;x++)if(Color.alpha(row[x])>24){left=Math.min(left,x);right=Math.max(right,x+1);top=Math.min(top,y);bottom=Math.max(bottom,y+1);}}
            bounds=new Rect(left,top,right,bottom);
        }
    }
    public HomeVesselView(Context c){super(c);setTag("home.bowl");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);bowl=new Layer(c,"bowl");lid=new Layer(c,"lid");cup=new Layer(c,"cup");describe();}
    public HomeVesselState presentation(){return state;}
    public void update(ControlState live,long now){
        update(live,now,null);
    }
    public void mode(boolean reverse,boolean stirrer,boolean visible){if(this.reverse==reverse&&this.stirrer==stirrer)return;this.reverse=reverse;this.stirrer=stirrer;describe();}
    public void update(ControlState live,long now,HomeCupState cupState){
        HomeVesselState next=HomeVesselState.from(live,now,cupState);
        boolean lidChanged=next.lid!=state.lid,cupChanged=next.cupInsertionCue!=state.cupInsertionCue;
        boolean descriptionChanged=lidChanged||cupChanged||next.cupWeightDetected!=state.cupWeightDetected;
        boolean firstKnown=visualLid==HomeVesselState.Lid.UNKNOWN;
        state=next;
        if(next.lid!=HomeVesselState.Lid.UNKNOWN)visualLid=next.lid;
        // Missing samples stop the live display; they are not a physical lid movement.
        if(!initialized||firstKnown){lidLift=next.lid==HomeVesselState.Lid.OPEN?1:0;cupLift=next.cupInsertionCue?0:1;}
        else if(lidChanged&&next.lid!=HomeVesselState.Lid.UNKNOWN&&next.lid!=HomeVesselState.Lid.POT_MISSING)animateLid(next.lid==HomeVesselState.Lid.OPEN?1:0);
        if(initialized&&cupChanged)animateCup(next.cupInsertionCue?0:1);
        if(descriptionChanged||!initialized){describe();invalidate();}initialized=true;
    }
    public void cupPrompt(boolean required){cupPrompt=required;updateCupPulse();}
    private void updateCupPulse(){
        if(cupPulse!=null){cupPulse.cancel();cupPulse=null;}cupAlpha=1;
        if(cupPrompt&&motion()){cupPulse=ValueAnimator.ofFloat(1,.62f);cupPulse.setDuration(900);cupPulse.setRepeatCount(ValueAnimator.INFINITE);cupPulse.setRepeatMode(ValueAnimator.REVERSE);cupPulse.addUpdateListener(a->{cupAlpha=HomeCupPulse.alpha(System.currentTimeMillis());invalidate();});cupPulse.start();}else invalidate();
    }
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(cupPrompt||cupPulse!=null)updateCupPulse();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(cupPrompt)updateCupPulse();}
    public void startHint(){/* Preparation does not trigger the finite START cue. */}
    public void startCue(long at){cupStartAt=at;if(startFlash!=null)startFlash.cancel();if(!motion())return;startFlash=ValueAnimator.ofFloat(0,1);startFlash.setDuration(CupStartCue.DURATION_MS);startFlash.addUpdateListener(a->invalidate());startFlash.start();}
    private boolean motion(){return isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&ValueAnimator.areAnimatorsEnabled();}
    private void animateLid(float target){if(lidMotion!=null)lidMotion.cancel();if(!motion()){lidLift=target;return;}lidMotion=ValueAnimator.ofFloat(lidLift,target);lidMotion.setStartDelay(target==1?200:0);lidMotion.setDuration(650);lidMotion.setInterpolator(new android.view.animation.DecelerateInterpolator());lidMotion.addUpdateListener(v->{lidLift=(float)v.getAnimatedValue();invalidate();});lidMotion.start();}
    private void animateCup(float target){if(cupMotion!=null)cupMotion.cancel();if(!motion()){cupLift=target;return;}cupMotion=ValueAnimator.ofFloat(cupLift,target);cupMotion.setDuration(850);cupMotion.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());cupMotion.addUpdateListener(v->{cupLift=(float)v.getAnimatedValue();invalidate();});cupMotion.start();}
    private void describe(){setContentDescription("Mixtopf. "+state.hint()+". "+(reverse?"Messer rückwärts zum Rühren":"Messer vorwärts zum Zerkleinern")+(stirrer?". Rühraufsatz vom Benutzer als eingesetzt ausgewählt":"")+(state.cupInsertionCue?"":". Messbecher bitte selbst prüfen. Bei geschlossenem Deckel zum Bestätigen antippen."));}
    private void drawLayer(Canvas c,Layer layer,RectF area,int alpha){ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);paint.setColorFilter(alpha<255?new ColorMatrixColorFilter(gray):null);paint.setAlpha(alpha);c.drawBitmap(layer.bitmap,layer.bounds,area,paint);paint.setAlpha(255);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth()/300f,getHeight()/252f),left=getWidth()/2f-(150+165*bowl.bounds.width()/bowl.bounds.height()*(.1f+.8f*.38f*.04f))*scale,top=(getHeight()-252*scale)/2;int save=canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);cupBounds.setEmpty();
        // Preserve each source's physical aspect ratio; never flatten an oblique photo.
        float bodyHeight=165,bodyWidth=bodyHeight*bowl.bounds.width()/bowl.bounds.height();
        RectF body=new RectF(150-bodyWidth/2,65,150+bodyWidth/2,230);drawLayer(canvas,bowl,body,visualLid==HomeVesselState.Lid.POT_MISSING?80:255);
        float rimWidth=bodyWidth*.80f,rimX=body.left+bodyWidth*.60f;
        if(visualLid!=HomeVesselState.Lid.POT_MISSING)weighing.draw(canvas,visualLid==HomeVesselState.Lid.OPEN);
        if(visualLid!=HomeVesselState.Lid.POT_MISSING&&temperature>35){
            float heat=Math.min(1,(temperature-35)/145f),px=165f/1086,ox=150-bodyWidth/2-35*px,oy=65-84*px;
            int heatSave=canvas.save();canvas.translate(ox,oy);canvas.scale(px,px);
            Path metal=new Path();metal.moveTo(466,232);metal.quadTo(805,242,1144,232);metal.lineTo(1144,880);metal.cubicTo(1144,1000,1030,1064,800,1064);metal.cubicTo(560,1064,466,1000,466,880);metal.close();canvas.clipPath(metal);
            float value=35+145*heat;int index=1;while(index<HEAT_STOPS.length-1&&value>HEAT_STOPS[index][0])index++;
            float[] a=HEAT_STOPS[index-1],b=HEAT_STOPS[index];float u=(value-a[0])/(b[0]-a[0]);
            int red=Math.round(a[1]+(b[1]-a[1])*u),green=Math.round(a[2]+(b[2]-a[2])*u),blue=Math.round(a[3]+(b[3]-a[3])*u);float opacity=a[4]+(b[4]-a[4])*u,topHeat=1064-(170+800*(a[5]+(b[5]-a[5])*u-35)/145);
            paint.setColorFilter(null);paint.setShader(new LinearGradient(0,topHeat,0,1064,new int[]{Color.argb(0,red,green,blue),Color.argb((int)(255*opacity*.40f),red,green,blue),Color.argb((int)(255*opacity),red,green,blue)},new float[]{0,.48f,1},Shader.TileMode.CLAMP));
            canvas.drawRect(450,topHeat,1160,1100,paint);paint.setShader(null);canvas.restoreToCount(heatSave);
        }
        float lidWidth=rimWidth/.87f,lidHeight=lidWidth*lid.bounds.height()/lid.bounds.width();
        RectF cover=new RectF(rimX-lidWidth*.56f,body.top-2-46*lidLift,rimX+lidWidth*.44f,body.top-2+lidHeight-46*lidLift);if(visualLid==HomeVesselState.Lid.CLOSED)drawLayer(canvas,lid,cover,255);
        if(lidLift<.85f&&visualLid==HomeVesselState.Lid.CLOSED){
            float holeX=cover.left+cover.width()*.56f,holeY=cover.top+cover.height()*.48f;
            float floating=28*cupLift,cupWidth=rimWidth*.38f,cupHeight=cupWidth*cup.bounds.height()/cup.bounds.width();
            RectF measure=new RectF(holeX-cupWidth*.46f,holeY-cupHeight*.62f-floating,holeX+cupWidth*.54f,holeY+cupHeight*.38f-floating);
            cupBounds.set(left+measure.left*scale,top+measure.top*scale,left+measure.right*scale,top+measure.bottom*scale);
            int cupSave=canvas.save();canvas.clipRect(0,0,300,holeY+3);int alpha=(int)(255*Math.max(0,1-lidLift)*cupAlpha);drawLayer(canvas,cup,measure,alpha);
            float red=motion()?CupStartCue.red(System.currentTimeMillis(),cupStartAt):0;
            if(red>0){paint.setColorFilter(new android.graphics.PorterDuffColorFilter(0xffef3e3e,android.graphics.PorterDuff.Mode.SRC_ATOP));paint.setAlpha((int)(alpha*red));canvas.drawBitmap(cup.bitmap,cup.bounds,measure,paint);paint.setColorFilter(null);paint.setAlpha(255);}
            canvas.restoreToCount(cupSave);
        }
        canvas.restoreToCount(save);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){RectF hit=new RectF(cupBounds);hit.inset(-Ui.dp(getContext(),12),-Ui.dp(getContext(),12));if(!isLongClickable()&&(state.lid!=HomeVesselState.Lid.CLOSED||cupBounds.isEmpty()||!hit.contains(event.getX(),event.getY())))return false;}return super.onTouchEvent(event);}
    @Override protected void onDetachedFromWindow(){if(startFlash!=null){startFlash.cancel();startFlash=null;}weighing.release();if(cupPulse!=null){cupPulse.cancel();cupPulse=null;}if(lidMotion!=null)lidMotion.cancel();if(cupMotion!=null)cupMotion.cancel();super.onDetachedFromWindow();}
}
