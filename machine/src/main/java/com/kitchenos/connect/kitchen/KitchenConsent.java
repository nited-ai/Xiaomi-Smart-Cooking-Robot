package com.kitchenos.connect.kitchen;
import android.app.*;
import android.content.Context;
import com.kitchenos.connect.ui.RotaryDialogs;
import org.json.JSONObject;

/** First use stays in context instead of sending the user through a settings form. */
public final class KitchenConsent {
    public static final String AI_TITLE="Kalomatik mit KI verwenden?";
    public static final String AI_MESSAGE="Kalomatik wertet deine Nachricht und angehängte Fotos online aus. Für passende Vorschläge werden auch dein Vorrat, Kochplan und deine Vorlieben verwendet. Gemeinsame Rezeptbeiträge kannst du getrennt in den Vorlieben erlauben.";
    public static boolean accept(Context context,String owner)throws Exception{
        if(owner.isEmpty()||!owner.equals(new CloudSession(context).account().optString("uid")))return false;
        KitchenStore store=new KitchenStore(context);JSONObject p=store.snapshot().getJSONObject("preferences");store.preferences(p.optString("diet"),p.optString("allergies"),p.optString("dislikes"),p.optString("notes"),p.optInt("servings",2),true,p.optBoolean("communityConsent"));return true;
    }
    public static void ai(Activity activity,Runnable confirmed){
        String uid=new CloudSession(activity).account().optString("uid");if(uid.isEmpty())return;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(AI_TITLE).setMessage(AI_MESSAGE).setNegativeButton("Später",null).setPositiveButton("KI verwenden",(d,w)->{try{if(accept(activity,uid))confirmed.run();}catch(Exception e){android.widget.Toast.makeText(activity,"Die Einstellung konnte nicht gespeichert werden.",android.widget.Toast.LENGTH_LONG).show();}}).create();dialog.show();RotaryDialogs.bind(activity,dialog);
    }
    private KitchenConsent(){}
}
