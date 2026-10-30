package com.kitchenos.connect.ui;

import com.kitchenos.connect.kitchen.MachineProgram;
import com.kitchenos.common.Recipe;
import java.util.*;
import org.json.*;

/** Worker-owned index. Validate programmes and normalize text once per catalogue revision. */
final class RecipeSearchIndex {
    private static final class Entry {
        final JSONObject recipe; final RecipeSearchText text;
        Entry(JSONObject value){recipe=value;List<String> ingredients=new ArrayList<>();JSONArray items=value.optJSONArray("ingredients");for(int i=0;items!=null&&i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null)ingredients.add(item.optString("name"));}text=new RecipeSearchText(value.optString("title"),ingredients);}
    }
    private final Set<String> preparedIds=new HashSet<>();
    private final List<Entry> prepared=new ArrayList<>(),online=new ArrayList<>();
    RecipeSearchIndex(JSONArray local,JSONArray shared,JSONArray catalogue,int servings){
        LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
        for(JSONArray source:Arrays.asList(local,shared))for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item==null||!MachineProgram.executable(item.optJSONObject("machineProgram")))continue;preparedIds.add(item.optString("id"));try{preparedIds.add(MachineProgram.asRecipe(item).id);}catch(Exception ignored){}String id=key(item);JSONObject previous=unique.get(id);if(previous==null||distance(item,servings)<distance(previous,servings))unique.put(id,item);}
        for(JSONObject item:unique.values())prepared.add(new Entry(item));
        for(int i=0;i<catalogue.length();i++){JSONObject item=catalogue.optJSONObject(i);if(item!=null)online.add(new Entry(item));}
    }
    List<JSONObject> search(String query,boolean discover){
        RecipeSearchText.Query request=new RecipeSearchText.Query(query);LinkedHashMap<String,Entry> unique=new LinkedHashMap<>();
        for(Entry entry:prepared)unique.put(key(entry.recipe),entry);
        if(discover)for(Entry entry:online)unique.putIfAbsent(key(entry.recipe),entry);
        Map<Entry,Integer> scores=new HashMap<>();List<Entry> matches=new ArrayList<>();
        for(Entry entry:unique.values()){int score=entry.text.score(request);if(score>=0){scores.put(entry,score);matches.add(entry);}}
        // Stable ordering preserves catalogue order for ties and an empty query.
        matches.sort(Comparator.comparingInt(scores::get));
        List<JSONObject> result=new ArrayList<>();for(Entry entry:matches)result.add(entry.recipe);return result;
    }
    List<Recipe> pending(List<Recipe> imports){List<Recipe> result=new ArrayList<>();for(Recipe recipe:imports)if(!preparedIds.contains(recipe.id))result.add(recipe);return result;}
    private static String key(JSONObject value){return value.optString("foodIdentity",value.optString("id"));}
    private static int distance(JSONObject value,int servings){return Math.abs(value.optJSONObject("machineProgram").optInt("servings")-servings);}
}
