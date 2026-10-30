package com.kitchenos.connect.kitchen;
import android.content.*;
import android.net.Uri;
import android.util.Base64;
import com.kitchenos.common.Pairing;
import java.io.*;
import java.util.*;
import org.json.*;

/** Paired phone photos become local drafts. Receipt never invokes cloud AI. */
public final class PhotoInbox {
    private static final Object LOCK=new Object();
    private static String key(Context context,String id)throws Exception{return key(new CloudSession(context).account().optString("uid"),id);}
    private static String key(String uid,String id)throws Exception{if(!UUID.fromString(id).toString().equals(id))throw new IOException("Ungültiger Fotobezug");return Pairing.fingerprint((uid+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static boolean save(Context context,JSONObject photo)throws Exception{synchronized(LOCK){
        if(photo.getInt("version")!=1||!Arrays.asList("pantry","chat").contains(photo.getString("intent")))throw new IOException("Unbekanntes Fotoformat");String uid=new CloudSession(context).account().optString("uid"),id=photo.getString("id"),key=key(uid,id);SharedPreferences store=context.getSharedPreferences("photo-inbox",0);if(store.contains(key))return false;if(store.getAll().size()>=50)throw new IOException("Bitte zuerst die empfangenen Fotos im Assistenten bearbeiten");
        JSONObject image=photo.getJSONObject("image");String encoded=image.getString("data");if(!image.getString("mimeType").equals("image/jpeg")||encoded.length()>800000||!encoded.matches("[A-Za-z0-9+/]+={0,2}"))throw new IOException("Ungültiges Foto");byte[] bytes=Base64.decode(encoded,Base64.DEFAULT);if(bytes.length<4||bytes.length>PhotoAttachment.MAX_BYTES||bytes[0]!=(byte)255||bytes[1]!=(byte)216)throw new IOException("Ungültiges Foto");
        File incoming=File.createTempFile("phone-photo-",".jpg",context.getCacheDir());File clean=null;try{try(OutputStream out=new FileOutputStream(incoming)){out.write(bytes);}clean=PhotoAttachment.importPhoto(context,Uri.fromFile(incoming));JSONObject row=new JSONObject().put("path",clean.getAbsolutePath()).put("intent",photo.getString("intent")).put("id",id).put("owner",Pairing.fingerprint(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)));if(!uid.equals(new CloudSession(context).account().optString("uid")))throw new IOException("Dein Konto hat sich geändert");if(!store.edit().putString(key,row.toString()).commit())throw new IOException("Foto konnte nicht gespeichert werden");return true;}catch(Exception e){if(clean!=null)clean.delete();throw e;}finally{incoming.delete();}
    }}
    public static JSONObject get(Context context,String id)throws Exception{synchronized(LOCK){String uid=new CloudSession(context).account().optString("uid");JSONObject row=new JSONObject(context.getSharedPreferences("photo-inbox",0).getString(key(uid,id),"{}"));PhotoAttachment.restore(context,row.getString("path"));if(!uid.equals(new CloudSession(context).account().optString("uid")))throw new IOException("Dein Konto hat sich geändert");return row;}}
    public static JSONArray pending(Context context){synchronized(LOCK){JSONArray result=new JSONArray();try{String uid=new CloudSession(context).account().optString("uid"),owner=Pairing.fingerprint(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));SharedPreferences store=context.getSharedPreferences("photo-inbox",0);for(Map.Entry<String,?> item:store.getAll().entrySet())try{JSONObject row=new JSONObject(String.valueOf(item.getValue()));if(owner.equals(row.optString("owner"))){PhotoAttachment.restore(context,row.getString("path"));result.put(new JSONObject().put("id",row.getString("id")).put("intent",row.getString("intent")));}}catch(Exception stale){store.edit().remove(item.getKey()).apply();}if(!uid.equals(new CloudSession(context).account().optString("uid")))return new JSONArray();}catch(Exception ignored){}return result;}}
    public static void consumed(Context context,String id){try{context.getSharedPreferences("photo-inbox",0).edit().remove(key(context,id)).apply();}catch(Exception ignored){}}
    private PhotoInbox(){}
}
