package com.kitchenos.connect.kitchen;

import android.app.*;
import android.graphics.Bitmap;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** QR approval in the phone browser. No Google Play services/browser required on the appliance. */
public final class DeviceLoginDialog {
    public static void show(Activity activity,String provider,Runnable complete){
        CloudSession session=new CloudSession(activity);
        AtomicBoolean cancelled=new AtomicBoolean();AtomicReference<String> pending=new AtomicReference<>("");
        LinearLayout box=Ui.column(activity);box.setPadding(24,8,24,12);
        TextView status=Ui.text(activity,"Anmeldung wird vorbereitet …",20);box.addView(status);
        ImageView qr=new ImageView(activity);qr.setContentDescription("QR-Code zur Kontoanmeldung am Handy");box.addView(qr,new LinearLayout.LayoutParams(-1,Ui.dp(activity,350)));
        box.addView(Ui.text(activity,"Scanne den Code mit deinem Handy. Melde dich beim ausgewählten Anbieter an und bestätige die Verbindung mit deiner Küchenmaschine. Die Anmeldung wird hier automatisch abgeschlossen.",18));
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Mit dem Handy anmelden").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Neuer Code",(d,w)->show(activity,provider,complete)).create();
        dialog.setOnDismissListener(d->{cancelled.set(true);String state=pending.get();if(!state.isEmpty())new Thread(()->{try{session.cancelLogin(state);}catch(Exception ignored){}},"KitchenOS-Cancel-Login").start();});dialog.show();dialog.setCanceledOnTouchOutside(false);dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);com.kitchenos.connect.ui.RotaryDialogs.bind(activity,dialog);
        new Thread(()->{try{
            JSONObject prepared=session.startDevice(provider);String state=prepared.getString("state");pending.set(state);
            if(cancelled.get()){session.cancelLogin(state);return;}
            int size=Ui.dp(activity,350);BitMatrix matrix=new MultiFormatWriter().encode(prepared.getString("url"),BarcodeFormat.QR_CODE,size,size);
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)bitmap.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);
            activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!cancelled.get()){qr.setImageBitmap(bitmap);status.setText("QR-Code am Handy scannen · 10 Minuten gültig");}});
            long deadline=android.os.SystemClock.elapsedRealtime()+600000;
            while(!cancelled.get()&&!activity.isDestroyed()&&android.os.SystemClock.elapsedRealtime()<deadline){
                if(session.pollDevice(state)){activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!cancelled.get()){dialog.dismiss();complete.run();Toast.makeText(activity,"Dein Konto wurde mit der Küche verbunden.",Toast.LENGTH_LONG).show();}});return;}
                Thread.sleep(3000);
            }
            session.cancelLogin(state);
            if(!cancelled.get())activity.runOnUiThread(()->{if(!activity.isDestroyed()){qr.setImageDrawable(null);status.setText("Der Code ist abgelaufen. Mit „Neuer Code“ kannst du die Anmeldung erneuern.");}});
        }catch(Exception error){String state=pending.get();if(!state.isEmpty())try{session.cancelLogin(state);}catch(Exception ignored){}activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!cancelled.get())status.setText(error.getMessage()==null?"Anmeldung konnte nicht abgeschlossen werden.":error.getMessage());});}},"KitchenOS-Device-Login").start();
    }
}
