package com.kitchenos.connect;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Intent;
import com.kitchenos.common.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.net.ssl.*;

public class ConnectSmoke extends Instrumentation {
    private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","");start();}
    private static void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);}
    @Override public void onStart(){Bundle result=new Bundle();Recipe test=null;RecipeStore store=null;RecipeServer server=null;
        try{
            if(mode.equals("stop")){getTargetContext().stopService(new Intent(getTargetContext(),RecipeReceiverService.class));new File(getTargetContext().getFilesDir(),"smoke-pairing.txt").delete();result.putString("result","receiver stopped");finish(-1,result);return;}
            store=new RecipeStore(getTargetContext());
            DeviceIdentity identity=new DeviceIdentity(getTargetContext());
            javax.net.ssl.SSLServerSocket socket=(javax.net.ssl.SSLServerSocket)identity.tls.getServerSocketFactory().createServerSocket(8765);socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            RecipeStore receiverStore=store;server=new RecipeServer(socket,identity.token,receiverStore::save);server.start();
            Pairing pairing=new Pairing("127.0.0.1",identity.token,identity.pin);
            if(mode.equals("serve")){
                File done=new File(getTargetContext().getFilesDir(),"smoke-done.txt");done.delete();
                try(FileOutputStream file=getTargetContext().openFileOutput("smoke-pairing.txt",0)){file.write(pairing.uri().getBytes(StandardCharsets.UTF_8));}
                long until=android.os.SystemClock.elapsedRealtime()+60000;while(!done.exists()&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);
                check(done.exists(),"Senderprüfung hat nicht geantwortet");done.delete();new File(getTargetContext().getFilesDir(),"smoke-pairing.txt").delete();
                result.putString("result","cross-UID receiver fixture finished");finish(-1,result);return;
            }
            test=new Recipe(UUID.randomUUID().toString(),"Transportprüfung — Äpfel & Öl","",new JSONArray().put("200 g Äpfel"),new JSONArray().put("Nur als Anleitung lesen."));
            String json=test.json().toString();
            check(post(pairing,json)==201,"Neue Rezepte müssen 201 liefern");
            check(post(pairing,json)==200,"Wiederholung muss idempotent sein");
            check(post(pairing,"{bad")==422,"Ungültiges JSON muss abgelehnt werden");
            check(post(pairing,test.json().put("title","Manipulierter Wiederholungsversuch").toString())==422,"Gleiche ID mit anderem Inhalt ablehnen");
            Pairing wrong=new Pairing(pairing.host,"00000000000000000000000000000000",pairing.pin);
            check(post(wrong,json)==401,"Ungültiges Token muss abgelehnt werden");
            Pairing badPin=new Pairing(pairing.host,pairing.token,new String(new char[64]).replace('\0','0'));
            boolean pinRejected=false;try{post(badPin,json);}catch(SSLException expected){pinRejected=true;}check(pinRejected,"Abweichendes Zertifikat ablehnen");
            check(raw(pairing,"Content-Length: "+(Recipe.MAX_BYTES+1)+"\r\n")==413,"Zu große Rezepte vor dem Body ablehnen");
            check(raw(pairing,"Transfer-Encoding: chunked\r\n")==400,"Mehrdeutige Nachrichtenlängen ablehnen");
            check(raw(pairing,"Content-Length: 1\r\nContent-Length: 2\r\n")==400,"Doppelte Längenheader ablehnen");
            int count=0;for(Recipe r:store.all())if(r.id.equals(test.id)){count++;check(r.title.equals(test.title)&&r.ingredients.optString(0).equals("200 g Äpfel"),"UTF-8-Inhalt unverändert speichern");}check(count==1,"Genau eine dauerhafte Kopie");
            // Android 10 can terminate the instrumented process inside finish().
            // Dispose fixtures before reporting completion, rather than relying on finally.
            store.delete(test);test=null;server.close();server=null;
            result.putString("result","PASS: TLS, pin rejection, token rejection, JSON rejection, oversized rejection, duplicate/collision handling, UTF-8 persistence");finish(-1,result);
        }catch(Throwable e){android.util.Log.e("KitchenOSConnectSmoke","Check failed",e);result.putString("failure",e.getClass().getName()+": "+e.getMessage());finish(0,result);}
        finally{if(test!=null&&store!=null)try{store.delete(test);}catch(Exception ignored){}if(server!=null)server.close();}
    }
    private int post(Pairing pairing,String json)throws Exception{
        HttpsURLConnection c=pairing.connection("/v1/recipes");try{c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=json.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}return c.getResponseCode();}finally{c.disconnect();}
    }
    private int raw(Pairing p,String headers)throws Exception {
        try(SSLSocket socket=(SSLSocket)p.connection("/v1/recipes").getSSLSocketFactory().createSocket(p.host,8765)){
            socket.setSoTimeout(5000);socket.startHandshake();
            socket.getOutputStream().write(("POST /v1/recipes HTTP/1.1\r\nHost: "+p.host+"\r\nAuthorization: Bearer "+p.token+"\r\nContent-Type: application/json\r\n"+headers+"\r\n").getBytes(StandardCharsets.US_ASCII));
            return Integer.parseInt(new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine().split(" ")[1]);
        }
    }
}
