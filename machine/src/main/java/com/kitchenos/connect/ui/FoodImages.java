package com.kitchenos.connect.ui;

import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/** Public food covers with bounded memory/disk storage; no account headers or cookies. */
public final class FoodImages {
    private static final int MAX_BYTES=2097152;
    private static final ExecutorService workers=Executors.newFixedThreadPool(2);
    private static final ExecutorService diskWorkers=Executors.newFixedThreadPool(2);
    private static final java.util.Map<String,Long> retryAfter=new ConcurrentHashMap<>();
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private static final java.util.Map<String,java.util.List<Target>> pending=new java.util.HashMap<>();
    private static final class Target {
        final ImageView view;final Runnable unavailable;
        Target(ImageView view,Runnable unavailable){this.view=view;this.unavailable=unavailable;}
    }
    public static boolean allowed(String source){try{URI uri=new URI(source);return uri.getScheme().equals("https")&&uri.getUserInfo()==null&&uri.getPort()==-1&&((uri.getHost().equals("app.kalomatik.ai")&&uri.getPath().matches("/api/recipes/[a-f0-9]{64}/(?:cover|steps/[0-9]{1,2}/image)"))||(uri.getHost().equals("kalomatik.pro")&&uri.getPath().startsWith("/assets/")));}catch(Exception e){return false;}}
    public static void load(ImageView view,String source){load(view,source,null);}
    public static void load(ImageView view,String source,Runnable unavailable){
        load(view,source,1440,unavailable);
    }
    /** Small list covers have their own decoded cache; detail images retain full resolution. */
    public static void load(ImageView view,String source,int requestedSize,Runnable unavailable){
        if(!allowed(source)){if(unavailable!=null)unavailable.run();return;}
        final int size=requestedSize<=512?512:1440;final String memoryKey=size==1440?source:source+"@512";
        view.setTag(memoryKey);view.animate().cancel();view.setAlpha(1f);view.setImageAlpha(255);
        Bitmap existing=cache.get(memoryKey);if(existing!=null){show(view,memoryKey,existing,false);return;}
        synchronized(pending){java.util.List<Target> targets=pending.get(memoryKey);if(targets!=null){targets.add(new Target(view,unavailable));return;}targets=new java.util.ArrayList<>();targets.add(new Target(view,unavailable));pending.put(memoryKey,targets);}
        android.content.Context app=view.getContext().getApplicationContext();
        diskWorkers.execute(()->{
            try{
                String key=com.kitchenos.common.Pairing.fingerprint(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));File saved=RecipeImageFiles.saved(app,"food-covers",key);
                Bitmap bitmap=readCached(saved,size);if(bitmap!=null){deliver(memoryKey,bitmap);return;}
                if(IngredientPortraits.isOffline(app)||System.currentTimeMillis()<retryAfter.getOrDefault(source,0L)){deliver(memoryKey,null);return;}
                // Slow or unavailable hosts must never hold up pictures already stored locally.
                workers.execute(()->{Bitmap result=null;try{byte[] bytes=download(source);result=decode(bytes,size);try{File dir=RecipeImageFiles.directory(app,"food-covers");save(dir,new File(dir,key),key,bytes);}catch(IOException ignored){}retryAfter.remove(source);}catch(Exception error){retryAfter.put(source,System.currentTimeMillis()+60000);}deliver(memoryKey,result);});
            }catch(Exception error){deliver(memoryKey,null);}
        });
    }
    public static void networkAvailable(){retryAfter.clear();}
    private static void deliver(String memoryKey,Bitmap bitmap){
        if(bitmap!=null)cache.put(memoryKey,bitmap);java.util.List<Target> targets;synchronized(pending){targets=pending.remove(memoryKey);}
        if(targets!=null)for(Target target:targets)target.view.post(()->{if(!memoryKey.equals(target.view.getTag()))return;if(bitmap!=null)show(target.view,memoryKey,bitmap,false);else if(target.unavailable!=null)target.unavailable.run();});
    }
    /** A broken cache entry is a miss, never a permanent failure or a reason to skip the network. */
    private static Bitmap readCached(File file){
        return readCached(file,1440);
    }
    private static Bitmap readCached(File file,int size){
        if(!file.isFile())return null;
        try{if(file.length()<1||file.length()>MAX_BYTES)throw new IOException("invalid cache size");Bitmap bitmap=decode(java.nio.file.Files.readAllBytes(file.toPath()),size);file.setLastModified(System.currentTimeMillis());return bitmap;}
        catch(Exception corrupt){file.delete();return null;}
    }
    private static byte[] download(String source)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)new URL(source).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(12000);
        try{if(connection.getResponseCode()!=200)throw new IOException("image unavailable");String type=connection.getContentType();if(type==null||!type.startsWith("image/"))throw new IOException("not an image");try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>MAX_BYTES)throw new IOException("image too large");output.write(buffer,0,count);}return output.toByteArray();}}
        finally{connection.disconnect();}
    }
    private static Bitmap decode(byte[] bytes,int size)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000)throw new IOException("invalid image dimensions");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>size||bounds.outHeight/options.inSampleSize>size)options.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("invalid image");bitmap.prepareToDraw();return bitmap;
    }
    private static void save(File directory,File saved,String key,byte[] bytes)throws IOException{
        File part=File.createTempFile(key,".part",directory);try{try(OutputStream output=new FileOutputStream(part)){output.write(bytes);}if(!part.renameTo(saved))throw new IOException("cache commit failed");trim(directory);}finally{if(part.exists())part.delete();}
    }
    private static void show(ImageView view,String source,Bitmap bitmap,boolean fade){
        if(!source.equals(view.getTag()))return;view.animate().cancel();view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setPadding(0,0,0,0);view.setImageBitmap(bitmap);view.setImageAlpha(255);
        view.setAlpha(1f);
    }
    private static void trim(File directory){File[] files=directory.listFiles((dir,name)->name.matches("[a-f0-9]{64}"));if(files==null)return;java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));long total=0;for(File file:files)total+=file.length();for(File file:files){if(total<=128L*1024*1024)break;long size=file.length();if(file.delete())total-=size;}}
    private FoodImages(){}
}
