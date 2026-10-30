package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled editorial photography for function introductions; never presented as a recipe-step proof. */
public final class FunctionPhotography {
    private FunctionPhotography() {}
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2,r->{Thread thread=new Thread(r,"function-photography");thread.setDaemon(true);return thread;});
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String name,Bitmap value){return value.getAllocationByteCount();}};
    private static final LruCache<String,Bitmap> HERO_CACHE=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String name,Bitmap value){return value.getAllocationByteCount();}};
    private static LruCache<String,Bitmap> cache(String asset){return asset.contains("functions-c3-circular-hero/")?HERO_CACHE:CACHE;}
    private static final android.os.Handler MAIN=new android.os.Handler(android.os.Looper.getMainLooper());
    private static final java.util.Map<String,java.util.List<java.util.function.Consumer<Bitmap>>> PENDING=new java.util.HashMap<>();
    private static final String[] MODES=FunctionSetup.ids().toArray(new String[0]);
    /** Start once while the local Home data is read; every small thumbnail fits in the cache together. */
    private static volatile long warmStarted=-1;private static boolean warmReported;
    public static void prewarm(Context context){Context app=context.getApplicationContext();if(warmStarted<0)warmStarted=android.os.SystemClock.elapsedRealtime();for(String mode:MODES)load(app,asset(mode),256,null);}
    public static int readyCount(){int count=0;for(String mode:MODES)if(CACHE.get(asset(mode)+"@256")!=null)count++;return count;}
    /** Bundled function imagery: existing food photographs, OEM photos and illustrative action photos. */
    public static boolean bind(Context context,ImageView image,String mode){
        String asset=asset(mode);if(asset==null)return false;
        bindAsset(context,image,asset,label(mode)+" Â· Beispielansicht");return true;
    }
    /** Real C3 form, derived from the repository's original clean-mode product photograph. */
    public static void bindBowl(Context context,ImageView image){bindAsset(context,image,"visuals/c3-open-bowl-v1.png","GeÃ¶ffneter C3-Mixtopf mit Messer Â· Produktdarstellung");image.setScaleType(ImageView.ScaleType.FIT_CENTER);}
    public static boolean has(String mode){return asset(mode)!=null;}
    /** Illustrative side view derived from the C3 bowl reference; not live geometry. */
    public static void bindBowlSide(Context context,ImageView image){bindAsset(context,image,"visuals/c3-bowl-side-v1.png","C3-Mixtopf in Seitenansicht");image.setScaleType(ImageView.ScaleType.FIT_CENTER);}
    public static void bindHero(Context context,ImageView image,String mode){
        if(!has(mode))return;
        // Square alpha-masked photo: fit the entire circle even in a wide header.
        image.setBackgroundColor(Color.TRANSPARENT);image.setClipToOutline(false);
        bindAsset(context,image,"visuals/functions-c3-circular-hero/"+mode+".webp",label(mode)+" Â· Beispielansicht");
    }
    private static String asset(String mode){if(mode==null)return null;if(mode.equals("blend")||mode.equals("smoothie"))mode="mix";return java.util.Arrays.asList(MODES).contains(mode)?"visuals/functions-c3-circular/"+mode+".webp":null;}
    private static String label(String mode){switch(mode){case "turbo":return "Turbo zerkleinern";case "caramel":return "Zucker karamellisieren";case "thicken":return "Saucen andicken";case "manual":return "Manuell kochen";case "lidless":return "Ohne Deckel kochen";case "scale":return "Zutaten wiegen";case "water":return "Wasser erhitzen";case "chop":return "Zerkleinern";case "puree":return "PÃ¼rieren";case "stir":return "Schonend rÃ¼hren";case "whip":return "Sahne schlagen";case "emulsify":return "Dressing emulgieren";case "grind":return "Mahlen";case "ice":return "Eis zerkleinern";case "fry":return "Anbraten";case "slow":return "Langsam garen";case "sousvide":return "Sous-vide garen";case "ferment":return "Joghurt fermentieren";case "clean":return "Sanft vorspÃ¼len";case "dough":return "Teig kneten";case "steam":return "Dampfgaren";case "eggs":return "Eier kochen";case "rice":return "Reis kochen";case "warm":return "Speisen erwÃ¤rmen";default:return "Mixen";}}
    private static void bindAsset(Context context,ImageView image,String asset,String description){
        Context app=context.getApplicationContext();image.setTag(com.kitchenos.connect.R.id.function_photo,asset);image.setContentDescription(description);image.setScaleType(asset.contains("functions-c3-circular-hero/")?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);image.setPadding(0,0,0,0);image.animate().cancel();image.setAlpha(1f);image.setImageAlpha(255);
        int size=asset.equals("visuals/c3-open-bowl-v1.png")||asset.equals("visuals/c3-bowl-side-v1.png")||asset.contains("functions-c3-circular-hero/")?768:256;
        Bitmap cached=cache(asset).get(asset+"@"+size);if(cached!=null){image.setImageDrawable(new BitmapDrawable(app.getResources(),cached));return;}
        image.setImageDrawable(null);
        load(app,asset,size,bitmap->{if(asset.equals(image.getTag(com.kitchenos.connect.R.id.function_photo))){image.setImageDrawable(new BitmapDrawable(app.getResources(),bitmap));}});
    }
    /** Coalesce prewarm and visible-view requests; never decode the same asset twice in parallel. */
    private static void load(Context app,String asset,int size,java.util.function.Consumer<Bitmap> receiver){
        if(asset==null)return;String key=asset+"@"+size;
        synchronized(PENDING){Bitmap hit=cache(asset).get(key);if(hit!=null){if(receiver!=null)MAIN.post(()->receiver.accept(hit));return;}
            java.util.List<java.util.function.Consumer<Bitmap>> waiting=PENDING.get(key);if(waiting!=null){if(receiver!=null)waiting.add(receiver);return;}
            waiting=new java.util.ArrayList<>();if(receiver!=null)waiting.add(receiver);PENDING.put(key,waiting);
        }
        WORK.execute(()->{Bitmap decoded=null;try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream stream=app.getAssets().open(asset)){BitmapFactory.decodeStream(stream,null,bounds);}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>size||bounds.outHeight/options.inSampleSize>size)options.inSampleSize*=2;
            try(InputStream stream=app.getAssets().open(asset)){decoded=BitmapFactory.decodeStream(stream,null,options);}
        }catch(Exception ignored){}final Bitmap ready=decoded;java.util.List<java.util.function.Consumer<Bitmap>> waiting;
            synchronized(PENDING){if(ready!=null)cache(asset).put(key,ready);waiting=PENDING.remove(key);if(!warmReported&&warmStarted>=0&&readyCount()==MODES.length){warmReported=true;android.util.Log.i("KitchenOSImages",MODES.length+" Funktionsbilder vorgeladen in "+(android.os.SystemClock.elapsedRealtime()-warmStarted)+" ms; Cache="+CACHE.size()+" Bytes");}}
            if(ready!=null&&waiting!=null)MAIN.post(()->{for(java.util.function.Consumer<Bitmap> receiverView:waiting)receiverView.accept(ready);});
        });
    }
}
