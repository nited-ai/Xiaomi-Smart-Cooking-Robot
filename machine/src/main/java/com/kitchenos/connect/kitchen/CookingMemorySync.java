package com.kitchenos.connect.kitchen;
import android.net.Uri;
import org.json.*;
import java.io.IOException;
import java.util.UUID;
/** Narrow adapter for exactly one assistant-state memory, not an arbitrary sync read. */
public final class CookingMemorySync {
 public interface Transport {JSONObject call(JSONObject request)throws Exception;}
 public static JSONObject call(JSONObject request,Transport transport)throws Exception{
  String action=request.optString("action");if(action.equals("read")){JSONArray keys=request.optJSONArray("keys");JSONObject key=keys==null?null:keys.optJSONObject(0);if(request.length()!=2||keys==null||keys.length()!=1||key==null||key.length()!=2||!"memory".equals(key.optString("type"))||memoryId(key.optString("id"))==null)throw new IOException("Ungültiger Erinnerungsabruf.");}
  else if(action.equals("push")){JSONObject operation=request.optJSONObject("operation");JSONArray changes=operation==null?null:operation.optJSONArray("changes");JSONObject change=changes==null?null:changes.optJSONObject(0),entity=change==null?null:change.optJSONObject("entity"),data=entity==null?null:entity.optJSONObject("data"),source=entity==null?null:entity.optJSONObject("source");String id=entity==null?null:memoryId(entity.optString("id"));JSONArray path=source==null?null:source.optJSONArray("path");if(request.length()!=2||changes==null||changes.length()!=1||entity==null||!"memory".equals(entity.optString("type"))||id==null||data==null||!id.equals(data.optString("id"))||source==null||!"settings".equals(source.optString("store"))||!"assistant-state".equals(source.optString("key"))||path==null||path.length()!=2||!"memories".equals(path.optString(0))||!id.equals(path.optString(1)))throw new IOException("Ungültige Erinnerungsübertragung.");}
  else throw new IOException("Dieser Erinnerungsauftrag ist nicht freigegeben.");return transport.call(new JSONObject(request.toString()));
 }
 private static String memoryId(String encoded){try{if(!encoded.startsWith("v4:")||encoded.length()>512)return null;JSONArray path=new JSONArray(Uri.decode(encoded.substring(3)));if(path.length()!=4||!path.getString(0).equals("settings")||!path.getString(1).equals("assistant-state")||!path.getString(2).equals("memories")||!encoded.equals("v4:"+Uri.encode(path.toString())))return null;String id=path.getString(3);return UUID.fromString(id).toString().equals(id)?id:null;}catch(Exception invalid){return null;}}
 private CookingMemorySync(){}
}
