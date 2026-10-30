package com.kitchenos.connect;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import android.graphics.Bitmap;
import com.kitchenos.common.*;
import com.kitchenos.connect.kitchen.*;
import com.kitchenos.connect.control.*;
import android.graphics.Rect;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real Android views/dialogs, using instrumented view actions rather than an unstable emulator IME. */
@android.annotation.TargetApi(29)
public final class KitchenUiSmoke extends Instrumentation {
    private Activity activity;
    private String viewport;private boolean quick;
    private void check(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);viewport=args==null?"":args.getString("viewport","");quick=args!=null&&args.getString("quick","").equals("true");start();}
    private View walk(View root,String text,boolean hint,boolean description){
        if(!root.isShown())return null;
        CharSequence value=description?root.getContentDescription():hint&&root instanceof EditText?((EditText)root).getHint():root instanceof TextView?((TextView)root).getText():null;
        if(value!=null&&value.toString().equalsIgnoreCase(text))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=walk(group.getChildAt(i),text,hint,description);if(found!=null)return found;}}
        return null;
    }
    private View find(String text,boolean hint,boolean description){List<View> windows=WindowInspector.getGlobalWindowViews();for(int i=windows.size()-1;i>=0;i--){View found=walk(windows.get(i),text,hint,description);if(found!=null)return found;}throw new IllegalStateException("UI nicht gefunden: "+text);}
    private void safely(Runnable action){java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable problem){error.set(problem);}});if(error.get()!=null)throw new IllegalStateException(error.get());waitForIdleSync();}
    private View awaitView(String text,boolean hint,boolean description){long deadline=SystemClock.elapsedRealtime()+10000;java.util.concurrent.atomic.AtomicReference<View> found=new java.util.concurrent.atomic.AtomicReference<>();do{runOnMainSync(()->{try{found.set(find(text,hint,description));}catch(IllegalStateException absent){found.set(null);}});if(found.get()!=null)return found.get();SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<deadline);throw new IllegalStateException("UI nicht gefunden: "+text);}
    private void click(String text){View view=awaitView(text,false,false);safely(()->{View target=view;while(!target.hasOnClickListeners()&&target.getParent() instanceof View){if(target.getParent() instanceof AdapterView){AdapterView<?> list=(AdapterView<?>)target.getParent();int position=list.getPositionForView(target);check(position>=0&&list.performItemClick(target,position,list.getItemIdAtPosition(position)),"Menüeintrag wurde nicht verarbeitet: "+text);return;}target=(View)target.getParent();}check(target.performClick(),"Klick wurde nicht verarbeitet: "+text);});}
    private void nav(String tab){action("Menü öffnen");click(tab);}
    private void geometry(){View add=awaitView("Lebensmittel hinzufügen",false,true),heart=awaitView("Favoriten öffnen",false,true),assistant=awaitView("Assistent öffnen",false,true);safely(()->{check(add.getWidth()==com.kitchenos.common.Ui.dp(activity,68)&&heart.getWidth()==com.kitchenos.common.Ui.dp(activity,48)&&assistant.getWidth()==com.kitchenos.common.Ui.dp(activity,48),"Kit-Aktionen müssen 48/68/48 dp messen");View root=(View)add.getParent().getParent();ScrollView content=(ScrollView)((android.widget.FrameLayout)root).getChildAt(0);int[] scrollPosition=new int[2],actionPosition=new int[2];content.getLocationOnScreen(scrollPosition);add.getLocationOnScreen(actionPosition);check(scrollPosition[1]+content.getHeight()<=actionPosition[1],"Hauptaktionen dürfen scrollbaren Inhalt nicht überdecken");View brand=find("Kalomatik",false,false);View word=(View)brand.getParent();int[] location=new int[2];word.getLocationOnScreen(location);check(Math.abs(location[0]+word.getWidth()/2f-root.getWidth()/2f)<3,"Markenheader muss zentriert sein");});}
    private void action(String description){View view=awaitView(description,false,true);safely(()->check(view.performClick(),"Hauptaktion wurde nicht verarbeitet"));}
    private void present(String text){awaitView(text,false,false);}
    private void fill(String hint,String value){EditText edit=(EditText)awaitView(hint,true,false);safely(()->{edit.setShowSoftInputOnFocus(false);edit.setText(value);});}
    private void screen(String name)throws Exception{waitForIdleSync();SystemClock.sleep(500);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot fehlt");if(name.startsWith("today-"))check(bitmap.getPixel(1,Math.min(bitmap.getHeight()-25,100))==Ui.BG,"Android-Fokushervorhebung darf die Kit-Hintergrundfarbe nicht verändern");try(FileOutputStream out=getTargetContext().openFileOutput("ui-"+viewport+"-"+name+".png",0)){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot konnte nicht gespeichert werden");}bitmap.recycle();}
    private void launch(String theme){getTargetContext().getSharedPreferences("appearance",0).edit().putString("theme",theme).commit();activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        check((Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"))&&Build.VERSION.SDK_INT>=29,"Nur für den isolierten Android-Emulator");
        RecipeStore recipes=new RecipeStore(getTargetContext());Recipe fixture=null;for(Recipe r:recipes.all())if(r.title.equals("Reispfanne"))fixture=r;
        if(fixture==null){fixture=new Recipe(UUID.randomUUID().toString(),"Reispfanne","",new JSONArray().put("200 g Reis").put("300 g Tomaten"),new JSONArray().put("Nur ein künstliches UI-Beispiel. Keine geprüfte Kochanleitung."),2);recipes.save(fixture);}
        KitchenStore kitchen=new KitchenStore(getTargetContext());JSONObject initial=kitchen.snapshot();if(initial.getJSONArray("pantry").length()==0){kitchen.addStock("Reis","300","g",java.time.LocalDate.now().plusDays(2).toString());kitchen.addStock("Tomaten","500","g",java.time.LocalDate.now().plusDays(3).toString());}boolean todayPlanned=false;JSONArray initialPlans=initial.getJSONArray("plans");for(int i=0;i<initialPlans.length();i++)if(initialPlans.getJSONObject(i).optString("date").equals(java.time.LocalDate.now().toString()))todayPlanned=true;if(!todayPlanned)kitchen.plan(fixture.id,java.time.LocalDate.now().toString(),4,2);
        for(String theme:new String[]{"dark","light"}){
            launch(theme);present("Deine Gerichte");geometry();screen("today-"+theme);if(quick)continue;
            nav("Mein Konto");if(!CloudSession.configured())safely(()->check(!find("Mit Google",false,false).isEnabled(),"Nicht konfigurierte Anmeldung muss deaktiviert bleiben"));screen("account-"+theme);click("Lokal weiter");
            nav("Vorrat");click("Hinzufügen");fill("Lebensmittel","Hafer");fill("Menge (optional)","250");click("Speichern");present("Hafer");boolean saved=false;JSONArray pantry=new KitchenStore(getTargetContext()).snapshot().getJSONArray("pantry");for(int i=0;i<pantry.length();i++){JSONObject item=pantry.getJSONObject(i);if(item.getString("name").equals("Hafer")&&item.getString("amount").equals("250"))saved=true;}check(saved,"Formularänderung nicht dauerhaft gespeichert");screen("pantry-"+theme);
            nav("Rezepte");present("Deine Rezepte");screen("recipes-"+theme);fill("Rezepte suchen","unbekannt-ui-987");present("Keine passenden Rezepte. Entdecke die Sammlung oder teile ein Rezept vom Handy.");fill("Rezepte suchen","");action("Rezept öffnen");action("Rezept-Menü");click("Rezept bearbeiten");present("Rezept verbessern");click("Abbrechen");screen("detail-"+theme);action("Als Favorit merken");screen("detail-favorite-"+theme);action("Meine Rezepte");action("Favoriten öffnen");present("Favoriten");present("Reispfanne");action("Rezept öffnen");action("Favorit entfernen");action("Meine Rezepte");
            ActivityMonitor chatMonitor=addMonitor(KitchenChatActivity.class.getName(),null,false);action("Assistent öffnen");Activity chat=waitForMonitorWithTimeout(chatMonitor,10000);check(chat!=null,"Messenger fehlt");removeMonitor(chatMonitor);present("Dein Assistent");screen("assistant-"+theme);click("Vorlieben");present("So koche ich gern");click("Abbrechen");fill("Nachricht …","Was kann ich aus meinem Vorrat kochen?");action("Nachricht senden");present("Vorschläge aus deiner gespeicherten Sammlung. Bitte prüfe die Mengen und Zubereitungszeiten im Rezept; dies ist keine neu erzeugte KI-Antwort.");screen("assistant-messages-"+theme);Recipe received=new Recipe(UUID.randomUUID().toString(),"UI-Empfang beim Assistenten","",new JSONArray().put("100 g Reis"),new JSONArray().put("Künstliches Empfangsbeispiel im Emulator."),1);new RecipeStore(getTargetContext()).save(received);action("Assistent schließen");present("UI-Empfang beim Assistenten");new RecipeStore(getTargetContext()).delete(received);
            nav("Küche");ActivityMonitor monitor=addMonitor(ControlActivity.class.getName(),null,false);click("Manuell kochen");Activity manual=waitForMonitorWithTimeout(monitor,10000);check(manual!=null,"Manuelle Bedienseite fehlt");removeMonitor(monitor);present("Nicht verbunden");safely(()->check(!ControlClient.get(getTargetContext()).state.busy()&&!ControlClient.get(getTargetContext()).state.rootChecking,"Öffnen darf weder Root noch Steuerverbindung starten"));screen("manual-"+theme);click("‹ Zur Küche");nav("Heute");action("Nächste Woche");awaitView("0 Gerichte geplant. Kochplan öffnen",false,true);action("Vorige Woche");
            KitchenStore current=new KitchenStore(getTargetContext());JSONArray items=current.snapshot().getJSONArray("pantry");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.getString("name").equalsIgnoreCase("hafer")||item.getString("name").equals("HHafer"))current.removeStock(item.getString("id"));}
        }
        launch("dark");screen("today-dark");result.putString("result",quick?"PASS: original 48/68/48 dp actions, centred branding, no content overlap, exact dark/light background colours":"PASS: Kit geometry, form persistence, recipe editor, native messenger with real local suggestions, preferences, cloud-disabled state, manual page without root/UART; dark/light screenshots");finish(-1,result);
    }catch(Throwable error){android.util.Log.e("KitchenOSUiSmoke","Failed",error);result.putString("failure",error.toString());finish(0,result);}}
}
