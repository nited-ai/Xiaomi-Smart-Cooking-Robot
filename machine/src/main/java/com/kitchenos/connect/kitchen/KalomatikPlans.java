package com.kitchenos.connect.kitchen;

import android.content.Context;
import android.util.AtomicFile;
import com.kitchenos.common.Pairing;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Read projection of the shared plan and confirmed cooking preferences. Never writes
 * KitchenStore or sends device commands. Unrelated sync data is discarded in RAM. */
public final class KalomatikPlans {
    public interface PullSource { JSONObject pull(long cursor)throws Exception; }
    public interface OwnerSource { String uid(); }
    public interface DateSource { LocalDate today(); }
    private static final Object LOCK=new Object();
    private static final int SHARED_SCHEMA=2;
    private static final int MAX_PAGES=10,MAX_ITEMS=500,MAX_BYTES=2097152;
    private static final long MAX_CURSOR=9007199254740991L;
    private final AtomicFile file;
    private final String ownerUid,ownerHash;
    private final PullSource source;
    private final OwnerSource owners;
    private final DateSource dates;

    public KalomatikPlans(Context context)throws Exception{
        this(context,new CloudSession(context)::planPull,()->new CloudSession(context).account().optString("uid"),LocalDate::now);
    }
    /** Dependency injection for offline contract checks; production uses the constructor above. */
    public KalomatikPlans(Context context,PullSource source,OwnerSource owners,DateSource dates)throws Exception{
        this.source=source;this.owners=owners;this.dates=dates;ownerUid=owners.uid();
        ownerHash=Pairing.fingerprint(ownerUid.getBytes(StandardCharsets.UTF_8));
        file=new AtomicFile(new File(context.getApplicationContext().getFilesDir(),"kalomatik-plans-v1-"+ownerHash+".json"));
    }
    private void checkOwner()throws IOException{if(!ownerUid.equals(owners.uid()))throw new IOException("Dein Konto hat sich geändert. Öffne den Kochplan erneut.");}
    private static JSONObject copy(JSONObject value)throws JSONException{return new JSONObject(value.toString());}
    private static boolean text(Object value,int max){return value instanceof String&&!((String)value).trim().isEmpty()&&((String)value).length()<=max&&!((String)value).matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*");}
    private static String value(JSONObject data,String field,int max){Object v=data.opt(field);return text(v,max)?((String)v).trim():"";}
    private static Object positive(Object value,double max){if(!(value instanceof Number))return JSONObject.NULL;double n=((Number)value).doubleValue();return Double.isFinite(n)&&n>0&&n<=max?n:JSONObject.NULL;}
    private static long integer(JSONObject data,String key)throws Exception{Object n=data.opt(key);if(!(n instanceof Number))throw new IOException("Unvollständiger Kalomatik-Planungsstand");double d=((Number)n).doubleValue();if(!Double.isFinite(d)||d<0||d>MAX_CURSOR||d!=Math.floor(d))throw new IOException("Ungültiger Kalomatik-Planungsstand");return ((Number)n).longValue();}
    private JSONObject empty()throws Exception{return new JSONObject().put("version",1).put("owner",ownerHash).put("windowStart","").put("cursor",0).put("updatedAt",0).put("items",new JSONArray());}
    private JSONObject load()throws Exception{
        checkOwner();if(ownerUid.isEmpty()||!file.getBaseFile().exists())return empty();
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Kochplan-Speicher ist zu groß");out.write(b,0,n);}
            JSONObject saved=new JSONObject(out.toString("UTF-8"));if(saved.optInt("version")!=1||!ownerHash.equals(saved.optString("owner"))||saved.optJSONArray("items")==null)throw new IOException("Kochplan-Speicher kann nicht gelesen werden");checkOwner();return saved;
        }
    }
    private void save(JSONObject state)throws Exception{
        checkOwner();byte[] bytes=state.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Dein Kochplan ist zu groß für diese Ansicht");
        FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);checkOwner();file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    /** Visible, completed plan snapshot; an unfinished catch-up never replaces these cards. */
    public JSONArray getItems()throws Exception{synchronized(LOCK){return visible(load().getJSONArray("items"),dates.today());}}
    public JSONObject sharedPreferences()throws Exception{synchronized(LOCK){JSONObject saved=load(),shared=saved.optJSONObject("shared"),result=SharedAssistantProfile.preferences(shared==null?new JSONObject():shared);if(saved.optInt("sharedSchema")!=SHARED_SCHEMA){JSONObject guarded=shared==null?new JSONObject():copy(shared);guarded.put("assistantProfile:migration",new JSONObject().put("remember",false));result=SharedAssistantProfile.preferences(guarded).put("rememberKnown",false);}return result;}}
    /** Admit only the server-confirmed memory written by this account. Keep pull cursor intact. */
    public void acceptConfirmedMemory(JSONObject entity)throws Exception{synchronized(LOCK){
        checkOwner();if(!"memory".equals(entity.optString("type"))||entity.optInt("schemaVersion")!=1||entity.optLong("revision")<1)throw new IOException("Ungültige Erinnerungsbestätigung");JSONObject saved=load();long revision=entity.getLong("revision");if(saved.optLong("cursor")>=revision)return;
        JSONObject shared=saved.optJSONObject("shared");if(shared==null)shared=new JSONObject();SharedAssistantProfile.apply(shared,entity);saved.put("shared",shared);JSONObject pending=saved.optJSONObject("pending");if(pending!=null&&pending.optLong("cursor")<revision){JSONObject staged=pending.optJSONObject("shared");if(staged==null)staged=new JSONObject();SharedAssistantProfile.apply(staged,entity);pending.put("shared",staged);}save(saved);
    }}
    public JSONObject read(String id)throws Exception{synchronized(LOCK){JSONArray items=getItems();for(int i=0;i<items.length();i++)if(id.equals(items.getJSONObject(i).optString("id")))return items.getJSONObject(i);return null;}}
    private JSONArray visible(JSONArray items,LocalDate today)throws Exception{
        ArrayList<JSONObject> rows=new ArrayList<>();for(int i=0;i<items.length();i++){JSONObject row=items.getJSONObject(i);if(inWindow(row.optString("date"),today))rows.add(copy(row));}
        Collections.sort(rows,(a,b)->(a.optString("date")+" "+a.optString("time")+" "+a.optString("title")).compareTo(b.optString("date")+" "+b.optString("time")+" "+b.optString("title")));
        JSONArray result=new JSONArray();for(JSONObject row:rows)result.put(row);checkOwner();return result;
    }
    private static boolean inWindow(String date,LocalDate today){try{LocalDate day=LocalDate.parse(date);return day.toString().equals(date)&&!day.isBefore(today)&&!day.isAfter(today.plusDays(9));}catch(Exception ignored){return false;}}
    /** At most ten pages per call. If pending=true, call again to continue bounded
     * catch-up. Only filtered food records are staged; completed items stay atomic. */
    public JSONObject refresh()throws Exception{synchronized(LOCK){
        checkOwner();if(ownerUid.isEmpty())throw new IOException("Bitte für deinen Kalomatik-Kochplan anmelden");
        LocalDate today=dates.today();String window=today.toString();JSONObject saved=load(),pending=saved.optJSONObject("pending");
        boolean continuing=pending!=null&&pending.optInt("sharedSchema")==SHARED_SCHEMA&&window.equals(pending.optString("windowStart")),sameWindow=window.equals(saved.optString("windowStart"))&&saved.optInt("windowDays")==10&&saved.optInt("sharedSchema")==SHARED_SCHEMA;
        JSONObject shared=copy((continuing?pending:sameWindow?saved:new JSONObject()).optJSONObject("shared")==null?new JSONObject():(continuing?pending:saved).optJSONObject("shared"));
        long cursor=continuing?integer(pending,"cursor"):sameWindow?integer(saved,"cursor"):0;
        JSONArray start=continuing?pending.getJSONArray("items"):sameWindow?saved.getJSONArray("items"):new JSONArray();
        LinkedHashMap<String,JSONObject> cards=new LinkedHashMap<>();for(int i=0;i<start.length();i++){JSONObject row=start.getJSONObject(i);if(inWindow(row.optString("date"),today))cards.put(row.getString("id"),copy(row));}
        boolean more=false;for(int page=0;page<MAX_PAGES;page++){
            JSONObject response=source.pull(cursor);checkOwner();if(response.optInt("schemaVersion")!=1||!(response.opt("more") instanceof Boolean))throw new IOException("Dein Kalomatik-Kochplan hat ein unbekanntes Format");
            JSONArray batches=response.optJSONArray("batches");if(batches==null||batches.length()>30)throw new IOException("Dein Kalomatik-Kochplan ist unvollständig");
            long previous=cursor;for(int i=0;i<batches.length();i++){
                JSONObject batch=batches.getJSONObject(i);long revision=integer(batch,"revision");if(revision<=previous)throw new IOException("Kalomatik-Änderungen sind nicht geordnet");previous=revision;
                JSONArray entities=batch.optJSONArray("entities");if(entities==null||entities.length()>100)throw new IOException("Kalomatik-Änderung ist unvollständig");HashSet<String> ids=new HashSet<>();
                for(int j=0;j<entities.length();j++){
                    JSONObject entity=entities.getJSONObject(j);if(!"planMeal".equals(entity.optString("type"))){if(java.util.Arrays.asList("profile","assistantProfile","memory").contains(entity.optString("type"))&&entity.opt("revision") instanceof Number&&entity.optInt("schemaVersion")==1&&integer(entity,"revision")==revision&&entity.opt("deleted") instanceof Boolean)SharedAssistantProfile.apply(shared,entity);continue;}
                    // Only food plans and the explicitly bounded cooking-preference projection are retained.
                    String id=value(entity,"id",512);if(entity.optInt("schemaVersion")!=1||id.isEmpty()||!ids.add(id)||integer(entity,"revision")!=revision||!(entity.opt("deleted") instanceof Boolean))throw new IOException("Ungültiger Kalomatik-Planeintrag");
                    cards.remove(id);if(entity.getBoolean("deleted"))continue;JSONObject card=project(entity.optJSONObject("data"),id,revision,today);if(card!=null)cards.put(id,card);
                    if(cards.size()>MAX_ITEMS)throw new IOException("Zu viele Gerichte im Kalomatik-Kochplan");
                }
            }
            long next=integer(response,"cursor");more=response.getBoolean("more");if(next!=previous||more&&next<=cursor)throw new IOException("Kalomatik-Planungsfortschritt konnte nicht bestätigt werden");cursor=next;
            if(!more)break;
        }
        JSONArray nextItems=new JSONArray();for(JSONObject row:cards.values())nextItems.put(row);
        JSONObject next=copy(saved);if(more)next.put("pending",new JSONObject().put("windowStart",window).put("cursor",cursor).put("items",nextItems).put("shared",shared).put("sharedSchema",SHARED_SCHEMA));
        else{next.remove("pending");next.put("windowStart",window).put("cursor",cursor).put("items",nextItems).put("shared",shared).put("sharedSchema",SHARED_SCHEMA).put("windowDays",10).put("updatedAt",System.currentTimeMillis());}
        checkOwner();if(!today.equals(dates.today()))throw new IOException("Der Tag hat gewechselt. Bitte Kochplan erneut laden.");save(next);
        return new JSONObject().put("items",visible(next.getJSONArray("items"),today)).put("cursor",integer(next,"cursor")).put("pending",more).put("updatedAt",next.optLong("updatedAt")).put("windowStart",window);
    }}
    /** Explicit food and known nutrient projection; body measurements and health records are discarded. */
    private static JSONObject project(JSONObject data,String id,long revision,LocalDate today)throws Exception{
        if(data==null||!inWindow(data.optString("date"),today)||!value(data,"consumedAt",100).isEmpty()||Boolean.TRUE.equals(data.opt("consumption")))return null;
        String title=value(data,"gericht",200);if(title.isEmpty())title=value(data,"name",200);if(title.isEmpty())return null;
        Object originalServings=positive(data.opt("portion"),50),cookingServings=positive(data.opt("cookingPortions"),50);
        JSONArray ingredients=new JSONArray(),raw=data.optJSONArray("ingredients");if(raw!=null){if(raw.length()>80)return null;for(int i=0;i<raw.length();i++){JSONObject item=raw.optJSONObject(i);if(item==null)return null;String name=value(item,"name",160);if(name.isEmpty())name=value(item,"lebensmittel",160);if(name.isEmpty())return null;Object amount=positive(item.has("amount")?item.opt("amount"):item.opt("menge_g"),1000000);String unit=value(item,"unit",24);if(unit.isEmpty()&&item.has("menge_g"))unit="g";ingredients.put(new JSONObject().put("id",value(item,"id",100).isEmpty()?"ingredient-"+i:value(item,"id",100)).put("name",name).put("amount",amount).put("unit",unit));}}
        JSONObject guide=guide(data.optJSONObject("recipeGuide"));boolean amounts=ingredients.length()>0;for(int i=0;i<ingredients.length();i++)amounts&=!ingredients.getJSONObject(i).isNull("amount")&&!ingredients.getJSONObject(i).optString("unit").isEmpty();
        boolean portions=originalServings instanceof Number&&((Number)originalServings).doubleValue()==Math.floor(((Number)originalServings).doubleValue())&&((Number)originalServings).doubleValue()<=20;
        String time=value(data,"time",5);if(!time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))time="";
        JSONObject card=new JSONObject().put("id",id).put("revision",revision).put("date",data.getString("date")).put("title",title).put("time",time).put("slotType",value(data,"slotType",40)).put("originalServings",originalServings).put("cookingPortions",cookingServings).put("ingredients",ingredients).put("recipeGuide",guide==null?JSONObject.NULL:guide).put("recipeOrigin",value(data,"recipeOrigin",80)).put("sourceMealId",value(data,"sourceMealId",200)).put("canPrepare",amounts&&portions&&guide!=null);
        JSONObject nutrition=new JSONObject();String[][] fields={{"kalorien","calories"},{"protein","protein"},{"fett","fat"},{"kohlenhydrate","carbs"},{"ballaststoffe","fiber"}};for(String[] field:fields){Object value=data.opt(field[0]);if(value instanceof Number&&Double.isFinite(((Number)value).doubleValue())&&((Number)value).doubleValue()>=0&&((Number)value).doubleValue()<=1000000)if(originalServings instanceof Number)nutrition.put(field[1],((Number)value).doubleValue()/((Number)originalServings).doubleValue());}if(nutrition.length()>0)card.put("knownNutrition",nutrition);return card;
    }
    private static JSONArray strings(JSONArray input,int limit,int length)throws Exception{JSONArray result=new JSONArray();if(input==null)return result;if(input.length()>limit)throw new IOException("Rezeptdetails sind zu lang");for(int i=0;i<input.length();i++)if(text(input.opt(i),length))result.put(input.getString(i).trim());return result;}
    private static JSONObject guide(JSONObject raw)throws Exception{
        if(raw==null)return null;JSONArray source=raw.optJSONArray("steps");if(source==null||source.length()==0||source.length()>40)return null;JSONObject clean=new JSONObject();
        for(String key:new String[]{"title","difficulty"}){String text=value(raw,key,160);if(!text.isEmpty())clean.put(key,text);}
        for(String key:new String[]{"prepMinutes","cookMinutes","restMinutes"}){Object number=raw.opt(key);if(number instanceof Number&&Double.isFinite(((Number)number).doubleValue())&&((Number)number).doubleValue()>=0&&((Number)number).doubleValue()<=4320)clean.put(key,number);}
        clean.put("equipment",strings(raw.optJSONArray("equipment"),15,100));clean.put("tips",strings(raw.optJSONArray("tips"),20,1000));JSONArray steps=new JSONArray();
        for(int i=0;i<source.length();i++){JSONObject step=source.optJSONObject(i);if(step==null)return null;String instruction=value(step,"instruction",2400);if(instruction.isEmpty())return null;JSONObject out=new JSONObject().put("title",value(step,"title",160)).put("instruction",instruction);
            for(String key:new String[]{"heat","cue"}){String text=value(step,key,1000);if(!text.isEmpty())out.put(key,text);}
            for(String key:new String[]{"minutes","temperatureC"}){Object n=step.opt(key);if(n instanceof Number&&Double.isFinite(((Number)n).doubleValue())&&((Number)n).doubleValue()>=0&&((Number)n).doubleValue()<=4320)out.put(key,n);}
            JSONArray uses=step.optJSONArray("ingredients"),safe=new JSONArray();if(uses!=null){if(uses.length()>80)return null;for(int j=0;j<uses.length();j++){JSONObject use=uses.optJSONObject(j);if(use==null)continue;String ingredientId=value(use,"ingredientId",100);Object share=positive(use.opt("share"),1);if(!ingredientId.isEmpty()&&share!=JSONObject.NULL)safe.put(new JSONObject().put("ingredientId",ingredientId).put("share",share));}}out.put("ingredients",safe);steps.put(out);
        }
        return clean.put("steps",steps);
    }
}
