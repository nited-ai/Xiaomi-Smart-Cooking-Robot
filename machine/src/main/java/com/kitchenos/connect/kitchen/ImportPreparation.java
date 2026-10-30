package com.kitchenos.connect.kitchen;

import android.content.*;
import com.kitchenos.common.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Receipt creates data, never a device command. Work survives leaving the recipe screen. */
public final class ImportPreparation {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE=Collections.synchronizedSet(new HashSet<>());
    public static String status(Context context,String recipeId){
        String uid=new CloudSession(context).account().optString("uid");
        return context.getSharedPreferences("import-preparation",0).getString(key(uid,recipeId),"");
    }
    private static String key(String uid,String id){return UUID.nameUUIDFromBytes((uid+":"+id).getBytes(StandardCharsets.UTF_8)).toString();}
    public static void enqueue(Context context,Recipe recipe,boolean retry){enqueue(context,recipe,retry,new CloudSession(context).account().optString("uid"));}
    public static void enqueue(Context context,Recipe recipe,boolean retry,String expectedUid){
        Context app=context.getApplicationContext();CloudSession session=new CloudSession(app);String uid=session.account().optString("uid");
        if(uid.isEmpty()||!uid.equals(expectedUid)||recipe.servings==0||!new KitchenStore(app).snapshot().optJSONObject("preferences").optBoolean("aiConsent"))return;
        String key=key(uid,recipe.id);String previous=status(app,recipe.id);if(!retry&&!previous.isEmpty()&&!previous.startsWith("Die Maschinenanleitung wird vorbereitet")||!ACTIVE.add(key))return;
        android.content.SharedPreferences requests=app.getSharedPreferences("import-preparation",0);String request=retry?UUID.randomUUID().toString():requests.getString(key+"-request-"+com.kitchenos.connect.control.ControlProfile.ID,UUID.randomUUID().toString());requests.edit().putString(key+"-request-"+com.kitchenos.connect.control.ControlProfile.ID,request).apply();
        final int portions=new KitchenStore(app).snapshot().optJSONObject("preferences").optInt("servings",2);if(portions<1||portions>20){ACTIVE.remove(key);set(app,key,"Portionen für diesen Kochvorgang bitte wählen");return;}
        set(app,key,"Die Maschinenanleitung wird vorbereitet …");
        WORK.execute(()->{
            try{
                if(!uid.equals(new CloudSession(app).account().optString("uid")))return;
                JSONObject result=session.api("/v1/recipes/prepare",new JSONObject().put("requestId",request).put("recipeId",recipe.id).put("recipe",recipe.json()).put("servings",portions).put("consent",true).put("controlProfileId",com.kitchenos.connect.control.ControlProfile.ID));
                if(!uid.equals(new CloudSession(app).account().optString("uid")))return;
                if(result.getBoolean("compatible")){new MachineProgramStore(app).save(result,uid);set(app,key,"Bereit zum Kochen");}
                else set(app,key,result.optString("reason","Dieses Gericht benötigt ein weiteres Kochgerät."));
            }catch(Exception error){if(uid.equals(new CloudSession(app).account().optString("uid")))set(app,key,error.getMessage()==null?"Die Anleitung konnte nicht erstellt werden. Öffne das Rezept für einen neuen Versuch.":error.getMessage());}
            finally{ACTIVE.remove(key);app.sendBroadcast(new Intent("com.kitchenos.connect.RECIPES_CHANGED").setPackage(app.getPackageName()),"com.kitchenos.connect.INTERNAL");}
        });
    }
    private static void set(Context app,String key,String text){app.getSharedPreferences("import-preparation",0).edit().putString(key,text).apply();}
}
