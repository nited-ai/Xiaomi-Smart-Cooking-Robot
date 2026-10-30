package com.kitchenos.machinelink;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.*;
import com.kitchenos.common.*;
import com.kitchenos.share.PhoneSetupActivity;

/** Native entry embedded in Kalomatik's Android app; no separate companion app required. */
public final class MachineEntryActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout page=Ui.page(this);page.addView(Ui.title(this,"Deine Kalomatik-Maschine"));
        page.addView(Ui.text(this,"Öffne auf deiner Maschine die Handy-Einrichtung und scanne ihren QR-Code. WLAN-Passwort und Kontoanmeldung erledigst du anschließend hier am Handy.",18));
        status=Ui.text(this,"",17);page.addView(status);
        page.addView(Ui.primary(this,"QR-Code der Maschine scannen",v->{
            GmsBarcodeScannerOptions options=new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build();
            GmsBarcodeScanning.getClient(this,options).startScan().addOnSuccessListener(code->{if(!isDestroyed())open(code.getRawValue());})
                    .addOnFailureListener(error->{if(!isDestroyed())status.setText("Scanner noch nicht verfügbar. Für die erstmalige Bereitstellung kann Google Play eine Internetverbindung benötigen. Du kannst den Code auch mit deiner Handykamera öffnen.");});
        }));
        page.addView(Ui.button(this,"Kopplungscode einfügen",v->{EditText code=Ui.input(this,"kitchenos://setup?…",true);code.setSaveEnabled(false);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Code von deiner Maschine").setView(code).setNegativeButton("Abbrechen",null).setPositiveButton("Öffnen",(d,w)->open(code.getText().toString())).create();dialog.show();}));
        try{Pairing paired=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));page.addView(Ui.button(this,"Gekoppelte Maschine öffnen",v->startActivity(new Intent(this,PhoneSetupActivity.class).setData(Uri.parse(paired.uri())).putExtra("reconnect",true))));}catch(Exception ignored){}
        page.addView(Ui.text(this,"Die lokale Kopplung benötigt kein Kalomatik-Konto. Mehrere Handys können dieselbe Maschine gleichberechtigt verwenden.",17));
    }
    private void open(String value){try{Uri uri=SetupCode.parse(value);new Pairing(uri.getQueryParameter("host"),uri.getQueryParameter("token"),uri.getQueryParameter("pin"));startActivity(new Intent(this,PhoneSetupActivity.class).setData(uri));}catch(Exception invalid){status.setText("Das ist kein vollständiger Maschinen-Code. Öffne einen neuen Code auf deiner Maschine.");}}
}
