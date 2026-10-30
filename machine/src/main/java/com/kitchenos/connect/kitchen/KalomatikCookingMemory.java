package com.kitchenos.connect.kitchen;

import android.content.*;
import android.net.Uri;
import com.kitchenos.common.Pairing;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Explicit user-confirmed memories through the existing Calomatik sync contract.
 * No profile replacement, model inference or meal-time inference. Durable exact retries. */
public final class KalomatikCookingMemory {
    public interface Transport {JSONObject call(JSONObject request)throws Exception;}
    private static final Object LOCK=new Object();
    private final Context context;private final String owner;private final SharedPreferences prefs;private final Transport transport;
    public KalomatikCookingMemory(Context context)throws Exception{this(context,new CloudSession(context)::cookingMemorySync);}
    public KalomatikCookingMemory(Context context,Transport transport)throws Exception{
        this.context=context.getApplicationContext();this.transport=transport;owner=new CloudSession(context).account().optString("uid");if(owner.isEmpty())throw new IOException("Bitte mit deinem Kalomatik-Konto anmelden.");prefs=context.getSharedPreferences("cooking-memories-"+Pairing.fingerprint(owner.getBytes(StandardCharsets.UTF_8)),0);
    }
    private void owner()throws Exception{if(!owner.equals(new CloudSession(context).account().optString("uid")))throw new IOException("Dein Konto hat sich geändert.");}
    private void remembering()throws Exception{JSONObject settings=new KalomatikPlans(context).sharedPreferences();if(!settings.optBoolean("rememberKnown"))throw new IOException("Die Assistenteneinstellungen müssen zuerst mit Calomatik abgeglichen werden.");if(!settings.optBoolean("remember"))throw new IOException("Erinnern ist in deinen Calomatik-Einstellungen ausgeschaltet.");}
    public JSONArray confirmed()throws Exception{owner();JSONArray items=new JSONArray();for(Map.Entry<String,?> row:prefs.getAll().entrySet())if(row.getKey().startsWith("confirmed:")){JSONObject entity=new JSONObject(String.valueOf(row.getValue()));if(!entity.optBoolean("deleted"))items.put(entity);}return items;}
    public JSONObject remember(String text)throws Exception{
        owner();remembering();text=text==null?"":text.trim();if(text.isEmpty()||text.length()>240||text.matches("(?s).*[\\x00-\\x1f].*"))throw new IOException("Bitte eine Vorliebe mit höchstens 240 Zeichen eingeben.");
        JSONArray local=confirmed();for(int i=0;i<local.length();i++){JSONObject existing=local.getJSONObject(i);if(text.equals(existing.getJSONObject("data").optString("text"))){JSONObject response=transport.call(new JSONObject().put("action","read").put("keys",new JSONArray().put(new JSONObject().put("type","memory").put("id",existing.getString("id")))));owner();JSONArray records=response.optJSONArray("entities");JSONObject current=records==null?null:records.optJSONObject(0);if(current!=null&&current.optJSONObject("data")!=null&&current.getJSONObject("data").optString("status").equals("active")&&!current.optBoolean("deleted")&&existing.getString("id").equals(current.optString("id"))&&text.equals(current.optJSONObject("data")==null?"":current.getJSONObject("data").optString("text"))){new KalomatikPlans(context).acceptConfirmedMemory(current);return current;}}}
        String key="pending:"+Pairing.fingerprint(text.getBytes(StandardCharsets.UTF_8));JSONObject operation;
        synchronized(LOCK){String pending=prefs.getString(key,"");operation=pending.isEmpty()?operation(text,UUID.randomUUID().toString(),UUID.randomUUID().toString(),Instant.now().toString()):new JSONObject(pending);if(pending.isEmpty()&&!prefs.edit().putString(key,operation.toString()).commit())throw new IOException("Die Vorliebe konnte nicht vorgemerkt werden.");}
        return send(key,operation);
    }
    private JSONObject send(String key,JSONObject operation)throws Exception{
        owner();remembering();JSONObject response=transport.call(new JSONObject().put("action","push").put("operation",new JSONObject(operation.toString())));owner();JSONArray entities=response.optJSONArray("entities");JSONObject expected=operation.getJSONArray("changes").getJSONObject(0).getJSONObject("entity");
        if(!"acknowledged".equals(response.optString("status"))||!operation.getString("operationId").equals(response.optString("operationId"))||entities==null||entities.length()!=1)throw new IOException("Das Speichern ist noch nicht bestätigt. Du kannst es erneut versuchen.");
        JSONObject actual=entities.getJSONObject(0);if(!expected.getString("id").equals(actual.optString("id"))||!"memory".equals(actual.optString("type"))||actual.optInt("schemaVersion")!=1||actual.optBoolean("deleted")!=expected.getBoolean("deleted")||response.optLong("revision")<1||actual.optLong("revision")!=response.getLong("revision")||!same(expected.getJSONObject("data"),actual.optJSONObject("data"))||!same(expected.getJSONObject("source"),actual.optJSONObject("source")))throw new IOException("Die bestätigte Erinnerung passt nicht zur Übertragung.");
        new KalomatikPlans(context).acceptConfirmedMemory(actual);
        if(!prefs.edit().putString("confirmed:"+actual.getString("id"),actual.toString()).remove(key).commit())throw new IOException("Gespeichert; die lokale Bestätigung fehlt noch. Bitte erneut versuchen.");return new JSONObject(actual.toString());
    }
    public static JSONObject operation(String text,String operationId,String id,String at)throws Exception{
        UUID.fromString(operationId);UUID.fromString(id);Instant.parse(at);if(text==null||text.trim().isEmpty()||text.length()>240)throw new IOException("Ungültige Vorliebe.");
        JSONObject data=new JSONObject().put("id",id).put("key","cooking.manual."+id).put("text",text).put("scope","home_cooking").put("kind","preference").put("status","active").put("basis","user_confirmed").put("createdAt",at).put("updatedAt",at).put("expiresOn",JSONObject.NULL);
        String entityId="v4:"+Uri.encode(new JSONArray().put("settings").put("assistant-state").put("memories").put(id).toString());JSONObject entity=new JSONObject().put("type","memory").put("id",entityId).put("schemaVersion",1).put("deleted",false).put("data",data).put("source",new JSONObject().put("store","settings").put("key","assistant-state").put("path",new JSONArray().put("memories").put(id)));
        return new JSONObject().put("schemaVersion",1).put("kind","entities.batch").put("operationId",operationId).put("occurredAt",at).put("changes",new JSONArray().put(new JSONObject().put("baseRevision",0).put("localVersion",1).put("entity",entity)));
    }
    private static boolean same(Object a,Object b)throws Exception{
        if(a==null||a==JSONObject.NULL)return b==null||b==JSONObject.NULL;if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;Iterator<String> keys=x.keys();while(keys.hasNext()){String k=keys.next();if(!y.has(k)||!same(x.get(k),y.get(k)))return false;}return true;}if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!same(x.get(i),y.get(i)))return false;return true;}return a.equals(b);
    }
}
