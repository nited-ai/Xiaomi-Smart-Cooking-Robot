package com.kitchenos.connect.ui;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.*;
import com.kitchenos.connect.control.ControlClient;
import org.json.JSONObject;

/** Calomatik provider artwork and quiet provider rows; an authenticated account shows its profile. */
public final class AccountDialog {
    public static void show(Activity a,Runnable changed){
        CloudSession session=new CloudSession(a);JSONObject account=session.account();boolean signedIn=!account.optString("uid").isEmpty();
        LinearLayout content=Ui.column(a);content.setPadding(Ui.dp(a,28),Ui.dp(a,16),Ui.dp(a,28),Ui.dp(a,24));
        TextView title=KitViews.text(a,signedIn?"Dein Profil":"Willkommen bei Kalomatik",32,Ui.INK,true);content.addView(title);TextView intro=KitViews.text(a,signedIn?account.optString("displayName","Deine Küche"):"Deine Rezepte und dein Kochplan. Auf dem Handy und auf deiner Maschine.",23,Ui.MUTED,false);intro.setPadding(0,Ui.dp(a,16),0,Ui.dp(a,22));content.addView(intro);
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);RotaryController knob=new RotaryController(a,dialog::dismiss);
        if(signedIn){TextView email=KitViews.text(a,account.optString("email","Mit Kalomatik verbunden"),26,Ui.INK,true);email.setTag("account.email");content.addView(email);TextView status=KitViews.text(a,"Mit Kalomatik verbunden",20,Ui.ACCENT,false);status.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,30));content.addView(status);Button logout=Ui.button(a,"Abmelden",v->{session.logout();dialog.dismiss();changed.run();});logout.setTag("account.logout");logout.setTextSize(24);logout.setMinHeight(Ui.dp(a,80));content.addView(logout);}
        else{String[] ids={"google","microsoft","facebook","apple","email"},labels={"Mit Google","Mit Microsoft","Mit Facebook","Mit Apple","Mit E-Mail"};for(int i=0;i<ids.length;i++){final String id=ids[i];LinearLayout row=new LinearLayout(a);row.setTag("account.provider."+id);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(a,24),0,Ui.dp(a,24),0);row.setContentDescription(labels[i]);ImageView image=new ImageView(a);image.setImageDrawable(logo(a,id));row.addView(image,new LinearLayout.LayoutParams(Ui.dp(a,32),Ui.dp(a,32)));TextView label=KitViews.text(a,labels[i],25,Ui.INK,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=Ui.dp(a,24);row.addView(label,lp);row.setBackground(KitViews.shape(a,Ui.CARD,16,0));row.setOnClickListener(v->{if(ControlClient.hasActiveSession()){Toast.makeText(a,"Beende zuerst den laufenden Kochvorgang.",Toast.LENGTH_LONG).show();return;}dialog.dismiss();DeviceLoginDialog.show(a,id,changed);});content.addView(row,new LinearLayout.LayoutParams(-1,Ui.dp(a,84)));View line=new View(a);line.setBackgroundColor(Ui.LINE);content.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));}}
        for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child.isClickable())knob.add(child);}com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->knob.dispatch(event));ScrollView scroll=new KitchenScroll(a);scroll.addView(content);dialog.setContentView(scroll);Window window=dialog.getWindow();window.setBackgroundDrawable(KitViews.shape(a,Ui.BG,28,0));dialog.show();window.setLayout(Math.min(Ui.dp(a,640),a.getResources().getDisplayMetrics().widthPixels-Ui.dp(a,48)),-2);dialog.setCanceledOnTouchOutside(true);
    }
    private static Drawable logo(Activity a,String id){return com.kitchenos.common.OnboardingUi.logo(a,id);}
    private AccountDialog(){}
}
