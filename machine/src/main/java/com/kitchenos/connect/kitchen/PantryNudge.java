package com.kitchenos.connect.kitchen;

import android.content.Context;
import android.content.SharedPreferences;
import com.kitchenos.common.Pairing;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small, account-owned onboarding cue. Dismissal schedules an in-app reminder. */
public final class PantryNudge {
    public static final long DAY=24L*60*60*1000;
    private final Context context;
    private final SharedPreferences store;
    public PantryNudge(Context context){
        this.context=context;
        String uid=new CloudSession(context).account().optString("uid");
        try{store=context.getSharedPreferences("pantry-nudge-"+Pairing.fingerprint(uid.getBytes(StandardCharsets.UTF_8)),0);}catch(Exception error){throw new IllegalStateException(error);}
    }
    public boolean complete(){
        JSONObject data=new KitchenStore(context).snapshot();
        JSONArray photos=data.optJSONArray("appliedPhotoRequests"),pantry=data.optJSONArray("pantry");
        return (photos!=null&&photos.length()>0)||(pantry!=null&&pantry.length()>0);
    }
    public boolean showHome(){return !complete()&&!store.contains("remindAt");}
    public void dismiss(long now){store.edit().putLong("remindAt",now+DAY).commit();}
    public boolean takeReminder(long now){
        long due=store.getLong("remindAt",Long.MAX_VALUE);
        if(complete()||now<due)return false;
        store.edit().putLong("remindAt",now+2*DAY).commit();return true;
    }
}
