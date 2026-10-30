package com.kitchenos.share;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import com.kitchenos.common.*;
import org.json.*;
import java.util.concurrent.*;

/** Explicit QR-driven setup. The home password stays in memory until sent over pinned TLS. */
public final class PhoneSetupActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Pairing invitation,paired;
    private Uri code;
    private String phoneName;
    private volatile Network apNetwork;
    private ConnectivityManager.NetworkCallback request;
    private boolean busy,leavingAp,waitingForHome;
    private Button findButton;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean accountScreen,accountPending;
    private long accountDeadline;
    private final Runnable accountPoll=()->pollAccount();
    private TextView status;
    private LinearLayout page;
    private interface Work { void run()throws Exception; }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            if(getIntent().getData()!=null&&"setup-result".equals(getIntent().getData().getHost())){accountReturn(getIntent().getData());return;}
            code=SetupCode.parse(getIntent().getDataString());
            invitation=new Pairing(code.getQueryParameter("host"),code.getQueryParameter("token"),code.getQueryParameter("pin"));
            if(getIntent().getBooleanExtra("reconnect",false)){
                Pairing saved=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));
                if(!saved.pin.equals(invitation.pin))throw new IllegalArgumentException();
                paired=saved;screen("Deine Maschine","Verbindung wird geprüft …");page.addView(Ui.button(this,"Erneut suchen",v->recover()));recover();return;
            }
            if(invitation.pin.equals(getSharedPreferences("onboarding",0).getString("homePin",""))){
                paired=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));
                if(paired.pin.equals(invitation.pin)){waiting();return;}
            }
            welcome();
        }catch(Exception invalid){screen("Code nicht lesbar","Öffne einen neuen Einrichtungscode auf deiner Maschine und scanne ihn erneut.");}
    }
    private void screen(String title,String text){page=OnboardingUi.page(this,title,"");status=Ui.text(this,text,17);status.setTextColor(Ui.MUTED);status.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,24));status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);}
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())action.run();});}
    private void work(Work action){
            if(busy||isDestroyed())return;busy=true;
        worker.execute(()->{try{action.run();}catch(Exception error){ui(()->{status.setText(error instanceof PhoneLink.Rejected?error.getMessage():error.getMessage()==null?"Verbindung nicht möglich. Bitte erneut versuchen.":error.getMessage());});}finally{ui(()->busy=false);}});
    }
    private void welcome(){
        phoneName=Build.MODEL.length()>60?Build.MODEL.substring(0,60):Build.MODEL;
        screen("Maschine verbinden","Bestätige gleich die WLAN-Verbindung auf deinem Handy. Anschließend wählst du dein Heim-WLAN.");
        OnboardingUi.add(page,OnboardingUi.secondary(this,"Erneut verbinden",v->{if(!busy){if("setup".equals(code.getHost()))connectAp();else enroll(null);}}));
        try{Pairing saved=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));if(saved.pin.equals(invitation.pin)&&!"setup".equals(code.getHost())){paired=saved;recover();return;}}catch(Exception ignored){}
        main.post(()->{if("setup".equals(code.getHost()))connectAp();else enroll(null);});
    }
    private void connectAp(){
        if(Build.VERSION.SDK_INT<29){status.setText("Für die automatische WLAN-Einrichtung ist Android 10 oder neuer erforderlich. Die Kopplung im gemeinsamen WLAN ist weiterhin möglich.");return;}
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(Build.VERSION.SDK_INT>=31&&Build.VERSION.SDK_INT<33?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,permission}:new String[]{permission},72);return;
        }
        releaseAp();leavingAp=false;
        try{
            String ssid=code.getQueryParameter("ssid"),password=code.getQueryParameter("password");
            if(ssid==null||ssid.isEmpty()||password==null)throw new IllegalArgumentException("Einrichtungscode unvollständig.");
            WifiNetworkSpecifier specifier=new WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(password).build();
            NetworkRequest networkRequest=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(specifier).build();
            request=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network network){ui(()->{if(request!=this)return;apNetwork=network;enroll(network);});}
                @Override public void onUnavailable(){ui(()->{if(request!=this)return;request=null;status.setText("Verbindung nicht bestätigt. Öffne gegebenenfalls einen neuen Code auf der Maschine und versuche es erneut.");});}
                @Override public void onLost(Network network){ui(()->{if(request==this&&!leavingAp){apNetwork=null;status.setText("Einrichtungsnetz verloren. Wenn du das Heim-WLAN bereits gesendet hast, wähle „Maschine wiederfinden“. Sonst öffne einen neuen Code auf der Maschine.");}});}
            };
            getSystemService(ConnectivityManager.class).requestNetwork(networkRequest,request,60000);
            status.setText("Bestätige jetzt die Verbindung zum Einrichtungsnetz im Android-Dialog.");
        }catch(Exception error){releaseAp();status.setText("Einrichtungsnetz nicht erreichbar. WLAN und unter Android 10–12 auch Standort einschalten, dann erneut versuchen.");}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==72){String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;if(checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)connectAp();else status.setText("Die WLAN-Freigabe wird für die Einrichtung benötigt. Du kannst sie in den App-Einstellungen erteilen.");}}
    private void enroll(Network network){
        status.setText("Sichere Verbindung zur Maschine wird geprüft …");
        work(()->{paired=PhoneLink.enroll(this,invitation,network==null?PhoneLink.wifi(this):network,phoneName);
            if("setup".equals(code.getHost())){
                JSONArray networks=PhoneLink.call(paired,network,"networks",new JSONObject()).getJSONArray("networks");ui(()->wifiForm(networks));
            }else{JSONObject result=PhoneLink.call(paired,PhoneLink.wifi(this),"status",new JSONObject());ui(()->account(result));}
        });
    }
    private void wifiForm(JSONArray networks){
        screen("Dein WLAN","Wähle das WLAN, das deine Maschine zu Hause verwenden soll.");
        if(networks.length()==0)status.setText("Noch kein WLAN gefunden. Du kannst erneut suchen oder den Namen eingeben.");
        for(int i=0;i<networks.length();i++){
            JSONObject network=networks.optJSONObject(i);if(network==null)continue;
            String name=network.optString("ssid"),security=network.optString("security");
            Button row=Ui.button(this,name+("OPEN".equals(security)?" · Offen":""),v->wifiPassword(name,"OPEN".equals(security),networks));
            row.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);row.setMinHeight(Ui.dp(this,56));OnboardingUi.add(page,row);
        }
        OnboardingUi.add(page,OnboardingUi.secondary(this,"WLAN nicht dabei?",v->wifiPassword("",false,networks)));
        OnboardingUi.add(page,OnboardingUi.secondary(this,"Liste aktualisieren",v->work(()->{JSONArray latest=PhoneLink.call(paired,apNetwork,"networks",new JSONObject()).getJSONArray("networks");ui(()->wifiForm(latest));})));
    }
    private void wifiPassword(String selected,boolean isOpen,JSONArray networks){
        screen(selected.isEmpty()?"WLAN hinzufügen":"Mit WLAN verbinden",selected.isEmpty()?"Gib den Namen deines Heim-WLANs ein.":selected);
        EditText ssid=Ui.input(this,"WLAN-Name",false);ssid.setText(selected);
        if(selected.isEmpty()){page.addView(Ui.text(this,"WLAN-Name",16));page.addView(ssid);}
        EditText password=Ui.input(this,"Passwort eingeben",false);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);
        TextView label=Ui.text(this,"WLAN-Passwort",16);page.addView(label);page.addView(password);
        CheckBox reveal=new CheckBox(this);reveal.setText("Passwort anzeigen");reveal.setTextColor(Ui.INK);reveal.setMinHeight(Ui.dp(this,48));reveal.setOnCheckedChangeListener((b,on)->{password.setTransformationMethod(on?null:android.text.method.PasswordTransformationMethod.getInstance());password.setSelection(password.length());});page.addView(reveal);
        CheckBox open=new CheckBox(this);open.setText("Offenes WLAN ohne Passwort");open.setTextColor(Ui.INK);open.setMinHeight(Ui.dp(this,48));
        open.setOnCheckedChangeListener((button,checked)->{password.setVisibility(checked?android.view.View.GONE:android.view.View.VISIBLE);label.setVisibility(password.getVisibility());reveal.setVisibility(password.getVisibility());});open.setChecked(isOpen);
        if(selected.isEmpty())page.addView(open);
        OnboardingUi.add(page,Ui.primary(this,"Verbinden",v->{
            if(busy)return;String name=ssid.getText().toString(),secret=open.isChecked()?"":password.getText().toString();
            if(name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32){ssid.setError("WLAN-Name mit höchstens 32 Bytes erforderlich.");return;}
            if(!open.isChecked()&&(secret.length()<8||secret.length()>63)&&!secret.matches("[a-fA-F0-9]{64}")){password.setError("WPA2-Passwort mit 8–63 Zeichen erforderlich.");return;}
            final String security=open.isChecked()?"OPEN":"WPA2";status.setText("WLAN wird übertragen …");
            work(()->{
                try{PhoneLink.call(paired,apNetwork,"wifi",new JSONObject().put("ssid",name).put("password",secret).put("security",security));}
                catch(java.net.SocketTimeoutException|java.net.SocketException lostAcknowledgement){/* The AP may have closed after accepting credentials. Confirm through pinned LAN discovery. */}
                getSharedPreferences("onboarding",0).edit().putString("homePin",paired.pin).commit();
                ui(()->{password.setText("");leavingAp=true;releaseAp();waiting();});});
        }));
        OnboardingUi.add(page,OnboardingUi.secondary(this,"Anderes WLAN wählen",v->wifiForm(networks)));
    }
    private void waiting(){
        waitingForHome=true;
        screen("Wird verbunden","Deine Maschine wechselt ins Heim-WLAN. Wir stellen die Verbindung automatisch wieder her.");
        ProgressBar progress=new ProgressBar(this);page.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        OnboardingUi.add(page,OnboardingUi.secondary(this,"WLAN am Handy öffnen",v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        findButton=Ui.primary(this,"Erneut suchen",v->recover());page.addView(findButton);
        main.postDelayed(()->{if(!isDestroyed()&&!isFinishing())recover();},4000);
    }
    private void recover(){
        if(paired==null||busy)return;
        status.setText("Maschine im Heim-WLAN wird automatisch gesucht … Das kann bis zu 90 Sekunden dauern.");
        if(findButton!=null)findButton.setEnabled(false);
        work(()->{try{paired=PhoneLink.resolve(this,paired,90000);JSONObject result=PhoneLink.call(paired,PhoneLink.wifi(this),"status",new JSONObject());ui(()->account(result));}
            finally{ui(()->{if(findButton!=null)findButton.setEnabled(true);});}});
    }
    @Override protected void onResume(){super.onResume();if(waitingForHome&&!busy)main.postDelayed(()->{if(waitingForHome&&!isDestroyed()&&!isFinishing())recover();},500);if(accountScreen)scheduleAccount(250);}
    private void account(JSONObject result){
        waitingForHome=false;findButton=null;accountScreen=true;
        getSharedPreferences("onboarding",0).edit().putString("homePin",paired==null?"":paired.pin).apply();
        if(result.optBoolean("accountConnected")){screen("Maschine verbunden","Dein Maschinenkonto ist angemeldet. Die Einrichtung wird abgeschlossen …");main.postDelayed(this::complete,150);return;}
        screen("Kalomatik anmelden","Die vorhandene Kalomatik-Anmeldung öffnet sich. Nach der Anmeldung ist deine Maschine automatisch bereit.");
        OnboardingUi.add(page,Ui.primary(this,"Anmeldung öffnen",v->openAccount()));
        OnboardingUi.add(page,OnboardingUi.secondary(this,"Ohne Konto weiter",v->complete()));
        scheduleAccount(3000);
        main.postDelayed(this::openAccount,250);
    }
    private void openAccount(){
        if(busy){main.postDelayed(this::openAccount,250);return;}
        if(!accountScreen||isDestroyed()||isFinishing())return;
        work(()->{
            // The existing device handshake is provider-independent; the original
            // Kalomatik screen chooses the provider, never this native Activity.
            JSONObject answer=PhoneLink.call(paired,PhoneLink.wifi(this),"account-start",new JSONObject().put("provider","email"));
            Uri old=Uri.parse(answer.getString("url"));
            if(!"https".equals(old.getScheme())||!"nutrisnap-17e83-kitchen.web.app".equals(old.getHost())||!"/auth/start".equals(old.getPath()))throw new java.io.IOException("Anmeldeziel ungültig.");
            String device=old.getQueryParameter("device_id"),challenge=old.getQueryParameter("code_challenge");
            if(device==null||challenge==null||!device.matches("[A-Za-z0-9_-]{43}")||!challenge.matches("[A-Za-z0-9_-]{43}"))throw new java.io.IOException("Anmeldung unvollständig.");
            byte[] random=new byte[32];new java.security.SecureRandom().nextBytes(random);
            String nonce=android.util.Base64.encodeToString(random,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
            long expires=System.currentTimeMillis()+600000;
            if(!getSharedPreferences("onboarding",0).edit().putString("accountNonce",nonce).putString("accountPin",paired.pin).putLong("accountExpires",expires).commit())throw new java.io.IOException("Anmeldung konnte nicht gespeichert werden.");
            JSONObject context=new JSONObject().put("deviceId",device).put("challenge",challenge).put("nonce",nonce).put("expiresAt",expires);
            Uri url=Uri.parse("https://app.kalomatik.ai/machine-account.html").buildUpon().fragment(context.toString()).build();
            ui(()->{accountPending=true;accountDeadline=SystemClock.elapsedRealtime()+600000;scheduleAccount(1500);startActivity(new Intent(Intent.ACTION_VIEW,url));});
        });
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(intent.getData()!=null&&"setup-result".equals(intent.getData().getHost()))accountReturn(intent.getData());else{setIntent(intent);recreate();}}
    private void accountReturn(Uri url){
        try{
            SharedPreferences prefs=getSharedPreferences("onboarding",0);
            String expected=prefs.getString("accountNonce",""),received=url.getQueryParameter("nonce"),mode=url.getQueryParameter("mode");
            long now=System.currentTimeMillis(),expires=prefs.getLong("accountExpires",0);
            if(!"kitchenos".equals(url.getScheme())||!"setup-result".equals(url.getHost())||url.getUserInfo()!=null||url.getPort()!=-1||!(url.getPath()==null||url.getPath().isEmpty())||expected.isEmpty()||received==null||!java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),received.getBytes(java.nio.charset.StandardCharsets.US_ASCII))||expires<=now||expires>now+600000||!("local".equals(mode)||"account".equals(mode)))throw new IllegalArgumentException();
            Pairing saved=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));
            if(!saved.pin.equals(prefs.getString("accountPin","")))throw new IllegalArgumentException();
            paired=saved;accountScreen=true;waitingForHome=false;
            screen("Maschine verbunden","Die Einrichtung wird abgeschlossen …");
            if("local".equals(mode))main.post(this::complete);
            else{accountPending=true;accountDeadline=SystemClock.elapsedRealtime()+Math.max(0,expires-now);scheduleAccount(100);}
        }catch(Exception invalid){if(status==null)screen("Anmeldung erneut öffnen","Diese Rückkehr gehört nicht mehr zur aktuellen Einrichtung. Öffne die Anmeldung in der App erneut.");}
    }
    private void scheduleAccount(long delay){main.removeCallbacks(accountPoll);main.postDelayed(accountPoll,delay);}
    private void pollAccount(){
        if(!accountScreen||isDestroyed()||isFinishing())return;
        if(busy){scheduleAccount(1000);return;}
        work(()->{try{
            JSONObject latest=PhoneLink.call(paired,PhoneLink.wifi(this),"status",new JSONObject());
            ui(()->{if(latest.optBoolean("accountConnected")){main.postDelayed(this::complete,150);return;}
                if(accountPending&&!latest.optString("accountError").isEmpty()){accountPending=false;status.setText(latest.optString("accountError"));}
                else if(accountPending&&SystemClock.elapsedRealtime()>accountDeadline){accountPending=false;status.setText("Die Anmeldung ist abgelaufen. Öffne die Anmeldung erneut.");}
                scheduleAccount(3000);
            });
        }catch(Exception offline){ui(()->{if(accountPending)status.setText("Verbindung zur Maschine wird wiederhergestellt. Deine Kopplung bleibt erhalten.");scheduleAccount(5000);});}});
    }
    private void complete(){
        if(busy){main.postDelayed(this::complete,250);return;}
        work(()->{PhoneLink.call(paired,PhoneLink.wifi(this),"finish",new JSONObject());ui(()->{
            accountScreen=false;accountPending=false;getSharedPreferences("onboarding",0).edit().remove("homePin").remove("accountNonce").remove("accountPin").remove("accountExpires").apply();main.removeCallbacks(accountPoll);
            Toast.makeText(this,"Maschine verbunden",Toast.LENGTH_SHORT).show();
            Intent home=getPackageManager().getLaunchIntentForPackage(getPackageName());if(home!=null)startActivity(home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
        });});
    }
    private void releaseAp(){ConnectivityManager.NetworkCallback old=request;request=null;apNetwork=null;if(old!=null)try{getSystemService(ConnectivityManager.class).unregisterNetworkCallback(old);}catch(Exception ignored){}}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);releaseAp();worker.shutdownNow();super.onDestroy();}
}
