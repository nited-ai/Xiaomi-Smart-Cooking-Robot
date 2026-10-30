package com.kitchenos.connect.ui;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import com.kitchenos.common.Ui;

/** Samples a small shared backdrop only inside individual chat elements.
 * Everything outside the bubbles reveals the live Activity. Android 10 compatible. */
public final class FrostedPanel {
    private final Bitmap bitmap;
    private final float scaleX,scaleY;
    private final int[] origin=new int[2];

    public FrostedPanel(Activity activity){
        View source=activity.getWindow().getDecorView();source.getLocationOnScreen(origin);
        int width=Math.max(1,source.getWidth()/12),height=Math.max(1,source.getHeight()/12);
        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(width/(float)Math.max(1,source.getWidth()),height/(float)Math.max(1,source.getHeight()));source.draw(canvas);
        int[] pixels=new int[width*height],scratch=new int[pixels.length];bitmap.getPixels(pixels,0,width,0,0,width,height);
        for(int pass=0;pass<2;pass++){blur(pixels,scratch,width,height,true);blur(scratch,pixels,width,height,false);}
        bitmap.setPixels(pixels,0,width,0,0,width,height);
        scaleX=source.getWidth()/(float)width;scaleY=source.getHeight()/(float)height;
    }

    public Drawable background(View anchor,boolean user,int radius){return new Glass(anchor,user,Ui.dp(anchor.getContext(),radius));}
    public void apply(View view,boolean user,int radius){view.setBackground(background(view,user,radius));}

    private final class Glass extends Drawable {
        private final View anchor;
        private final boolean user;
        private final float radius;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        private final Matrix matrix=new Matrix();
        private final int[] location=new int[2];
        private int alpha=255;
        Glass(View anchor,boolean user,float radius){this.anchor=anchor;this.user=user;this.radius=radius;}
        @Override public void draw(Canvas canvas){
            RectF area=new RectF(getBounds());anchor.getLocationOnScreen(location);
            matrix.setScale(scaleX,scaleY);matrix.postTranslate(origin[0]-location[0],origin[1]-location[1]);shader.setLocalMatrix(matrix);
            paint.setStyle(Paint.Style.FILL);paint.setShader(shader);paint.setAlpha(alpha);canvas.drawRoundRect(area,radius,radius,paint);paint.setShader(null);
            int tint=user?(Ui.dark?0xc8382922:0xcffff0e9):(Ui.dark?0xc2212823:0xd9ffffff);
            paint.setColor(tint);paint.setAlpha(Color.alpha(tint)*alpha/255);canvas.drawRoundRect(area,radius,radius,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(anchor.getContext(),1));
            int border=anchor.isSelected()||anchor.isFocused()?Ui.ACCENT:user?0x66ee6542:Ui.dark?0x447f9184:0x99ffffff;
            paint.setColor(border);paint.setAlpha(Color.alpha(border)*alpha/255);area.inset(.5f,.5f);canvas.drawRoundRect(area,radius,radius,paint);
        }
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private static void blur(int[] source,int[] target,int width,int height,boolean horizontal){
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int red=0,green=0,blue=0;
            for(int k=-3;k<=3;k++){int nx=horizontal?Math.max(0,Math.min(width-1,x+k)):x,ny=horizontal?y:Math.max(0,Math.min(height-1,y+k));int color=source[ny*width+nx];red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);}
            target[y*width+x]=Color.rgb(red/7,green/7,blue/7);
        }
    }
}
