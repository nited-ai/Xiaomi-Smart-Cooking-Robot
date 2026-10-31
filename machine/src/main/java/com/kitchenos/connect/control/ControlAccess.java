package com.kitchenos.connect.control;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Chooses the owner's configured factory session or the existing Magisk path. */
public final class ControlAccess {
    public static final String FILE="factory-access.json";
    private ControlAccess(){}
    private static File file(Context context){return new File(context.getFilesDir(),FILE);}
    private static JSONObject envelope(Context context)throws IOException{
        File source=file(context);if(!source.exists())return null;
        try{
            StructStat stat=Os.lstat(source.getAbsolutePath());
            if(!OsConstants.S_ISREG(stat.st_mode)||stat.st_uid!=android.os.Process.myUid()||(stat.st_mode&077)!=0||stat.st_size>4096)
                throw new IOException("Invalid private access configuration");
            byte[] bytes=new byte[(int)stat.st_size];
            try(FileInputStream input=new FileInputStream(source)){int offset=0;for(int n;offset<bytes.length&&(n=input.read(bytes,offset,bytes.length-offset))>0;)offset+=n;if(offset!=bytes.length||input.read()!=-1)throw new IOException("Access configuration changed");}
            JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(value.length()!=4||!value.has("schemaVersion")||!value.has("socket")||!value.has("token")||!value.has("expiresAtSeconds")
                    ||!(value.get("schemaVersion") instanceof Integer)||value.getInt("schemaVersion")!=1
                    ||!(value.get("socket") instanceof String)||!(value.get("token") instanceof String)
                    ||!(value.get("expiresAtSeconds") instanceof Long||value.get("expiresAtSeconds") instanceof Integer))throw new IOException("Invalid access configuration fields");
            return value;
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException("Cannot read private access configuration");}
    }
    public static FactoryAccessSession readSession(Context context)throws IOException{
        JSONObject value=envelope(context);if(value==null)return null;
        try{return parse(value);}catch(Exception error){throw new IOException("Factory access expired or is invalid; reconnect with the owner installer");}
    }
    public static FactoryAccessSession parse(JSONObject value)throws IOException{
        try{
            if(value.length()!=4||!value.has("schemaVersion")||!value.has("socket")||!value.has("token")||!value.has("expiresAtSeconds")
                    ||!(value.get("schemaVersion") instanceof Integer)||value.getInt("schemaVersion")!=1
                    ||!(value.get("socket") instanceof String)||!(value.get("token") instanceof String)
                    ||!(value.get("expiresAtSeconds") instanceof Long||value.get("expiresAtSeconds") instanceof Integer))throw new IOException("Invalid access fields");
            return new FactoryAccessSession(value.getString("socket"),value.getString("token"),value.getLong("expiresAtSeconds"),android.os.Process.myUid(),System.currentTimeMillis()/1000);
        }catch(Exception error){throw new IOException("Invalid or expired factory access session");}
    }
    /** Used only to bind privileged cleanup, including an already expired lease. */
    public static String storedSocket(Context context)throws IOException{
        JSONObject value=envelope(context);if(value==null)return null;
        try{
            String socket=value.getString("socket");
            if(!socket.matches("xcr\\.access\\."+android.os.Process.myUid()+"\\.[a-f0-9]{16}"))throw new IOException("Invalid stored socket");
            return socket;
        }catch(JSONException error){throw new IOException("Invalid stored socket");}
    }
    public static void store(Context context,FactoryAccessSession session)throws IOException{
        AtomicFile target=new AtomicFile(file(context));FileOutputStream output=null;
        try{
            JSONObject value=new JSONObject().put("schemaVersion",1).put("socket",session.socket).put("token",session.token).put("expiresAtSeconds",session.expiresAtSeconds);
            output=target.startWrite();output.write(value.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(output);output=null;
            Os.chmod(file(context).getAbsolutePath(),0600);
        }catch(Exception error){if(output!=null)target.failWrite(output);throw new IOException("Cannot save private access configuration");}
    }
    public static void clear(Context context){new AtomicFile(file(context)).delete();}
    public static void verifyIdentity(Context context,FactoryAccessSession session)throws IOException{
        if(session==null)throw new IOException("No factory access session");
        try(LocalSocket ignored=connect(session,"ID")){}
    }
    private static LocalSocket connect(FactoryAccessSession session,String operation)throws IOException{
        LocalSocket socket=new LocalSocket();
        try{
            socket.connect(new LocalSocketAddress(session.socket,LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(3000);
            // Check before transmitting the installation token to the server.
            if(socket.getPeerCredentials().getUid()!=0)throw new IOException("Factory access peer is not root");
            socket.getOutputStream().write(("XCR1 "+session.token+" "+session.mode(operation)+"\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            ByteArrayOutputStream response=new ByteArrayOutputStream();int value;
            while((value=socket.getInputStream().read())!=-1&&value!='\n'){if(value<32||value>126||response.size()>=256)throw new IOException("Invalid factory access handshake");response.write(value);}
            if(value!='\n'||!session.accepts(response.toString("US-ASCII"),operation))throw new IOException("Factory access was rejected or expired");
            socket.setSoTimeout(0);return socket;
        }catch(Exception error){try{socket.close();}catch(IOException ignored){}throw new IOException("Factory access unavailable; renew the owner session");}
    }
    public static Process open(Context context,String operation)throws IOException{
        if(!"ID".equals(operation)&&!"SERVE".equals(operation)&&!"PROBE".equals(operation))throw new IOException("Unsupported access operation");
        FactoryAccessSession session=readSession(context);
        if(session==null){
            String command="id";
            if(!operation.equals("ID")){
                File binary=new File(context.getApplicationInfo().nativeLibraryDir,"libkitchenos_bridge.so");
                if(!binary.isFile())throw new IOException("Native controller is missing");
                command="exec '"+binary.getAbsolutePath().replace("'","'\"'\"'")+"' "+(operation.equals("SERVE")?"--serve":"--probe-fast");
            }
            return new ProcessBuilder("/sbin/magisk","su","-c",command).redirectErrorStream(true).start();
        }
        LocalSocket socket=connect(session,operation);
        if(operation.equals("ID")){socket.close();return new FinishedProcess("uid=0(root) factory-adb-session\n");}
        return new SocketProcess(socket);
    }
    private static final class FinishedProcess extends Process{
        final InputStream input;FinishedProcess(String output){input=new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));}
        public InputStream getInputStream(){return input;}public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
        public OutputStream getOutputStream(){return new ByteArrayOutputStream();}public int waitFor(){return 0;}public int exitValue(){return 0;}
        public boolean waitFor(long timeout,TimeUnit unit){return true;}public boolean isAlive(){return false;}public void destroy(){}
    }
    /** Closing the socket delivers EOF; the broker retains the bridge's STOP handling. */
    private static final class SocketProcess extends Process{
        final LocalSocket socket;final InputStream input;private int exit=-1;
        SocketProcess(LocalSocket socket)throws IOException{
            this.socket=socket;input=new FilterInputStream(socket.getInputStream()){
                final ByteArrayOutputStream line=new ByteArrayOutputStream();
                private void observe(int value){
                    if(value==-1){finished(1);return;}
                    if(value=='\n'){
                        try{JSONObject record=new JSONObject(line.toString("UTF-8"));if(record.optString("type").equals("access-exit")){int code=record.getInt("code");finished(code>=0&&code<=255?code:1);}}catch(Exception ignored){}
                        line.reset();
                    }else if(line.size()<8192)line.write(value);else line.reset();
                }
                @Override public int read()throws IOException{try{int value=in.read();observe(value);return value;}catch(IOException error){finished(1);throw error;}}
                @Override public int read(byte[] bytes,int offset,int length)throws IOException{try{int count=in.read(bytes,offset,length);if(count==-1)observe(-1);else for(int i=offset;i<offset+count;i++)observe(bytes[i]&255);return count;}catch(IOException error){finished(1);throw error;}}
            };
        }
        private synchronized void finished(int value){if(exit<0){exit=value;notifyAll();}}
        public InputStream getInputStream(){return input;}public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
        public OutputStream getOutputStream(){try{return socket.getOutputStream();}catch(IOException error){return new OutputStream(){public void write(int value)throws IOException{throw new IOException("Access connection closed");}};}}
        public synchronized int waitFor()throws InterruptedException{while(exit<0)wait();return exit;}
        public synchronized boolean waitFor(long timeout,TimeUnit unit)throws InterruptedException{long until=System.nanoTime()+unit.toNanos(timeout);while(exit<0){long remaining=until-System.nanoTime();if(remaining<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,remaining);}return true;}
        public synchronized int exitValue(){if(exit<0)throw new IllegalThreadStateException("Access connection is live");return exit;}
        public synchronized boolean isAlive(){return exit<0;}
        public void destroy(){try{socket.close();}catch(IOException ignored){}finished(1);}
    }
}
