package com.kitchenos.connect.ui;

import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;

/** Bundled generic tool photographs. AI asset provenance: visuals/tool-photos-v1.json. */
public final class ToolPhotos {
    private static final LruCache<String,Bitmap> CACHE=new LruCache<>(3);
    private ToolPhotos(){}
    public static boolean apply(ImageView view,String tool){
        if(!tool.equals("oven")&&!tool.equals("pan"))return false;
        Bitmap image=CACHE.get(tool);if(image==null)try(InputStream source=view.getContext().getAssets().open(tool.equals("oven")?"visuals/oven-installed-v2.png":"visuals/"+tool+"-photo-v1.png")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;image=BitmapFactory.decodeStream(source,null,options);if(image==null)return false;CACHE.put(tool,image);
        }catch(Exception unavailable){return false;}
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(image);view.setContentDescription(tool.equals("oven")?"Backofen · Beispielabbildung":"Pfanne · Beispielabbildung");return true;
    }
}
