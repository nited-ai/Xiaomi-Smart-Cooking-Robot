package com.kitchenos.connect.kitchen;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Local kitchen data, separate from pairing and imported recipes. No cloud claim. */
public final class KitchenStore {
    public static final class RevisionConflict extends IllegalStateException {public RevisionConflict(){super("Dein Vorrat wurde inzwischen geändert. Bitte das Ergebnis erneut mit dem aktuellen Vorrat abgleichen.");}}
    private static final Object LOCK=new Object();
    private static final java.util.LinkedHashMap<String,CachedState> CACHE=new java.util.LinkedHashMap<>();
    private static final class CachedState {final long time,size;final String json;CachedState(File f,String json){time=f.lastModified();size=f.length();this.json=json;}}
    private final AtomicFile file;
    private final Context context;private final String ownerUid;
    public KitchenStore(Context context){this(context,null);}
    public KitchenStore(Context context,String expectedOwner){this.context=context.getApplicationContext();String uid=new CloudSession(context).account().optString("uid");if(expectedOwner!=null&&!expectedOwner.equals(uid))throw new IllegalStateException("Dein Konto hat sich geändert. Öffne die Ansicht erneut.");ownerUid=uid;String name="kitchen-state-v1.json";if(!uid.isEmpty())try{name="kitchen-account-"+com.kitchenos.common.Pairing.fingerprint(uid.getBytes(StandardCharsets.UTF_8))+".json";}catch(Exception e){throw new IllegalStateException("Kontobereich nicht verfügbar",e);}file=new AtomicFile(new File(context.getFilesDir(),name));}
    public JSONObject snapshot(){synchronized(LOCK){checkOwner();try{
        if(!file.getBaseFile().exists()){CACHE.remove(file.getBaseFile().getAbsolutePath());return empty();}
        String cacheKey=file.getBaseFile().getAbsolutePath();CachedState cached=CACHE.get(cacheKey);if(cached!=null&&cached.time==file.getBaseFile().lastModified()&&cached.size==file.getBaseFile().length()&&!new File(cacheKey+".bak").exists()){JSONObject copy=new JSONObject(cached.json);checkOwner();return copy;}
        try(InputStream input=file.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>2097152)throw new IOException("Küchendaten zu groß");bytes.write(buffer,0,n);}
            JSONObject data=new JSONObject(bytes.toString("UTF-8"));if(data.getInt("version")!=1)throw new JSONException("Unbekannte Datenversion");checkOwner();CACHE.put(cacheKey,new CachedState(file.getBaseFile(),data.toString()));while(CACHE.size()>8)CACHE.remove(CACHE.keySet().iterator().next());return data;
        }
    }catch(Exception e){throw new IllegalStateException("Küchendaten konnten nicht gelesen werden. Vorhandene Daten bleiben erhalten.",e);}}}
    private JSONObject empty()throws JSONException{return new JSONObject().put("version",1).put("revision",0).put("pantry",new JSONArray()).put("plans",new JSONArray()).put("feedback",new JSONObject()).put("preferences",new JSONObject().put("servings",2).put("diet","").put("allergies","").put("dislikes","").put("notes","").put("aiConsent",false).put("communityConsent",false));}
    private void checkOwner(){if(!ownerUid.equals(new CloudSession(context).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert. Öffne die Ansicht erneut.");}
    private void save(JSONObject value)throws Exception {
        checkOwner();
        value.put("revision",value.getLong("revision")+1);byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2097152)throw new IOException("Küchenspeicher ist voll");
        FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);CACHE.put(file.getBaseFile().getAbsolutePath(),new CachedState(file.getBaseFile(),value.toString()));}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    public void addStock(String name,String amount,String unit,String expires)throws Exception {synchronized(LOCK){
        JSONObject item=KitchenLogic.stock(UUID.randomUUID().toString(),name,amount,unit,expires);JSONObject data=snapshot();JSONArray items=data.getJSONArray("pantry");if(items.length()>=500)throw new IllegalArgumentException("Maximal 500 Vorratseinträge");items.put(item);save(data);
    }}
    public void removeStock(String id)throws Exception{synchronized(LOCK){JSONObject data=snapshot();data.put("pantry",without(data.getJSONArray("pantry"),id));save(data);}}
    /** Correct one existing recognition/manual item atomically, keeping its identity and metadata. */
    public void updateStock(String id,String name,String amount,String unit,String expires)throws Exception{synchronized(LOCK){
        if(id==null||id.isEmpty())throw new IllegalArgumentException("Vorratseintrag fehlt");
        JSONObject checked=KitchenLogic.stock(id,name,amount,unit,expires),data=snapshot();JSONArray pantry=data.getJSONArray("pantry");int found=-1;
        for(int i=0;i<pantry.length();i++)if(id.equals(pantry.getJSONObject(i).optString("id"))){if(found>=0)throw new IllegalStateException("Vorratseintrag ist nicht eindeutig");found=i;}
        if(found<0)throw new IllegalStateException("Dieser Vorratseintrag wurde inzwischen entfernt");
        JSONObject next=new JSONObject(pantry.getJSONObject(found).toString());for(String field:new String[]{"id","name","amount","unit","expires"})next.put(field,checked.get(field));pantry.put(found,next);save(data);
    }}
    /** One recognition result is applied atomically; retries do not add the same food twice. */
    public int recognizedStock(String requestId,long expectedRevision,JSONArray recognized)throws Exception{synchronized(LOCK){
        UUID.fromString(requestId);if(recognized==null||recognized.length()>100)throw new IllegalArgumentException("Ungültige Vorratserkennung");
        JSONObject data=snapshot();JSONArray applied=data.optJSONArray("appliedPhotoRequests");if(applied==null)applied=new JSONArray();
        for(int i=0;i<applied.length();i++)if(requestId.equals(applied.optString(i)))return 0;
        if(data.getLong("revision")!=expectedRevision)throw new RevisionConflict();
        JSONArray pantry=data.getJSONArray("pantry");java.util.HashSet<String> names=new java.util.HashSet<>();for(int i=0;i<pantry.length();i++)names.add(normalName(pantry.getJSONObject(i).getString("name")));
        JSONArray additions=new JSONArray();for(int i=0;i<recognized.length();i++){
            JSONObject item=recognized.getJSONObject(i);String name=item.getString("name").trim();double confidence=item.optDouble("confidence",0);
            if(!Double.isFinite(confidence)||confidence<0.9||confidence>1||name.isEmpty()||name.length()>100)continue;
            if(!names.add(normalName(name)))continue;
            // Never infer package quantities from a picture. Only an explicitly legible label counts.
            boolean legible=item.optBoolean("amountLegible",false);String amount=legible?item.optString("amount",""):"",unit=legible?item.optString("unit",""):"";
            additions.put(KitchenLogic.stock(UUID.randomUUID().toString(),name,amount,unit,""));
        }
        if(pantry.length()+additions.length()>500)throw new IllegalArgumentException("Maximal 500 Vorratseinträge");for(int i=0;i<additions.length();i++)pantry.put(additions.get(i));
        applied.put(requestId);JSONArray bounded=new JSONArray();for(int i=Math.max(0,applied.length()-100);i<applied.length();i++)bounded.put(applied.get(i));data.put("appliedPhotoRequests",bounded);save(data);return additions.length();
    }}
    private static String normalName(String name){return java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.GERMAN).trim().replaceAll("\\s+"," ");}
    public void favorite(String recipeId,boolean enabled)throws Exception{synchronized(LOCK){UUID.fromString(recipeId);JSONObject data=snapshot();JSONArray previous=data.optJSONArray("favorites"),next=new JSONArray();if(previous!=null)for(int i=0;i<previous.length();i++)if(!recipeId.equals(previous.optString(i)))next.put(previous.getString(i));if(enabled)next.put(recipeId);data.put("favorites",next);save(data);}}
    public void catalogFavorite(String identity,boolean enabled)throws Exception{synchronized(LOCK){if(!identity.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Ungültiger Rezeptbezug");JSONObject data=snapshot();JSONArray previous=data.optJSONArray("catalogFavorites"),next=new JSONArray();if(previous!=null)for(int i=0;i<previous.length();i++)if(!identity.equals(previous.optString(i)))next.put(previous.getString(i));if(enabled)next.put(identity);data.put("catalogFavorites",next);save(data);}}
    public void preferences(String diet,String allergies,String dislikes,String notes,int servings,boolean aiConsent,boolean communityConsent)throws Exception{synchronized(LOCK){
        if(servings<1||servings>20)throw new IllegalArgumentException("1–20 Portionen auswählen");
        for(String text:new String[]{diet,allergies,dislikes,notes})if(text.length()>2000)throw new IllegalArgumentException("Präferenztext ist zu lang");
        JSONObject data=snapshot();data.put("preferences",new JSONObject().put("diet",diet.trim()).put("allergies",allergies.trim()).put("dislikes",dislikes.trim()).put("notes",notes.trim()).put("servings",servings).put("aiConsent",aiConsent).put("communityConsent",communityConsent));save(data);
    }}
    public void plan(String recipeId,String date,int servings,int baseServings)throws Exception{synchronized(LOCK){
        UUID.fromString(recipeId);LocalDate parsed=LocalDate.parse(date);if(parsed.isBefore(LocalDate.now())||parsed.isAfter(LocalDate.now().plusDays(366))||servings<1||servings>20||baseServings<1||baseServings>20)throw new IllegalArgumentException("Datum oder Portionen außerhalb des Planungsbereichs");
        JSONObject data=snapshot();JSONArray plans=data.getJSONArray("plans");if(plans.length()>=500)throw new IllegalArgumentException("Planung ist voll");plans.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("recipeId",recipeId).put("date",date).put("servings",servings).put("baseServings",baseServings));save(data);
    }}
    public void removePlan(String id)throws Exception{synchronized(LOCK){JSONObject data=snapshot();data.put("plans",without(data.getJSONArray("plans"),id));save(data);}}
    public void feedback(String recipeId,String digest,int rating,boolean worked,String note)throws Exception{synchronized(LOCK){
        UUID.fromString(recipeId);if(rating<1||rating>5||note.length()>2000)throw new IllegalArgumentException("Bewertung bitte prüfen");
        JSONObject data=snapshot();data.getJSONObject("feedback").put(recipeId,new JSONObject().put("digest",digest).put("rating",rating).put("worked",worked).put("note",note.trim()).put("at",java.time.Instant.now().toString()).put("scope","local"));save(data);
    }}
    public void onlineRecipe(String recipeId,String localDigest,String catalogId,String revision)throws Exception{synchronized(LOCK){
        UUID.fromString(recipeId);if(!catalogId.matches("[a-f0-9]{64}")||!revision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Ungültiger Online-Bezug");
        JSONObject data=snapshot(),online=data.optJSONObject("onlineRecipes");if(online==null)online=new JSONObject();online.put(recipeId,new JSONObject().put("catalogId",catalogId).put("revision",revision).put("digest",localDigest));data.put("onlineRecipes",online);save(data);
    }}
    private JSONArray without(JSONArray source,String id){JSONArray result=new JSONArray();for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item!=null&&!id.equals(item.optString("id")))result.put(item);}return result;}
}
