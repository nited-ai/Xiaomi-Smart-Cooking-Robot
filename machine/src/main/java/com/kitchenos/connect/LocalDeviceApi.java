package com.kitchenos.connect;

import android.content.Context;
import com.kitchenos.connect.kitchen.CloudSession;
import org.json.JSONObject;
import java.util.Arrays;

/** Local phone enrollment and setup. No cooking/MCU endpoints. */
public final class LocalDeviceApi implements RecipeServer.Access {
    private final Context context;
    private final PhoneRegistry phones;
    private final String pin;
    private volatile String accountState="", accountError="";
    private volatile boolean closed;
    public LocalDeviceApi(Context context,PhoneRegistry phones,String pin){this.context=context.getApplicationContext();this.phones=phones;this.pin=pin;}
    @Override public boolean allowed(String token,String path){
        if(closed)return false;
        if(path.equals("/v1/device/enroll"))return phones.invited(token);
        return Arrays.asList("/v1/recipes","/v1/assistant/photos","/v1/device/status","/v1/device/networks",
                "/v1/device/wifi","/v1/device/account-start","/v1/device/finish").contains(path)&&phones.authorized(token);
    }
    @Override public synchronized JSONObject call(String path,String token,JSONObject body)throws Exception{
        if(!allowed(token,path))throw new IllegalStateException("Kopplung entfernt oder abgelaufen.");
        if(path.equals("/v1/device/enroll"))return phones.enroll(body).put("device",pin);
        CloudSession cloud=new CloudSession(context);
        if(path.equals("/v1/device/finish")){
            String pending=accountState;accountState="";
            if(!pending.isEmpty())cloud.cancelLogin(pending);
            String mode=cloud.account().optString("uid").isEmpty()?"local":"account";
            complete(mode);
            return new JSONObject().put("complete",true).put("mode",mode);
        }
        if(path.equals("/v1/device/status"))return new JSONObject().put("device",pin)
                .put("accountConnected",!cloud.account().optString("uid").isEmpty()).put("accountError",accountError)
                .put("setup",WifiSetupService.active==null?"idle":WifiSetupService.active.state);
        if(path.equals("/v1/device/networks")){
            WifiSetupService setup=WifiSetupService.active;
            if(setup==null||!setup.acceptsCredentials())throw new IllegalStateException();
            return new JSONObject().put("networks",setup.networks());
        }
        if(path.equals("/v1/device/wifi")){
            WifiSetupService setup=WifiSetupService.active;if(setup==null)throw new IllegalStateException();setup.provision(body);
            return new JSONObject().put("accepted",true);
        }
        if(path.equals("/v1/device/account-start")){
            if(!cloud.account().optString("uid").isEmpty())throw new IllegalStateException("Die Maschine ist bereits angemeldet.");
            if(com.kitchenos.connect.control.ControlClient.hasActiveSession())throw new IllegalStateException();
            JSONObject prepared=cloud.startDevice(body.getString("provider"));String state=prepared.getString("state");
            accountState=state;accountError="";
            new Thread(()->{
                try{
                    long deadline=android.os.SystemClock.elapsedRealtime()+600000;
                    while(!closed&&state.equals(accountState)&&android.os.SystemClock.elapsedRealtime()<deadline){
                        if(cloud.pollDevice(state)){
                            synchronized(LocalDeviceApi.this){
                                if(!closed&&state.equals(accountState)&&!cloud.account().optString("uid").isEmpty()){
                                    complete("account");accountState="";accountError="";
                                }
                            }
                            return;
                        }
                        Thread.sleep(3000);
                    }
                    if(state.equals(accountState))accountError="Die Anmeldung ist abgelaufen. Bitte erneut starten.";
                }catch(Exception error){if(state.equals(accountState)&&!closed)accountError="Anmeldung konnte nicht abgeschlossen werden. Bitte erneut starten.";}
                finally{try{cloud.cancelLogin(state);}catch(Exception ignored){}}
            },"Kalomatik-Phone-Account").start();
            return new JSONObject().put("url",prepared.getString("url"));
        }
        throw new IllegalArgumentException("Unbekannter Endpunkt");
    }
    private void complete(String mode)throws java.io.IOException{
        if(!context.getSharedPreferences("setup",0).edit().putBoolean("seen",true).putString("mode",mode)
                .putString("completion",java.util.UUID.randomUUID().toString()).commit())throw new java.io.IOException();
    }
    public void close(){
        closed=true;
        // Wait off the main thread for an in-flight account-start, then cancel its exact state.
        new Thread(()->{synchronized(LocalDeviceApi.this){String state=accountState;accountState="";
            if(!state.isEmpty())try{new CloudSession(context).cancelLogin(state);}catch(Exception ignored){}
        }},"Kalomatik-Cancel-Account").start();
    }
}
