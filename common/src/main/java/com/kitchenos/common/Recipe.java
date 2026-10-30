package com.kitchenos.common;

import org.json.*;
import java.util.UUID;

/** Data only: imported instructions can never become device commands. */
public final class Recipe {
    public static final int MAX_BYTES = 262144;
    public final String id, title, source;
    public final JSONArray ingredients, steps;
    public final int servings;
    public Recipe(String id, String title, String source, JSONArray ingredients, JSONArray steps) throws JSONException {
        this(id,title,source,ingredients,steps,0);
    }
    public Recipe(String id,String title,String source,JSONArray ingredients,JSONArray steps,int servings)throws JSONException {
        if (!id.matches("[a-f0-9-]{36}") || !UUID.fromString(id).toString().equals(id)) throw new JSONException("Ungültige Rezept-ID");
        this.id=id;
        this.title=limited(title.trim(),160,"Titel");
        if (this.title.isEmpty()) throw new JSONException("Rezepttitel fehlt");
        this.source=limited(source,4096,"Quelle");
        if (!source.isEmpty() && !source.startsWith("https://") && !source.startsWith("http://")) throw new JSONException("Ungültige Rezeptquelle");
        this.ingredients=lines(ingredients); this.steps=lines(steps);
        if(servings<0||servings>20)throw new JSONException("Ungültige Portionsangabe");this.servings=servings;
        if (this.steps.length()==0 && this.ingredients.length()==0) throw new JSONException("Rezeptinhalt fehlt");
        if (json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES) throw new JSONException("Rezept ist zu groß");
    }
    private static String limited(String s,int max,String label) throws JSONException {
        if(s==null || s.length()>max || s.indexOf('\0')>=0)throw new JSONException(label+" ist ungültig oder zu lang"); return s;
    }
    private static JSONArray lines(JSONArray a) throws JSONException {
        if(a==null || a.length()>500)throw new JSONException("Zu viele Rezeptzeilen");
        JSONArray result=new JSONArray();
        for(int i=0;i<a.length();i++) {
            Object item=a.get(i); if(!(item instanceof String))throw new JSONException("Rezeptzeile muss Text sein");
            String s=limited((String)item,4096,"Rezeptzeile").trim(); if(!s.isEmpty())result.put(s);
        }
        return result;
    }
    public JSONObject json() throws JSONException { JSONObject value=new JSONObject().put("version",1).put("id",id).put("title",title).put("source",source).put("ingredients",ingredients).put("steps",steps);if(servings>0)value.put("servings",servings);return value; }
    public static Recipe parse(String data) throws JSONException {
        JSONObject o=new JSONObject(data);
        if(o.getInt("version")!=1)throw new JSONException("Unbekanntes Rezeptformat");
        return new Recipe(o.getString("id"),o.getString("title"),o.getString("source"),o.getJSONArray("ingredients"),o.getJSONArray("steps"),o.optInt("servings",0));
    }
}
