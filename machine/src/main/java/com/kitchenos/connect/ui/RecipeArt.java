package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import com.kitchenos.connect.ui.IngredientPortraits.ImageState;

/** Reuses Kalomatik's existing dish image catalogue. Only food names, no pantry photos. */
public final class RecipeArt {
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32));
    private static final android.util.LruCache<String,Bitmap> MEMORY=new android.util.LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};
    private static final ExecutorService DISK=Executors.newFixedThreadPool(2);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<String,List<WeakReference<ImageView>>> WAITERS=new HashMap<>();
    private static final Map<String,Long> RETRY_AFTER=new HashMap<>();
    private static final Map<String,ImageState> STATES=new HashMap<>();
    /** Prefer the exact account/title/ingredient cover already on disk before trying a public URL. */
    public static boolean loadCached(Context context,ImageView view,JSONObject recipe){
        try{Context app=context.getApplicationContext();String uid=new CloudSession(app).account().optString("uid"),title=recipe.optString("title");if(uid.isEmpty()||title.trim().isEmpty())return false;JSONArray ingredients=recipe.optJSONArray("ingredients"),names=new JSONArray();if(ingredients!=null)for(int i=0;i<ingredients.length();i++){JSONObject item=ingredients.optJSONObject(i);if(item!=null&&!item.optString("name").trim().isEmpty())names.put(item.optString("name"));}if(names.length()==0)return false;String key=Pairing.fingerprint((uid+":"+title+":"+names).getBytes(StandardCharsets.UTF_8));File image=RecipeImageFiles.saved(app,"dish-art",key+".jpg");if(!image.isFile()||image.length()<4||image.length()>1500000)return false;load(app,view,recipe);return true;}catch(Exception unavailable){return false;}
    }
    public static void load(Context context,ImageView view,JSONObject recipe){
        Context app=context.getApplicationContext();String title=recipe.optString("title"),uid=new CloudSession(app).account().optString("uid");
        if(view.getDrawable()==null)IngredientPortraits.showState(app,view,title,ImageState.LOADING,true);
        if(uid.isEmpty()){view.setTag(null);IngredientPortraits.showState(app,view,title,ImageState.LOGIN,true);return;}
        JSONArray names=new JSONArray(),ingredients=recipe.optJSONArray("ingredients");if(ingredients!=null)for(int i=0;i<ingredients.length();i++){JSONObject item=ingredients.optJSONObject(i);if(item!=null&&!item.optString("name").trim().isEmpty())names.put(item.optString("name"));}
        if(title.trim().isEmpty()||names.length()==0){view.setTag(null);IngredientPortraits.showState(app,view,title,ImageState.UNAVAILABLE,true);return;}
        final String key;try{key=Pairing.fingerprint((uid+":"+title+":"+names).getBytes(StandardCharsets.UTF_8));}catch(Exception e){view.setTag(null);IngredientPortraits.showState(app,view,title,ImageState.UNAVAILABLE,true);return;}
        File directory=RecipeImageFiles.directory(app,"dish-art");File image=RecipeImageFiles.saved(app,"dish-art",key+".jpg");view.setTag(key);Bitmap cached=MEMORY.get(key);if(cached!=null){view.setImageBitmap(cached);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setAlpha(1f);return;}
        if(!image.exists()&&!IngredientPortraits.hasAiConsent(app)){IngredientPortraits.showState(app,view,title,ImageState.CONSENT,true);return;}
        if(!image.exists()&&IngredientPortraits.isOffline(app)){IngredientPortraits.showState(app,view,title,ImageState.OFFLINE,true);return;}
        synchronized(WAITERS){List<WeakReference<ImageView>> pending=WAITERS.get(key);if(pending!=null){pending.add(new WeakReference<>(view));return;}if(!image.exists()&&RETRY_AFTER.getOrDefault(key,0L)>System.currentTimeMillis()){IngredientPortraits.showState(app,view,title,STATES.getOrDefault(key,ImageState.UNAVAILABLE),true);return;}List<WeakReference<ImageView>> next=new ArrayList<>();next.add(new WeakReference<>(view));WAITERS.put(key,next);}
        enqueue(app,uid,title,names,key,directory,image,0);
    }
    private static void enqueue(Context app,String uid,String title,JSONArray names,String key,File directory,File image,int attempt){
        try{(image.isFile()?DISK:WORK).execute(()->{
            try{
                CloudSession session=new CloudSession(app);if(!uid.equals(session.account().optString("uid"))){failed(app,uid,title,key,ImageState.LOGIN);return;}
                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("image cache unavailable");
                if(!image.exists()){
                    if(!IngredientPortraits.hasAiConsent(app)){failed(app,uid,title,key,ImageState.CONSENT);return;}
                    if(IngredientPortraits.isOffline(app)){failed(app,uid,title,key,ImageState.OFFLINE);return;}
                    JSONObject result=session.recipeCover(title,names);if(!uid.equals(new CloudSession(app).account().optString("uid"))){failed(app,uid,title,key,ImageState.LOGIN);return;}
                    if("pending".equals(result.optString("status"))&&attempt<2){long delay=Math.max(1000,Math.min(8000,result.optLong("retryAfterMs",2500)));MAIN.postDelayed(()->enqueue(app,uid,title,names,key,directory,image,attempt+1),delay);return;}
                    if(!result.optString("status").equals("ready"))throw new IOException("image not ready");
                    JSONObject data=result.getJSONObject("image");if(!data.getString("mimeType").equals("image/jpeg")||data.getString("data").length()>2000000)throw new IOException("invalid image envelope");byte[] bytes=Base64.decode(data.getString("data"),Base64.DEFAULT);if(bytes.length<4||bytes.length>1500000||bytes[0]!=(byte)255||bytes[1]!=(byte)216)throw new IOException("invalid image format");
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000)throw new IOException("invalid image dimensions");
                    File temp=new File(directory,key+".part");try(OutputStream out=new FileOutputStream(temp)){out.write(bytes);}if(!temp.renameTo(image)){temp.delete();throw new IOException("image cache unavailable");}trim(directory);
                }
                BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(image.getAbsolutePath(),dimensions);if(dimensions.outWidth<1||dimensions.outHeight<1||(long)dimensions.outWidth*dimensions.outHeight>20000000){image.delete();throw new IOException("invalid cached image");}BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;int sample=1;while(dimensions.outWidth/sample>1024||dimensions.outHeight/sample>1024)sample*=2;options.inSampleSize=sample;Bitmap bitmap=BitmapFactory.decodeFile(image.getAbsolutePath(),options);if(bitmap==null){image.delete();throw new IOException("invalid cached image");}
                MEMORY.put(key,bitmap);List<WeakReference<ImageView>> targets;synchronized(WAITERS){targets=WAITERS.remove(key);RETRY_AFTER.remove(key);STATES.remove(key);}if(targets!=null)for(WeakReference<ImageView> target:targets){ImageView current=target.get();if(current!=null)current.post(()->{if(key.equals(current.getTag())&&uid.equals(new CloudSession(app).account().optString("uid"))){current.animate().cancel();current.setAlpha(1f);current.setScaleType(ImageView.ScaleType.CENTER_CROP);current.setImageBitmap(bitmap);current.setImageAlpha(255);current.setContentDescription(title+" · KI-generierte Anrichtung");}});}
            }catch(Exception ignored){failed(app,uid,title,key,IngredientPortraits.isOffline(app)?ImageState.OFFLINE:ImageState.UNAVAILABLE);}
        });}catch(RejectedExecutionException busy){failed(app,uid,title,key,ImageState.UNAVAILABLE);}
    }
    private static void failed(Context app,String uid,String title,String key,ImageState state){List<WeakReference<ImageView>> targets;synchronized(WAITERS){targets=WAITERS.remove(key);STATES.put(key,state);if(state==ImageState.UNAVAILABLE)RETRY_AFTER.put(key,System.currentTimeMillis()+15*60*1000);else RETRY_AFTER.remove(key);}if(targets!=null)for(WeakReference<ImageView> target:targets){ImageView view=target.get();if(view!=null)view.post(()->{if(key.equals(view.getTag())&&uid.equals(new CloudSession(app).account().optString("uid")))IngredientPortraits.showState(app,view,title,state,true);});}}
    private static void trim(File directory){File[] files=directory.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long bytes=0;for(File f:files)bytes+=f.length();for(File f:files){if(bytes<=64000000)break;long size=f.length();if(f.delete())bytes-=size;}}
}
