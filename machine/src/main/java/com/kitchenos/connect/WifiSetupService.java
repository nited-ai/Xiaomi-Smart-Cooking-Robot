package com.kitchenos.connect;

import android.app.*;
import android.content.*;
import android.net.wifi.*;
import android.os.*;
import com.kitchenos.connect.control.ControlClient;
import org.json.*;
import java.net.*;
import java.util.*;

/** Explicit, bounded setup session. Credentials are handed only to Android's Wi-Fi store. */
public final class WifiSetupService extends Service {
    public static volatile WifiSetupService active;
    public volatile String state="starting", message="Einrichtungsnetz wird vorbereitet …", ssid="", password="", address="";
    public volatile long generation;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WifiManager wifi;
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private volatile boolean destroyed, connecting;
    private boolean originalWifi;
    private boolean requested;
    private long epoch;
    private JSONArray networks=new JSONArray();
    public static void start(Context context) {
        if(ControlClient.hasActiveSession())throw new IllegalStateException("Beende zuerst den Kochvorgang.");
        context.startForegroundService(new Intent(context,WifiSetupService.class));
    }
    public static void stop(Context context){context.stopService(new Intent(context,WifiSetupService.class));}
    @Override public void onCreate(){
        super.onCreate();active=this;wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);originalWifi=wifi.isWifiEnabled();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("setup","Einrichtung",NotificationManager.IMPORTANCE_LOW));
        startForeground(8766,new Notification.Builder(this,"setup").setContentTitle("Kalomatik einrichten")
                .setContentText("Temporäres Einrichtungsnetz · maximal 10 Minuten")
                .setSmallIcon(android.R.drawable.stat_sys_upload).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){if(!requested&&!connecting){requested=true;begin();}return START_NOT_STICKY;}
    private void set(String value,String text){state=value;message=text;generation++;}
    private void begin(){
        if(destroyed||ControlClient.hasActiveSession()){set("failed","Beende zuerst den Kochvorgang.");return;}
        final long run=++epoch;set("starting","Einrichtungsnetz wird vorbereitet …");
        RecipeReceiverService.requestStart(this);
        try{
            networks=new JSONArray();Set<String> names=new HashSet<>();
            List<ScanResult> rows=wifi.getScanResults();
            if(rows!=null)for(ScanResult row:rows){WifiNetworks.Security security=WifiNetworks.security(row.capabilities);
                if(!row.SSID.isEmpty()&&names.add(row.SSID)&&(security==WifiNetworks.Security.OPEN||security==WifiNetworks.Security.WPA2))
                    networks.put(new JSONObject().put("ssid",row.SSID).put("security",security.name()));}
            wifi.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback(){
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation value){
                    if(destroyed||epoch!=run){value.close();return;}
                    reservation=value;WifiConfiguration config=value.getWifiConfiguration();
                    if(config==null||config.SSID==null||config.preSharedKey==null){fail("Einrichtungsnetz konnte nicht vorbereitet werden.");return;}
                    ssid=config.SSID;password=config.preSharedKey;
                    findAddress(run,0);
                }
                @Override public void onFailed(int reason){if(!destroyed&&run==epoch)fail("Einrichtungsnetz konnte nicht gestartet werden. Bitte erneut versuchen.");}
                @Override public void onStopped(){if(!destroyed&&run==epoch&&!connecting)fail("Einrichtungsnetz wurde beendet. Du kannst es erneut öffnen.");}
            },main);
            main.postDelayed(()->{if(!destroyed&&run==epoch&&!connecting)fail("Der Einrichtungscode ist abgelaufen. Öffne einen neuen Code.");},600000);
        }catch(Exception error){fail("Einrichtungsnetz nicht verfügbar. Prüfe die Standortfreigabe und versuche es erneut.");}
    }
    private void findAddress(long run,int attempt){
        if(destroyed||run!=epoch||reservation==null)return;
        try{NetworkInterface iface=NetworkInterface.getByName("wlan0");
            if(iface!=null)for(InetAddress ip:Collections.list(iface.getInetAddresses()))
                if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()&&wifi.getConnectionInfo().getIpAddress()==0){
                    address=ip.getHostAddress();set("ready","Scanne den Code mit deinem Handy.");return;}
        }catch(Exception ignored){}
        if(attempt>=20){fail("Das Einrichtungsnetz hat noch keine Adresse. Bitte erneut versuchen.");return;}
        main.postDelayed(()->findAddress(run,attempt+1),500);
    }
    public synchronized JSONArray networks()throws Exception{return new JSONArray(networks.toString());}
    public boolean acceptsCredentials(){return !destroyed&&state.equals("ready")&&reservation!=null&&!ControlClient.hasActiveSession();}
    public synchronized void provision(JSONObject body)throws Exception{
        if(!acceptsCredentials())throw new IllegalStateException("Öffne die Einrichtung auf der Maschine.");
        String security=body.optString("security","WPA2");
        if(!security.equals("WPA2")&&!security.equals("OPEN"))throw new IllegalArgumentException("Bitte ein persönliches WPA2- oder offenes WLAN verwenden.");
        WifiConfiguration config=WifiNetworks.configuration(body.getString("ssid"),body.optString("password"),WifiNetworks.Security.valueOf(security));
        connecting=true;set("connecting","Die Maschine verbindet sich mit deinem WLAN …");
        // Let the authenticated HTTPS acknowledgement leave the AP before changing networks.
        main.postDelayed(()->connect(config),1500);
    }
    private void connect(WifiConfiguration config){
        if(destroyed)return;
        if(ControlClient.hasActiveSession()){connecting=false;fail("Beende zuerst den Kochvorgang.");return;}
        ++epoch;release();wifi.setWifiEnabled(true);long run=epoch;
        main.postDelayed(()->saveAndConnect(config,run,0),1500);
    }
    private void saveAndConnect(WifiConfiguration config,long run,int attempt){
        if(destroyed||run!=epoch)return;
        if(!wifi.isWifiEnabled()){if(attempt<15){main.postDelayed(()->saveAndConnect(config,run,attempt+1),500);return;}connecting=false;fail("WLAN konnte nicht eingeschaltet werden.");return;}
        try{
            List<WifiConfiguration> saved=wifi.getConfiguredNetworks();
            if(saved!=null)for(WifiConfiguration row:saved)if(config.SSID.equals(row.SSID)&&row.allowedKeyManagement.equals(config.allowedKeyManagement)){config.networkId=row.networkId;break;}
            int id=config.networkId>=0?wifi.updateNetwork(config):wifi.addNetwork(config);
            if(id<0||!wifi.enableNetwork(id,true))throw new IllegalStateException();
            wifi.reconnect();awaitNetwork(run,id,0);
        }catch(Exception error){connecting=false;fail("WLAN konnte nicht gespeichert werden. Öffne einen neuen Einrichtungscode und versuche es erneut.");}
    }
    private void awaitNetwork(long run,int id,int attempt){
        if(destroyed||run!=epoch)return;
        WifiInfo info=wifi.getConnectionInfo();
        if(info.getNetworkId()==id&&info.getIpAddress()!=0){connecting=false;set("connected","Mit deinem WLAN verbunden. Wähle jetzt Kontoanmeldung oder lokalen Betrieb.");return;}
        if(attempt>=60){connecting=false;fail("Keine Verbindung. Prüfe WLAN-Name und Passwort und öffne einen neuen Einrichtungscode.");return;}
        main.postDelayed(()->awaitNetwork(run,id,attempt+1),1000);
    }
    private void fail(String text){++epoch;requested=false;connecting=false;release();if(originalWifi)wifi.setWifiEnabled(true);set("failed",text);}
    private void release(){WifiManager.LocalOnlyHotspotReservation old=reservation;reservation=null;ssid=password=address="";if(old!=null)old.close();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){destroyed=true;++epoch;main.removeCallbacksAndMessages(null);release();if(originalWifi&&!wifi.isWifiEnabled())wifi.setWifiEnabled(true);if(active==this)active=null;stopForeground(true);super.onDestroy();}
}
