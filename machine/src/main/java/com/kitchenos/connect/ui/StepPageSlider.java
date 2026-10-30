package com.kitchenos.connect.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.function.Supplier;
import com.kitchenos.common.Ui;

/** Finger-driven pages. Preview is inert; only release commits the real navigation. */
public final class StepPageSlider {
    private final FrameLayout originalStage,overlay;
    private final ImageView outgoing;
    private final View preview;
    private final Bitmap snapshot;
    private final int width,top;
    private boolean disposed;
    private int direction;
    public StepPageSlider(Activity activity,FrameLayout stage,View scroll,View footer,View preview){
        originalStage=stage;this.preview=preview;width=stage.getWidth();
        int[] base=new int[2],start=new int[2],end=new int[2];stage.getLocationOnScreen(base);scroll.getLocationOnScreen(start);footer.getLocationOnScreen(end);top=start[1]-base[1];
        snapshot=Bitmap.createBitmap(width,Math.max(1,stage.getHeight()-top),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(snapshot);canvas.drawColor(Ui.BG);canvas.save();canvas.translate(start[0]-base[0],0);scroll.draw(canvas);canvas.restore();canvas.save();canvas.translate(end[0]-base[0],end[1]-start[1]);footer.draw(canvas);canvas.restore();
        overlay=new FrameLayout(activity);overlay.setBackgroundColor(Ui.BG);overlay.setClipChildren(true);overlay.setClickable(true);overlay.addView(preview,new FrameLayout.LayoutParams(-1,-1));outgoing=new ImageView(activity);outgoing.setScaleType(ImageView.ScaleType.FIT_XY);outgoing.setImageBitmap(snapshot);overlay.addView(outgoing,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,-1);size.topMargin=top;stage.addView(overlay,size);
    }
    public void drag(float offset){if(disposed)return;if(offset!=0)direction=offset<0?-1:1;outgoing.setTranslationX(offset);preview.setTranslationX(offset-direction*width);}
    public void release(float offset,boolean commit,Runnable navigate,Supplier<FrameLayout> stage,Supplier<View> scroll,Supplier<View> footer){
        if(disposed)return;if(!commit){settleBack();return;}navigate.run();FrameLayout current=stage.get();if(current==originalStage){settleBack();return;}
        originalStage.removeView(overlay);overlay.removeView(outgoing);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,-1);size.topMargin=top;current.addView(outgoing,size);outgoing.setTranslationX(offset);View content=scroll.get(),actions=footer.get();float incoming=offset-direction*width;content.setTranslationX(incoming);actions.setTranslationX(incoming);content.animate().translationX(0).setDuration(180).start();actions.animate().translationX(0).setDuration(180).start();outgoing.animate().translationX(direction*width).setDuration(180).withEndAction(()->{current.removeView(outgoing);dispose();}).start();
    }
    private void settleBack(){outgoing.animate().translationX(0).setDuration(160).withEndAction(this::cancel).start();preview.animate().translationX(-direction*width).setDuration(160).start();}
    public void cancel(){if(disposed)return;outgoing.animate().cancel();preview.animate().cancel();originalStage.removeView(overlay);dispose();}
    private void dispose(){if(disposed)return;disposed=true;outgoing.setImageDrawable(null);snapshot.recycle();}
}
