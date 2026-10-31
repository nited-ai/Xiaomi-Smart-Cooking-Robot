package com.kitchenos.connect.control;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Owner ADB provisioning, protected by the platform DUMP permission in manifest. */
public final class AccessBootstrapReceiver extends BroadcastReceiver {
    public static final String ACTION="com.kitchenos.connect.CONFIGURE_FACTORY_ACCESS";
    @Override public void onReceive(Context context,Intent intent){
        if(!ACTION.equals(intent.getAction())){setResultCode(400);setResultData("Invalid action");return;}
        final PendingResult result=goAsync();final Context app=context.getApplicationContext();
        final boolean clear=intent.getBooleanExtra("clear",false),status=intent.getBooleanExtra("status",false);
        final String expected=intent.getStringExtra("socket"),encoded=intent.getStringExtra("config");
        new Thread(()->{
            int code=400;String message="Invalid access request";
            try{
                if(clear&&status)throw new IllegalArgumentException();
                String current=ControlAccess.storedSocket(app);
                if(expected!=null&&current!=null&&!expected.equals(current)){code=409;message="A different access session is configured";}
                else if(status){
                    FactoryAccessSession session=ControlAccess.readSession(app);
                    if(session==null){code=404;message="No factory access session";}
                    else{
                        ControlAccess.verifyIdentity(app,session);
                        new Handler(Looper.getMainLooper()).post(()->{try{if(!session.socket.equals(ControlAccess.storedSocket(app)))finish(result,409,"Access session changed during verification");else finish(result,200,"App identity verified");}catch(Exception error){finish(result,503,"Factory access identity unavailable");}});return;
                    }
                }else{
                    FactoryAccessSession prepared=null;
                    if(!clear){
                        if(encoded==null||encoded.length()>8192)throw new IllegalArgumentException();
                        byte[] bytes=Base64.decode(encoded,Base64.DEFAULT);if(bytes.length>4096)throw new IllegalArgumentException();
                        prepared=ControlAccess.parse(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
                        ControlAccess.verifyIdentity(app,prepared);
                    }
                    final FactoryAccessSession session=prepared;
                    new Handler(Looper.getMainLooper()).post(()->{
                        try{
                            String latest=ControlAccess.storedSocket(app);
                            if(expected!=null&&latest!=null&&!expected.equals(latest)){finish(result,409,"A different access session is configured");return;}
                            if(!ControlClient.canReconfigureAccess()){finish(result,409,"Leave the control interface and wait for its connection to close");return;}
                            if(clear)ControlAccess.clear(app);else ControlAccess.store(app,session);
                            ControlClient.invalidateAccess();finish(result,200,clear?"Factory access configuration removed":"Factory access configured and identity verified");
                        }catch(Exception error){finish(result,400,"Access configuration was not changed");}
                    });return;
                }
            }catch(IllegalArgumentException|org.json.JSONException error){code=400;message="Invalid access configuration";}
            catch(Exception error){code=503;message="Factory access identity unavailable";}
            finish(result,code,message);
        },"Owner-Access-Provisioning").start();
    }
    private static void finish(PendingResult result,int code,String message){result.setResultCode(code);result.setResultData(message);result.finish();}
}
