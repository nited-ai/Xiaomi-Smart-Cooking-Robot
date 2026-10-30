package com.kitchenos.connect.ui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.*;
import com.kitchenos.connect.kitchen.CloudSession;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

/** First-run setup is separate from cooking. Local use never requires a cloud account. */
public final class SetupActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private String shown="";
    private boolean finished;
    private String completion;
    private boolean showAnotherPhone;
    private final Runnable refresh=new Runnable(){public void run(){if(isDestroyed()||finished)return;if(!completion.equals(getSharedPreferences("setup",0).getString("completion",""))){finishSetup(new CloudSession(SetupActivity.this).account().optString("uid").isEmpty()?"local":"account");return;}String key=key();if(!key.equals(shown)){shown=key;render();}main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);completion=getSharedPreferences("setup",0).getString("completion","");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);RecipeReceiverService.requestStart(this);main.post(refresh);if(saved==null&&(getIntent().getBooleanExtra("startAccessPoint",false)||address().isEmpty())&&WifiSetupService.active==null)main.post(this::startAp);}
    private String key(){WifiSetupService setup=WifiSetupService.active;RecipeReceiverService receiver=RecipeReceiverService.active;
        return (setup==null?"idle":setup.state+setup.generation)+"/"+address()+"/"+(receiver==null?"off":receiver.identity==null?"starting":receiver.identity.pin)
                +"/"+(receiver==null?"":receiver.error)+"/"+(receiver==null||receiver.phones==null?"":receiver.phones.invitation().hashCode())+"/"+phoneCount()+"/"+showAnotherPhone+"/"+new CloudSession(this).account().optString("uid");}
    private int phoneCount(){try{return new PhoneRegistry(this).list().length();}catch(Exception unavailable){return 0;}}
    private String address(){try{int ip=((WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo().getIpAddress();return ip==0?"":String.format(java.util.Locale.ROOT,"%d.%d.%d.%d",ip&255,ip>>8&255,ip>>16&255,ip>>24&255);}catch(Exception e){return "";}}
    private void render(){
        WifiSetupService setup=WifiSetupService.active;RecipeReceiverService receiver=RecipeReceiverService.active;
        boolean continuing=phoneCount()>0&&!showAnotherPhone&&(setup==null||setup.state.equals("connected"));
        LinearLayout body=OnboardingUi.page(this,continuing?"Am Handy weiter":"Deine Küche ist gleich bereit",continuing?"Dein Handy ist gekoppelt. Wähle dort dein Konto oder starte ohne Konto.":"Scanne den Code mit deiner Handykamera. Den Rest erledigst du am Handy.");
        String host=address();
        if(receiver!=null&&receiver.error!=null&&!receiver.error.isEmpty())body.addView(Ui.text(this,"Handy-Verbindung konnte nicht gestartet werden: "+receiver.error,20));
        if(setup!=null&&setup.state.equals("connected")){WifiSetupService.stop(this);setup=null;}
        if(setup!=null){
            body.addView(Ui.text(this,setup.message,23));
            if(setup.state.equals("ready")&&receiver!=null&&receiver.identity!=null&&receiver.phones!=null){
                Uri uri=Uri.parse("kitchenos://setup").buildUpon().appendQueryParameter("host",setup.address)
                        .appendQueryParameter("ssid",setup.ssid).appendQueryParameter("password",setup.password)
                        .appendQueryParameter("token",receiver.phones.invitation()).appendQueryParameter("pin",receiver.identity.pin).build();
                qr(body,SetupCode.link(uri.toString()));body.addView(Ui.text(this,"WLAN auswählen · Passwort eingeben · Loskochen",22));
            }
            if(setup.state.equals("failed"))body.addView(Ui.button(this,"Neuen Einrichtungscode öffnen",v->startAp()));
            if(!setup.state.equals("connecting"))body.addView(Ui.button(this,"Einrichtungsnetz beenden",v->{WifiSetupService.stop(this);shown="";}));
            else body.addView(Ui.text(this,"Dein Handy findet die Maschine anschließend automatisch im Heim-WLAN. Du brauchst keinen weiteren QR-Code.",20));
        }else if(host.isEmpty()){
            body.addView(Ui.button(this,"Mit dem Handy einrichten",v->startAp()));
            body.addView(Ui.text(this,"Die Maschine öffnet dafür vorübergehend ein eigenes geschütztes WLAN. Bluetooth wird nicht benötigt.",19));
        }else{
            body.addView(Ui.text(this,"Mit deinem WLAN verbunden",24));
            boolean paired=phoneCount()>0;
            if(paired&&!showAnotherPhone){
                body.addView(Ui.text(this,"Diese Ansicht schließt sich danach automatisch.",23));
                body.addView(Ui.button(this,"Weiteres Handy verbinden",v->{showAnotherPhone=true;shown="";}));
            }
            if((!paired||showAnotherPhone)&&receiver!=null&&receiver.identity!=null&&receiver.phones!=null){
                String uri=new Pairing(host,receiver.phones.invitation(),receiver.identity.pin).uri()+"&enroll=1";
                qr(body,SetupCode.link(uri));body.addView(Ui.text(this,"Verbinde dein Handy mit demselben WLAN und scanne diesen Code.",21));
            }
            if(!paired&&new CloudSession(this).account().optString("uid").isEmpty())
                body.addView(Ui.button(this,"Mit meinem Kalomatik-Konto anmelden",v->AccountDialog.show(this,()->{shown="";if(!new CloudSession(this).account().optString("uid").isEmpty())complete(false);} )));
            else if(!new CloudSession(this).account().optString("uid").isEmpty())body.addView(OnboardingUi.secondary(this,"Zur Küche",v->complete(false)));
            body.addView(Ui.button(this,"Anderes WLAN am Handy einrichten",v->startAp()));
        }
        if(setup==null||!setup.state.equals("connecting"))body.addView(Ui.button(this,"Ohne Konto lokal starten",v->complete(true)));
        body.addView(Ui.text(this,"Ein Konto ist freiwillig. Deine gekoppelten Handys bleiben auch ohne Konto verbunden.",18));
    }
    private void qr(LinearLayout body,String value){try{int size=Ui.dp(this,320);BitMatrix bits=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size);int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=bits.get(x,y)?Color.BLACK:Color.WHITE;Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.RGB_565);ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setContentDescription("QR-Code zur sicheren Handy-Einrichtung");body.addView(image,new LinearLayout.LayoutParams(-1,size));}catch(Exception e){body.addView(Ui.text(this,"Code konnte nicht erstellt werden. Bitte erneut öffnen.",20));}}
    private void startAp(){
        showAnotherPhone=false;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},91);return;}
        try{WifiSetupService.start(this);shown="";}catch(Exception e){Toast.makeText(this,"Beende zuerst den Kochvorgang und versuche es erneut.",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==91&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startAp();}
    private void complete(boolean local){
        if(local&&!new CloudSession(this).account().optString("uid").isEmpty()){
            new AlertDialog.Builder(this).setTitle("Maschinenkonto abmelden?").setMessage("Die lokalen Daten und Handy-Kopplungen bleiben erhalten.")
                    .setNegativeButton("Zurück",null).setPositiveButton("Lokal starten",(d,w)->{new CloudSession(this).logout();finishSetup("local");}).show();return;
        }
        finishSetup(local?"local":"account");
    }
    private void finishSetup(String mode){if(!getSharedPreferences("setup",0).edit().putBoolean("seen",true).putString("mode",mode).commit())return;finished=true;WifiSetupService.stop(this);startActivity(new Intent(this,ApplianceActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
    @Override public void onBackPressed(){complete(new CloudSession(this).account().optString("uid").isEmpty());}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
