package com.kitchenos.connect.ui;
import android.content.Context;
import android.content.SharedPreferences;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.CloudSession;
import org.json.*;
/** Keeps source cards reachable after leaving a public search page; owned by the signed-in account. */
public final class FavoriteRecipeCards {
    private static SharedPreferences store(Context c,String owner){try{return c.getSharedPreferences("favorite-cards-"+Pairing.fingerprint(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)),0);}catch(Exception e){throw new IllegalStateException(e);}}
    public static void keep(Context c,String owner,String id,JSONObject card,boolean enabled){if(!owner.equals(new CloudSession(c).account().optString("uid")))return;if(!id.matches("[a-f0-9]{64}"))return;SharedPreferences.Editor edit=store(c,owner).edit();if(enabled&&card.toString().length()<=131072)edit.putString(id,card.toString());else edit.remove(id);edit.commit();}
    public static JSONArray read(Context c){String owner=new CloudSession(c).account().optString("uid");JSONArray cards=new JSONArray();for(Object value:store(c,owner).getAll().values()){if(cards.length()>=300)break;if(value instanceof String)try{cards.put(new JSONObject((String)value));}catch(Exception invalid){}}return cards;}
    private FavoriteRecipeCards(){}
}
