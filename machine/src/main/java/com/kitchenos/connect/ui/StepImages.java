package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Source-matched public photos or private illustrations of the actual prepared step. */
public final class StepImages {
    private StepImages() {}
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Map<String,List<WeakReference<ImageView>>> WAITERS=new HashMap<>();
    private static final Map<String,Long> RETRY=new HashMap<>();
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};

    /** Food and the actual prepared instruction only, never a source URL or arbitrary original index. */
    public static JSONObject nativeRequest(JSONObject recipe,int index)throws Exception{
        JSONObject program=MachineProgram.validate(recipe.getJSONObject("machineProgram"));JSONArray steps=program.getJSONArray("steps");
        if(index<0||index>=steps.length())throw new JSONException("Schritt fehlt");
        String dish=program.getString("title"),instruction=steps.getJSONObject(index).getString("instruction");
        JSONArray ingredients=MachineProgram.ingredients(program),names=new JSONArray();Set<String> unique=new LinkedHashSet<>();
        for(int i=0;i<ingredients.length();i++){String name=ingredients.getJSONObject(i).getString("name");if(!validText(name,160)||name.matches("(?is).*(@|https?:|www\\.).*"))throw new JSONException("Ungültiger Bildbezug");unique.add(name);}
        for(String name:unique)names.put(name);
        if(!validText(dish,160)||!validText(instruction,1200)||names.length()<1||names.length()>30)throw new JSONException("Schrittbild benötigt eine vollständige, unterstützte Anleitung");
        return new JSONObject().put("style","native-recipe-step-v1").put("dish",dish).put("ingredients",names).put("step",instruction);
    }
    public static String cacheKey(String uid,JSONObject recipe,int index)throws Exception{
        if(uid==null||uid.isEmpty()||!recipe.optString("programDigest").matches("[a-f0-9]{64}"))throw new JSONException("Rezeptfassung fehlt");
        JSONObject program=MachineProgram.validate(recipe.getJSONObject("machineProgram")),step=program.getJSONArray("steps").getJSONObject(index);
        String identity=uid+"\n"+recipe.optString("id")+"\n"+recipe.getString("programDigest")+"\n"+step.getString("id")+"\n"+index+"\n"+exactPublicSource(recipe,index)+"\n"+program;
        return Pairing.fingerprint(identity.getBytes(StandardCharsets.UTF_8));
    }
    public static boolean exactPublicSource(JSONObject recipe,int index){return !recipe.optBoolean("private")&&recipe.optBoolean("stepImagesMatch")&&recipe.optString("id").matches("[a-f0-9]{64}")&&index>=0&&index<30;}
    private static boolean validText(String value,int max){return value!=null&&!value.trim().isEmpty()&&value.length()<=max&&!value.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f].*");}
    public static void load(Context context,ImageView view,JSONObject recipe,int index){
        // Generic food imagery cannot reliably depict the C3 or its closed lid.
        // Machine steps retain the host's explicit appliance-state illustration.
        JSONObject program=recipe.optJSONObject("machineProgram");JSONArray steps=program==null?null:program.optJSONArray("steps");JSONObject step=steps==null?null:steps.optJSONObject(index);
        if(step==null||"machine".equals(step.optString("kind")))return;
        if(program.optString("title").equals("Schokoladen-Brownies")&&step.optString("kind").equals("prepare")&&step.optString("title").equals("Backform vorbereiten")&&step.optString("instruction").contains("20 × 20 cm")&&step.optString("instruction").contains("Backpapier"))try(InputStream input=context.getAssets().open("visuals/brownie-lined-pan-v1.png")){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap picture=BitmapFactory.decodeStream(input,null,options);if(picture!=null){view.setImageBitmap(picture);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setContentDescription("Mit Backpapier ausgelegte quadratische Backform · KI-Beispielabbildung");return;}}catch(IOException unavailable){}
        Context app=context.getApplicationContext();CloudSession session=new CloudSession(app);String uid=session.account().optString("uid");if(uid.isEmpty())return;
        final String key;final JSONObject request;final boolean publicSource=exactPublicSource(recipe,index);final String publicId=recipe.optString("id");
        try{request=publicSource?null:nativeRequest(recipe,index);key=cacheKey(uid,recipe,index);}catch(Exception unsupported){return;}
        view.setTag(key);Bitmap cached=MEMORY.get(key);if(cached!=null){show(app,view,uid,key,index,cached,false);return;}
        File directory=new File(app.getCacheDir(),"prepared-step-images-v1"),file=new File(directory,key+".image");JSONObject preferences=new KitchenStore(app).snapshot().optJSONObject("preferences");
        if(!file.exists()&&(preferences==null||!preferences.optBoolean("aiConsent")))return;
        synchronized(WAITERS){List<WeakReference<ImageView>> pending=WAITERS.get(key);if(pending!=null){pending.add(new WeakReference<>(view));return;}if(!file.exists()&&RETRY.getOrDefault(key,0L)>System.currentTimeMillis())return;List<WeakReference<ImageView>> pendingNew=new ArrayList<>();pendingNew.add(new WeakReference<>(view));WAITERS.put(key,pendingNew);}
        WORK.execute(()->{
            boolean success=false;
            try{
                if(!uid.equals(new CloudSession(app).account().optString("uid")))return;
                if(!directory.isDirectory()&&!directory.mkdirs())return;
                if(!file.exists()){
                    JSONObject currentPrefs=new KitchenStore(app).snapshot().optJSONObject("preferences");if(currentPrefs==null||!currentPrefs.optBoolean("aiConsent"))return;
                    byte[] bytes;
                    if(publicSource){JSONObject answer=session.recipeStep(publicId,index);if(!"ready".equals(answer.optString("status")))return;bytes=publicImage(publicId,index);}
                    else {JSONObject answer=session.nativeRecipeStep(request.getString("dish"),request.getJSONArray("ingredients"),request.getString("step"));bytes=decodeEnvelope(answer.getJSONObject("image"));}
                    if(!uid.equals(new CloudSession(app).account().optString("uid")))return;validateRaster(bytes);
                    File part=new File(directory,key+".part");try(OutputStream out=new FileOutputStream(part)){out.write(bytes);}if(!part.renameTo(file)){part.delete();return;}trim(directory);
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000){file.delete();return;}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>768||bounds.outHeight/options.inSampleSize>768)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(bitmap==null){file.delete();return;}
                if(!uid.equals(new CloudSession(app).account().optString("uid")))return;MEMORY.put(key,bitmap);
                List<WeakReference<ImageView>> targets;synchronized(WAITERS){targets=WAITERS.remove(key);RETRY.remove(key);}success=true;
                if(targets!=null)for(WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null)target.post(()->show(app,target,uid,key,index,bitmap,true));}
            }catch(Exception unavailable){/* The schematic and full written instruction remain visible. */}
            finally{if(!success)synchronized(WAITERS){WAITERS.remove(key);RETRY.put(key,System.currentTimeMillis()+15*60*1000);}}
        });
    }
    private static void show(Context app,ImageView view,String uid,String key,int index,Bitmap bitmap,boolean fade){
        if(!key.equals(view.getTag())||!uid.equals(new CloudSession(app).account().optString("uid")))return;
        view.setPadding(0,0,0,0);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImageBitmap(bitmap);view.setContentDescription("KI-Illustration zur Anleitung von Schritt "+(index+1));
        view.animate().cancel();view.setAlpha(1f);
    }
    private static byte[] decodeEnvelope(JSONObject image)throws Exception{
        String mime=image.getString("mimeType"),data=image.getString("data");if(!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime)||data.isEmpty()||data.length()>2000000||!data.matches("[A-Za-z0-9+/]+={0,2}"))throw new IOException("Ungültiges Schrittbild");
        byte[] bytes=Base64.decode(data,Base64.NO_WRAP);boolean jpeg=bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216;boolean png=bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;boolean webp=bytes.length>=12&&bytes[0]==82&&bytes[1]==73&&bytes[2]==70&&bytes[3]==70&&bytes[8]==87&&bytes[9]==69&&bytes[10]==66&&bytes[11]==80;
        if(!(mime.equals("image/jpeg")&&jpeg||mime.equals("image/png")&&png||mime.equals("image/webp")&&webp))throw new IOException("Ungültiges Bildformat");return bytes;
    }
    private static void validateRaster(byte[] bytes)throws IOException{if(bytes.length<12||bytes.length>1500000)throw new IOException("Schrittbild zu groß");BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>20000000)throw new IOException("Ungültige Bildgröße");}
    private static byte[] publicImage(String id,int index)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL("https://app.kalomatik.ai/api/recipes/"+id+"/steps/"+index+"/image").openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(30000);connection.setRequestProperty("Accept","image/*");
        try{if(connection.getResponseCode()!=200)throw new IOException("Schrittbild noch nicht bereit");try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1500000)throw new IOException("Schrittbild zu groß");out.write(buffer,0,n);}return out.toByteArray();}}finally{connection.disconnect();}
    }
    private static void trim(File directory){File[] files=directory.listFiles((dir,name)->name.endsWith(".image"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File file:files)total+=file.length();for(File file:files){if(total<=64000000)break;long size=file.length();if(file.delete())total-=size;}}
}
