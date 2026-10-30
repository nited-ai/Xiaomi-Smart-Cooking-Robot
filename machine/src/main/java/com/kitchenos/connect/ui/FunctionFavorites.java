package com.kitchenos.connect.ui;

import android.content.Context;
import android.content.SharedPreferences;
import com.kitchenos.connect.kitchen.CloudSession;
import java.util.*;
import org.json.JSONArray;

/** Legacy adapter for historical fixtures. The production catalogue uses FunctionOrder. */
@Deprecated
public final class FunctionFavorites {
    private static final List<String> ALLOWED=Arrays.asList("manual","lidless","scale","stir","emulsify","warm","fry","clean","eggs","water","rice","dough","steam","chop","mix","puree","whip","grind","ice","slow","sousvide","ferment");
    private final SharedPreferences preferences;
    public FunctionFavorites(Context context){String uid=new CloudSession(context).account().optString("uid");String owner;try{owner=com.kitchenos.common.Pairing.fingerprint(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}preferences=context.getSharedPreferences("home-functions-"+owner,0);}
    public List<String> items(){ArrayList<String> result=new ArrayList<>();try{JSONArray saved=new JSONArray(preferences.getString("items","[\"manual\",\"scale\",\"stir\",\"water\",\"fry\",\"whip\",\"clean\",\"mix\"]"));for(int i=0;i<saved.length()&&result.size()<10;i++){String id=saved.optString(i);if(ALLOWED.contains(id)&&!result.contains(id))result.add(id);}}catch(Exception ignored){}for(String id:Arrays.asList("fry","mix","manual","scale","stir","water","whip","clean","emulsify","warm"))if(result.size()<10&&!result.contains(id))result.add(id);return result;}
    public void place(String id,int slot){if(!ALLOWED.contains(id)||slot<0||slot>=10)return;List<String> list=items();int existing=list.indexOf(id);if(existing>=0)Collections.swap(list,existing,slot);else list.set(slot,id);preferences.edit().putString("items",new JSONArray(list).toString()).apply();}
}
