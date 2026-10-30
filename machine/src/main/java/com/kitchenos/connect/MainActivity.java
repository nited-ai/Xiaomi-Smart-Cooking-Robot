package com.kitchenos.connect;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.kitchen.KitchenPages;
import com.kitchenos.connect.kitchen.KitchenChatActivity;
import com.kitchenos.connect.kitchen.KitViews;
import com.kitchenos.connect.kitchen.KitchenRecipes;
import com.kitchenos.connect.control.ControlClient;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.*;

/** Kitchen pages and recipe reception. Hardware access lives in the explicit manual controller. */
public class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout page,body;
    private WifiPanel wifi;
    private String screen="today";
    private java.time.LocalDate selectedDay=java.time.LocalDate.now();
    private final Runnable update=new Runnable(){public void run(){if(wifi!=null)wifi.refresh();if(screen.equals("receive"))showReceiverBody();main.postDelayed(this,3000);}};
    private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(screen.equals("recipes")||screen.equals("today"))home(screen);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);String requested=getIntent().getStringExtra("screen");if(!java.util.Arrays.asList("wifi","receive").contains(requested)){startActivity(new Intent(this,com.kitchenos.connect.ui.ApplianceActivity.class).putExtra("page",java.util.Arrays.asList("recipes","plan").contains(requested)?requested:"home"));finish();return;}screen=requested;if(state!=null){screen=state.getString("screen","today");try{selectedDay=java.time.LocalDate.parse(state.getString("day",selectedDay.toString()));}catch(Exception ignored){}}show();}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);home("today");}
    @Override protected void onResume(){super.onResume();IntentFilter filter=new IntentFilter("com.kitchenos.connect.RECIPES_CHANGED");if(Build.VERSION.SDK_INT>=33)registerReceiver(changes,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(changes,filter,"com.kitchenos.connect.INTERNAL",null);main.post(update);}
    @Override protected void onPause(){main.removeCallbacks(update);unregisterReceiver(changes);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("screen",screen);b.putString("day",selectedDay.toString());super.onSaveInstanceState(b);}
    private void show(){switch(screen){case "wifi":showWifi();break;case "receive":receive();break;default:home(screen);}}
    private void base(){wifi=null;page=Ui.page(this);}
    private void home(String tab){startActivity(new Intent(this,com.kitchenos.connect.ui.ApplianceActivity.class).putExtra("page",tab.equals("today")?"home":tab.equals("mine")||tab.equals("kitchen")?"modes":tab));finish();}
    private KitchenPages.Navigation navigation(){return new KitchenPages.Navigation(){public void home(String tab){MainActivity.this.home(tab);}public java.time.LocalDate date(){return selectedDay;}public void day(java.time.LocalDate date){selectedDay=date;MainActivity.this.home("today");}public void recipe(Recipe r){detail(r);}public void wifi(){showWifi();}public void receiver(){receive();}public void vendor(){MainActivity.this.vendor();}};}
    private void vendor(){if(ControlClient.hasActiveSession()){Toast.makeText(this,"Bitte zuerst die manuelle Steuerung sauber trennen und den Maschinenzustand prüfen.",Toast.LENGTH_LONG).show();return;}try{Intent i=getPackageManager().getLaunchIntentForPackage("com.chunmi.multicooker3");if(i==null)throw new IllegalStateException();startActivity(i);}catch(Exception e){Toast.makeText(this,"Xiaomi-App ist nicht verfügbar",Toast.LENGTH_LONG).show();}}
    private void backHeader(String title){page.addView(Ui.button(this,"‹ Zurück",v->home("mine")));page.addView(Ui.title(this,title));}
    private void showWifi(){screen="wifi";base();page.addView(Ui.button(this,"‹ Zurück",v->home("mine")));wifi=new WifiPanel(this,page);}
    private void receive(){screen="receive";base();backHeader("Handy verbinden");body=Ui.column(this);page.addView(body);showReceiverBody();}
    private String displayedPairing;
    private void showReceiverBody(){
        RecipeReceiverService service=RecipeReceiverService.active;
        String host=wifiAddress();
        String key=service==null?"off":service.error!=null?service.error:service.identity==null?"starting":host+service.identity.token;
        if(key.equals(displayedPairing)&&body.getChildCount()>0)return;displayedPairing=key;body.removeAllViews();
        body.addView(Ui.text(this,"1. Handy und Maschine mit demselben WLAN verbinden.\n2. KitchenOS-Sende-App auf dem Handy installieren.\n3. QR-Code mit der Handykamera öffnen. Danach im Browser: Teilen → An KitchenOS senden.",20));
        if(service==null){body.addView(Ui.button(this,"Rezeptempfang einschalten",v->{RecipeReceiverService.requestStart(this);displayedPairing=null;main.postDelayed(()->{if(screen.equals("receive"))showReceiverBody();},700);}));return;}
        if(service.error!=null)body.addView(Ui.text(this,service.error,20));
        else if(service.identity==null)body.addView(Ui.text(this,"Verschlüsselte Verbindung wird vorbereitet …",20));
        else if(host==null){body.addView(Ui.text(this,"Bitte die Maschine zuerst mit WLAN verbinden.",21));body.addView(Ui.button(this,"WLAN einrichten",v->showWifi()));}
        else try{
            Pairing pairing=new Pairing(host,service.identity.token,service.identity.pin);
            int size=Ui.dp(this,350);BitMatrix qr=new MultiFormatWriter().encode(pairing.uri(),BarcodeFormat.QR_CODE,size,size);
            Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setPixel(x,y,qr.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView view=new ImageView(this);view.setImageBitmap(image);view.setContentDescription("KitchenOS-Kopplungscode");body.addView(view,new LinearLayout.LayoutParams(-1,size));
            TextView address=Ui.text(this,"Maschine: "+host+"\nVerschlüsselt · nur gekoppelte Handys",18);body.addView(address);
            body.addView(Ui.button(this,"Kopplungscode anzeigen / kopieren",v->{EditText code=Ui.input(this,"Kopplungscode",true);code.setText(pairing.uri());code.setTextIsSelectable(true);new AlertDialog.Builder(this).setTitle("Auf dem Handy in KitchenOS einfügen").setView(code).setPositiveButton("Schließen",null).show();}));
        }catch(Exception e){body.addView(Ui.text(this,"Kopplung konnte nicht dargestellt werden.",20));}
        body.addView(Ui.button(this,"Empfang ausschalten",v->{stopService(new Intent(this,RecipeReceiverService.class));displayedPairing=null;main.postDelayed(()->{if(screen.equals("receive"))showReceiverBody();},300);}));
        body.addView(Ui.button(this,"Alle Handys entkoppeln",v->{stopService(new Intent(this,RecipeReceiverService.class));getSharedPreferences("receiver",0).edit().remove("token").commit();Toast.makeText(this,"Kopplung aufgehoben. Empfang erneut einschalten und neu koppeln.",Toast.LENGTH_LONG).show();displayedPairing=null;main.postDelayed(()->{if(screen.equals("receive"))showReceiverBody();},300);}));
    }
    private String wifiAddress(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);int ip=w.getConnectionInfo().getIpAddress();return ip==0?null:String.format(Locale.ROOT,"%d.%d.%d.%d",ip&255,ip>>8&255,ip>>16&255,ip>>24&255);}catch(Exception e){return null;}}
    private void detail(Recipe r){
        screen="detail";base();KitchenPages pages=new KitchenPages(this,navigation());LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);tools.addView(KitViews.icon(this,"‹","Meine Rezepte",()->home("recipes")),new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));View spacer=new View(this);tools.addView(spacer,new LinearLayout.LayoutParams(0,1,1));ImageButton heart=KitViews.imageIcon(this,pages.isFavorite(r)?"ic_favorite_filled":"ic_favorite",pages.isFavorite(r)?"Favorit entfernen":"Als Favorit merken",()->pages.toggleFavorite(r));heart.setSelected(pages.isFavorite(r));tools.addView(heart,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));tools.addView(KitViews.imageIcon(this,"ic_more","Rezept-Menü",()->pages.recipeMenu(r)),new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));page.addView(tools);
        KitViews.space(this,page,24);TextView title=KitViews.text(this,r.title,21,Ui.INK,true);page.addView(title);KitViews.space(this,page,18);LinearLayout info=new LinearLayout(this);info.setGravity(Gravity.CENTER_VERTICAL);ImageView photo=new ImageView(this);photo.setImageResource(getResources().getIdentifier("meal_placeholder","drawable",getPackageName()));photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(KitViews.shape(this,Ui.CARD,20,0));photo.setClipToOutline(true);info.addView(photo,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,96)));LinearLayout facts=Ui.column(this);facts.setPadding(Ui.dp(this,20),0,0,0);facts.addView(KitViews.text(this,r.servings>0?r.servings+" Portionen":"Portionsbasis offen",17,Ui.INK,true));KitViews.space(this,facts,8);facts.addView(KitViews.text(this,r.ingredients.length()+" Zutaten · "+r.steps.length()+" Schritte",13,Ui.MUTED,false));info.addView(facts,new LinearLayout.LayoutParams(0,-2,1));page.addView(info);KitViews.space(this,page,20);
        KitViews.row(this,page,"Einplanen","Datum und gewünschte Portionen",null,()->pages.planDialog(r));KitViews.row(this,page,"Rezept bearbeiten","Eine eigene Fassung speichern",null,()->pages.editRecipe(r));
        KitViews.heading(this,page,"Zutaten",null,null);for(int i=0;i<r.ingredients.length();i++){TextView ingredient=KitViews.text(this,r.ingredients.optString(i),16,Ui.INK,false);ingredient.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));page.addView(ingredient);KitViews.divider(this,page);}
        KitViews.heading(this,page,"Zubereitung",null,null);for(int i=0;i<r.steps.length();i++){TextView step=KitViews.text(this,(i+1)+"  "+r.steps.optString(i),16,Ui.INK,false);step.setLineSpacing(Ui.dp(this,5),1);step.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,16));page.addView(step);}
        if(!r.source.isEmpty())KitViews.row(this,page,"Quelle",r.source,null,null);
        KitViews.space(this,page,20);TextView delete=KitViews.text(this,"Rezept löschen",14,Ui.MUTED,false);delete.setMinHeight(Ui.dp(this,44));delete.setGravity(Gravity.CENTER_VERTICAL);delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Rezept löschen?").setMessage(r.title).setPositiveButton("Löschen",(d,w)->{try{KitchenRecipes.delete(this,r);home("recipes");}catch(Exception e){Toast.makeText(this,"Löschen fehlgeschlagen",Toast.LENGTH_LONG).show();}}).setNegativeButton("Behalten",null).show());page.addView(delete);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=40)return;if(result==RESULT_OK&&data!=null){String id=data.getStringExtra("recipeId");if(id!=null){for(Recipe recipe:KitchenRecipes.all(this))if(recipe.id.equals(id)){detail(recipe);return;}}String tab=data.getStringExtra("tab");if(tab!=null&&Arrays.asList("today","pantry","recipes","plan","kitchen","wifi","receive").contains(tab)){screen=tab;show();return;}}if(Arrays.asList("today","pantry","recipes","plan","favorites","kitchen").contains(screen))home(screen);}
    @Override public void onBackPressed(){if(screen.equals("today"))super.onBackPressed();else home("today");}
}
