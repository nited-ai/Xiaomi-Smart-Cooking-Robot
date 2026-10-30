package com.kitchenos.share;

import android.text.Html;
import com.kitchenos.common.Recipe;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Reads public schema.org Recipe JSON-LD or accepts the user's complete plain text. */
public final class RecipeImporter {
    private static final Pattern URL_PATTERN=Pattern.compile("https://[^\\s<>\"']+",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT=Pattern.compile("<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script\\s*>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    public static Recipe prepare(String text,String title)throws Exception {
        text=text.trim();if(text.isEmpty())throw new IOException("Bitte Rezepttext oder Rezeptlink teilen.");
        if(text.length()>100000)throw new IOException("Geteilter Text ist zu lang.");
        Matcher link=URL_PATTERN.matcher(text);
        if(link.find()) {
            String source=link.group().replaceAll("[).,;]+$","");
            String id=com.kitchenos.common.PublicRecipes.referenceId(source);if(!id.isEmpty())return new com.kitchenos.common.PublicRecipes().detail(id);return fromHtml(fetch(source),source,title);
        }
        if(text.startsWith("http://"))throw new IOException("Rezeptlinks bitte als HTTPS teilen oder den Rezepttext einfügen.");
        JSONArray steps=new JSONArray();for(String line:text.split("\\r?\\n\\s*\\r?\\n|\\r?\\n"))if(!line.trim().isEmpty())steps.put(line.trim());
        if(title.trim().isEmpty())title=text.split("\\r?\\n",2)[0];
        if(title.length()>160)title=title.substring(0,157)+"…";
        return new Recipe(UUID.randomUUID().toString(),title,"",new JSONArray(),steps);
    }
    static Recipe fromHtml(String html,String source,String title)throws Exception {
            Matcher scripts=SCRIPT.matcher(html);
            while(scripts.find())try{
                Object json=new JSONTokener(scripts.group(1).trim()).nextValue();JSONObject recipe=findRecipe(json,0);
                if(recipe!=null){JSONArray ingredients=strings(recipe.opt("recipeIngredient"));JSONArray steps=instructions(recipe.opt("recipeInstructions"),0);
                    if(steps.length()>0||ingredients.length()>0)return new Recipe(UUID.randomUUID().toString(),clean(recipe.optString("name",title.isEmpty()?"Geteiltes Rezept":title)),source,ingredients,steps,servings(recipe.opt("recipeYield")));}
            }catch(JSONException ignored){}
            throw new IOException("Auf dieser Seite wurde kein lesbares Rezept gefunden. Bitte Zutaten und Zubereitung als Text teilen oder hier einfügen.");
    }
    private static JSONObject findRecipe(Object value,int depth){
        if(depth>16)return null;
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;Object type=o.opt("@type");if(hasType(type))return o;
            Iterator<String> keys=o.keys();while(keys.hasNext()){JSONObject r=findRecipe(o.opt(keys.next()),depth+1);if(r!=null)return r;}}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){JSONObject r=findRecipe(a.opt(i),depth+1);if(r!=null)return r;}}return null;
    }
    private static boolean hasType(Object type){if(type instanceof String)return type.equals("Recipe")||type.equals("https://schema.org/Recipe")||type.equals("http://schema.org/Recipe");if(type instanceof JSONArray){JSONArray a=(JSONArray)type;for(int i=0;i<a.length();i++)if(hasType(a.opt(i)))return true;}return false;}
    private static JSONArray strings(Object value){JSONArray result=new JSONArray();if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)if(a.opt(i) instanceof String)result.put(clean(a.optString(i)));}else if(value instanceof String)result.put(clean((String)value));return result;}
    private static JSONArray instructions(Object value,int depth){
        JSONArray result=new JSONArray();if(depth>16)return result;
        if(value instanceof String){String s=clean((String)value);if(!s.isEmpty())result.put(s);}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){JSONArray nested=instructions(a.opt(i),depth+1);for(int j=0;j<nested.length();j++)result.put(nested.optString(j));}}
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;if(o.has("text"))return instructions(o.opt("text"),depth+1);if(o.has("itemListElement"))return instructions(o.opt("itemListElement"),depth+1);if(o.has("item"))return instructions(o.opt("item"),depth+1);}
        return result;
    }
    private static String clean(String s){return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().trim();}
    static int servings(Object value){
        // Do not guess ranges, tray sizes or yields such as "12 Kekse" as meal portions.
        if(value instanceof JSONArray){JSONArray values=(JSONArray)value;if(values.length()!=1)return 0;value=values.opt(0);}
        if(!(value instanceof String)&&!(value instanceof Number))return 0;
        String text=String.valueOf(value).trim();Matcher match=Pattern.compile("^([1-9][0-9]?)(?:\\s*(?:Portionen?|Personen?|servings?))?$",Pattern.CASE_INSENSITIVE).matcher(text);
        if(!match.matches())return 0;int count=Integer.parseInt(match.group(1));return count<=20?count:0;
    }
    private static String fetch(String address)throws Exception {
        URL url=new URL(address);
        for(int hop=0;hop<6;hop++){
            if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))throw new IOException("Nur öffentliche HTTPS-Rezeptseiten sind unterstützt.");
            InetAddress[] resolved=InetAddress.getAllByName(url.getHost());if(resolved.length==0)throw new IOException("Rezeptseite nicht erreichbar");
            for(InetAddress ip:resolved)if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isSiteLocalAddress()||ip.isLinkLocalAddress()||ip.isMulticastAddress())throw new IOException("Lokale Adressen sind keine Rezeptseiten.");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","KitchenOS-RecipeShare/0.1");
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Ungültige Weiterleitung");url=new URL(url,location);continue;}
                if(status!=200)throw new IOException("Rezeptseite antwortet mit HTTP "+status+". Bitte Rezepttext teilen.");
                String type=c.getContentType();if(type==null||(!type.toLowerCase(Locale.ROOT).contains("text/html")&&!type.toLowerCase(Locale.ROOT).contains("application/xhtml")))throw new IOException("Der Link führt nicht zu einer Rezeptseite.");
                if(c.getContentLengthLong()>2097152)throw new IOException("Rezeptseite zu groß");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2097152)throw new IOException("Rezeptseite zu groß");bytes.write(buffer,0,n);}return bytes.toString("UTF-8");
                }
            }finally{c.disconnect();}
        }
        throw new IOException("Zu viele Weiterleitungen");
    }
}
