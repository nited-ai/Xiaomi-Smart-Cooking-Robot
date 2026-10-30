package com.kitchenos.connect;

import com.kitchenos.common.Recipe;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Small bounded TLS endpoint. It has no reference to any cooker controller. */
public final class RecipeServer implements Closeable {
    public interface Sink { boolean save(Recipe recipe)throws Exception; }
    public interface PhotoSink {boolean save(JSONObject photo)throws Exception;}
    public interface Access {
        boolean allowed(String token,String path);
        JSONObject call(String path,String token,JSONObject body)throws Exception;
    }
    private Access access;
    private final PhotoSink photos;
    private final ServerSocket server;
    private final String token;
    private final Sink sink;
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final Set<Socket> clients=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public RecipeServer(ServerSocket server,String token,Sink sink){this(server,token,sink,null);}
    public RecipeServer(ServerSocket server,String token,Sink sink,PhotoSink photos){this.server=server;this.token=token;this.sink=sink;this.photos=photos;}
    public RecipeServer(ServerSocket server,Access access,Sink sink,PhotoSink photos){this(server,"",sink,photos);this.access=access;}
    public void start(){new Thread(()->{
        while(!closed)try{
            Socket s=server.accept();s.setSoTimeout(8000);clients.add(s);
            try{pool.execute(()->handle(s));}catch(RejectedExecutionException e){clients.remove(s);s.close();}
        }catch(IOException e){if(!closed){android.util.Log.e("KitchenOSRecipeReceiver","Accept failed",e);close();}}
    },"KitchenOS-Recipe-Accept").start();}
    private static String line(InputStream in,int[] remaining)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int v;
        while((v=in.read())!=-1){if(--remaining[0]<0)throw new IOException("Header zu groß");if(v=='\n')return b.toString("US-ASCII").replace("\r","");b.write(v);}
        throw new EOFException();
    }
    private void handle(Socket socket){
        try(Socket s=socket){
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();int[] budget={8192};
            String request=line(in,budget);Map<String,String> headers=new HashMap<>();String h;
            while(!(h=line(in,budget)).isEmpty()){
                int p=h.indexOf(':');if(p<1){reply(out,400,"Ungültiger Header");return;}
                String key=h.substring(0,p).trim().toLowerCase(Locale.ROOT);
                if(headers.put(key,h.substring(p+1).trim())!=null){reply(out,400,"Doppelter Header");return;}
            }
            String path=request.startsWith("POST ")&&request.endsWith(" HTTP/1.1")?request.substring(5,request.length()-9):"";
            String authorization=headers.getOrDefault("authorization","");
            String bearer=authorization.startsWith("Bearer ")?authorization.substring(7):"";
            byte[] expected=("Bearer "+token).getBytes(StandardCharsets.UTF_8);
            if(access!=null?!access.allowed(bearer,path):!MessageDigest.isEqual(expected,authorization.getBytes(StandardCharsets.UTF_8))){reply(out,401,"Kopplung erforderlich");return;}
            boolean deviceRequest=access!=null&&path.matches("/v1/device/[a-z-]+");
            boolean photoRequest=request.equals("POST /v1/assistant/photos HTTP/1.1")&&photos!=null;
            if(!request.equals("POST /v1/recipes HTTP/1.1")&&!photoRequest&&!deviceRequest){reply(out,404,"Unbekannter Endpunkt");return;}
            if(headers.containsKey("transfer-encoding")){reply(out,400,"Content-Length erforderlich");return;}
            if(!headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/json")){reply(out,415,"JSON erforderlich");return;}
            int length;
            try{length=Integer.parseInt(headers.getOrDefault("content-length","-1"));}catch(NumberFormatException e){reply(out,400,"Ungültige Länge");return;}
            if(length<0){reply(out,411,"Länge erforderlich");return;}if(length>(deviceRequest?16384:photoRequest?810000:Recipe.MAX_BYTES)){reply(out,413,"Anfrage zu groß");return;}
            byte[] data=new byte[length];int read=0,n;
            while(read<length&&(n=in.read(data,read,length-read))!=-1)read+=n;
            if(read!=length){reply(out,400,"Unvollständiges Rezept");return;}
            if(access!=null&&!access.allowed(bearer,path)){reply(out,401,"Kopplung abgelaufen oder entfernt");return;}
            if(deviceRequest){try{replyJson(out,200,access.call(path,bearer,new JSONObject(new String(data,StandardCharsets.UTF_8))).toString());}catch(IllegalArgumentException e){reply(out,422,"Eingaben ungültig. Bitte prüfen und erneut versuchen.");}catch(Exception e){reply(out,409,"Vorgang derzeit nicht möglich. Bitte den Hinweis auf der Maschine prüfen.");}return;}
            try{if(photoRequest){boolean created=photos.save(new JSONObject(new String(data,StandardCharsets.UTF_8)));reply(out,created?201:200,"Foto als Entwurf gespeichert");return;}Recipe recipe=Recipe.parse(new String(data,StandardCharsets.UTF_8));boolean created=sink.save(recipe);reply(out,created?201:200,created?"Rezept gespeichert":"Rezept bereits vorhanden");}
            catch(Exception e){reply(out,422,"Rezept konnte nicht gespeichert werden");}
        }catch(IOException failure){android.util.Log.w("KitchenOSRecipeReceiver","TLS/request failed",failure);}finally{clients.remove(socket);}
    }
    private static void reply(OutputStream out,int code,String text)throws IOException {
        replyJson(out,code,"{\"message\":\""+text+"\"}");
    }
    private static void replyJson(OutputStream out,int code,String json)throws IOException {
        byte[] data=json.getBytes(StandardCharsets.UTF_8);
        String header="HTTP/1.1 "+code+" Response\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
    }
    @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(Socket s:clients)try{s.close();}catch(IOException ignored){}pool.shutdownNow();}
}
