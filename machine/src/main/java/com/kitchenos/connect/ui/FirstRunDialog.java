package com.kitchenos.connect.ui;
import android.app.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.*;
public final class FirstRunDialog {
    public static Dialog show(Activity a,Runnable wifi,Runnable completed){LinearLayout content=Ui.column(a);content.setPadding(Ui.dp(a,30),Ui.dp(a,24),Ui.dp(a,30),Ui.dp(a,24));content.addView(KitViews.text(a,"Deine Küche wird Kalomatik",34,Ui.INK,true));TextView copy=KitViews.text(a,"Verbinde die Maschine mit deinem WLAN und melde dich mit deinem Kalomatik-Konto an. Den QR-Code scannst du mit deinem Handy. Dort kannst du Rezepte entdecken, deine Woche planen und Essen erfassen.",25,Ui.MUTED,false);copy.setPadding(0,Ui.dp(a,20),0,Ui.dp(a,24));content.addView(copy);AlertDialog dialog=new AlertDialog.Builder(a).setView(content).setNegativeButton("Lokal starten",(d,w)->{a.getSharedPreferences("setup",0).edit().putBoolean("seen",true).apply();completed.run();}).create();Button network=Ui.button(a,"WLAN einrichten",v->{dialog.dismiss();wifi.run();}),login=Ui.button(a,"Mit dem Handy anmelden",v->{dialog.dismiss();AccountDialog.show(a,()->{if(!new CloudSession(a).account().optString("uid").isEmpty())a.getSharedPreferences("setup",0).edit().putBoolean("seen",true).apply();completed.run();});});network.setMinHeight(Ui.dp(a,82));login.setMinHeight(Ui.dp(a,82));content.addView(network);content.addView(login);dialog.show();RotaryDialogs.bind(a,dialog);dialog.setCanceledOnTouchOutside(false);return dialog;}
    private FirstRunDialog(){}
}
