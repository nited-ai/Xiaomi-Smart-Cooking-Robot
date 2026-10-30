package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;

/** Original resources from the unpacked C3 app, not redrawn pictograms or generated mode photos.
 * Artwork reuse does not import an OEM cooking algorithm or change its verified control envelope. */
public final class OemModeImages {
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    private OemModeImages(){}
    /** Exact matches, plus explicit category-art matches recorded in mode-mapping.json. */
    private static int oemId(String id){
        if(id==null)return 0;
        if(id.startsWith("oem:"))try{int n=Integer.parseInt(id.substring(4));return n>=1&&n<=18?n:0;}catch(Exception ignored){return 0;}
        switch(id){
            case "manual":return 1;case "scale":return 2;case "steam":return 3;
            case "chop":return 6;case "dough":return 8;case "mix":return 9;
            case "grind":return 10;case "ice":return 12;case "clean":return 14;
            case "warm":return 17;default:return 0;
        }
    }
    private static final String[] ICONS={null,"manual","weight","steam","stew","high_temp","cut","strong","flour","juice","grind","meat","thick_ice","fine_ice","clean","clean_deep","clean_flour","keep_warm","bean_milk"};
    private static final String[] PHOTOS={null,"manual_detail",null,"steam_detail","hint_stew_detail","high_temp_detal","cut_detail","strong_detail","flour_detail","juice_detail","graind_detail","meat_detail","ice_detail","ice_detail","clean_detail","clean_detail","clean_detail","warm_detail","soybean"};
    private static final String[] LABELS={null,"Manuell kochen","Waage","Dämpfen und Kochen","Schmoren","Anbraten","Zerkleinern","Extreme Rotation","Teig kneten","Smoothies und Säfte","Mahlen","Hacken","Eis zerkleinern","Feines Eis","Reinigen","Tiefenreinigung","Teigreste entfernen","Warme Speisen","Sojamilch"};
    public static Drawable icon(Context context,String id){
        int mode=oemId(id);if(mode==0)return null;
        Bitmap bitmap=bitmap(context,"modes/ic_mode_"+ICONS[mode]+".png",false);
        if(bitmap==null)return null;
        Drawable icon=new BitmapDrawable(context.getResources(),bitmap);
        icon.setTint(com.kitchenos.common.Ui.ACCENT);return icon;
    }
    /** False means no original photo maps to this function; the caller chooses its honest fallback. */
    public static boolean bindPhoto(ImageView view,String id){
        int mode=oemId(id);if(mode==0||PHOTOS[mode]==null)return false;
        Bitmap bitmap=bitmap(view.getContext(),"modes/ic_mode_"+PHOTOS[mode]+".webp",true);
        if(bitmap==null)return false;
        view.setPadding(0,0,0,0);view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(bitmap);view.setImageAlpha(255);view.setAlpha(1f);
        view.setContentDescription(LABELS[mode]+" · Originalabbildung des Herstellers");return true;
    }
    public enum Instruction { CLOSE_LID("ic_security_hint.png","Deckel schließen"), LID_OPEN("ic_error_cover.png","Deckel prüfen"), POT_REMOVED("ic_error_pot_pull.png","Mixtopf einsetzen"), MEASURING_CUP("ic_high_temp_hint.png","Messbecher prüfen");
        final String file,label;Instruction(String file,String label){this.file=file;this.label=label;}
    }
    public static boolean bindInstruction(ImageView view,Instruction instruction){
        if(instruction==null)return false;Bitmap bitmap=bitmap(view.getContext(),instruction.file,false);if(bitmap==null)return false;
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(bitmap);view.setImageAlpha(255);view.setAlpha(1f);view.setContentDescription(instruction.label);return true;
    }
    private static Bitmap bitmap(Context context,String file,boolean photo){
        Bitmap cached=CACHE.get(file);if(cached!=null)return cached;
        try(InputStream input=context.getAssets().open("oem/"+file)){
            BitmapFactory.Options options=new BitmapFactory.Options();
            if(photo){
                try(InputStream bounds=context.getAssets().open("oem/"+file)){options.inJustDecodeBounds=true;BitmapFactory.decodeStream(bounds,null,options);}
                options.inJustDecodeBounds=false;options.inSampleSize=1;
                while(options.outWidth/options.inSampleSize>1600)options.inSampleSize*=2;
            }
            Bitmap image=BitmapFactory.decodeStream(input,null,options);if(image!=null)CACHE.put(file,image);return image;
        }catch(Exception ignored){return null;}
    }
}
