package com.kitchenos.connect.kitchen;
import android.content.Context;
import android.util.AtomicFile;
import com.kitchenos.common.Pairing;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Account-specific prepared imports. Shared source recipes remain in RecipeStore. */
public final class MachineProgramStore {
    private static final Object LOCK=new Object();private final Context context;private final String uid;private final AtomicFile file;
    public MachineProgramStore(Context context){this.context=context.getApplicationContext();uid=new CloudSession(context).account().optString("uid");try{file=new AtomicFile(new File(context.getFilesDir(),"machine-programs-"+Pairing.fingerprint(uid.getBytes(StandardCharsets.UTF_8))+".json"));}catch(Exception e){throw new IllegalStateException(e);}}
    public JSONArray all(){synchronized(LOCK){if(uid.isEmpty()||!uid.equals(new CloudSession(context).account().optString("uid"))||!file.getBaseFile().exists())return new JSONArray();try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4194304)throw new IOException("Rezeptspeicher ist zu groß");out.write(buffer,0,n);}JSONArray result=new JSONArray(out.toString("UTF-8"));return uid.equals(new CloudSession(context).account().optString("uid"))?result:new JSONArray();}catch(Exception e){throw new IllegalStateException("Gespeicherte Kochprogramme konnten nicht gelesen werden",e);}}}
    public void save(JSONObject variant,String expectedUid)throws Exception{synchronized(LOCK){if(uid.isEmpty()||!uid.equals(expectedUid)||!uid.equals(new CloudSession(context).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert");if(!variant.getBoolean("private"))throw new JSONException("Privates Rezept erwartet");MachineProgram.validate(variant.getJSONObject("program"));JSONArray old=all(),next=new JSONArray();for(int i=0;i<old.length();i++){JSONObject row=old.getJSONObject(i);if(!row.getString("recipeId").equals(variant.getString("recipeId")))next.put(row);}next.put(variant);if(next.length()>100)throw new IOException("Zu viele gespeicherte Maschinenrezepte");byte[] bytes=next.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>4194304)throw new IOException("Rezeptspeicher ist voll");FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}}
}
