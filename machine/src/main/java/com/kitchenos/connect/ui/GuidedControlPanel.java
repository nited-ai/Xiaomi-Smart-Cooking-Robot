package com.kitchenos.connect.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.control.ControlState;
import com.kitchenos.connect.control.ControlValues;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.connect.kitchen.CookingAssistant;
import com.kitchenos.connect.kitchen.KitViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/**
 * Inline foreground controls for ONE recipe step. The local start press authorizes
 * its displayed values; connection and telemetry readiness need no second confirmation.
 *
 * Host contract: dispose the previous panel BEFORE replacing a step; call resume/onResume,
 * pause/onPause, dispose/onDestroy and before leaving the step. Do not register this panel's
 * hardware buttons as MCP actions. Add knobTargets() to the host's rotary group instead.
 * Move actionView() from its initial parent into a fixed, non-scrolling footer so all run
 * actions and STOPP remain visible. Read modifiedRecipe() before disposing/completing.
 * The host may preserve prior modification with markPreviouslyModified(true).
 */
public final class GuidedControlPanel {
    private final Activity activity;
    private final ControlClient client;
    private final ParameterDial duration,temperature,speed;
    private final BooleanSupplier reverse;
    private final ControlValues original;
    private boolean lidless;
    private final ApplianceTools tools;
    private final Runnable onCompleted;
    private final String owner;
    private CookSession session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<View> targets=new ArrayList<>();
    private final LinearLayout root,actions,dock;
    private final TextView status,live;
    private final LinearLayout corrections;private final Button heatOff,lowerSpeed,shortTime,forwardBlade,attachmentSpeed;
    private final Button start,apply,pauseRun,resumeRun,stop,done,check;
    private boolean resumed,disposed,rendering,modified,locallyStopped,compactHome,reviewedComplete;
    private final ControlClient.Listener stateChanges=this::render;
    private int setupStage;private int attachmentMax=100;
    private AlertDialog confirmation;
    private ControlValues queuedValues;
    private ControlValues queuedApply;private boolean queuedApplyTime;private long applyRequestedAt;
    private long requestedAt;
    private long appliedTimeRevision,pendingTimeRevision=-1,queuedTimeRevision,queuedStartTimeRevision;private long pendingTimeCommand,pendingTimeAckStatus=-1;
    private String startIssue="",renderedConfiguration="";
    private String reportedHomeAction="";
    private String checkedSession;
    private long checkedEpoch;
    private boolean checkedOwner;
    private AlertDialog inspection;
    private String confirmAction="";
    private String functionTitle="";
    private java.util.function.Consumer<View> primaryChanged;private View lastPrimary;
    private Runnable startHint;
    private BooleanSupplier homeStirrer=()->false;
    private BooleanSupplier homeCupReady=()->false;
    private Runnable homeCupConfirm=()->{};
    private long nextApplyAt;private int lidShownSeconds=-1;
    private Runnable homeStartCue=()->{};
    public void onHomeStartCue(Runnable action){homeStartCue=action;}
    private java.util.function.Consumer<Boolean> homeCupPrompt=required->{};
    public interface HomePrimary {void show(String label,boolean enabled,boolean stopping);}
    private HomePrimary homePrimary;private HomeFunctionProfile homeFunction=HomeFunctionProfile.get("manual");
    public void homePresentation(HomePrimary presentation){homePrimary=presentation;}
    public void homeFunction(HomeFunctionProfile profile){homeFunction=profile;lidless=profile.id.equals("lidless");functionTitle=profile.title;awaitingHomeCup=false;startIssue="";render();}
    public void pressHomeKnob(){
        if(!compactHome||!localReady())return;
        client.drainReceived();
        ControlState s=client.state;
        if(client.lidInterruption.active()&&!s.ownRun&&!s.stopping){continueAfterLid();return;}
        if(client.program.active()){stopLocally();return;}
        if(s.ownRun||s.stopping||s.pendingAction.equals("START")||s.uncertainRun||s.deviceFaultLatched||s.potCheck.active()||s.pendingAction.equals("CHECK_POT")){performHomePrimaryAction();return;}
        if(setupStage>0)return;
        try{values();}catch(IllegalArgumentException error){startIssue=error.getMessage();message(startIssue);render();return;}
        // A foreground view may outlive a connection after a Home rebuild. This
        // explicit press restores only observation; no values/start are queued.
        if(!s.foreground)client.foreground(true);
        HomeStartGate.Action gate=HomeStartGate.next(s,SystemClock.elapsedRealtime(),lidless);
        if(gate!=HomeStartGate.Action.READY){
            awaitingHomeCup=false;
            if(gate==HomeStartGate.Action.ROOT)client.checkRoot();
            else if(gate==HomeStartGate.Action.CONNECT)client.connect();
            startIssue=gate==HomeStartGate.Action.ROOT||gate==HomeStartGate.Action.CONNECT||gate==HomeStartGate.Action.WAIT?"Verbindung wird vorbereitet. Danach den Drehknopf erneut drücken.":s.startReason(SystemClock.elapsedRealtime(),lidless);
            android.util.Log.i("KitchenOSKnob","Start noch nicht bereit: "+startIssue+"; statusAgeMs="+(SystemClock.elapsedRealtime()-s.statusAt)+"; infoAgeMs="+(SystemClock.elapsedRealtime()-s.infoAt)+"; samples="+s.samples);
            if(!startIssue.isEmpty())message(startIssue);render();return;
        }
        awaitingHomeCup=false;confirmHomeSpeedSelection();
    }

    public void cancelHomeCup(){if(!localReady()||client.program.active()||client.state.ownRun||setupStage>0||client.state.pendingAction.equals("START"))return;awaitingHomeCup=false;startIssue="";homeCupPrompt.accept(false);render();}
    private View homeCupAction;private boolean awaitingHomeCup;private Boolean homeStoppingStyle;
    private View nextAfterStop;
    private Dialog assistant;

    private final Runnable heartbeat=new Runnable(){@Override public void run(){
        if(!resumed||disposed)return;
        if(!sameOwner()){pause();return;}
        // A live controller heartbeat must also maintain its visible controls.
        // An unrelated screen replacing the legacy listener cannot freeze this panel.
        render();client.tick();
        if(resumed&&!disposed)main.postDelayed(this,200);
    }};

