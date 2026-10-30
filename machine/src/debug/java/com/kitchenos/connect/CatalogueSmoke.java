package com.kitchenos.connect;

import android.app.Instrumentation;
import android.os.*;
import com.kitchenos.common.Recipe;
import com.kitchenos.connect.kitchen.KalomatikCatalogue;
import org.json.*;

/** Bounded public reads only. No account credentials, AI requests or cloud writes. */
public final class CatalogueSmoke extends Instrumentation {
    private boolean live;
    @Override public void onCreate(Bundle args){super.onCreate(args);live=args!=null&&args.getString("live","").equals("true");start();}
    private void check(boolean pass,String reason)throws Exception{if(!pass)throw new Exception(reason);}
    private JSONObject fixture()throws Exception{
        return new JSONObject().put("id",new String(new char[64]).replace('\0','a')).put("title","Reis mit Öl").put("portion",1).put("guideStatus","ready")
            .put("ingredients",new JSONArray().put(new JSONObject().put("id","rice").put("name","Reis").put("amount",100).put("unit","g")))
            .put("guide",new JSONObject().put("equipment",new JSONArray()).put("tips",new JSONArray()).put("steps",new JSONArray().put(new JSONObject().put("title","Garen").put("instruction","Nach Anleitung garen.").put("ingredients",new JSONArray().put(new JSONObject().put("ingredientId","rice").put("share",1))))));
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        check(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"),"Nur isolierter Emulator");
        JSONObject source=fixture();Recipe original=KalomatikCatalogue.convert(source);
        check(original.servings==1&&original.ingredients.getString(0).equals("100 g Reis"),"Öffentliche Ein-Portion-Basis erhalten");
        check(KalomatikCatalogue.convert(fixture()).id.equals(original.id),"Erneuter Import muss identisch sein");
        source.put("title","Anderer Reis");check(!KalomatikCatalogue.convert(source).id.equals(original.id),"Geänderte Inhalte dürfen keine alte Bestätigung erben");
        source=fixture();source.getJSONObject("guide").getJSONArray("steps").getJSONObject(0).getJSONArray("ingredients").getJSONObject(0).put("share",0.5);
        boolean rejected=false;try{KalomatikCatalogue.convert(source);}catch(JSONException expected){rejected=true;}check(rejected,"Unvollständige Zutatenbezüge ablehnen");
        source=fixture();source.put("title","Re\u0001is");rejected=false;try{KalomatikCatalogue.convert(source);}catch(JSONException expected){rejected=true;}check(rejected,"Steuerzeichen ablehnen");
        if(live){KalomatikCatalogue api=new KalomatikCatalogue();JSONObject page=api.search("","");JSONArray items=page.getJSONArray("items");check(items.length()>0,"Live-Katalog muss mindestens ein vollständiges Rezept liefern");Recipe recipe=api.detail(items.getJSONObject(0).getString("id"));check(recipe.steps.length()>0&&recipe.ingredients.length()>0,"Vollständige Live-Anleitung fehlt");RecipeStore store=new RecipeStore(getTargetContext(),"catalogue-smoke");try{store.save(recipe);Recipe loaded=null;for(Recipe row:store.all())if(row.id.equals(recipe.id))loaded=row;check(loaded!=null&&loaded.json().toString().equals(recipe.json().toString()),"Übernahme und erneutes Lesen des Live-Rezepts");}finally{store.delete(recipe);}result.putString("live","PASS: existing public API search, complete guide conversion, isolated local save/readback/delete; zero cloud writes");}
        result.putString("result","PASS: catalogue portion basis, stable import, content revision, incomplete ingredient rejection, control characters");finish(-1,result);
    }catch(Throwable error){android.util.Log.e("KitchenOSCatalogueSmoke","Failed",error);result.putString("failure",error.toString());finish(0,result);}}
}
