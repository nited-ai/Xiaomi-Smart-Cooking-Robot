package com.kitchenos.connect.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.connect.kitchen.MachineProgram;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import org.json.*;

/** One app-private owner-bound cooking journey. Never stores Android parcels, auth, or run receipts. */
public final class CookJourneyStore {
    private static final Object LOCK=new Object();
    private static final int MAX_BYTES=1048576;
    private static final String PROCESS=UUID.randomUUID().toString();
    public static String processMarker(){return PROCESS;}
    private final Supplier<String> account;
    private final String owner;
    private final AtomicFile file;
    public CookJourneyStore(Context context){this(context,"journey-v1",()->new CloudSession(context).account().optString("uid"));}
    /** Explicit namespace/account source allows real isolated file checks without touching app accounts. */
    public CookJourneyStore(Context context,String namespace,Supplier<String> account){
        if(!namespace.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Ungültiger Kochspeicher");
        this.account=account;owner=account.get();
        try{String hash=Pairing.fingerprint(owner.getBytes(StandardCharsets.UTF_8));file=new AtomicFile(new File(context.getFilesDir(),"cook-"+namespace+"-"+hash+".json"));}catch(Exception e){throw new IllegalStateException("Kochstand kann nicht gespeichert werden",e);}
    }
    private void checkOwner(){if(!owner.equals(account.get()))throw new IllegalStateException("Das Kochkonto wurde gewechselt");}
    public void save(Bundle journey)throws Exception{synchronized(LOCK){
        checkOwner();JSONObject data=encode(journey).put("version",1).put("owner",owner).put("process",PROCESS);
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Kochstand ist zu groß");
        if(file.getBaseFile().isFile()&&file.getBaseFile().length()==bytes.length&&!new File(file.getBaseFile().getPath()+".bak").exists()&&java.util.Arrays.equals(bytes,java.nio.file.Files.readAllBytes(file.getBaseFile().toPath()))){checkOwner();return;}
        FileOutputStream out=null;try{out=file.startWrite();checkOwner();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }}
    public Bundle load()throws Exception{synchronized(LOCK){
        checkOwner();if(!file.getBaseFile().exists())return null;ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=file.openRead()){byte[] buffer=new byte[8192];int size;while((size=input.read(buffer))!=-1){if(bytes.size()+size>MAX_BYTES)throw new IOException("Kochstand ist zu groß");bytes.write(buffer,0,size);}}
        JSONObject data=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));checkOwner();
        if(data.optInt("version")!=1||!owner.equals(data.optString("owner")))throw new IOException("Kochstand gehört zu einem anderen Konto");
        Bundle state=decode(data);boolean restart=!PROCESS.equals(data.optString("process")),interrupted=restart||data.optBoolean("coldInterrupted");state.putString("journeyProcess",PROCESS);state.putBoolean("modified",state.getBoolean("modified")||restart);state.putBoolean("coldInterrupted",interrupted);
        // No persisted timer or controller state becomes live after a fresh launch.
        state.putLong("restDeadline",0);state.putLong("savedUptime",SystemClock.elapsedRealtime());Bundle controls=new Bundle();
        JSONObject program=new JSONObject(state.getString("recipe")).getJSONObject("machineProgram");JSONObject step=program.getJSONArray("steps").getJSONObject(state.getInt("step"));
        if(interrupted&&step.optString("kind").equals("machine")&&!state.getStringArrayList("completed").contains(step.optString("id"))){Bundle marker=new Bundle();marker.putString("process","cold-restart-no-run-proof");marker.putString("owner",owner);marker.putString("cookId",state.getString("cookId"));marker.putString("digest",data.getJSONObject("recipe").getString("programDigest"));marker.putString("stepId",step.getString("id"));marker.putBoolean("interrupted",true);marker.putBoolean("modified",true);marker.putBoolean("submitted",false);controls.putBundle(step.getString("id"),marker);}
        state.putBundle("controls",controls);return state;
    }}
    public void clear(){synchronized(LOCK){checkOwner();file.delete();}}
    private JSONObject encode(Bundle state)throws Exception{
        if(state==null||!owner.equals(state.getString("owner","")))throw new IOException("Kochstand gehört zu einem anderen Konto");
        String cook=state.getString("cookId","");UUID.fromString(cook);JSONObject card=card(new JSONObject(state.getString("recipe",""))),program=card.getJSONObject("machineProgram");int index=state.getInt("step",0);if(index<0||index>=program.getJSONArray("steps").length())throw new IOException("Kochschritt fehlt");
        Set<String> ids=new HashSet<>(),checks=new HashSet<>(),weighIds=new HashSet<>();JSONArray steps=program.getJSONArray("steps");for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);ids.add(step.getString("id"));if("weigh".equals(step.optString("kind")))weighIds.add(step.getString("id"));JSONArray ingredients=step.getJSONArray("ingredients");for(int j=0;j<ingredients.length();j++){String name=ingredients.getJSONObject(j).getString("name");checks.add(step.getString("id")+":"+name);checks.add("recipe:"+name);}}
        JSONObject result=new JSONObject().put("cookId",cook).put("recipe",card).put("step",index).put("modified",state.getBoolean("modified",false)).put("coldInterrupted",state.getBoolean("coldInterrupted",false)).put("completed",strings(state.getStringArrayList("completed"),ids,40)).put("ingredients",strings(state.getStringArrayList("ingredients"),checks,500)).put("weighingStarted",strings(state.getStringArrayList("weighingStarted"),weighIds,40));
        String history=state.getString("weighing","[]");if(history.length()>131072)throw new IOException("Zu viele gespeicherte Wiegewerte");JSONArray records=new JSONArray(history),safeRecords=new JSONArray();if(records.length()>200)throw new IOException("Zu viele gespeicherte Wiegewerte");for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);if(!"weigh".equals(step.optString("kind")))continue;JSONArray matched=RecipeWeighingHistory.forStep(records,step);for(int j=0;j<matched.length()&&safeRecords.length()<200;j++)safeRecords.put(matched.getJSONObject(j));}result.put("weighing",safeRecords);
        JSONObject edits=new JSONObject(),directions=new JSONObject();Bundle values=state.getBundle("edits"),turns=state.getBundle("directions");for(String id:ids){String key=card.getString("programDigest")+":"+id;if(values!=null){int[] v=values.getIntArray(key);if(v!=null&&v.length==3&&v[0]>=0&&v[0]<=7200&&v[1]>=0&&v[1]<=180&&v[2]>=0&&v[2]<=100)edits.put(key,new JSONArray().put(v[0]).put(v[1]).put(v[2]));}if(turns!=null&&turns.containsKey(id))directions.put(id,turns.getBoolean(id));}return result.put("edits",edits).put("directions",directions);
    }
    private Bundle decode(JSONObject data)throws Exception{
        // Round-trip through the same typed whitelist rather than trusting a raw JSON/Bundle.
        Bundle provisional=new Bundle();provisional.putString("owner",owner);provisional.putString("cookId",data.getString("cookId"));provisional.putString("recipe",data.getJSONObject("recipe").toString());provisional.putInt("step",data.getInt("step"));provisional.putBoolean("modified",data.optBoolean("modified",true));provisional.putBoolean("coldInterrupted",data.optBoolean("coldInterrupted",false));provisional.putString("weighing",data.optJSONArray("weighing")==null?"[]":data.optJSONArray("weighing").toString());provisional.putStringArrayList("completed",list(data.optJSONArray("completed")));provisional.putStringArrayList("ingredients",list(data.optJSONArray("ingredients")));provisional.putStringArrayList("weighingStarted",list(data.optJSONArray("weighingStarted")));Bundle edits=new Bundle(),directions=new Bundle();JSONObject rawEdits=data.optJSONObject("edits"),rawDirections=data.optJSONObject("directions");if(rawEdits!=null)for(Iterator<String> keys=rawEdits.keys();keys.hasNext();){String key=keys.next();JSONArray row=rawEdits.getJSONArray(key);if(row.length()==3)edits.putIntArray(key,new int[]{row.getInt(0),row.getInt(1),row.getInt(2)});}if(rawDirections!=null)for(Iterator<String> keys=rawDirections.keys();keys.hasNext();){String key=keys.next();directions.putBoolean(key,rawDirections.getBoolean(key));}provisional.putBundle("edits",edits);provisional.putBundle("directions",directions);
        JSONObject clean=encode(provisional);provisional.putString("recipe",clean.getJSONObject("recipe").toString());provisional.putString("weighing",clean.getJSONArray("weighing").toString());provisional.putStringArrayList("completed",list(clean.getJSONArray("completed")));provisional.putStringArrayList("ingredients",list(clean.getJSONArray("ingredients")));provisional.putStringArrayList("weighingStarted",list(clean.getJSONArray("weighingStarted")));Bundle cleanEdits=new Bundle(),cleanDirections=new Bundle();JSONObject safeEdits=clean.getJSONObject("edits"),safeDirections=clean.getJSONObject("directions");for(Iterator<String> keys=safeEdits.keys();keys.hasNext();){String key=keys.next();JSONArray row=safeEdits.getJSONArray(key);cleanEdits.putIntArray(key,new int[]{row.getInt(0),row.getInt(1),row.getInt(2)});}for(Iterator<String> keys=safeDirections.keys();keys.hasNext();){String key=keys.next();cleanDirections.putBoolean(key,safeDirections.getBoolean(key));}provisional.putBundle("edits",cleanEdits);provisional.putBundle("directions",cleanDirections);return provisional;
    }
    private static JSONArray strings(ArrayList<String> input,Set<String> allowed,int max){JSONArray result=new JSONArray();if(input!=null)for(String item:input)if(result.length()<max&&allowed.contains(item))result.put(item);return result;}
    private static ArrayList<String> list(JSONArray rows)throws Exception{ArrayList<String> result=new ArrayList<>();if(rows!=null&&rows.length()<=500)for(int i=0;i<rows.length();i++)result.add(rows.getString(i));return result;}
    private static JSONObject pick(JSONObject original,String... keys)throws Exception{JSONObject result=new JSONObject();for(String key:keys)if(original.has(key)){Object value=original.get(key);if(value==JSONObject.NULL||value instanceof String||value instanceof Number||value instanceof Boolean)result.put(key,value);}return result;}
    private static JSONObject image(JSONObject original)throws Exception{JSONObject result=pick(original,"alt","kind");for(String key:new String[]{"url","source"})if(original.has(key))result.put(key,url(original.optString(key)));return result;}
    private static String url(String source)throws Exception{if(source.isEmpty()||source.equals("null"))return "";URI uri=new URI(source);if(!Arrays.asList("https","http").contains(uri.getScheme())||uri.getUserInfo()!=null)throw new IOException("Ungültige Rezeptadresse");return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),uri.getPath(),null,null).toASCIIString();}
    private static JSONObject card(JSONObject original)throws Exception{
        String digest=original.getString("programDigest");if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Rezeptfassung fehlt");JSONObject source=MachineProgram.validate(original.getJSONObject("machineProgram"));JSONObject program=pick(source,"version","id","title","deviceModel","servings");program.put("cover",image(source.getJSONObject("cover")));JSONObject provenance=pick(source.getJSONObject("source"),"recipeId","revision");provenance.put("url",url(source.getJSONObject("source").optString("url")));program.put("source",provenance);JSONArray tools=new JSONArray(),rawTools=source.optJSONArray("tools");for(int i=0;rawTools!=null&&i<Math.min(rawTools.length(),40);i++){String tool=rawTools.getString(i);if(tool.matches("[a-z-]{1,80}"))tools.put(tool);}program.put("tools",tools);JSONArray steps=new JSONArray();for(int i=0;i<source.getJSONArray("steps").length();i++){JSONObject raw=source.getJSONArray("steps").getJSONObject(i),step=pick(raw,"id","kind","title","instruction");step.put("settings",pick(raw.getJSONObject("settings"),"durationSeconds","temperatureC","speedLevel","direction"));JSONArray ingredients=new JSONArray();for(int j=0;j<raw.getJSONArray("ingredients").length();j++)ingredients.put(pick(raw.getJSONArray("ingredients").getJSONObject(j),"name","amount","unit","preparation"));step.put("ingredients",ingredients);if(raw.optJSONObject("image")!=null)step.put("image",image(raw.getJSONObject("image")));steps.put(step);}program.put("steps",steps);MachineProgram.validate(program);
        JSONObject result=pick(original,"id","foodIdentity","private","reviewStatus","stepImagesMatch","curated");result.put("programDigest",digest).put("title",program.getString("title")).put("coverUrl",program.getJSONObject("cover").optString("url")).put("ingredients",MachineProgram.ingredients(program)).put("machineProgram",program);
        JSONObject imported=original.optJSONObject("importedRecipe");if(imported!=null){JSONObject copy=pick(imported,"version","id","title","servings");copy.put("source",url(imported.optString("source")));for(String key:new String[]{"ingredients","steps"}){JSONArray rows=imported.optJSONArray(key),safe=new JSONArray();for(int i=0;rows!=null&&i<Math.min(rows.length(),100);i++){String value=rows.getString(i);if(value.length()<=4096)safe.put(value);}copy.put(key,safe);}result.put("importedRecipe",copy);}return result;
    }
}
