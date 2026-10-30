package com.kitchenos.share;
import com.kitchenos.common.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class RecipeSender {
    public static void send(Pairing pairing,Recipe recipe)throws Exception {
        sendJson(pairing,"/v1/recipes",recipe.json());
    }
    public static void sendPhoto(Pairing pairing,JSONObject photo)throws Exception{sendJson(pairing,"/v1/assistant/photos",photo);}
    public static void send(android.content.Context context,Pairing pairing,Recipe recipe)throws Exception { sendJson(PhoneLink.resolve(context,pairing,15000),"/v1/recipes",recipe.json(),PhoneLink.wifi(context)); }
    public static void sendPhoto(android.content.Context context,Pairing pairing,JSONObject photo)throws Exception { sendJson(PhoneLink.resolve(context,pairing,15000),"/v1/assistant/photos",photo,PhoneLink.wifi(context)); }
    private static void sendJson(Pairing pairing,String path,JSONObject payload)throws Exception{sendJson(pairing,path,payload,null);}
    private static void sendJson(Pairing pairing,String path,JSONObject payload,android.net.Network network)throws Exception{
        HttpsURLConnection c=pairing.connection(path,network);
        try{
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            if(status==401)throw new IOException("Kopplung ungültig. Bitte an der Maschine neu koppeln.");
            if(status!=200&&status!=201)throw new IOException("Maschine konnte das Rezept nicht speichern (HTTP "+status+").");
            // Only a complete application response confirms durable receipt.
            try(InputStream in=c.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()){
                byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(result.size()+n>4096)throw new IOException("Ungültige Antwort");result.write(buffer,0,n);}
                if(!new JSONObject(result.toString("UTF-8")).has("message"))throw new IOException("Empfang nicht bestätigt");
            }
        }finally{c.disconnect();}
    }
}
