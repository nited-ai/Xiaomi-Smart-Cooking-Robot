package com.kitchenos.connect.control;

import android.app.*;
import android.os.*;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.ui.*;
import com.kitchenos.connect.kitchen.*;
import android.view.*;
import java.util.Locale;

/** Foreground-only, local controls. Merely opening this page performs no root/UART action. */
public final class ControlActivity extends Activity {
    private ControlClient client;
    private TextView status,sensors,live,readiness,events,rootState,runClock;
    private Button root,connect,start,apply,pause,resume,stop,checked;
    private ParameterDial duration,temperature,stirring;
    private Button direction;
    private boolean reverse;
    private RotaryController knob;
    private ApplianceTools tools;
    private Dialog assistant;
    private AlertDialog confirmation;
    private CheckBox potConfirmation;
    private String confirmAction;
    private boolean foreground,exitRequested,guidedResultSent;
    private int setupStage;
    private boolean connectionDetails;
    private LinearLayout connectionCard;
    private Button guidedDone;private int entryStartCount;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable heartbeat=new Runnable(){public void run(){if(!foreground)return;client.tick();main.postDelayed(this,200);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);client=ControlClient.get(this);if(!recipeOwnerMatches()){finish();return;}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        knob=new RotaryController(this,this::leave);tools=new ApplianceTools(()->new CloudSession(this).account().optString("uid"));entryStartCount=saved==null?client.state.acceptedStarts:saved.getInt("entryStarts",client.state.acceptedStarts);
        LinearLayout page=Ui.page(this);page.addView(control("‹ Zur Küche",this::leave));TextView title=Ui.title(this,getIntent().getBooleanExtra("guided",false)?getIntent().getStringExtra("stepTitle"):"Manuell kochen");title.setTextSize(32);page.addView(title);
        page.addView(Ui.text(this,getIntent().getBooleanExtra("guided",false)?getIntent().getStringExtra("instruction"):"Zeit, Stufe und Temperatur direkt am Gerät einstellen.",23));
        LinearLayout rootCard=Ui.card(this,page);connectionCard=rootCard;rootCard.setVisibility(View.GONE);rootState=Ui.text(this,"",17);rootCard.addView(rootState);
        root=Ui.button(this,"Gerätezugang einrichten",v->client.checkRoot());rootCard.addView(root);
        connect=Ui.button(this,"Steuerung verbinden",v->{if(client.state.busy())client.disconnect();else showDialog(new AlertDialog.Builder(this).setTitle("Maschine verbinden")
            .setMessage("Nur übernehmen, wenn kein Lauf aktiv ist. In der separaten Teststeuerung zuerst Trennen wählen. Die Xiaomi-App wird beim Verbinden pausiert. Ein Kochlauf beginnt erst nach deiner Startbestätigung.")
            .setNegativeButton("Abbrechen",null).setPositiveButton("Verbinden",(d,w)->client.connect()));});rootCard.addView(connect);
        LinearLayout stateCard=Ui.card(this,page);status=Ui.title(this,"Nicht verbunden");stateCard.addView(status);runClock=Ui.text(this,"",48);runClock.setTextColor(Ui.ACCENT);stateCard.addView(runClock);sensors=Ui.text(this,"",18);stateCard.addView(sensors);live=Ui.text(this,"",16);stateCard.addView(live);live.setVisibility(View.GONE);readiness=Ui.text(this,"",18);readiness.setTextColor(Ui.ACCENT);stateCard.addView(readiness);
        checked=Ui.button(this,"Maschinenzustand geprüft",v->showDialog(new AlertDialog.Builder(this).setTitle("Zustand und Fehlerursache geprüft?").setMessage("Diese Bestätigung ist keine Messung. Prüfe vor Ort, dass Motor und Heizung nicht mehr laufen; nötigenfalls die Maschine am Netzschalter sichern. Vor einem weiteren Lauf muss die Ursache des letzten Gerätefehlers behoben sein.").setNegativeButton("Abbrechen",null).setPositiveButton("Geprüft",(d,w)->client.acknowledgePhysicalCheck())));stateCard.addView(checked);
        LinearLayout input=Ui.card(this,page);
        ControlValues prepared=new ControlValues(5,0,5);
        try{prepared=new ControlValues(getIntent().getIntExtra("preparedSeconds",5),getIntent().getIntExtra("preparedTemperature",0),getIntent().getIntExtra("preparedSpeed",5));}catch(IllegalArgumentException ignored){}
        ControlValues original=prepared;
        try{original=new ControlValues(getIntent().getIntExtra("originalSeconds",prepared.seconds),getIntent().getIntExtra("originalTemperature",prepared.temperature),getIntent().getIntExtra("originalSpeed",prepared.speed));}catch(IllegalArgumentException ignored){}
        client.cookingReview.begin(getIntent().getBooleanExtra("guided",false)?original:null,saved!=null&&(saved.getBoolean("modifiedRecipe",true)||client.cookingReview.changed()||saved.getBoolean("hadSubmittedRun",false)));
        if(saved!=null)try{prepared=new ControlValues(saved.getInt("seconds",5),saved.getInt("temperature",0),saved.getInt("speed",5));}catch(IllegalArgumentException ignored){}
        duration=new ParameterDial(this,"Dauer",1,600,1,prepared.seconds);temperature=new ParameterDial(this,"Temperatur",0,100,5,prepared.temperature);stirring=new ParameterDial(this,"Stufe",0,30,5,Math.abs(prepared.speed));reverse=prepared.speed<0;
        LinearLayout circles=new LinearLayout(this);for(ParameterDial dial:new ParameterDial[]{duration,temperature,stirring}){String id=dial==duration?"parameter.duration":dial==temperature?"parameter.temperature":"parameter.speed";dial.setTag(id);dial.onChanged(value->tools.invalidate());knob.add(dial);dial.setOnClickListener(v->knob.edit(dial));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,220),1);circles.addView(dial,lp);}input.addView(circles);AccessorySelection.stirrer(this,false);stirring.ceiling(100);
        direction=control(reverse?"Rühren":"Messern",()->{reverse=!reverse;direction.setText(reverse?"Rühren":"Messern");});input.addView(direction);
        input.addView(Ui.text(this,"Manueller Bereich: Stufe 0–3 · Aus oder 35–100 °C. Bei Stufe 0 eine Temperatur wählen. Die Topferkennung ist noch nicht verifiziert.",15));
        LinearLayout actions=Ui.card(this,page);start=control("Starten",this::beginStart);apply=control("Werte übernehmen",()->act("APPLY"));pause=control("Pause",()->client.pause());resume=control("Fortsetzen",()->act("RESUME"));actions.addView(start);actions.addView(apply);actions.addView(pause);actions.addView(resume);if(getIntent().getBooleanExtra("guided",false)){guidedDone=control("Schritt abgeschlossen · Weiter",()->{if(!canComplete())return;guidedResultSent=true;setResult(RESULT_OK,new android.content.Intent().putExtra("stepId",getIntent().getStringExtra("stepId")).putExtra("ownerUid",getIntent().getStringExtra("ownerUid")).putExtra("programDigest",getIntent().getStringExtra("programDigest")).putExtra("modifiedRecipe",client.cookingReview.changed()));leave();});actions.addView(guidedDone);}
        stop=Ui.button(this,"STOPP",v->client.stop());stop.setTextSize(24);stop.setTypeface(null,Typeface.BOLD);stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffa63038));FrameLayout content=(FrameLayout)((View)page.getParent()).getParent();FrameLayout.LayoutParams stopParams=new FrameLayout.LayoutParams(-1,Ui.dp(this,72),Gravity.BOTTOM);stopParams.setMargins(Ui.dp(this,24),0,Ui.dp(this,24),Ui.dp(this,16));LinearLayout cookingDock=new LinearLayout(this);cookingDock.setTag("assistant.footer");cookingDock.setGravity(Gravity.CENTER_VERTICAL);cookingDock.setBackgroundColor(Ui.BG);Button assistantButton=control("Frag Kalomatik",()->{if(assistant==null||!assistant.isShowing())assistant=CookingAssistant.show(this,tools);});cookingDock.addView(assistantButton,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams stopLp=new LinearLayout.LayoutParams(0,-1,1);stopLp.leftMargin=Ui.dp(this,14);cookingDock.addView(stop,stopLp);content.addView(cookingDock,stopParams);
        page.addView(Ui.text(this,"Beim Verlassen wird die Steuerung getrennt und ein eigener Lauf gestoppt. Ohne Stoppbestätigung den Zustand an der Maschine prüfen. Software-Stopp ersetzt keinen physischen Not-Aus.",15));
        events=Ui.text(this,"",14);events.setVisibility(View.GONE);page.addView(events);
        page.removeView(rootCard);page.removeView(stateCard);page.addView(stateCard,3);page.addView(rootCard);page.addView(control("Geräteverbindung",()->{connectionDetails=!connectionDetails;connectionCard.setVisibility(connectionDetails?View.VISIBLE:View.GONE);}));page.addView(control("Gerätedetails",()->showDialog(new AlertDialog.Builder(this).setTitle("Gerätedetails").setMessage(live.getText()+"\n\n"+client.events()).setNegativeButton("Schließen",null))));knob.reset();register(content);knob.confirmNext(duration,temperature);knob.confirmNext(temperature,stirring);knob.confirmNext(stirring,direction);knob.edit(duration);render();
    }
    private Button control(String label,Runnable action){Button button=Ui.button(this,label,v->action.run());button.setContentDescription(label);button.setTextSize(22);button.setMinHeight(Ui.dp(this,64));android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_selected},com.kitchenos.connect.kitchen.KitViews.shape(this,Ui.CARD,28,Ui.ACCENT));states.addState(new int[]{},com.kitchenos.connect.kitchen.KitViews.shape(this,Ui.CARD,28,Ui.LINE));button.setBackground(states);knob.add(button);return button;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return knob!=null&&knob.dispatch(event)||super.dispatchKeyEvent(event);}
    @Override public boolean onGenericMotionEvent(MotionEvent event){return knob!=null&&knob.motion(event)||super.onGenericMotionEvent(event);}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("hadSubmittedRun",client.state.acceptedStarts>entryStartCount||client.state.pendingAction.equals("START"));state.putInt("entryStarts",entryStartCount);state.putBoolean("modifiedRecipe",client.cookingReview.changed());state.putInt("seconds",duration.value());state.putInt("temperature",temperature.value());state.putInt("speed",stirring.value()*(reverse?-1:1));super.onSaveInstanceState(state);}
    private void register(View view){if(view instanceof Button||view instanceof ParameterDial){knob.add(view);String id=view.getTag() instanceof String?(String)view.getTag():"control."+String.valueOf(view instanceof TextView?((TextView)view).getText():"action");view.setContentDescription(view instanceof ParameterDial?view.getContentDescription():((TextView)view).getText());tools.action(id,view,()->view.performClick());return;}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)register(((ViewGroup)view).getChildAt(i));}
    private void showDialog(AlertDialog.Builder builder){AlertDialog dialog=builder.create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void text(TextView view,String value){if(!view.getText().toString().equals(value))view.setText(value);}
    private String stateName(int value){switch(value){case 0:return "Bereit";case 1:return "Kochen / Mischen";case 2:return "Warmhalten";case 3:return "Pause";case 8:return "Gerätefehler";case 9:return "Deckelprüfung";case 11:return "Fertig";default:return "Gerätezustand "+value;}}
    private void render(){
        if(status==null)return;stirring.ceiling(AccessorySelection.stirrer(this)?40:100);View picker=getWindow().getDecorView().findViewWithTag("control.attachment");if(picker instanceof AccessoryPicker)((AccessoryPicker)picker).refresh();ControlState s=client.state;long now=SystemClock.elapsedRealtime();
        try{org.json.JSONObject context=new org.json.JSONObject(getIntent().getStringExtra("cookingContext")==null?"{}":getIntent().getStringExtra("cookingContext"));context.put("page","cooking").put("connected",s.connected).put("fresh",s.fresh(now)).put("liveState",!s.connected?"Nicht verbunden":s.fresh(now)?stateName(s.state):"Status nicht aktuell").put("liveTemperatureC",s.fresh(now)?s.temperature:org.json.JSONObject.NULL).put("elapsedSeconds",s.fresh(now)?s.elapsed:org.json.JSONObject.NULL);tools.context(context);}catch(org.json.JSONException ignored){}
        text(rootState,s.rootChecking?"Freigabe in Magisk prüfen …":s.rootVerified?"Root-Zugang für KitchenOS bestätigt":"Root-Zugang für KitchenOS noch nicht bestätigt");root.setEnabled(!s.busy()&&!s.rootChecking);
        text(connect,s.closing?"Wird getrennt …":s.connecting?"Verbindung abbrechen":s.connected?"Steuerung trennen":"Maschine verbinden");connect.setEnabled(!s.closing&&!s.rootChecking&&(s.busy()||s.rootVerified&&!s.uncertainRun));
        text(status,s.connected?stateName(s.state)+" · "+s.temperature+" °C":s.closing?"Wird getrennt …":s.connecting?"Wird verbunden …":"Nicht verbunden");
        runClock.setVisibility(s.ownRun?View.VISIBLE:View.GONE);if(s.ownRun)text(runClock,String.format(Locale.GERMAN,"%02d:%02d gelaufen",s.elapsed/60,s.elapsed%60));
        text(sensors,s.statusAt<0?"Deckel und Live-Werte erscheinen nach dem Verbinden.":"Deckel: "+(s.coverVerified&&s.coverOpen>=0?(s.coverOpen==0?"geschlossen":"offen"):"unbestätigt")+"\nTopf: "+(s.potVerified?(s.pot==1?"erkannt":"fehlt"):"vor Start selbst bestätigen")+" · "+s.elapsed+" s");
        text(live,s.connected&&s.statusAt>=0?"Live · "+s.samples+" Antworten · letzte vor "+(now-s.statusAt)+" ms\nMCU "+s.mcuVersion+" · IH "+s.ihVersion+" · Motor "+s.motorVersion:"Keine aktive Steuerverbindung");
        String reason=s.startReason(now);text(readiness,reason.isEmpty()?"Start bereit · Topf im nächsten Dialog bestätigen":reason);
        if(setupStage==1&&!s.rootChecking){if(s.rootVerified){setupStage=2;client.connect();}else setupStage=0;}
        if(setupStage==2){if(s.allows("START",now)){setupStage=0;main.post(()->{if(foreground)act("START");});}else if(!s.busy())setupStage=0;}
        text(start,setupStage>0?"Maschine wird vorbereitet …":!s.rootVerified?"Starten · Gerätezugang prüfen":!s.connected?"Starten · Maschine verbinden":"Starten");
        boolean configured=ControlProfile.supportsLocal(duration.value(),temperature.value(),stirring.value()*(reverse?-1:1));direction.setEnabled(stirring.value()!=0);start.setEnabled(configured&&setupStage==0&&!s.rootChecking&&!s.closing&&!s.ownRun&&!s.uncertainRun&&!s.deviceFaultLatched&&(!s.connected&&!s.connecting||s.allows("START",now)));resume.setEnabled(configured&&s.allows("RESUME",now));apply.setEnabled(configured&&s.allows("APPLY",now));pause.setEnabled(s.allows("PAUSE",now));apply.setVisibility(s.ownRun?View.VISIBLE:View.GONE);pause.setVisibility(s.ownRun&&s.state!=3?View.VISIBLE:View.GONE);resume.setVisibility(s.ownRun&&s.state==3?View.VISIBLE:View.GONE);stop.setEnabled(s.connected&&!s.stopping);checked.setVisibility(s.uncertainRun||s.deviceFaultLatched?android.view.View.VISIBLE:android.view.View.GONE);checked.setEnabled(!s.busy());text(events,client.events());if(guidedDone!=null){guidedDone.setEnabled(canComplete());guidedDone.setVisibility(s.acceptedStarts>entryStartCount&&!s.ownRun?View.VISIBLE:View.GONE);}
        if(confirmation!=null&&confirmation.isShowing())confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(potConfirmation.isChecked()&&s.allows(confirmAction,now));
        if(exitRequested&&!s.busy()){
            exitRequested=false;if(s.uncertainRun)showDialog(new AlertDialog.Builder(this).setTitle("Stopp nicht bestätigt").setMessage("Bitte den Maschinenzustand prüfen und nötigenfalls am Netzschalter sichern.").setPositiveButton("Hier bleiben",null).setNegativeButton("Zur Küche",(d,w)->finish()));else finish();
        }
    }
    private void beginStart(){ControlState s=client.state;if(setupStage>0||s.ownRun||s.uncertainRun||s.deviceFaultLatched)return;if(!s.rootVerified){setupStage=1;client.checkRoot();}else if(!s.connected){setupStage=2;client.connect();}else act("START");render();}
    private boolean canComplete(){ControlState s=client.state;return s.naturalCompletion&&s.acceptedStarts>entryStartCount&&s.idleConfirmed&&!s.ownRun&&!s.uncertainRun&&!s.deviceFaultLatched&&s.error==0;}
    private void act(String action){
        if(confirmation!=null&&confirmation.isShowing())return;
        try{
            if(AccessorySelection.stirrer(this)&&stirring.value()>40)throw new IllegalArgumentException("Mit Rühraufsatz höchstens Stufe 4 wählen.");ControlValues values=new ControlValues(duration.value(),temperature.value(),stirring.value()*(reverse?-1:1));
            if(action.equals("APPLY")){client.execute(action,values,false);return;}
            confirmAction=action;potConfirmation=new CheckBox(this);potConfirmation.setText("Topf korrekt eingesetzt, Füllmarkierung beachtet und Deckel geschlossen");potConfirmation.setTextSize(18);potConfirmation.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12));
            confirmation=new AlertDialog.Builder(this).setTitle(action.equals("START")?"Maschine jetzt starten?":"Lauf fortsetzen?")
                .setMessage(String.format(Locale.GERMAN,"%d Sekunden · Stufe %.1f · %s\nTemperatur: %s\n\nGesamtbudget je Lauf: höchstens 10 Minuten, auch nach Änderungen oder Pausen.\nTopfsensor nicht verifiziert. Einsetzen selbst prüfen und die Maschine beaufsichtigen.",values.seconds,Math.abs(values.speed)/10.0,values.speed<0?"Rückwärts":"Vorwärts",values.temperature==0?"Aus":values.temperature+" °C"))
                .setView(potConfirmation).setNegativeButton("Abbrechen",null).setPositiveButton("Jetzt starten",(d,w)->client.execute(action,values,potConfirmation.isChecked())).create();
            confirmation.setOnShowListener(d->{confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);potConfirmation.setOnCheckedChangeListener((b,on)->render());RotaryDialogs.bind(this,confirmation);});confirmation.setOnDismissListener(d->{confirmation=null;potConfirmation=null;});confirmation.show();
        }catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void leave(){if(!guidedResultSent&&getIntent().getBooleanExtra("guided",false))setResult(RESULT_CANCELED,new android.content.Intent().putExtra("ownerUid",getIntent().getStringExtra("ownerUid")).putExtra("programDigest",getIntent().getStringExtra("programDigest")).putExtra("modifiedRecipe",client.cookingReview.changed()||client.state.ownRun||client.state.pendingAction.equals("START")));if(client.state.busy()){exitRequested=true;client.disconnect();}else finish();}
    @Override public void onBackPressed(){leave();}
    private boolean recipeOwnerMatches(){return !getIntent().getBooleanExtra("privateRecipe",false)||getIntent().getStringExtra("ownerUid")!=null&&getIntent().getStringExtra("ownerUid").equals(new CloudSession(this).account().optString("uid"));}
    @Override protected void onResume(){super.onResume();if(isFinishing()||!recipeOwnerMatches()){finish();return;}foreground=true;client.foreground(true);client.listen(this::render);main.post(heartbeat);}
    @Override protected void onPause(){foreground=false;setupStage=0;main.removeCallbacks(heartbeat);if(confirmation!=null)confirmation.dismiss();if(assistant!=null)assistant.dismiss();client.listen(null);client.foreground(false);super.onPause();}
}

