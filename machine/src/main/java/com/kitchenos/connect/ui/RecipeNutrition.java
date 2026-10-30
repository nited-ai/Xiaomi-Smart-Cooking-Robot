package com.kitchenos.connect.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.kitchenos.common.PublicRecipes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Public, per-portion source values, independent of search pages and cooking programs. */
public final class RecipeNutrition implements AutoCloseable {
    private static final String[] KEYS={"calories","protein","fat","carbs","fiber"};
    private static final long REFRESH_MS=24*60*60*1000L,RETRY_MS=60*1000L;
    private final SharedPreferences cache;
    private final NutritionEstimator estimator;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final Map<String,List<Consumer<JSONObject>>> pending=new HashMap<>();
    private final Map<String,Long> attempts=new HashMap<>();
    private boolean closed;

    public RecipeNutrition(Context context){cache=context.getSharedPreferences("public-recipe-nutrition",0);estimator=new NutritionEstimator(context);}
    public static JSONObject clean(JSONObject raw){
        JSONObject result=new JSONObject();if(raw==null)return result;
        for(String key:KEYS){Object value=raw.opt(key);if(value instanceof Number){double number=((Number)value).doubleValue();if(Double.isFinite(number)&&number>=0&&number<=1000000)try{result.put(key,number);}catch(JSONException impossible){throw new IllegalStateException(impossible);}}}
        return result;
    }
    private static void merge(JSONObject target,JSONObject source){
        JSONObject values=clean(source);for(String key:KEYS)if(!target.has(key)&&values.has(key))try{target.put(key,values.get(key));}catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    public static JSONObject known(JSONObject recipe){
        JSONObject result=clean(recipe.optJSONObject("knownNutrition"));
        JSONObject plan=recipe.optJSONObject("planMeal");if(plan!=null)merge(result,plan.optJSONObject("knownNutrition"));
        JSONObject program=recipe.optJSONObject("machineProgram");if(program!=null)merge(result,program.optJSONObject("knownNutrition"));
        return result;
    }
    private static String publicId(JSONObject recipe){String id=recipe.optString("id");return !recipe.optBoolean("private")&&recipe.optJSONObject("planMeal")==null&&!recipe.has("originalServings")&&id.matches("[a-f0-9]{64}")?id:"";}
    private JSONObject saved(String id){try{return new JSONObject(cache.getString(id,"{}"));}catch(JSONException ignored){return new JSONObject();}}
    public JSONObject get(JSONObject recipe,JSONArray... sources){
        JSONObject result=known(recipe);String id=recipe.optString("id");
        // Food-family identities can group different ingredient amounts. Match the exact recipe.
        for(JSONArray rows:sources)for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))merge(result,known(row));}
        String sourceId=publicId(recipe);if(!sourceId.isEmpty())merge(result,saved(sourceId).optJSONObject("values"));
        return result.length()==0?null:result;
    }
    public JSONObject display(JSONObject recipe,JSONArray... sources){
        JSONObject known=get(recipe,sources);if(known!=null&&known.length()==KEYS.length)return known;
        JSONObject estimated=estimator.estimate(recipe);if(estimated==null)return known;
        if(known==null)known=new JSONObject();int count=known.length();merge(known,estimated);
        if(known.length()>count)try{known.put("_estimated",true);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
        return known;
    }
    public void load(JSONObject recipe,Consumer<JSONObject> ready){
        String id=publicId(recipe);if(closed||id.isEmpty())return;
        JSONObject values=known(recipe),previous=saved(id);long now=System.currentTimeMillis();
        if(values!=null&&values.length()==KEYS.length||now-previous.optLong("at")<REFRESH_MS)return;
        List<Consumer<JSONObject>> waiting=pending.get(id);if(waiting!=null){waiting.add(ready);return;}
        if(now-attempts.getOrDefault(id,0L)<RETRY_MS)return;
        attempts.put(id,now);waiting=new ArrayList<>();waiting.add(ready);pending.put(id,waiting);
        worker.execute(()->{
            JSONObject downloaded=null;
            try{JSONObject source=new PublicRecipes().rawDetail(id);if(id.equals(source.optString("id")))downloaded=clean(source.optJSONObject("knownNutrition"));}catch(Exception ignored){}
            final JSONObject result=downloaded;
            main.post(()->{
                if(closed)return;List<Consumer<JSONObject>> listeners=pending.remove(id);
                if(result==null)return;
                try{
                    SharedPreferences.Editor edit=cache.edit();
                    if(cache.getAll().size()>=200&&!cache.contains(id)){
                        String oldest=null;long at=Long.MAX_VALUE;for(String key:cache.getAll().keySet()){long time=saved(key).optLong("at");if(time<at){at=time;oldest=key;}}if(oldest!=null)edit.remove(oldest);
                    }
                    edit.putString(id,new JSONObject().put("at",System.currentTimeMillis()).put("values",result).toString()).apply();
                }catch(JSONException impossible){throw new IllegalStateException(impossible);}
                if(listeners!=null)for(Consumer<JSONObject> listener:listeners)listener.accept(get(recipe));
            });
        });
    }
    @Override public void close(){closed=true;pending.clear();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
