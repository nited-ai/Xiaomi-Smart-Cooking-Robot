package com.kitchenos.connect.kitchen;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import com.kitchenos.connect.MainActivity;
public final class AccountCallbackActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);TextView status=new TextView(this);status.setPadding(32,48,32,32);status.setTextSize(22);status.setText("Anmeldung wird abgeschlossen …");setContentView(status);Uri uri=getIntent().getData();
        if(saved!=null||uri==null){status.setText("Bitte Anmeldung bei Bedarf erneut öffnen.");return;}
        new Thread(()->{try{new CloudSession(this).complete(uri);runOnUiThread(()->{Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(open);finish();});}catch(Exception e){runOnUiThread(()->status.setText(e.getMessage()==null?"Anmeldung fehlgeschlagen. Bitte erneut versuchen.":e.getMessage()));}},"KitchenOS-Account-Return").start();
    }
}
