package com.kitchenos.connect.kitchen;
import android.content.Context;
import com.kitchenos.common.Pairing;
import org.json.*;

public final class ChatHistory {
    private static final Object EXCHANGES=new Object();
    private static String key(String uid)throws Exception{return "chat-"+Pairing.fingerprint(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static JSONArray read(Context context,String uid){try{if(!uid.equals(new CloudSession(context).account().optString("uid")))return new JSONArray();return new JSONArray(context.getSharedPreferences("assistant-history",0).getString(key(uid),"[]"));}catch(Exception e){return new JSONArray();}}
    public static void save(Context context,String uid,JSONArray history){try{if(!uid.equals(new CloudSession(context).account().optString("uid")))return;while(history.length()>60)history.remove(0);context.getSharedPreferences("assistant-history",0).edit().putString(key(uid),history.toString()).apply();}catch(Exception ignored){}}
    public static JSONArray context(Context context,String uid){JSONArray result=new JSONArray(),history=read(context,uid);try{for(int i=Math.max(0,history.length()-12);i<history.length();i++){JSONObject row=history.optJSONObject(i);if(row==null)continue;String message=row.optString("text").trim();if(message.isEmpty())continue;int end=Math.min(1500,message.length());if(end>0&&end<message.length()&&Character.isHighSurrogate(message.charAt(end-1)))end--;result.put(new JSONObject().put("role",row.optBoolean("user")?"user":"assistant").put("text",message.substring(0,end)));}}catch(Exception ignored){}return result;}
    /** Commit a completed exchange once, independently of any open chat view. */
    public static boolean exchange(Context context,String uid,String requestId,String prompt,String reply){synchronized(EXCHANGES){try{
        if(!uid.equals(new CloudSession(context).account().optString("uid")))return false;
        JSONArray rows=read(context,uid);boolean user=false,assistant=false;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&requestId.equals(row.optString("requestId"))){if(row.optBoolean("user"))user=true;else assistant=true;}}
        if(!user)rows.put(new JSONObject().put("user",true).put("text",prompt).put("requestId",requestId));
        if(!assistant)rows.put(new JSONObject().put("user",false).put("text",reply).put("requestId",requestId));
        while(rows.length()>60)rows.remove(0);
        return context.getSharedPreferences("assistant-history",0).edit().putString(key(uid),rows.toString()).commit();
    }catch(Exception error){return false;}}}
    private ChatHistory(){}
}
