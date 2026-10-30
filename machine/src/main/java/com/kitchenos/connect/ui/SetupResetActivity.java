package com.kitchenos.connect.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.*;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.kitchen.CloudSession;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Non-exported supervised setup reset. Requires an independently armed device recovery process. */
public final class SetupResetActivity extends Activity {
    private TextView status;
    private boolean running;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout page=Ui.page(this);
        page.addView(Ui.title(this,"Einrichtung neu starten"));
        status=Ui.text(this,"Maschinenkonto, Handy-Kopplungen und gespeicherte WLANs werden zurückgesetzt. Rezepte und die übrigen App-Daten bleiben erhalten.",22);page.addView(status);
        page.addView(Ui.primary(this,"Einrichtung zurücksetzen",v->reset()));
        page.addView(Ui.button(this,"Abbrechen",v->{if(!running)finish();}));
        if(getIntent().getBooleanExtra("confirmed",false))reset();
    }
    private String recovery(String command)throws Exception{
        if(!command.equals("status")&&!command.equals("forget"))throw new IllegalArgumentException();
        Process process=new ProcessBuilder("/sbin/magisk","su","-c",
            "CLASSPATH=/data/local/tmp/kalomatik-wifi-recovery.jar /system/bin/app_process /system/bin com.kitchenos.recovery.WifiRecovery "+command).redirectErrorStream(true).start();
        try{
            if(!process.waitFor(20,TimeUnit.SECONDS))throw new java.io.IOException("Zeitüberschreitung");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=process.getInputStream()){
                byte[] buffer=new byte[512];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>8192)throw new java.io.IOException();bytes.write(buffer,0,n);}
            }
            if(process.exitValue()!=0)throw new java.io.IOException("Rückfallsicherung nicht bereit");return bytes.toString("UTF-8");
        }finally{process.destroy();}
    }
    private void reset(){
        if(running)return;
        if(ControlClient.hasActiveSession()){status.setText("Beende zuerst den Kochvorgang.");return;}
        running=true;status.setText("Die Einrichtung wird zurückgesetzt …");
        new Thread(()->{
            boolean changed=false;
            try{
                String check=recovery("status");if(!check.contains("alive=true backup=true state=ARMED"))throw new java.io.IOException();
                runOnUiThread(()->{WifiSetupService.stop(this);stopService(new Intent(this,RecipeReceiverService.class));});
                long end=SystemClock.elapsedRealtime()+5000;
                while((WifiSetupService.active!=null||RecipeReceiverService.active!=null)&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
                if(WifiSetupService.active!=null||RecipeReceiverService.active!=null)throw new java.io.IOException();
                new CloudSession(this).logout();changed=true;
                if(!new CloudSession(this).account().optString("uid").isEmpty())throw new java.io.IOException();
                new PhoneRegistry(this).clear();
                if(!getSharedPreferences("receiver",0).edit().clear().commit()||!getSharedPreferences("setup",0).edit().clear().commit())throw new java.io.IOException();
                if(!recovery("forget").contains("FORGOTTEN"))throw new java.io.IOException();
                runOnUiThread(()->{startActivity(new Intent(this,SetupActivity.class).putExtra("startAccessPoint",true));finish();});
            }catch(Exception error){final boolean partial=changed;runOnUiThread(()->{running=false;status.setText(partial?
                "Die Einrichtung wurde nur teilweise zurückgesetzt. Konto und Kopplungen bitte prüfen. Die WLAN-Rückfallsicherung bleibt aktiv.":
                "Zurücksetzen konnte nicht gestartet werden. Die WLAN-Rückfallsicherung muss zuerst bereit sein.");});}
        },"Kalomatik-Setup-Reset").start();
    }
    @Override public void onBackPressed(){if(!running)super.onBackPressed();}
}
