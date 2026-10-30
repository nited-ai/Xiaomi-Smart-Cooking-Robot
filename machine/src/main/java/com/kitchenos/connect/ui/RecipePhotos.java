package com.kitchenos.connect.ui;

import android.graphics.*;
import android.widget.ImageView;
import java.io.InputStream;
import org.json.JSONObject;

/** Offline example cover for the authored brownie recipe; never replaces a private recipe photo. */
public final class RecipePhotos {
    private static Bitmap brownies;
    private RecipePhotos(){}
    public static boolean apply(ImageView view,JSONObject recipe){
        if(!recipe.optBoolean("curated")||!recipe.optString("title").equals("Schokoladen-Brownies"))return false;
        if(brownies==null)try(InputStream source=view.getContext().getAssets().open("visuals/brownie-cover-v1.png")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;brownies=BitmapFactory.decodeStream(source,null,options);
        }catch(Exception unavailable){return false;}
        if(brownies==null)return false;view.setImageBitmap(brownies);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setAlpha(1f);view.setContentDescription("Schokoladen-Brownies · KI-Beispielanrichtung");return true;
    }
}
