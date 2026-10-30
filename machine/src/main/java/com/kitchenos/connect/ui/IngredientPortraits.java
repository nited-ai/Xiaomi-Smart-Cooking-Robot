package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.connect.kitchen.KitchenStore;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Consistently framed, Kalomatik example ingredient photography, with bounded offline caching. */
public final class IngredientPortraits {
    private IngredientPortraits() {}
    private static final int CREAM=0xfff2f3ee;
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    private static final Map<String,List<WeakReference<ImageView>>> WAITERS=new HashMap<>();
    private static final Map<String,Long> DEADLINES=new HashMap<>();
    private static final Set<String> WARMED=new LinkedHashSet<>();
    public static void prewarm(Context context,org.json.JSONArray ingredients){
        if(ingredients==null||!hasAiConsent(context)||isOffline(context))return;
        String uid=new CloudSession(context).account().optString("uid");int queued=0;
        for(int i=0;i<ingredients.length()&&queued<8;i++){
            JSONObject ingredient=ingredients.optJSONObject(i),request=IngredientImageRequest.from(ingredient);if(request==null)continue;
            String key=uid+":"+request.optString("name")+":"+IngredientImageRequest.quantity(request);
            synchronized(WARMED){if(WARMED.contains(key))continue;if(WARMED.size()>=64)WARMED.remove(WARMED.iterator().next());WARMED.add(key);}
            queued++;bind(context,new ImageView(context),ingredient);
        }
    }
    private static final Map<String,Long> RETRY=new HashMap<>();
    private static final Map<String,ImageState> STATES=new HashMap<>();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService CACHE_IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ingredient-cache");t.setDaemon(true);return t;});
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48),r->{Thread t=new Thread(r,"ingredient-portraits");t.setDaemon(true);return t;});

    /** Use a square ImageView, preferably 96–160dp. No quantity is inferred from this portrait. */
    public static void bind(Context context,ImageView view,JSONObject ingredient){JSONObject request=IngredientImageRequest.from(ingredient);if(request==null){view.setTag(null);view.setScaleType(ImageView.ScaleType.FIT_CENTER);showState(context,view,ingredient==null?"Zutat":ingredient.optString("name"),ImageState.UNAVAILABLE,false);return;}bind(context,view,ingredient.optString("name"),request);}
    public static void bind(Context context,ImageView view,String ingredientName){JSONObject request=new JSONObject();try{request.put("style","ingredient-cutout-v3").put("name",canonicalName(ingredientName)).put("amount",100).put("unit","g");}catch(Exception impossible){}bind(context,view,ingredientName,request);}
    private static void bind(Context context,ImageView view,String ingredientName,JSONObject request){
        final Context app=context.getApplicationContext();
        final String name=request.optString("name");
        view.animate().cancel();view.setAlpha(1f);view.setImageAlpha(255);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(0,0,0,0);showState(app,view,ingredientName,ImageState.LOADING,false);
        if(name.isEmpty()){view.setTag(null);showState(app,view,ingredientName,ImageState.UNAVAILABLE,false);return;}
        CloudSession session=new CloudSession(app);final String uid=session.account().optString("uid");
        if(uid.isEmpty()){view.setTag(null);showState(app,view,ingredientName,ImageState.LOGIN,false);return;}
        final String key;try{key=Pairing.fingerprint((uid+":ingredient-cutout-v3:"+IngredientImageRequest.quantity(request)+":"+name).getBytes(StandardCharsets.UTF_8));}catch(Exception e){view.setTag(null);showState(app,view,ingredientName,ImageState.UNAVAILABLE,false);return;}
        view.setTag(key);Bitmap hit=MEMORY.get(key);if(hit!=null){show(app,view,key,uid,name,hit,false);return;}Bitmap fallback=MEMORY.get("legacy:"+key);if(fallback!=null)show(app,view,key,uid,name,fallback,false);
        File directory=new File(app.getCacheDir(),"ingredient-portraits-v3");File file=new File(directory,key+".png");
        JSONObject prefs=new KitchenStore(app).snapshot().optJSONObject("preferences");
        if(!file.exists()&&!legacy(app,uid,name).exists()&&fallback==null&&(prefs==null||!prefs.optBoolean("aiConsent"))){showState(app,view,ingredientName,ImageState.CONSENT,false);return;}
        if(!file.exists()&&!legacy(app,uid,name).exists()&&fallback==null&&isOffline(app)){showState(app,view,ingredientName,ImageState.OFFLINE,false);return;}
        synchronized(WAITERS){
            List<WeakReference<ImageView>> waiting=WAITERS.get(key);if(waiting!=null){waiting.add(new WeakReference<>(view));return;}
            if(!file.exists()&&RETRY.getOrDefault(key,0L)>System.currentTimeMillis()){if(fallback!=null)return;if(legacy(app,uid,name).isFile()){legacyOnly(app,view,uid,name,key);return;}showState(app,view,ingredientName,STATES.getOrDefault(key,ImageState.UNAVAILABLE),false);return;}
            List<WeakReference<ImageView>> waitingNew=new ArrayList<>();waitingNew.add(new WeakReference<>(view));WAITERS.put(key,waitingNew);DEADLINES.put(key,0L);
        }
        enqueue(app,session,uid,name,key,directory,file,0,request);
    }
    private static File legacy(Context app,String uid,String name){try{return new File(new File(app.getCacheDir(),"ingredient-portraits-v1"),Pairing.fingerprint((uid+":ingredient-flat-v1:"+name).getBytes(StandardCharsets.UTF_8))+".png");}catch(Exception unavailable){return new File(app.getCacheDir(),"missing-legacy-portrait");}}
    private static Bitmap readLegacy(Context app,String uid,String name,String key){Bitmap cached=MEMORY.get("legacy:"+key);if(cached!=null)return cached;File file=legacy(app,uid,name);if(!file.isFile()||file.length()>1500000)return null;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000)return null;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap image=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(image!=null)MEMORY.put("legacy:"+key,image);return image;}
    private static void legacyOnly(Context app,ImageView view,String uid,String name,String key){WeakReference<ImageView> reference=new WeakReference<>(view);CACHE_IO.execute(()->{Bitmap image=readLegacy(app,uid,name,key);ImageView target=reference.get();if(target==null)return;target.post(()->{if(image!=null)show(app,target,key,uid,name,image,false);else if(key.equals(target.getTag())&&uid.equals(new CloudSession(app).account().optString("uid")))showState(app,target,name,ImageState.UNAVAILABLE,false);});});}
    private static void showLegacy(Context app,String uid,String name,String key){Bitmap image=readLegacy(app,uid,name,key);if(image==null)return;List<WeakReference<ImageView>> targets;synchronized(WAITERS){targets=WAITERS.get(key)==null?new ArrayList<>():new ArrayList<>(WAITERS.get(key));}for(WeakReference<ImageView> reference:targets){ImageView view=reference.get();if(view!=null)view.post(()->show(app,view,key,uid,name,image,false));}}
    private static void enqueue(Context app,CloudSession session,String uid,String name,String key,File directory,File file,int attempt,JSONObject request){try{WORK.execute(()->load(app,session,uid,name,key,directory,file,attempt,request));}catch(RejectedExecutionException busy){failed(app,uid,name,key,ImageState.UNAVAILABLE);}}
    private static void load(Context app,CloudSession session,String uid,String name,String key,File directory,File file,int attempt,JSONObject request){
        try{
            if(!uid.equals(new CloudSession(app).account().optString("uid"))){failed(app,uid,name,key,ImageState.LOGIN);return;}
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("image cache unavailable");
            if(!file.exists()){
                showLegacy(app,uid,name,key);
                if(!hasAiConsent(app)){failed(app,uid,name,key,ImageState.CONSENT);return;}
                if(isOffline(app)){failed(app,uid,name,key,ImageState.OFFLINE);return;}
                long deadline; synchronized(WAITERS){if(!WAITERS.containsKey(key))return;deadline=DEADLINES.getOrDefault(key,0L);if(deadline==0){deadline=android.os.SystemClock.elapsedRealtime()+290000;DEADLINES.put(key,deadline);}}long left=deadline-android.os.SystemClock.elapsedRealtime();if(left<=0)throw new IOException("image wait budget ended");JSONObject result=session.ingredientPortrait(request,(int)Math.min(290000,left));
                if(!uid.equals(new CloudSession(app).account().optString("uid"))){failed(app,uid,name,key,ImageState.LOGIN);return;}
                if("pending".equals(result.optString("status"))&&android.os.SystemClock.elapsedRealtime()<deadline){long delay=Math.max(1000,Math.min(8000,result.optLong("retryAfterMs",2500)));MAIN.postDelayed(()->enqueue(app,session,uid,name,key,directory,file,attempt+1,request),delay);return;}
                if(!"ready".equals(result.optString("status")))throw new IOException("image not ready");
                if(!IngredientImageRequest.approved(request,result.optJSONObject("asset")))throw new IOException("image portion not approved");
                JSONObject encoded=result.getJSONObject("image");String data=encoded.getString("data");
                if(!"image/png".equals(encoded.getString("mimeType"))||data.length()>2000000)throw new IOException("invalid image envelope");
                byte[] bytes=Base64.decode(data,Base64.DEFAULT);
                if(bytes.length<8||bytes.length>1500000||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw new IOException("invalid image format");
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000)throw new IOException("invalid image dimensions");
                File part=new File(directory,key+".part");try(OutputStream out=new FileOutputStream(part)){out.write(bytes);}
                if(!part.renameTo(file)){part.delete();throw new IOException("image cache unavailable");}trim(directory);
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000){file.delete();throw new IOException("invalid cached image");}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>384||bounds.outHeight/options.inSampleSize>384)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(bitmap==null){file.delete();throw new IOException("invalid cached image");}
            if(!uid.equals(new CloudSession(app).account().optString("uid"))){failed(app,uid,name,key,ImageState.LOGIN);return;}
            MEMORY.put(key,bitmap);List<WeakReference<ImageView>> targets;
            synchronized(WAITERS){targets=WAITERS.remove(key);DEADLINES.remove(key);RETRY.remove(key);STATES.remove(key);}
            if(targets!=null)for(WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null)target.post(()->show(app,target,key,uid,name,bitmap,true));}
        }catch(Exception ignored){failed(app,uid,name,key,isOffline(app)?ImageState.OFFLINE:ImageState.UNAVAILABLE);}
    }
    private static void failed(Context app,String uid,String name,String key,ImageState state){List<WeakReference<ImageView>> targets;synchronized(WAITERS){targets=WAITERS.remove(key);DEADLINES.remove(key);STATES.put(key,state);if(state==ImageState.UNAVAILABLE)RETRY.put(key,System.currentTimeMillis()+60*1000);else RETRY.remove(key);}if(targets!=null)for(WeakReference<ImageView> ref:targets){ImageView view=ref.get();if(view!=null)view.post(()->{if(key.equals(view.getTag())&&uid.equals(new CloudSession(app).account().optString("uid"))&&!(view.getDrawable() instanceof Portrait))showState(app,view,name,state,false);});}}
    static boolean hasAiConsent(Context context){JSONObject prefs=new KitchenStore(context).snapshot().optJSONObject("preferences");return prefs!=null&&prefs.optBoolean("aiConsent");}
    static boolean isOffline(Context context){try{ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(manager==null)return false;NetworkCapabilities capabilities=manager.getNetworkCapabilities(manager.getActiveNetwork());return capabilities==null||!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}catch(Exception unavailable){return false;}}
    private static void show(Context app,ImageView view,String key,String uid,String name,Bitmap bitmap,boolean animate){
        if(!key.equals(view.getTag())||!uid.equals(new CloudSession(app).account().optString("uid")))return;
        view.setImageDrawable(new Portrait(bitmap));view.setImageAlpha(255);view.setContentDescription(name+" · Zutatenfoto, Beispielansicht");
        view.animate().cancel();view.setAlpha(1f);
    }
    static String canonicalName(String value){
        if(value==null)return "";String name=Normalizer.normalize(value,Normalizer.Form.NFKC).trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+"," ");
        if(name.length()>100||name.isEmpty()||!name.matches("[\\p{L}\\p{N} ,().%+'’/\\-]+")||!name.matches(".*\\p{L}.*"))return "";
        switch(name){case "eier":return "ei";case "tomaten":return "tomate";case "kartoffeln":return "kartoffel";case "möhren":case "möhre":case "karotten":return "karotte";case "paprika":return "paprikaschote";default:return name;}
    }
    private static void trim(File directory){File[] files=directory.listFiles((dir,n)->n.endsWith(".png"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File file:files)total+=file.length();for(File file:files){if(total<=32000000)break;long bytes=file.length();if(file.delete())total-=bytes;}}
    public enum ImageState {
        LOADING("Bild lädt","Bild wird geladen"),OFFLINE("Offline","Offline · kein gespeichertes Bild"),CONSENT("KI-Bilder aus","KI-Bilder nicht aktiviert"),UNAVAILABLE("Ohne Bild","Bild nicht verfügbar"),LOGIN("Anmelden","Zum Laden des Bildes anmelden");
        public final String shortLabel,label;ImageState(String shortLabel,String label){this.shortLabel=shortLabel;this.label=label;}
    }
    public static void showState(Context context,ImageView view,String name,ImageState state,boolean cover){view.animate().cancel();view.setAlpha(1f);view.setPadding(0,0,0,0);view.setScaleType(cover?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);view.setImageDrawable(new StatusPortrait(name,state,cover));view.setImageAlpha(255);view.setContentDescription((name==null||name.trim().isEmpty()?cover?"Rezept":"Zutat":name)+" · "+state.label);}
    private static final class StatusPortrait extends PortraitBase {
        private final String monogram;private final ImageState state;private final boolean cover;
        StatusPortrait(String name,ImageState state,boolean cover){this.state=state;this.cover=cover;String letters=(name==null?"":name).trim().replaceAll("[^\\p{L}\\p{N} ]","");String[] words=letters.split("\\s+");String first=words.length>0?words[0]:"";this.monogram=first.isEmpty()?cover?"R":"Z":(words.length>1?first.substring(0,1)+words[1].substring(0,1):first.substring(0,Math.min(2,first.length()))).toUpperCase(Locale.GERMAN);}
        @Override public void draw(Canvas canvas){float width=getBounds().width(),height=getBounds().height(),side=Math.min(width,height),x=getBounds().exactCenterX(),y=getBounds().exactCenterY();if(cover){paint.setColor(com.kitchenos.common.Ui.CARD);canvas.drawRect(getBounds(),paint);paint.setColor(com.kitchenos.common.Ui.MUTED);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(Math.min(22,height*.09f));canvas.drawText(state==ImageState.LOADING?"Rezeptbild wird geladen …":state==ImageState.OFFLINE?"Rezeptbild noch nicht gespeichert":"Rezeptbild nicht verfügbar",x,y,paint);return;}paint.setStyle(Paint.Style.FILL);paint.setColor(CREAM);if(cover)canvas.drawRoundRect(getBounds().left,getBounds().top,getBounds().right,getBounds().bottom,side*.12f,side*.12f,paint);else canvas.drawCircle(x,y,side*.48f,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(side*(cover?.30f:.29f));paint.setColor(0xff72806f);canvas.drawText(monogram,x,y+side*.02f,paint);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(Math.max(11,Math.min(18,side*.12f)));paint.setColor(0xff53604f);canvas.drawText(state.shortLabel,x,y+side*.28f,paint);}
    }
    private static abstract class PortraitBase extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter color){paint.setColorFilter(color);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Portrait extends PortraitBase {
        private final Bitmap image;Portrait(Bitmap image){this.image=image;}
        @Override public void draw(Canvas canvas){float side=Math.min(getBounds().width(),getBounds().height())*.96f;float x=getBounds().exactCenterX(),y=getBounds().exactCenterY();int save=canvas.save();Path clip=new Path();clip.addCircle(x,y,side*.5f,Path.Direction.CW);canvas.clipPath(clip);paint.setColor(com.kitchenos.common.Ui.CARD);canvas.drawCircle(x,y,side*.5f,paint);float factor=Math.min(side*.94f/image.getWidth(),side*.94f/image.getHeight()),halfWidth=image.getWidth()*factor/2,halfHeight=image.getHeight()*factor/2;RectF dest=new RectF(x-halfWidth,y-halfHeight,x+halfWidth,y+halfHeight);paint.setFilterBitmap(true);canvas.drawBitmap(image,null,dest,paint);canvas.restoreToCount(save);}
    }
}
