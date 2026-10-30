package com.kitchenos.connect.kitchen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.kitchenos.common.Pairing;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Account-owned draft and request lifecycle. Dialogs only observe it; closing a view
 * neither drops results nor starts another request. View actions remain explicit. */
public final class AssistantConversation {
    public interface Gateway {JSONObject send(Context context,String owner,String id,String message,File photo,boolean pantry,boolean create,JSONObject scene,JSONObject snapshot)throws Exception;}
    private static final Map<String,AssistantConversation> sessions=new HashMap<>();
    private static final ExecutorService requests=Executors.newCachedThreadPool();
    private final Context context;
    private final String owner,key;
    private final SharedPreferences store;
    private final Gateway gateway;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Runnable> listeners=new ArrayList<>();
    private JSONObject state;

    public static synchronized AssistantConversation get(Context context){
        Context app=context.getApplicationContext();String uid=new CloudSession(app).account().optString("uid"),id=app.getFilesDir().getAbsolutePath()+":"+uid;
        AssistantConversation session=sessions.get(id);if(session==null){session=new AssistantConversation(app,"default",AssistantGateway::send);sessions.put(id,session);}session.reconcile();return session;
    }
    /** A separate namespace and injected gateway permit isolated, network-free checks. */
    public AssistantConversation(Context context,String namespace,Gateway gateway){
        if(!namespace.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Ungültiger Gesprächsspeicher");
        this.context=context;this.gateway=gateway;owner=new CloudSession(context).account().optString("uid");
        try{key=Pairing.fingerprint(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception error){throw new IllegalStateException(error);}
        store=context.getSharedPreferences("assistant-conversation-"+namespace,0);
        try{state=new JSONObject(store.getString(key,"{}"));}catch(Exception error){state=new JSONObject();}
        if(dictating()){put("dictationBusy",false);put("error","Das Diktat wurde unterbrochen. Dein bisheriger Text bleibt erhalten.");persist(true);}
        if(busy()){put("busy",false);put("error","Die vorige Anfrage wurde unterbrochen. Dein Entwurf bleibt erhalten; du kannst dieselbe Anfrage erneut senden.");persist(true);}
        if(!state.optString("photo").isEmpty()&&photo()==null){put("photo","");put("inbox","");put("error","Das angehängte Foto ist nicht mehr verfügbar. Dein Text bleibt erhalten.");persist(true);}
        reconcile();
    }
    private void put(String name,Object value){try{state.put(name,value);}catch(JSONException error){throw new IllegalStateException(error);}}
    private void persist(boolean sync){SharedPreferences.Editor edit=store.edit().putString(key,state.toString());if(sync)edit.commit();else edit.apply();}
    public void flush(){persist(true);}
    public String owner(){return owner;}
    public boolean sameOwner(){return owner.equals(new CloudSession(context).account().optString("uid"));}
    public boolean dictating(){return state.optBoolean("dictationBusy");}
    public boolean beginDictation(){if(busy()||dictating()||awaitingMerge()||!sameOwner())return false;put("dictationBusy",true);put("error","");persist(true);changed();return true;}
    public void endDictation(String text,String previous,int from,int to,String error){put("dictationBusy",false);if(text!=null&&!text.trim().isEmpty()){String current=draft();from=current.equals(previous)?Math.max(0,Math.min(from,current.length())):current.length();to=current.equals(previous)?Math.max(from,Math.min(to,current.length())):current.length();String inserted=(from>0&&!Character.isWhitespace(current.charAt(from-1))?" ":"")+text.trim()+(to<current.length()&&!Character.isWhitespace(current.charAt(to))?" ":"");String next=current.substring(0,from)+inserted+current.substring(to);put("draft",next);if(next.length()>2000)error="Dein Diktat ist gespeichert, aber die Nachricht ist zu lang. Bitte kürze sie vor dem Senden.";}put("error",error==null?"":error);persist(true);changed();}
    public boolean busy(){return state.optBoolean("busy");}
    public boolean awaitingMerge(){return state.optBoolean("mergePending");}
    public String draft(){return state.optString("draft");}
    public String error(){return state.optString("error");}
    public File photo(){try{String path=state.optString("photo");return path.isEmpty()?null:PhotoAttachment.restore(context,path);}catch(Exception error){return null;}}
    public JSONObject uiRequest(){return state.optJSONObject("uiRequest");}
    public JSONArray history(){reconcile();return ChatHistory.read(context,owner);}
    public void listen(Runnable listener){listeners.add(listener);}
    public void unlisten(Runnable listener){listeners.remove(listener);}
    private void changed(){for(Runnable listener:new ArrayList<>(listeners))listener.run();}
    public void draft(String text){if(!busy()&&!awaitingMerge()&&sameOwner()){put("draft",text);persist(false);}}
    public void attach(File photo,String inbox,boolean pantry)throws Exception{
        if(busy()||awaitingMerge()||!sameOwner())return;PhotoAttachment.restore(context,photo.getAbsolutePath());put("photo",photo.getAbsolutePath());put("inbox",inbox);put("pantry",pantry);put("error","");persist(true);changed();
    }
    /** Removing an unsent attachment leaves the phone inbox item available for later. */
    public void removePhoto(){if(busy()||awaitingMerge()||!sameOwner())return;put("photo","");put("inbox","");put("pantry",false);put("error","");persist(true);changed();}
    public void clearUiRequest(){put("uiRequest",JSONObject.NULL);persist(true);}
    public void confirmedMemory(JSONObject receipt)throws Exception{if(!sameOwner()||receipt.optLong("revision")<1||!"memory".equals(receipt.optString("type")))return;String text=receipt.getJSONObject("data").getString("text");ChatHistory.exchange(context,owner,"memory:"+receipt.getString("id"),"Als Kochvorliebe merken: "+text,"Deine Kochvorliebe ist in deinem gemeinsamen Calomatik-Konto gespeichert. Du kannst sie in den Assistenteneinstellungen auf dem Handy ändern oder löschen.");changed();}

    public boolean submit(JSONObject scene,JSONObject snapshot)throws Exception{
        reconcile();if(busy()||dictating()||awaitingMerge()||state.optBoolean("resultPending")||!sameOwner()||owner.isEmpty())return false;
        String prompt=draft().trim();if(prompt.length()>2000)throw new IllegalArgumentException("Bitte kürze die Nachricht auf höchstens 2000 Zeichen. Dein Entwurf bleibt erhalten.");File photo=photo();boolean stock=state.optBoolean("pantry")&&photo!=null;
        if(prompt.isEmpty()&&photo==null)return false;
        if(prompt.isEmpty())prompt=stock?"Erkenne die Lebensmittel auf diesem Foto und ergänze meinen Vorrat. Unleserliche Mengen bleiben offen.":"Was siehst du auf diesem Foto?";
        String signature=Pairing.fingerprint((prompt+":"+stock+":"+(photo==null?"":photo.getAbsolutePath())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Retry unchanged drafts with exactly the same request id and original payload,
        // even when the dialog or appliance scene has since been recreated.
        if(!signature.equals(state.optString("signature"))||state.optJSONObject("snapshot")==null){
            snapshot=AssistantGateway.requestSnapshot(context,snapshot);snapshot.put("conversation",ChatHistory.context(context,owner));put("requestId",UUID.randomUUID().toString());put("signature",signature);put("scene",new JSONObject(scene.toString()));put("snapshot",new JSONObject(snapshot.toString()));
        }
        put("submittedText",prompt);put("submittedPhoto",photo==null?"":photo.getAbsolutePath());put("submittedInbox",state.optString("inbox"));put("submittedPantry",stock);put("error","");put("uiRequest",JSONObject.NULL);put("busy",true);persist(true);
        final String id=state.getString("requestId"),text=prompt;final File attached=photo;final JSONObject originalScene=new JSONObject(state.getJSONObject("scene").toString()),originalSnapshot=new JSONObject(state.getJSONObject("snapshot").toString());
        changed();requests.execute(()->{
            try{JSONObject response=gateway.send(context,owner,id,text,attached,stock,false,originalScene,originalSnapshot);main.post(()->{put("busy",false);put("result",response);put("resultPending",true);persist(true);reconcile();changed();});}
            catch(Exception error){main.post(()->{put("busy",false);put("error",(error.getMessage()==null?"Die Anfrage konnte nicht abgeschlossen werden.":error.getMessage())+" Dein Entwurf bleibt erhalten.");if(error instanceof AssistantGateway.LocalMergeConflict){put("result",((AssistantGateway.LocalMergeConflict)error).response);put("mergePending",true);}if(error instanceof CloudSession.ApiException&&((CloudSession.ApiException)error).code.equals("job_failed"))put("signature","");persist(true);changed();});}
        });return true;
    }
    public void applyPendingStock(){
        if(busy()||!awaitingMerge()||!sameOwner())return;
        try{JSONObject response=AssistantGateway.applyResponse(context,owner,state.getString("requestId"),state.optBoolean("submittedPantry"),new KitchenStore(context).snapshot().getLong("revision"),new JSONObject(state.getJSONObject("result").toString()));put("result",response);put("mergePending",false);put("resultPending",true);persist(true);reconcile();}
        catch(Exception error){put("error",error.getMessage()==null?"Das Ergebnis konnte noch nicht übernommen werden.":error.getMessage());persist(true);}changed();
    }
    public void discardPendingStock(){
        if(busy()||!awaitingMerge()||!sameOwner())return;
        JSONObject response=state.optJSONObject("result");if(response==null)return;
        try{response=new JSONObject(response.toString());response.put("message",response.optString("message")+"\n\nDas Fotoergebnis wurde nicht in deinen Vorrat übernommen.");response.remove("uiRequest");response.remove("variant");}catch(JSONException error){throw new IllegalStateException(error);}
        put("result",response);put("submittedPantry",false);put("declined",true);put("mergePending",false);put("resultPending",true);persist(true);reconcile();changed();
    }
    private void reconcile(){
        if(!state.optBoolean("resultPending")||!sameOwner())return;
        JSONObject response=state.optJSONObject("result");if(response==null)return;
        boolean clarification=response.optString("recipePhotoStatus").equals("needs-clarification"),stock=state.optBoolean("submittedPantry");String photo=state.optString("submittedPhoto"),reply=response.optString("message");
        if(stock&&!response.has("recipePhotoStatus"))reply+="\n\n"+response.optInt("addedStock")+" Lebensmittel ergänzt.";
        if(response.optJSONObject("variant")!=null)reply+="\n\nDas neue Rezept findest du in deiner Rezeptübersicht.";
        if(!ChatHistory.exchange(context,owner,state.optString("requestId"),state.optString("submittedText")+(photo.isEmpty()?"":"\n[Foto]"),reply)){put("error","Die Antwort konnte noch nicht im Verlauf gespeichert werden. Öffne den Assistenten erneut.");persist(true);return;}
        put("draft",clarification?"Nachtrag zum selben Rezeptfoto: ":"");
        if(clarification)put("pantry",false);
        else if(photo.equals(state.optString("photo"))){put("photo","");put("inbox","");put("pantry",false);if(!state.optBoolean("declined")){if(!state.optString("submittedInbox").isEmpty())PhotoInbox.consumed(context,state.optString("submittedInbox"));if(!photo.isEmpty())try{PhotoAttachment.restore(context,photo).delete();}catch(Exception ignored){}}}
        put("uiRequest",response.optJSONObject("uiRequest"));put("resultPending",false);put("error","");put("signature","");put("requestId",UUID.randomUUID().toString());state.remove("snapshot");state.remove("scene");state.remove("result");state.remove("declined");persist(true);
    }
}
