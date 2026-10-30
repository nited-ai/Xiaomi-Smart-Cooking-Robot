package com.kitchenos.common;

import com.kitchenos.common.*;
import java.net.*;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Existing public Kalomatik catalogue. Reads send no account, pantry or health context. */
public final class PublicRecipes {
    public static final String ORIGIN="https://app.kalomatik.ai";
    public static String referenceId(String source){try{URI uri=new URI(source);if(!"https".equals(uri.getScheme())||!"app.kalomatik.ai".equals(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return "";String path=uri.getPath();java.util.regex.Pattern recipePath=java.util.regex.Pattern.compile("^/(?:api/)?(?:recipes|recipe|rezepte|rezept)/([a-f0-9]{64})/?$");java.util.regex.Matcher match=recipePath.matcher(path);if(match.matches())return match.group(1);String fragment=uri.getFragment();if(fragment!=null){match=recipePath.matcher(fragment);if(match.matches())return match.group(1);}if(Arrays.asList("","/","/recipes","/rezepte","/recipe","/rezept").contains(path)&&uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&")){int equals=pair.indexOf('=');if(equals>0){String key=URLDecoder.decode(pair.substring(0,equals),"UTF-8"),id=URLDecoder.decode(pair.substring(equals+1),"UTF-8");if((key.equals("recipeId")||key.equals("recipe"))&&id.matches("[a-f0-9]{64}"))return id;}}}catch(Exception ignored){}return "";}
    public JSONObject categories()throws Exception{return get("/api/recipes?view=categories");}
    public JSONObject search(String query,String cursor)throws Exception{return searchCategory(query,cursor,"");}
    public JSONObject searchCategory(String query,String cursor,String category)throws Exception{
        if(!category.isEmpty()&&!category.matches("[a-z0-9-]{1,64}"))throw new IllegalArgumentException("Ungültige Kategorie");
        if(query.length()>120||cursor.length()>512)throw new IllegalArgumentException("Suchbegriff zu lang");
        return get("/api/recipes?status=recipe&limit=12"+(category.isEmpty()?"":"&category="+category)+"&q="+URLEncoder.encode(query,"UTF-8")+(cursor.isEmpty()?"":"&cursor="+URLEncoder.encode(cursor,"UTF-8")));
    }
    public Recipe detail(String id)throws Exception{if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Ungültige Katalogkennung");JSONObject envelope=get("/api/recipes/"+id);JSONObject value=envelope.getJSONObject("recipe");if(!id.equals(value.getString("id")))throw new IOException("Rezeptbezug hat sich geändert");return convert(value);}
    public JSONObject rawDetail(String id)throws Exception{if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Ungültige Katalogkennung");JSONObject value=get("/api/recipes/"+id).getJSONObject("recipe");if(!value.optString("id").matches("[a-f0-9]{64}")||!value.optString("guideStatus").equals("ready"))throw new IOException("Die vollständige Anleitung ist noch nicht verfügbar");return value;}
    private JSONObject get(String path)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","KitchenOS/0.2 Kalomatik-Catalogue");
        try{if(c.getResponseCode()!=200)throw new IOException("Die Kalomatik-Rezeptdatenbank ist gerade nicht erreichbar.");String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).contains("application/json"))throw new IOException("Unerwartete Katalogantwort");try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2000000)throw new IOException("Katalogantwort ist zu groß");bytes.write(buffer,0,n);}JSONObject body=new JSONObject(bytes.toString("UTF-8"));if(body.getInt("schemaVersion")!=1)throw new IOException("Unbekanntes Katalogformat");return body;}}finally{c.disconnect();}
    }
    private static String text(JSONObject o,String key,int max,boolean empty)throws JSONException{Object value=o.opt(key);if(!(value instanceof String))throw new JSONException("Rezepttext fehlt");String result=((String)value).trim();if(result.length()>max||!empty&&result.isEmpty())throw new JSONException("Ungültiger Rezepttext");for(int i=0;i<result.length();i++)if(result.charAt(i)<32)throw new JSONException("Steuerzeichen im Rezepttext");return result;}
    private static BigDecimal number(Object value,int max)throws JSONException{if(!(value instanceof Number))throw new JSONException("Rezeptmenge ist ungültig");try{BigDecimal result=new BigDecimal(value.toString());if(result.signum()<0||result.compareTo(new BigDecimal(max))>0)throw new NumberFormatException();return result;}catch(NumberFormatException e){throw new JSONException("Rezeptzahl ist ungültig");}}
    private static String amount(JSONObject ingredient,BigDecimal share)throws JSONException{
        String name=text(ingredient,"name",160,false),unit=text(ingredient,"unit",24,false);Object raw=ingredient.opt("amount");if(raw==null||raw==JSONObject.NULL)return name+" · Menge offen";
        BigDecimal value=number(raw,1000000);if(value.signum()==0)return name+" · Menge prüfen";
        return value.multiply(share).setScale(6,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+" "+unit+" "+name;
    }
    private static void metadata(StringBuilder output,JSONObject step,String key,String unit,int max)throws JSONException{Object value=step.opt(key);if(value!=null&&value!=JSONObject.NULL)output.append("\n").append(number(value,max).stripTrailingZeros().toPlainString()).append(unit);}
    public static Recipe convert(JSONObject value)throws Exception{
        String publicId=value.getString("id");if(!publicId.matches("[a-f0-9]{64}")||number(value.get("portion"),20).compareTo(BigDecimal.ONE)!=0||!value.optString("guideStatus").equals("ready"))throw new JSONException("Noch keine vollständige Kochanleitung");
        String title=text(value,"title",160,false);JSONArray input=value.getJSONArray("ingredients"),ingredients=new JSONArray(),steps=new JSONArray();if(input.length()<1||input.length()>80)throw new JSONException("Ungültige Zutatenliste");
        Map<String,JSONObject> byId=new LinkedHashMap<>();Map<String,BigDecimal> used=new HashMap<>();
        for(int i=0;i<input.length();i++){JSONObject item=input.getJSONObject(i);String id=text(item,"id",100,false);if(byId.put(id,item)!=null)throw new JSONException("Doppelte Zutat");ingredients.put(amount(item,BigDecimal.ONE));}
        JSONObject guide=value.getJSONObject("guide");JSONArray instructions=guide.getJSONArray("steps");if(instructions.length()<1||instructions.length()>30)throw new JSONException("Ungültige Rezeptschritte");
        JSONArray equipment=guide.getJSONArray("equipment");if(equipment.length()>20)throw new JSONException("Zu viel Ausstattung");List<String> tools=new ArrayList<>();for(int i=0;i<equipment.length();i++){JSONObject row=new JSONObject().put("text",equipment.get(i));tools.add(text(row,"text",160,false));}if(!tools.isEmpty())steps.put("Du brauchst: "+android.text.TextUtils.join(", ",tools));
        for(int i=0;i<instructions.length();i++){
            JSONObject step=instructions.getJSONObject(i);StringBuilder output=new StringBuilder(text(step,"title",160,false));JSONArray refs=step.getJSONArray("ingredients");if(refs.length()>80)throw new JSONException("Zu viele Zutatenbezüge");Set<String> seen=new HashSet<>();
            for(int j=0;j<refs.length();j++){JSONObject ref=refs.getJSONObject(j);String id=ref.getString("ingredientId");BigDecimal share=number(ref.get("share"),1);if(!seen.add(id)||!byId.containsKey(id)||share.signum()<=0)throw new JSONException("Ungültiger Zutatenbezug");used.put(id,used.getOrDefault(id,BigDecimal.ZERO).add(share));output.append("\n").append(amount(byId.get(id),share));}
            output.append("\n").append(text(step,"instruction",2000,false));metadata(output,step,"minutes"," Minuten",4320);metadata(output,step,"temperatureC"," °C",500);
            if(step.opt("heat") instanceof String&&!step.getString("heat").trim().isEmpty())output.append(" · ").append(text(step,"heat",160,false));
            if(step.opt("cue") instanceof String&&!step.getString("cue").trim().isEmpty())output.append("\nSo erkennst du es: ").append(text(step,"cue",1000,true));steps.put(output.toString());
        }
        for(String id:byId.keySet())if(used.getOrDefault(id,BigDecimal.ZERO).subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000001"))>0)throw new JSONException("Zutatenanteile sind unvollständig oder mehrfach gezählt");
        JSONArray tips=guide.getJSONArray("tips");if(tips.length()>10)throw new JSONException("Zu viele Hinweise");for(int i=0;i<tips.length();i++)steps.put("Tipp: "+text(new JSONObject().put("text",tips.get(i)),"text",1000,false));
        // Content-bound local revision: a later enriched/edited public guide never
        // silently replaces a previously imported recipe or inherits its validation.
        String digest=Pairing.fingerprint((publicId+new JSONObject().put("title",title).put("ingredients",ingredients).put("steps",steps).toString()).getBytes(StandardCharsets.UTF_8));String localId=digest.substring(0,8)+"-"+digest.substring(8,12)+"-"+digest.substring(12,16)+"-"+digest.substring(16,20)+"-"+digest.substring(20,32);
        return new Recipe(localId,title,ORIGIN+"/api/recipes/"+publicId,ingredients,steps,1);
    }
}
