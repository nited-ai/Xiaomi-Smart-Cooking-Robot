package com.kitchenos.connect.ui;

import android.content.Context;
import com.kitchenos.common.Pairing;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Account-scoped identities only. Recipe contents continue to come from their catalogue. */
public final class RecentCooking {
    private final android.content.SharedPreferences prefs;private final String key;
    public RecentCooking(Context context,String owner){prefs=context.getSharedPreferences("recent-cooking",0);try{key=Pairing.fingerprint(owner.getBytes(StandardCharsets.UTF_8));}catch(Exception unavailable){throw new IllegalStateException(unavailable);}}
    public List<String> ids(){List<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(Exception ignored){}return out;}
    public void record(String id){if(id==null||id.isEmpty())return;List<String> all=ids();all.remove(id);all.add(0,id);while(all.size()>20)all.remove(all.size()-1);prefs.edit().putString(key,new JSONArray(all).toString()).apply();}
}
