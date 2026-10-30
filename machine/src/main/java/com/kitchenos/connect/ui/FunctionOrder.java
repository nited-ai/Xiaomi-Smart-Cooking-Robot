package com.kitchenos.connect.ui;

import android.content.Context;
import android.content.SharedPreferences;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.common.Pairing;
import java.util.*;
import org.json.JSONArray;

/** Complete account-scoped catalogue order. Preserves the former shortcut order on migration. */
public final class FunctionOrder {
    private static final List<String> ALL=Collections.unmodifiableList(Arrays.asList("manual","scale","stir","water","fry","whip","clean","mix","emulsify","warm","lidless","eggs","rice","dough","steam","chop","puree","grind","ice","slow","sousvide","ferment","thicken","turbo","caramel"));
    private final SharedPreferences preferences;
    public FunctionOrder(Context context){String uid=new CloudSession(context).account().optString("uid");try{preferences=context.getSharedPreferences("home-functions-"+Pairing.fingerprint(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)),0);}catch(Exception e){throw new IllegalStateException(e);}}
    public List<String> items(){ArrayList<String> result=new ArrayList<>();try{JSONArray saved=new JSONArray(preferences.getString("order",preferences.getString("items","[]")));for(int i=0;i<saved.length();i++){String id=saved.optString(i);if(ALL.contains(id)&&!result.contains(id))result.add(id);}}catch(Exception ignored){}for(String id:ALL)if(!result.contains(id))result.add(id);return result;}
    /** Scale is hidden until explicitly enabled, including selections saved before this default existed. */
    public List<String> visibleItems(){List<String> order=items();if(preferences.contains("visible"))try{JSONArray stored=new JSONArray(preferences.getString("visible","[]"));Set<String> selected=new HashSet<>();for(int i=0;i<stored.length();i++)selected.add(stored.optString(i));order.removeIf(id->!selected.contains(id));}catch(Exception ignored){}if(!preferences.getBoolean("scale-opt-in",false))order.remove("scale");return order;}
    public boolean isVisible(String id){return visibleItems().contains(id);}
    /** Membership never removes a function from its saved catalogue position. */
    public void setVisible(String id,boolean visible){if(!ALL.contains(id))return;List<String> selected=visibleItems();if(visible&&!selected.contains(id))selected.add(id);else if(!visible)selected.remove(id);SharedPreferences.Editor edit=preferences.edit().putString("visible",new JSONArray(selected).toString());if(id.equals("scale"))edit.putBoolean("scale-opt-in",visible);edit.apply();}
    /** Reorder selected functions while hidden catalogue entries keep their slots. */
    public void setVisibleOrder(List<String> selected){
        List<String> visible=visibleItems();if(selected.size()!=visible.size()||new HashSet<>(selected).size()!=visible.size()||!new HashSet<>(selected).equals(new HashSet<>(visible)))return;
        List<String> order=items();Set<String> shown=new HashSet<>(visible);int next=0;
        for(int i=0;i<order.size();i++)if(shown.contains(order.get(i)))order.set(i,selected.get(next++));
        preferences.edit().putString("order",new JSONArray(order).toString()).apply();
    }
    /** Insert at the destination and shift the intervening functions. */
    public void move(String id,int destination){if(!ALL.contains(id)||destination<0||destination>=ALL.size())return;List<String> order=items();order.remove(id);order.add(destination,id);preferences.edit().putString("order",new JSONArray(order).toString()).apply();}
}
