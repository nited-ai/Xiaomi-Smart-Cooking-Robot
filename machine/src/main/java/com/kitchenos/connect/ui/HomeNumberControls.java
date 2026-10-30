package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.control.ControlState;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Consumer;

/** Home presentation; deliberate speed releases delegate to the guarded cooking panel. */
public final class HomeNumberControls extends LinearLayout {
    static final int TOP_SPACE=40,SCALE_TOP=10;
    private static final int VESSEL_HEIGHT=392,NUMBER_HEIGHT=152,SHORTCUT_HEIGHT=92,ACTION_HEIGHT=104,QUICK_SIZE=108;
    private final Activity activity;private final Handler main=new Handler(Looper.getMainLooper());
    private final ParameterDial time,heat,speed;private final BooleanSupplier reverse;private final Predicate<Boolean> chooseDirection;
    private final Consumer<ParameterDial> finishEdit;
    private final Face[] faces;private final LinearLayout quick;private final ImageButton direction;private final TextView directionText;
    private final FrameLayout vessel;private final HomeVesselWebGLView vesselPhoto;private final HomeScaleArcView scaleArc;
    private final LinearLayout modeView;private final FrameLayout scaleView;private final Button attachment,start;
    private final Button cupCancel,endPaused;private final TextView cupQuestion,startStatus;private boolean cupPromptShown;private ValueAnimator cupQuestionPulse;private long cupQuestionPulseAt;private Consumer<Boolean> cupChoiceChanged=show->{};private Runnable cancelCup=()->{};
    private final FunctionWorkspacePager functionPager;private final LinearLayout guidancePage,controlsContent,cleaningPage,cleaningChoices;private final TextView unavailableContent;
    private final LinearLayout functionInfo,functionExtras,riceSummary;private FrameLayout quickSpace;private WaterBubbleBackdrop waterBackdrop;private final TextView functionTitle,functionDetail,programHeading,riceDuration,riceKind,cleaningDetail;
    private final LinearLayout cleaningRunSummary;private final TextView cleaningRunProgram,cleaningRemaining,cleaningReadings;
    private HomeFunctionProfile function=HomeFunctionProfile.get("manual");
    private int selectedEggGrade=1;
    private final ArrayList<Button> functionOptions=new ArrayList<>();
    private final List<Button> readOnlyOptions=Collections.unmodifiableList(functionOptions);
    private boolean functionChoiceConfirmed,functionChoiceRunObserved,primaryDirty;private long functionTimeRevision;private int riceSelection;private int cleaningPreview=-1;
    private Runnable functionStart=()->{};
    public String functionId(){return function.id;}
    public boolean pureeSelectionPending(){return function.puree()&&!functionChoiceConfirmed;}
    private String pureeChoice="";
    private Button functionHelp;private android.app.AlertDialog functionHelpDialog;
    public boolean eggTimeSelected(){return function.id.equals("eggs")&&functionChoiceConfirmed;}
    private void showFunctionHelp(){
        if(!canChooseFrying()||functionHelpDialog!=null)return;
        FunctionPageContent.Data content=FunctionPageContent.get(function.id);
        StringBuilder message=new StringBuilder();
        for(String[] step:content.steps)message.append(step[0]).append("\n").append(step[1]).append("\n\n");
        message.append(FunctionPageContent.settingsTip(function.id));
        if(!content.program.isEmpty())message.append("\n\n").append(content.program);
        if(!function.runnable()&&!function.unavailableReason().isEmpty())message.append("\n\n").append(function.unavailableReason());
        functionHelpDialog=new android.app.AlertDialog.Builder(activity).setTitle(function.title).setMessage(message.toString()).setNegativeButton("Schließen",null).create();
        functionHelpDialog.setOnDismissListener(d->functionHelpDialog=null);functionHelpDialog.show();RotaryDialogs.bind(activity,functionHelpDialog);
    }
    // Function pages share the water page's title/value/action hierarchy.
    // Manual cooking retains its continuous wheel presentation.
    private boolean functionReadout(){return !function.id.equals("manual")&&function.runnable();}
    private boolean standardFunctionLayout(){return functionReadout()&&!singleTemperatureLayout()&&!rice()&&!cleaning();}
    private boolean timeOnlyLayout(){return functionReadout()&&function.timeControl()&&!function.temperatureControl()&&!function.speedControl();}
    private int functionReadoutHeight(){return timeOnlyLayout()?204:292;}
    private int functionActionTop(){
        int height=functionPager.getLayoutParams().height;
        int content=standardFunctionLayout()?functionReadoutHeight()+104:rice()?244:292;return dp(content)+Math.max(0,(height-dp(content+ACTION_HEIGHT))/2);
    }
    private boolean cleaning(){return function.cleaning!=null;}
    private boolean water(){return function.temperatureOnly();}
    private boolean singleTemperatureLayout(){return water()||function.fixedDuration();}
    private boolean warm(){return function.id.equals("warm");}
    private boolean rice(){return function.id.equals("rice");}
    private boolean slow(){return function.id.equals("slow");}
    private int timeUnit(){return slow()?3600:60;}
    private boolean turbo(){return function.id.equals("turbo");}
    public List<Button> functionOptions(){return readOnlyOptions;}
    public void onFunctionStart(Runnable action){functionStart=action;}
    public void dismissShortcuts(){main.removeCallbacks(expire);generation++;for(Button b:shortcuts)b.animate().cancel();showQuick(false);}
    public void showFunctionOptions(){if(!canChooseFrying())return;functionChoiceConfirmed=rice();primaryDirty=true;dismissShortcuts();if(rice())functionPager.show(1,false);if(function.cleaning!=null){functionPager.controlsAvailable(false);functionPager.show(1,false);}primaryState(primaryLabel,primaryEnabled,primaryStopping);}
    public boolean cleaningSelectionPending(){return function.cleaning!=null&&!functionChoiceConfirmed;}
    public boolean cleaningSelectionShowing(){return cleaningSelectionPending()&&functionPager.page()==1;}
    public void focusFunctionOption(int index){if(!rice())for(int i=0;i<functionOptions.size();i++)optionState(functionOptions.get(i),i==index);if(function.cleaning!=null&&index>=0&&index<functionOptions.size()){cleaningPreview=index;updateCleaningExplanation();View selected=functionOptions.get(index);if(selected.isShown())selected.requestRectangleOnScreen(new Rect(0,0,selected.getWidth(),selected.getHeight()),true);}}
    public int selectedFunctionOption(){if(rice())return riceSelection;for(int i=0;i<functionOptions.size();i++)if(functionOptions.get(i).isSelected())return i;return 0;}
    public void requestFunctionStart(){
        if(!function.runnable()||!canChooseFrying())return;
        functionChoiceConfirmed=true;primaryDirty=true;if(function.cleaning!=null){functionPager.controlsAvailable(false);functionPager.show(1,false);}dismissShortcuts();primaryState(primaryLabel,primaryEnabled,primaryStopping);
        functionStart.run();knobStateChanged.run();
    }
    private boolean stirrerSelected,modeShown;private long modeUntil;private Runnable speedSelected=()->{},explicitStart=()->{},toolChanged=()->{};
    private BooleanSupplier chooseFrying=()->false;private Consumer<Boolean> chooseMotion=ignored->{};
    private java.util.function.IntPredicate chooseManualTemperature=value->false;
    private Face active;private int activePart,increment=-1;private boolean holding;private int generation;
    private final ArrayList<Button> shortcuts=new ArrayList<>();
    private final ArrayList<Integer> shortcutValues=new ArrayList<>();
    private Boolean renderedReverse;private boolean expandedWheelPreview,detentedWheelPreview,thresholdSnapPreview,motionBridgePreview,directTrackingPreview,directDetentPreview;
    private static final Typeface REGULAR=Typeface.create("sans-serif",Typeface.NORMAL),MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private static final String[] NUMBER_LABELS=new String[256],TIME_LABELS=new String[121],SPEED_LABELS=new String[121];
    static {for(int i=0;i<NUMBER_LABELS.length;i++)NUMBER_LABELS[i]=Integer.toString(i);for(int i=0;i<TIME_LABELS.length;i++)TIME_LABELS[i]=i<10?"0"+i:NUMBER_LABELS[i];for(int i=0;i<SPEED_LABELS.length;i++)SPEED_LABELS[i]=i%10==0?NUMBER_LABELS[i/10]:NUMBER_LABELS[i/10]+","+i%10;}
    private final Runnable expire=()->fadeOut();
    private final HomeScaleState readings=new HomeScaleState();private final HomeCupState cup=new HomeCupState();private TextView weight,scaleStatus;private Button tare;private boolean scaleAttempted,scalePending,cupRequired;
    private boolean hostActive=true;private long nextIdleConnect;
    private android.app.AlertDialog potInspection;
    private final ControlClient.Listener scaleChanges=this::refreshScale;
    private final Runnable scalePoll=new Runnable(){public void run(){if(!hostActive)return;refreshScale();if(hostActive&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE)main.postDelayed(this,200);}};
    public HomeNumberControls(Activity activity,ParameterDial time,ParameterDial heat,ParameterDial speed,BooleanSupplier reverse,Predicate<Boolean> chooseDirection,Consumer<ParameterDial> finishEdit){
        super(activity);this.activity=activity;this.time=time;this.heat=heat;this.speed=speed;this.reverse=reverse;this.chooseDirection=chooseDirection;this.finishEdit=finishEdit;stirrerSelected=activity.getSharedPreferences("home-tool-selection",0).getBoolean("stirrer",false);
        setOrientation(VERTICAL);setClipChildren(false);setTag("control.dials");setMinimumHeight(dp(VESSEL_HEIGHT+NUMBER_HEIGHT+SHORTCUT_HEIGHT+ACTION_HEIGHT));
        vessel=new FrameLayout(activity);vessel.setTag("home.vessel");vessel.setMinimumHeight(dp(VESSEL_HEIGHT));vessel.setClipChildren(false);vesselPhoto=new HomeVesselWebGLView(activity);
        scaleArc=new HomeScaleArcView(activity);vessel.addView(scaleArc,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams bowlSize=new FrameLayout.LayoutParams(dp(520),-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL);bowlSize.bottomMargin=dp(16);vessel.addView(vesselPhoto,bowlSize);vesselPhoto.setScaleX(1.12f);vesselPhoto.setScaleY(1.12f);vesselPhoto.setTranslationY(dp(24));
        vesselPhoto.setOnLongClickListener(v->{showPotInspection();return true;});vessel.bringChildToFront(scaleArc);
        modeView=new LinearLayout(activity);modeView.setTag("home.mode");modeView.setOrientation(HORIZONTAL);modeView.setGravity(Gravity.CENTER);modeView.setAlpha(0);modeView.setVisibility(GONE);
        direction=new ImageButton(activity);direction.setTag("direction-label");direction.setBackground(null);direction.setPadding(dp(12),dp(10),dp(12),dp(10));direction.setScaleType(ImageView.ScaleType.FIT_CENTER);direction.setClickable(false);direction.setFocusable(false);direction.setDefaultFocusHighlightEnabled(false);
        directionText=label("",24);modeView.addView(direction,new LinearLayout.LayoutParams(dp(48),dp(48)));modeView.addView(directionText);
        vessel.addView(modeView,new FrameLayout.LayoutParams(dp(232),dp(56),Gravity.RIGHT|Gravity.BOTTOM));
        scaleView=new FrameLayout(activity);scaleView.setTag("home.scale");
        weight=label("— g",36);weight.setTextColor(Ui.INK);weight.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));weight.setFontFeatureSettings("tnum");weight.setTag("home.scale.value");weight.setVisibility(GONE);FrameLayout.LayoutParams weightSize=new FrameLayout.LayoutParams(dp(190),dp(64),Gravity.TOP|Gravity.RIGHT);weightSize.rightMargin=dp(6);scaleView.addView(weight,weightSize);
        tare=Ui.button(activity,"Tara",v->{readings.tare();refreshScale();});tare.setTag("home.scale.tare");tare.setTextSize(17);tare.setPadding(dp(16),0,dp(16),0);tare.setMinHeight(dp(48));tare.setMinimumHeight(dp(48));tare.setBackground(ControlFocus.background(activity,Ui.CARD));tare.setContentDescription("Waage auf null setzen, nachdem der Fingerdruck abgeklungen ist");FrameLayout.LayoutParams tareSize=new FrameLayout.LayoutParams(dp(76),dp(48),Gravity.TOP|Gravity.LEFT);tareSize.leftMargin=dp(124);scaleView.addView(tare,tareSize);
        tare.setVisibility(GONE);scaleArc.setOnClickListener(v->{if(tare.isEnabled()){readings.tare();refreshScale();}});
        scaleStatus=label("Tara …",13);scaleStatus.setVisibility(GONE);FrameLayout.LayoutParams statusSize=new FrameLayout.LayoutParams(dp(112),dp(24),Gravity.TOP|Gravity.LEFT);statusSize.leftMargin=dp(124);scaleView.addView(scaleStatus,statusSize);
        vessel.addView(scaleView,new FrameLayout.LayoutParams(-1,-1));
        attachment=plainButton("Messer","home.attachment");attachment.setTextSize(16);attachment.setOnClickListener(v->{ControlState s=ControlClient.get(activity).state;if(s.ownRun||s.stopping||!s.pendingAction.isEmpty())return;
            if(speed.value()!=0){Toast.makeText(activity,"Zum Wechseln des Aufsatzes zuerst Stufe 0 wählen.",Toast.LENGTH_SHORT).show();return;}
            stirrerSelected=!stirrerSelected;activity.getSharedPreferences("home-tool-selection",0).edit().putBoolean("stirrer",stirrerSelected).apply();if(stirrerSelected)chooseDirection.test(true);speed.ceiling(stirrerSelected?40:100);attachment.setText(stirrerSelected?"Aufschlagbesen":"Messer");attachment.setContentDescription(stirrerSelected?"Aufschlagbesen ausgewählt. Antippen, um nach dem Entfernen wieder das Messer auszuwählen.":"Messer ausgewählt. Antippen, um einen eingesetzten Aufschlagbesen auszuwählen.");modeUntil=android.os.SystemClock.elapsedRealtime()+4000;toolChanged.run();refresh();refreshScale();});
        attachment.setText(stirrerSelected?"Aufschlagbesen":"Messer");
        attachment.setVisibility(GONE);vessel.addView(attachment,new FrameLayout.LayoutParams(dp(232),dp(44),Gravity.LEFT|Gravity.BOTTOM));
        start=plainButton("Starten","home.start");start.setTextSize(25);start.setTextColor(0xff171716);start.setBackground(ControlFocus.background(activity,Ui.ACCENT));start.setOnClickListener(v->explicitStart.run());
        // The cup is confirmed with the physical knob, never by a touch on the illustration.
        addView(vessel,new LinearLayout.LayoutParams(-1,dp(VESSEL_HEIGHT)));
        controlsContent=new LinearLayout(activity);controlsContent.setOrientation(VERTICAL);controlsContent.setClipChildren(false);
        LinearLayout numbers=new LinearLayout(activity);numbers.setTag("home.numbers");numbers.setClipChildren(false);numbers.setGravity(Gravity.CENTER);
        faces=new Face[]{new Face(time,0),new Face(heat,1),new Face(speed,2)};
        ParameterDial[] models={time,heat,speed};float[] weights={3.0f,2.2f,1.6f};
        for(int i=0;i<3;i++){models[i].numberFace(faces[i]);numbers.addView(models[i],new LinearLayout.LayoutParams(0,dp(152),weights[i]));}
        controlsContent.addView(numbers,new LinearLayout.LayoutParams(-1,dp(152)));
        quickSpace=new FrameLayout(activity);quickSpace.setClipChildren(false);quickSpace.setClipToPadding(false);
        functionInfo=new LinearLayout(activity);functionInfo.setOrientation(VERTICAL);functionInfo.setGravity(Gravity.CENTER);functionInfo.setTag("home.function.info");functionInfo.setTranslationY(-dp(18));
        functionTitle=label("Manuell kochen",24);functionTitle.setTextColor(Ui.INK);functionTitle.setTypeface(MEDIUM);functionTitle.setTag("home.function.title");functionInfo.addView(functionTitle);
        functionDetail=label("",17);functionDetail.setMaxLines(2);functionInfo.addView(functionDetail);quickSpace.addView(functionInfo,new FrameLayout.LayoutParams(-1,dp(SHORTCUT_HEIGHT),Gravity.BOTTOM));
        riceSummary=new LinearLayout(activity);riceSummary.setTag("home.rice.summary");riceSummary.setOrientation(VERTICAL);riceSummary.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);riceSummary.setPadding(0,dp(4),0,0);riceSummary.setVisibility(GONE);
        TextView riceHeading=label("Reis kochen",30);riceHeading.setTextColor(Ui.INK);riceHeading.setTypeface(MEDIUM);riceSummary.addView(riceHeading);
        riceDuration=label("25 Min",96);riceDuration.setTag("home.rice.duration");riceDuration.setTextColor(Ui.ACCENT);riceDuration.setTypeface(MEDIUM);LinearLayout.LayoutParams riceTimeSize=new LinearLayout.LayoutParams(-2,-2);riceTimeSize.topMargin=dp(16);riceSummary.addView(riceDuration,riceTimeSize);
        View riceUnderline=new View(activity);riceUnderline.setBackgroundColor(Ui.ACCENT);LinearLayout.LayoutParams underlineSize=new LinearLayout.LayoutParams(dp(172),dp(3));underlineSize.gravity=Gravity.CENTER_HORIZONTAL;riceSummary.addView(riceUnderline,underlineSize);
        riceKind=label("Weißreis · Richtwert inkl. Aufheizen",18);riceKind.setTag("home.rice.kind");LinearLayout.LayoutParams riceKindSize=new LinearLayout.LayoutParams(-1,-2);riceKindSize.topMargin=dp(14);riceSummary.addView(riceKind,riceKindSize);
        quickSpace.addView(riceSummary,new FrameLayout.LayoutParams(-1,-1));
        // Shortcuts share the action's actual overlay bounds, including touch targets.
        quick=new LinearLayout(activity);quick.setGravity(Gravity.CENTER);quick.setVisibility(INVISIBLE);quick.setTag("home.number-shortcuts");LinearLayout.LayoutParams quickRegion=new LinearLayout.LayoutParams(-1,dp(SHORTCUT_HEIGHT+24));quickRegion.topMargin=-dp(24);controlsContent.addView(quickSpace,quickRegion);directionChanged();
        FrameLayout actionSpace=new FrameLayout(activity);actionSpace.setTag("home.primary.region");
        functionExtras=new LinearLayout(activity);functionExtras.setGravity(Gravity.CENTER);functionExtras.setTag("home.function.extras");functionExtras.setPadding(dp(16),0,dp(16),0);actionSpace.addView(functionExtras,new FrameLayout.LayoutParams(-1,dp(ACTION_HEIGHT),Gravity.TOP));
        controlsContent.addView(actionSpace,new LinearLayout.LayoutParams(-1,dp(ACTION_HEIGHT)));
        functionTitle.setTextSize(28);functionDetail.setTextSize(21);functionDetail.setMaxLines(2);
        guidancePage=new LinearLayout(activity);guidancePage.setOrientation(VERTICAL);guidancePage.setPadding(dp(60),dp(20),dp(60),dp(12));guidancePage.setTag("home.function.guidance");
        FrameLayout controlsPage=new FrameLayout(activity);controlsPage.setTag("home.function.controls");waterBackdrop=new WaterBubbleBackdrop(activity);waterBackdrop.setVisibility(GONE);controlsPage.addView(waterBackdrop,new FrameLayout.LayoutParams(-1,-1));controlsPage.addView(controlsContent,new FrameLayout.LayoutParams(-1,-1));
        programHeading=label("",30);programHeading.setTextColor(Ui.INK);programHeading.setTypeface(MEDIUM);programHeading.setTag("home.function.program-heading");programHeading.setVisibility(GONE);
        controlsPage.addView(programHeading,new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP));
        unavailableContent=label("",24);unavailableContent.setPadding(dp(64),dp(32),dp(64),dp(20));unavailableContent.setGravity(Gravity.TOP);unavailableContent.setVisibility(GONE);controlsPage.addView(unavailableContent,new FrameLayout.LayoutParams(-1,-1));
        cleaningPage=new LinearLayout(activity);cleaningPage.setOrientation(VERTICAL);cleaningPage.setPadding(dp(60),0,dp(60),0);cleaningPage.setTag("home.cleaning.selection");
        TextView cleaningTitle=label("Vorspülen",30);cleaningTitle.setTag("home.cleaning.info");cleaningTitle.setOnClickListener(v->showCleaningInfo());cleaningTitle.setTypeface(MEDIUM);cleaningTitle.setTextColor(Ui.INK);cleaningPage.addView(cleaningTitle,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView cleaningHint=label("500 ml–1 l Wasser · Messer ohne Aufsatz\nDeckel und Messbecher einsetzen",20);cleaningHint.setTag("home.cleaning.preparation");cleaningPage.addView(cleaningHint,new LinearLayout.LayoutParams(-1,dp(52)));
        ScrollView cleaningScroll=new ScrollView(activity);cleaningScroll.setTag("home.cleaning.scroll");cleaningScroll.setVerticalScrollBarEnabled(false);cleaningChoices=new LinearLayout(activity);cleaningChoices.setOrientation(VERTICAL);cleaningScroll.addView(cleaningChoices);LinearLayout.LayoutParams cleaningScrollSize=new LinearLayout.LayoutParams(-1,dp(222));cleaningScrollSize.topMargin=dp(12);cleaningPage.addView(cleaningScroll,cleaningScrollSize);
        cleaningDetail=label("",23);cleaningDetail.setTag("home.cleaning.explanation");cleaningDetail.setMaxLines(3);LinearLayout.LayoutParams cleaningDetailSize=new LinearLayout.LayoutParams(-1,dp(84));cleaningDetailSize.topMargin=dp(14);cleaningPage.addView(cleaningDetail,cleaningDetailSize);
        cleaningRunSummary=new LinearLayout(activity);cleaningRunSummary.setOrientation(VERTICAL);cleaningRunSummary.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cleaningRunSummary.setTag("home.cleaning.run");cleaningRunSummary.setVisibility(GONE);
        cleaningRunProgram=label("",26);cleaningRunProgram.setTextColor(Ui.INK);cleaningRunProgram.setTag("home.cleaning.run.program");cleaningRunSummary.addView(cleaningRunProgram,new LinearLayout.LayoutParams(-1,dp(42)));
        cleaningRunSummary.addView(label("Restzeit",20),new LinearLayout.LayoutParams(-1,dp(32)));
        cleaningRemaining=label("",96);cleaningRemaining.setTypeface(MEDIUM);cleaningRemaining.setTextColor(Ui.ACCENT);cleaningRemaining.setFontFeatureSettings("tnum");cleaningRemaining.setTag("home.cleaning.run.remaining");cleaningRunSummary.addView(cleaningRemaining,new LinearLayout.LayoutParams(-1,dp(120)));
        View cleaningUnderline=new View(activity);cleaningUnderline.setBackgroundColor(Ui.ACCENT);cleaningRunSummary.addView(cleaningUnderline,new LinearLayout.LayoutParams(dp(172),dp(3)));
        cleaningReadings=label("",22);cleaningReadings.setTag("home.cleaning.run.readings");LinearLayout.LayoutParams cleaningReadingsSize=new LinearLayout.LayoutParams(-1,dp(52));cleaningReadingsSize.topMargin=dp(12);cleaningRunSummary.addView(cleaningReadings,cleaningReadingsSize);
        cleaningPage.addView(cleaningRunSummary,1,new LinearLayout.LayoutParams(-1,dp(288)));
        functionPager=new FunctionWorkspacePager(activity,guidancePage,controlsPage);addView(functionPager,new LinearLayout.LayoutParams(-1,dp(600)));
        FrameLayout.LayoutParams stopSize=new FrameLayout.LayoutParams(dp(440),dp(104),Gravity.TOP|Gravity.CENTER_HORIZONTAL);stopSize.leftMargin=stopSize.rightMargin=dp(60);stopSize.topMargin=dp(wheelHeight()+12);functionPager.overlay(start,stopSize);
        startStatus=label("Wird geprüft …",24);startStatus.setTag("home.start.status");startStatus.setVisibility(GONE);startStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        FrameLayout.LayoutParams startStatusSize=new FrameLayout.LayoutParams(-1,dp(44),Gravity.TOP);startStatusSize.leftMargin=startStatusSize.rightMargin=dp(60);functionPager.overlay(startStatus,startStatusSize);
        cupQuestion=label("Messbecher eingesetzt?",34);cupQuestion.setTypeface(MEDIUM);cupQuestion.setTextColor(Ui.INK);cupQuestion.setTag("home.cup.question");cupQuestion.setVisibility(GONE);
        FrameLayout.LayoutParams questionSize=new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP);questionSize.leftMargin=questionSize.rightMargin=dp(60);functionPager.overlay(cupQuestion,questionSize);
        cupCancel=plainButton("Abbrechen","home.cup.cancel");cupCancel.setTextSize(23);cupCancel.setBackground(ControlFocus.background(activity,Ui.CARD,52));cupCancel.setVisibility(GONE);cupCancel.setOnClickListener(v->cancelCup.run());
        FrameLayout.LayoutParams cancelSize=new FrameLayout.LayoutParams(dp(204),dp(104),Gravity.TOP|Gravity.RIGHT);cancelSize.rightMargin=dp(60);functionPager.overlay(cupCancel,cancelSize);
        endPaused=plainButton("Beenden","home.pause.end");endPaused.setTextSize(28);endPaused.setTextColor(Ui.INK);endPaused.setBackground(ControlFocus.background(activity,Ui.CARD,52));endPaused.setVisibility(GONE);endPaused.setOnClickListener(v->ControlClient.get(activity).endLidInterruption());
        FrameLayout.LayoutParams endSize=new FrameLayout.LayoutParams(dp(220),dp(64),Gravity.TOP|Gravity.CENTER_HORIZONTAL);endSize.topMargin=dp(wheelHeight()+132);functionPager.overlay(endPaused,endSize);
        FrameLayout.LayoutParams quickSize=new FrameLayout.LayoutParams(-1,dp(QUICK_SIZE),Gravity.TOP);quickSize.topMargin=dp(wheelHeight()+10);functionPager.overlay(quick,quickSize);
        functionHelp=plainButton("ⓘ","home.function.help");functionHelp.setTextSize(30);functionHelp.setContentDescription("Informationen zur Funktion");functionHelp.setVisibility(GONE);functionHelp.setOnClickListener(v->showFunctionHelp());
        FrameLayout.LayoutParams helpSize=new FrameLayout.LayoutParams(dp(64),dp(56),Gravity.TOP|Gravity.RIGHT);helpSize.rightMargin=dp(12);functionPager.overlay(functionHelp,helpSize);
        // Accepted interaction is the default for every caller, including new screens.
        expandedWheelPreview(true);directTrackingPreview(true);motionBridgePreview(true);motionBridgeBrakingPreview(true);
        functionPager.onPageChanged(()->showQuick(quick.getVisibility()==VISIBLE));
    }
    private int dp(int n){return Ui.dp(activity,n);}
    private Button plainButton(String text,String tag){Button b=new Button(activity);b.setText(text);b.setContentDescription(text);b.setTag(tag);b.setAllCaps(false);b.setTextSize(20);b.setBackground(null);b.setTextColor(Ui.MUTED);b.setPadding(0,0,0,0);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setDefaultFocusHighlightEnabled(false);return b;}
    public void showFunctionPreparation(){functionPager.show(1,false);}
    public void showFunctionControls(){functionPager.show(1,true);}
    public void onSpeedSelected(Runnable action){speedSelected=action;}
    public void onExplicitStart(Runnable action){explicitStart=action;}
    public void onToolChanged(Runnable action){toolChanged=action;}
    public void onMotionSelected(Consumer<Boolean> action){chooseMotion=action;}
    private boolean hadProgram;
    private Consumer<Boolean> chooseInterval=value->{};private Consumer<HomeFunctionProfile.Preset> choosePreset=value->{};
    public void onPresetSelected(Consumer<HomeFunctionProfile.Preset> action){choosePreset=action;}
    public void onIntervalSelected(Consumer<Boolean> action){chooseInterval=action;}
    public void onFryingSelected(BooleanSupplier action){chooseFrying=action;}
    public void onManualTemperatureSelected(java.util.function.IntPredicate action){chooseManualTemperature=action;}
    private boolean canChooseFrying(){ControlState s=ControlClient.get(activity).state;return !ControlClient.get(activity).program.active()&&!s.ownRun&&!s.stopping&&!s.uncertainRun&&!s.closing&&s.pendingAction.isEmpty()&&!s.potCheck.active();}
    public boolean stirrerSelected(){return stirrerSelected;}
    private void showPotInspection(){
        ControlClient client=ControlClient.get(activity);if(potInspection!=null)return;
        if(!client.canCheckPot()){Toast.makeText(activity,"Topfprüfung: Maschine kalt und stillstehen lassen, Deckel öffnen und Live-Verbindung abwarten.",Toast.LENGTH_LONG).show();return;}
        potInspection=new android.app.AlertDialog.Builder(activity).setTitle("Mixtopf prüfen")
            .setMessage("Der Deckel bleibt offen. Hände von Topf und Verriegelung nehmen. Die kurze Geräteprüfung kann die Verriegelung bewegen und endet automatisch nach 1,5 Sekunden mit STOP.")
            .setNegativeButton("Zurück",null).setPositiveButton("Jetzt prüfen",(d,w)->{if(!client.checkPot())Toast.makeText(activity,"Gerätezustand hat sich geändert. Prüfung nicht gestartet.",Toast.LENGTH_SHORT).show();}).create();
        potInspection.setOnDismissListener(d->potInspection=null);potInspection.show();RotaryDialogs.bind(activity,potInspection);
    }
    private int knobSlot=-1;private boolean knobEditing;private Runnable knobStateChanged=()->{};
    public void onKnobStateChanged(Runnable action){knobStateChanged=action;}
    public boolean stopShowing(){return primaryStopping&&start.isShown();}
    public int knobTimePart(){return faces[0].part;}
    public void knobTimePart(int part){faces[0].part=turbo()?1:part;activePart=faces[0].part;}
    public void knobFocus(int slot,boolean editing){knobSlot=slot;knobEditing=editing;for(Face face:faces)face.model.invalidate();}
    /** Reveal shortcuts only for deliberate knob input, never a telemetry refresh. */
    public void knobChoices(int slot){if(slot<0||slot>=faces.length)return;Face face=faces[slot];if(face.model.isEnabled()&&face.model.isShown())activate(face,face.part);}
    public void beginKnobEdit(int slot){Face face=faces[slot<2?0:slot-1];face.part=slot<2?(turbo()?1:slot):0;activate(face,face.part);face.model.beginNumberEdit();}
    public void turnTime(int part,int delta){int before=time.value(),unit=timeUnit(),major=before/unit,minor=before%unit/(slow()?60:1);
        if(turbo())part=1;
        int next=part==0?HomeControlMath.cyclicValue(major+delta,time.minimum()/unit,time.maximum()/unit)*unit+minor*(slow()?60:1):major*unit+HomeControlMath.cyclicValue(minor+delta,0,major==time.maximum()/unit?time.maximum()%unit/(slow()?60:1):59)*(slow()?60:1);
        time.value(Math.max(time.minimum(),Math.min(time.maximum(),next)));if(time.value()!=before)faces[0].tick();}
    public Button attachmentButton(){return attachment;}
    public Button startButton(){return start;}
    public Button cupCancelButton(){return cupCancel;}
    public boolean cupPromptShowing(){return cupPromptShown;}
    public void onCupChoice(Consumer<Boolean> action,Runnable cancel){cupChoiceChanged=action;cancelCup=cancel;}
    private void questionPulse(boolean show){
        if(cupQuestionPulse!=null){cupQuestionPulse.cancel();cupQuestionPulse=null;}cupQuestion.setAlpha(1);
        if(show&&hostActive&&motion()){cupQuestionPulse=ValueAnimator.ofFloat(1,.62f);cupQuestionPulse.setDuration(900);cupQuestionPulse.setRepeatCount(ValueAnimator.INFINITE);cupQuestionPulse.setRepeatMode(ValueAnimator.REVERSE);cupQuestionPulse.addUpdateListener(a->{cupQuestionPulseAt=System.currentTimeMillis();cupQuestion.setAlpha(HomeCupPulse.alpha(cupQuestionPulseAt));});cupQuestionPulse.start();}
    }
    public void function(HomeFunctionProfile profile,java.util.function.IntConsumer chooseEgg){
        boolean changed=!function.id.equals(profile.id)||guidancePage.getChildCount()==0;function=profile;for(Face face:faces)face.cachedChoices=null;stirrerSelected=false;cup.reset();vesselPhoto.tool(profile.tool);
        // Only independent, editable parameters occupy space on function pages.
        time.setVisibility(profile.timeControl()?VISIBLE:GONE);speed.setVisibility(profile.speedControl()?VISIBLE:GONE);heat.setVisibility(profile.temperatureControl()?VISIBLE:GONE);
        time.minuteKnob(slow());
        heat.setContentDescription(warm()?"Zieltemperatur":"Temperatur");speed.setContentDescription(warm()?"Linkslauf-Stufe":"Stufe");
        LinearLayout.LayoutParams timeSize=(LinearLayout.LayoutParams)time.getLayoutParams();boolean timeOnly=profile.timeControl()&&!profile.temperatureControl()&&!profile.speedControl();timeSize.width=timeOnly?dp(360):0;timeSize.weight=timeOnly?0:3.0f;time.setLayoutParams(timeSize);
        LinearLayout.LayoutParams heatSize=(LinearLayout.LayoutParams)heat.getLayoutParams();heatSize.width=singleTemperatureLayout()?dp(520):0;heatSize.weight=singleTemperatureLayout()?0:slow()?3.0f:2.2f;heat.setLayoutParams(heatSize);
        LinearLayout.LayoutParams speedSize=(LinearLayout.LayoutParams)speed.getLayoutParams();speedSize.weight=profile.puree()||warm()||turbo()?2.2f:1.6f;speed.setLayoutParams(speedSize);
        int numberHeight=dp(rice()||cleaning()?0:functionReadout()?functionReadoutHeight():wheelHeight());View numberRow=findViewWithTag("home.numbers");numberRow.getLayoutParams().height=numberHeight;for(Face face:faces)face.model.getLayoutParams().height=numberHeight;numberRow.requestLayout();
        LinearLayout.LayoutParams quickRegion=(LinearLayout.LayoutParams)quickSpace.getLayoutParams();quickRegion.height=singleTemperatureLayout()?0:rice()?dp(244):standardFunctionLayout()?dp(104):dp(116);quickRegion.topMargin=functionReadout()?0:-dp(24);quickSpace.setLayoutParams(quickRegion);
        View actionRegion=(View)functionExtras.getParent();LinearLayout.LayoutParams regionSize=(LinearLayout.LayoutParams)actionRegion.getLayoutParams();regionSize.height=functionReadout()?0:dp(ACTION_HEIGHT);regionSize.weight=functionReadout()?1:0;actionRegion.setLayoutParams(regionSize);
        FrameLayout.LayoutParams optionsSize=(FrameLayout.LayoutParams)functionExtras.getLayoutParams();optionsSize.gravity=singleTemperatureLayout()||standardFunctionLayout()||rice()?Gravity.CENTER_VERTICAL:Gravity.TOP;optionsSize.topMargin=0;riceSummary.setVisibility(rice()?VISIBLE:GONE);functionExtras.setLayoutParams(optionsSize);
        functionInfo.setTranslationY(standardFunctionLayout()?0:-dp(18));
        FrameLayout.LayoutParams infoSize=(FrameLayout.LayoutParams)functionInfo.getLayoutParams();infoSize.height=dp(standardFunctionLayout()?104:SHORTCUT_HEIGHT);functionInfo.setLayoutParams(infoSize);
        programHeading.setText(profile.title);programHeading.setVisibility(standardFunctionLayout()?VISIBLE:GONE);
        vesselPhoto.water(profile.temperatureOnly());waterBackdrop.setVisibility(profile.temperatureOnly()?VISIBLE:GONE);functionPager.directControls(profile.directControls());functionHelp.setVisibility(profile.id.equals("manual")?GONE:VISIBLE);if(changed&&water())ControlClient.get(activity).waterHeating.clearResult();
        if(changed){dismissShortcuts();faces[0].part=turbo()?1:0;activePart=faces[0].part;}
        FunctionPageContent.Data content=FunctionPageContent.get(profile.id);
        functionTitle.setText(profile.title);functionDetail.setText(profile.intervals&&(profile.id.equals("stir")||profile.id.equals("slow"))?"5 s Rühren 0,5 · 25 s Pause\nWiederholung bis zum Zeitende":FunctionPageContent.settingsTip(profile.id));functionExtras.removeAllViews();functionOptions.clear();cleaningPreview=-1;functionChoiceConfirmed=rice();riceSelection=0;primaryDirty=true;if(changed){functionTimeRevision=time.changeRevision();}
        fillGuidance(guidancePage,profile);
        cleaningChoices.removeAllViews();functionPager.middlePage(profile.cleaning==null?null:cleaningPage);functionPager.controlsAvailable(profile.cleaning==null);functionPager.directControls(profile.directControls());
        controlsContent.setVisibility(profile.runnable()?VISIBLE:INVISIBLE);unavailableContent.setVisibility(profile.runnable()?GONE:VISIBLE);
        unavailableContent.setText(profile.title+"\n\n"+(profile.id.equals("scale")?"Gewicht oben am Mixtopf ablesen. Zum Tarieren die Gewichtsanzeige antippen.":profile.unavailableReason())+(content.program.isEmpty()?"":"\n\nGeplanter Ablauf\n"+content.program));
        if(changed)functionPager.show(profile.directControls()||cleaning()?1:0,false);
        if(profile.id.equals("eggs")){
            String[] grades={"Weich","Wachsweich","Hart"};
            for(int i=0;i<grades.length;i++){final int grade=i;addFunctionOption(grades[i],"home.eggs.grade."+i,false,()->chooseEgg.accept(grade==2?3:grade));}
        }else if(profile.id.equals("lidless")){
            for(int i=0;i<2;i++){final boolean still=i==1;addFunctionOption(still?"Ohne Bewegung":"Sanft rühren","home.lidless.motion."+i,still==(profile.maxSpeed==0),()->chooseMotion.accept(still));}
        }else if(profile.id.equals("stir")){
            for(boolean intervals:new boolean[]{false,true})addFunctionOption(intervals?"Mit Pausen":"Stetig rühren","home.interval."+intervals,profile.maxSpeed>0&&profile.intervals==intervals,()->chooseInterval.accept(intervals));
        }else if(profile.presets().length>0){
            int i=0;for(HomeFunctionProfile.Preset preset:profile.presets())addFunctionOption(preset.label,"home.preset."+i++,preset.frying!=null&&preset.frying==profile.frying,()->{
                boolean keepTime=preset.frying!=null||function.puree()||preset.cleaning==null&&!rice()&&!function.id.equals("turbo")&&!function.fixedDuration()&&time.changeRevision()!=functionTimeRevision;int seconds=time.value();
                choosePreset.accept(preset);if(keepTime)time.value(seconds);else functionTimeRevision=time.changeRevision();
            });
        }else{TextView hint=label(profile.intervals?profile.detail:profile.id.equals("fry")?"Bis 180 °C · höchstens 30 Minuten · Rühren 1":"",20);hint.setMaxLines(3);functionExtras.addView(hint);}
        if(rice()&&!functionOptions.isEmpty()){optionState(functionOptions.get(0),true);updateRiceSummary();}
        if(!functionOptions.isEmpty()&&!rice())functionDetail.setText(profile.cleaning!=null?profile.cleaning.title+" · Programm wählen und starten":FunctionPageContent.settingsTip(profile.id).split("\n")[0]+"\nAuswählen und starten");
        if(profile.id.equals("fry"))functionDetail.setText("Rückwärts · Stufe 1 · Rhythmus wählen und starten\nIn den Messerpausen bleibt die Temperaturregelung aktiv.");
        updatePureeExplanation();
        functionTitle.setText(profile.cleaning!=null?"Vorspülen  ⓘ":profile.title);functionTitle.setOnClickListener(profile.cleaning!=null?v->showCleaningInfo():null);
        primaryState(primaryLabel,primaryEnabled,primaryStopping);

        refresh();refreshScale();
    }
    private void showCleaningInfo(){if(!canChooseFrying())return;StringBuilder info=new StringBuilder("Messer ohne Aufsatz. Grobe Reste entfernen, 500 ml bis höchstens 1 l Wasser einfüllen. Deckel und Messbecher einsetzen.\n\n");for(com.kitchenos.connect.control.CleaningProgram c:com.kitchenos.connect.control.CleaningProgram.values())info.append(c.title).append("\n").append(c.information()).append("\n\n");info.append("Anschließend Wasser vorsichtig ausgießen und mit klarem Wasser nachspülen. Geplante Phasenzeit; Geräteübergänge können länger dauern.");android.app.AlertDialog d=new android.app.AlertDialog.Builder(activity).setTitle("Reinigungsprogramme").setMessage(info.toString()).setPositiveButton("Verstanden",null).create();d.show();RotaryDialogs.bind(activity,d);}
    private void fillGuidance(LinearLayout page,HomeFunctionProfile profile){
        FunctionPageContent.Data content=FunctionPageContent.get(profile.id);
        page.removeAllViews();LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.TOP);heading.setBaselineAligned(false);
        TextView title=label(profile.title,36);title.setTextColor(Ui.INK);title.setGravity(Gravity.LEFT);title.setTypeface(MEDIUM);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        String tool=FunctionPageContent.tool(profile.tool);if(!tool.isEmpty()){TextView accessory=label(tool,20);accessory.setGravity(Gravity.RIGHT);heading.addView(accessory,new LinearLayout.LayoutParams(dp(220),-2));}page.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        ScrollView instructions=new ScrollView(activity);instructions.setTag("home.function.instructions");instructions.setVerticalScrollBarEnabled(false);instructions.setFillViewport(false);LinearLayout list=new LinearLayout(activity);list.setOrientation(VERTICAL);instructions.addView(list);LinearLayout.LayoutParams readSize=new LinearLayout.LayoutParams(-1,0,1);readSize.topMargin=dp(18);page.addView(instructions,readSize);
        for(int i=0;i<content.steps.length;i++){View rule=new View(activity);rule.setBackgroundColor(Ui.LINE);list.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));LinearLayout row=new LinearLayout(activity);row.setPadding(0,dp(12),0,dp(12));TextView n=label(String.format(java.util.Locale.ROOT,"%02d",i+1),22);n.setGravity(Gravity.LEFT|Gravity.TOP);row.addView(n,new LinearLayout.LayoutParams(dp(46),-1));LinearLayout words=new LinearLayout(activity);words.setOrientation(VERTICAL);TextView stepTitle=label(content.steps[i][0],25);stepTitle.setTextColor(Ui.INK);stepTitle.setGravity(Gravity.LEFT);stepTitle.setTypeface(MEDIUM);words.addView(stepTitle);TextView detail=label(content.steps[i][1],24);detail.setGravity(Gravity.LEFT);words.addView(detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));list.addView(row);}
    }
    public void onFunctionSwipe(BooleanSupplier allowed,Consumer<String> select){
        functionPager.neighbours(new FunctionWorkspacePager.Neighbours(){
            public boolean available(){return !function.id.equals("manual")&&allowed.getAsBoolean();}
            public FunctionWorkspacePager.Transition preview(int direction){
                java.util.List<String> ids=FunctionSetup.ids();ids.remove("manual");
                int index=ids.indexOf(function.id);if(index<0)return null;
                String id=ids.get((index+direction+ids.size())%ids.size());
                LinearLayout preview=new LinearLayout(activity);preview.setOrientation(VERTICAL);preview.setPadding(dp(60),dp(20),dp(60),dp(12));preview.setTag("home.function.preview."+id);
                HomeFunctionProfile upcoming=HomeFunctionProfile.get(id);preview.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);preview.addView(label(upcoming.title,30));
                if(!upcoming.runnable())preview.addView(label(id.equals("scale")?"Gewicht oben am Mixtopf":upcoming.unavailableReason(),24));
                else{
                    StringBuilder values=new StringBuilder();
                    if(upcoming.timeControl())values.append(upcoming.seconds/60).append(" Min ").append(upcoming.seconds%60).append(" Sek");
                    if(upcoming.temperatureControl())values.append(values.length()>0?"    ":"").append(upcoming.temperature==0?"Heizung aus":upcoming.temperature+" °C");
                    if(upcoming.speedControl())values.append(values.length()>0?"    ":"").append("Stufe ").append(SPEED_LABELS[upcoming.speed]);
                    if(values.length()>0){TextView value=label(values.toString(),48);value.setTextColor(Ui.ACCENT);preview.addView(value,new LinearLayout.LayoutParams(-1,dp(236)));}
                    preview.addView(label(FunctionPageContent.settingsTip(id),21));
                    StringBuilder choices=new StringBuilder();for(HomeFunctionProfile.Preset preset:upcoming.presets()){if(choices.length()>0)choices.append("    ");choices.append(preset.label);}
                    if(id.equals("eggs"))choices.append("Weich    Wachsweich    Hart");
                    if(choices.length()>0)preview.addView(label(choices.toString(),24));
                }
                return new FunctionWorkspacePager.Transition(preview,()->{if(allowed.getAsBoolean())select.accept(id);});
            }
        });
    }
    private void addFunctionOption(String label,String tag,boolean selected,Runnable apply){
        Button b=optionButton(label,tag);
        if(function.id.equals("fry")){b.setSingleLine(false);b.setMaxLines(2);b.setText(label.replace(" · ","\n").replace("Durchgehend rühren","Durchgehend\nrühren"));}
        if(function.cleaning!=null){
            b.setText(com.kitchenos.connect.control.CleaningProgram.at(functionOptions.size()).title);b.setTextSize(26);b.setGravity(Gravity.CENTER);
        }optionState(b,selected);functionOptions.add(b);
        b.setOnClickListener(v->{if(!canChooseFrying())return;apply.run();if(rice()){
            riceSelection=functionOptions.indexOf(b);for(Button option:functionOptions)optionState(option,option==b);updateRiceSummary();
            requestFunctionStart();
        }else if(water()){ControlClient.get(activity).waterHeating.clearResult();for(Button option:functionOptions)optionState(option,option==b);refresh();requestFunctionStart();}else if(function.puree()){
            for(Button option:functionOptions)optionState(option,option==b);
            requestFunctionStart();
        }else if(function.id.equals("eggs")){
            functionChoiceConfirmed=true;primaryDirty=true;dismissShortcuts();primaryState(primaryLabel,primaryEnabled,false);knobStateChanged.run();
        }else if(function.cleaning!=null){
            functionChoiceConfirmed=true;primaryDirty=true;functionPager.controlsAvailable(false);functionPager.show(1,false);
            cleaningPreview=function.cleaning.ordinal();dismissShortcuts();updateCleaningExplanation();requestFunctionStart();
        }else requestFunctionStart();});addOption(b);
    }
    private void updatePureeExplanation(){
        if(!function.puree())return;
        functionTitle.setText("Pürieren");functionTitle.setOnClickListener(null);
        String why=speed.value()<=60?"Stufe 6 lässt mehr Struktur":speed.value()<=80?"Stufe "+speed.value()/10+" für cremiges Püree":"Stufe "+speed.value()/10+" für besonders feines Püree";
        functionDetail.setText(functionChoiceConfirmed?why+"\nSanfter Hochlauf · "+time.value()+" s insgesamt": "Zeit einstellen, dann Cremigkeit wählen und starten.\nGrob 6 · Cremig 8 · Fein 10 · sanfter Hochlauf");
    }
    private void updateCleaningExplanation(){
        if(!cleaning())return;
        com.kitchenos.connect.control.CleaningProgram program=functionChoiceConfirmed?function.cleaning:com.kitchenos.connect.control.CleaningProgram.at(cleaningPreview);
        ControlClient client=ControlClient.get(activity);
        String text=client.program.active()?programText(client):primaryStopping?program.title+"\n"+primaryLabel:functionChoiceConfirmed||cleaningPreview>=0?program.title+" · "+program.selectionDuration()+"\n"+program.selectionSummary():"Antippen startet das Programm.\nDauer und Anwendung unter ⓘ.";
        if(!text.contentEquals(cleaningDetail.getText()))cleaningDetail.setText(text);
        for(int i=0;i<functionOptions.size();i++){Button choice=functionOptions.get(i);boolean selected=(functionChoiceConfirmed||cleaningPreview>=0)&&i==program.ordinal();if(choice.isSelected()!=selected)optionState(choice,selected);}
    }
    private void updateCleaningRunSummary(){
        if(!cleaning())return;
        ControlClient client=ControlClient.get(activity);ControlState state=client.state;long now=android.os.SystemClock.elapsedRealtime();
        boolean fresh=state.foreground&&state.fresh(now)&&state.error==0&&!state.potMissing();
        int remaining=client.program.active()&&fresh?client.program.remaining(now):-1;
        String remainingText=remaining<0?"— Min — Sek":String.format(Locale.GERMANY,"%d Min %02d Sek",remaining/60,remaining%60);
        String actual=fresh&&state.temperature>0&&state.temperature<=250?state.temperature+" °C":"—";
        int stage=client.program.speed();String stageText=client.program.active()&&fresh?SPEED_LABELS[Math.min(100,Math.abs(stage))]+(stage<0?" · Linkslauf":""):"—";
        String readingsText="Aktuell "+actual+" · Stufe "+stageText;
        if(!function.cleaning.title.contentEquals(cleaningRunProgram.getText()))cleaningRunProgram.setText(function.cleaning.title);
        if(!remainingText.contentEquals(cleaningRemaining.getText())){
            android.text.SpannableString text=new android.text.SpannableString(remainingText);
            int minuteUnit=remainingText.indexOf(" Min "),secondUnit=remainingText.indexOf(" Sek");
            text.setSpan(new android.text.style.RelativeSizeSpan(.24f),minuteUnit,minuteUnit+5,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new android.text.style.RelativeSizeSpan(.24f),secondUnit,text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),minuteUnit,minuteUnit+5,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),secondUnit,text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cleaningRemaining.setText(text);
        }
        if(!readingsText.contentEquals(cleaningReadings.getText()))cleaningReadings.setText(readingsText);
    }
    private void updateRiceSummary(){
        HomeFunctionProfile.Preset[] presets=function.presets();
        HomeFunctionProfile.Preset selected=presets[Math.max(0,Math.min(riceSelection,presets.length-1))];
        android.text.SpannableString duration=new android.text.SpannableString((selected.seconds/60)+" Min");
        int unit=duration.toString().indexOf(" Min");
        duration.setSpan(new android.text.style.RelativeSizeSpan(.25f),unit,duration.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        duration.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),unit,duration.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        riceDuration.setText(duration);
        riceKind.setText((riceSelection==0?"Weißreis":"Vollkorn")+" · Richtwert inkl. Aufheizen");
    }
    private Button optionButton(String text,String tag){
        Button b=plainButton(text,tag);b.setTextSize(22);b.setSingleLine(true);b.setPadding(dp(10),0,dp(10),0);b.setStateListAnimator(null);b.setElevation(0);optionState(b,false);return b;
    }
    private void optionState(View view,boolean selected){
        view.setSelected(selected);if(!(view instanceof Button))return;
        Button b=(Button)view;b.setTextColor(Ui.ACCENT);b.setTypeface(selected?MEDIUM:REGULAR);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setCornerRadius(dp(ACTION_HEIGHT/2));shape.setColor(selected?(0x20000000|(Ui.ACCENT&0x00ffffff)):android.graphics.Color.TRANSPARENT);shape.setStroke(dp(1),Ui.ACCENT);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30000000|(Ui.ACCENT&0x00ffffff)),shape,null));
        b.setContentDescription(b.getText().toString().replace("\uFFFC", "").trim()+(selected?", ausgewählt":"")+(water()?". Auswählen und erhitzen":rice()?". Auswählen und Reis kochen":cleaning()?". Antippen startet das Spülprogramm":". Bestätigen und starten"));
    }
    private void addOption(Button b){if(function.cleaning!=null){
        int index=functionOptions.size()-1;boolean firstInRow=index==0||index==3;
        LinearLayout row;
        if(firstInRow){row=new LinearLayout(activity);row.setOrientation(HORIZONTAL);row.setBaselineAligned(false);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(ACTION_HEIGHT));if(index>0)size.topMargin=dp(14);cleaningChoices.addView(row,size);}
        else row=(LinearLayout)cleaningChoices.getChildAt(cleaningChoices.getChildCount()-1);
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);if(!firstInRow)cell.leftMargin=dp(14);row.addView(b,cell);return;
    }LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);if(functionExtras.getChildCount()>0)size.leftMargin=dp(14);functionExtras.addView(b,size);}
    public void eggGrade(int grade){selectedEggGrade=grade;int selected=grade<0?3:grade==3?2:grade;for(int i=0;i<functionExtras.getChildCount();i++)optionState(functionExtras.getChildAt(i),i==selected);updateEggDetail();}
    private void updateEggDetail(){
        if(!function.id.equals("eggs"))return;
        int selected=selectedEggGrade<0?3:selectedEggGrade==3?2:selectedEggGrade;
        com.kitchenos.connect.control.EggCooking clock=ControlClient.get(activity).eggCooking;
        String phase=clock.suspended()?"Unterbrochen · weiterkochen bestätigen":clock.preheating()?"Aufheizen · Garzeit startet bei 100 °C":clock.active()?"Garzeit läuft ab 100 °C":clock.completed()?"Garzeit beendet · Eier entnehmen":"Versuchszeit ab 100 °C";
        String next="250 ml Wasser · Eier im Gareinsatz\n"+(selectedEggGrade<0?"Eigene Zeit":new String[]{"Weich","Wachsweich","Hart"}[selected])+" · "+phase;
        if(!functionDetail.getText().toString().equals(next))functionDetail.setText(next);
    }
    private String primaryLabel="";private boolean primaryEnabled,primaryStopping,primaryPaused,primaryIdle;
    public void primaryState(String label,boolean actionable,boolean stopping){
        ControlState state=ControlClient.get(activity).state;
        // A blocked choice retains its explicit retry. Only a submitted run consumes
        // the choice, and only confirmed idle restores it (never an interval pause,
        // a lid interruption or missing STOP acknowledgement).
        if(!functionChoiceConfirmed)functionChoiceRunObserved=false;
        else if(state.ownRun||state.pendingAction.equals("START")||ControlClient.get(activity).program.active())functionChoiceRunObserved=true;
        if(functionChoiceRunObserved&&!stopping&&state.idleConfirmed&&state.fresh(android.os.SystemClock.elapsedRealtime())
                &&!state.deviceFaultLatched&&!ControlClient.get(activity).lidInterruption.active()&&canChooseFrying()){
            if(rice())time.value(function.presets()[riceSelection].seconds);
            functionChoiceConfirmed=rice();functionChoiceRunObserved=false;primaryDirty=true;
            if(function.cleaning!=null){functionPager.controlsAvailable(false);functionPager.show(1,false);}
        }

        boolean paused=ControlClient.get(activity).lidInterruption.active()&&!ControlClient.get(activity).state.ownRun&&ControlClient.get(activity).state.idleConfirmed&&!ControlClient.get(activity).state.stopping;
        boolean idleStart=!water()&&!rice()&&!cleaning()&&!label.isEmpty()&&function.runnable()&&(function.id.equals("manual")||functionOptions.isEmpty()||functionChoiceConfirmed)&&!stopping&&!label.equals("Messbecher bestätigen");
        if(!primaryDirty&&primaryLabel.equals(label)&&primaryEnabled==actionable&&primaryStopping==stopping&&primaryPaused==paused&&primaryIdle==idleStart)return;
        primaryDirty=false;primaryIdle=idleStart;
        primaryLabel=label;primaryEnabled=actionable;primaryStopping=stopping;primaryPaused=paused;endPaused.setVisibility(paused?VISIBLE:GONE);endPaused.setEnabled(paused);
        boolean asking=label.equals("Messbecher bestätigen"),replaces=stopping||asking||idleStart;functionHelp.setEnabled(!stopping&&!asking);if(stopping&&functionHelpDialog!=null)functionHelpDialog.dismiss();
        String hint=idleStart?(label.equals("Starten")?"Start":label):asking?"Bestätigen und starten":stopping?label:label.equals("Starten")?"Drehknopf drücken → Starten":label;
        if(!hint.contentEquals(start.getText()))start.setText(hint);
        start.setContentDescription(hint);
        startStatus.setVisibility(cleaning()&&stopping&&label.equals("Abbrechen")?VISIBLE:GONE);
        start.setEnabled(actionable);start.setClickable(replaces);
        start.setVisibility(replaces||label.equals("Startet …")?VISIBLE:INVISIBLE);
        FrameLayout.LayoutParams actionSize=(FrameLayout.LayoutParams)start.getLayoutParams();
        int pauseWidth=Math.max(dp(220),(getWidth()-dp(136))/2);actionSize.width=paused?pauseWidth:asking?dp(404):stopping||idleStart?dp(440):-1;actionSize.height=dp(replaces?104:80);actionSize.gravity=asking||paused?Gravity.TOP|Gravity.LEFT:Gravity.CENTER_HORIZONTAL|(stopping||idleStart?Gravity.TOP:Gravity.BOTTOM);actionSize.leftMargin=dp(60);actionSize.rightMargin=asking||paused?0:dp(60);actionSize.topMargin=replaces?dp(rice()?432:wheelHeight()+((function.puree()||function.cleaning!=null||warm())&&idleStart?SHORTCUT_HEIGHT+12:asking?72:water()?92:12)):0;actionSize.bottomMargin=replaces?0:dp(12);if(functionReadout()&&!water()&&replaces&&!asking)actionSize.topMargin=functionActionTop();if(cleaning()&&replaces)actionSize.topMargin=Math.max(dp(440),functionPager.getLayoutParams().height-dp(120));start.setLayoutParams(actionSize);
        FrameLayout.LayoutParams startStatusSize=(FrameLayout.LayoutParams)startStatus.getLayoutParams();startStatusSize.topMargin=actionSize.topMargin-dp(56);startStatus.setLayoutParams(startStatusSize);
        cupQuestion.setVisibility(asking?VISIBLE:GONE);cupCancel.setVisibility(asking?VISIBLE:GONE);cupCancel.setEnabled(asking);
        ((FrameLayout.LayoutParams)cupQuestion.getLayoutParams()).topMargin=dp(wheelHeight()+4);cupQuestion.requestLayout();
        ((FrameLayout.LayoutParams)cupCancel.getLayoutParams()).topMargin=dp(wheelHeight()+72);cupCancel.requestLayout();
        guidancePage.setPadding(dp(60),dp(20),dp(60),dp(stopping||asking?Math.max(104,600-wheelHeight()):12));
        FrameLayout.LayoutParams endSize=(FrameLayout.LayoutParams)endPaused.getLayoutParams();endSize.width=pauseWidth;endSize.height=dp(104);endSize.gravity=Gravity.TOP|Gravity.RIGHT;endSize.rightMargin=dp(60);endSize.topMargin=actionSize.topMargin;endPaused.setLayoutParams(endSize);
        start.setTextSize((state.recovering||state.uncertainRun||state.deviceFaultLatched)?26:paused?28:asking?23:idleStart&&!label.equals("Starten")?26:replaces?36:18);start.setTextColor(paused||idleStart?(actionable?0xff171716:Ui.MUTED):stopping?0xffffffff:asking?0xff171716:Ui.MUTED);
        start.setBackground(replaces?ControlFocus.background(activity,asking||paused||idleStart?Ui.ACCENT:0xffc43c43,52):null);
        for(int i=0;i<functionExtras.getChildCount();i++)functionExtras.getChildAt(i).setEnabled(!replaces||function.puree()&&idleStart);
        for(Button option:functionOptions)option.setEnabled(water()||rice()||cleaning()?!stopping&&!asking&&canChooseFrying():!replaces||function.puree()&&idleStart);
        cleaningPage.setVisibility(asking?INVISIBLE:VISIBLE);
        if(cupPromptShown!=asking){cupPromptShown=asking;questionPulse(asking);cupChoiceChanged.accept(asking);}
        showQuick(quick.getVisibility()==VISIBLE&&!stopping&&!asking);
        knobStateChanged.run();
    }

    /** The knob follows the same signed blade/CCW scale as the touch wheel. */
    public void turnSpeed(int delta){
        if(!speed.isEnabled()||delta==0)return;Face face=faces[2];int current=face.index(0),next=Math.max(face.speedMinPosition(),Math.min(face.speedMaxPosition(),current+delta));
        if(current<0&&next>0||current>0&&next<0)next=0;
        boolean wantReverse=next==0?reverse.getAsBoolean():next<0;if(!chooseDirection.test(wantReverse))return;
        int value=face.speedPreviewValue(next);if(value<0)return;speed.value(value);directionChanged();refresh();
    }
    /** Taller optional wheel window for evaluating visible, tappable neighboring numbers. */
    public void expandedWheelPreview(boolean expanded){
        if(expandedWheelPreview==expanded)return;expandedWheelPreview=expanded;updateWheelGeometry();
    }
    public void directTrackingPreview(boolean enabled){if(directTrackingPreview==enabled)return;directTrackingPreview=enabled;updateWheelGeometry();}
    /** Optional local center latch on the direct drawing; measured selection stays unchanged. */
    public void directDetentPreview(boolean enabled){directDetentPreview=enabled;for(Face face:faces)face.model.invalidate();}
    private boolean directWheel(){return expandedWheelPreview&&directTrackingPreview;}
    private int wheelHeight(){return expandedWheelPreview?(directWheel()?NumberWheelStyle.HEIGHT_DP:280):NUMBER_HEIGHT;}
    private void updateWheelGeometry(){
        int height=dp(wheelHeight());View row=findViewWithTag("home.numbers");row.getLayoutParams().height=height;
        for(Face face:faces){face.stopMotionBridge();face.wheelDrag=directWheel()?HomeControlMath.WheelDrag.direct():expandedWheelPreview?HomeControlMath.WheelDrag.deliberate():new HomeControlMath.WheelDrag();face.model.getLayoutParams().height=height;face.model.invalidate();}
        ((FrameLayout.LayoutParams)quick.getLayoutParams()).topMargin=height+dp(10);
        setMinimumHeight(dp(VESSEL_HEIGHT)+functionPager.getLayoutParams().height);requestLayout();
    }
    /** Reserve the complete shelf below; only remove spare space after the controls. */
    public void fitWorkspace(int availableHeight){
        int height=Math.max(dp(wheelHeight()+(SHORTCUT_HEIGHT+ACTION_HEIGHT)),Math.min(dp(600),availableHeight-dp(VESSEL_HEIGHT)));
        if(functionPager.getLayoutParams().height==height)return;
        functionPager.getLayoutParams().height=height;setMinimumHeight(dp(VESSEL_HEIGHT)+height);requestLayout();
    }
    public void detentedWheelPreview(boolean enabled){detentedWheelPreview=enabled;}
    public void thresholdSnapPreview(boolean enabled){thresholdSnapPreview=enabled;}
    /** Opt-in rendering experiment; measured events alone still select and commit values. */
    public void motionBridgePreview(boolean enabled){motionBridgePreview=enabled;for(Face face:faces)face.stopMotionBridge();}
    public void motionBridgeBrakingPreview(boolean enabled){for(Face face:faces){face.stopMotionBridge();face.motionBridge.adaptiveBraking(enabled);}}
    public void retainSceneForNavigation(boolean retain){vesselPhoto.retainForNavigation(retain);}
    public void resumeScale(){hostActive=true;ControlClient.get(activity).potMonitoring(this,true);scaleAttempted=false;scalePending=false;nextIdleConnect=0;main.removeCallbacks(scalePoll);if(isAttachedToWindow()&&getWindowVisibility()==VISIBLE){ControlClient.get(activity).addObserver(scaleChanges);main.post(scalePoll);}}
    public void pauseScale(){if(functionHelpDialog!=null)functionHelpDialog.dismiss();questionPulse(false);ControlClient.get(activity).potMonitoring(this,false);ControlClient.get(activity).removeObserver(scaleChanges);hostActive=false;main.removeCallbacks(scalePoll);if(potInspection!=null)potInspection.dismiss();cup.reset();for(Face face:faces)face.stopMotionBridge();}
    public void windowTouch(int action){long now=android.os.SystemClock.elapsedRealtime();if(action==MotionEvent.ACTION_DOWN){readings.touch(true,now);ControlClient.get(activity).potTouch(this,true);vesselPhoto.interaction(true);}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){readings.touch(false,now);ControlClient.get(activity).potTouch(this,false);vesselPhoto.interaction(false);}}
    public boolean cupReady(){ControlState s=ControlClient.get(activity).state;cup.observe(s,android.os.SystemClock.elapsedRealtime());return cup.inserted();}
    public void windowKnob(android.view.KeyEvent event){int key=event.getKeyCode();if(key!=android.view.KeyEvent.KEYCODE_F3&&key!=android.view.KeyEvent.KEYCODE_F4&&key!=android.view.KeyEvent.KEYCODE_F5)return;windowTouch(event.getAction()==android.view.KeyEvent.ACTION_DOWN?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_UP);}
    public void windowKnobRotation(){windowTouch(MotionEvent.ACTION_UP);}
    public void confirmCup(){ControlState s=ControlClient.get(activity).state;if(!s.ownRun&&!s.stopping&&s.pendingAction.isEmpty())cup.confirm(s,android.os.SystemClock.elapsedRealtime());refreshScale();}
    public void requireCup(boolean required){if(cupRequired==required)return;cupRequired=required;vesselPhoto.requireCup(required);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        // ScrollView first measures without a height limit; only consume extra space
        // once its fill-viewport pass gives this weighted Home block an exact height.
        if(vessel!=null)vessel.getLayoutParams().height=MeasureSpec.getMode(heightSpec)==MeasureSpec.EXACTLY?Math.max(dp(VESSEL_HEIGHT),MeasureSpec.getSize(heightSpec)-dp(wheelHeight()+SHORTCUT_HEIGHT+ACTION_HEIGHT)):dp(VESSEL_HEIGHT);
        int endpoint=dp(SCALE_TOP-TOP_SPACE)+Math.round(MeasureSpec.getSize(widthSpec)*.495f);
        ((FrameLayout.LayoutParams)tare.getLayoutParams()).topMargin=endpoint-dp(24);
        ((FrameLayout.LayoutParams)weight.getLayoutParams()).topMargin=endpoint-dp(32);
        ((FrameLayout.LayoutParams)scaleStatus.getLayoutParams()).topMargin=endpoint+dp(24);
        super.onMeasure(widthSpec,heightSpec);
    }
    private TextView label(String text,int size){TextView v=new TextView(activity);v.setText(text);v.setTextSize(size);v.setTextColor(Ui.MUTED);v.setGravity(Gravity.CENTER);return v;}
    private String programText(ControlClient client){String status=client.program.status();if(function.id.equals("fry"))return function.frying.label+"\n"+status;return function.cleaning==null?status:function.cleaning.title+"\n"+(status.equals("Programm beendet")?"Beendet · Wasser ausgießen und klar nachspülen":status);}
    public void refresh(){updateCleaningExplanation();updateEggDetail();ControlClient c=ControlClient.get(activity);if(c.program.active()){String text=programText(c);if(!functionDetail.getText().toString().equals(text))functionDetail.setText(text);}for(Face face:faces)face.changed();updateMarks();directionChanged();}
    public Button tareButton(){return tare;}
    public void startHint(){showFunctionControls();vesselPhoto.startHint();}
    public void startCue(){cup.indicateStart();vesselPhoto.startCue();refreshScale();}
    private void refreshScale(){
        updateCleaningRunSummary();
        functionPager.updateArrows();
        ControlClient client=ControlClient.get(activity);ControlState state=client.state;long now=android.os.SystemClock.elapsedRealtime();if(client.program.active()||hadProgram){String phase=programText(client);if(!functionDetail.getText().toString().equals(phase))functionDetail.setText(phase);hadProgram=client.program.active();}updateEggDetail();cup.observe(state,now);vesselPhoto.update(state,now,cup);
        updatePresentation(state,now);
        if(quick.getVisibility()==VISIBLE)updateMarks();
        if(!state.foreground){scaleAttempted=scalePending=false;readings.reset();weight.setText("— g");scaleArc.reading(null);vesselPhoto.weight(null);tare.setEnabled(false);scaleArc.tareAvailable(false);return;}
        boolean idle=!ControlClient.get(activity).program.active()&&!state.ownRun&&!state.stopping&&!state.uncertainRun&&!state.deviceFaultLatched&&!state.potCheck.active()&&state.pendingAction.isEmpty();
        if(scalePending&&!state.rootChecking){scalePending=false;if(state.rootVerified&&idle&&!state.busy()){nextIdleConnect=now+2000;client.connect();}}
        // Recover a read-only idle display after an ended bridge. Never resume a run.
        if(idle&&!state.busy()&&!state.rootChecking&&now>=nextIdleConnect&&(!scaleAttempted||state.rootVerified)){
            scaleAttempted=true;nextIdleConnect=now+2000;
            if(state.rootVerified)client.connect();else{scalePending=true;client.checkRoot();}
        }
        readings.observe(state,now);String text=readings.text();if(!weight.getText().toString().equals(text))weight.setText(text);
        scaleArc.reading(readings.grams());vesselPhoto.weight(state.fresh(now)&&showVesselInterior(state,now)?readings.grams():null);weight.setAlpha(readings.grams()==null?.55f:1);scaleStatus.setVisibility(GONE);weight.setContentDescription("Waage: "+text+(readings.stable(now)?". Stabil":". Stabilisierung abwarten"));
        tare.setEnabled(idle&&!state.potMissing()&&!state.potCheck.required()&&state.connected&&!state.closing&&!state.rootChecking);scaleArc.tareAvailable(tare.isEnabled());
    }
    private boolean showVesselInterior(ControlState state,long now){
        ControlClient c=ControlClient.get(activity);
        boolean warm=(state.foreground&&state.connected&&c.temperatureHistory.fresh(now)||c.temperatureHistory.hot())&&c.temperatureHistory.temperature()>35;
        boolean active=c.program.active()||state.foreground&&state.fresh(now)&&state.ownRun&&!state.stopping&&state.state!=3&&!state.potMissing();
        // Keep interval pauses open; weight alone must not reveal a cold, idle blade.
        return !"blade".equals(function.tool)||stirrerSelected||heat.value()>0||warm||active;
    }
    private void updatePresentation(ControlState state,long now){
        modeShown=false;modeView.setVisibility(GONE);attachment.setVisibility(GONE);
        scaleView.setVisibility(VISIBLE);scaleView.setAlpha(1);scaleArc.setVisibility(VISIBLE);
        ControlClient c=ControlClient.get(activity);vesselPhoto.mode(c.program.active()?c.program.speed()<0:reverse.getAsBoolean(),stirrerSelected,showVesselInterior(state,now));vesselPhoto.level(c.program.active()?Math.abs(c.program.speed())/10f:speed.value()/10f);
        attachment.setEnabled(!state.ownRun&&!state.stopping&&state.pendingAction.isEmpty());
    }
    public void directionChanged(){boolean next=reverse.getAsBoolean();if(renderedReverse!=null&&renderedReverse==next)return;renderedReverse=next;direction.setImageDrawable(new CookingToolIcon(reverse.getAsBoolean()));String text=reverse.getAsBoolean()?"Rühren · ↶":"Zerkleinern · ↷";direction.setContentDescription(text+". Stufe nach oben ziehen: Messer. Nach unten: Rühren.");directionText.setText(text);directionText.setTextColor(reverse.getAsBoolean()?0xff63c49a:0xffef655a);}
    private boolean motion(){return isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();}
    private void activate(Face face,int part){
        if(water()){ControlClient.get(activity).waterHeating.clearResult();active=face;activePart=part;dismissShortcuts();return;}
        if(!function.puree()&&!function.id.equals("eggs")&&!primaryStopping&&!functionOptions.isEmpty()&&functionChoiceConfirmed){functionChoiceConfirmed=false;primaryDirty=true;primaryState(primaryLabel,primaryEnabled,false);}
        main.removeCallbacks(expire);generation++;functionInfo.setVisibility(INVISIBLE);boolean changed=active!=face||activePart!=part||quick.getVisibility()!=VISIBLE;active=face;activePart=part;increment=-1;modeUntil=face.kind==2?android.os.SystemClock.elapsedRealtime()+4000:0;updatePresentation(ControlClient.get(activity).state,android.os.SystemClock.elapsedRealtime());
        for(Button b:shortcuts){b.animate().cancel();b.setAlpha(1);b.setTranslationY(0);}
        if(changed){quick.removeAllViews();shortcuts.clear();shortcutValues.clear();int[] choices=FunctionShortcuts.choices(function,face.kind,part,face.model.minimum(),face.model.maximum(),face.model::accepts);
            for(int i=0;i<choices.length;i++){
                if(turbo()&&!face.model.accepts(choices[i]))continue;
                final int choice=choices[i];Button button=new Button(activity);button.setText(face.kind==0?"+"+choice:face.kind==1?(choice==0?"---":choice+"°"):SPEED_LABELS[choice]);button.setAllCaps(false);button.setTextSize(27);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(0,0,0,0);button.setBackground(ControlFocus.background(activity,Ui.CARD));button.setStateListAnimator(null);button.setElevation(0);button.setDefaultFocusHighlightEnabled(false);button.setTextColor(Ui.MUTED);button.setTag("home.quick."+face.kind+"."+part+"."+choice);button.setContentDescription(face.kind==0?choice+(slow()?(part==0?" Stunden":" Minuten"):(part==0?" Minuten":" Sekunden"))+" hinzufügen":face.kind==1?choice+" Grad Celsius":"Stufe "+SPEED_LABELS[choice]);
                // The circle is inset; Android dispatches taps across the full square button.
                button.setBackground(new android.graphics.drawable.InsetDrawable(ControlFocus.background(activity,Ui.CARD,QUICK_SIZE/2),dp(4)));
                if(turbo()){button.setText(face.kind==0?choice+" s":String.valueOf(choice/10));button.setTextSize(34);button.setContentDescription(face.kind==0?choice+" Sekunden":"Stufe "+SPEED_LABELS[choice]);}
                if(face.kind==1&&choice==180&&(function.id.equals("manual")||function.id.equals("fry"))){
                    android.graphics.drawable.Drawable flame=new ModeIcon("fry");flame.setBounds(0,0,dp(18),dp(18));
                    android.text.SpannableString label=new android.text.SpannableString("180° \ufffc");label.setSpan(new android.text.style.ImageSpan(flame,android.text.style.ImageSpan.ALIGN_BASELINE),5,6,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);button.setText(label);button.setContentDescription("180 Grad Celsius, in den Bratmodus wechseln");
                }
                button.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){main.removeCallbacks(expire);generation++;}else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)scheduleHide();return false;});
                button.setOnClickListener(v->{if(!face.model.isEnabled()||!button.isEnabled())return;activate(face,part);face.part=part;
                    if(face.kind==1&&choice==180&&(function.id.equals("manual")||function.id.equals("fry"))){if(canChooseFrying()&&chooseFrying.getAsBoolean()){face.tick();finishEdit.accept(face.model);face.pulse();refresh();scheduleHide();}return;}
                    if(face.kind==1&&function.id.equals("fry")&&choice<=100){if(canChooseFrying()&&chooseManualTemperature.test(choice)){face.tick();finishEdit.accept(face.model);face.pulse();refresh();scheduleHide();}return;}
                    face.model.beginNumberEdit();int before=face.model.value();
                    if(face.kind==0){face.model.value(turbo()?choice:Math.min(face.model.maximum(),face.model.value()+choice*(slow()?(part==0?3600:60):(part==0?60:1))));increment=choice;}
                    else face.model.value(choice);
                    if(face.model.value()!=before)face.tick();face.model.commitNumberEdit();finishEdit.accept(face.model);face.pulse();refresh();scheduleHide();if(face.kind==2&&face.model.value()>0)speedSelected.run();});
                LinearLayout.LayoutParams shortcutSize=new LinearLayout.LayoutParams(dp(QUICK_SIZE),dp(QUICK_SIZE));quick.addView(button,shortcutSize);shortcuts.add(button);shortcutValues.add(choice);
                if(motion()){button.setAlpha(0);button.setTranslationY(dp(4));button.animate().alpha(1).translationY(0).setStartDelay(i*28L).setDuration(200).start();}
            }
        }
        showQuick(!shortcuts.isEmpty()&&!primaryStopping&&!cupPromptShown);updateMarks();scheduleHide();
    }
    private void showQuick(boolean visible){
        // Retain the title and read-only running values; only program choices
        // are replaced by the existing Cancel/Stop action.
        boolean cleaningRun=cleaning()&&primaryStopping;
        if(cleaning()){
            cleaningPage.setVisibility(cupPromptShown?INVISIBLE:VISIBLE);
            cleaningRunSummary.setVisibility(cleaningRun?VISIBLE:GONE);
            cleaningPage.findViewWithTag("home.cleaning.preparation").setVisibility(cleaningRun?GONE:VISIBLE);
            cleaningPage.findViewWithTag("home.cleaning.scroll").setVisibility(cleaningRun?GONE:VISIBLE);
            cleaningDetail.setVisibility(cleaningRun?GONE:VISIBLE);
            updateCleaningRunSummary();
        }
        functionPager.findViewWithTag("home.function.page.0").setVisibility(VISIBLE);
        functionPager.findViewWithTag("home.function.page.1").setVisibility(VISIBLE);
        View help=functionPager.findViewWithTag("home.function.help");if(cleaning()&&help!=null)help.setVisibility(VISIBLE);

        boolean controls=functionPager.page()==1;
        if(!controls||water()||cleaning())visible=false;
        quick.setVisibility(visible?VISIBLE:INVISIBLE);
        boolean replaces=primaryStopping||primaryIdle&&controls||cupPromptShown;
        start.setVisibility(!visible&&(replaces||primaryLabel.equals("Startet …"))?VISIBLE:INVISIBLE);
        start.setClickable(!visible&&replaces);
        if(standardFunctionLayout()){
            functionTitle.setVisibility((function.puree()||function.cleaning!=null)&&functionChoiceConfirmed?VISIBLE:GONE);
            ((FrameLayout.LayoutParams)quick.getLayoutParams()).topMargin=functionActionTop();quick.requestLayout();
        }else functionTitle.setVisibility(VISIBLE);
        updatePureeExplanation();updateCleaningExplanation();if(function.id.equals("eggs")){functionTitle.setVisibility(functionChoiceConfirmed?VISIBLE:GONE);functionTitle.setText("‹ Gargrad ändern");functionTitle.setOnClickListener(v->showFunctionOptions());}functionInfo.setVisibility(singleTemperatureLayout()||rice()||cleaning()||visible||replaces&&!((functionReadout()||rice())&&primaryIdle)?INVISIBLE:VISIBLE);
        functionExtras.setVisibility(rice()?(visible||primaryStopping||cupPromptShown?INVISIBLE:VISIBLE):water()?(primaryStopping||cupPromptShown?INVISIBLE:VISIBLE):visible||replaces?INVISIBLE:VISIBLE);
    }
    private void updateMarks(){
        if(active==null)return;
        for(int i=0;i<shortcuts.size();i++){
            Button button=shortcuts.get(i);int value=shortcutValues.get(i);boolean selected=active.kind==0&&!turbo()?value==increment:active.model.value()==value;
            int flags=selected?button.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG:button.getPaintFlags()&~Paint.UNDERLINE_TEXT_FLAG;if(button.getPaintFlags()!=flags)button.setPaintFlags(flags);
            Typeface font=selected?MEDIUM:REGULAR;if(button.getTypeface()!=font)button.setTypeface(font);if(button.isSelected()!=selected)button.setSelected(selected);
            boolean modeShortcut=active.kind==1&&(function.id.equals("manual")&&value==180||function.id.equals("fry")&&(value==180||value<=100));
            boolean enabled=active.model.isEnabled()&&(modeShortcut?canChooseFrying():active.kind==0&&!turbo()?active.model.value()<active.model.maximum():active.model.accepts(value));
            if(button.isEnabled()!=enabled)button.setEnabled(enabled);
            int color=enabled?(selected?Ui.ACCENT:Ui.MUTED):Color.argb(90,Color.red(Ui.MUTED),Color.green(Ui.MUTED),Color.blue(Ui.MUTED));if(button.getCurrentTextColor()!=color)button.setTextColor(color);
        }
    }
    private void scheduleHide(){main.removeCallbacks(expire);if(!holding&&active!=null)main.postDelayed(expire,4000);}
    private void fadeOut(){if(holding)return;final int token=++generation;for(int i=0;i<shortcuts.size();i++){Button b=shortcuts.get(i);b.animate().cancel();if(motion())b.animate().alpha(0).translationY(dp(4)).setStartDelay(i*28L).setDuration(180).start();}main.postDelayed(()->{if(token==generation){showQuick(false);for(Button b:shortcuts)b.animate().cancel();}},motion()?180+Math.max(0,shortcuts.size()-1)*28:0);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(hostActive){ControlClient.get(activity).potMonitoring(this,true);ControlClient.get(activity).addObserver(scaleChanges);main.post(scalePoll);}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(main==null)return;main.removeCallbacks(scalePoll);ControlClient.get(activity).removeObserver(scaleChanges);if(visibility==VISIBLE&&hostActive&&isAttachedToWindow()){ControlClient.get(activity).potMonitoring(this,true);ControlClient.get(activity).addObserver(scaleChanges);main.post(scalePoll);}else{ControlClient.get(activity).potMonitoring(this,false);scaleAttempted=false;scalePending=false;readings.reset();cup.reset();}}
    @Override protected void onDetachedFromWindow(){questionPulse(false);ControlClient.get(activity).potMonitoring(this,false);ControlClient.get(activity).removeObserver(scaleChanges);main.removeCallbacksAndMessages(null);readings.reset();cup.reset();for(Button b:shortcuts)b.animate().cancel();super.onDetachedFromWindow();}

    final class Face implements ParameterDial.NumberFace {
        final ParameterDial model;final int kind;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float[] shown={Float.NaN,Float.NaN};final int[] target={Integer.MIN_VALUE,Integer.MIN_VALUE};final ValueAnimator[] rolls=new ValueAnimator[2];
        int part,startValue,startIndex;boolean dragging,moved,lastLive,lastMotor,startReverse;float downY,upRoom,downRoom,pulse,startPosition;ValueAnimator pop;ArrayList<Integer> gestureChoices;
        private HomeControlMath.WheelDrag wheelDrag=new HomeControlMath.WheelDrag();
        private final HomeControlMath.DetentDrag detentDrag=new HomeControlMath.DetentDrag();
        private final HomeControlMath.ZeroStopDrag directDrag=new HomeControlMath.ZeroStopDrag();
        private final TouchMotionBridge motionBridge=new TouchMotionBridge();
        private float motionOffset;
        private final Runnable bridgeFrame=new Runnable(){public void run(){
            if(!dragging||!moved||!expandedWheelPreview||!motionBridgePreview||!motion()||!model.isEnabled()){stopMotionBridge();return;}
            long now=android.os.SystemClock.uptimeMillis();
            motionOffset=(motionBridge.frame(now)-motionBridge.measured())/wheelDrag.distancePerStep();
            model.invalidate();if(motionBridge.needsFrame(now))model.postOnAnimation(this);
        }};
        void stopMotionBridge(){model.removeCallbacks(bridgeFrame);motionBridge.finish();motionOffset=0;model.invalidate();}
        void scheduleMotionBridge(){if(motionBridgePreview&&expandedWheelPreview){model.removeCallbacks(bridgeFrame);model.postOnAnimation(bridgeFrame);}}
        private ArrayList<Integer> cachedChoices;
        private int cachedMin,cachedMax,cachedPart,cachedMinute;private boolean cachedReverse;
        private final Shader fade,expandedFade,directFade;private final android.graphics.Xfermode fadeMode=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        private int goalValue=Integer.MIN_VALUE;private String goalLabel;
        private final CookingToolIcon spoonIcon=new CookingToolIcon(true),bladeIcon=new CookingToolIcon(false);
        private final TopViewBladeIcon wheelBladeIcon=new TopViewBladeIcon();
        private final WheelSpoonIcon wheelSpoonIcon=new WheelSpoonIcon();
        Face(ParameterDial model,int kind){this.model=model;this.kind=kind;float d=getResources().getDisplayMetrics().density;fade=new LinearGradient(0,0,0,128*d,new int[]{Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT},new float[]{0,.30f,.70f,1},Shader.TileMode.CLAMP);expandedFade=NumberWheelStyle.fade(256*d);directFade=new LinearGradient(0,0,0,NumberWheelStyle.WINDOW_DP*d,new int[]{0x00ffffff,0x1fffffff,0x75ffffff,0xffffffff,0xffffffff,0x75ffffff,0x1fffffff,0x00ffffff},new float[]{.10f,.148f,.294f,.388f,.612f,.706f,.852f,.90f},Shader.TileMode.CLAMP);}
        int timeMajor(int seconds){return seconds/timeUnit();}
        int timeMinor(int seconds){return seconds%timeUnit()/(slow()?60:1);}
        int timeCompose(int major,int minor){return major*timeUnit()+minor*(slow()?60:1);}
        int timeDelta(int value){return slow()?value/(part==0?3600:60):value/(part==0?60:1);}
        ArrayList<Integer> choices(){
            int minimum=model.minimum(),maximum=model.maximum(),minute=kind==0&&part==1?timeMajor(model.value()):0;boolean backwards=kind==2&&reverse.getAsBoolean();
            if(cachedChoices!=null&&minimum==cachedMin&&maximum==cachedMax&&part==cachedPart&&minute==cachedMinute&&backwards==cachedReverse)return cachedChoices;
            cachedMin=minimum;cachedMax=maximum;cachedPart=part;cachedMinute=minute;cachedReverse=backwards;ArrayList<Integer> result=new ArrayList<>();if(kind==0){int max=part==0?timeMajor(model.maximum()):Math.min(59,(model.maximum()-timeMajor(model.value())*timeUnit())/(slow()?60:1));int min=part==0?timeMajor(model.minimum()):Math.max(0,(model.minimum()-timeMajor(model.value())*timeUnit())/(slow()?60:1));for(int n=min;n<=max;n++)result.add(n);}
            else for(int n=kind==2?0:model.minimum();n<=model.maximum();n++)if(kind==2&&n==0||model.accepts(n))result.add(n);if(result.isEmpty())result.add(0);cachedChoices=result;return result;}
        int numeric(int column){if(kind==0)return column==0?timeMajor(model.displayedSeconds()):timeMinor(model.displayedSeconds());if(liveTemperature())return model.currentReading();return model.value();}
        boolean liveTemperature(){return kind==1&&!dragging&&model.measuredTemperature();}
        int index(int column){int n=numeric(column);if(kind==0||liveTemperature())return n;ArrayList<Integer> values=choices();int i=values.indexOf(n);if(i>=0)return kind==2&&reverse.getAsBoolean()?-i:i;return 0;}
        int speedPreviewCount(boolean backwards){int count=0;for(int n=0;n<=model.maximum();n++)if(n==0||model.accepts(n))count++;return count;}
        int speedMinPosition(){return 1-speedPreviewCount(true);}
        int speedMaxPosition(){return warm()?-1:speedPreviewCount(false)-1;}
        int speedPreviewValue(int signedIndex){int at=0;for(int n=0;n<=model.maximum();n++)if(n==0||model.accepts(n)){if(at++==Math.abs(signedIndex))return n;}return -1;}
        @Override public void changed(){
            if(kind==2&&scaleView!=null)updatePresentation(ControlClient.get(activity).state,android.os.SystemClock.elapsedRealtime());
            if(kind==2&&expandedWheelPreview){String description=(warm()?"Linkslauf-Stufe ":"Stufe ")+SPEED_LABELS[model.value()]+(model.value()==0?" · Aus":reverse.getAsBoolean()?" · Rühren":" · Messer");if(!description.contentEquals(model.getContentDescription()))model.setContentDescription(description);}
            boolean live=liveTemperature();if(live!=lastLive){Arrays.fill(target,Integer.MIN_VALUE);lastLive=live;}
            if(kind==2&&model.liveMode()&&!lastMotor){shown[0]=0;target[0]=0;}lastMotor=model.liveMode();
            for(int i=0;i<(kind==0?2:1);i++){
                int next=index(i);if(dragging&&i==part){target[i]=next;continue;}if(target[i]==next)continue;float from=shown[i];if(rolls[i]!=null){rolls[i].cancel();rolls[i]=null;}
                if(kind==0&&!Float.isNaN(from))next=Math.round(HomeControlMath.nearestPosition(from,next,timeMaximum(i)-timeMinimum(i)+1));
                boolean animate=target[i]!=Integer.MIN_VALUE&&!dragging&&!liveTemperature()&&motion();target[i]=index(i);final int column=i,finish=target[i];
                if(!animate){shown[i]=finish;continue;}
                rollTo(column,from,next,finish,kind==1?320:220);
            }model.invalidate();
        }
        void rollTo(int column,float from,float end,int finish,long duration){
            ValueAnimator roll=ValueAnimator.ofFloat(from,end);rolls[column]=roll;roll.setDuration(duration);roll.setInterpolator(new android.view.animation.DecelerateInterpolator(2));roll.addUpdateListener(a->{shown[column]=(float)a.getAnimatedValue();model.invalidate();});
            roll.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(rolls[column]!=a)return;shown[column]=finish;rolls[column]=null;model.invalidate();}});roll.start();
        }
        void settle(){
            float from=shown[part];if(rolls[part]!=null){rolls[part].cancel();rolls[part]=null;}int finish=index(part);target[part]=finish;
            if(!motion()){shown[part]=finish;model.invalidate();return;}
            float end=kind==2||liveTemperature()?finish:HomeControlMath.nearestPosition(from,finish,choices().size());rollTo(part,from,end,finish,160);
        }
        void showDragPosition(float position){
            // A gentle positional pull toward the center, without a dead zone or per-number jump.
            shown[part]=detentedWheelPreview&&!directWheel()?HomeControlMath.detentPosition(position):position;
        }
        float visualPosition(int column){
            float position=shown[column]+(column==part&&dragging&&rolls[column]==null?motionOffset:0);
            if(kind==2&&expandedWheelPreview&&motionBridgePreview)position=Math.max(speedMinPosition(),Math.min(speedMaxPosition(),position));
            if(directWheel()&&dragging&&column==part)position=directDrag.constrainVisual(position);
            // Apply after the bridge, so tiny estimated movements also settle in the center zone.
            if(directWheel()&&directDetentPreview&&dragging&&moved&&column==part)position=HomeControlMath.directDetentPosition(position);
            return position;
        }
        void moveDirect(float steps,float fingerDp,long eventTime){
            int before=model.value();boolean oldReverse=reverse.getAsBoolean(),wasHeld=directDrag.holding();float position=directDrag.move(steps,fingerDp);
            if(wasHeld||directDrag.holding()){motionOffset=0;motionBridge.reset(steps*wheelDrag.distancePerStep(),eventTime);}
            if(kind==2){
                position=Math.max(speedMinPosition(),Math.min(speedMaxPosition(),position));
                int selected=Math.round(position);boolean wantReverse=selected==0?oldReverse:selected<0;
                if(!chooseDirection.test(wantReverse))return;directionChanged();model.value(speedPreviewValue(selected));
            }else{
                int selected=HomeControlMath.wheelIndex(startIndex,gestureChoices.size()-1,position-startPosition),next=gestureChoices.get(selected);
                if(kind==0){model.value(part==0?Math.min(model.maximum(),timeCompose(next,timeMinor(startValue))):timeCompose(timeMajor(startValue),next));increment=HomeControlMath.reachedIncrement(timeDelta(model.value()-startValue));}
                else model.value(next);
            }
            shown[part]=position;
            if(model.value()!=before||kind==2&&model.value()>0&&oldReverse!=reverse.getAsBoolean())tick();changed();updateMarks();
        }
        void showThresholdDrag(int previousCenter){
            final int column=part,center=detentDrag.center();
            if(center==previousCenter){
                if(rolls[column]==null)shown[column]=center+detentDrag.previewOffset();
                return;
            }
            float from=shown[column];ValueAnimator old=rolls[column];rolls[column]=null;if(old!=null)old.cancel();
            if(!motion()){shown[column]=center;return;}
            ValueAnimator roll=ValueAnimator.ofFloat(0,1);rolls[column]=roll;roll.setDuration(180);
            roll.setInterpolator(new android.view.animation.OvershootInterpolator(.8f));
            roll.addUpdateListener(a->{float progress=(float)a.getAnimatedValue();shown[column]=from+(center-from)*progress;model.invalidate();});
            roll.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(rolls[column]!=a)return;detentDrag.settled();shown[column]=center;rolls[column]=null;model.invalidate();}});roll.start();
        }
        void moveDetent(float steps){
            int previousCenter=detentDrag.center(),previous=model.value();boolean oldReverse=reverse.getAsBoolean();detentDrag.move(steps);int selected=detentDrag.center();
            if(kind==2){boolean wantReverse=selected==0?oldReverse:selected<0;if(!chooseDirection.test(wantReverse))return;directionChanged();model.value(speedPreviewValue(selected));}
            else{int next=gestureChoices.get(Math.floorMod(selected,gestureChoices.size()));
                if(kind==0){model.value(part==0?Math.min(model.maximum(),timeCompose(next,timeMinor(startValue))):timeCompose(timeMajor(startValue),next));increment=HomeControlMath.reachedIncrement(timeDelta(model.value()-startValue));}
                else model.value(next);
            }
            showThresholdDrag(previousCenter);if(model.value()!=previous||kind==2&&model.value()>0&&oldReverse!=reverse.getAsBoolean())tick();changed();updateMarks();
        }
        void tick(){model.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
        int timeMinimum(int column){return model.liveMode()?0:column==0?timeMajor(model.minimum()):Math.max(0,(model.minimum()-timeMajor(model.value())*timeUnit())/(slow()?60:1));}
        int timeMaximum(int column){return column==0?timeMajor(model.maximum()):model.liveMode()?59:Math.min(59,(model.maximum()-timeMajor(model.value())*timeUnit())/(slow()?60:1));}
        String formatIndex(int index,int column){
            if(kind==0)return TIME_LABELS[HomeControlMath.cyclicValue(index,timeMinimum(column),timeMaximum(column))];
            if(liveTemperature())return index<0||index>255?"":NUMBER_LABELS[index];
            if(kind==2&&expandedWheelPreview){int value=speedPreviewValue(index);return value<0?"":SPEED_LABELS[value];}
            ArrayList<Integer> values=choices();if(kind==2){if(reverse.getAsBoolean())index=-index;if(index<0||index>=values.size())return "";}
            int value=values.get(Math.floorMod(index,values.size()));return kind==1?(value==0?"---":NUMBER_LABELS[value]):SPEED_LABELS[value];
        }
        private void functionText(Canvas canvas,String text,float x,float y,float size,float maxWidth,int color){
            paint.setTypeface(MEDIUM);paint.setTextSize(size);float width=paint.measureText(text);
            drawText(canvas,text,x,y,width>maxWidth?size*maxWidth/width:size,color,1);
        }
        private void functionFocus(Canvas canvas,float cx,float half,float d){
            if(knobSlot!=kind||!model.isSelected()||!model.isEnabled())return;
            paint.setColor(Ui.ACCENT);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
            float top=kind==0&&timeOnlyLayout()?66:100,bottom=kind==0&&timeOnlyLayout()?187:235;
            canvas.drawRect(cx-half,top*d,cx+half,(top+3)*d,paint);
            canvas.drawRect(cx-half,bottom*d,cx+half,(bottom+3)*d,paint);
        }
        private void temperatureText(Canvas canvas,String target,String actual,float x,float y,float size,int color,float maxWidth){
            float live=model.temperatureLiveOpacity();
            if(live<1f){int layer=canvas.saveLayerAlpha(0,0,model.getWidth(),model.getHeight(),Math.round(255*(1-live)));functionText(canvas,target,x,y,size,maxWidth,color);canvas.restoreToCount(layer);}
            if(live>0f){int layer=canvas.saveLayerAlpha(0,0,model.getWidth(),model.getHeight(),Math.round(255*live));functionText(canvas,actual,x,y,size,maxWidth,color);canvas.restoreToCount(layer);}
        }
        private float timeSplit(){return functionReadout()?.5f:.56f;}
        private void drawSlowTime(Canvas canvas,float d,float w){
            float group=Math.min(w,300*d),cy=169*d;
            drawText(canvas,model.liveMode()?"Restzeit":"Zeit",w/2,76*d,21*d,Ui.MUTED,1);
            for(int column=0;column<2;column++){
                float cx=(w-group)/2+group*(column+.5f)/2-18*d;
                float position=visualPosition(column);int base=(int)Math.floor(position);
                int saved=canvas.save();canvas.clipRect(column==0?0:w/2,99*d,column==0?w/2:w,239*d);
                for(int offset=-1;offset<=2;offset++){
                    float distance=base+offset-position;
                    if(Math.abs(distance)>1.35f)continue;
                    if(timeMinimum(column)==timeMaximum(column)&&base+offset!=index(column))continue;
                    float emphasis=Math.max(0,1-Math.abs(distance));
                    float size=(30+70*emphasis*emphasis)*d,y=cy+distance*74*d;
                    String value=Integer.toString(HomeControlMath.cyclicValue(base+offset,timeMinimum(column),timeMaximum(column)));
                    if(column==1&&value.length()==1)value="0"+value;
                    drawText(canvas,value,cx,y,size,blend(Ui.BG,Ui.ACCENT,emphasis*emphasis),1);
                }
                canvas.restoreToCount(saved);
                drawText(canvas,column==0?"H":"Min",cx+(column==0?48:82)*d,cy+20*d,20*d,Ui.MUTED,1);
                functionFocus(canvas,cx,column==0?35*d:62*d,d);
            }
        }
        private void drawFunctionTime(Canvas canvas,float d,float w){
            boolean compact=timeOnlyLayout();float cy=(compact?128:169)*d;
            if(!compact)drawText(canvas,model.liveMode()?"Restzeit":"Zeit",w/2,76*d,21*d,Ui.MUTED,1);
            int seconds=model.displayedSeconds(),columns=turbo()?1:2;
            float group=Math.min(w,(turbo()?220:360)*d),cell=group/columns;
            for(int column=0;column<columns;column++){
                String unit=turbo()||column==1?"Sek":"Min";
                String value=Integer.toString(turbo()?seconds:column==0?timeMajor(seconds):timeMinor(seconds));
                float unitSize=18*d,gap=8*d;
                paint.setTypeface(REGULAR);paint.setTextSize(unitSize);float unitWidth=paint.measureText(unit);
                float size=(compact?128:112)*d;
                paint.setTypeface(MEDIUM);paint.setTextSize(size);float valueWidth=paint.measureText(value);
                float available=Math.max(20*d,cell-unitWidth-gap-16*d);
                if(valueWidth>available){size*=available/valueWidth;valueWidth=available;}
                float center=(w-group)/2+(column+.5f)*cell,x=center-(unitWidth+gap)/2;
                drawText(canvas,value,x,cy,size,Ui.ACCENT,1);
                drawText(canvas,unit,x+valueWidth/2+gap+unitWidth/2,cy+size*.30f,unitSize,Ui.MUTED,1);
                functionFocus(canvas,x,Math.min(60*d,valueWidth/2+8*d),d);
            }
        }
        /** Frying keeps the same gesture/knob positions; render their fractional motion. */
        private void drawFryingRoll(Canvas canvas,float d,float cx,float size,float width,int column){
            float position=visualPosition(column);int base=(int)Math.floor(position);
            int saved=canvas.save();canvas.clipRect(cx-width/2,99*d,cx+width/2,239*d);
            for(int offset=-1;offset<=2;offset++){
                float distance=base+offset-position;
                if(Math.abs(distance)>1.35f)continue;
                if(kind==0&&timeMinimum(column)==timeMaximum(column)&&base+offset!=index(column))continue;
                float emphasis=Math.max(0,1-Math.abs(distance));
                String value=kind==0?Integer.toString(HomeControlMath.cyclicValue(base+offset,timeMinimum(column),timeMaximum(column))):formatIndex(base+offset,column);
                functionText(canvas,value,cx,169*d+displayRow(distance)*d,28*d+(size-28*d)*emphasis*emphasis,width,blend(Ui.BG,Ui.ACCENT,emphasis*emphasis));
            }
            canvas.restoreToCount(saved);
        }
        private void drawFryingValues(Canvas canvas,float d,float w){
            if(kind==0){
                float group=Math.min(w,320*d),cell=group/2,unitSize=18*d,gap=6*d;
                for(int column=0;column<2;column++){
                    String unit=column==0?"Min":"Sek";
                    String value=Integer.toString(column==0?timeMajor(model.displayedSeconds()):timeMinor(model.displayedSeconds()));
                    paint.setTypeface(REGULAR);paint.setTextSize(unitSize);float unitWidth=paint.measureText(unit);
                    float size=100*d,available=cell-unitWidth-gap-12*d;
                    paint.setTypeface(MEDIUM);paint.setTextSize(size);float valueWidth=paint.measureText(value);
                    if(valueWidth>available){size*=available/valueWidth;valueWidth=available;}
                    float center=(w-group)/2+(column+.5f)*cell,cx=center-(unitWidth+gap)/2;
                    drawFryingRoll(canvas,d,cx,size,available,column);
                    drawText(canvas,unit,cx+valueWidth/2+gap+unitWidth/2,199*d,unitSize,Ui.MUTED,1);
                    functionFocus(canvas,cx,Math.min(56*d,valueWidth/2+8*d),d);
                }
                return;
            }
            float cx=w/2,live=dragging?0:model.temperatureLiveOpacity();
            temperatureText(canvas,"Zieltemperatur","Aktuelle Temperatur",cx,76*d,21*d,Ui.MUTED,w-20*d);
            if(live<1){int layer=canvas.saveLayerAlpha(0,0,w,model.getHeight(),Math.round(255*(1-live)));drawFryingRoll(canvas,d,cx,144*d,w-32*d,0);canvas.restoreToCount(layer);}
            if(live>0){int layer=canvas.saveLayerAlpha(0,0,w,model.getHeight(),Math.round(255*live));functionText(canvas,model.measuredTemperatureText(),cx,169*d,144*d,w-32*d,Ui.ACCENT);canvas.restoreToCount(layer);}
            String goal="Ziel "+model.value()+" °C";
            temperatureText(canvas,model.currentReading()<0?"Messwert fehlt":"Aktuell "+model.currentReading()+" °C",model.currentReading()<0?"Messwert fehlt · "+goal:goal,cx,258*d,21*d,Ui.MUTED,w-20*d);
            functionFocus(canvas,cx,Math.min(86*d,w*.35f),d);
        }
        private void drawFunctionValue(Canvas canvas,float d,float w){
            if(function.id.equals("fry")&&(kind==0||kind==1)){drawFryingValues(canvas,d,w);return;}
            if(kind==0){if(slow())drawSlowTime(canvas,d,w);else drawFunctionTime(canvas,d,w);return;}
            int count=(time.getVisibility()==VISIBLE?1:0)+(heat.getVisibility()==VISIBLE?1:0)+(speed.getVisibility()==VISIBLE?1:0);
            float size=(count==1?160:count==2?144:112)*d,cx=w/2;
            String label=warm()?"Linkslauf-Stufe":"Stufe";
            if(kind==1)temperatureText(canvas,"Zieltemperatur","Aktuelle Temperatur",cx,76*d,21*d,Ui.MUTED,w-20*d);
            else drawText(canvas,label,cx,76*d,21*d,Ui.MUTED,1);
            ControlClient client=ControlClient.get(activity);boolean program=kind==2&&(function.intervals||function.puree())&&client.program.active();
            int stage=program?Math.abs(client.program.speed()):model.value();
            String value=SPEED_LABELS[Math.min(stage,SPEED_LABELS.length-1)];
            if(kind==1)temperatureText(canvas,model.value()==0?"---":Integer.toString(model.value()),model.measuredTemperatureText(),cx,169*d,size,Ui.ACCENT,w-32*d);
            else functionText(canvas,value,cx,169*d,size,w-32*d,Ui.ACCENT);
            String footer;
            if(kind==1){String goal=model.value()==0?"Ziel: Heizung aus":"Ziel "+model.value()+" °C"+(model.minimum()==model.maximum()?" · fest":"");temperatureText(canvas,model.currentReading()<0?"Messwert fehlt":"Aktuell "+model.currentReading()+" °C",model.currentReading()<0?"Messwert fehlt · "+goal:goal,cx,258*d,21*d,Ui.MUTED,w-20*d);functionFocus(canvas,cx,Math.min(86*d,w*.35f),d);return;}
            else if(program)footer=stage==0?"Pause":(client.program.speed()<0?"Rückwärts":"Vorwärts")+(function.puree()?" · Ziel "+speed.value()/10:"");
            else footer=function.intervals?"Automatisch":stage==0?"Stillstand":(reverse.getAsBoolean()?"Rückwärts":"Vorwärts")+(model.minimum()==model.maximum()?" · fest":"");
            functionText(canvas,footer,cx,258*d,21*d,w-20*d,Ui.MUTED);
            functionFocus(canvas,cx,Math.min(86*d,w*.35f),d);
        }
        // Compact only the drawing; finger travel, zero latch and accepted values stay unchanged.
        float displayRow(float distance){float magnitude=Math.abs(distance);return Math.copySign(magnitude<=1?magnitude*70:70+(magnitude-1)*52,distance);}
        @Override public void draw(Canvas canvas){
            if(Float.isNaN(shown[0]))changed();boolean expanded=expandedWheelPreview,direct=directWheel();float d=getResources().getDisplayMetrics().density,w=model.getWidth(),cy=(direct?168:expanded?128:64)*d,window=(direct?336:expanded?256:128)*d;
            if(singleTemperatureLayout()&&kind==1){
                float cx=w/2;drawText(canvas,function.title,cx,28*d,30*d,Ui.INK,1);
                temperatureText(canvas,"Zieltemperatur","Aktuelle Temperatur",cx,76*d,21*d,Ui.MUTED,w-20*d);
                temperatureText(canvas,Integer.toString(model.value()),model.measuredTemperatureText(),cx,169*d,144*d,Ui.ACCENT,w-32*d);
                drawText(canvas,"°C",cx+160*d,183*d,28*d,Ui.MUTED,1);
                String status=water()?ControlClient.get(activity).waterHeating.status(ControlClient.get(activity).state):"";
                String goal=function.fixedDuration()?"Ziel "+model.value()+" °C · "+HomeFunctionProfile.thickenSeconds(model.value())/60+" Min. fest":model.value()==0?"Ziel: Heizung aus":"Ziel "+model.value()+" °C";
                temperatureText(canvas,model.currentReading()<0?"Messwert fehlt":"Aktuell "+model.currentReading()+" °C",model.currentReading()<0?"Messwert fehlt · "+goal:goal,cx,(status.isEmpty()?272:250)*d,21*d,Ui.MUTED,w-20*d);
                if(!status.isEmpty())functionText(canvas,status,cx,280*d,17*d,w-20*d,Ui.MUTED);
                if(knobSlot==1&&model.isSelected()&&model.isEnabled()){paint.setColor(Ui.ACCENT);canvas.drawRect(cx-86*d,100*d,cx+86*d,103*d,paint);canvas.drawRect(cx-86*d,235*d,cx+86*d,238*d,paint);}return;
            }
            if(functionReadout()){drawFunctionValue(canvas,d,w);return;}
            if(kind!=0){paint.setColor(Ui.LINE);paint.setAlpha(180);paint.setStrokeWidth(d);canvas.drawLine(d,cy-24*d,d,cy+28*d,paint);paint.setAlpha(255);}
            for(int column=0;column<(kind==0?2:1);column++){
                float left=kind==0&&column==1?w*timeSplit():0,right=kind==0&&column==0?w*timeSplit():w,cell=right-left,cx=kind==0?left+cell*(column==0?.44f:.40f):kind==1?w*.42f:w*.48f;
                // Selected time and speed remain known through telemetry gaps.
                // The measured temperature has its own bounded freshness check.
                // Predictions move only the drawing. They never reach model.value(), detents or haptics.
                float position=visualPosition(column);
                int base=(int)Math.floor(position);float fraction=position-base;
                int saved=canvas.saveLayer(left,0,right,window,null);canvas.clipRect(left,0,right,window);
                float liveOpacity=kind==1&&!dragging?model.temperatureLiveOpacity():0f;
                if(liveOpacity>0f){
                    int liveLayer=canvas.saveLayerAlpha(left,0,right,window,Math.round(255*liveOpacity));
                    // Measured heat stays central; the selected target occupies
                    // the lower neighbor with the same type, tint and focus mask.
                    drawText(canvas,model.measuredTemperatureText(),cx,cy,(expanded?94:74)*d,Ui.ACCENT,1);
                    if(goalValue!=model.value()){goalValue=model.value();goalLabel=goalValue==0?"---":Integer.toString(goalValue);}
                    float depth=(float)Math.cos(Math.PI/(expanded?6:3)),weight=depth*depth;
                    float size=(expanded?26+48*weight*weight*weight:20+54*weight)*d;
                    float y=cy+(direct?displayRow(1):(float)Math.sin(Math.PI/(expanded?6:3))*(expanded?118:60))*d;
                    int color=expanded?blend(Ui.BG,Ui.MUTED,.75f+.15f*weight):blend(Ui.MUTED,Ui.INK,weight);
                    int row=canvas.save();canvas.scale(1,expanded?.85f+.15f*depth:.45f+.55f*depth,cx,y);drawText(canvas,goalLabel,cx,y,size,color,1);canvas.restoreToCount(row);
                    canvas.restoreToCount(liveLayer);
                }
                if(liveOpacity<1f){
                int targetLayer=kind==1?canvas.saveLayerAlpha(left,0,right,window,Math.round(255*(1-liveOpacity))):canvas.save();
                for(int offset=expanded?-2:-1;offset<=(expanded?3:2);offset++){
                    if(model.minimum()==model.maximum()&&base+offset!=index(column))continue;
                    if(kind==2&&function.fixedDirection){int candidate=speedPreviewValue(base+offset);if(candidate<function.minSpeed||candidate>function.maxSpeed||candidate!=0&&(base+offset<0)!=function.reverse)continue;}
                    if(kind==0&&timeMinimum(column)==timeMaximum(column)&&base+offset!=index(column))continue;
                    float distance=offset-fraction;if(Math.abs(distance)>(expanded?2.5f:1.5f))continue;float angle=distance*(float)Math.PI/(expanded?6:3),depth=Math.max(0,(float)Math.cos(angle)),weight=depth*depth;float centralBoost=expanded?(function.puree()?38:20)*(float)Math.pow(Math.max(0,1-Math.pow(distance/.75f,2)),2):0;float size=(expanded?26+48*weight*weight*weight+centralBoost:20+54*weight)*d,y=cy+(direct?displayRow(distance):(float)Math.sin(angle)*(expanded?118:60))*d;
                    int color=expanded?blend(Ui.BG,Ui.MUTED,.75f+.15f*weight):blend(Ui.MUTED,Ui.INK,weight);float scale=column==part?1+pulse*.10f:1;
                    if(expanded&&(direct&&dragging?base+offset==Math.round(shown[column]):Math.abs(distance)<.5f))color=Ui.ACCENT;
                    else if(!expanded&&weight>.9f&&column==part)color=blend(color,0xff54bf78,pulse);
                    if(expanded&&kind==2&&speedPreviewValue(base+offset)==5){
                        float textSize=size*.82f,gap=6*d;int iconSize=(int)((22+22*weight*weight)*d);
                        paint.setTypeface(MEDIUM);paint.setTextSize(textSize);float textWidth=paint.measureText("0,5"),groupWidth=textWidth+gap+iconSize;
                        float textX=cx-groupWidth/2+textWidth/2,iconX=cx+groupWidth/2-iconSize/2;
                        int row=canvas.save();canvas.scale(1,.85f+.15f*depth,cx,y);drawText(canvas,"0,5",textX,y,textSize,color,scale);
                        android.graphics.drawable.Drawable icon=base+offset<0?wheelSpoonIcon:wheelBladeIcon;icon.setBounds((int)(iconX-iconSize/2f),(int)(y-iconSize/2f),(int)(iconX+iconSize/2f),(int)(y+iconSize/2f));icon.setAlpha(Math.abs(distance)<.5f?255:190);icon.draw(canvas);canvas.restoreToCount(row);
                    }
                    else if(!expanded&&kind==2&&model.value()==0&&!model.liveMode()&&Math.abs(position)<.001f&&base+offset!=0){
                        if(Math.abs(base+offset)>1)continue;
                        CookingToolIcon icon=distance<0?spoonIcon:bladeIcon;int iconSize=(int)(24*d);icon.setBounds((int)cx-iconSize/2,(int)y-iconSize/2,(int)cx+iconSize/2,(int)y+iconSize/2);icon.setAlpha(180);icon.draw(canvas);
                    }
                    else{int row=canvas.save();canvas.scale(1,expanded?.85f+.15f*depth:.45f+.55f*depth,cx,y);drawText(canvas,formatIndex(base+offset,column),cx,y,size,color,scale);canvas.restoreToCount(row);}
                }
                canvas.restoreToCount(targetLayer);
                }
                paint.setShader(direct?directFade:expanded?expandedFade:fade);paint.setXfermode(fadeMode);paint.setAlpha(255);canvas.drawRect(left,0,right,window,paint);paint.setShader(null);paint.setXfermode(null);canvas.restoreToCount(saved);
                int marked=kind;
                if(knobSlot==marked&&model.isSelected()&&model.isEnabled()){
                    paint.setColor(Ui.ACCENT);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
                    float halfWidth=(function.puree()?46:38)*d,inner=(function.puree()?54:44)*d;
                    canvas.drawRoundRect(new RectF(cx-halfWidth,cy-inner-4*d,cx+halfWidth,cy-inner),2*d,2*d,paint);
                    canvas.drawRoundRect(new RectF(cx-halfWidth,cy+inner,cx+halfWidth,cy+inner+4*d),2*d,2*d,paint);
                }
                if(kind==0){paint.setTypeface(MEDIUM);paint.setTextSize((expanded?(function.puree()?112:94):74)*d);float valueWidth=paint.measureText(column==0?(function.puree()?"00":slow()?"08":"120"):"00");drawText(canvas,slow()?(column==0?"Std":"Min"):(column==0?"Min":"Sek"),cx+valueWidth/2+14*d,cy+20*d,slow()?17*d:16*d,Ui.MUTED,1);}
            }
            if(kind!=0&&model.minimum()==model.maximum()&&!liveTemperature())drawText(canvas,"Fest",w*.45f,cy+70*d,16*d,Ui.MUTED,1);
            if(kind==1){
                float live=dragging?0f:model.temperatureLiveOpacity();
                if(live>0f&&model.currentReading()<0){int layer=canvas.saveLayerAlpha(0,0,w,model.getHeight(),Math.round(255*live));drawText(canvas,"Messwert fehlt",w*.45f,cy+70*d,16*d,Ui.MUTED,1);canvas.restoreToCount(layer);}
                if(live<1f){int layer=canvas.saveLayerAlpha(0,0,w,model.getHeight(),Math.round(255*(1-live)));drawText(canvas,"Ziel",w*.80f,cy-20*d,16*d,Ui.MUTED,1);canvas.restoreToCount(layer);}
                float unitX=w*.80f;drawText(canvas,"°C",unitX,cy+20*d,20*d,Ui.MUTED,1);
                if(live>0f){int layer=canvas.saveLayerAlpha(0,0,w,model.getHeight(),Math.round(255*live));drawText(canvas,"Ziel",unitX,cy+(direct?displayRow(1):(float)Math.sin(Math.PI/(expanded?6:3))*(expanded?118:60))*d,16*d,Ui.MUTED,1);canvas.restoreToCount(layer);}
            }
        }
        void drawText(Canvas canvas,String text,float x,float y,float size,int color,float scale){paint.setStyle(Paint.Style.FILL);paint.setTypeface(size>30*getResources().getDisplayMetrics().density?MEDIUM:REGULAR);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);paint.setColor(color);paint.setAlpha(model.isEnabled()?255:125);int save=canvas.save();canvas.scale(scale,scale,x,y);canvas.drawText(text,x,y-(paint.ascent()+paint.descent())/2,paint);canvas.restoreToCount(save);paint.setAlpha(255);}
        void pulse(){if(pop!=null)pop.cancel();if(!motion())return;pop=ValueAnimator.ofFloat(0,1,0);pop.setDuration(380);pop.setInterpolator(new android.view.animation.DecelerateInterpolator());pop.addUpdateListener(a->{pulse=(float)a.getAnimatedValue();model.invalidate();});pop.start();}
        float wheelTravel(MotionEvent event,float density){
            float rawOffset=event.getRawY()-event.getY();
            for(int i=0;i<event.getHistorySize();i++){
                float historical=wheelDrag.move((downY-event.getHistoricalY(i)-rawOffset)/density,event.getHistoricalEventTime(i));
                if(motionBridgePreview)motionBridge.sample(historical*wheelDrag.distancePerStep(),event.getHistoricalEventTime(i));
            }
            float steps=wheelDrag.move((downY-event.getRawY())/density,event.getEventTime());
            if(motionBridgePreview)motionBridge.sample(steps*wheelDrag.distancePerStep(),event.getEventTime());
            return steps;
        }
        boolean tapNeighbor(float y){
            if(functionReadout()||!expandedWheelPreview||kind==2)return false;
            int offset=0;float closest=26;
            for(int n=-2;n<=2;n++){float row=directWheel()?168+displayRow(n):128+(float)Math.sin(n*Math.PI/6)*118;float distance=Math.abs(y-row);if(distance<closest){closest=distance;offset=n;}}
            if(offset==0)return false;
            int next=gestureChoices.get(Math.floorMod(startIndex+offset,gestureChoices.size())),before=model.value();
            if(kind==0){model.value(part==0?Math.min(model.maximum(),timeCompose(next,timeMinor(startValue))):timeCompose(timeMajor(startValue),next));increment=HomeControlMath.reachedIncrement(timeDelta(model.value()-startValue));}
            else model.value(next);
            if(model.value()==before)return false;tick();updateMarks();return true;
        }
        @Override public boolean touch(MotionEvent event){
            if(!model.isEnabled())return false;float d=getResources().getDisplayMetrics().density;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    stopMotionBridge();motionBridge.reset(0,event.getEventTime());
                    part=kind==0&&(turbo()||event.getX()>model.getWidth()*timeSplit())?1:0;holding=true;activate(this,part);model.beginNumberEdit();startValue=model.value();startReverse=reverse.getAsBoolean();downY=event.getRawY();wheelDrag.reset(event.getEventTime());moved=false;dragging=true;increment=-1;gestureChoices=choices();int current=kind==0?(part==0?timeMajor(model.value()):timeMinor(model.value())):model.value();startIndex=Math.max(0,gestureChoices.indexOf(current));if(rolls[part]!=null){rolls[part].cancel();rolls[part]=null;}startPosition=shown[part]=index(part);
                    detentDrag.reset(Math.round(startPosition),kind==2?speedMinPosition():-1000000,kind==2?speedMaxPosition():1000000);
                    directDrag.reset(startPosition,kind==2?speedMinPosition():-1000000,kind==2?speedMaxPosition():1000000,kind==2?0:gestureChoices.get(0)==0?gestureChoices.size():-1);
                    int[] location=new int[2];getRootView().getLocationOnScreen(location);upRoom=(downY-location[1])/d-24;downRoom=(getRootView().getHeight()+location[1]-downY)/d-24;model.getParent().requestDisallowInterceptTouchEvent(true);return true;
                case MotionEvent.ACTION_MOVE:
                    float displacement=(downY-event.getRawY())/d,steps=kind==2&&!expandedWheelPreview?0:wheelTravel(event,d);if(!moved&&Math.abs(displacement)<ViewConfiguration.get(activity).getScaledTouchSlop()/d)return true;moved=true;scheduleMotionBridge();
                    if(directWheel()){moveDirect(steps,displacement,event.getEventTime());return true;}
                    if(expandedWheelPreview&&thresholdSnapPreview){
                        moveDetent(steps);return true;
                    }
                    if(kind==2&&expandedWheelPreview){
                        float position=Math.max(speedMinPosition(),Math.min(speedMaxPosition(),startPosition+steps));int selected=Math.round(position),previous=model.value();boolean oldReverse=reverse.getAsBoolean(),wantReverse=selected==0?oldReverse:selected<0;
                        if(!chooseDirection.test(wantReverse))return true;directionChanged();model.value(speedPreviewValue(selected));showDragPosition(position);
                        if(model.value()!=previous||model.value()>0&&oldReverse!=wantReverse)tick();changed();updateMarks();return true;
                    }
                    int before=model.value();boolean beforeReverse=reverse.getAsBoolean();if(kind==2){boolean wantReverse=displacement<0;if(!chooseDirection.test(wantReverse))return true;directionChanged();gestureChoices=choices();}
                    float room=displacement>=0?upRoom:downRoom,travel=kind==2?Math.copySign(HomeControlMath.speedPosition(gestureChoices.size()-1,Math.abs(displacement),room),displacement):steps;int index=kind==2?(int)Math.abs(travel):HomeControlMath.wheelIndex(startIndex,gestureChoices.size()-1,travel);int next=gestureChoices.get(index);
                    if(kind==0){model.value(part==0?Math.min(model.maximum(),timeCompose(next,timeMinor(startValue))):timeCompose(timeMajor(startValue),next));increment=HomeControlMath.reachedIncrement(timeDelta(model.value()-startValue));}
                    else model.value(next);showDragPosition((kind==2?0:startPosition)+travel);if(model.value()!=before||kind==2&&model.value()>0&&beforeReverse!=reverse.getAsBoolean())tick();changed();updateMarks();return true;
                case MotionEvent.ACTION_UP:
                    float finalVisual=directWheel()?visualPosition(part):shown[part]+(rolls[part]==null?motionOffset:0);stopMotionBridge();
                    if(directWheel()&&moved){moveDirect(wheelTravel(event,d),(downY-event.getRawY())/d,event.getEventTime());stopMotionBridge();shown[part]=finalVisual;}
                    else if(expandedWheelPreview&&thresholdSnapPreview&&moved)moveDetent(wheelTravel(event,d));
                    if(!moved)moved=tapNeighbor(event.getY()/d);
                    dragging=false;holding=false;model.getParent().requestDisallowInterceptTouchEvent(false);settle();if(moved){model.commitNumberEdit();finishEdit.accept(model);if(water()&&kind==1&&model.value()!=startValue)requestFunctionStart();if(kind==2){modeUntil=android.os.SystemClock.elapsedRealtime()+4000;if(model.value()>0)speedSelected.run();}}else{model.performClick();activate(this,part);}scheduleHide();return true;
                case MotionEvent.ACTION_CANCEL:
                    if(directWheel())shown[part]=visualPosition(part);
                    stopMotionBridge();
                    if(kind==2)chooseDirection.test(startReverse);model.value(startValue);dragging=false;holding=false;settle();finishEdit.accept(model);directionChanged();model.getParent().requestDisallowInterceptTouchEvent(false);scheduleHide();return true;
                default:return true;
            }
        }
        @Override public void dispose(){stopMotionBridge();for(ValueAnimator roll:rolls)if(roll!=null)roll.cancel();if(pop!=null)pop.cancel();}
        @Override public void confirmed(){pulse();}
    }
    private static int blend(int a,int b,float t){t=Math.max(0,Math.min(1,t));return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
}
