package com.kitchenos.connect;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import com.kitchenos.common.Ui;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Appliance Wi-Fi. A stalled system-service call must never stall navigation. */
public final class WifiPanel {
    // Binder calls cannot reliably be interrupted. Keep one in flight across all
    // panels; timing out never queues more calls or creates more blocked threads.
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"KitchenOS-Wifi");t.setDaemon(true);return t;});
    private static final AtomicBoolean SERVICE_BUSY=new AtomicBoolean();
    private static final long TIMEOUT_MS=2500;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable poll;
    private final Activity activity;private final Backend backend;
    private final LinearLayout networks;private final TextView state;private final Switch toggle;
    private boolean updating,locationPrompted,disposed,pending;private long request;
    private Snapshot latest;private String networkKey="";private Runnable viewsChanged;
    private AlertDialog credentialsDialog;
    private final List<ScanResult> lastResults=new ArrayList<>();
    public void onViewsChanged(Runnable callback){viewsChanged=callback;}
    public WifiPanel(Activity activity,LinearLayout page){this(activity,page,new AndroidBackend(activity.getApplicationContext()));}
    // Emulator tests can supply a deliberately blocked system service.
    WifiPanel(Activity activity,LinearLayout page,Backend backend){
        this.activity=activity;this.backend=backend;
        poll=new Runnable(){public void run(){if(disposed)return;if(page.isAttachedToWindow()&&page.getWindowVisibility()==View.VISIBLE)refresh();main.postDelayed(this,2000);}};
        page.addView(Ui.title(activity,"WLAN"));
        LinearLayout card=Ui.card(activity,page);toggle=new Switch(activity);toggle.setText("WLAN einschalten");toggle.setTextColor(Ui.INK);toggle.setTextSize(23);card.addView(toggle);
        state=Ui.text(activity,"WLAN-Verbindung wird geprüft …",18);state.setTag("wifi.status");card.addView(state);
        toggle.setOnCheckedChangeListener((button,on)->{
            if(updating)return;
            if(!task(()->{backend.enabled(on);return on;},enabled->{state.setText(enabled?"WLAN wird eingeschaltet …":"WLAN wird ausgeschaltet …");refresh();}))restoreToggle();
        });
        TextView scan=Ui.text(activity,"Netzwerke aktualisieren  ↻",23);scan.setTextColor(Ui.ACCENT);scan.setPadding(12,24,12,24);scan.setOnClickListener(v->scan());scan.setTag("wifi.scan");page.addView(scan);
        networks=Ui.column(activity);page.addView(networks);
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){main.removeCallbacks(poll);main.postDelayed(poll,2000);}public void onViewDetachedFromWindow(View view){dispose();}});
        refresh();if(page.isAttachedToWindow())main.postDelayed(poll,2000);
    }
    private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private void restoreToggle(){updating=true;toggle.setChecked(latest!=null&&latest.enabled);updating=false;}
    private <T> boolean task(Callable<T> work,Consumer<T> success){
        if(disposed||pending)return false;
        if(!SERVICE_BUSY.compareAndSet(false,true)){state.setText("Der Android-WLAN-Dienst antwortet noch nicht. Die übrige Bedienung ist weiter möglich.");return false;}
        pending=true;long token=++request;boolean[] expired={false};
        Runnable timeout=()->{if(disposed||request!=token)return;expired[0]=true;state.setText("Der Android-WLAN-Dienst antwortet nicht. Bitte die Maschine neu starten.");restoreToggle();};
        main.postDelayed(timeout,TIMEOUT_MS);
        WORK.execute(()->{
            T value=null;Exception failure=null;
            try{value=work.call();}catch(Exception error){failure=error;}finally{SERVICE_BUSY.set(false);}
            final T answer=value;final Exception error=failure;
            main.post(()->{
                main.removeCallbacks(timeout);if(disposed||request!=token)return;pending=false;
                if(expired[0])return; // Never apply a stale reply or reopen an old dialog.
                if(error!=null){restoreToggle();String text=error instanceof IllegalArgumentException?error.getMessage():"WLAN konnte nicht abgefragt oder geändert werden. Bitte erneut versuchen.";state.setText(text);return;}
                success.accept(answer);
            });
        });return true;
    }
    public void dispose(){disposed=true;request++;main.removeCallbacksAndMessages(null);if(credentialsDialog!=null)credentialsDialog.dismiss();viewsChanged=null;}
    public void scan(){
        if(disposed)return;
        if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},41);return;}
        task(()->backend.snapshot(true),snapshot->{
            apply(snapshot);if(!snapshot.enabled){message("Bitte WLAN zuerst einschalten.");return;}
            if(!snapshot.location&&!locationPrompted){locationPrompted=true;new AlertDialog.Builder(activity).setTitle("WLAN-Suche aktivieren").setMessage("Aktiviere den Standortdienst, damit Android die verfügbaren WLAN-Netze anzeigen kann.").setPositiveButton("Aktivieren",(d,w)->activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Zurück",null).show();}
        });
    }
    public void refresh(){task(()->backend.snapshot(false),this::apply);}
    private void apply(Snapshot snapshot){
        latest=snapshot;restoreToggle();if(snapshot.location)locationPrompted=false;
        String text=snapshot.connected?(snapshot.ssid.isEmpty()?"WLAN verbunden":"Verbunden mit "+snapshot.ssid)+(snapshot.internet?" · Internet erreichbar":" · Internet wird geprüft"):"WLAN "+(snapshot.enabled?"eingeschaltet · noch nicht verbunden":"ausgeschaltet");
        if(!snapshot.ip.isEmpty())text+="\nIP-Adresse: "+snapshot.ip;
        if(!snapshot.permission)text+=" · Netzwerksuche nicht freigegeben";else if(!snapshot.location)text+=" · WLAN-Suche deaktiviert";
        state.setText(text);renderNetworks(snapshot);
    }
    private void renderNetworks(Snapshot snapshot){
        if(!snapshot.enabled){networks.removeAllViews();networkKey="";if(viewsChanged!=null)viewsChanged.run();return;}
        if(!snapshot.results.isEmpty()){lastResults.clear();lastResults.addAll(snapshot.results);}
        List<ScanResult> results=new ArrayList<>(lastResults);results.sort((a,b)->Integer.compare(b.level,a.level));Map<String,ScanResult> found=new LinkedHashMap<>();
        for(ScanResult result:results)if(result.SSID!=null&&!result.SSID.isEmpty()&&!found.containsKey(result.SSID))found.put(result.SSID,result);
        String key=found.keySet().toString()+snapshot.ssid;if(key.equals(networkKey)&&networks.getChildCount()>0)return;networkKey=key;networks.removeAllViews();
        for(ScanResult item:found.values()){
            LinearLayout row=new LinearLayout(activity);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(activity,16),Ui.dp(activity,18),Ui.dp(activity,16),Ui.dp(activity,18));
            ImageView icon=new ImageView(activity);icon.setImageDrawable(new com.kitchenos.connect.ui.InterfaceIcon("wifi",Ui.INK));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(activity,34),Ui.dp(activity,34)));
            LinearLayout copy=Ui.column(activity);TextView title=Ui.text(activity,item.SSID,25);title.setTypeface(null,android.graphics.Typeface.BOLD);copy.addView(title);boolean active=item.SSID.equals(snapshot.ssid);
            TextView description=Ui.text(activity,active?"Verbunden":securityLabel(WifiNetworks.security(item.capabilities))+" · "+(item.frequency>=4900?"5 GHz":"2,4 GHz"),19);description.setTextColor(active?Ui.ACCENT:Ui.MUTED);copy.addView(description);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.leftMargin=Ui.dp(activity,20);row.addView(copy,size);row.setContentDescription(item.SSID+(active?", verbunden":""));row.setOnClickListener(v->credentials(item));row.setTag("wifi.network."+item.SSID);networks.addView(row,new LinearLayout.LayoutParams(-1,-2));
            View divider=new View(activity);divider.setBackgroundColor(Ui.LINE);networks.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(activity,1)));
        }
        if(found.isEmpty())networks.addView(Ui.text(activity,"Noch keine Netzwerke gefunden. Die Suche wird aktualisiert.",17));if(viewsChanged!=null)viewsChanged.run();
    }
    private void credentials(ScanResult result){
        if(disposed||pending)return;
        LinearLayout box=Ui.column(activity);box.setPadding(28,12,28,12);
        EditText name=Ui.input(activity,"Netzwerkname (SSID)",false);if(result!=null){name.setText(result.SSID);name.setEnabled(false);}box.addView(name);
        boolean noPassword=result!=null&&(WifiNetworks.security(result.capabilities)==WifiNetworks.Security.OPEN||WifiNetworks.security(result.capabilities)==WifiNetworks.Security.OWE);
        EditText password=Ui.input(activity,"WLAN-Passwort",false);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);com.kitchenos.connect.ui.EditorKeyboardDialog.bind(activity,password);box.addView(password);
        int existingId=latest==null||result==null?-1:latest.savedIds.getOrDefault(WifiNetworks.quote(result.SSID),-1);
        CheckBox reuse=new CheckBox(activity);reuse.setText("Gespeicherte Verbindung verwenden");reuse.setTextColor(Ui.INK);reuse.setChecked(existingId>=0);reuse.setVisibility(existingId>=0?View.VISIBLE:View.GONE);box.addView(reuse);password.setVisibility(existingId>=0||noPassword?View.GONE:View.VISIBLE);reuse.setOnCheckedChangeListener((b,on)->password.setVisibility(on||noPassword?View.GONE:View.VISIBLE));
        CheckBox open=new CheckBox(activity);open.setText("Offenes Netzwerk ohne Passwort");open.setTextColor(Ui.INK);open.setChecked(result!=null&&WifiNetworks.security(result.capabilities)==WifiNetworks.Security.OPEN);open.setVisibility(result==null?View.VISIBLE:View.GONE);open.setOnCheckedChangeListener((b,on)->password.setVisibility(on||reuse.isChecked()||noPassword?View.GONE:View.VISIBLE));box.addView(open);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Mit WLAN verbinden").setView(box).setPositiveButton("Verbinden",null).setNegativeButton("Abbrechen",null).create();credentialsDialog=dialog;
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String ssid=name.getText().toString(),secret=password.getText().toString();boolean saved=reuse.isChecked();WifiNetworks.Security security=open.isChecked()?WifiNetworks.Security.OPEN:result==null?WifiNetworks.Security.WPA2:WifiNetworks.security(result.capabilities);
            if(!saved||existingId<0)try{WifiNetworks.configuration(ssid,secret,security);}catch(IllegalArgumentException error){message(error.getMessage());return;}
            if(task(()->backend.connect(ssid,secret,security,saved?existingId:-1),text->{password.setText("");dialog.dismiss();state.setText(text);}))password.setText("");
        }));dialog.show();com.kitchenos.connect.ui.RotaryDialogs.bind(activity,dialog);
    }
    private static String securityLabel(WifiNetworks.Security security){switch(security){case OPEN:return "Offen";case OWE:return "Ohne Passwort · verschlüsselt";case ENTERPRISE:return "Unternehmensnetz";default:return security.name();}}

    interface Backend {Snapshot snapshot(boolean scan);void enabled(boolean enabled);String connect(String ssid,String password,WifiNetworks.Security security,int savedId);}
    static final class Snapshot {
        boolean enabled,connected,internet,permission,location;String ssid="",ip="";List<ScanResult> results=Collections.emptyList();Map<String,Integer> savedIds=new HashMap<>();
    }
    private static final class AndroidBackend implements Backend {
        private final Context app;private WifiManager wifi;private ConnectivityManager connectivity;private long lastScan;
        AndroidBackend(Context app){this.app=app;}
        private static void offMain(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Wi-Fi system calls require a worker thread");}
        private void services(){offMain();if(wifi==null)wifi=(WifiManager)app.getSystemService(Context.WIFI_SERVICE);if(connectivity==null)connectivity=(ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);}
        public Snapshot snapshot(boolean scan){
            services();Snapshot value=new Snapshot();value.enabled=wifi.isWifiEnabled();value.permission=app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            value.location=Settings.Secure.getInt(app.getContentResolver(),Settings.Secure.LOCATION_MODE,0)!=Settings.Secure.LOCATION_MODE_OFF;
            NetworkInfo info=connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);value.connected=info!=null&&info.isConnected();WifiInfo connection=wifi.getConnectionInfo();
            if(connection!=null){String ssid=connection.getSSID();if(ssid!=null&&!ssid.equalsIgnoreCase("<unknown ssid>"))value.ssid=ssid.replace("\"","");int ip=connection.getIpAddress();if(value.connected&&ip!=0)value.ip=String.format(Locale.ROOT,"%d.%d.%d.%d",ip&255,(ip>>>8)&255,(ip>>>16)&255,(ip>>>24)&255);}
            Network network=connectivity.getActiveNetwork();NetworkCapabilities capabilities=network==null?null:connectivity.getNetworkCapabilities(network);value.internet=capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if(value.enabled&&value.permission&&value.location){
                if(scan||SystemClock.elapsedRealtime()-lastScan>=30000){lastScan=SystemClock.elapsedRealtime();wifi.startScan();}
                List<ScanResult> results=wifi.getScanResults();if(results!=null)value.results=new ArrayList<>(results);
                List<WifiConfiguration> saved=wifi.getConfiguredNetworks();if(saved!=null)for(WifiConfiguration row:saved)value.savedIds.put(row.SSID,row.networkId);
            }return value;
        }
        public void enabled(boolean enabled){services();if(!wifi.setWifiEnabled(enabled))throw new IllegalArgumentException("Android hat die Änderung abgelehnt. Bitte Systemeinstellungen öffnen.");}
        public String connect(String ssid,String password,WifiNetworks.Security security,int id){
            services();if(id<0){
                WifiConfiguration config=WifiNetworks.configuration(ssid,password,security);
                if(security==WifiNetworks.Security.WPA3&&(Build.VERSION.SDK_INT<29||!wifi.isWpa3SaeSupported()))throw new IllegalArgumentException("Dieses Gerät unterstützt dieses reine WPA3-Netz nicht. Aktiviere am Router WPA2/WPA3 oder WPA2.");
                if(security==WifiNetworks.Security.OWE&&(Build.VERSION.SDK_INT<29||!wifi.isEnhancedOpenSupported()))throw new IllegalArgumentException("Dieses Gerät unterstützt das verschlüsselte offene Netz nicht.");
                if(Build.VERSION.SDK_INT==29&&(security==WifiNetworks.Security.WPA3||security==WifiNetworks.Security.OWE)){
                    WifiNetworkSuggestion.Builder suggestion=new WifiNetworkSuggestion.Builder().setSsid(ssid);if(security==WifiNetworks.Security.WPA3)suggestion.setWpa3Passphrase(password);else suggestion.setIsEnhancedOpen(true);
                    int answer=wifi.addNetworkSuggestions(Collections.singletonList(suggestion.build()));if(answer!=WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS&&answer!=WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE)throw new IllegalArgumentException("Android konnte das Netzwerk nicht vormerken. Bitte erneut versuchen.");
                    return "Netzwerk gespeichert. Bestätige gegebenenfalls die WLAN-Freigabe von Android; die Verbindung wird automatisch hergestellt.";
                }
                if(Build.VERSION.SDK_INT>=30&&security==WifiNetworks.Security.WPA3)config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);if(Build.VERSION.SDK_INT>=30&&security==WifiNetworks.Security.OWE)config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
                id=wifi.addNetwork(config);if(id<0)throw new IllegalArgumentException("Android konnte die Verbindung nicht speichern. Ist das Netz bereits gespeichert, wähle Gespeicherte Verbindung verwenden.");
            }
            if(!wifi.enableNetwork(id,true))throw new IllegalArgumentException("Android hat die Auswahl des gespeicherten Netzwerks abgelehnt. Bitte WLAN erneut versuchen.");wifi.reconnect();return "Verbindung zu "+ssid+" wird hergestellt …";
        }
    }
}
