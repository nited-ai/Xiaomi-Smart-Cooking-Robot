package com.kitchenos.connect.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Taxonomy and memberships come from the shared recipe database, never from title heuristics. */
final class RecipeCategories {
    private final Context context;
    private JSONArray categories=new JSONArray();
    RecipeCategories(Context context){this.context=context;try{categories=new JSONArray(context.getSharedPreferences("recipe-category-catalog",0).getString("items","[]"));}catch(JSONException ignored){}}
    void update(JSONObject response)throws JSONException{JSONArray values=response.getJSONArray("categories");if(values.length()>100)throw new JSONException("Too many categories");for(int i=0;i<values.length();i++){JSONObject v=values.getJSONObject(i);if(!v.getString("id").matches("[a-z0-9-]{1,64}")||v.getString("title").length()>80||v.getJSONArray("recipeIds").length()>5000)throw new JSONException("Invalid category");}categories=new JSONArray(values.toString());context.getSharedPreferences("recipe-category-catalog",0).edit().putString("items",categories.toString()).apply();}
    private SharedPreferences prefs(String owner){return context.getSharedPreferences("recipe-category-selection",0);}
    private String key(String owner){return "selected:"+owner;}
    List<JSONObject> all(){List<JSONObject> result=new ArrayList<>();for(int i=0;i<categories.length();i++){JSONObject c=categories.optJSONObject(i);if(c!=null)result.add(c);}return result;}
    List<String> selected(String owner){String stored=prefs(owner).getString(key(owner),null);List<String> ids=new ArrayList<>();if(stored!=null)try{JSONArray values=new JSONArray(stored);for(int i=0;i<values.length();i++)ids.add(values.getString(i));return ids;}catch(JSONException ignored){}for(JSONObject c:all())if(c.optBoolean("default",false))ids.add(c.optString("id"));if(ids.isEmpty())for(JSONObject c:all())ids.add(c.optString("id"));return ids;}
    void save(String owner,List<String> ids){prefs(owner).edit().putString(key(owner),new JSONArray(ids).toString()).apply();}
    void reset(String owner){prefs(owner).edit().remove(key(owner)).apply();}
    boolean contains(JSONObject category,JSONObject recipe){return has(category.optJSONArray("recipeIds"),recipe.optString("id"))||has(category.optJSONArray("foodIdentities"),recipe.optString("foodIdentity"));}
    private boolean has(JSONArray values,String id){if(id.isEmpty())return false;for(int i=0;values!=null&&i<values.length();i++)if(id.equals(values.optString(i)))return true;return false;}
}
