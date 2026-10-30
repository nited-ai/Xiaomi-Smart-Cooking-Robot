package com.kitchenos.connect.ui;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.wifi.WifiManager;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import org.json.*;
import java.util.*;

public final class DeviceConnectionPage {
    public static String state(Activity activity){
        RecipeReceiverService service=RecipeReceiverService.active;
        if(service==null)return "off";
        if(service.error!=null)return service.error;
        if(service.identity==null||service.phones==null)return "starting";
        try{return address(activity)+":"+service.phones.list().toString().hashCode()+":"+service.phones.invitation().hashCode();}
        catch(Exception e){return "unavailable";}
    }
    public static void render(Activity a,LinearLayout body,Runnable refresh,Runnable wifi){
        body.addView(Ui.title(a,"Verbundene Handys"));
        body.addView(Ui.text(a,"Alle gekoppelten Handys haben dieselben lokalen Möglichkeiten. Ein Kalomatik-Konto ist für die Kopplung nicht erforderlich.",22));
        try{
            JSONArray phones=new PhoneRegistry(a).list();
            if(phones.length()==0)body.addView(Ui.text(a,"Noch kein Handy gekoppelt.",21));
            for(int i=0;i<phones.length();i++){
                JSONObject phone=phones.getJSONObject(i);String id=phone.getString("id"),name=phone.getString("name");
                LinearLayout card=Ui.card(a,body);card.addView(Ui.text(a,name,24));card.addView(Ui.text(a,"Gekoppelt · gleichberechtigter Zugriff",17));
                card.addView(Ui.button(a,"Handy entfernen",v->{AlertDialog dialog=new AlertDialog.Builder(a).setTitle(name+" entfernen?")
                    .setMessage("Dieses Handy muss danach einen neuen Code scannen. Andere Handys bleiben gekoppelt.")
                    .setNegativeButton("Behalten",null).setPositiveButton("Entfernen",(d,w)->{try{new PhoneRegistry(a).remove(id);refresh.run();}catch(Exception e){Toast.makeText(a,"Handy konnte nicht entfernt werden.",Toast.LENGTH_LONG).show();}}).create();dialog.show();RotaryDialogs.bind(a,dialog);}));
            }
        }catch(Exception e){body.addView(Ui.text(a,"Die Handyliste konnte nicht gelesen werden.",20));}
        RecipeReceiverService service=RecipeReceiverService.active;String host=address(a);
        if(service==null){body.addView(Ui.button(a,"Handy-Empfang einschalten",v->{RecipeReceiverService.requestStart(a);refresh.run();}));return;}
        if(service.error!=null){body.addView(Ui.text(a,service.error,22));return;}
        if(service.identity==null||service.phones==null){body.addView(Ui.text(a,"Die verschlüsselte Verbindung wird vorbereitet …",22));return;}
        if(host==null){body.addView(Ui.button(a,"Mit dem Handy einrichten",v->a.startActivity(new Intent(a,SetupActivity.class))));return;}
        body.addView(Ui.text(a,"Weiteres Handy hinzufügen",26));
        try{
            String code=new Pairing(host,service.phones.invitation(),service.identity.pin).uri()+"&enroll=1";
            int size=Ui.dp(a,320);BitMatrix qr=new MultiFormatWriter().encode(SetupCode.link(code),BarcodeFormat.QR_CODE,size,size);
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=qr.get(x,y)?Color.BLACK:Color.WHITE;
            ImageView view=new ImageView(a);view.setImageBitmap(Bitmap.createBitmap(pixels,size,size,Bitmap.Config.RGB_565));view.setContentDescription("Code zum Hinzufügen eines Handys");body.addView(view,new LinearLayout.LayoutParams(-1,size));
            body.addView(Ui.text(a,"Handy ins gleiche WLAN bringen und den Code mit der Handykamera scannen. Jedes Handy erhält einen eigenen, gleichberechtigten Zugang.",20));
        }catch(Exception e){body.addView(Ui.text(a,"Code konnte nicht erstellt werden.",20));}
        body.addView(Ui.button(a,"Empfang ausschalten",v->{a.getSharedPreferences("receiver",0).edit().putBoolean("enabled",false).commit();a.stopService(new Intent(a,RecipeReceiverService.class));refresh.run();}));
    }
    private static String address(Activity a){try{int ip=((WifiManager)a.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getIpAddress();return ip==0?null:String.format(Locale.ROOT,"%d.%d.%d.%d",ip&255,ip>>8&255,ip>>16&255,ip>>24&255);}catch(Exception e){return null;}}
    private DeviceConnectionPage(){}
}
