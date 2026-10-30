package com.kitchenos.connect.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.control.ControlState;
import com.kitchenos.connect.control.ScaleReadingTracker;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.connect.kitchen.KitViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Scale embedded in a guided step, using the host's foreground controller.
 * The host must maintain ControlClient.foreground/tick/listen itself. This view
 * never replaces its listener or issues motor/heater commands. Explicit retry reconnects an idle bridge.
 * Call resume after the host becomes foreground, pause when hidden, and detach
 * when replacing the step. Measurements delivered to the listener are NEW
 * receipts only; old receipts are explicitly displayed as history.
 */
public final class InlineScalePanel extends LinearLayout {
    public interface Listener { void onMeasurements(JSONObject envelope); }
    private final Activity host;
    private final ControlClient client;
    private final RotaryController knob;
    private final String ownerUid,programDigest,stepId,rotaryGroup;
    private final JSONObject step;
    private final JSONArray ingredients;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScaleReadingTracker readings=new ScaleReadingTracker();
    private final LinkedHashMap<String,JSONObject> measured=new LinkedHashMap<>(),historical=new LinkedHashMap<>();
    private final java.util.HashSet<String> addedByHand=new java.util.HashSet<>(),confirmedWithoutMeasurement=new java.util.HashSet<>(),acceptedHistory=new java.util.HashSet<>();
    private final TextView target,value,status,history;
    private final ImageView ingredientPhoto;
    private final LinearLayout ingredientList;
    private final Button tare,confirm,retry;
    private final ArrayList<View> rotaryTargets=new ArrayList<>();
    private final java.util.IdentityHashMap<Button,Boolean> buttonStates=new java.util.IdentityHashMap<>();
    private String checkedSession,feedback="";
    private long checkedEpoch,feedbackUntil;
    private boolean checkedOwner,feedbackNeedsTare;
    private boolean autoZeroPending=true,started;
    private boolean resumed,detached,attempted,checkingRoot,sawBusy,failed,validBinding;
    private long attemptAt=-1;
    private int selected,pendingNext=-1;
    private final Runnable heartbeat=new Runnable(){@Override public void run(){if(!resumed||detached)return;refresh();main.postDelayed(this,200);}};

