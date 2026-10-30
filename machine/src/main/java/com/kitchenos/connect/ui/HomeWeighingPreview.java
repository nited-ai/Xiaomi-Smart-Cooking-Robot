package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.InputStream;
import java.util.Locale;

/** Cold-start frames baked from the same photographic cutaway and cavity fill. */
final class HomeWeighingPreview {
    private static final Bitmap[][] FRAMES=new Bitmap[2][21];
    private static volatile boolean loaded,loading;
    private final View owner;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private ValueAnimator massMotion,openingMotion;
    private float target=-1,level,opening;private boolean initialized;
    HomeWeighingPreview(View owner){this.owner=owner;preload(owner.getContext().getApplicationContext());}
    private static synchronized void preload(Context context){
        if(loaded||loading)return;loading=true;
        new Thread(()->{
            try{for(int light=0;light<2;light++)for(int i=0;i<=20;i++){
                if(light==1){FRAMES[light][i]=FRAMES[0][i];continue;}
                String name="photo-"+String.format(Locale.ROOT,"%02d",i)+".png";
                try(InputStream input=context.getAssets().open("visuals/home-vessel-webgl/weighing-preview/"+name)){FRAMES[light][i]=BitmapFactory.decodeStream(input);if(FRAMES[light][i]==null)throw new IllegalStateException("Invalid preview frame");}
            }loaded=true;android.util.Log.i("KitchenOSWebGL","Sofortansicht vorbereitet");}
            catch(Exception error){android.util.Log.w("KitchenOSWebGL","Sofortansicht nicht verfügbar",error);}
        },"KitchenOS-Weighing-Preview").start();
    }
    boolean visible(){return loaded&&initialized&&opening>.001f;}
    float fraction(){return level/20f;}
    float aperture(){return opening;}
    private void cancel(){if(massMotion!=null)massMotion.cancel();if(openingMotion!=null)openingMotion.cancel();}
    void level(float next){
        if(next==target)return;target=next;
        if(next<0){cancel();level=opening=0;owner.invalidate();return;}
        if(!loaded||!owner.isShown()||owner.getVisibility()!=View.VISIBLE){level=next;opening=next>0?1:0;owner.invalidate();return;}
        animate();
    }
    private void aperture(float to){
        if(openingMotion!=null)openingMotion.cancel();openingMotion=ValueAnimator.ofFloat(opening,to);openingMotion.setDuration(320);
        openingMotion.addUpdateListener(a->{opening=(float)a.getAnimatedValue();owner.invalidate();});openingMotion.start();
    }
    private void animate(){
        cancel();if(target<0)return;
        if(!ValueAnimator.areAnimatorsEnabled()){level=target;opening=target>0?1:0;owner.invalidate();return;}
        if(target>0)aperture(1);
        final float destination=target;massMotion=ValueAnimator.ofFloat(level,destination);massMotion.setDuration(260+Math.round(Math.abs(destination-level)*10));
        massMotion.addUpdateListener(a->{level=(float)a.getAnimatedValue();owner.invalidate();});
        massMotion.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled&&destination==0&&target==0)aperture(0);}});massMotion.start();
    }
    void draw(Canvas canvas,boolean light){
        if(!loaded){if(target>0)owner.postInvalidateDelayed(80);return;}
        if(!initialized){initialized=true;if(target>0){level=opening=0;owner.post(this::animate);}}
        if(opening<=.001f)return;
        // Fade the complete window, independent of the displayed fill level.
        int save=canvas.saveLayerAlpha(0,0,300,252,Math.round(255*opening));
        int low=Math.max(0,Math.min(20,(int)Math.floor(level))),high=Math.min(20,low+1);float mix=Math.max(0,Math.min(1,level-low));
        RectF destination=new RectF(0,0,300,252);Bitmap[] frames=FRAMES[light?1:0];
        paint.setAlpha(255);canvas.drawBitmap(frames[low],null,destination,paint);
        if(high!=low&&mix>.001f){paint.setAlpha(Math.round(255*mix));canvas.drawBitmap(frames[high],null,destination,paint);paint.setAlpha(255);}
        canvas.restoreToCount(save);
    }
    void release(){cancel();}
}
