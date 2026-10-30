package com.kitchenos.connect.ui;

import android.app.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;
import org.json.JSONObject;

/** Handoff to the source plan: missing amounts remain explicit. */
public final class PlanCompletionDialog {
    public static Dialog show(Activity activity,JSONObject recipe,Runnable refresh){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        RotaryController knob=new RotaryController(activity,dialog::dismiss);
        LinearLayout surface=Ui.column(activity);surface.setPadding(Ui.dp(activity,28),Ui.dp(activity,24),Ui.dp(activity,28),Ui.dp(activity,24));surface.setBackgroundColor(Ui.BG);dialog.setContentView(surface);
        surface.addView(KitViews.text(activity,"Rezept in Kalomatik ergänzen",29,Ui.INK,true));
        KitchenScroll scroll=new KitchenScroll(activity);LinearLayout content=Ui.column(activity);scroll.addView(content);surface.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String date=recipe.optString("planSourceDate");
        TextView help=KitViews.text(activity,"Scanne den Code mit deinem Handy und öffne deinen Kalomatik-Kochplan. Ergänze dort für „"+recipe.optString("title")+"“"+(date.isEmpty()?"":" am "+date)+" die fehlenden Angaben. Danach kannst du den Kochplan hier neu laden.",23,Ui.INK,false);
        content.addView(help);ReadingRegion reader=knob.addReadingRegion(help,scroll);content.addView(reader.hintView());
        ImageView qr=new ImageView(activity);qr.setPadding(18,18,18,18);qr.setBackgroundColor(0xfffafbf6);qr.setContentDescription("QR-Code: https://app.kalomatik.ai");
        try{BitMatrix code=new MultiFormatWriter().encode("https://app.kalomatik.ai",BarcodeFormat.QR_CODE,360,360);Bitmap bitmap=Bitmap.createBitmap(360,360,Bitmap.Config.RGB_565);for(int y=0;y<360;y++)for(int x=0;x<360;x++)bitmap.setPixel(x,y,code.get(x,y)?0xff172018:0xfffafbf6);qr.setImageBitmap(bitmap);}catch(Exception ignored){}
        LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(-1,Ui.dp(activity,340));image.topMargin=Ui.dp(activity,18);content.addView(qr,image);
        content.addView(KitViews.text(activity,"app.kalomatik.ai · dein persönlicher Kochplan",18,Ui.MUTED,false));
        Button reload=Ui.button(activity,"Kochplan aktualisieren",v->{dialog.dismiss();refresh.run();});reload.setTextSize(22);reload.setBackground(KitViews.shape(activity,Ui.ACCENT,18,0));reload.setTextColor(0xff171716);reload.setTag("plan.complete.reload");knob.add(reload);surface.addView(reload,new LinearLayout.LayoutParams(-1,Ui.dp(activity,66)));
        Button close=Ui.button(activity,"Zurück zum Rezept",v->dialog.dismiss());close.setTag("plan.complete.close");knob.add(close);surface.addView(close,new LinearLayout.LayoutParams(-1,Ui.dp(activity,60)));
        com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->knob.dispatch(event));dialog.setOnDismissListener(d->knob.reset());dialog.show();dialog.getWindow().setLayout(activity.getResources().getDisplayMetrics().widthPixels-Ui.dp(activity,32),activity.getResources().getDisplayMetrics().heightPixels-Ui.dp(activity,48));dialog.getWindow().getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());return dialog;
    }
    private PlanCompletionDialog(){}
}