    public InlineScalePanel(Activity context,ControlClient client,RotaryController knob,String ownerUid,String programDigest,JSONObject step,JSONArray existingMeasurements,Listener listener){
        this(context,client,knob,ownerUid,programDigest,step,existingMeasurements,Collections.emptySet(),false,listener);
    }
    public InlineScalePanel(Activity context,ControlClient client,RotaryController knob,String ownerUid,String programDigest,JSONObject step,JSONArray existingMeasurements,java.util.Set<String> completedIngredients,boolean alreadyStarted,Listener listener){
        super(context);this.host=context;this.client=client;this.knob=knob;this.ownerUid=ownerUid==null?"":ownerUid;this.programDigest=programDigest==null?"":programDigest;this.listener=listener;
        JSONObject copy;try{copy=new JSONObject(step.toString());}catch(Exception e){copy=new JSONObject();}this.step=copy;this.stepId=copy.optString("id");
        JSONArray source=copy.optJSONArray("ingredients");ingredients=source!=null&&source.length()<=80?source:new JSONArray();
        rotaryGroup="inline.scale."+System.identityHashCode(this);
        validBinding=!this.ownerUid.isEmpty()&&this.programDigest.matches("[a-f0-9]{64}")&&!stepId.isEmpty()&&stepId.length()<=80;
        java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<ingredients.length();i++)if(ingredients.optJSONObject(i)==null||ingredients.optJSONObject(i).optString("name").isEmpty()||ingredientId(i).length()>180||!ids.add(ingredientId(i)))validBinding=false;
        if(validBinding&&existingMeasurements!=null)try{JSONArray safe=RecipeWeighingHistory.forStep(existingMeasurements,copy);for(int i=0;i<safe.length();i++){JSONObject receipt=safe.getJSONObject(i);historical.put(receipt.getString("ingredientId"),receipt);}}catch(Exception ignored){}
        for(int i=0;i<ingredients.length();i++)if(completedIngredients.contains(ingredientId(i))){if(!measurable(i))addedByHand.add(ingredientId(i));else if(historical.containsKey(ingredientId(i)))acceptedHistory.add(ingredientId(i));else confirmedWithoutMeasurement.add(ingredientId(i));}
        started=alreadyStarted||!historical.isEmpty()||!addedByHand.isEmpty();autoZeroPending=!started;
        for(int i=0;i<ingredients.length();i++)if(!done(i)){selected=i;break;}
        setOrientation(VERTICAL);setPadding(dp(24),dp(22),dp(24),dp(24));setBackground(KitViews.shape(context,Ui.CARD,28,Ui.LINE));
        TextView heading=KitViews.text(context,"Einwiegen",25,Ui.INK,true);addView(heading);
        LinearLayout selectedIngredient=new LinearLayout(context);selectedIngredient.setGravity(Gravity.CENTER_VERTICAL);selectedIngredient.setPadding(0,dp(16),0,0);
        ingredientPhoto=new ImageView(context);selectedIngredient.addView(ingredientPhoto,new LayoutParams(dp(96),dp(96)));
        target=KitViews.text(context,"",27,Ui.INK,true);LayoutParams targetSize=new LayoutParams(0,-2,1);targetSize.leftMargin=dp(20);selectedIngredient.addView(target,targetSize);addView(selectedIngredient);
        value=KitViews.text(context,"— g",88,Ui.INK,true);value.setGravity(Gravity.CENTER);addView(value,new LayoutParams(-1,dp(126)));
        status=KitViews.text(context,"Waage wird vorbereitet …",21,Ui.MUTED,false);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(58));addView(status,new LayoutParams(-1,-2));
        history=KitViews.text(context,"",20,Ui.MUTED,false);history.setGravity(Gravity.CENTER);addView(history,new LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(context);actions.setPadding(0,dp(16),0,dp(8));tare=button("Tara · 0 g",this::tareWeight);confirm=button("Zutat übernehmen",this::acceptWeight);
        LayoutParams tareSize=new LayoutParams(0,dp(76),1);tareSize.rightMargin=dp(12);actions.addView(tare,tareSize);confirm.setVisibility(GONE);addView(actions);
        retry=button("Waage erneut versuchen",()->{if(!resumed||!ownerMatches())return;interruptReading();feedback="";attempted=false;checkingRoot=sawBusy=failed=false;attemptAt=-1;if(client.state.connected&&!cooking(client.state))client.disconnect();refresh();});retry.setVisibility(GONE);addView(retry,new LayoutParams(-1,dp(72)));
        ingredientList=new LinearLayout(context);ingredientList.setOrientation(VERTICAL);addView(ingredientList);drawIngredients();refresh();
    }

    /** No listener or connection ownership is transferred to the panel. */
    public void resume(){if(detached||resumed)return;resumed=true;checkedSession=null;readings.reset();autoZeroPending=!started;main.removeCallbacks(heartbeat);main.post(heartbeat);}
    /** Suspending display never disconnects or changes an ongoing cook session. */
    public void pause(){resumed=false;main.removeCallbacks(heartbeat);interruptReading();feedback="";}
    public void detach(){pause();detached=true;if(knob!=null)knob.replaceGroup(rotaryGroup,Collections.emptyList());}
    public int selectedIngredient(){return selected;}
    public void selectIngredient(int index){
        if(index<0||index>=ingredients.length()||selected==index||!resumed||!validBinding||!ownerMatches())return;
        long now=SystemClock.elapsedRealtime();readings.observe(client.state,now);
        if(measurable(index)&&!done(index)&&!readings.stable(now)){feedbackNeedsTare=false;feedback="Topf kurz ruhig halten, dann die nächste Zutat auswählen.";feedbackUntil=now+2000;refresh();return;}
        selected=index;pendingNext=-1;feedback="";if(measurable()&&!done(selected))zeroForIngredient(now);drawIngredients();refresh();
    }

    private int dp(int size){return Ui.dp(host,size);}
    private String ingredientId(int index){JSONObject item=ingredients.optJSONObject(index);return stepId+":"+(item==null?"":item.optString("name"));}
    private boolean ownerMatches(){
        // Telemetry must not decrypt the account on every 100 ms status update.
        // Revalidate whenever encrypted session or epoch changes, including logout/login.
        SharedPreferences prefs=host.getSharedPreferences("account",0);
        String session=prefs.getString("session","");long epoch=prefs.getLong("epoch",0);
        if(!session.equals(checkedSession)||epoch!=checkedEpoch){
            boolean matches=ownerUid.equals(new CloudSession(host).account().optString("uid"));
            if(!session.equals(prefs.getString("session",""))||epoch!=prefs.getLong("epoch",0)){checkedSession=null;return false;}
            checkedSession=session;checkedEpoch=epoch;checkedOwner=matches;
        }
        return checkedOwner;
    }
    private JSONObject current(){return ingredients.optJSONObject(selected);}
    private boolean done(int index){String id=ingredientId(index);return measured.containsKey(id)||acceptedHistory.contains(id)||addedByHand.contains(id)||confirmedWithoutMeasurement.contains(id);}
    private boolean allDone(){if(ingredients.length()==0)return false;for(int i=0;i<ingredients.length();i++)if(!done(i))return false;return true;}
    private boolean measurable(){return measurable(selected);}
    private boolean measurable(int index){JSONObject item=ingredients.optJSONObject(index);if(item==null)return false;String unit=item.optString("unit");return "g".equals(unit)||"kg".equals(unit);}
    private Double targetGrams(){JSONObject item=current();if(item==null||!(item.opt("amount") instanceof Number)||!measurable())return null;double amount=item.optDouble("amount",0)*("kg".equals(item.optString("unit"))?1000:1);return Double.isFinite(amount)&&amount>0?amount:null;}
    private String amount(JSONObject item){return item==null||!item.has("amount")||item.isNull("amount")?"Menge nach Rezept":item.optString("amount")+" "+item.optString("unit");}
    private Button button(String text,Runnable action){Button button=Ui.button(host,text,v->action.run());button.setTextSize(23);button.setMinHeight(dp(72));button.setContentDescription(text);return button;}
    private void appearance(Button button,boolean enabled){if(Boolean.valueOf(enabled).equals(buttonStates.get(button)))return;buttonStates.put(button,enabled);button.setEnabled(enabled);button.setBackgroundTintList(ColorStateList.valueOf(enabled?Ui.ACCENT:Ui.SOFT));button.setTextColor(enabled?0xff171716:Ui.MUTED);}
    private static void text(TextView view,CharSequence value){if(!TextUtils.equals(view.getText(),value))view.setText(value);}
    private static void visibility(View view,int value){if(view.getVisibility()!=value)view.setVisibility(value);}
    private void interruptReading(){
        readings.reset();autoZeroPending=!started;
        // A pending spoon-to-grams transition also loses its trusted baseline
        // on interruption. Keep the next selection, but require explicit tare.
        if(pendingNext>=0){selected=pendingNext;pendingNext=-1;drawIngredients();}
    }
    private void zeroForIngredient(long now){
        // An explicit ingredient transition adopts the current stable bowl load.
        // Take the baseline now, before a fast user can add the next ingredient.
        autoZeroPending=!(resumed&&readings.tare(now));
        if(autoZeroPending)readings.reset();else markStarted();
    }
    private boolean advancePending(long now){
        if(pendingNext<0||measurable(pendingNext)&&!readings.stable(now))return false;
        selected=pendingNext;pendingNext=-1;
        if(measurable())zeroForIngredient(now);else autoZeroPending=false;
        return true;
    }
    private void tareWeight(){
        if(!resumed||!validBinding||!ownerMatches())return;
        long now=SystemClock.elapsedRealtime();readings.observe(client.state,now);
        feedbackNeedsTare=readings.tare(now);if(feedbackNeedsTare){autoZeroPending=false;markStarted();}feedback=feedbackNeedsTare?"Waage auf 0 gesetzt. Jetzt Zutat hinzugeben.":"Einen Moment warten, bis das Gewicht ruhig ist. Dann Tara wählen.";
        feedbackUntil=now+1500;refresh();
    }

    private void drawIngredients(){
        ingredientList.removeAllViews();rotaryTargets.clear();rotaryTargets.add(tare);rotaryTargets.add(confirm);rotaryTargets.add(retry);
        for(int i=0;i<ingredients.length();i++){
            JSONObject item=ingredients.optJSONObject(i);if(item==null)continue;final int index=i;JSONObject receipt=measured.get(ingredientId(i));boolean earlier=receipt==null;if(earlier)receipt=historical.get(ingredientId(i));
            LinearLayout row=new LinearLayout(host);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setMinimumHeight(dp(80));
            TextView name=KitViews.text(host,item.optString("name")+(addedByHand.contains(ingredientId(i))?"\nHinzugefügt":receipt==null?"":(earlier?"\nZuletzt eingewogen: ":"\nEingewogen: ")+receipt.optInt("grams")+" g"),22,Ui.INK,true);row.addView(name,new LayoutParams(0,-2,1));TextView goal=KitViews.text(host,amount(item),22,Ui.INK,false);row.addView(goal);
            row.setBackground(KitViews.shape(host,selected==i?Ui.SOFT:Ui.CARD,18,selected==i?Ui.ACCENT:Ui.LINE));row.setOnClickListener(v->selectIngredient(index));row.setContentDescription("Einwiegen: "+item.optString("name")+", "+amount(item));LayoutParams rowSize=new LayoutParams(-1,-2);rowSize.topMargin=dp(10);ingredientList.addView(row,rowSize);rotaryTargets.add(row);
        }
        if(knob!=null)knob.replaceGroup(rotaryGroup,new ArrayList<>(rotaryTargets));JSONObject item=current();IngredientPortraits.bind(host,ingredientPhoto,item==null?"":item.optString("name"));
    }
    private JSONArray newReceipts(){JSONArray array=new JSONArray();for(JSONObject row:measured.values())array.put(row);return array;}
    private JSONObject progress()throws org.json.JSONException{return RecipeWeighingHistory.envelope(ownerUid,programDigest,step,newReceipts()).put("confirmedWithoutMeasurement",new JSONArray(confirmedWithoutMeasurement)).put("addedByHand",new JSONArray(addedByHand)).put("weighingStarted",started);}
    private void markStarted(){if(started)return;started=true;try{if(listener!=null)listener.onMeasurements(progress());}catch(org.json.JSONException ignored){}}
    private void acceptWeight(){
        if(!resumed||!validBinding||!ownerMatches()||done(selected)||cooking(client.state))return;
        try{long now=SystemClock.elapsedRealtime();readings.observe(client.state,now);
            boolean weighed=measurable();if(weighed&&readings.canConfirm(now)){JSONObject receipt=readings.confirm(ingredientId(selected),selected,targetGrams(),now,System.currentTimeMillis());measured.put(ingredientId(selected),receipt);}else if(weighed&&readings.hasTare()&&readings.fresh(now)&&targetGrams()!=null&&targetGrams()<=5&&Math.abs(readings.displayedGrams())<=3)confirmedWithoutMeasurement.add(ingredientId(selected));else if(!weighed)addedByHand.add(ingredientId(selected));else throw new IllegalStateException("Gewicht noch nicht bereit");
            started=true;JSONObject envelope=progress();
            for(int n=1;n<ingredients.length();n++){int next=(selected+n)%ingredients.length();if(!done(next)){pendingNext=next;break;}}
            // For a manual addition, get new stable samples after confirmation
            // before zeroing a subsequent gram ingredient.
            if(!weighed&&pendingNext>=0&&measurable(pendingNext)){readings.reset();autoZeroPending=false;}
            advancePending(now);
            feedback="";drawIngredients();refresh();if(listener!=null)listener.onMeasurements(envelope);
        }catch(Exception ignored){feedbackNeedsTare=false;feedback="Bitte Tara wählen und das Gewicht kurz zur Ruhe kommen lassen.";feedbackUntil=SystemClock.elapsedRealtime()+1500;refresh();}
    }
    /** Explicit Continue is the only confirmation; never records an unstable weight. */
    public boolean next(){if(allDone())return true;acceptWeight();return allDone();}
    public View primaryAction(){return tare;}
    private boolean cooking(ControlState state){return state.ownRun||!state.pendingAction.isEmpty()||state.stopping||state.uncertainRun;}
    private void prepare(ControlState state,long now,boolean account){
        if(!resumed||detached||!account||!state.foreground||cooking(state))return;
        // A preceding guided panel may still be closing its bridge. That is not
        // a failed attempt by this step: wait for the old connection to end.
        if(state.closing&&!attempted)return;
        if(state.connected){sawBusy=true;if(!attempted){attempted=true;attemptAt=now;}return;}
        if(failed)return;
        if(!attempted){attempted=true;attemptAt=now;if(state.busy()){sawBusy=true;return;}if(state.rootChecking){checkingRoot=true;return;}if(!state.rootVerified){checkingRoot=true;client.checkRoot();return;}client.connect();sawBusy=state.busy();return;}
        if(checkingRoot&&!state.rootChecking){checkingRoot=false;if(state.rootVerified&&!state.busy()){client.connect();sawBusy=state.busy();}else if(!state.rootVerified)failed=true;}
        if(state.busy())sawBusy=true;else if(sawBusy)failed=true;
        if(attemptAt>=0&&now-attemptAt>35000)failed=true;
    }
    /** May also be called by the host's normal ControlClient change callback. */
    public void refresh(){
        if(detached)return;ControlState state=client.state;boolean account=ownerMatches();long now=SystemClock.elapsedRealtime();prepare(state,now,account);
        if(resumed&&account)readings.observe(state,now);else readings.reset();if(!account){boolean hadReceipts=!measured.isEmpty()||!historical.isEmpty()||!addedByHand.isEmpty();measured.clear();historical.clear();addedByHand.clear();acceptedHistory.clear();pendingNext=-1;if(hadReceipts)drawIngredients();}
        // Do not reveal the next ingredient before its zero is established. In
        // particular, a spoon/piece may be confirmed while the bowl is moving.
        if(pendingNext>=0&&(!state.connected||state.closing||cooking(state)||state.error!=0||state.state!=0&&state.state!=11||Math.abs((long)state.weightRaw)>5000))interruptReading();
        if(resumed&&account&&advancePending(now))drawIngredients();
        if(autoZeroPending&&resumed&&account&&validBinding&&measurable()&&readings.tare(now)){autoZeroPending=false;markStarted();}
        JSONObject item=current();text(target,item==null?"Zutaten einwiegen":item.optString("name")+"\n"+amount(item));JSONObject old=measured.get(ingredientId(selected));boolean earlier=old==null;if(earlier)old=historical.get(ingredientId(selected));text(history,old==null?"":(earlier?"Zuletzt eingewogen: ":"Eingewogen: ")+old.optInt("grams")+" g");visibility(history,old==null?GONE:VISIBLE);
        boolean finished=allDone(),needsScale=measurable()||pendingNext>=0&&measurable(pendingNext),fresh=account&&resumed&&readings.fresh(now),stable=fresh&&readings.stable(now),accept=account&&resumed&&validBinding&&!done(selected)&&!cooking(state)&&(!measurable()||stable&&readings.canConfirm(now));
        if(fresh)failed=false;
        boolean showFeedback=!feedback.isEmpty()&&now<feedbackUntil&&(!feedbackNeedsTare||state.connected&&!state.closing&&readings.hasTare());
        appearance(tare,stable&&measurable()&&!finished);appearance(confirm,accept);text(confirm,finished?"Alles hinzugefügt":done(selected)?"Bereits übernommen":!measurable()?"Zutat hinzugefügt":accept?readings.stableGrams()+" g übernehmen":"Zutat übernehmen");if(!TextUtils.equals(confirm.getContentDescription(),confirm.getText()))confirm.setContentDescription(confirm.getText());text(value,finished?"Fertig":!measurable()?"":fresh&&readings.hasTare()?readings.displayedGrams()+" g":"— g");
        if(finished&&account&&resumed)text(status,"Alles eingewogen.");
        else if(!account)text(status,"Das Konto hat sich geändert. Bitte das Rezept erneut öffnen.");
        else if(account&&cooking(state))text(status,state.uncertainRun?"Maschinenzustand prüfen. Wiegen ist gerade nicht verfügbar.":"Wiegen ist möglich, sobald die Maschine wieder stillsteht.");
        else if(needsScale&&state.connected&&Math.abs((long)state.weightRaw)>5000)text(status,"Zu viel Gewicht. Bitte den Topf entlasten.");
        else if(needsScale&&state.connected&&state.error!=0&&state.samples>=3)text(status,"Bitte Topf und Maschine prüfen. Die Waage ist gerade nicht verfügbar.");
        else if(needsScale&&state.connected&&(state.state!=0&&state.state!=11)&&state.samples>=3)text(status,"Wiegen ist möglich, sobald die Maschine stillsteht.");
        else if(pendingNext>=0&&account&&resumed&&(failed||state.connected&&attemptAt>=0&&now-attemptAt>10000&&!fresh)){failed=true;text(status,"Für die nächste Zutat die Waage erneut versuchen.");}
        else if(pendingNext>=0&&account&&resumed)text(status,"Hinzugefügt · Topf kurz ruhig halten, die nächste Zutat wird vorbereitet …");
        else if(account&&resumed&&!measurable())text(status,"Menge nach Rezept hinzugeben, dann Weiter.");
        else if(fresh){failed=false;if(showFeedback)text(status,feedback);else if(done(selected))text(status,"Bereits übernommen · nächste Zutat auswählen.");else if(!readings.hasTare())text(status,autoZeroPending?"Waage stellt sich auf 0 · kurz warten …":"Wiegen unterbrochen. Unbestätigte Zutat herausnehmen, Tara wählen und erneut einwiegen.");else if(!stable)text(status,"Gewicht kommt zur Ruhe …");else if(accept)text(status,"Passt die Menge? Weiter zum nächsten Schritt.");else text(status,"Zutat hinzugeben · dann Weiter.");}
        else if(showFeedback)text(status,feedback);
        else if(failed||state.connected&&attemptAt>=0&&now-attemptAt>10000){failed=true;text(status,state.userIssue.isEmpty()?"Die Waage antwortet gerade nicht. Bitte erneut versuchen.":state.userIssue);}
        else text(status,state.connected?"Gewicht wird erfasst …":"Waage wird bereitgemacht …");
        visibility(retry,resumed&&account&&failed&&!finished&&needsScale&&!cooking(state)&&!state.closing?VISIBLE:GONE);appearance(retry,true);
    }
}
