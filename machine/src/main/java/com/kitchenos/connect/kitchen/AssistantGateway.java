package com.kitchenos.connect.kitchen;
import android.content.Context;
import java.io.*;
import org.json.*;

/** Same request, account binding and pantry merge for the kitchen and cooking assistants. */
public final class AssistantGateway {
    /** The remote request is already complete. Keep its answer for a local-only rebase. */
    public static final class LocalMergeConflict extends IOException {
        public final JSONObject response;
        public LocalMergeConflict(JSONObject response)throws JSONException{super("Deine Küchendaten wurden während der Anfrage geändert. Das Fotoergebnis ist gespeichert und kann mit dem aktuellen Vorrat abgeglichen werden.");this.response=new JSONObject(response.toString());}
    }
    /** Resolve shared context once before persisting the request; retries retain this exact payload. */
    public static JSONObject requestSnapshot(Context context,JSONObject snapshot)throws Exception{
        JSONObject remote=new JSONObject();JSONArray items=new JSONArray();try{KalomatikPlans shared=new KalomatikPlans(context);remote=shared.sharedPreferences();items=shared.getItems();}catch(Exception unavailable){/* Local preferences remain available. */}
        return sharedSnapshot(snapshot,remote,items).put("controlProfileId",com.kitchenos.connect.control.ControlProfile.ID);
    }
    public static JSONObject sharedSnapshot(JSONObject source,JSONObject remote,JSONArray items)throws Exception{
        JSONObject snapshot=new JSONObject(source.toString()),preferences=snapshot.getJSONObject("preferences");JSONArray meals=new JSONArray();
        for(String field:new String[]{"diet","allergies","dislikes"}){String local=preferences.optString(field),value=remote.optString(field);if(!value.isEmpty()&&local.isEmpty())preferences.put(field,value.substring(0,Math.min(2000,value.length())));}
        String notes=preferences.optString("notes"),remoteNotes=remote.optString("notes");if(!remoteNotes.isEmpty()){String combined=notes+"\n"+remoteNotes;preferences.put("notes",combined.substring(0,Math.min(2000,combined.length())));}
        for(int i=0;i<Math.min(30,items.length());i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String title=item.optString("title");meals.put(new JSONObject().put("date",item.optString("date")).put("title",title.substring(0,Math.min(160,title.length()))).put("time",item.optString("time")));}
        return snapshot.put("plannedMeals",meals);
    }
    public static JSONObject send(Context context,String owner,String id,String message,File photo,boolean pantry,boolean create,JSONObject scene,JSONObject snapshot)throws Exception{
        CloudSession session=new CloudSession(context);if(owner.isEmpty()||!owner.equals(session.account().optString("uid")))throw new IOException("Bitte mit deinem Kalomatik-Konto anmelden");
        JSONObject preferences=new JSONObject(snapshot.getJSONObject("preferences").toString());JSONArray plannedMeals=snapshot.optJSONArray("plannedMeals");if(plannedMeals==null)plannedMeals=new JSONArray();
        JSONObject request=new JSONObject().put("requestId",id).put("message",message).put("intent",pantry?"pantry":"chat").put("preferences",preferences).put("plannedMeals",plannedMeals).put("pantry",snapshot.getJSONArray("pantry")).put("plans",snapshot.getJSONArray("plans")).put("conversation",snapshot.optJSONArray("conversation")==null?new JSONArray():snapshot.optJSONArray("conversation")).put("consent",true);
        if(snapshot.has("controlProfileId"))request.put("controlProfileId",snapshot.getString("controlProfileId"));
        if(photo!=null)request.put("photo",PhotoAttachment.payload(photo));if(scene!=null)request.put("uiScene",scene);
        JSONObject response;
        try{response=session.api(create?"/v1/recipes/create":"/v1/assistant/message",request);}
        catch(CloudSession.ApiException error){
            if(!owner.equals(session.account().optString("uid")))throw new IOException("Dein Konto hat sich geändert");
            JSONObject status=null;
            if(error.status==429&&(error.code.equals("personal_daily_limit")||error.code.equals("service_daily_limit")))try{status=session.api("/v1/assistant/status",new JSONObject());}catch(Exception unavailable){/* The original limit remains actionable without a second request. */}
            if(!owner.equals(session.account().optString("uid")))throw new IOException("Dein Konto hat sich geändert");
            String detail=error.status==429?AssistantLimit.message(error.code,status):"";
            if(!detail.isEmpty())throw new IOException(detail,error);throw error;
        }
        return applyResponse(context,owner,id,pantry,snapshot.getLong("revision"),response);
    }
    /** Apply a received answer locally; this method never contacts the model or backend. */
    public static JSONObject applyResponse(Context context,String owner,String id,boolean pantry,long revision,JSONObject response)throws Exception{
        if(!owner.equals(new CloudSession(context).account().optString("uid")))throw new IOException("Dein Konto hat sich geändert");
        int added=0;try{if(pantry)added=new KitchenStore(context).recognizedStock(id,revision,response.getJSONArray("pantryItems"));}catch(KitchenStore.RevisionConflict conflict){throw new LocalMergeConflict(response);}response.put("addedStock",added);
        JSONObject variant=response.optJSONObject("variant");if(variant!=null){MachineProgram.card(variant);if(variant.optBoolean("private"))new MachineProgramStore(context).save(variant,owner);}
        return response;
    }
    private AssistantGateway(){}
}
