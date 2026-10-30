package com.kitchenos.connect.ui;

import android.content.Context;
import android.util.AtomicFile;
import com.kitchenos.common.Pairing;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Public catalogue responses only. Private recipes keep their existing account-bound store. */
public final class RecipeCatalogueCache {
    public interface Fetch { JSONObject load() throws Exception; }
    private static final long FRESH_MS=6*60*60*1000L, RETRY_MS=60*1000L;
    private final File directory;
    private final BooleanSupplier online;
    private final ConcurrentHashMap<String,FutureTask<JSONObject>> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Long> attempts=new ConcurrentHashMap<>();
    public RecipeCatalogueCache(Context context){this(context,()->!IngredientPortraits.isOffline(context));}
    public RecipeCatalogueCache(Context context,BooleanSupplier online){directory=new File(context.getFilesDir(),"recipe-library-v1");this.online=online;}
    private File file(String key)throws Exception{return new File(directory,Pairing.fingerprint(key.getBytes(StandardCharsets.UTF_8))+".json");}
    private JSONObject envelope(String key){try{AtomicFile file=new AtomicFile(file(key));if(file.getBaseFile().length()>2500000)return null;return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));}catch(Exception absent){return null;}}
    public JSONObject read(String key){JSONObject saved=envelope(key);return saved==null?null:saved.optJSONObject("value");}
    /** Worker-only: fresh responses need no network, stale responses remain usable offline. */
    public JSONObject get(String key,Fetch fetch)throws Exception{
        JSONObject saved=envelope(key),value=saved==null?null:saved.optJSONObject("value");long now=System.currentTimeMillis();
        if(value!=null&&now>=saved.optLong("at")&&now-saved.optLong("at")<FRESH_MS)return value;
        FutureTask<JSONObject> running=pending.get(key);if(running!=null)return running.get();
        if(!online.getAsBoolean()||now-attempts.getOrDefault(key,0L)<RETRY_MS){if(value!=null)return value;throw new IOException("Dieses Rezept ist noch nicht offline gespeichert.");}
        FutureTask<JSONObject> task=new FutureTask<>(()->{attempts.put(key,System.currentTimeMillis());try{JSONObject result=fetch.load();try{save(key,result);}catch(IOException storageUnavailable){/* Display a valid response even if local storage is full. */}return result;}catch(Exception error){if(value!=null)return value;throw error;}});
        FutureTask<JSONObject> existing=pending.putIfAbsent(key,task);if(existing!=null)return existing.get();
        try{task.run();return task.get();}finally{pending.remove(key,task);}
    }
    private void save(String key,JSONObject value)throws Exception{
        directory.mkdirs();byte[] bytes=new JSONObject().put("at",System.currentTimeMillis()).put("value",value).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2500000)throw new IOException("Rezeptantwort ist zu groß");
        AtomicFile file=new AtomicFile(file(key));FileOutputStream output=null;try{output=file.startWrite();output.write(bytes);file.finishWrite(output);}catch(IOException error){if(output!=null)file.failWrite(output);throw error;}
        File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();for(File f:files){if(total<=64L*1024*1024)break;long size=f.length();if(!f.equals(file.getBaseFile())&&f.delete())total-=size;}
    }
    public JSONObject page(String query,String cursor,String category)throws Exception{return get("page:"+new org.json.JSONArray().put(query).put(cursor).put(category),()->new com.kitchenos.common.PublicRecipes().searchCategory(query,cursor,category));}
    public JSONObject categories()throws Exception{return get("categories",()->new com.kitchenos.common.PublicRecipes().categories());}
    public JSONObject detail(String id)throws Exception{return get("detail:"+id,()->new com.kitchenos.common.PublicRecipes().rawDetail(id));}
}