    public GuidedControlPanel(Activity activity,LinearLayout parent,ParameterDial duration,
            ParameterDial temperature,ParameterDial speed,BooleanSupplier reverse,
            ControlValues original,ApplianceTools tools,Runnable onCompleted){
        this(activity,parent,duration,temperature,speed,reverse,original,tools,onCompleted,false);
    }
    public GuidedControlPanel(Activity activity,LinearLayout parent,ParameterDial duration,
            ParameterDial temperature,ParameterDial speed,BooleanSupplier reverse,
            ControlValues original,ApplianceTools tools,Runnable onCompleted,boolean lidless){
        if(activity==null||parent==null||duration==null||temperature==null||speed==null
                ||reverse==null||original==null||tools==null||onCompleted==null)
            throw new IllegalArgumentException("Ein vollständiger Kochschritt ist erforderlich");
        this.activity=activity;this.duration=duration;this.temperature=temperature;this.speed=speed;
        this.reverse=reverse;this.original=original;this.tools=tools;this.onCompleted=onCompleted;this.lidless=lidless;appliedTimeRevision=duration.changeRevision();
        owner=new CloudSession(activity).account().optString("uid");client=ControlClient.get(activity);
        session=CookSession.open(owner,UUID.randomUUID().toString(),"anonymous","step",client);
        root=Ui.column(activity);root.setPadding(0,Ui.dp(activity,12),0,0);parent.addView(root);
        status=KitViews.text(activity,"Bereit für diesen Schritt",22,Ui.INK,true);root.addView(status);
        live=KitViews.text(activity,"",18,Ui.MUTED,false);root.addView(live);
        corrections=new LinearLayout(activity);root.addView(corrections);
        attachmentSpeed=correction("Stufe 4 wählen","control.fix.attachment",()->speed.value(40));
        heatOff=correction("Heizung aus","control.fix.heat",()->temperature.value(0));
        lowerSpeed=correction("Stufe 3 wählen","control.fix.speed",()->speed.value(30));
        shortTime=correction("5 Minuten wählen","control.fix.time",()->duration.value(com.kitchenos.connect.control.ControlProfile.maxSeconds(com.kitchenos.connect.control.ControlProfile.MOTOR)));
        forwardBlade=correction("Messer wählen","control.fix.direction",()->{View direction=((View)root.getParent()).findViewWithTag("direction-label");if(direction!=null&&direction.isEnabled())direction.performClick();});
        actions=Ui.column(activity);actions.setBackgroundColor(Ui.BG);root.addView(actions);
        start=button("Schritt starten",this::beginStart);start.setBackground(ControlFocus.background(activity,Ui.ACCENT));start.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Ui.MUTED,0xff171716}));LinearLayout.LayoutParams primarySize=new LinearLayout.LayoutParams(-1,Ui.dp(activity,86));primarySize.setMargins(Ui.dp(activity,54),0,Ui.dp(activity,54),0);actions.addView(start,primarySize);
        LinearLayout running=new LinearLayout(activity);
        pauseRun=button("Pause",()->{if(localReady())client.pause();});
        resumeRun=button("Weiterkochen",()->{boolean explicit=duration.changeRevision()!=appliedTimeRevision;if(!explicit){int remaining=client.state.displayRemainingSeconds();if(remaining<=0){message(remaining==0?"Die Restzeit ist beendet. Wähle eine neue Zeit oder stoppe den Schritt.":"Die Restzeit ist noch nicht bekannt. Bitte Live-Werte abwarten.");return;}duration.seedValue(remaining);}confirm("RESUME");});
        apply=button("Neue Werte übernehmen",()->{
            if(!localReady()||!session.hasRun())return;
            try{client.execute("APPLY",values(),false);}catch(IllegalArgumentException e){message(e.getMessage());}
            render();
        });
        running.addView(pauseRun,new LinearLayout.LayoutParams(0,-2,1));
        running.addView(resumeRun,new LinearLayout.LayoutParams(0,-2,1));
        running.addView(apply,new LinearLayout.LayoutParams(0,-2,1));actions.addView(running);
        done=button("Schritt fertig · Weiter",()->{
            if(!canComplete())return;
            session.markModified(modifiedRecipe());if(session.deliver())onCompleted.run();
        });actions.addView(done);
        check=button("Maschinenzustand prüfen",this::checkPhysicalState);actions.addView(check);
        dock=new LinearLayout(activity);
        dock.setBackgroundColor(Ui.BG);dock.setPadding(0,Ui.dp(activity,10),0,Ui.dp(activity,8));
        stop=button("STOPP",this::stopLocally);stop.setTag("control.stop");
        stop.setTextColor(0xffffffff);stop.setBackground(ControlFocus.background(activity,0xffa63038));
        LinearLayout.LayoutParams stopSize=new LinearLayout.LayoutParams(-1,Ui.dp(activity,86));stopSize.setMargins(Ui.dp(activity,54),0,Ui.dp(activity,54),0);dock.addView(stop,stopSize);actions.addView(dock);
        render();
    }

    private Button correction(String label,String tag,Runnable edit){Button view=Ui.button(activity,label,v->{if(!localReady()||client.state.ownRun)return;startIssue="";edit.run();render();});view.setTag(tag);view.setTextSize(18);view.setMinHeight(Ui.dp(activity,54));view.setBackground(ControlFocus.background(activity,Ui.SOFT));view.setDefaultFocusHighlightEnabled(false);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,Ui.dp(activity,58),1);size.rightMargin=Ui.dp(activity,8);corrections.addView(view,size);targets.add(view);return view;}
    private Button button(String label,Runnable action){
        Button view=Ui.button(activity,label,v->action.run());view.setTextSize(25);
        view.setMinHeight(Ui.dp(activity,82));view.setContentDescription(label);view.setDefaultFocusHighlightEnabled(false);view.setBackground(ControlFocus.background(activity,Ui.CARD));targets.add(view);return view;
    }
    public List<View> knobTargets(){return Collections.unmodifiableList(targets);}
    public LinearLayout view(){return root;}
    /** Detach from view() and attach to the host's fixed footer, outside its ScrollView. */
    public LinearLayout actionView(){return actions;}
    public LinearLayout dock(){return dock;}
    public boolean modifiedRecipe(){return modified||session.modified()||session.acceptsRunMetadata(client.state.acceptedStarts)&&client.cookingReview.changed();}
    public void markPreviouslyModified(boolean value){modified|=value;session.markModified(value);}
    /** Optional function/manual presentation. Does not alter command or completion guards. */
    public void setFunctionTitle(String title){
        String value=title==null?"":title.trim();functionTitle=value.length()>160?value.substring(0,160):value;render();
    }
    public void compactHome(){compactHome=true;render();}
    /** Visual preparation cue only; it does not approve a start or change hardware guards. */
    public void onStartHint(Runnable hint){startHint=hint;}
    public boolean canComplete(){
        observeSession();ControlState state=client.state;
        return localReady()&&session.canComplete()&&!state.ownRun&&!state.uncertainRun&&!state.deviceFaultLatched&&state.error==0;
    }
    public boolean hasRun(){return session.hasRun();}
    public void onPrimaryChanged(java.util.function.Consumer<View> listener){primaryChanged=listener;lastPrimary=primaryAction();}
    public View primaryAction(){if(compactHome&&homeCupAction!=null)return homeCupAction;if(compactHome&&!client.state.ownRun&&!client.state.uncertainRun&&!client.state.deviceFaultLatched)return awaitingHomeCup&&homeCupAction!=null?homeCupAction:speed;return client.state.ownRun||client.state.stopping||client.state.uncertainRun||client.state.deviceFaultLatched?stop:nextAfterStop!=null&&(canComplete()||canMoveOn()||canReviewForward())?nextAfterStop:canComplete()?done:start;}
    /** Same horizontal navigation as all other guided steps. */
    public void navigation(View previous,View next){
        // Keep the reason for a blocked start readable beside its action, even on long steps.
        root.removeView(status);root.removeView(live);actions.addView(status,0);actions.addView(live,1);
        live.setMinHeight(Ui.dp(activity,58));live.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        // One fixed row: Back | Start/Stop | Next. State changes never reflow it.
        LinearLayout primary=new LinearLayout(activity);primary.setTag("control.navigation");primary.setGravity(android.view.Gravity.CENTER_VERTICAL);
        primary.addView(previous,new LinearLayout.LayoutParams(Ui.dp(activity,140),Ui.dp(activity,86)));
        android.widget.FrameLayout runSlot=new android.widget.FrameLayout(activity);runSlot.setTag("control.run-slot");
        for(View item:new View[]{start,stop,resumeRun,check}){((android.view.ViewGroup)item.getParent()).removeView(item);runSlot.addView(item,new android.widget.FrameLayout.LayoutParams(-1,-1));}
        LinearLayout.LayoutParams runSize=new LinearLayout.LayoutParams(0,Ui.dp(activity,86),1);runSize.leftMargin=Ui.dp(activity,10);primary.addView(runSlot,runSize);
        ((android.view.ViewGroup)done.getParent()).removeView(done);done.setVisibility(View.GONE);
        nextAfterStop=next;next.setContentDescription("Weiter");if(next instanceof TextView)((TextView)next).setText("Weiter");
        LinearLayout.LayoutParams nextSize=new LinearLayout.LayoutParams(0,Ui.dp(activity,86),1);nextSize.leftMargin=Ui.dp(activity,10);primary.addView(next,nextSize);actions.addView(primary);render();
    }
    public void reviewCompleted(){reviewedComplete=true;render();}
    private boolean canReviewForward(){ControlState s=client.state;return reviewedComplete&&localReady()&&setupStage==0&&!s.rootChecking&&!s.connecting&&!s.closing&&!s.ownRun&&!s.stopping&&s.pendingAction.isEmpty()&&!s.uncertainRun&&!s.deviceFaultLatched&&(!s.connected||s.error==0&&s.idleConfirmed&&s.fresh(SystemClock.elapsedRealtime()));}
    public boolean forward(){if(canComplete()){done.performClick();return true;}if(canMoveOn()){moveOn();return true;}if(canReviewForward()){onCompleted.run();return true;}return false;}
    public boolean canMoveOn(){ControlState s=client.state;return localReady()&&locallyStopped&&s.connected&&s.idleConfirmed&&!s.ownRun&&!s.stopping&&!s.closing&&s.pendingAction.isEmpty()&&!s.uncertainRun&&!s.deviceFaultLatched&&s.error==0;}
    public boolean pendingStart(){return setupStage>0||awaitingHomeCup||client.state.pendingAction.equals("START");}
    public boolean homePreparationInFlight(){return setupStage>0;}
    public void moveOn(){if(!canMoveOn())return;modified=true;session.markModified(true);onCompleted.run();}
    /** Explicit physical confirmation after editing speed authorizes the displayed run. */
    public void confirmSpeed(){if(client.state.ownRun){if(client.state.state==3&&resumeRun.isEnabled())resumeRun.performClick();else confirmValues(false);}else if(compactHome||locallyStopped)beginStart();else if(start.isShown()&&start.isEnabled())start.performClick();}
    public void homeAttachment(BooleanSupplier selected){homeStirrer=selected;readAttachmentChoice();render();}
    public void homeCup(BooleanSupplier ready,Runnable confirm,View action,java.util.function.Consumer<Boolean> prompt){homeCupReady=ready;homeCupConfirm=confirm;homeCupAction=action;homeCupPrompt=prompt;render();}
    /** One visible Home action delegates to the existing guarded controller paths. */
    public void performHomePrimaryAction(){
        if(!compactHome||!localReady())return;
        ControlState s=client.state;
        if(client.lidInterruption.active()&&!s.ownRun&&!s.stopping){continueAfterLid();return;}
        if(s.recovering||!s.ownRun&&!s.stopping&&(s.uncertainRun||s.deviceFaultLatched))return;
        if(client.program.active()||s.ownRun||s.stopping||s.pendingAction.equals("START")||s.uncertainRun||s.deviceFaultLatched||s.potCheck.active()||s.pendingAction.equals("CHECK_POT")){stopLocally();return;}
        confirmHomeCupAndStart();
    }
    /** A second physical press (or cup tap) confirms placement and authorizes current values. */
    public void confirmHomeCupAndStart(){
        if(!compactHome)return;
        if(!awaitingHomeCup){confirmSpeed();return;}
        if(!localReady()||!client.state.allows("START",SystemClock.elapsedRealtime(),lidless)){awaitingHomeCup=false;render();return;}
        try{values();}catch(IllegalArgumentException error){awaitingHomeCup=false;startIssue=error.getMessage();render();return;}
        homeCupConfirm.run();confirmHomeSpeedSelection();
    }
    /** A deliberate touch release authorizes only an immediately ready start.
     * A blocked release never arms a future start on a lid/status/connection change. */
    public void confirmHomeSpeedSelection(){
        if(!compactHome||!localReady()||setupStage!=0||client.state.ownRun)return;
        client.drainReceived();
        long now=SystemClock.elapsedRealtime();ControlState s=client.state;ControlValues chosen;
        try{chosen=values();}catch(IllegalArgumentException e){awaitingHomeCup=false;startIssue=e.getMessage();render();return;}
        if(!s.allows("START",now,lidless)){
            awaitingHomeCup=false;startIssue=s.startReason(now,lidless);if(startIssue.isEmpty())startIssue="Die Maschine ist noch nicht startbereit.";render();return;
        }
        // The explicit Home start gesture now replaces the extra cup dialog.
        // Actual device readiness, lid interlocks and native limits are still mandatory.
        awaitingHomeCup=false;
        // Pot presence is normally inferred from the verified cover interlock on
        // firmware without a separate pot mapping; never invent a pot sensor.
        queuedStartTimeRevision=duration.changeRevision();startIssue="";s.userIssue="";
        if(startHint!=null)startHint.run();submitLocal("START",chosen);
    }

    private void continueAfterLid(){
        if(!localReady()||!client.lidInterruption.active())return;
        try{int previous=client.state.acceptedStarts;long revision=duration.changeRevision();
            if(client.continueAfterLid(values())){session.submitted(previous);timeSubmitted(revision);homeStartCue.run();startIssue="";}
        }catch(IllegalArgumentException e){message(e.getMessage());}
        render();
    }
    /** Live Home edits coalesce behind the current ACK, without a confirming click. */
    public void liveValueChanged(boolean time){if(client.state.ownRun&&!client.state.stopping&&!client.lidInterruption.active())confirmValues(time);}
    public void confirmValues(){confirmValues(true);}
    public void confirmValues(boolean replaceTime){if(client.program.active())return;if(!localReady()||!session.hasRun()||!session.ownsCurrentRun()||session.interrupted()||!client.state.ownRun)return;try{if(replaceTime)duration.noteUserChoice();queuedApply=values();queuedApplyTime|=replaceTime;if(queuedApplyTime)queuedTimeRevision=duration.changeRevision();applyRequestedAt=SystemClock.elapsedRealtime();}catch(IllegalArgumentException error){message(error.getMessage());}render();}
    private void progressApply(long now){if(queuedApply==null)return;if(!localReady()||!client.state.ownRun||client.state.stopping||client.lidInterruption.active()||session.interrupted()){queuedApply=null;queuedApplyTime=false;return;}if(now-applyRequestedAt>3000){queuedApply=null;queuedApplyTime=false;message("Die Änderung ist noch nicht bestätigt. Bitte den Wert erneut einstellen.");return;}if(now<nextApplyAt)return;if(client.state.allows("APPLY",now,lidless)){nextApplyAt=now+100;ControlValues next=queuedApply;boolean time=queuedApplyTime;long revision=queuedTimeRevision;queuedApply=null;queuedApplyTime=false;if(!client.execute(time?"APPLY":"APPLY_SETTINGS",next,false))message(client.state.userIssue.isEmpty()?"Diese Änderung ist gerade nicht möglich.":client.state.userIssue);else if(time)timeSubmitted(revision);}}
    private void stopLocally(){
        // STOP owns this transition. Capturing the displayed remainder is not a
        // user time edit and must not flush an older live change before STOP.
        queuedApply=null;queuedApplyTime=false;
        if(client.program.active()){client.stop();locallyStopped=true;render();return;}
        if(!localReady()||!client.state.connected||client.state.stopping)return;
        if(session.ownsCurrentRun()&&client.state.ownRun){if(!duration.isEditing()&&client.state.remainingSeconds>0)duration.seedValue(compactHome&&homeFunction.id.equals("eggs")&&client.eggCooking.active()?client.eggCooking.remainingSeconds(SystemClock.elapsedRealtime()):client.state.displayRemainingSeconds());locallyStopped=true;session.stopped();modified=true;}
        client.stop();render();
    }

    /** Bind before any start. Preserve cookId on back/forward; use a NEW UUID for a new cook. */
    public void bindSession(String cookId,String programDigest,String stepId){
        UUID.fromString(cookId);
        if(!programDigest.matches("[a-f0-9]{64}")||stepId==null||stepId.isEmpty()||stepId.length()>160||stepId.indexOf('\n')>=0)throw new IllegalArgumentException("Ungültiger Kochschrittbezug");
        if(session.hasRun())throw new IllegalStateException("Ein laufender Kochschritt kann nicht neu zugeordnet werden");
        session=CookSession.open(owner,cookId,programDigest,stepId,client);session.markModified(modified);render();
    }
    public Bundle snapshot(){
        observeSession();session.markModified(modifiedRecipe());CookSession.Snapshot state=session.snapshot();Bundle saved=new Bundle();saved.putBoolean("locallyStopped",locallyStopped);
        saved.putString("process",state.process);saved.putString("owner",state.owner);saved.putString("cookId",state.cookId);saved.putString("digest",state.digest);saved.putString("stepId",state.stepId);saved.putString("runToken",state.runToken);saved.putInt("entryStarts",state.entryStarts);
        saved.putBoolean("submitted",state.submitted);saved.putBoolean("accepted",state.accepted);saved.putBoolean("idleReceipt",state.idleReceipt);saved.putBoolean("interrupted",state.interrupted);saved.putBoolean("modified",state.modified);saved.putBoolean("delivered",state.delivered);return saved;
    }
    /** Call after bindSession. False indicates an interrupted/foreign-process receipt, never an auto restart. */
    public boolean restore(Bundle saved){
        if(saved==null||!sameOwner())return false;locallyStopped=saved.getBoolean("locallyStopped",false);
        boolean restored=session.restore(new CookSession.Snapshot(saved.getString("process",""),saved.getString("owner",""),saved.getString("cookId",""),saved.getString("digest",""),saved.getString("stepId",""),saved.getString("runToken",""),saved.getInt("entryStarts",0),saved.getBoolean("submitted"),saved.getBoolean("accepted"),saved.getBoolean("idleReceipt"),saved.getBoolean("interrupted"),saved.getBoolean("modified"),saved.getBoolean("delivered")));
        modified|=session.modified();render();return restored;
    }
    private void observeSession(){
        ControlState state=client.state;if(session.acceptsRunMetadata(state.acceptedStarts)&&client.cookingReview.stopped()){locallyStopped=true;session.stopped();modified=true;}if(session.acceptsRunMetadata(state.acceptedStarts))session.markModified(client.cookingReview.changed());
        session.observe(state.acceptedStarts,state.idleConfirmed,state.ownRun,state.pendingAction.equals("START"),state.uncertainRun,state.deviceFaultLatched,state.error,state.naturalCompletion);
    }

    public void resume(){
        if(disposed||resumed)return;
        checkedSession=null;
        if(!sameOwner())return;
        resumed=true;client.addObserver(stateChanges);client.foreground(true);render();main.post(heartbeat);
    }
    public void pause(){
        if(!resumed)return;
        boolean awaitingIdentity=setupStage==1&&queuedValues!=null&&client.state.rootChecking&&!client.state.busy()&&!session.hasRun()&&sameOwner()&&SystemClock.elapsedRealtime()-requestedAt<30000;
        observeSession();session.markModified(modifiedRecipe());session.leaving();modified|=session.modified();
        resumed=false;awaitingHomeCup=false;homeCupPrompt.accept(false);if(!awaitingIdentity){setupStage=0;queuedValues=null;}queuedApply=null;queuedApplyTime=false;main.removeCallbacksAndMessages(null);dismissDialogs();
        client.removeObserver(stateChanges);client.foreground(false);
    }
    public void dispose(){if(disposed)return;pause();setupStage=0;queuedValues=null;disposed=true;main.removeCallbacksAndMessages(null);dismissDialogs();}
    private void dismissDialogs(){if(confirmation!=null)confirmation.dismiss();if(inspection!=null)inspection.dismiss();if(assistant!=null)assistant.dismiss();}
    private boolean sameOwner(){
        // A render runs several guards for every telemetry sample. Repeated Keystore
        // decryptions here delayed both the sample and the foreground heartbeat.
        // Revalidate on session replacement, logout/login epoch changes and resume.
        SharedPreferences prefs=activity.getSharedPreferences("account",0);
        String session=prefs.getString("session","");long epoch=prefs.getLong("epoch",0);
        if(!session.equals(checkedSession)||epoch!=checkedEpoch){
            boolean matches=owner.equals(new CloudSession(activity).account().optString("uid"));
            if(!session.equals(prefs.getString("session",""))||epoch!=prefs.getLong("epoch",0)){
                checkedSession=null;return false;
            }
            checkedSession=session;checkedEpoch=epoch;checkedOwner=matches;
        }
        return checkedOwner;
    }
    private boolean localReady(){return resumed&&!disposed&&sameOwner()&&!activity.isFinishing();}
    public void attachmentChoice(boolean ignored,int maximum){speedLimit(maximum);}
    public void speedLimit(int maximum){int next=Math.min(compactHome&&homeFunction.id.equals("turbo")?120:100,maximum);if(next!=attachmentMax)startIssue="";attachmentMax=next;if(compactHome)attachmentMax=Math.min(attachmentMax,homeFunction.maxSpeed);speed.ceiling(attachmentMax);render();}
    public void requestStop(){stopLocally();}
    private void readAttachmentChoice(){int bladeMax=compactHome&&homeFunction.id.equals("turbo")?120:100;attachmentMax=Math.min(AccessorySelection.stirrer(activity)?40:bladeMax,compactHome&&homeStirrer.getAsBoolean()?40:bladeMax);if(compactHome)attachmentMax=Math.min(attachmentMax,homeFunction.maxSpeed);speed.ceiling(attachmentMax);}
    private ControlValues values(){readAttachmentChoice();if(compactHome){if(homeFunction.puree()&&client.state.temperature>=60)throw new IllegalArgumentException("Vor dem Pürieren Topf und Inhalt auf unter 60 °C abkühlen lassen.");String issue=homeFunction.issue(duration.value(),temperature.value(),speed.value()*(reverse.getAsBoolean()?-1:1));if(!issue.isEmpty())throw new IllegalArgumentException(issue);}String toolIssue=AccessorySelection.issue(activity,speed.value(),temperature.value());if(!toolIssue.isEmpty())throw new IllegalArgumentException(toolIssue);if(compactHome&&homeStirrer.getAsBoolean()&&(speed.value()>40||speed.value()>0&&!reverse.getAsBoolean()))throw new IllegalArgumentException("Mit ausgewähltem Rühraufsatz rückwärts und höchstens auf Stufe 4 rühren.");if(speed.value()>attachmentMax)throw new IllegalArgumentException(AccessorySelection.LIMIT);if(compactHome&&homeFunction.temperatureOnly()){ControlValues water=ControlValues.water(temperature.value());return homeStirrer.getAsBoolean()||AccessorySelection.stirrer(activity)?water.withoutHeatBoost():water;}if(compactHome&&homeFunction.id.equals("turbo"))return ControlValues.turbo(duration.value(),speed.value());if(compactHome&&homeFunction.id.equals("eggs")){if(client.eggCooking.startedCooking())return ControlValues.eggsRemaining(duration.value(),Math.max(1,duration.value()-client.eggCooking.elapsedSeconds(SystemClock.elapsedRealtime())));return ControlValues.eggs(duration.value());}int boundedSeconds=compactHome&&homeFunction.id.equals("slow")?5:duration.value();ControlValues chosen=new ControlValues(boundedSeconds,temperature.value(),speed.value()*(reverse.getAsBoolean()?-1:1),lidless,compactHome?!homeFunction.id.equals("fry")&&!lidless:functionTitle.equals("Manuell kochen"));boolean ordinaryHeat=compactHome?!homeFunction.automatic()&&!java.util.Arrays.asList("fry","thicken","stir","lidless").contains(homeFunction.id):!lidless;return ordinaryHeat&&chosen.temperature>0&&!homeStirrer.getAsBoolean()&&!AccessorySelection.stirrer(activity)?chosen.boostHeating():chosen;}
    private String configurationIssue(){if(speed.value()>attachmentMax)return AccessorySelection.LIMIT;try{values();return "";}catch(IllegalArgumentException error){
        if(compactHome||functionTitle.equals("Manuell kochen"))return error.getMessage();
        if(speed.value()>30&&temperature.value()>0)return "Stufe "+String.format(Locale.GERMAN,"%.0f",speed.value()/10.0)+" ist derzeit nur ohne Heizung freigegeben. Für "+temperature.value()+" °C wähle höchstens Stufe 3.";
        if(speed.value()>30&&reverse.getAsBoolean())return "Wähle für Stufe 4–10 das Messer vorwärts. Rühren läuft bis Stufe 3.";
        if(speed.value()>30&&duration.value()>com.kitchenos.connect.control.ControlProfile.maxSeconds(com.kitchenos.connect.control.ControlProfile.MOTOR))return "Für diese Messerstufe ist der einzelne Lauf auf 5 Minuten begrenzt. Kürze die Zeit oder wähle höchstens Stufe 3.";
        return error.getMessage();
    }}

    private void beginStart(){
        if(compactHome){confirmHomeSpeedSelection();return;}
        if(!localReady()||setupStage!=0)return;ControlState state=client.state;
        if(state.ownRun||!state.pendingAction.isEmpty()||state.uncertainRun||state.deviceFaultLatched)return;
        try{queuedValues=values();queuedStartTimeRevision=duration.changeRevision();}catch(IllegalArgumentException e){startIssue=e.getMessage();render();return;}
        locallyStopped=false;startIssue="";state.userIssue="";requestedAt=SystemClock.elapsedRealtime();
        if(startHint!=null)startHint.run();
        if(!state.rootVerified){setupStage=1;client.checkRoot();}
        else if(!state.connected){setupStage=2;client.connect();}
        else setupStage=3;
        render();
    }
    /** The visible physical start press authorizes these exact values, with no second dialog. */
    private void confirm(String action){
        if(!localReady()||!session.hasRun())return;
        try{submitLocal(action,values());}catch(IllegalArgumentException e){message(e.getMessage());}
    }
    private boolean submitLocal(String action,ControlValues chosen){
        if(!localReady()||!client.state.allows(action,SystemClock.elapsedRealtime(),lidless)){
            startIssue=client.state.startReason(SystemClock.elapsedRealtime(),lidless);render();return false;
        }
        if(action.equals("START"))client.cookingReview.begin(original,modifiedRecipe());
        int previous=client.state.acceptedStarts;
        boolean sequence=action.equals("START")&&compactHome&&homeFunction.automatic();
        int programSeconds=compactHome&&homeFunction.id.equals("slow")?duration.value():chosen.seconds;
        boolean sent=sequence?client.startProgram(homeFunction.program(programSeconds,chosen.temperature,chosen.speed)):client.execute(action,chosen,true);
        if(sent&&action.equals("START")){reviewedComplete=false;session.submitted(previous);timeSubmitted(queuedStartTimeRevision);if(compactHome)homeStartCue.run();}else if(sent&&action.equals("RESUME"))timeSubmitted(duration.changeRevision());
        if(!sent)startIssue="Der Schritt wurde nicht gestartet. Bitte die Maschinenanzeige prüfen.";
        render();return sent;
    }
    private void timeSubmitted(long revision){pendingTimeRevision=revision;pendingTimeCommand=client.state.commandSequence;pendingTimeAckStatus=-1;}
    private void progressStart(long now){
        if(setupStage==0||!localReady())return;ControlState s=client.state;
        if(now-requestedAt>(setupStage==1?30000:10000)){setupStage=0;queuedValues=null;startIssue="Die Maschine antwortet nicht rechtzeitig. Bitte erneut starten.";main.post(this::render);return;}
        if(setupStage==1&&!s.rootChecking){
            if(!s.rootVerified){setupStage=0;queuedValues=null;startIssue=s.userIssue.isEmpty()?"Die Maschinenverbindung ist nicht verfügbar. Bitte erneut versuchen.":s.userIssue;main.post(this::render);return;}
            setupStage=2;requestedAt=now;main.post(()->{if(localReady()&&setupStage==2){client.connect();render();}});return;
        }
        if(setupStage==2){if(s.connected&&!s.closing){setupStage=3;requestedAt=now;}else if(!s.busy()&&!s.closing){main.post(()->{if(localReady()&&setupStage==2)client.connect();});}return;}
        if(setupStage==3){
            if(s.allows("START",now,lidless)){
                // Consume only at dispatch on this same UI turn. Posting a second turn
                // let fresh telemetry expire and reopened the duplicate-press window.
                ControlValues chosen=queuedValues;setupStage=4;
                boolean sent=chosen!=null&&submitLocal("START",chosen);
                if(!sent&&!s.fresh(SystemClock.elapsedRealtime())&&!s.closing&&!s.ownRun&&s.pendingAction.isEmpty()){
                    setupStage=3;startIssue="";return; // Keep authorization while only telemetry ages.
                }
                setupStage=0;queuedValues=null;main.post(this::render);
            }
            else if(s.fresh(now)){setupStage=0;queuedValues=null;startIssue=s.startReason(now,lidless);android.util.Log.i("KitchenOSControl","Start nicht gesendet: "+startIssue+"; Werte="+duration.value()+"s/"+temperature.value()+"C/"+speed.value()+"; Zustand="+s.state+"; Deckel="+s.coverOpen+"; Topf="+s.pot);main.post(this::render);}
        }
    }

    private void checkPhysicalState(){
        if(!localReady()||!client.state.canInspect(SystemClock.elapsedRealtime(),lidless)||inspection!=null)return;
        inspection=new AlertDialog.Builder(activity).setTitle("Maschinenzustand prüfen")
                .setMessage("Prüfe vor Ort, dass Motor und Heizung stillstehen und die Ursache des Gerätefehlers behoben ist. Falls der Lauf nicht stoppt, die Maschine am Netzschalter ausschalten.")
                .setNegativeButton("Zurück",null).setPositiveButton("Vor Ort geprüft",(d,w)->{
                    if(localReady())client.acknowledgePhysicalCheck(lidless);
                }).create();inspection.setOnDismissListener(d->inspection=null);inspection.show();RotaryDialogs.bind(activity,inspection);
    }
    public void showAssistant(){if(localReady()&&(assistant==null||!assistant.isShowing()))assistant=CookingAssistant.show(activity,tools);}
    private void message(String value){android.widget.Toast.makeText(activity,value==null?"Diese Einstellung ist nicht verfügbar.":value,android.widget.Toast.LENGTH_LONG).show();}
    private static void text(TextView view,String next){if(!view.getText().toString().equals(next))view.setText(next);if(view instanceof Button)view.setContentDescription(next);}

    private void render(){
        if(disposed||rendering)return;rendering=true;
        try{
            // Dispatch against the incoming sample before rendering/serializing the UI can age it.
            if(localReady())progressStart(SystemClock.elapsedRealtime());
            readAttachmentChoice();observeSession();ControlState state=client.state;long now=SystemClock.elapsedRealtime();if(pendingTimeRevision>=0){if(state.acceptedCommandSequence==pendingTimeCommand)pendingTimeAckStatus=state.acceptedCommandStatusSequence;if(state.ownRun&&pendingTimeAckStatus>=0&&state.statusSequence>pendingTimeAckStatus){appliedTimeRevision=pendingTimeRevision;pendingTimeRevision=-1;}}if(client.lidInterruption.active()&&!state.ownRun&&state.idleConfirmed){
                if(lidShownSeconds!=client.lidInterruption.seconds()){lidShownSeconds=client.lidInterruption.seconds();duration.seedValue(lidShownSeconds);appliedTimeRevision=duration.changeRevision();pendingTimeRevision=-1;}
            }else if(!client.lidInterruption.active())lidShownSeconds=-1;
            boolean explicitTime=duration.changeRevision()!=appliedTimeRevision;
            if(awaitingHomeCup&&(!localReady()||!state.allows("START",now,lidless)))awaitingHomeCup=false;
            homeCupPrompt.accept(awaitingHomeCup);
            boolean local=localReady(),fresh=state.fresh(now),owned=session.hasRun()&&session.ownsCurrentRun()&&!session.interrupted()&&(state.ownRun||state.pendingAction.equals("START"));
            String chosenConfiguration=duration.value()+":"+temperature.value()+":"+speed.value()+":"+reverse.getAsBoolean();if(!renderedConfiguration.isEmpty()&&!chosenConfiguration.equals(renderedConfiguration)&&!state.ownRun&&setupStage==0)startIssue="";renderedConfiguration=chosenConfiguration;
            String configurationIssue=configurationIssue();
            boolean function=!functionTitle.isEmpty(),unsupportedSetup=!configurationIssue.isEmpty(),unsupportedDuration=duration.value()>com.kitchenos.connect.control.ControlProfile.MAX_SECONDS&&!(compactHome&&homeFunction.id.equals("slow")),unsupportedTemperature=temperature.value()!=0&&(temperature.value()<com.kitchenos.connect.control.ControlProfile.MIN_TEMPERATURE||temperature.value()>com.kitchenos.connect.control.ControlProfile.MAX_TEMPERATURE);boolean preparing=awaitingHomeCup||client.program.active()||setupStage>0||state.pendingAction.equals("START");duration.setEnabled(local&&!preparing);temperature.setEnabled(local&&!preparing&&(!state.ownRun||client.activeProfile()!=com.kitchenos.connect.control.ControlProfile.MOTOR));speed.setEnabled(local&&!preparing&&(!state.ownRun||client.activeProfile()!=com.kitchenos.connect.control.ControlProfile.FRY));if(compactHome){duration.setEnabled(duration.isEnabled()&&!homeFunction.temperatureOnly()&&!homeFunction.fixedDuration()&&homeFunction.runnable()&&homeFunction.minSeconds()!=homeFunction.maxSeconds);temperature.setEnabled(temperature.isEnabled()&&!((homeFunction.temperatureOnly()||homeFunction.fixedDuration())&&state.ownRun)&&homeFunction.runnable()&&homeFunction.minTemperature!=homeFunction.maxTemperature);speed.setEnabled(speed.isEnabled()&&homeFunction.speedControl());}View direction=root.getParent() instanceof View?((View)root.getParent()).findViewWithTag("direction-label"):null;if(direction!=null)direction.setEnabled(local&&!preparing&&speed.value()!=0&&temperature.value()<=150&&(!state.ownRun||client.activeProfile()==com.kitchenos.connect.control.ControlProfile.NORMAL||client.activeProfile()==com.kitchenos.connect.control.ControlProfile.MANUAL));temperature.reading((compactHome||state.ownRun)&&state.foreground&&state.connected&&!state.closing&&!state.potMissing()&&state.error==0&&state.temperature>0&&state.temperature<=250&&state.statusAt>=0&&now>=state.statusAt&&now-state.statusAt<1200?state.temperature:-1);duration.countdown(compactHome&&homeFunction.id.equals("eggs")&&client.eggCooking.active()&&!duration.isEditing()?client.eggCooking.preheating()?-1:client.eggCooking.remainingSeconds(now):client.program.active()?client.program.remaining(now):state.ownRun&&!duration.isEditing()&&!explicitTime&&state.remainingSeconds>=0?state.displayRemainingSeconds():state.naturalCompletion&&state.idleConfirmed&&!explicitTime?0:-1);
            if(compactHome&&homeFunction.id.equals("turbo")&&(state.ownRun||state.stopping||state.pendingAction.equals("START"))){duration.setEnabled(false);temperature.setEnabled(false);speed.setEnabled(false);}duration.liveState(state.ownRun,fresh);temperature.liveState(state.ownRun,fresh);speed.liveState(state.ownRun&&state.motorStatus!=0,fresh);
            String label=state.uncertainRun?"Maschinenzustand vor Ort prüfen":state.deviceFaultLatched?state.startReason(now,lidless)
                    :state.stopping?"Wird gestoppt …":state.closing?"Steuerung wird beendet …"
                    :state.pendingAction.equals("START")?"Start wird bestätigt …":owned?(state.state==3?"Pausiert":state.state==9||state.state==0?"Topf und Deckel werden geprüft …":function?"Läuft":"Dieser Schritt läuft")
                    :state.ownRun?"Maschine läuft":canComplete()?(function?"Beendet":"Schritt abgeschlossen"):locallyStopped?"Angehalten":setupStage>0?"Maschine wird vorbereitet …":!startIssue.isEmpty()||!state.userIssue.isEmpty()?"Nicht gestartet":unsupportedDuration||unsupportedTemperature?"Einstellung noch nicht verfügbar":function?"Bereit":"Bereit für diesen Schritt";
            text(status,function&&!functionTitle.equals("Manuell kochen")?functionTitle+" · "+label:label);
            String detail=awaitingHomeCup?"Messbecher eingesetzt? Zum Bestätigen und Starten den großen Knopf drücken .":!startIssue.isEmpty()?startIssue:!state.userIssue.isEmpty()?state.userIssue:state.pendingAction.equals("APPLY")?"Änderung wird bestätigt …":owned&&fresh?""
                    :state.rootChecking?"Maschine wird bereitgemacht …"
                    :setupStage>0||state.connecting?"Verbindung und Live-Werte werden geprüft …"
                    :!sameOwner()?"Das Konto hat sich geändert. Bitte die Ansicht erneut öffnen."
                    :locallyStopped?"":unsupportedSetup&&!compactHome&&!functionTitle.equals("Manuell kochen")?configurationIssue:!startIssue.isEmpty()?startIssue:!state.userIssue.isEmpty()?state.userIssue:state.connected&&!fresh?"Live-Werte werden aktualisiert …"
                    :state.connected&&!state.startReason(now,lidless).isEmpty()?state.startReason(now,lidless)
                    :"";
            // Home presents issues on the vessel; detailed wording remains available on demand.
            if(compactHome)state.visualIssue=!startIssue.isEmpty()?startIssue:!state.userIssue.isEmpty()?state.userIssue:!state.ownRun&&!client.program.active()?configurationIssue:"";
            if((compactHome||functionTitle.equals("Manuell kochen"))&&setupStage==0&&startIssue.isEmpty()&&!state.ownRun&&!state.stopping&&!state.uncertainRun&&!state.deviceFaultLatched)detail="";
            boolean canCorrect=!compactHome&&!functionTitle.equals("Manuell kochen")&&local&&!state.ownRun&&setupStage==0&&unsupportedSetup;attachmentSpeed.setVisibility(canCorrect&&speed.value()>attachmentMax?View.VISIBLE:View.GONE);heatOff.setVisibility(canCorrect&&speed.value()>30&&temperature.value()>0?View.VISIBLE:View.GONE);lowerSpeed.setVisibility(canCorrect&&speed.value()>30?View.VISIBLE:View.GONE);shortTime.setVisibility(canCorrect&&speed.value()>30&&duration.value()>com.kitchenos.connect.control.ControlProfile.maxSeconds(com.kitchenos.connect.control.ControlProfile.MOTOR)?View.VISIBLE:View.GONE);forwardBlade.setVisibility(canCorrect&&speed.value()>30&&reverse.getAsBoolean()?View.VISIBLE:View.GONE);corrections.setVisibility(canCorrect&&(attachmentSpeed.getVisibility()==View.VISIBLE||heatOff.getVisibility()==View.VISIBLE||lowerSpeed.getVisibility()==View.VISIBLE||shortTime.getVisibility()==View.VISIBLE||forwardBlade.getVisibility()==View.VISIBLE)?View.VISIBLE:View.GONE);
            text(live,detail);live.setVisibility(compactHome?View.GONE:detail.isEmpty()?(nextAfterStop==null?View.GONE:View.INVISIBLE):View.VISIBLE);status.setVisibility(compactHome?View.GONE:View.VISIBLE);if(compactHome)root.setVisibility(detail.isEmpty()?View.GONE:View.VISIBLE);if(compactHome){
                boolean keepOn=client.program.active()||state.ownRun||state.stopping||state.potCheck.active()||state.pendingAction.equals("CHECK_POT")||setupStage>0||state.pendingAction.equals("START");int flag=android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                // Reapplying an unchanged window flag forces measure/layout on every 200 ms heartbeat.
                if(((activity.getWindow().getAttributes().flags&flag)!=0)!=keepOn){if(keepOn)activity.getWindow().addFlags(flag);else activity.getWindow().clearFlags(flag);}
            }
            start.setVisibility(compactHome||state.ownRun||state.stopping||owned||nextAfterStop==null&&canComplete()||state.deviceFaultLatched||state.uncertainRun?View.GONE:View.VISIBLE);
            text(start,setupStage>0?"Maschine wird bereitgemacht …":"Starten");
            start.setEnabled(local&&!unsupportedSetup&&setupStage==0&&!state.closing&&!state.ownRun
                    &&!state.uncertainRun&&!state.deviceFaultLatched&&state.pendingAction.isEmpty());
            pauseRun.setVisibility(View.GONE);
            pauseRun.setEnabled(local&&session.hasRun()&&state.allows("PAUSE",now,lidless));
            resumeRun.setVisibility(state.ownRun&&state.state==3?View.VISIBLE:View.GONE);
            resumeRun.setEnabled(local&&session.hasRun()&&state.allows("RESUME",now,lidless)&&(explicitTime||state.remainingSeconds>0));
            apply.setVisibility(View.GONE);apply.setEnabled(local&&state.allows("APPLY",now,lidless));
            dock.setVisibility(!compactHome&&nextAfterStop==null&&(state.ownRun||state.uncertainRun||state.deviceFaultLatched||state.stopping)?View.VISIBLE:View.GONE);boolean stopAvailable=local&&(client.program.active()||state.connected&&!state.stopping);stop.setEnabled(stopAvailable);stop.setTextColor(stopAvailable?0xffffffff:Ui.MUTED);if(nextAfterStop!=null){stop.setVisibility(state.ownRun||state.stopping||state.uncertainRun||state.deviceFaultLatched?View.VISIBLE:View.GONE);text(stop,state.stopping?"Stoppt …":"Stoppen");}
            done.setVisibility(!compactHome&&canComplete()?View.VISIBLE:View.GONE);done.setEnabled(canComplete());
            text(done,function?"Fertig · Zurück":"Weiter");if(nextAfterStop!=null)done.setVisibility(View.GONE);
            if(nextAfterStop!=null){nextAfterStop.setVisibility(View.VISIBLE);nextAfterStop.setEnabled(canComplete()||canMoveOn()||canReviewForward());}
            check.setVisibility(View.GONE);check.setEnabled(false);
            if(compactHome&&homeCupAction instanceof Button){
                Button action=(Button)homeCupAction;boolean lidPaused=client.lidInterruption.active()&&!state.ownRun&&!state.stopping&&state.idleConfirmed;boolean inspect=state.recovering||!state.ownRun&&!state.stopping&&(state.uncertainRun||state.deviceFaultLatched),stoppingMode=client.lidInterruption.active()||client.program.active()||state.ownRun||state.stopping||state.pendingAction.equals("START")||state.uncertainRun||state.deviceFaultLatched||state.potCheck.active()||state.pendingAction.equals("CHECK_POT");
                // Display only: START submission and MCU preflight are cancellable through
                // the same STOP path. An ACK alone is not a running observation.
                boolean startChecking=homeFunction.cleaning!=null&&!state.stopping&&!state.uncertainRun&&!state.deviceFaultLatched&&!client.lidInterruption.active()
                    && !(fresh&&state.ownRun&&state.state==1)
                    && (state.pendingAction.equals("START")||state.ownRun&&(state.state==0||state.state==9||state.state==11));
                String homeLabel=lidPaused?(state.coverOpen!=0?"Deckel schließen":client.runBudgetRemaining()<=com.kitchenos.connect.control.RunBudget.STOP_RESERVE_MILLIS?"Zeitbudget beendet":"Weiterkochen"):client.lidInterruption.active()?"Stoppt …":startChecking?"Abbrechen":client.program.active()?"Stoppen":inspect?(state.recovering?"Prüft Zustand …":"Warte auf Maschine …"):state.stopping?"Stoppt …":stoppingMode?"Stoppen":setupStage>0||state.pendingAction.equals("START")?"Startet …":awaitingHomeCup?"Messbecher bestätigen":!fresh?"Wird bereit …":"Starten";
                if(!stoppingMode&&setupStage==0&&state.potCheck.required())homeLabel=state.potMissing()?"Mixtopf einsetzen":"Topfprüfung ausstehend";
                if(!reportedHomeAction.equals(homeLabel)){reportedHomeAction=homeLabel;android.util.Log.i("KitchenOSRunUi","action="+homeLabel+"; owned="+state.ownRun+"; pending="+state.pendingAction+"; status="+state.statusSequence);}
                if(homePrimary==null){text(action,homeLabel);action.setVisibility(View.VISIBLE);}
                action.setEnabled(lidPaused?local&&client.canContinueAfterLid(duration.value()):inspect?false:stoppingMode?local&&stopAvailable:local&&setupStage==0&&!state.closing&&!state.uncertainRun&&!state.deviceFaultLatched&&!state.potMissing()&&!state.potCheck.required()&&state.pendingAction.isEmpty()&&(state.allows("START",now,lidless)&&!unsupportedSetup));
                if(homeStoppingStyle==null||homeStoppingStyle!=stoppingMode){homeStoppingStyle=stoppingMode;action.setBackground(ControlFocus.background(activity,stoppingMode?0xffa63038:Ui.ACCENT));action.setTextColor(stoppingMode?0xffffffff:0xff171716);
                    if(primaryChanged!=null)main.post(()->{if(!disposed)primaryChanged.accept(action);});
                }
                boolean diagnosticOnly=(state.potCheck.active()||state.pendingAction.equals("CHECK_POT"))&&!state.ownRun&&!state.uncertainRun&&!state.deviceFaultLatched;
                if(homePrimary!=null)homePrimary.show(diagnosticOnly?"":homeLabel,diagnosticOnly?false:action.isEnabled(),!diagnosticOnly&&(stoppingMode||inspect));
            }
            if(nextAfterStop!=null){resumeRun.setVisibility(View.GONE);check.setVisibility(View.GONE);if(state.recovering||!state.ownRun&&!state.stopping&&(state.uncertainRun||state.deviceFaultLatched)){stop.setVisibility(View.VISIBLE);text(stop,state.recovering?"Prüft Zustand …":"Warte auf Maschine …");stop.setEnabled(false);}}
            if(confirmation!=null&&confirmation.isShowing()){
                Button positive=confirmation.getButton(AlertDialog.BUTTON_POSITIVE);
                if(positive!=null)positive.setEnabled(local&&state.allows(confirmAction,now,lidless));
            }
            try{JSONObject context=tools.contextSnapshot();if(context==null)context=new JSONObject();
                context.put("page","cooking").put("connected",state.connected).put("fresh",fresh)
                        .put("liveState",label).put("liveTemperatureC",fresh?state.temperature:JSONObject.NULL)
                        .put("elapsedSeconds",fresh&&owned?state.elapsed:JSONObject.NULL);
                if(function)context.put("functionTitle",functionTitle);tools.context(context);
            }catch(Exception ignored){}
            if(local){progressStart(SystemClock.elapsedRealtime());progressApply(SystemClock.elapsedRealtime());}View primary=primaryAction();if(primary!=lastPrimary){lastPrimary=primary;if(primaryChanged!=null)main.post(()->{if(!disposed&&primary==lastPrimary)primaryChanged.accept(primary);});}
        }finally{rendering=false;}
    }
}
