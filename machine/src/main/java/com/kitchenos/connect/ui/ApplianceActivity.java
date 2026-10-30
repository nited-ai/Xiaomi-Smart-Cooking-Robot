package com.kitchenos.connect.ui;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.*;
import com.kitchenos.connect.kitchen.*;
import com.kitchenos.connect.control.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Appliance shell. Knob, touch and typed assistant actions share the same navigation. */
public final class ApplianceActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private RotaryController knob;
    private ApplianceTools tools;
    private final HashMap<String,int[]> modeEdits=new HashMap<>();
    private LinearLayout surface,body,toolbar,footer,actions;
    private ScrollView scroll;
    private GuidedControlPanel cookingPanel;
    private RecipeNutrition nutritionCache;
    private InlineScalePanel inlineScale;
    private RecipeSearchPanel recipeSearchPanel;
    private RecipeCategories recipeCategories;
    private RecipeCatalogueCache recipeOffline;
    private List<JSONObject> cachedRecipeRows;
    private String cachedRecipeQuery="",cachedRecipeOwner="";private boolean cachedRecipeBrowse;
    private JSONArray cachedPrivate,cachedReady,cachedOnline;
    private static final class CachedShelf {final String signature;final RecipeShelf view;CachedShelf(String signature,RecipeShelf view){this.signature=signature;this.view=view;}}
    private final LinkedHashMap<String,CachedShelf> recipeShelfCache=new LinkedHashMap<>(16,.75f,true);
    private RootPageDrag rootDrag;private boolean preparingRootDrag,renderAfterRootDrag;
    private boolean rootGesture,rootVertical,functionGesture,gestureAxisLocked;
    private int rootEdgeDirection;
    private long rootGestureStartedAt;
    private float rootMinX,rootMaxX;
    private HomePageState cachedHome;
    /** One bounded, paused Home tree; no catalogue pages or active cooking sessions. */
    private final class HomePageState {
        final LinearLayout savedSurface=surface,savedBody=body,savedToolbar=toolbar,savedFooter=footer,savedActions=actions;
        final ScrollView savedScroll=scroll;
        final GuidedControlPanel panel=cookingPanel;
        final HomeNumberControls numbers=homeNumbers;
        final ParameterDial time=duration,heat=temperature,speed=stirring;
        final RotaryController rotary=knob;
        final ApplianceTools savedTools=tools;
        final HomeKnobController homeInput=homeKnob;
        final FrameLayout drawer=drawerScrim;
        final AccessoryPicker picker=attachmentPicker;
        final String owner=accountUid,signature=homeCacheSignature();
        void dispose(){numbers.retainSceneForNavigation(false);panel.dispose();rotary.reset();}
        void restore(){
            surface=savedSurface;body=savedBody;toolbar=savedToolbar;footer=savedFooter;actions=savedActions;scroll=savedScroll;
            cookingPanel=panel;homeNumbers=numbers;duration=time;temperature=heat;stirring=speed;knob=rotary;tools=savedTools;homeKnob=homeInput;
            drawerScrim=drawer;attachmentPicker=picker;drawerOpen=false;assistantLauncher=null;recipeSearchPanel=null;inlineScale=null;
            renderedPage="home";surface.setTranslationX(0);surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if(surface.getParent()!=null)((ViewGroup)surface.getParent()).removeView(surface);
            setContentView(surface);numbers.retainSceneForNavigation(false);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if(screenResumed){panel.resume();numbers.resumeScale();}configureHomeKnob(numbers.cupPromptShowing());
            android.util.Log.i("KitchenOSNavigation","Home reused");
        }
    }
    private String homeCacheSignature(){FunctionOrder order=new FunctionOrder(this);return getSharedPreferences("appearance",0).getString("theme","")+order.items()+order.visibleItems();}
    private void clearHomeCache(){HomePageState old=cachedHome;cachedHome=null;if(old!=null)old.dispose();}
    private boolean parkHome(String destination){
        if(!ROOT_PAGES.contains(destination)||destination.equals("home"))return false;
        if(!page.equals("home")||!homeFunction.equals("manual")||homeNumbers==null||cookingPanel==null||!navigationIdle())return false;
        clearHomeCache();rememberPanel();homeNumbers.retainSceneForNavigation(true);homeNumbers.pauseScale();cookingPanel.pause();knob.cancelPress();
        cachedHome=new HomePageState();cookingPanel=null;homeNumbers=null;return true;
    }
    private boolean restoreHome(){
        if(cachedHome==null||!page.equals("home"))return false;
        if(!homeFunction.equals("manual")||!cachedHome.owner.equals(accountUid)||!cachedHome.signature.equals(homeCacheSignature())){clearHomeCache();return false;}
        HomePageState saved=cachedHome;cachedHome=null;knob.reset();saved.restore();return true;
    }
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)clearHomeCache();}
    private FunctionDrawer functionDrawer;private String functionsOrigin="home";
    private String activeRecipeCategory="";private final Set<String> categoryPreviewsLoaded=new HashSet<>();private boolean categoryPreviewsLoading;
    private final Map<String,Integer> shelfPositions=new HashMap<>(),pageScrollPositions=new HashMap<>();
    private String paintedRecipeQuery="";
    private static final List<String> ROOT_PAGES=Arrays.asList("favorites","home","recipes","plan","pantry");
    private final ExecutorService recipeSearchWorker=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicLong recipeSearchGeneration=new java.util.concurrent.atomic.AtomicLong();
    private RecipeSearchIndex recipeSearchIndex; // Accessed only by recipeSearchWorker.
    private JSONArray indexedPrivate,indexedReady,indexedOnline;private int indexedServings;
    private List<JSONObject> liveRecipeRows;private List<Recipe> searchIncoming=Collections.emptyList();
    private boolean paintingRecipeResults;private long lastRecipeInputAt,lastRecipeMotionAt;private boolean recipeTouchHeld;private String recipeResultsOwner="";
    private final LinkedHashMap<String,View> searchCards=new LinkedHashMap<>();
    private final Runnable liveRecipeRender=this::searchRecipeResults;
    private final Runnable liveRecipeLookup=()->{if(!this.destroyed&&this.page.equals("recipes")&&this.browseCatalogue)loadSourceRecipes(this.query,false);};
    private boolean inlineMeasurementDelivery;
    private FrameLayout drawerScrim;
    private FloatingAssistant assistantLauncher;
    private Dialog assistantDialog,setupDialog,parameterEditor;private boolean renderAfterParameterEditor;private String parameterEditorOwner="";
    private boolean renderAfterAssistant,homeHasPantry;
    private Bitmap pantryGalleryPhoto;private Runnable ingredientWarmup;
    private boolean assistantGesture;
    private boolean drawerOpen;
    private float gestureX,gestureY;
    private boolean edgeGesture,stepGestureBlocked;
    private final Runnable scaleHeartbeat=new Runnable(){public void run(){if(inlineScale==null||!screenResumed)return;ControlClient.get(ApplianceActivity.this).tick();main.postDelayed(this,200);}};
    private void activateScale(){if(inlineScale==null||!screenResumed)return;ControlClient client=ControlClient.get(this);client.foreground(true);client.listen(()->{if(inlineScale!=null)inlineScale.refresh();});inlineScale.resume();main.removeCallbacks(scaleHeartbeat);main.post(scaleHeartbeat);}
    private String cookSessionId="";
    private Bundle activeCookState,stepControlStates=new Bundle();
    private JSONArray weighingRecords=new JSONArray();
    private String renderedPage="",detailTab="overview";
    private final HashSet<String> ingredientChecks=new HashSet<>(),weighingStarted=new HashSet<>();
    private boolean screenResumed,planLoading;
    private final HashMap<String,Boolean> favoriteOverrides=new HashMap<>();private final HashMap<String,Long> favoriteVersions=new HashMap<>();private long favoriteVersion;
    private final ExecutorService favoriteWrites=Executors.newSingleThreadExecutor();
    private volatile long preparationEpoch;private int requestedServings;private boolean browseCatalogue,sourceLoading;private volatile long sourceEpoch;private String sourceCursor="",sourceQuery="",sourceError="";
    private JSONArray kalomatikPlan=new JSONArray();private String planError="",selectedPlanDate=java.time.LocalDate.now().toString();private long planRefreshed,planEpoch;private final ExecutorService planWorker=Executors.newSingleThreadExecutor();
    private String homeCollectionFilter="";
    private String page="home",lastPage="home",functionOrigin="home",connectionOrigin="settings",query="";
    private int recipePage;
    private String featuredRecipeKey="";
    private JSONArray catalogue=new JSONArray(),readyCatalogue=new JSONArray(),privateCatalogue=new JSONArray();
    private WifiPanel wifiPanel;private String connectionState="";private boolean receiverRegistered;
    private final BroadcastReceiver recipeChanges=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){readPrivateCache();loadPrivateCatalogue();prepareImports();if(Arrays.asList("recipes","favorites").contains(page))render();}};
    private final Runnable connectionUpdate=new Runnable(){public void run(){if(destroyed)return;if(page.equals("receive")){String next=DeviceConnectionPage.state(ApplianceActivity.this);if(!next.equals(connectionState))render();main.postDelayed(this,1000);}else if(page.equals("wifi")&&wifiPanel!=null){wifiPanel.refresh();main.postDelayed(this,3000);}}};
    private final ThreadPoolExecutor preparation=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final ThreadPoolExecutor reviewWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final ArrayList<CancellationSignal> reviewCancellations=new ArrayList<>();
    private RecipeReviewTransport reviewTransport=(session,input,cancellation)->session.api("/v1/recipes/review",input,cancellation);
    private Future<?> preparationWork;private CancellationSignal preparationCancellation;private Runnable preparationTimeout;
    private RecipePreparationTransport preparationTransport=(session,input,cancellation)->{cancellation.throwIfCanceled();CloudConnection.requireOnline(this,new java.net.URI(com.kitchenos.connect.BuildConfig.KITCHEN_API_BASE).getHost());cancellation.throwIfCanceled();return session.api("/v1/recipes/prepare",input,cancellation);};
    private boolean machineLoading,warmStarted,preparingRecipe,catalogueRefreshFailed;private long catalogueRefreshed;
    private String warmRetry="";
    private String recipeError="",preparationError="",accountUid="";
    private final HashSet<String> completedSteps=new HashSet<>();
    private boolean cookingModified;
    private long restDeadline;
    private String restTimerKey="";
    private final HashMap<String,Long> restTimers=new HashMap<>();
    private boolean coldInterrupted,navigationApproved,navigationPromptShown;
    private final Runnable restTick=this::tickRest;
    private StepPageSlider stepSlider;private boolean slideForward;
    private TextView restLabel;
    private String homeFunction="manual";private boolean applyingHome;private int eggGrade=1;private int homeCleaning=2;private int homeFrying=0;
    private int[] homeSettings={5,0,0};private boolean homeReverse=true;private String homeFilter="Für dich";
    private JSONObject current;
    private int currentStep;
    private boolean loading;private volatile boolean destroyed;
    private android.net.ConnectivityManager.NetworkCallback coverNetwork;private String lastValidatedNetwork="";
    private TextView loadingLabel;
    private final HashMap<String,int[]> stepEdits=new HashMap<>();
    private final HashSet<String> checks=new HashSet<>();
    private ParameterDial duration,temperature,stirring;
    private HomeNumberControls homeNumbers;private boolean homeMotionStill;private Boolean homeIntervals;
    private HomeFunctionProfile homeProfile(){HomeFunctionProfile p=(homeFunction.equals("clean")?HomeFunctionProfile.cleaning(com.kitchenos.connect.control.CleaningProgram.at(homeCleaning)):HomeFunctionProfile.get(homeFunction)).lidlessMotion(homeFunction.equals("slow")?false:homeMotionStill);if(homeIntervals!=null&&!homeMotionStill&&!homeFunction.equals("slow"))p.intervals(homeIntervals);p.frying=com.kitchenos.connect.control.FryingRhythm.at(homeFrying);return p;}
    private boolean reverse=true;private AccessoryPicker attachmentPicker;
    private static final String[][] MODES={
        {"manual","Manuell kochen","Deine Zeit. Deine Einstellungen."},
        {"lidless","Ohne Deckel kochen","Bis 100 °C und Stufe 3"},
        {"scale","Waage","Zutaten direkt im Topf abwiegen"},
        {"eggs","Eier kochen","Zum Frühstück und zwischendurch"},
        {"water","Wasser erhitzen","Heißes Wasser auf Knopfdruck"},
        {"rice","Reis kochen","Für deine Beilage"},
        {"dough","Teig kneten","Brot, Pizza und mehr"},
        {"steam","Dampfgaren","Gemüse und Beilagen"},
        {"chop","Zerkleinern","Grob oder fein"},
        {"mix","Mixen","Smoothies und cremige Ergebnisse"},
        {"puree","Pürieren","Suppen, Saucen und Pürees"},
        {"stir","Rühren","Schonend vermischen"},
        {"whip","Schlagen","Sahne und luftige Cremes"},
        {"emulsify","Emulgieren","Dips und Dressings"},
        {"thicken","Andicken","Saucen, Cremes und Pudding binden"},
        {"turbo","Turbo","1–2 Sekunden auf Stufe 10, 11 oder 12"},
        {"caramel","Zucker karamellisieren","Bernsteinfarbenes Karamell"},
        {"grind","Mahlen","Nüsse und Gewürze"},
        {"ice","Eis zerkleinern","Für kalte Getränke"},
        {"warm","Erwärmen","Schonend auf Temperatur bringen"},
        {"fry","Anbraten","Zwiebeln und Gemüse anschwitzen"},
        {"slow","Langsam garen","Schonend schmoren"},
        {"sousvide","Sous-vide","Garen bei konstanter Temperatur"},
        {"ferment","Fermentieren","Zeit für Joghurt und mehr"},
        {"clean","Vorspülen","Fünf Reinigungsprogramme für unterschiedliche Reste"}
    };
    @Override public void onCreate(Bundle saved){long startupAt=SystemClock.uptimeMillis();super.onCreate(saved);if(saved!=null){query=saved.getString("recipeQuery","");activeRecipeCategory=saved.getString("activeRecipeCategory","");Object shelves=saved.getSerializable("shelfPixelPositions"),positions=saved.getSerializable("pageScrollPositions");if(shelves instanceof HashMap)shelfPositions.putAll((HashMap<String,Integer>)shelves);if(positions instanceof HashMap)pageScrollPositions.putAll((HashMap<String,Integer>)positions);}recipeCategories=new RecipeCategories(this);recipeOffline=new RecipeCatalogueCache(this);FunctionPhotography.prewarm(this);if(saved!=null){int[] h=saved.getIntArray("homeSettings");if(h!=null&&h.length==3)homeSettings=h;homeReverse=saved.getBoolean("homeReverse",true);homeFunction=saved.getString("homeFunction","manual");homeMotionStill=saved.getBoolean("homeMotionStill",false);homeIntervals=saved.containsKey("homeIntervals")?saved.getBoolean("homeIntervals"):null;eggGrade=saved.getInt("eggGrade",1);homeCleaning=saved.getInt("homeCleaning",2);homeFrying=saved.getInt("homeFrying",0);homeFilter=saved.getString("homeFilter","Für dich");}if(saved!=null)featuredRecipeKey=saved.getString("featuredRecipeKey","");knob=new RotaryController(this);knob.onHomeLongPress(this::requestStandby);tools=new ApplianceTools(()->new CloudSession(this).account().optString("uid"));if(saved!=null){requestedServings=saved.getInt("requestedServings",0);cookingModified=saved.getBoolean("cookingModified",true);page=saved.getString("page","home");selectedMode=saved.getString("mode","manual");try{String raw=saved.getString("recipe","");if(!raw.isEmpty())current=new JSONObject(raw);}catch(Exception ignored){}currentStep=saved.getInt("step",0);ArrayList<String> completed=saved.getStringArrayList("completedSteps");if(completed!=null)completedSteps.addAll(completed);restDeadline=saved.getLong("restDeadline",0);lastPage=saved.getString("lastPage","recipes");functionOrigin=saved.getString("functionOrigin","home");connectionOrigin=saved.getString("connectionOrigin","settings");selectedPlanDate=saved.getString("selectedPlanDate",java.time.LocalDate.now().toString());int[] modeValues=saved.getIntArray("modeValues");if(modeValues!=null)modeEdits.put(selectedMode,modeValues);reverse=saved.getBoolean("reverse",true);}accountUid=new CloudSession(this).account().optString("uid");if(saved!=null&&!accountUid.equals(saved.getString("ownerUid",""))){if(current!=null&&current.optBoolean("private")){current=null;page="home";}completedSteps.clear();cookingModified=false;restDeadline=0;}restoreCookTracking(saved);readCache();readPrivateCache();readPlanCache();StartupTrace.mark("local-data",startupAt);String requestedPage=getIntent().getStringExtra("page");if(saved==null&&Arrays.asList("home","recipes","modes","pantry","receive","wifi","plan").contains(requestedPage))page=requestedPage;render();StartupTrace.mark("views-built",startupAt);StartupTrace.afterFirstDraw(this,startupAt,()->{if(destroyed)return;firstRun();if(!accountUid.isEmpty())main.postDelayed(this::loadPlan,700);watchCoverNetwork();loadRecipeCategories();loadCatalogue();loadMachineCatalogue();loadPrivateCatalogue();});String provider=getIntent().getStringExtra("loginProvider");prepareImports();if(saved==null)openRequestedRecipe(getIntent());if(saved==null&&Arrays.asList("google","microsoft","facebook","apple","email").contains(provider))main.post(()->DeviceLoginDialog.show(this,provider,()->go("home")));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readPrivateCache();String requested=intent.getStringExtra("page");if(Arrays.asList("home","recipes","modes","pantry","receive","wifi","plan").contains(requested))go(requested);openRequestedRecipe(intent);}
    private void openRequestedRecipe(Intent intent){String id=intent.getStringExtra("recipeId");if(id==null||!id.matches("[a-f0-9-]{36}"))return;for(Recipe recipe:KitchenRecipes.all(this))if(id.equals(recipe.id)){openImported(recipe);break;}}
    @Override protected void onSaveInstanceState(Bundle saved){if(rootDrag!=null)rootDrag.abort();saved.putString("recipeQuery",query);saved.putString("activeRecipeCategory",activeRecipeCategory);saved.putSerializable("shelfPixelPositions",new HashMap<>(shelfPositions));saved.putSerializable("pageScrollPositions",new HashMap<>(pageScrollPositions));rememberPanel();checkpointCook();saved.putIntArray("homeSettings",homeSettings);saved.putString("homeFunction",homeFunction);saved.putBoolean("homeMotionStill",homeMotionStill);if(homeIntervals!=null)saved.putBoolean("homeIntervals",homeIntervals);saved.putInt("eggGrade",eggGrade);saved.putInt("homeCleaning",homeCleaning);saved.putInt("homeFrying",homeFrying);saved.putBoolean("homeReverse",homeReverse);saved.putString("homeFilter",homeFilter);saved.putString("featuredRecipeKey",featuredRecipeKey);saved.putString("cookSessionId",cookSessionId);saved.putBundle("stepControlStates",new Bundle(stepControlStates));if(activeCookState!=null)saved.putBundle("activeCookState",new Bundle(activeCookState));saved.putBundle("workingCookState",workingCookState());saved.putInt("requestedServings",requestedServings);saved.putBoolean("cookingModified",cookingModified);saved.putString("ownerUid",accountUid);saved.putString("page",page);saved.putString("mode",selectedMode);saved.putString("lastPage",lastPage);saved.putString("functionOrigin",functionOrigin);saved.putString("connectionOrigin",connectionOrigin);saved.putString("selectedPlanDate",selectedPlanDate);if(current!=null)saved.putString("recipe",current.toString());saved.putInt("step",currentStep);saved.putStringArrayList("completedSteps",new ArrayList<>(completedSteps));saved.putLong("restDeadline",restDeadline);saved.putBoolean("reverse",reverse);if(page.equals("function")&&duration!=null)saved.putIntArray("modeValues",new int[]{duration.value(),temperature.value(),stirring.value()});super.onSaveInstanceState(saved);}
    @Override protected void onResume(){super.onResume();screenResumed=true;if(stirring!=null)refreshAttachment();if(!restTimers.isEmpty()){main.removeCallbacks(timerAlarms);main.post(timerAlarms);}if(cookingPanel!=null)cookingPanel.resume();if(homeNumbers!=null)homeNumbers.resumeScale();activateScale();IntentFilter receiverFilter=new IntentFilter("com.kitchenos.connect.RECIPES_CHANGED");if(Build.VERSION.SDK_INT>=33)registerReceiver(recipeChanges,receiverFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(recipeChanges,receiverFilter,"com.kitchenos.connect.INTERNAL",null);receiverRegistered=true;readPrivateCache();String uid=new CloudSession(this).account().optString("uid");if(!uid.equals(accountUid)){cancelPreparation();cancelReviews();clearCookTracking();completedSteps.clear();cookingModified=false;stepEdits.clear();directionEdits.clear();restDeadline=0;if(current!=null&&current.optBoolean("private")){current=null;page="home";completedSteps.clear();cookingModified=false;}accountUid=uid;planEpoch++;planLoading=false;planRefreshed=0;kalomatikPlan=new JSONArray();readPlanCache();if(!uid.isEmpty())main.post(this::loadPlan);warmStarted=false;readPrivateCache();loadMachineCatalogue();loadPrivateCatalogue();warmMachineRecipes();prepareImports();render();}}
    @Override protected void onPostResume(){super.onPostResume();if(getSharedPreferences("receiver",0).getBoolean("enabled",false)&&RecipeReceiverService.active==null)RecipeReceiverService.requestStart(this);}
    @Override protected void onPause(){if(knob!=null)knob.cancelPress();screenResumed=false;recipeTouchHeld=false;if(rootDrag!=null)rootDrag.abort();if(functionDrawer!=null)functionDrawer.dispose();if(parameterEditor!=null)parameterEditor.dismiss();if(assistantDialog!=null)assistantDialog.dismiss();screenResumed=false;if(homeNumbers!=null)homeNumbers.pauseScale();main.removeCallbacks(scaleHeartbeat);if(inlineScale!=null){inlineScale.pause();ControlClient.get(this).listen(null);ControlClient.get(this).foreground(false);}if(cookingPanel!=null){cookingPanel.pause();rememberPanel();}checkpointCook();if(receiverRegistered){unregisterReceiver(recipeChanges);receiverRegistered=false;}main.removeCallbacks(connectionUpdate);super.onPause();}
    @Override protected void onDestroy(){clearHomeCache();if(nutritionCache!=null)nutritionCache.close();if(coverNetwork!=null)try{((android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(coverNetwork);}catch(Exception ignored){}if(assistantDialog!=null)assistantDialog.dismiss();releaseCookingPanel();checkpointCook();destroyed=true;cancelPreparation();cancelReviews();main.removeCallbacksAndMessages(null);recipeSearchGeneration.incrementAndGet();recipeSearchWorker.shutdownNow();planWorker.shutdown();worker.shutdown();preparation.shutdownNow();reviewWorker.shutdownNow();favoriteWrites.shutdown();super.onDestroy();}
    private boolean requestStandby(){
        if(rootDrag!=null||functionDrawer!=null)return false;
        if(!page.equals("home")||drawerOpen||!screenResumed||
                parameterEditor!=null&&parameterEditor.isShowing()||
                assistantDialog!=null&&assistantDialog.isShowing()||
                setupDialog!=null&&setupDialog.isShowing())return false;
        boolean timerRunning=false;long now=SystemClock.elapsedRealtime();
        for(long deadline:restTimers.values())if(deadline>now){timerRunning=true;break;}
        String refusal=StandbyPolicy.refusal(ControlClient.get(this).state,timerRunning);
        if(!refusal.isEmpty()){Toast.makeText(this,refusal,Toast.LENGTH_LONG).show();return true;}
        checkpointCook();StandbyController.get(this).start();return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(StandbyController.get(this).consume(event))return true;if(rootDrag!=null)return true;if(homeNumbers!=null&&page.equals("home"))homeNumbers.windowKnob(event);if(stepSlider!=null){stepSlider.cancel();stepSlider=null;}if(page.equals("recipes")&&recipeSearchPanel!=null&&recipeSearchPanel.dispatchKeyboard(event))return true;return knob!=null&&knob.dispatch(event)||super.dispatchKeyEvent(event);}
    @Override public boolean onGenericMotionEvent(MotionEvent event){if(StandbyController.get(this).blocksInput())return true;if(homeNumbers!=null&&page.equals("home")&&(event.getSource()&android.view.InputDevice.SOURCE_ROTARY_ENCODER)!=0)homeNumbers.windowKnobRotation();return knob!=null&&knob.motion(event)||super.onGenericMotionEvent(event);}
    @Override public void onBackPressed(){if(rootDrag!=null){rootDrag.abort();return;}if(functionDrawer!=null){functionDrawer.close();return;}if(page.equals("recipes")&&recipeSearchPanel!=null&&recipeSearchPanel.editing()){recipeSearchPanel.closeEditor();return;}if(drawerOpen){setDrawer(false);return;}if(page.equals("home")){if(!confirmNavigation(this::onBackPressed))super.onBackPressed();}else if(page.equals("step"))previousStep();else go(page.equals("detail")?lastPage:page.equals("function")?functionOrigin:Arrays.asList("wifi","receive").contains(page)?connectionOrigin:"home");}
    private int dp(int n){return Ui.dp(this,n);}
    private LinearLayout col(){return Ui.column(this);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=KitViews.text(this,value,size,color,bold);t.setLineSpacing(dp(3),1);return t;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private android.graphics.drawable.Drawable shape(int color,int radius,boolean focus){StateListDrawable states=new StateListDrawable();GradientDrawable selected=KitViews.shape(this,color,radius,Ui.ACCENT),plain=KitViews.shape(this,color,radius,0);states.addState(new int[]{-android.R.attr.state_enabled},KitViews.shape(this,Ui.CARD,radius,0));states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{android.R.attr.state_focused},selected);states.addState(new int[]{},plain);return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x20ee6542),states,null);}
    private void target(View v,String id,String label,Runnable action){v.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.98f).scaleY(.98f).setDuration(90).start();else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(160).start();return false;});v.setContentDescription(label);v.setTag(id);v.setBackground(shape(Ui.CARD,18,true));v.setOnClickListener(x->{if(!accountUid.equals(new CloudSession(this).account().optString("uid"))){go("home");return;}knob.select(v);action.run();});knob.add(v);tools.action(id,v,action);}
    private Button button(String title,String id,Runnable action,boolean primary){Button b=Ui.button(this,title,v->action.run());b.setTextSize(24);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinimumHeight(dp(80));target(b,id,title,action);b.setDefaultFocusHighlightEnabled(false);b.setBackground(ControlFocus.background(this,primary?Ui.ACCENT:Ui.CARD));if(primary)b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Ui.MUTED,0xff171716}));return b;}
    /** Navigation stops an owned foreground run only after the user's explicit choice. */
    private boolean confirmNavigation(Runnable destination){
        if(navigationApproved||cookingPanel==null)return false;
        ControlState state=ControlClient.get(this).state;
        if(!CookingNavigation.requiresInterruption(state.ownRun||ControlClient.get(this).program.active(),state.pendingAction,state.uncertainRun))return false;
        if(navigationPromptShown)return true;navigationPromptShown=true;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Kochen unterbrechen?").setMessage("Beim Wechsel wird dieser Maschinenschritt gestoppt. Dein Kochstand bleibt erhalten; hier kannst du ihn später fortsetzen.").setNegativeButton("Bleiben",null).setPositiveButton("Unterbrechen und wechseln",(d,w)->{
            navigationApproved=true;try{ControlClient.get(this).stop();destination.run();}finally{navigationApproved=false;}
        }).create();dialog.setOnDismissListener(d->navigationPromptShown=false);dialog.show();RotaryDialogs.bindInterruption(this,dialog);return true;
    }
    private void go(String destination){if(confirmNavigation(()->go(destination)))return;if(functionDrawer!=null)functionDrawer.dispose();if(scroll!=null)pageScrollPositions.put(page,scroll.getScrollY());if(!destination.equals("recipes")&&recipeSearchPanel!=null)recipeSearchPanel.closeEditor();closeAssistantForNavigation();if(!destination.equals("step"))stopRestDisplay();if(!ROOT_PAGES.contains(destination))clearHomeCache();if(!parkHome(destination))releaseCookingPanel();checkpointCook();if(destination.equals("plan"))readPlanCache();String uid=new CloudSession(this).account().optString("uid");boolean changed=!uid.equals(accountUid);if(changed&&current!=null&&current.optBoolean("private")){current=null;page="home";completedSteps.clear();cookingModified=false;}accountUid=uid;if(changed){cancelPreparation();cancelReviews();planEpoch++;planLoading=false;planRefreshed=0;kalomatikPlan=new JSONArray();readPlanCache();if(!uid.isEmpty())main.post(this::loadPlan);clearCookTracking();completedSteps.clear();cookingModified=false;stepEdits.clear();directionEdits.clear();restDeadline=0;warmStarted=false;readPrivateCache();loadMachineCatalogue();loadPrivateCatalogue();}if(!destination.equals("detail"))cancelPreparation();if(Arrays.asList("wifi","receive").contains(destination)&&!Arrays.asList("wifi","receive").contains(page))connectionOrigin=page;page=destination;render();if(destination.equals("home"))main.post(this::firstRun);if(destination.equals("plan")&&!accountUid.isEmpty()&&SystemClock.elapsedRealtime()-planRefreshed>60000)main.post(this::loadPlan);if(Arrays.asList("home","recipes","favorites").contains(page)&&SystemClock.elapsedRealtime()-catalogueRefreshed>(catalogueRefreshFailed?60000L:6*60*60*1000L)){loadMachineCatalogue();loadPrivateCatalogue();}if(changed){warmMachineRecipes();prepareImports();}}
    private void render(){
        if(rootDrag!=null&&!preparingRootDrag||functionDrawer!=null){renderAfterRootDrag=true;return;}
        String liveOwner=new CloudSession(this).account().optString("uid");
        if(assistantDialog!=null&&assistantDialog.isShowing()){
            if(accountUid.equals(liveOwner)){renderAfterAssistant=true;return;}
            closeAssistantForNavigation();
        }
        renderAfterAssistant=false;
        if(page.equals("recipes")&&renderedPage.equals("recipes")&&accountUid.equals(liveOwner)&&recipeResultsOwner.equals(liveOwner)&&homeCollectionFilter.isEmpty()&&recipeSearchPanel!=null&&recipeSearchPanel.isAttachedToWindow()){
            scheduleRecipeResults();return;
        }
        recipeSearchGeneration.incrementAndGet();main.removeCallbacks(liveRecipeRender);searchCards.clear();
        if(!accountUid.equals(liveOwner)){cancelPreparation();cancelReviews();clearCookTracking();accountUid=liveOwner;kalomatikPlan=new JSONArray();if(current!=null&&current.optBoolean("private")){current=null;page="home";}completedSteps.clear();cookingModified=false;restDeadline=0;stepEdits.clear();directionEdits.clear();readPrivateCache();}
        if(restoreHome())return;
        if(page.equals("home")&&homeNumbers!=null){
            FunctionCarousel shelf=(FunctionCarousel)body.findViewWithTag("functions.carousel");
            if(shelf!=null&&shelf.isReordering()){main.removeCallbacks(renderAfterFunctionDrag);main.postDelayed(renderAfterFunctionDrag,100);return;}
        }
        if(cookingPanel!=null&&(cookingPanel.pendingStart()||ControlClient.get(this).state.ownRun||ControlClient.get(this).state.stopping))return;
        String pageIdentity=page.equals("step")?page+":"+(current==null?"":current.optString("programDigest"))+":"+currentStep:page.equals("detail")?page+":"+(current==null?"":key(current)):page;boolean samePage=pageIdentity.equals(renderedPage);if(samePage&&parameterEditor!=null&&parameterEditor.isShowing()&&parameterEditorOwner.equals(liveOwner)){renderAfterParameterEditor=true;return;}int retainedScroll=samePage&&scroll!=null?scroll.getScrollY():pageScrollPositions.getOrDefault(page,0);
        if(parameterEditor!=null)parameterEditor.dismiss();releaseCookingPanel();checkpointCook();
        Ui.theme(this);if(cachedHome==null||cachedHome.rotary!=knob)knob.reset();knob=new RotaryController(this);knob.onHomeLongPress(this::requestStandby);tools=new ApplianceTools(()->new CloudSession(this).account().optString("uid"));homeKnob=null;homeNumbers=null;if((page.equals("detail")||page.equals("step"))&&current==null)page="recipes";
        boolean restoreSearchFocus=recipeSearchPanel!=null&&recipeSearchPanel.editing();
        surface=col();surface.setBackgroundColor(Ui.BG);setContentView(surface);
        FrameLayout stage=new FrameLayout(this);surface.addView(stage,new LinearLayout.LayoutParams(-1,-1));LinearLayout workspace=new LinearLayout(this);stage.addView(workspace,new FrameLayout.LayoutParams(-1,-1));
        boolean focused=page.equals("step")||page.equals("function")||page.equals("finished");if(focused)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout canvas=col();workspace.addView(canvas,new LinearLayout.LayoutParams(0,-1,1));
        boolean compactHeader=page.equals("home");
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(28),dp(compactHeader||page.equals("recipes")?4:12),dp(28),dp(compactHeader||page.equals("recipes")?4:8));
        boolean root=ROOT_PAGES.contains(page)||page.equals("modes");
        toolbar.addView(iconButton("menu","Menü öffnen","header.menu",()->setDrawer(true)),new LinearLayout.LayoutParams(dp(72),dp(72)));
        if(!root)toolbar.addView(iconButton("back","Zurück","back",this::onBackPressed),new LinearLayout.LayoutParams(dp(64),dp(64)));
        TextView brand=text(root?"":page.equals("detail")?"Rezept":page.equals("step")?"Geführtes Kochen":page.equals("function")?mode(selectedMode)[1]:"Deine Küche",root?27:21,root?Ui.INK:Ui.MUTED,true);brand.setLetterSpacing(-.025f);LinearLayout.LayoutParams brandSize=new LinearLayout.LayoutParams(0,dp(76),1);brandSize.leftMargin=dp(20);FrameLayout thermalHeader=new FrameLayout(this);thermalHeader.addView(brand,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));toolbar.addView(thermalHeader,brandSize);
        if(page.equals("step")&&activeCookState!=null)toolbar.addView(iconButton("close","Kochen abbrechen","cook.cancel",this::confirmCancelCook),new LinearLayout.LayoutParams(dp(64),dp(64)));
        if(root)toolbar.addView(iconButton("search","Rezept suchen","header.search",this::recipeSearch),new LinearLayout.LayoutParams(dp(80),dp(page.equals("recipes")?64:80)));
        if(page.equals("function"))toolbar.addView(iconButton("more","Optionen für "+mode(selectedMode)[1],"function.options",()->FunctionVisibilityMenu.show(this,selectedMode,mode(selectedMode)[1],tools::invalidate)),new LinearLayout.LayoutParams(dp(64),dp(64)));

        FrameLayout headerFrame=new FrameLayout(this);headerFrame.addView(toolbar,new FrameLayout.LayoutParams(-1,-1));
        headerFrame.setTag(compactHeader?"home.navigation":"page.navigation");
        if(!compactHeader)canvas.addView(headerFrame,new LinearLayout.LayoutParams(-1,dp(page.equals("recipes")?80:112)));
        scroll=new KitchenScroll(this);((KitchenScroll)scroll).onMotion(()->{if(page.equals("recipes"))lastRecipeMotionAt=SystemClock.uptimeMillis();});body=col();body.setPadding(dp(28),dp(page.equals("recipes")?0:14),dp(28),dp(28));scroll.addView(body);canvas.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(page){case "discover":homeCollections();break;case "recipes":if(homeCollectionFilter.isEmpty()){liveRecipeRows=cachedRecipeRows!=null&&cachedRecipeOwner.equals(accountUid)&&cachedRecipeQuery.equals(query)&&cachedRecipeBrowse==browseCatalogue&&cachedPrivate==privateCatalogue&&cachedReady==readyCatalogue&&cachedOnline==catalogue?cachedRecipeRows:Collections.emptyList();paintingRecipeResults=true;recipes(false);paintingRecipeResults=false;liveRecipeRows=null;}else recipes(false);break;case "favorites":recipes(true);break;case "modes":modes();break;case "pantry":pantry();break;case "detail":detail();break;case "step":step();break;case "function":function();break;case "finished":finished();break;case "wifi":wifiPanel=new WifiPanel(this,body);wifiPanel.onViewsChanged(()->registerInteractive(body,"wifi"));registerInteractive(body,"wifi");main.removeCallbacks(connectionUpdate);main.postDelayed(connectionUpdate,3000);break;case "receive":connectionState=DeviceConnectionPage.state(this);DeviceConnectionPage.render(this,body,()->main.postDelayed(()->{if(page.equals("receive"))render();},650),()->go("wifi"));registerInteractive(body,"receive");main.removeCallbacks(connectionUpdate);main.postDelayed(connectionUpdate,1000);break;case "recipe-categories":recipeCategorySettings();break;case "settings":settingsPage();break;case "local-plan":heading("Eigener Einkauf");new KitchenPages(this,legacyNavigation()).content(body,"plan");registerInteractive(body,"local-plan");break;case "plan":planPage();break;default:page="home";home();}
        footer=col();footer.setTag("assistant.footer");footer.setPadding(dp(28),dp(10),dp(28),dp(16));footer.setBackgroundColor(Ui.BG);canvas.addView(footer);
        if(cookingPanel!=null){if(page.equals("step"))cookingPanel.navigation(button("Zurück","step.previous",this::previousStep,false),button("Weiter","step.next",()->cookingPanel.forward(),true));View panelActions=cookingPanel.actionView();((ViewGroup)panelActions.getParent()).removeView(panelActions);footer.addView(panelActions);for(View item:cookingPanel.knobTargets())knob.add(item);cookingPanel.markPreviouslyModified(cookingModified);if(screenResumed)cookingPanel.resume();}
        else if(page.equals("step"))stepNavigation(footer);
        if(page.equals("home")){footer.setPadding(dp(28),0,dp(28),0);FrameLayout.LayoutParams corners=new FrameLayout.LayoutParams(-1,dp(88),Gravity.TOP);stage.addView(headerFrame,corners);}
        
        else if(page.equals("detail")){Button cook=button(preparingRecipe?"Anleitung wird vorbereitet …":requestedServings>0&&program()!=null&&requestedServings!=program().optInt("servings")?"Anleitung für "+requestedServings+" Portionen vorbereiten":activeRecipeMatches()?"Kochen fortsetzen":program()==null?"Mit KI für die Maschine vorbereiten":"Loskochen","recipe.cook",this::startOrResumeCooking,true);cook.setEnabled(!preparingRecipe);if(current.optBoolean("planIncomplete"))cook.setText("In Kalomatik vervollständigen");footer.addView(cook);if(activeRecipeMatches())footer.addView(textLink("Kochen abbrechen","cook.cancel",this::confirmCancelCook));}
        if(page.equals("recipes"))recipeSearchFooter(restoreSearchFocus);
        if(!page.equals("home")&&(ROOT_PAGES.contains(page)||page.equals("step")||page.equals("function")))footer.addView(functionHandle(),new LinearLayout.LayoutParams(-1,dp(42)));if(footer.getChildCount()==0)footer.setVisibility(View.GONE);
        if(cookingPanel!=null){cookingPanel.onPrimaryChanged(primary->{if(page.equals("home")&&homeKnob!=null)homeKnob.refresh();else if((page.equals("step")||page.equals("function"))&&!knob.isEditing())knob.select(primary);});knob.confirmAction(duration,()->{cookingPanel.confirmValues();if(temperature.isEnabled())knob.edit(temperature);else if(stirring.isEnabled())knob.edit(stirring);else knob.select(cookingPanel.primaryAction());});knob.confirmAction(temperature,()->{cookingPanel.confirmValues(false);if(stirring.isEnabled())knob.edit(stirring);else knob.select(cookingPanel.primaryAction());});knob.confirmAction(stirring,()->{knob.select(cookingPanel.primaryAction());if(page.equals("home"))cookingPanel.confirmValues(false);else cookingPanel.confirmSpeed();});if(page.equals("home"))configureHomeKnob(homeNumbers.cupPromptShowing());stirring.setContentDescription(page.equals("home")&&homeProfile().id.equals("warm")?"Linkslauf-Stufe. 0,5, 1 oder 1,5 einstellen. Zum Starten die eigene Starttaste wählen; im Lauf Stoppen auswählen.":page.equals("home")?"Stufe. Im Leerlauf kurz drücken zum Starten; bei Stufe null zurück zur Zeit. Im Lauf bestätigen und Stoppen auswählen. Lange drücken stoppt den Lauf.":"Stufe. Drehen zum Einstellen; Drücken startet den angezeigten Lauf.");if(page.equals("step"))knob.select(cookingPanel.primaryAction());}
        else if(page.equals("step")){View primary=body.findViewWithTag("step.timer");if(primary==null||restDeadline>0)primary=footer.findViewWithTag("step.next");if(primary!=null)knob.select(primary);}

        if(inlineScale!=null)activateScale();else if(cookingPanel==null)ControlClient.get(this).foreground(false);
        if(screenResumed&&page.equals("home")&&homeNumbers!=null)homeNumbers.resumeScale();
        assistantLauncher=null;
        if(page.equals("recipes")||page.equals("favorites")){FrameLayout assistantButton=new FrameLayout(this);assistantButton.addView(new AssistantAvatar(this),new FrameLayout.LayoutParams(-1,-1));target(assistantButton,"assistant","Frag Kalomatik",this::openAssistant);toolbar.addView(assistantButton,new LinearLayout.LayoutParams(dp(64),dp(64)));}
        else if(!page.equals("home")){
            assistantLauncher=new FloatingAssistant(this,this::openAssistant);assistantLauncher.protectFooter(footer);if(page.equals("step"))assistantLauncher.protectControls(body.findViewWithTag("timer.controls"));else if(page.equals("modes"))assistantLauncher.protectChoices(body,body.findViewWithTag("functions.intro"));
            if(inlineScale!=null)assistantLauncher.initialTop(dp(100));else if(page.equals("modes"))assistantLauncher.initialTop(dp(124));
            int plateSize=page.equals("modes")?132:FloatingAssistant.PLATE_DP;stage.addView(assistantLauncher,new FrameLayout.LayoutParams(dp(plateSize),dp(plateSize)));
            knob.add(assistantLauncher);tools.action("assistant",assistantLauncher,this::openAssistant);
        }
        drawerScrim=new FrameLayout(this);drawerScrim.setBackgroundColor(0x33000000);drawerScrim.setOnClickListener(v->setDrawer(false));drawerScrim.setVisibility(View.GONE);drawerOpen=false;FrameLayout.LayoutParams drawerSize=new FrameLayout.LayoutParams(-1,-1);stage.addView(drawerScrim,drawerSize);View navigation=sidebar();navigation.setOnClickListener(v->{});drawerScrim.addView(navigation,new FrameLayout.LayoutParams(dp(350),-1));
        try{JSONObject scene=new JSONObject().put("page",page);scene.put("maximumFunctionSpeed",usesStirrer()?4:10);if(page.equals("function"))scene.put("functionId",selectedMode).put("functionTitle",mode(selectedMode)[1]).put("instruction",String.join("\n",ModeHelp.forMode(selectedMode)));if(current!=null&&(page.equals("detail")||page.equals("step")||page.equals("finished"))){scene.put("recipeTitle",current.optString("title")).put("recipeId",current.optString("id")).put("programDigest",current.optString("programDigest"));JSONObject program=program();if(page.equals("step")&&program!=null){JSONArray steps=program.getJSONArray("steps");JSONObject step=steps.getJSONObject(currentStep);scene.put("stepNumber",currentStep+1).put("stepCount",steps.length()).put("stepTitle",step.optString("title")).put("instruction",step.optString("instruction"));if(currentStep+1<steps.length())scene.put("nextInstruction",steps.getJSONObject(currentStep+1).optString("instruction"));}}tools.context(scene);}catch(JSONException ignored){}
        if(page.equals("recipes")){recipeResultsOwner=accountUid;captureTools(body);if(homeCollectionFilter.isEmpty())scheduleRecipeResults();ArrayList<View> targets=new ArrayList<>();collectInteractive(body,targets);knob.replaceGroup("recipe.results",targets);}
        renderedPage=pageIdentity;if(retainedScroll>0){ScrollView retainedView=scroll;retainedView.post(()->retainedView.scrollTo(0,retainedScroll));}else{body.setAlpha(1);body.setTranslationY(0);}
    }
    private HomeKnobController homeKnob;
    private void configureHomeKnob(boolean cup){
        if(knob==null||homeNumbers==null||cookingPanel==null||!page.equals("home")||drawerOpen)return;
        if(homeKnob==null){homeKnob=new HomeKnobController(knob,homeNumbers,duration,temperature,stirring,cookingPanel);homeNumbers.onKnobStateChanged(()->{if(homeKnob!=null)homeKnob.refresh();});}
        homeKnob.configure(cup);
    }
    private void setDrawer(boolean open){if(drawerScrim==null)return;if(open)new FrostedPanel(this).apply(drawerScrim.getChildAt(0),false,0);drawerOpen=open;knob.scope(open?drawerScrim:null);drawerScrim.setVisibility(open?View.VISIBLE:View.GONE);if(open){drawerScrim.setAlpha(0f);drawerScrim.animate().alpha(1f).setDuration(160).start();}else if(page.equals("home")&&homeNumbers!=null)configureHomeKnob(homeNumbers.cupPromptShowing());else knob.select(toolbar.findViewWithTag("header.menu"));}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(homeNumbers!=null&&page.equals("home"))homeNumbers.windowTouch(event.getActionMasked());
        CarouselMotion.touchWindow(getWindow().getDecorView(),event.getActionMasked());
        if(StandbyController.get(this).blocksInput())return true;
        if(knob!=null)knob.touchOutside(event);int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){rootGestureStartedAt=event.getEventTime();rootMinX=rootMaxX=event.getX();}
        else {rootMinX=Math.min(rootMinX,event.getX());rootMaxX=Math.max(rootMaxX,event.getX());}
        if(action==MotionEvent.ACTION_DOWN)recipeTouchHeld=page.equals("recipes")&&inside(scroll,event);
        if(recipeTouchHeld)lastRecipeMotionAt=SystemClock.uptimeMillis();
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)recipeTouchHeld=false;
        float dx=event.getX()-gestureX,dy=event.getY()-gestureY;
        if(rootDrag!=null){if(action==MotionEvent.ACTION_MOVE)rootDrag.drag(dx);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(action==MotionEvent.ACTION_UP)rootDrag.drag(dx);rootDrag.release(action==MotionEvent.ACTION_UP,rootSwipeVelocity(event));}return true;}
        if(functionGesture&&functionDrawer!=null){if(action==MotionEvent.ACTION_MOVE)functionDrawer.dragOpen(-dy);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){functionDrawer.releaseOpen(action==MotionEvent.ACTION_UP);functionGesture=false;}return true;}
        if(functionDrawer!=null)return super.dispatchTouchEvent(event);
        if(stepSlider!=null){dx=slideForward?Math.min(0,dx):Math.max(0,dx);if(action==MotionEvent.ACTION_MOVE){stepSlider.drag(dx);return true;}if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){StepPageSlider slider=stepSlider;stepSlider=null;slider.release(dx,action==MotionEvent.ACTION_UP&&Math.abs(dx)>surface.getWidth()*.22f,()->swipeStep(slideForward),()->(FrameLayout)surface.getChildAt(0),()->scroll,()->footer);return true;}return true;}
        if(action==MotionEvent.ACTION_DOWN){
            gestureX=event.getX();gestureY=event.getY();gestureAxisLocked=rootVertical=false;
            stepGestureBlocked=blocksStepGesture(body,event.getRawX(),event.getRawY());
            assistantGesture=!drawerOpen&&assistantLauncher!=null&&assistantLauncher.contains(event.getRawX(),event.getRawY());
            rootEdgeDirection=rootEdgeDirection(event);
            rootGesture=!drawerOpen&&ROOT_PAGES.contains(page)&&rootEdgeDirection!=0&&(recipeSearchPanel==null||!recipeSearchPanel.editing());
            edgeGesture=!drawerOpen&&(inside(surface.findViewWithTag("navigation.functions"),event)||page.equals("home")&&inside(surface.findViewWithTag("home.functions.region"),event));
        }
        if(assistantGesture||drawerOpen)return super.dispatchTouchEvent(event);
        if((action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP&&rootGesture)&&!rootVertical){
            int slop=ViewConfiguration.get(this).getScaledTouchSlop();
            if(!gestureAxisLocked&&Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.25f){gestureAxisLocked=true;rootVertical=true;if(edgeGesture&&dy<0&&action==MotionEvent.ACTION_MOVE){cancelChildGesture(event);if(createFunctions()){functionGesture=true;functionDrawer.dragOpen(-dy);}return true;}}
            if(!gestureAxisLocked&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.25f){
                gestureAxisLocked=true;
                if(rootGesture){cancelChildGesture(event);if(dx*rootEdgeDirection<0)beginRootDrag(rootEdgeDirection);if(rootDrag!=null){rootDrag.drag(dx);if(action==MotionEvent.ACTION_UP)rootDrag.release(true,rootSwipeVelocity(event));}return true;}
                if(page.equals("step")&&!stepGestureBlocked&&navigationIdle()){cancelChildGesture(event);slideForward=dx<0;stepSlider=new StepPageSlider(this,(FrameLayout)surface.getChildAt(0),scroll,footer,stepPeek(slideForward));stepSlider.drag(dx);return true;}
            }
        }
        if(rootGesture&&gestureAxisLocked&&!rootVertical)return true;
        return super.dispatchTouchEvent(event);
    }
    private float rootSwipeVelocity(MotionEvent event){
        float offset=event.getX()-gestureX,excursion=offset<0?gestureX-rootMinX:rootMaxX-gestureX;
        return RootSwipeDecision.flickVelocity(offset,excursion,event.getEventTime()-rootGestureStartedAt,getResources().getDisplayMetrics().density);
    }
    private void cancelChildGesture(MotionEvent event){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();}
    private boolean inside(View view,MotionEvent event){Rect r=new Rect();return view!=null&&view.isShown()&&view.getGlobalVisibleRect(r)&&r.contains((int)event.getRawX(),(int)event.getRawY());}
    /** Only a gesture beginning at the display edge can change main sections. */
    private int rootEdgeDirection(MotionEvent event){
        Rect bounds=new Rect();if(surface==null||!surface.getGlobalVisibleRect(bounds)||!bounds.contains((int)event.getRawX(),(int)event.getRawY()))return 0;
        // Physical C3 edge swipes first reported 38–60 px inside the glass.
        // Keep the initial contact's ownership; a row never hands off mid-swipe.
        int edge=Math.min(dp(80),bounds.width()/8);
        if(event.getRawX()<bounds.left+edge)return -1;
        return event.getRawX()>=bounds.right-edge?1:0;
    }
    private boolean navigationIdle(){ControlState state=ControlClient.get(this).state;return !state.ownRun&&!state.stopping&&!state.uncertainRun&&state.pendingAction.isEmpty()&&!ControlClient.get(this).program.active()&&(cookingPanel==null||!cookingPanel.pendingStart())&&(homeNumbers==null||!homeNumbers.cupPromptShowing());}
    private void beginRootDrag(int direction){
        int target=ROOT_PAGES.indexOf(page)+direction;if(!ROOT_PAGES.contains(page)||target<0||target>=ROOT_PAGES.size())return;
        if(!navigationIdle()){Toast.makeText(this,"Während des Kochens über das Menü wechseln.",Toast.LENGTH_SHORT).show();return;}
        String origin=page,oldFunction=homeFunction;int[] oldSettings=homeSettings.clone();boolean oldMotion=homeMotionStill,oldReverse=homeReverse;Boolean oldIntervals=homeIntervals;View oldSurface=surface;
        if(homeNumbers!=null)homeNumbers.pauseScale();preparingRootDrag=true;try{if(ROOT_PAGES.get(target).equals("home"))prepareManualHome();go(ROOT_PAGES.get(target));}finally{preparingRootDrag=false;}
        if(surface==oldSurface)return;
        rootDrag=new RootPageDrag((FrameLayout)findViewById(android.R.id.content),oldSurface,surface,-direction,()->{rootDrag=null;flushRootRender();},()->{rootDrag=null;homeFunction=oldFunction;homeSettings=oldSettings;homeMotionStill=oldMotion;homeReverse=oldReverse;homeIntervals=oldIntervals;go(origin);flushRootRender();});
    }
    private void flushRootRender(){if(renderAfterRootDrag){renderAfterRootDrag=false;render();}}
    private void prepareManualHome(){if(!homeFunction.equals("manual")){homeFunction="manual";homeMotionStill=false;homeIntervals=null;homeSettings=new int[]{homeSettings[0],Math.min(homeSettings[1],150),homeSettings[2]};if(homeSettings[1]>100)homeSettings[1]=0;}}
    private void startManualHome(){if(!navigationIdle()){if(confirmNavigation(this::startManualHome))return;Toast.makeText(this,"Warte, bis die Maschine stillsteht.",Toast.LENGTH_SHORT).show();return;}releaseCookingPanel();prepareManualHome();go("home");}
    private boolean createFunctions(){
        if(functionDrawer!=null)return true;if(!navigationIdle()){Toast.makeText(this,"Funktionen nach dem Stoppen auswählen.",Toast.LENGTH_SHORT).show();return false;}
        if(recipeSearchPanel!=null)recipeSearchPanel.closeEditor();functionsOrigin=page;LinearLayout choices=col();choices.setPadding(dp(24),0,dp(24),dp(24));
        FunctionOrder order=new FunctionOrder(this);
        FunctionOrderGrid grid=new FunctionOrderGrid(this,order.items(),5,(id,position)->{
            LinearLayout tile=functionTile(id,"functions.choose."+id,106,true);
            TextView label=(TextView)tile.getChildAt(1);label.setTextSize(18);label.setMinHeight(dp(52));label.setPadding(dp(4),dp(8),dp(4),0);
            catalogueVisibility(tile,id,order);
            tile.setOnClickListener(v->{FunctionDrawer sheet=functionDrawer;if(sheet!=null)sheet.dispose();openMode(id);});return tile;
        },(id,position)->{order.move(id,position);functionAnchor="";renderAfterRootDrag=true;});
        choices.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout stage=(FrameLayout)surface.getChildAt(0);int top=toolbar.getHeight();View pager=surface.findViewWithTag("home.function.pager");if(page.equals("home")&&pager!=null){int[] location=new int[2],base=new int[2];pager.getLocationOnScreen(location);stage.getLocationOnScreen(base);top=location[1]-base[1];}
        View origin=surface.findViewWithTag(page.equals("home")?"home.functions.region":"navigation.functions");int offset=stage.getHeight()-top;if(origin!=null){int[] start=new int[2],base=new int[2];origin.getLocationOnScreen(start);stage.getLocationOnScreen(base);offset=Math.max(1,start[1]-base[1]-top);}
        functionDrawer=new FunctionDrawer(this,choices,offset,origin,()->{functionDrawer=null;functionGesture=false;main.post(()->{if(!destroyed&&functionDrawer==null&&rootDrag==null)flushRootRender();});knob.replaceGroup("functions.sheet",Collections.emptyList());knob.scope(null);if(page.equals("home")&&homeNumbers!=null)configureHomeKnob(homeNumbers.cupPromptShowing());});FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);lp.topMargin=top;stage.addView(functionDrawer,lp);knob.add(functionDrawer.findViewWithTag("navigation.functions.close"));ArrayList<View> sheetTargets=new ArrayList<>();collectInteractive(functionDrawer,sheetTargets);knob.replaceGroup("functions.sheet",sheetTargets);knob.scope(functionDrawer);return true;
    }
    private void openFunctions(){if(createFunctions())functionDrawer.open();}
    /** Read-only neighbor presentation: no active panel, receipt, timer start or image generation. */
    private View stepPeek(boolean forward){JSONArray steps=program().optJSONArray("steps");int index=forward?RecipePresentation.next(steps,currentStep+1):RecipePresentation.previous(steps,currentStep-1);LinearLayout copy=col();copy.setPadding(dp(28),dp(14),dp(28),dp(28));copy.setBackgroundColor(Ui.BG);copy.addView(text(current.optString("title"),28,Ui.INK,true));gap(copy,12);if(index<0||index>=steps.length()){copy.addView(text(index<0?"Rezept ansehen":"Gericht fertig",34,Ui.INK,true));return copy;}LinearLayout progress=new LinearLayout(this);for(int i=0;i<steps.length();i++){if(RecipePresentation.omitted(steps.optJSONObject(i)))continue;View mark=new View(this);mark.setBackground(shape(i<=index?Ui.ACCENT:Ui.LINE,3,false));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(4),1);size.rightMargin=dp(4);progress.addView(mark,size);}copy.addView(progress);gap(copy,14);copy.addView(text("Schritt "+RecipePresentation.ordinal(steps,index)+" von "+RecipePresentation.count(steps),17,Ui.MUTED,false));gap(copy,16);JSONObject step=steps.optJSONObject(index);copy.addView(text(step.optString("title"),34,Ui.INK,true));gap(copy,12);copy.addView(text(step.optString("instruction"),28,Ui.INK,false));for(StepFacts.Fact fact:StepFacts.from(step.optString("instruction"))){gap(copy,12);LinearLayout row=new LinearLayout(this);ImageView icon=new ImageView(this);icon.setImageDrawable(new InterfaceIcon(fact.icon));row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));TextView value=text(fact.text,24,Ui.INK,true);value.setPadding(dp(14),0,0,0);row.addView(value);copy.addView(row);}return copy;}
    private boolean blocksStepGesture(View view,float x,float y){if(view==null||!view.isShown())return false;Rect bounds=new Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains((int)x,(int)y))return false;if(view instanceof ParameterDial||view instanceof EditText||view instanceof Button)return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(blocksStepGesture(((ViewGroup)view).getChildAt(i),x,y))return true;return false;}
    private void swipeStep(boolean forward){if(!forward){previousStep();return;}if(cookingPanel!=null&&cookingPanel.forward())return;View next=footer==null?null:footer.findViewWithTag("step.next");if(next!=null&&next.isEnabled())next.performClick();else Toast.makeText(this,"Beende diesen Maschinenschritt zuerst.",Toast.LENGTH_SHORT).show();}
    private View sidebar(){
        LinearLayout rail=col();rail.setPadding(dp(22),dp(24),dp(22),dp(22));rail.setBackgroundColor(Ui.CARD);TextView name=text("Deine Küche",32,Ui.INK,true);rail.addView(name);gap(rail,24);
        String[][] destinations={{"home","home","Start"},{"recipes","recipes","Rezepte"},{"favorites","favorite","Favoriten"},{"modes","modes","Funktionen"},{"plan","plan","Kochplan"},{"pantry","pantry","Vorrat"}};
        for(String[] item:destinations){LinearLayout entry=new LinearLayout(this);entry.setGravity(Gravity.CENTER_VERTICAL);entry.setPadding(dp(16),dp(12),dp(16),dp(12));ImageView icon=new ImageView(this);icon.setImageDrawable(new InterfaceIcon(item[1],page.equals(item[0])?Ui.ACCENT:Ui.INK));entry.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));TextView label=text(item[2],27,Ui.INK,true);LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(0,-2,1);labelSize.leftMargin=dp(20);entry.addView(label,labelSize);target(entry,"nav."+item[0],item[2],()->{setDrawer(false);if(item[0].equals("home")){startManualHome();return;}if(item[0].equals("modes")){openFunctions();return;}if(item[0].equals("recipes")){homeCollectionFilter="";query="";recipePage=0;activeRecipeCategory="";}go(item[0]);});entry.setBackground(shape(page.equals(item[0])?0x33ee6542:0,18,true));rail.addView(entry,new LinearLayout.LayoutParams(-1,dp(92)));gap(rail,8);}
        rail.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));themeButtons(rail);gap(rail,12);rail.addView(button("Einstellungen","nav.settings",()->{setDrawer(false);settings();},false));return rail;
    }
    private View iconButton(String icon,String label,String id,Runnable action){FrameLayout button=new FrameLayout(this);ImageView picture=new ImageView(this);picture.setImageDrawable(new InterfaceIcon(icon));FrameLayout.LayoutParams inset=new FrameLayout.LayoutParams(dp(icon.equals("back")?40:36),dp(icon.equals("back")?40:36),Gravity.CENTER);button.addView(picture,inset);target(button,id,label,action);button.setBackground(shape(Ui.BG,16,true));return button;}
    private void openAssistant(){
        if(cookingPanel!=null&&!page.equals("home")){cookingPanel.showAssistant();return;}
        if(assistantDialog==null||!assistantDialog.isShowing())assistantDialog=CookingAssistant.show(this,tools,this::assistantClosed);
    }
    private void closeAssistantForNavigation(){renderAfterAssistant=false;if(assistantDialog!=null){assistantDialog.dismiss();assistantDialog=null;}}
    private void assistantClosed(){
        if(assistantDialog!=null&&assistantDialog.isShowing())return;
        if(page.equals("home")&&homeHasPantry&&new PantryNudge(this).complete())renderAfterAssistant=true;
        assistantDialog=null;if(renderAfterAssistant&&!destroyed&&!isFinishing()){renderAfterAssistant=false;main.post(()->{if(!destroyed&&!isFinishing())render();});}
    }

    private void heading(String title){TextView h=text(title,36,Ui.INK,true);h.setLetterSpacing(-.035f);body.addView(h);gap(body,18);}
    private View textLink(String label,String id,Runnable action){
        TextView link=new TextView(this){@Override protected void drawableStateChanged(){super.drawableStateChanged();setPaintFlags(isSelected()||isFocused()?getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG:getPaintFlags()&~Paint.UNDERLINE_TEXT_FLAG);}};link.setText(label);link.setTextSize(21);link.setTextColor(Ui.ACCENT);link.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));link.setGravity(Gravity.CENTER_VERTICAL);link.setPadding(dp(8),0,0,0);link.setMinHeight(dp(56));
        android.graphics.drawable.Drawable arrow=new InterfaceIcon("arrow-right",Ui.ACCENT);arrow.setBounds(0,0,dp(18),dp(18));link.setCompoundDrawables(null,null,arrow,null);link.setCompoundDrawablePadding(dp(7));
        target(link,id,label,action);link.setBackground(null);link.setDefaultFocusHighlightEnabled(false);link.setOnFocusChangeListener((v,has)->{if(has)knob.select(v);});return link;
    }
    private void section(String title,String label,Runnable action){gap(body,20);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(title,26,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));if(label!=null){View link=title.equals("Einfach loslegen")?textLink(label,"section."+title,action):button(label,"section."+title,action,false);row.addView(link);}body.addView(row);gap(body,14);}
    private void home(){
        reverse=homeReverse;
        dialRow(homeSettings[0],homeSettings[1],homeSettings[2],v->{homeSettings=new int[]{duration.value(),temperature.value(),stirring.value()};});renderDirection();
        cookingPanel=new GuidedControlPanel(this,body,duration,temperature,stirring,()->reverse,modeReference(homeSettings),tools,()->go("home"));cookingPanel.setFunctionTitle("Manuell kochen");cookingPanel.speedLimit(usesStirrer()?40:page.equals("home")&&homeFunction.equals("turbo")?120:100);cookingPanel.compactHome();cookingPanel.homeAttachment(()->homeNumbers!=null&&homeNumbers.stirrerSelected());cookingPanel.onStartHint(()->{if(homeNumbers!=null)homeNumbers.startHint();});body.setPadding(dp(28),dp(HomeNumberControls.TOP_SPACE),dp(28),dp(5));
        cookingPanel.onHomeStartCue(homeNumbers::startCue);cookingPanel.homeCup(homeNumbers::cupReady,homeNumbers::confirmCup,homeNumbers.startButton(),homeNumbers::requireCup);cookingPanel.homePresentation(homeNumbers::primaryState);applyHomeFunction(false);
        View functionSpace=new View(this);functionSpace.setTag("home.functions.space");body.addView(functionSpace,new LinearLayout.LayoutParams(-1,dp(0),1));LinearLayout functions=col();functions.setTag("home.functions.region");body.addView(functions);homeShortcuts(functions);
        HomeNumberControls fittedControls=homeNumbers;ScrollView homeScroll=scroll;LinearLayout homeBody=body;
        Runnable fitFunctions=()->{if(homeNumbers==fittedControls&&homeScroll.getHeight()>0&&functions.getHeight()>0)fittedControls.fitWorkspace(homeScroll.getHeight()-homeBody.getPaddingTop()-homeBody.getPaddingBottom()-functions.getHeight());};
        functions.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->functions.post(fitFunctions));homeScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->functions.post(fitFunctions));
        if(homeFunction.equals("manual")&&activeCookState!=null&&accountUid.equals(activeCookState.getString("owner",""))){JSONObject resumedRecipe=activeRecipe();if(resumedRecipe!=null){LinearLayout resume=col();resume.setPadding(dp(24),dp(22),dp(24),dp(24));resume.setBackground(shape(Ui.CARD,22,false));resume.addView(text(resumedRecipe.optString("title"),28,Ui.INK,true));gap(resume,8);if(activeCookState.getBoolean("coldInterrupted")){resume.addView(text("Unterbrochen · Schritt "+(activeCookState.getInt("step",0)+1)+" ist gespeichert",18,Ui.MUTED,false));gap(resume,16);}resume.addView(button("Kochen fortsetzen","cook.resume",this::resumeActiveCook,true));resume.addView(textLink("Kochen abbrechen","cook.cancel",this::confirmCancelCook));body.addView(resume);gap(body,20);}}
    }
    /** The second page keeps discovery separate from the machine workspace. */
    private void applyHomeLimits(){if(applyingHome||!page.equals("home"))return;applyingHome=true;try{HomeFunctionProfile p=homeProfile();duration.precision(p.id.equals("slow")?60:1,false);duration.choices(p.minSeconds(),p.maxSeconds);temperature.precision(p.fixedDuration()?20:p.id.equals("fry")?5:1,false);temperature.choices(p.minTemperature,p.maxTemperature);stirring.precision(5,p.id.equals("manual"));stirring.choices(p.minSpeed,p.maxSpeed);stirring.ceiling(p.maxSpeed);}finally{applyingHome=false;}}
    private void applyHomeFunction(boolean defaults){HomeFunctionProfile p=homeProfile();AccessorySelection.function(this,p.id);boolean fixedReverse=p.id.equals("slow")||p.id.equals("warm")||p.id.equals("fry");reverse=fixedReverse||homeReverse;if(fixedReverse)homeReverse=true;applyHomeLimits();if(defaults){duration.seedValue(p.seconds);temperature.seedValue(p.temperature);stirring.seedValue(p.speed);}if(p.id.equals("slow")){duration.seedValue(Math.max(3600,Math.min(28800,Math.round(duration.value()/60f)*60)));stirring.seedValue(5);}if(p.fixedDuration()){if(temperature.value()!=80&&temperature.value()!=100)temperature.seedValue(p.temperature);duration.seedValue(HomeFunctionProfile.thickenSeconds(temperature.value()));stirring.seedValue(p.speed);}homeNumbers.function(p,grade->{eggGrade=grade;if(grade>=0)duration.value(HomeFunctionProfile.eggSeconds(grade));homeNumbers.eggGrade(grade);});if(p.id.equals("eggs"))homeNumbers.eggGrade(eggGrade);cookingPanel.homeFunction(p);renderDirection();homeSettings=new int[]{duration.value(),temperature.value(),stirring.value()};}
    /** Prepare the existing guarded fry profile; changing a shortcut never starts it. */
    private boolean chooseHomeFrying(){
        if(!canSwitchHomeProfile()||!temperature.isEnabled())return false;
        homeFunction="fry";homeReverse=true;applyHomeFunction(false);
        temperature.value(180);homeSettings=new int[]{duration.value(),temperature.value(),stirring.value()};
        return true;
    }
    private boolean canSwitchHomeProfile(){
        ControlState s=ControlClient.get(this).state;
        return !ControlClient.get(this).program.active()&&page.equals("home")&&screenResumed&&!destroyed&&cookingPanel!=null&&!cookingPanel.homePreparationInFlight()&&!s.ownRun&&!s.stopping&&!s.uncertainRun&&!s.closing&&s.pendingAction.isEmpty()&&!s.potCheck.active();
    }
    /** Preserve valid settings; unsupported high heat is turned off. No apply/start. */
    private boolean chooseHomeManual(int target){
        if(!canSwitchHomeProfile()||target<0||target>150||target>0&&target<35)return false;
        homeFunction="manual";homeMotionStill=false;homeReverse=reverse;applyHomeFunction(false);
        temperature.value(target);homeSettings=new int[]{duration.value(),temperature.value(),stirring.value()};
        homeNumbers.showFunctionControls();return true;
    }
    private void homeCollections(){
        heading("Deine Küche");
        body.addView(textLink("Kochplan öffnen","discover.plan",()->go("plan")));gap(body,24);
        // Reuse one validated recipe set and one kitchen snapshot for this render.
        JSONArray prepared=allPrepared();JSONObject kitchen=new KitchenStore(this).snapshot();
        List<JSONObject> all=rows(false,prepared,kitchen),favorite=rows(true,prepared,kitchen);
        List<JSONObject> featured=FeaturedRecipes.select(favorite,all,5,this::key);int planned=0;for(int i=kalomatikPlan.length()-1;i>=0&&planned<2;i--){JSONObject meal=kalomatikPlan.optJSONObject(i);if(meal==null||meal.optString("date").compareTo(java.time.LocalDate.now().plusDays(2).toString())>0)continue;try{JSONObject card=new JSONObject().put("id",Pairing.fingerprint((accountUid+":planned:"+meal.optString("id")).getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("title",meal.optString("title")).put("ingredients",meal.optJSONArray("ingredients")).put("planMeal",meal).put("plannedDate",meal.optString("date")).put("portion",meal.opt("cookingPortions")).put("knownNutrition",meal.opt("knownNutrition"));for(JSONObject known:all)if(known.optString("title").equalsIgnoreCase(meal.optString("title"))){card.put("coverUrl",known.optString("coverUrl"));break;}featured.add(0,card);planned++;}catch(Exception invalid){}}
        homeFilters();featured=filteredHome(featured,favorite);homeHasPantry=homeFilter.equals("Für dich")&&new PantryNudge(this).showHome();
        if(!featured.isEmpty()||homeHasPantry)featuredCarousel(featured,!favorite.isEmpty());else{body.addView(text("Hier sind noch keine Gerichte gespeichert.",23,Ui.MUTED,false));gap(body,20);}
    }
    private final Runnable renderAfterFunctionDrag=()->{if(!destroyed&&page.equals("home"))render();};
    private String functionAnchor="";
    private View functionHandle(){FunctionHandle handle=new FunctionHandle(this);handle.setTag("navigation.functions");handle.setOnClickListener(v->openFunctions());knob.add(handle);return handle;}
    private void homeShortcuts(LinearLayout host){host.addView(functionHandle(),new LinearLayout.LayoutParams(-1,dp(42)));functionShelf(host,false);}
    private void catalogueVisibility(LinearLayout tile,String id,FunctionOrder order){
        boolean visible=order.isVisible(id);ImageView photo=(ImageView)tile.getChildAt(0);TextView label=(TextView)tile.getChildAt(1);
        if(!visible){android.graphics.ColorMatrix gray=new android.graphics.ColorMatrix();gray.setSaturation(0);photo.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));photo.setAlpha(.45f);label.setTextColor(Ui.MUTED);tile.setContentDescription(mode(id)[1]+" · In der Funktionsleiste ausgeblendet");}
    }
    private void functionShelf(LinearLayout host,boolean editing){
        FunctionOrder order=new FunctionOrder(this);List<String> ids=editing?order.items():order.visibleItems();
        if(!editing){
            if(ids.isEmpty()){TextView empty=text("Noch keine Funktionen ausgewählt. Im Menü unter Funktionen wählst du aus, was hier erscheint.",19,Ui.MUTED,false);empty.setTag("functions.empty");host.addView(empty);return;}
            int initial=Math.max(0,ids.indexOf(functionAnchor));Map<String,Integer> copies=new java.util.HashMap<>();
            FunctionCarousel carousel=new FunctionCarousel(this,ids.size(),initial,index->{String id=ids.get(index);int copy=copies.getOrDefault(id,0);copies.put(id,copy+1);return functionTile(id,"mode."+id+(copy==0?"":".copy."+copy),156,true);});
            carousel.onPositionChanged(index->functionAnchor=ids.get(index));
            carousel.onReordered(indices->{List<String> selected=new ArrayList<>();for(int index:indices)selected.add(ids.get(index));order.setVisibleOrder(selected);functionAnchor=ids.get(carousel.position());});host.addView(carousel,new LinearLayout.LayoutParams(-1,-2));return;
        }
        FunctionOrderGrid grid=new FunctionOrderGrid(this,ids,(id,position)->{
            LinearLayout tile=functionTile(id,"function.slot."+position,106,true);
            TextView label=(TextView)tile.getChildAt(1);label.setText((position+1)+" · "+mode(id)[1]);tile.setContentDescription(tile.getContentDescription()+" · Position "+(position+1));
            catalogueVisibility(tile,id,order);
            return tile;
        },(id,position)->{new FunctionOrder(this).move(id,position);functionAnchor="";main.post(this::render);});
        host.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
    private LinearLayout functionTile(String id,String tag,int size){return functionTile(id,tag,size,true);}
    private LinearLayout functionTile(String id,String tag,int size,boolean showLabel){
        LinearLayout tile=col();tile.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);ImageView icon=new ImageView(this);icon.setTag("modeIcon."+id);icon.setDuplicateParentStateEnabled(true);
        GradientDrawable focusRing=new GradientDrawable();focusRing.setShape(GradientDrawable.OVAL);focusRing.setColor(Color.TRANSPARENT);focusRing.setStroke(dp(3),Ui.ACCENT);StateListDrawable rings=new StateListDrawable();rings.addState(new int[]{android.R.attr.state_selected},focusRing);rings.addState(new int[]{android.R.attr.state_focused},focusRing);rings.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));icon.setForeground(rings);
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_selected},shape(Ui.SOFT,100,true));states.addState(new int[]{},shape(Ui.SOFT,100,false));icon.setBackground(states);icon.setClipToOutline(true);tile.addView(icon,new LinearLayout.LayoutParams(dp(size),dp(size)));if(!FunctionPhotography.bind(this,icon,id)){icon.setImageDrawable(new ModeIcon(id));icon.setPadding(dp(18),dp(18),dp(18),dp(18));}
        if(showLabel){TextView label=text(mode(id)[1],page.equals("home")?24:18,Ui.INK,true);label.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);label.setMaxLines(2);label.setMinHeight(dp(page.equals("home")?72:52));if(page.equals("home"))label.setPadding(dp(4),dp(10),dp(4),0);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));}else tile.setTooltipText(mode(id)[1]);
        boolean available=modeRunnable(id);target(tile,tag,mode(id)[1]+(available?"":", noch nicht verfügbar"),()->openMode(id));tile.setBackgroundColor(Color.TRANSPARENT);return tile;
    }
    private void homeFilters(){
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);strip.addView(row);
        for(String filter:new String[]{"Für dich","Favoriten","Zuletzt gekocht","Schnell","Frühstück","Mittagessen","Abendessen"}){
            boolean active=filter.equals(homeFilter);LinearLayout tab=col();tab.setGravity(Gravity.CENTER);TextView label=text(filter,20,active?Ui.INK:Ui.MUTED,active);label.setGravity(Gravity.CENTER);tab.addView(label,new LinearLayout.LayoutParams(-2,dp(42)));
            View underline=new View(this);underline.setBackground(shape(active?Ui.ACCENT:Color.TRANSPARENT,2,false));LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(-1,dp(3));rule.leftMargin=dp(10);rule.rightMargin=dp(10);tab.addView(underline,rule);
            target(tab,"home.filter."+filter,filter,()->{homeFilter=filter;featuredRecipeKey="";render();});tab.setBackgroundColor(Color.TRANSPARENT);LinearLayout.LayoutParams tabSize=new LinearLayout.LayoutParams(-2,dp(54));tabSize.rightMargin=dp(30);row.addView(tab,tabSize);
        }
        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);filters.setTag("home.filters");filters.addView(strip,new LinearLayout.LayoutParams(0,dp(56),1));filters.addView(iconButton("layout-grid","Übersicht öffnen","home.collection",this::openHomeCollection),new LinearLayout.LayoutParams(dp(56),dp(56)));body.addView(filters);gap(body,12);
    }
    private void openHomeCollection(){query="";browseCatalogue=false;recipePage=0;if(homeFilter.equals("Favoriten")){go("favorites");return;}homeCollectionFilter=homeFilter.equals("Für dich")?"":homeFilter;go("recipes");}
    private List<JSONObject> filteredHome(List<JSONObject> featured,List<JSONObject> favorites){return filteredCollection(homeFilter,featured,favorites);}
    private List<JSONObject> filteredCollection(String filter,List<JSONObject> featured,List<JSONObject> favorites){
        if(filter.equals("Für dich"))return featured;if(filter.equals("Favoriten"))return favorites;List<JSONObject> all=rows(false),out=new ArrayList<>();
        if(filter.equals("Zuletzt gekocht")){for(String id:new RecentCooking(this,accountUid).ids())for(JSONObject item:all)if(key(item).equals(id)){out.add(item);break;}return out;}
        for(JSONObject item:all){JSONObject timing=item.optJSONObject("timing");if(timing==null&&item.optJSONObject("machineProgram")!=null)timing=item.optJSONObject("machineProgram").optJSONObject("timing");String title=item.optString("title").toLowerCase(Locale.GERMAN);boolean match=false;
            if(filter.equals("Schnell")&&timing!=null){int prep=timing.optInt("prepMinutes",-1),cook=timing.optInt("cookMinutes",-1),rest=timing.optInt("restMinutes",0);match=prep>=0&&cook>=0&&prep+cook+rest<=30;}
            String type=item.optString("mealType");if(filter.equals("Frühstück"))match=type.equals("breakfast")||title.matches(".*(porridge|müsli|pancake|crêpe|frühstück|omelett).*" );if(filter.equals("Mittagessen"))match=type.equals("lunch")||title.matches(".*(huhn|reis|pasta|nudel|kartoffel|curry).*" );if(filter.equals("Abendessen"))match=type.equals("dinner")||title.matches(".*(suppe|salat|focaccia|pizza|brot).*" );if(match)out.add(item);
        }return out;
    }

    private void featuredCarousel(List<JSONObject> recipes,boolean hasFavorites){
        List<HomeGallery.Item> items=HomeGallery.items(recipes,homeHasPantry);
        TextView position=text("",17,Ui.MUTED,false);position.setGravity(Gravity.CENTER);
        position.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        int initial=0;for(int i=0;i<items.size();i++)if(items.get(i).key().equals(featuredRecipeKey)){initial=i;break;}
        RecipeCarousel carousel=new RecipeCarousel(this,items.size(),initial,.43f,new RecipeCarousel.Cards(){
            public void inactive(View card){View dismiss=card.findViewById(com.kitchenos.connect.R.id.pantry_dismiss);if(dismiss!=null)dismiss.setEnabled(false);}
            public View create(int index){HomeGallery.Item item=items.get(index);return item.pantry?pantryGalleryCard():featuredCard(item.recipe);}
            public void selected(int index,View active){
                HomeGallery.Item item=items.get(index);featuredRecipeKey=item.key();if(ingredientWarmup!=null)main.removeCallbacks(ingredientWarmup);if(!item.pantry){String owner=accountUid;ingredientWarmup=()->{if(!destroyed&&page.equals("home")&&owner.equals(new CloudSession(ApplianceActivity.this).account().optString("uid"))&&featuredRecipeKey.equals(item.key()))IngredientPortraits.prewarm(ApplianceActivity.this,item.recipe.optJSONArray("ingredients"));};main.postDelayed(ingredientWarmup,900);}
                position.setText((index+1)+" / "+items.size());
                target(active,item.pantry?"pantry.photo":"recipe.featured",item.title()+(item.pantry?"":" öffnen"),()->{if(item.pantry)chat(true);else if(item.recipe.optJSONObject("planMeal")!=null)openPlannedMeal(item.recipe.optJSONObject("planMeal"));else if(item.recipe.optJSONObject("machineProgram")==null)openSourceRecipe(item.recipe);else openRecipe(item.recipe);});
                ArrayList<View> targets=new ArrayList<>();targets.add(active);
                if(item.pantry){View dismiss=active.findViewById(com.kitchenos.connect.R.id.pantry_dismiss);if(dismiss!=null){dismiss.setEnabled(true);target(dismiss,"pantry.dismiss","Vorratskarte später erinnern",()->{new PantryNudge(ApplianceActivity.this).dismiss(System.currentTimeMillis());featuredRecipeKey="";render();});dismiss.setBackground(shape(0xa6101411,24,true));targets.add(dismiss);}}
                // Neighbor cards never register cooking or assistant actions.
                knob.replaceGroup("home.featured",targets);tools.invalidate();
                active.setBackground(shape(Ui.CARD,24,true));
            }
        });
        body.addView(carousel,new LinearLayout.LayoutParams(-1,dp(176)));
    }

    private View pantryGalleryCard(){
        FrameLayout card=new FrameLayout(this);card.setClipToOutline(true);card.setBackground(KitViews.shape(this,Ui.CARD,24,0));
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setContentDescription("Vorräte in Gläsern und frisches Gemüse");
        try(java.io.InputStream source=getAssets().open("visuals/pantry-gallery-v1.png")){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;if(pantryGalleryPhoto==null)pantryGalleryPhoto=BitmapFactory.decodeStream(source,null,options);photo.setImageBitmap(pantryGalleryPhoto);}catch(java.io.IOException unavailable){photo.setImageDrawable(new InterfaceIcon("pantry",Ui.MUTED));}
        card.addView(photo,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xee101411}));card.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=col();caption.setPadding(dp(16),dp(14),dp(16),dp(14));caption.addView(text("Erfasse deinen Vorrat",22,0xffffffff,true));gap(caption,6);caption.addView(text("Mit dem Handy fotografieren",14,0xfff2f3ee,false));card.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        FrameLayout dismiss=new FrameLayout(this);dismiss.setId(com.kitchenos.connect.R.id.pantry_dismiss);dismiss.setTag("home.pantryDismiss");dismiss.setEnabled(false);ImageView cross=new ImageView(this);cross.setImageDrawable(new InterfaceIcon("close",0xffffffff));dismiss.addView(cross,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));FrameLayout.LayoutParams dismissSize=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);dismissSize.setMargins(0,dp(12),dp(12),0);card.addView(dismiss,dismissSize);return card;
    }
    private View featuredCard(JSONObject recipe){
        FrameLayout hero=new FrameLayout(this);hero.setClipToOutline(true);hero.setBackground(KitViews.shape(this,Ui.CARD,24,0));
        hero.addView(image(recipe,340),new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xee101411}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=col();caption.setPadding(dp(16),dp(14),dp(16),dp(14));
        TextView title=text(recipe.optString("title"),22,0xffffffff,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(title);gap(caption,6);
        caption.addView(text(recipeDuration(recipe)+"   ·   "+portionLabel(recipe),14,0xfff2f3ee,false));
        hero.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));return hero;
    }
    private JSONObject recipeNutrition(JSONObject recipe){if(nutritionCache==null)nutritionCache=new RecipeNutrition(this);return nutritionCache.display(recipe,catalogue,FavoriteRecipeCards.read(this));}
    private String recipeFacts(JSONObject recipe){JSONArray ingredients=recipe.optJSONArray("ingredients");return ingredients==null?"":ingredients.length()+" Zutaten";}
    private void detailNutrition(){
        JSONObject recipe=current;String owner=accountUid;LinearLayout host=col();host.setTag("recipe.nutrition.host");body.addView(host);
        Runnable update=()->{JSONObject values=recipeNutrition(recipe);host.removeAllViews();if(values!=null)host.addView(NutritionSummary.detail(this,values,values.optBoolean("_estimated")?"Geschätzte Nährwerte je Portion":"Nährwerte je Portion laut Quelle"));};
        update.run();nutritionCache.load(recipe,values->{if(!destroyed&&current==recipe&&page.equals("detail")&&host.isAttachedToWindow()&&owner.equals(new CloudSession(this).account().optString("uid")))update.run();});
    }
    private String portionLabel(JSONObject recipe){JSONObject program=recipe.optJSONObject("machineProgram");double portions=program==null?recipe.optDouble("portion",recipe.optDouble("servings",Double.NaN)):program.optDouble("servings");return Double.isFinite(portions)&&portions>0?String.format(Locale.GERMAN,portions==Math.floor(portions)?"%.0f Portionen":"%.1f Portionen",portions):"Portionen offen";}
    private String recipeDuration(JSONObject recipe){String times=RecipeTiming.summary(recipe.optJSONObject("timing"));if(!times.isEmpty())return times;return recipe.optJSONObject("machineProgram")!=null?"Zeiten in der Anleitung":recipe.opt("minutes") instanceof Number?recipe.optInt("minutes")+" Min. laut Quelle":"Schritt für Schritt";}

    private void loadingHint(){String message=machineLoading||loading?"Deine Maschinenrezepte werden geladen …":!recipeError.isEmpty()?recipeError:"Hier erscheinen Gerichte mit einer vollständigen Maschinenanleitung.";loadingLabel=text(message,21,Ui.MUTED,false);body.addView(loadingLabel);if(!machineLoading&&!loading&&readyCatalogue.length()==0){if(new CloudSession(this).account().optString("uid").isEmpty())body.addView(button("Mit Kalomatik anmelden","recipes.login",()->new KitchenPages(this,legacyNavigation()).account(),true));else if(!new KitchenStore(this).snapshot().optJSONObject("preferences").optBoolean("aiConsent"))body.addView(button("Assistenten einrichten","recipes.setup",()->new KitchenPages(this,legacyNavigation()).preferences(),true));}if(!machineLoading&&!loading)body.addView(button("Rezepte laden","recipes.retry",()->{warmStarted=false;warmRetry=UUID.randomUUID().toString();loadMachineCatalogue();warmMachineRecipes();},false));}
    private List<JSONObject> rows(boolean favoriteOnly){return rows(favoriteOnly,allPrepared(),new KitchenStore(this).snapshot());}
    private List<JSONObject> rows(boolean favoriteOnly,JSONArray all,JSONObject kitchen){List<JSONObject> list=new ArrayList<>();JSONArray favorites=kitchen.optJSONArray("catalogFavorites");HashSet<String> saved=new HashSet<>();if(favorites!=null)for(int i=0;i<favorites.length();i++)saved.add(favorites.optString(i));JSONArray localFavorites=kitchen.optJSONArray("favorites");if(localFavorites!=null)for(int i=0;i<localFavorites.length();i++)saved.add(localFavorites.optString(i));for(Map.Entry<String,Boolean> pending:favoriteOverrides.entrySet())if(pending.getKey().startsWith(accountUid+":")){String identity=pending.getKey().substring(accountUid.length()+1);if(pending.getValue())saved.add(identity);else saved.remove(identity);}for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&(!favoriteOnly||saved.contains(key(r)))&&(favoriteOnly||!page.equals("recipes")||matchesRecipe(r,query)))list.add(r);}LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();int preferred=Math.min(20,kitchen.optJSONObject("preferences").optInt("servings",2));for(JSONObject row:list){JSONObject previous=unique.get(key(row));if(previous==null||Math.abs(row.optJSONObject("machineProgram").optInt("servings")-preferred)<Math.abs(previous.optJSONObject("machineProgram").optInt("servings")-preferred))unique.put(key(row),row);}if(favoriteOnly){JSONArray held=FavoriteRecipeCards.read(this);for(int n=0;n<held.length();n++){JSONObject card=held.optJSONObject(n);if(card!=null&&saved.contains(key(card)))unique.putIfAbsent(key(card),card);}for(int i=0;i<catalogue.length();i++){JSONObject candidate=catalogue.optJSONObject(i);if(candidate!=null&&saved.contains(key(candidate)))unique.putIfAbsent(key(candidate),candidate);}for(Recipe recipe:KitchenRecipes.all(this))if(saved.contains(recipe.id)){try{JSONObject candidate=importedCard(recipe);unique.putIfAbsent(key(candidate),candidate);}catch(Exception invalid){/* Incomplete imports remain in the incoming recipes list. */}}}return new ArrayList<>(unique.values());}
    private boolean matchesRecipe(JSONObject recipe,String search){String needle=search.toLowerCase(Locale.GERMAN).trim();if(needle.isEmpty()||recipe.optString("title").toLowerCase(Locale.GERMAN).contains(needle))return true;JSONArray ingredients=recipe.optJSONArray("ingredients");for(int i=0;ingredients!=null&&i<ingredients.length();i++)if(ingredients.optJSONObject(i).optString("name").toLowerCase(Locale.GERMAN).contains(needle))return true;return false;}
    private void recipeSearch(){activeRecipeCategory="";homeCollectionFilter="";recipePage=0;go("recipes");if(recipeSearchPanel!=null)recipeSearchPanel.focusEditor();}
    private void recipeQueryChanged(String value){
        String next=value.trim();if(query.equals(next))return;query=next;homeCollectionFilter="";recipePage=0;
        // Invalidate a slow response immediately, before the next debounced request starts.
        sourceEpoch++;sourceCursor="";sourceLoading=false;sourceError="";
        lastRecipeInputAt=SystemClock.uptimeMillis();scheduleRecipeResults();
        main.removeCallbacks(liveRecipeLookup);if(browseCatalogue)main.postDelayed(liveRecipeLookup,350);
    }
    // The input callback only records text and schedules work. Never detach the keyboard.
    private void scheduleRecipeResults(){
        recipeSearchGeneration.incrementAndGet();main.removeCallbacks(liveRecipeRender);
        main.postDelayed(liveRecipeRender,Math.max(0,160-(SystemClock.uptimeMillis()-lastRecipeInputAt)));
    }
    private boolean recipeScrollBusy(){return recipeTouchHeld||rootDrag!=null||SystemClock.uptimeMillis()-lastRecipeMotionAt<180;}
    private void searchRecipeResults(){
        if(destroyed||!page.equals("recipes")||recipeSearchWorker.isShutdown())return;
        if(recipeScrollBusy()){main.postDelayed(liveRecipeRender,180);return;}
        final long generation=recipeSearchGeneration.get();final String search=query,owner=accountUid;
        final boolean discover=browseCatalogue;final LinearLayout destination=body;
        final JSONArray local=privateCatalogue,shared=readyCatalogue,online=catalogue;
        recipeSearchWorker.execute(()->{
            if(generation!=recipeSearchGeneration.get())return;
            final int servings;try{servings=Math.min(20,new KitchenStore(this,owner).snapshot().optJSONObject("preferences").optInt("servings",2));}catch(IllegalStateException unavailable){return;}
            boolean changed=recipeSearchIndex==null||indexedPrivate!=local||indexedReady!=shared||indexedOnline!=online||indexedServings!=servings;
            if(changed){recipeSearchIndex=new RecipeSearchIndex(local,shared,online,servings);indexedPrivate=local;indexedReady=shared;indexedOnline=online;indexedServings=servings;}
            List<JSONObject> result=recipeSearchIndex.search(search,discover);
            List<Recipe> incoming=recipeSearchIndex.pending(KitchenRecipes.all(this));
            main.post(()->{
                if(destroyed||generation!=recipeSearchGeneration.get()||!page.equals("recipes")||body!=destination||!owner.equals(accountUid)||!owner.equals(new CloudSession(this).account().optString("uid")))return;
                // A data response must not detach a row under a held finger or abort its fling.
                if(recipeScrollBusy()){scheduleRecipeResults();return;}
                if(changed)searchCards.clear();
                cachedRecipeRows=result;cachedRecipeOwner=owner;cachedRecipeQuery=search;cachedRecipeBrowse=discover;cachedPrivate=local;cachedReady=shared;cachedOnline=online;searchIncoming=incoming;recipeSearchPanel.query(query);liveRecipeRows=result;paintingRecipeResults=true;
                int preservedY=paintedRecipeQuery.equals(query)?scroll.getScrollY():0;paintedRecipeQuery=query;
                try{body.removeAllViews();recipes(false);ArrayList<View> targets=new ArrayList<>();collectInteractive(body,targets);knob.replaceGroup("recipe.results",targets);tools.reset();captureTools(surface);try{tools.context(new JSONObject().put("page","recipes"));}catch(JSONException ignored){}ScrollView resultsScroll=scroll;resultsScroll.post(()->resultsScroll.scrollTo(0,preservedY));}
                finally{paintingRecipeResults=false;liveRecipeRows=null;}
            });
        });
    }
    private void recipeSearchFooter(boolean focused){
        if(recipeSearchPanel==null)recipeSearchPanel=new RecipeSearchPanel(this,this::recipeQueryChanged);
        if(recipeSearchPanel.getParent()!=null)((ViewGroup)recipeSearchPanel.getParent()).removeView(recipeSearchPanel);
        recipeSearchPanel.query(query);footer.setPadding(0,0,0,0);footer.addView(recipeSearchPanel,new LinearLayout.LayoutParams(-1,-2));if(focused)recipeSearchPanel.focusEditor();
    }

    private String key(JSONObject r){return r.optString("foodIdentity",r.optString("id"));}
    private ImageView image(JSONObject recipe,int height){ImageView photo=new ImageView(this);photo.setContentDescription(recipe.optString("title"));photo.setTag(com.kitchenos.connect.R.id.recipe_cover_size,height<=260?512:1440);photo.setTag(com.kitchenos.connect.R.id.recipe_cover,recipe.toString());photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(KitViews.shape(this,Ui.CARD,24,0));photo.setClipToOutline(false);reloadCover(photo,recipe);return photo;}
    private void reloadCover(ImageView photo,JSONObject recipe){if(photo.getDrawable() instanceof BitmapDrawable)return;if(RecipePhotos.apply(photo,recipe))return;if(RecipeArt.loadCached(this,photo,recipe))return;if(photo.getDrawable()==null)IngredientPortraits.showState(this,photo,recipe.optString("title"),IngredientPortraits.ImageState.LOADING,true);String url=recipe.optString("coverUrl");if(url.startsWith("/"))url=KalomatikCatalogue.ORIGIN+url;if(url.isEmpty()||url.endsWith("/assets/recipe-placeholder.svg"))RecipeArt.load(this,photo,recipe);else FoodImages.load(photo,url,photo.getTag(com.kitchenos.connect.R.id.recipe_cover_size) instanceof Integer?(Integer)photo.getTag(com.kitchenos.connect.R.id.recipe_cover_size):1440,()->RecipeArt.load(this,photo,recipe));}
    private void watchCoverNetwork(){try{coverNetwork=new android.net.ConnectivityManager.NetworkCallback(){@Override public void onCapabilitiesChanged(android.net.Network network,android.net.NetworkCapabilities caps){String id=network.toString();if(!caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)){if(id.equals(lastValidatedNetwork))lastValidatedNetwork="";return;}if(!id.equals(lastValidatedNetwork)){lastValidatedNetwork=id;FoodImages.networkAvailable();main.post(()->{if(!destroyed&&body!=null)retryCoverViews(body);});}}@Override public void onLost(android.net.Network network){if(network.toString().equals(lastValidatedNetwork))lastValidatedNetwork="";}};((android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(coverNetwork);}catch(Exception ignored){}}
    private void retryCoverViews(View view){if(view instanceof ImageView){Object recipe=view.getTag(com.kitchenos.connect.R.id.recipe_cover);if(recipe instanceof String)try{reloadCover((ImageView)view,new JSONObject((String)recipe));}catch(Exception ignored){}}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)retryCoverViews(((ViewGroup)view).getChildAt(i));}

    private View recipeMetric(String icon,String value,String description){
        LinearLayout cell=new LinearLayout(this);cell.setGravity(Gravity.CENTER_VERTICAL);cell.setContentDescription(description);cell.setTag("recipe.metric."+icon);
        ImageView glyph=new ImageView(this);glyph.setImageDrawable(new InterfaceIcon(icon,Color.WHITE));cell.addView(glyph,new LinearLayout.LayoutParams(dp(16),dp(16)));
        TextView label=text(value,14,Color.WHITE,false);label.setSingleLine(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(4);cell.addView(label,lp);return cell;
    }
    private FrameLayout recipePhotoCard(JSONObject recipe){
        FrameLayout card=new FrameLayout(this);card.setClipToOutline(true);
        ImageView photo=image(recipe,230);photo.setContentDescription(null);card.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x99000000,0xee000000}));card.addView(shade,new FrameLayout.LayoutParams(-1,dp(140),Gravity.BOTTOM));
        LinearLayout caption=col();caption.setPadding(dp(14),dp(10),dp(14),dp(14));
        TextView name=text(recipe.optString("title"),21,Color.WHITE,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setShadowLayer(dp(2),0,dp(1),0x99000000);caption.addView(name);gap(caption,4);
        String total=RecipeCardMetrics.totalTime(recipe),ingredients=RecipeCardMetrics.ingredients(recipe),portions=RecipeCardMetrics.portions(recipe);
        if(nutritionCache==null)nutritionCache=new RecipeNutrition(this);JSONObject nutrition=nutritionCache.get(recipe);if(nutrition==null||!nutrition.has("calories"))nutrition=nutritionCache.display(recipe);String calories=RecipeCardMetrics.calories(nutrition);
        LinearLayout metrics=new RecipeMetricRow(this);metrics.setTag("recipe.metrics");metrics.setGravity(Gravity.CENTER_VERTICAL);
        String[] icons={"clock","person","ingredients","calories"},values={RecipeCardMetrics.compactTime(recipe),RecipeCardMetrics.compactPortions(recipe),RecipeCardMetrics.compactIngredients(recipe),calories},descriptions={"Gesamtzeit in Minuten: "+total,portions,ingredients,"Kalorien pro Portion: "+calories.replace("kcal","Kilokalorien")};
        for(int i=0;i<icons.length;i++){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(25));if(i>0)size.leftMargin=dp(8);metrics.addView(recipeMetric(icons[i],values[i],descriptions[i]),size);}caption.addView(metrics);
        card.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        boolean favorite=isFavorite(recipe),needsPreparation=recipe.optJSONObject("machineProgram")==null;
        if(favorite){TextView heart=text("♥",22,Color.WHITE,true);heart.setGravity(Gravity.CENTER);heart.setBackground(KitViews.shape(this,0x99000000,18,0));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.TOP|Gravity.RIGHT);badge.setMargins(0,dp(10),dp(10),0);card.addView(heart,badge);}
        if(needsPreparation){TextView state=text("Vorbereiten",14,Color.WHITE,true);state.setPadding(dp(9),dp(5),dp(9),dp(5));state.setBackground(KitViews.shape(this,0xcc000000,12,0));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);badge.setMargins(dp(10),dp(10),dp(50),0);card.addView(state,badge);}
        target(card,"recipe."+recipe.optString("id"),recipe.optString("title")+", Gesamtzeit: "+total+", "+ingredients+", "+portions+", "+calories.replace("kcal","Kilokalorien pro Portion")+(favorite?", Favorit":"")+(needsPreparation?", Vorbereiten":""),()->{if(recipe.optJSONObject("machineProgram")!=null)openRecipe(recipe);else openSourceRecipe(recipe);});
        // Draw rotary focus above the photo instead of hiding it behind the image.
        StateListDrawable focus=new StateListDrawable();focus.addState(new int[]{android.R.attr.state_selected},KitViews.shape(this,Color.TRANSPARENT,18,Ui.ACCENT));focus.addState(new int[]{android.R.attr.state_focused},KitViews.shape(this,Color.TRANSPARENT,18,Ui.ACCENT));focus.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));card.setForeground(focus);
        return card;
    }
    private void tiles(List<JSONObject> list){
        for(int offset=0;offset<list.size();offset+=2){LinearLayout row=new LinearLayout(this);
            for(int j=offset;j<Math.min(offset+2,list.size());j++){JSONObject recipe=list.get(j);String cacheId=recipe.optString("id")+":"+recipe.optString("programDigest");View cached=page.equals("recipes")?searchCards.get(cacheId):null;
                if(cached!=null){if(cached.getParent()!=null)((ViewGroup)cached.getParent()).removeView(cached);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(276),1);if(j%2==1)cp.leftMargin=dp(18);row.addView(cached,cp);continue;}
                FrameLayout card=recipePhotoCard(recipe);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(276),1);if(j%2==1)cp.leftMargin=dp(18);row.addView(card,cp);if(page.equals("recipes")){searchCards.put(cacheId,card);while(searchCards.size()>36)searchCards.remove(searchCards.keySet().iterator().next());}}
            if(offset+1==list.size())row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));body.addView(row);gap(body,20);
        }
    }

    private void recipes(boolean favoriteOnly){
        if(favoriteOnly)heading("Deine Favoriten");
        else if(!homeCollectionFilter.isEmpty())heading(homeCollectionFilter);
        else if(!activeRecipeCategory.isEmpty())for(JSONObject category:recipeCategories.all())if(activeRecipeCategory.equals(category.optString("id"))){heading(category.optString("title"));break;}
        if(!activeRecipeCategory.isEmpty()||!homeCollectionFilter.isEmpty())body.addView(textLink("Alle Kategorien","recipes.browse",()->{activeRecipeCategory="";homeCollectionFilter="";recipePage=0;if(browseCatalogue)loadSourceRecipes("",false);else render();}));
        List<JSONObject> list=liveRecipeRows!=null?liveRecipeRows:favoriteOnly||!browseCatalogue?rows(favoriteOnly):catalogueRows();if(!favoriteOnly&&!homeCollectionFilter.isEmpty())list=filteredCollection(homeCollectionFilter,list,rows(true));if(!favoriteOnly&&query.isEmpty()&&homeCollectionFilter.isEmpty()&&activeRecipeCategory.isEmpty()){recipeShelves(list);}else if(!list.isEmpty()){if(!activeRecipeCategory.isEmpty())list=categoryRows(activeRecipeCategory,list);recipePage=Math.max(0,Math.min(recipePage,Math.max(0,(list.size()-1)/12)));int first=recipePage*12;tiles(list.subList(first,Math.min(first+12,list.size())));if(list.size()>12){LinearLayout pages=new LinearLayout(this);Button previous=button("Vorherige","recipes.previous",()->{recipePage--;render();},false),next=button("Weitere","recipes.next",()->{recipePage++;render();},false);previous.setEnabled(recipePage>0);next.setEnabled(first+12<list.size());pages.addView(previous,new LinearLayout.LayoutParams(0,dp(82),1));pages.addView(next,new LinearLayout.LayoutParams(0,dp(82),1));body.addView(pages);}}
        else if(favoriteOnly){body.addView(text("Merke dir Gerichte mit dem Herz am Rezept. Hier findest du sie beim nächsten Kochen sofort wieder.",25,Ui.MUTED,false));gap(body,20);body.addView(button("Rezepte entdecken","favorites.discover",()->{query="";browseCatalogue=false;go("recipes");},true));}else if(sourceLoading){body.addView(text("Passende Rezepte werden geladen …",22,Ui.MUTED,false));}else if(!query.isEmpty()){body.addView(text("Hier ist noch kein passendes Gericht dabei.",23,Ui.INK,true));body.addView(button(browseCatalogue?"Suche zurücksetzen":"In Kalomatik suchen","recipes.expand",()->{if(browseCatalogue)query="";browseCatalogue=true;recipePage=0;loadSourceRecipes(query,false);},true));}else if(!homeCollectionFilter.isEmpty())body.addView(text(homeCollectionFilter.equals("Zuletzt gekocht")?"Deine zuletzt gekochten Gerichte erscheinen hier.":"Hier sind noch keine passenden Gerichte gespeichert.",23,Ui.MUTED,false));else loadingHint();
        if(!favoriteOnly){if(browseCatalogue&&!sourceError.isEmpty()){body.addView(text(sourceError,21,Ui.MUTED,false));body.addView(button("Erneut versuchen","recipes.retry",()->loadSourceRecipes(sourceQuery,false),false));gap(body,14);}if(browseCatalogue&&!sourceCursor.isEmpty()){Button more=button(sourceLoading?"Weitere Gerichte werden geladen …":"Weitere Kalomatik-Rezepte","recipes.sourceMore",()->loadSourceRecipes(sourceQuery,true),false);more.setEnabled(!sourceLoading);body.addView(more);gap(body,14);}if(browseCatalogue)body.addView(text("Vor dem Kochen wird die Maschinenanleitung geprüft. Backofen oder andere Geräte werden in der Anleitung klar benannt.",18,Ui.MUTED,false));List<Recipe> incoming=paintingRecipeResults?searchIncoming:pendingImports();if(!paintingRecipeResults)searchIncoming=incoming;if(!incoming.isEmpty()){section("Vom Handy",null,null);for(Recipe recipe:incoming){body.addView(button(recipe.title,"import."+recipe.id,()->openImported(recipe),false));body.addView(text(recipe.servings==0?"Portionenzahl ergänzen":ImportPreparation.status(this,recipe.id).isEmpty()?"Rezept ansehen":ImportPreparation.status(this,recipe.id),18,Ui.MUTED,false));}}gap(body,18);body.addView(textLink("Deinen Kalomatik-Kochplan öffnen","recipes.plan",()->go("plan")));}
    }
    private List<JSONObject> catalogueRows(){LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();for(JSONObject row:rows(false))result.put(key(row),row);for(int i=0;i<catalogue.length();i++){JSONObject item=catalogue.optJSONObject(i);if(item!=null&&matchesRecipe(item,query))result.putIfAbsent(key(item),item);}return new ArrayList<>(result.values());}
    private void openSourceRecipe(JSONObject recipe){String id=recipe.optString("id"),owner=accountUid;JSONObject saved=recipeOffline.read("detail:"+id);openRecipe(saved==null?recipe:saved);worker.execute(()->{try{JSONObject source=recipeOffline.detail(id);main.post(()->{if(destroyed||!owner.equals(new CloudSession(this).account().optString("uid"))||current==null||!id.equals(current.optString("id"))||program()!=null)return;current=source;if(page.equals("detail"))render();});}catch(Exception ignored){}});}

    private void openRecipe(JSONObject recipe){if(confirmNavigation(()->openRecipe(recipe)))return;cancelPreparation();stopRestDisplay();releaseCookingPanel();checkpointCook();cookSessionId="";stepControlStates=new Bundle();weighingRecords=new JSONArray();requestedServings=0;preparationError="";detailTab="overview";ingredientChecks.clear();weighingStarted.clear();lastPage=page.equals("favorites")?"favorites":"recipes";current=recipe;currentStep=0;completedSteps.clear();cookingModified=false;coldInterrupted=false;stepEdits.clear();directionEdits.clear();go("detail");}
    private JSONObject program(){return current==null?null:current.optJSONObject("machineProgram");}
    private void detail(){
        if(current==null)return;JSONObject program=program();
        if(preparingRecipe){LinearLayout status=new LinearLayout(this);status.setGravity(Gravity.CENTER_VERTICAL);status.setTag("recipe.preparing");ProgressBar progress=new ProgressBar(this);status.addView(progress,new LinearLayout.LayoutParams(dp(36),dp(36)));TextView label=text("Die Maschinenanleitung wird vorbereitet. Das kann ein bis zwei Minuten dauern.",21,Ui.INK,true);LinearLayout.LayoutParams copy=new LinearLayout.LayoutParams(0,-2,1);copy.leftMargin=dp(16);status.addView(label,copy);body.addView(status);gap(body,20);}
        if(!preparationError.isEmpty()){TextView failure=text("Die Maschinenanleitung konnte nicht vorbereitet werden.\n"+preparationError,23,Ui.INK,true);failure.setTag("recipe.preparationError");body.addView(failure);gap(body,20);if(preparationError.contains("WLAN")||preparationError.contains("Internet")){body.addView(button("WLAN prüfen","recipe.preparationWifi",()->go("wifi"),false));gap(body,20);}}
        if(current.optBoolean("planIncomplete")){body.addView(text("Rezeptangaben ergänzen",28,Ui.INK,true));body.addView(text(current.optString("planMissing"),24,Ui.MUTED,false));}
        FrameLayout cover=new FrameLayout(this);cover.setClipToOutline(true);cover.setBackground(KitViews.shape(this,Ui.CARD,24,0));
        ImageView photo=image(current,300);photo.setOnClickListener(v->ImageDetail.show(this,current.optString("title"),large->reloadCover(large,current)));cover.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        View favorite=iconButton(isFavorite(current)?"favorite-filled":"favorite",isFavorite(current)?"Favorit entfernen":"Als Favorit merken","recipe.favorite",this::toggleFavorite);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.TOP|Gravity.RIGHT);fp.setMargins(0,dp(14),dp(14),0);cover.addView(favorite,fp);body.addView(cover,new LinearLayout.LayoutParams(-1,dp(300)));gap(body,22);heading(current.optString("title"));
        LinearLayout metadata=new LinearLayout(this);metadata.setGravity(Gravity.CENTER_VERTICAL);metadata.addView(text(recipeDuration(current),20,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        int basePortions=program==null?current.optInt("servings",1):program.optInt("servings");int portions=requestedServings>0?requestedServings:basePortions;LinearLayout selector=new LinearLayout(this);selector.setGravity(Gravity.CENTER_VERTICAL);selector.setTag("recipe.portions");
        Button minus=button("−","recipe.portions.minus",()->adjustPortions(-1),false);minus.setEnabled(!preparingRecipe&&portions>1);selector.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(64)));
        TextView amount=text(portions>0?String.valueOf(portions):"—",27,Ui.INK,true);amount.setGravity(Gravity.CENTER);selector.addView(amount,new LinearLayout.LayoutParams(dp(50),dp(64)));
        Button plus=button("+","recipe.portions.plus",()->adjustPortions(1),false);plus.setEnabled(!preparingRecipe&&portions<20);selector.addView(plus,new LinearLayout.LayoutParams(dp(54),dp(64)));metadata.addView(selector);body.addView(metadata);gap(body,8);body.addView(textLink("Im Kochplan einplanen","recipe.plan",this::planCurrent));gap(body,12);
        body.addView(text(program==null?"Portionen":RecipePresentation.count(program.optJSONArray("steps"))+" geführte Schritte",19,Ui.MUTED,false));gap(body,24);
        if(activeRecipeMatches())body.addView(button("Rezept neu beginnen","cook.restart",this::restartCurrentRecipe,false));
        detailNutrition();if(program==null){body.addView(text("Calomatik passt dieses Rezept vor dem Kochen per KI an die Maschine und deine Portionszahl an.",21,Ui.MUTED,false));gap(body,18);}else if(!RecipeTiming.summary(current.optJSONObject("timing")).isEmpty()){body.addView(text("Geschätzte Teilzeiten laut Rezept; einzelne Abläufe können parallel stattfinden.",18,Ui.MUTED,false));gap(body,12);}section("Zutaten",null,null);JSONArray ingredients=current.optJSONArray("ingredients");for(int i=0;ingredients!=null&&i<ingredients.length();i++)ingredientRow(portionPreview(ingredients.optJSONObject(i),portions,basePortions),false,"overview");
        if(program!=null){gap(body,20);section("Gerät und Zubehör",null,null);JSONArray required=program.optJSONArray("tools");toolCards(required==null?new JSONArray():required);}

    }
    private JSONObject portionPreview(JSONObject item,int portions,int base){if(item==null||portions==base||base<1||!(item.opt("amount") instanceof Number))return item;try{return new JSONObject(item.toString()).put("amount",new java.math.BigDecimal(item.opt("amount").toString()).multiply(java.math.BigDecimal.valueOf(portions)).divide(java.math.BigDecimal.valueOf(base),6,java.math.RoundingMode.HALF_UP).doubleValue());}catch(Exception invalid){return item;}}
    private void adjustPortions(int delta){if(preparingRecipe||current==null)return;int base=program()==null?current.optInt("servings",1):program().optInt("servings");if(current.optBoolean("planIncomplete")){PlanCompletionDialog.show(this,current,()->{go("plan");loadPlan();});return;}requestedServings=Math.max(1,Math.min(20,(requestedServings>0?requestedServings:base)+delta));render();}
    private String externalTool(JSONObject step){String instruction=(step.optString("title")+" "+step.optString("instruction")).toLowerCase(Locale.GERMAN);if(instruction.matches("(?s).*(air[ -]?fryer|heißluftfritteuse).*"))return "air-fryer";if(instruction.matches("(?s).*\\b(backofen|ofen|oven)\\b.*"))return "oven";if(instruction.matches("(?s).*\\b(pfanne|bratpfanne|kochfeld|herd)\\b.*"))return "pan";return "";}
    private String stepKind(JSONObject step){String kind=step.optString("kind");return kind.equals("machine")?"Küchenmaschine":kind.equals("weigh")?"Einwiegen":!externalTool(step).isEmpty()?MachineProgram.toolName(externalTool(step))+" · von Hand":kind.equals("rest")?"Zeit zum Ruhen":"Von Hand";}
    private void toolCards(JSONArray required){toolCards(required,true);}
    private void toolCards(JSONArray required,boolean recipe){
        ArrayList<String> items=new ArrayList<>();if(recipe)items.add("device");for(int i=0;i<required.length();i++){String id=required.optString(i);if((!recipe||!id.equals("mixing-blade"))&&!id.equals("scale")&&!items.contains(id))items.add(id);}
        int columns=recipe?3:Math.min(2,Math.max(1,items.size()));for(int offset=0;offset<items.size();offset+=columns){LinearLayout row=new LinearLayout(this);for(int i=offset;i<Math.min(offset+columns,items.size());i++){String id=items.get(i);OemApplianceImages.Part part=OemApplianceImages.forTool(id);LinearLayout cell=col();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(10),dp(14),dp(10),dp(14));cell.setBackground(KitViews.shape(this,Ui.CARD,20,0));ImageView visual;
            if(part!=null)visual=OemApplianceImages.view(this,part);else{visual=new ImageView(this);if(!ToolPhotos.apply(visual,id))visual.setImageDrawable(new InterfaceIcon(id,Ui.INK));visual.setPadding(dp(12),dp(4),dp(12),dp(4));visual.setScaleType(ImageView.ScaleType.FIT_CENTER);}
            cell.addView(visual,new LinearLayout.LayoutParams(-1,dp(190)));TextView label=text(part==null?MachineProgram.toolName(id):part==OemApplianceImages.Part.STIRRER?"Rühraufsatz · Schneebesen":part.label,22,Ui.INK,true);label.setGravity(Gravity.CENTER);cell.addView(label);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(4),0,dp(4),dp(12));row.addView(cell,cp);
        }int remaining=columns-Math.min(columns,items.size()-offset);for(int k=0;k<remaining;k++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));body.addView(row);}
    }

    private ApplianceVessel.Part vesselPart(String id){switch(id){case "mixing-blade":return ApplianceVessel.Part.KNIFE;case "spatula":return ApplianceVessel.Part.SPATULA;case "steamer":return ApplianceVessel.Part.STEAMER;case "steam-basket":return ApplianceVessel.Part.BASKET;case "stirring-attachment":return ApplianceVessel.Part.STIRRER;default:return ApplianceVessel.Part.BOWL;}}
    private void ingredientRow(JSONObject item,boolean checkable,String group){
        if(item==null)return;String name=item.optString("name");String id=group+":"+name;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);IngredientPortraits.bind(this,portrait,item);row.addView(portrait,new LinearLayout.LayoutParams(dp(100),dp(100)));portrait.setOnClickListener(v->ImageDetail.show(this,name,large->IngredientPortraits.bind(this,large,item)));LinearLayout copy=col();TextView title=text(name,22,Ui.INK,true);copy.addView(title);if(!item.optString("preparation").isEmpty())copy.addView(text(item.optString("preparation"),17,Ui.MUTED,false));JSONObject measured=RecipeWeighingHistory.find(weighingRecords,id);if(measured!=null)copy.addView(text("Zuletzt eingewogen: "+measured.optInt("grams")+" g · gespeichert",17,Ui.ACCENT,false));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(16);row.addView(copy,cp);TextView amount=text(item.optBoolean("literal")?"":amount(item),20,Ui.INK,false);amount.setGravity(Gravity.RIGHT);row.addView(amount,new LinearLayout.LayoutParams(-2,-2));
        if(checkable){ImageView check=new ImageView(this);check.setImageDrawable(new InterfaceIcon(ingredientChecks.contains(id)?"check":"circle",ingredientChecks.contains(id)?Ui.ACCENT:Ui.LINE));LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(dp(23),dp(23));kp.leftMargin=dp(18);row.addView(check,kp);Runnable toggle=()->{if(!ingredientChecks.add(id))ingredientChecks.remove(id);boolean selected=ingredientChecks.contains(id);check.setImageDrawable(new InterfaceIcon(selected?"check":"circle",selected?Ui.ACCENT:Ui.MUTED));title.setAlpha(selected?.55f:1);};target(row,"ingredient."+id,name+" bereitstellen",toggle);}
        body.addView(row);gap(body,6);
    }

    private void changePortions(int currentPortions){ParameterDial dial=new ParameterDial(this,"Portionen",1,20,1,Math.max(1,currentPortions));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Für wie viele kochen wir?").setView(dial).setNegativeButton("Abbrechen",null).setPositiveButton("Anleitung anpassen",(d,w)->prepareRecipe(current,true,dial.value())).create();dialog.show();dial.setMinimumHeight(dp(240));RotaryDialogs.bind(this,dialog);}
    private String amount(JSONObject item){Object v=item.opt("amount");return v instanceof Number?new java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()+" "+item.optString("unit"):"Menge offen";}
    private boolean isFavorite(JSONObject recipe){String override=accountUid+":"+key(recipe);if(favoriteOverrides.containsKey(override))return favoriteOverrides.get(override);JSONArray favorites=new KitchenStore(this).snapshot().optJSONArray(key(recipe).matches("[a-f0-9]{64}")?"catalogFavorites":"favorites");if(favorites!=null)for(int i=0;i<favorites.length();i++)if(key(recipe).equals(favorites.optString(i)))return true;return false;}
    private void toggleFavorite(){
        if(current==null)return;final JSONObject recipe=current;final String identity=key(recipe),uid=accountUid,override=uid+":"+identity;final boolean enabled=!isFavorite(recipe);final long version=++favoriteVersion;favoriteVersions.put(override,version);favoriteOverrides.put(override,enabled);paintFavorite(enabled);
        final KitchenStore store;try{store=new KitchenStore(this,uid);}catch(Exception failure){favoriteOverrides.remove(override);favoriteVersions.remove(override);paintFavorite(isFavorite(recipe));error(failure);return;}favoriteWrites.execute(()->{try{if(identity.matches("[a-f0-9]{64}")){store.catalogFavorite(identity,enabled);FavoriteRecipeCards.keep(this,uid,identity,recipe,enabled);}else store.favorite(identity,enabled);main.post(()->{if(Long.valueOf(version).equals(favoriteVersions.get(override))){favoriteVersions.remove(override);favoriteOverrides.remove(override);}});}catch(Exception error){main.post(()->{if(destroyed||!uid.equals(accountUid))return;if(Long.valueOf(version).equals(favoriteVersions.get(override))){favoriteOverrides.remove(override);if(current!=null&&identity.equals(key(current)))paintFavorite(isFavorite(current));}error(error);});}});
    }
    private void paintFavorite(boolean enabled){View view=surface.findViewWithTag("recipe.favorite");if(view==null)return;String label=enabled?"Favorit entfernen":"Als Favorit merken";view.setContentDescription(label);if(view instanceof Button)((Button)view).setText(enabled?"Aus Favoriten entfernen":"Zu Favoriten hinzufügen");else if(view instanceof ViewGroup&&((ViewGroup)view).getChildAt(0) instanceof ImageView)((ImageView)((ViewGroup)view).getChildAt(0)).setImageDrawable(new InterfaceIcon(enabled?"favorite-filled":"favorite"));}
    private void planCurrent(){if(current==null)return;int count=requestedServings>0?requestedServings:program()==null?current.optInt("portion",current.optInt("servings",1)):program().optInt("servings",1);SharedPlanDialog.show(this,current,count,()->{planRefreshed=0;go("plan");});}

    private KitchenPages.Navigation legacyNavigation(){return new KitchenPages.Navigation(){public void home(String tab){go(tab.equals("today")?(page.equals("settings")?"settings":"home"):tab.equals("stock")?"pantry":tab.equals("assistant")?"settings":tab);}public void recipe(Recipe r){openImported(r);}public void plannedRecipe(Recipe r,int portions){openImported(r,portions);}public void wifi(){legacy("wifi");}public void receiver(){legacy("receive");}public void vendor(){legacy("kitchen");}};}
    private void step(){
        if(cookSessionId.isEmpty()&&program()!=null){if(activeRecipeMatches())restoreActiveWorkingState();else if(activeCookState==null)beginCookState(currentStep);else{page="detail";detail();return;}}
        JSONObject program=program();if(program==null){page="detail";detail();return;}JSONArray steps=program.optJSONArray("steps");if(steps==null||steps.length()==0)return;currentStep=RecipePresentation.next(steps,Math.max(0,Math.min(currentStep,steps.length()-1)));JSONObject step=steps.optJSONObject(currentStep);String kind=step.optString("kind"),stepId=step.optString("id");boolean timedRest=kind.equals("rest")&&step.optJSONObject("settings")!=null&&step.optJSONObject("settings").optInt("durationSeconds")>0;
        body.addView(text(current.optString("title"),28,Ui.INK,true));gap(body,12);LinearLayout progress=new LinearLayout(this);for(int i=0;i<steps.length();i++){if(RecipePresentation.omitted(steps.optJSONObject(i)))continue;View segment=new View(this);segment.setBackground(KitViews.shape(this,i<=currentStep?Ui.ACCENT:Ui.LINE,3,0));LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(4),1);cell.rightMargin=dp(4);progress.addView(segment,cell);}body.addView(progress);gap(body,14);body.addView(text("Schritt "+RecipePresentation.ordinal(steps,currentStep)+" von "+RecipePresentation.count(steps),17,Ui.MUTED,false));gap(body,16);
        heading(step.optString("title"));TextView instruction=text(step.optString("instruction"),28,Ui.INK,false);body.addView(instruction);if(!kind.equals("machine")&&!kind.equals("weigh"))stepFacts(step);
        if(!kind.equals("weigh")&&!kind.equals("machine")&&!timedRest){gap(body,18);FrameLayout visual=new FrameLayout(this);visual.setClipToOutline(true);visual.setBackground(KitViews.shape(this,Ui.CARD,24,0));ImageView photo=image(current,240);visual.addView(photo,new FrameLayout.LayoutParams(-1,-1));ImageView art=new ImageView(this);StepImages.load(this,art,current,currentStep);visual.addView(art,new FrameLayout.LayoutParams(-1,-1));body.addView(visual,new LinearLayout.LayoutParams(-1,dp(240)));}
        JSONArray ingredients=step.optJSONArray("ingredients");if(!kind.equals("weigh")&&ingredients!=null&&ingredients.length()>0){gap(body,18);for(int i=0;i<ingredients.length();i++)ingredientRow(ingredients.optJSONObject(i),false,stepId);}
        JSONObject settings=step.optJSONObject("settings");String editKey=current.optString("programDigest")+":"+stepId;int[] edited=stepEdits.get(editKey);if(edited==null)edited=new int[]{settings.optInt("durationSeconds",0),settings.optInt("temperatureC",0),(int)Math.round(settings.optDouble("speedLevel",0)*10)};final int[] values=edited;
        if(kind.equals("machine")){
            gap(body,18);dialRow(values[0],values[1],values[2],v->{values[0]=duration.value();values[1]=temperature.value();values[2]=stirring.value();stepEdits.put(editKey,values);});reverse=directionEdits.containsKey(stepId)?directionEdits.get(stepId):settings.optString("direction").equals("counterclockwise");renderDirection();
            try{ControlValues original=new ControlValues(settings.getInt("durationSeconds"),settings.optInt("temperatureC",0),(int)Math.round(settings.getDouble("speedLevel")*10)*(settings.optString("direction").equals("counterclockwise")?-1:1));cookingPanel=new GuidedControlPanel(this,body,duration,temperature,stirring,()->reverse,original,tools,()->{cookingModified|=cookingPanel.modifiedRecipe();completedSteps.add(stepId);advanceStep();});cookingPanel.speedLimit(usesStirrer()?40:page.equals("home")&&homeFunction.equals("turbo")?120:100);cookingPanel.bindSession(cookSessionId,current.getString("programDigest"),stepId);Bundle savedControl=stepControlStates.getBundle(stepId);if(completedSteps.contains(stepId))cookingPanel.reviewCompleted();else if(savedControl!=null)cookingPanel.restore(savedControl);}catch(Exception e){body.addView(text("Für diesen Schritt fehlt noch ein passender Geräteablauf.",21,Ui.MUTED,false));}
        }else{
            duration=null;if(kind.equals("weigh")){gap(body,14);inlineScale=new InlineScalePanel(this,ControlClient.get(this),knob,accountUid,current.optString("programDigest"),step,weighingRecords,ingredientChecks,weighingStarted.contains(step.optString("id")),envelope->{inlineMeasurementDelivery=true;try{acceptWeighingResult(RESULT_OK,new Intent().putExtra("weighingResult",envelope.toString()));}finally{inlineMeasurementDelivery=false;}});inlineScale.setTag("step.scale");body.addView(inlineScale,new LinearLayout.LayoutParams(-1,-2));}
            if(!kind.equals("weigh")&&values[0]>0){gap(body,16);duration=new ParameterDial(this,"Dauer",1,7200,1,values[0]);knob.add(duration);duration.setTag("parameter.duration");tools.action("parameter.duration",duration,()->knob.toggleEdit(duration));duration.touchPicker(true);duration.setOnClickListener(v->openParameterEditor(duration,true));duration.onChanged(v->{values[0]=v;stepEdits.put(editKey,values);});LinearLayout timerBlock=col();timerBlock.setTag("timer.controls");timerBlock.addView(duration,new LinearLayout.LayoutParams(-1,dp(380)));body.addView(timerBlock);restLabel=null;restTimerKey=currentTimerKey();restDeadline=restTimers.getOrDefault(restTimerKey,0L);
                LinearLayout timerActions=new LinearLayout(this);timerActions.setGravity(Gravity.CENTER);timerActions.setTag("timer.actions");timerActions.addView(button(restDeadline>0?"Pause":"Timer starten","step.timer",()->{if(restTimers.containsKey(currentTimerKey()))pauseRestTimer();else startRestTimer();},true),new LinearLayout.LayoutParams(dp(260),dp(74)));Button reset=button("Zurücksetzen","step.timer.reset",()->{pauseRestTimer();duration.countdown(-1);duration.value(settings.optInt("durationSeconds",values[0]));checkpointCook();},false);LinearLayout.LayoutParams rs=new LinearLayout.LayoutParams(dp(230),dp(74));rs.leftMargin=dp(12);timerActions.addView(reset,rs);timerBlock.addView(timerActions);knob.confirmAction(duration,()->{if(restTimers.containsKey(currentTimerKey()))startRestTimer();else knob.select(timerActions.getChildAt(0));});if(restDeadline>0)tickRest();}

        }
        nextStepPreview(steps);
    }
    private void nextStepPreview(JSONArray steps){int next=RecipePresentation.next(steps,currentStep+1);if(next<steps.length()){gap(body,20);body.addView(text("Als Nächstes",18,Ui.MUTED,true));body.addView(text(steps.optJSONObject(next).optString("title"),23,Ui.INK,true));}}
    private void startRestTimer(){restTimerKey=currentTimerKey();restDeadline=SystemClock.elapsedRealtime()+duration.value()*1000L;restTimers.put(restTimerKey,restDeadline);checkpointCook();main.removeCallbacks(timerAlarms);main.post(timerAlarms);timerActionLabel("Pause");tickRest();View next=footer==null?null:footer.findViewWithTag("step.next");if(next!=null&&next.isEnabled())knob.select(next);}
    private void pauseRestTimer(){String key=currentTimerKey();long deadline=restTimers.getOrDefault(key,0L);if(deadline>0){int left=(int)Math.max(1,(deadline-SystemClock.elapsedRealtime()+999)/1000);restTimers.remove(key);duration.countdown(-1);duration.value(left);}restDeadline=0;main.removeCallbacks(restTick);timerActionLabel("Timer starten");checkpointCook();}
    private void timerActionLabel(String label){View v=body==null?null:body.findViewWithTag("step.timer");if(v instanceof Button){((Button)v).setText(label);v.setContentDescription(label);}}
    private float recipeWaterFraction(JSONObject step){double ml=0;JSONArray uses=step.optJSONArray("ingredients");for(int i=0;uses!=null&&i<uses.length();i++){JSONObject item=uses.optJSONObject(i);if(!item.optString("name").toLowerCase(Locale.GERMAN).matches(".*(wasser|water).*"))continue;String unit=item.optString("unit").toLowerCase(Locale.ROOT);if(unit.equals("ml"))ml+=item.optDouble("amount",0);else if(unit.equals("l"))ml+=item.optDouble("amount",0)*1000;}return (float)Math.max(0,Math.min(1,ml/2200d));}
    private void previousStep(){if(confirmNavigation(this::previousStep))return;stopRestDisplay();int previous=RecipePresentation.previous(program().optJSONArray("steps"),currentStep-1);if(previous>=0){currentStep=previous;go("step");}else go("detail");}
    private void advanceStep(){stopRestDisplay();JSONArray steps=program().optJSONArray("steps");int next=RecipePresentation.next(steps,currentStep+1);if(next<steps.length()){currentStep=next;go("step");}else if(allMachineStepsCompleted()){activeCookState=null;go("finished");}else showDialog(new AlertDialog.Builder(this).setTitle("Ein Maschinenschritt ist noch offen").setMessage("Öffne den Schritt in der Übersicht und führe ihn aus.").setPositiveButton("Zur Übersicht",(d,w)->{detailTab="steps";go("detail");}));}
    private boolean weighingComplete(JSONObject step){JSONArray items=step.optJSONArray("ingredients");if(items==null||items.length()==0)return false;for(int i=0;i<items.length();i++)if(!ingredientChecks.contains(step.optString("id")+":"+items.optJSONObject(i).optString("name")))return false;return true;}
    private void refreshWeighingNavigation(JSONObject step){
        if(footer==null)return;View view=footer.findViewWithTag("step.next");if(!(view instanceof Button))return;
        boolean complete=weighingComplete(step);Button next=(Button)view;String label=currentStep==program().optJSONArray("steps").length()-1?"Gericht fertig":"Weiter";
        next.setText(label);next.setContentDescription(label);next.setBackground(ControlFocus.background(this,complete?Ui.ACCENT:Ui.CARD));next.setTextColor(complete?0xff171716:Ui.INK);tools.invalidate();
    }
    private void stepNavigation(LinearLayout dock){
        if(program()==null)return;JSONArray steps=program().optJSONArray("steps");JSONObject step=steps.optJSONObject(currentStep);String id=step.optString("id"),kind=step.optString("kind");LinearLayout actions=new LinearLayout(this);Button previous=button("Zurück","step.previous",this::previousStep,false);actions.addView(previous,new LinearLayout.LayoutParams(dp(170),dp(82)));Button next=button(currentStep==steps.length()-1?"Gericht fertig":"Weiter","step.next",()->{if(kind.equals("machine")&&!completedSteps.contains(id))return;if(kind.equals("weigh")&&inlineScale!=null&&!weighingComplete(step)&&!inlineScale.next())return;JSONObject setting=step.optJSONObject("settings");if(!kind.equals("machine")&&duration!=null&&duration.value()!=setting.optInt("durationSeconds",0))cookingModified=true;completedSteps.add(id);advanceStep();},!kind.equals("weigh")||weighingComplete(step));next.setEnabled(!kind.equals("machine")||completedSteps.contains(id));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(82),1);np.leftMargin=dp(12);actions.addView(next,np);dock.addView(actions);
    }

    private final HashMap<String,Boolean> directionEdits=new HashMap<>();
    private void settingsRemoveDirectionOverride(String id){directionEdits.put(id,reverse);}
    private void startGuided(JSONObject step){try{int signed=stirring.value()*(directionEdits.containsKey(step.optString("id"))?(directionEdits.get(step.optString("id"))?-1:1):(step.optJSONObject("settings").optString("direction").equals("counterclockwise")?-1:1));new ControlValues(duration.value(),temperature.value(),signed);startActivityForResult(new Intent(this,ControlActivity.class).putExtra("preparedSeconds",duration.value()).putExtra("preparedTemperature",temperature.value()).putExtra("preparedSpeed",signed).putExtra("stirringAttachment",usesStirrer()).putExtra("guided",true).putExtra("programDigest",current.optString("programDigest")).putExtra("originalSeconds",step.getJSONObject("settings").getInt("durationSeconds")).putExtra("originalTemperature",step.getJSONObject("settings").optInt("temperatureC",0)).putExtra("originalSpeed",(int)Math.round(step.getJSONObject("settings").getDouble("speedLevel")*10)*(step.getJSONObject("settings").optString("direction").equals("counterclockwise")?-1:1)).putExtra("cookingContext",tools.scene().getJSONObject("context").toString()).putExtra("privateRecipe",current.optBoolean("private")).putExtra("ownerUid",accountUid).putExtra("stepId",step.optString("id")).putExtra("stepTitle",step.optString("title")).putExtra("instruction",step.optString("instruction")),42);}catch(Exception e){showDialog(new AlertDialog.Builder(this).setTitle("Dieser Schritt braucht noch ein Geräteprofil").setMessage("Die Anleitung ist vollständig vorbereitet. Diese Einstellungen sind an deiner Maschine noch nicht für die eigene Steuerung geprüft. Der Schritt wird nicht gestartet und nicht als erledigt markiert.").setNegativeButton("Zur Anleitung",null));}}
    private String currentTimerKey(){try{return current.getString("programDigest")+":"+program().getJSONArray("steps").getJSONObject(currentStep).getString("id");}catch(Exception e){return "";}}
    private void stopRestDisplay(){restDeadline=0;restTimerKey="";main.removeCallbacks(restTick);}
    private void clearRestTimers(){stopRestDisplay();restTimers.clear();}
    private void tickRest(){main.removeCallbacks(restTick);if(restDeadline<=0||!page.equals("step")||destroyed)return;if(!CookingNavigation.sameTimer(restTimerKey,currentTimerKey())){stopRestDisplay();return;}restDeadline=restTimers.getOrDefault(restTimerKey,0L);if(restDeadline<=0)return;long remaining=Math.max(0,(restDeadline-SystemClock.elapsedRealtime()+999)/1000);if(restLabel!=null)restLabel.setText(remaining==0?"Timer beendet":String.format(Locale.GERMAN,"Noch %02d:%02d",remaining/60,remaining%60));if(duration!=null){if(!duration.isEditing())duration.countdown((int)remaining);duration.setEnabled(true);}if(remaining>0)main.postDelayed(restTick,250);else{main.removeCallbacks(timerAlarms);main.post(timerAlarms);}}
    private boolean allMachineStepsCompleted(){JSONArray steps=program().optJSONArray("steps");for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i);if(step.optString("kind").equals("machine")&&!completedSteps.contains(step.optString("id")))return false;}return true;}
    private void finished(){activeCookState=null;if(current!=null)new RecentCooking(this,accountUid).record(key(current));try{new CookJourneyStore(this).clear();}catch(Exception ignored){}body.addView(image(current,330),new LinearLayout.LayoutParams(-1,dp(330)));gap(body,24);heading("Guten Appetit!");body.addView(text(current.optString("title"),26,Ui.INK,true));body.addView(text("Wie ist dein Gericht geworden? Deine Rückmeldung hilft bei den nächsten Rezepten.",23,Ui.MUTED,false));gap(body,22);body.addView(button("Calomatik Feedback geben","recipe.feedback",()->{seedAssistantDraft("Mein Feedback zu "+current.optString("title")+": ");openAssistant();},true));if(!cookingModified)body.addView(button("Gericht bewerten","recipe.review",this::reviewCurrent,false));body.addView(button(isFavorite(current)?"Aus Favoriten entfernen":"Zu Favoriten hinzufügen","recipe.favorite",this::toggleFavorite,false));body.addView(button("Zurück zur Küche","recipe.done",()->go("home"),false));}
    private void reviewCurrent(){showDialog(new AlertDialog.Builder(this).setTitle("Wie hat es geklappt?").setItems(new String[]{"★★★★★ Sehr gut","★★★★ Gut","★★★ In Ordnung","★★ Verbesserungsbedarf","★ Hat nicht geklappt"},(d,w)->sendReview(5-w)));}
    private void cancelReviews(){for(CancellationSignal signal:reviewCancellations)signal.cancel();reviewCancellations.clear();reviewWorker.getQueue().clear();}
    private void sendReview(int rating){
        if(cookingModified||current==null||!allMachineStepsCompleted()||!page.equals("finished"))return;
        final JSONObject recipe=current;final CloudSession session=new CloudSession(this);final String owner=session.account().optString("uid");final CancellationSignal cancellation=new CancellationSignal();reviewCancellations.add(cancellation);
        reviewWorker.execute(()->{try{cancellation.throwIfCanceled();JSONObject program=recipe.getJSONObject("machineProgram");reviewTransport.send(session,new JSONObject().put("recipeId",recipe.getString("id")).put("servings",program.getInt("servings")).put("programDigest",recipe.getString("programDigest")).put("rating",rating).put("worked",rating>=3),cancellation);main.post(()->{if(!destroyed&&!cancellation.isCanceled()&&owner.equals(new CloudSession(this).account().optString("uid")))Toast.makeText(this,"Danke für deine Rückmeldung.",Toast.LENGTH_LONG).show();});}catch(Exception error){main.post(()->{if(!destroyed&&!cancellation.isCanceled()&&owner.equals(new CloudSession(this).account().optString("uid")))error(error);});}finally{main.post(()->reviewCancellations.remove(cancellation));}});
    }
    private void openParameterEditor(ParameterDial dial,boolean timer){
        if(parameterEditor!=null&&parameterEditor.isShowing()){parameterEditor.dismiss();return;}
        knob.edit(dial);parameterEditorOwner=accountUid;parameterEditor=ParameterWheelDialog.show(this,dial,()->{
            if(timer){if(restTimers.containsKey(currentTimerKey()))startRestTimer();View next=footer.findViewWithTag("step.next");if(next!=null)knob.select(next);}
            else if(cookingPanel!=null){cookingPanel.confirmValues(dial==duration);knob.select(cookingPanel.primaryAction());}
        },cookingPanel!=null&&ControlClient.get(this).state.ownRun?()->{if(cookingPanel!=null)cookingPanel.requestStop();}:null);parameterEditor.setOnDismissListener(d->{if(parameterEditor==d)parameterEditor=null;if(dial.isEditing())knob.toggleEdit(dial);if(cookingPanel!=null&&(ControlClient.get(this).state.ownRun||ControlClient.get(this).state.stopping)&&!knob.isEditing())knob.select(cookingPanel.primaryAction());if(renderAfterParameterEditor&&parameterEditor==null){renderAfterParameterEditor=false;main.post(()->{if(!destroyed&&!isFinishing())render();});}});
    }
    private boolean usesStirrer(){return AccessorySelection.stirrer(this);}
    private void refreshAttachment(){
        ControlClient.get(this).attachmentChanged();if(stirring!=null)stirring.ceiling(usesStirrer()?40:page.equals("home")&&homeFunction.equals("turbo")?120:100);if(attachmentPicker!=null)attachmentPicker.refresh();if(cookingPanel!=null)cookingPanel.speedLimit(usesStirrer()?40:page.equals("home")&&homeFunction.equals("turbo")?120:100);
        try{JSONObject context=tools.contextSnapshot();context.put("maximumFunctionSpeed",usesStirrer()?4:10);tools.context(context);tools.invalidate();}catch(Exception ignored){}
    }
    private void dialRow(int seconds,int temp,int speed,java.util.function.IntConsumer change){
        LinearLayout row=new LinearLayout(this);row.setTag("control.dials");row.setClipChildren(false);row.setClipToPadding(false);body.setClipChildren(false);body.setClipToPadding(false);
        AccessorySelection.function(this,page.equals("function")?selectedMode:page.equals("home")?homeFunction:"manual");ControlClient.get(this).attachmentChanged();
        boolean limited=!page.equals("function")||modeRunnable(selectedMode),lidless=page.equals("function")&&selectedMode.equals("lidless");
        duration=new ParameterDial(this,"Dauer",page.equals("home")?0:1,page.equals("home")?28800:7200,1,Math.max(page.equals("home")?0:1,seconds));temperature=new ParameterDial(this,"Temperatur",0,page.equals("home")?180:lidless?100:180,page.equals("home")?1:5,temp);stirring=new ParameterDial(this,"Stufe",page.equals("home")?0:limited?ControlProfile.MIN_SPEED:0,page.equals("home")?120:lidless?30:limited?ControlProfile.MAX_SPEED:100,5,speed);
        if(page.equals("home")){
            ParameterDial[] models={duration,temperature,stirring};
            for(int i=0;i<models.length;i++){
                ParameterDial dial=models[i];dial.homeLayout(true);knob.add(dial);String id="parameter."+(i==0?"duration":i==1?"temperature":"speed");dial.setTag(id);tools.action(id,dial,()->knob.toggleEdit(dial));
                dial.onDirectEdit(()->knob.edit(dial),()->{if(cookingPanel!=null){if(dial==stirring&&stirring.value()==0&&ControlClient.get(this).state.ownRun)cookingPanel.requestStop();else if(!ControlClient.get(this).state.ownRun)cookingPanel.confirmValues(dial==duration);}});dial.setOnClickListener(v->knob.edit(dial));
                dial.onChanged(value->{if(!applyingHome)applyHomeLimits();if(!applyingHome&&homeFunction.equals("thicken")&&dial==temperature&&!ControlClient.get(this).state.ownRun)duration.seedValue(HomeFunctionProfile.thickenSeconds(value));change.accept(value);if(homeNumbers!=null)homeNumbers.refresh();tools.invalidate();
                    if(!applyingHome&&!dial.isSeeding()&&cookingPanel!=null&&ControlClient.get(this).state.ownRun){if(dial==stirring&&value==0)cookingPanel.requestStop();else cookingPanel.liveValueChanged(dial==duration);}
                });
            }
            homeNumbers=new HomeNumberControls(this,duration,temperature,stirring,()->reverse,wantReverse->{
                ControlClient client=ControlClient.get(this);HomeFunctionProfile profile=homeProfile();if(profile.fixedDirection&&wantReverse!=profile.reverse)return false;if(!wantReverse&&homeNumbers!=null&&homeNumbers.stirrerSelected()||client.state.ownRun&&client.activeProfile()!=ControlProfile.NORMAL&&client.activeProfile()!=ControlProfile.MANUAL&&wantReverse!=reverse)return false;
                reverse=wantReverse;homeReverse=reverse;return true;
            },knob::finishTouchEdit);
            homeNumbers.onFunctionSwipe(()->canSwitchHomeProfile()&&!cookingPanel.pendingStart(),id->{if(canSwitchHomeProfile()&&!cookingPanel.pendingStart())openMode(id);});
            homeNumbers.onFryingSelected(this::chooseHomeFrying);
            homeNumbers.onManualTemperatureSelected(this::chooseHomeManual);
            homeNumbers.onCupChoice(this::configureHomeKnob,()->{if(cookingPanel!=null)cookingPanel.cancelHomeCup();});knob.add(homeNumbers.cupCancelButton());
            homeNumbers.onPresetSelected(preset->{if(!canSwitchHomeProfile())return;if(preset.frying!=null){homeFrying=preset.frying.ordinal();applyHomeFunction(false);homeNumbers.showFunctionControls();return;}if(preset.cleaning!=null){homeCleaning=preset.cleaning.ordinal();applyHomeFunction(true);homeNumbers.showFunctionControls();return;}duration.value(preset.seconds);temperature.value(preset.temperature);stirring.value(preset.speed);homeNumbers.refresh();});
            homeNumbers.onIntervalSelected(enabled->{if(ControlClient.get(this).program.active())return;homeMotionStill=false;homeIntervals=enabled;applyHomeFunction(false);homeNumbers.showFunctionControls();});
            homeNumbers.onMotionSelected(still->{homeMotionStill=still;if(homeFunction.equals("slow"))homeIntervals=false;applyHomeFunction(false);stirring.value(still?0:5);homeNumbers.showFunctionControls();});
            homeNumbers.onSpeedSelected(()->{if(cookingPanel!=null&&!ControlClient.get(this).state.ownRun)cookingPanel.confirmValues(false);});homeNumbers.onFunctionStart(()->{if(cookingPanel!=null)cookingPanel.pressHomeKnob();});homeNumbers.onExplicitStart(()->{if(cookingPanel!=null){knob.select(homeNumbers.startButton());cookingPanel.performHomePrimaryAction();}});homeNumbers.onToolChanged(()->{if(cookingPanel!=null)cookingPanel.homeAttachment(()->homeNumbers.stirrerSelected());});
            knob.turnAction(stirring,homeNumbers::turnSpeed);body.addView(homeNumbers,new LinearLayout.LayoutParams(-1,-2));knob.add(homeNumbers.tareButton());knob.add(homeNumbers.attachmentButton());knob.add(homeNumbers.startButton());attachmentPicker=null;stirring.ceiling(homeNumbers.stirrerSelected()?40:usesStirrer()?40:homeFunction.equals("turbo")?120:100);homeNumbers.refresh();knob.confirmNext(duration,temperature);knob.confirmNext(temperature,stirring);knob.select(duration);return;
        }
        ParameterDial[] dials={duration,temperature,stirring};for(int i=0;i<dials.length;i++){
            ParameterDial dial=dials[i];dial.homeLayout(page.equals("home"));knob.add(dial);String dialId="parameter."+(i==0?"duration":i==1?"temperature":"speed");dial.setTag(dialId);tools.action(dialId,dial,()->knob.toggleEdit(dial));dial.circularDrag();dial.onDirectEdit(()->knob.edit(dial),()->{if(cookingPanel!=null)cookingPanel.confirmValues(dial==duration);});dial.setOnClickListener(v->knob.edit(dial));dial.onChanged(value->{if(page.equals("function")){applyFunctionLimits();}else if(temperature!=null&&stirring!=null&&!ControlClient.get(this).state.ownRun){temperature.choices(0,lidless||stirring.value()==0?100:180);boolean fry=temperature.value()>100;stirring.choices(fry?10:0,fry?10:lidless?30:100);if(fry)reverse=true;}renderDirection();change.accept(value);tools.invalidate();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(page.equals("home")?320:260),1);if(i>0)lp.leftMargin=dp(page.equals("home")?4:8);
            if(i==2){FrameLayout speedColumn=new FrameLayout(this);speedColumn.setClipChildren(false);speedColumn.addView(dial,new FrameLayout.LayoutParams(-1,-1));
                ImageButton direction=new ImageButton(this);direction.setTag("direction-label");direction.setScaleType(ImageView.ScaleType.CENTER_INSIDE);direction.setPadding(dp(3),dp(3),dp(3),dp(3));direction.setBackgroundColor(Color.TRANSPARENT);direction.setOnClickListener(v->{toggleDirection();if(cookingPanel!=null)cookingPanel.confirmValues(false);});knob.add(direction);tools.action(page.equals("step")?"step.direction":"control.direction",direction,this::toggleDirection);
                FrameLayout.LayoutParams action=new FrameLayout.LayoutParams(dp(70),dp(70),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);action.bottomMargin=dp(34);speedColumn.addView(direction,action);dial.accessory(direction);row.addView(speedColumn,lp);
            }else row.addView(dial,lp);
        }
        LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2);rowSize.leftMargin=-dp(page.equals("home")?24:12);rowSize.rightMargin=-dp(page.equals("home")?24:12);body.addView(row,rowSize);attachmentPicker=null;stirring.ceiling(usesStirrer()?40:100);applyFunctionLimits();renderDirection();knob.confirmNext(duration,temperature);knob.confirmNext(temperature,stirring);knob.edit(duration);gap(body,14);
    }
    private void loadMachineCatalogue(){if(machineLoading)return;machineLoading=true;catalogueRefreshed=SystemClock.elapsedRealtime();worker.execute(()->{try{JSONObject result=recipeOffline.get("machine:"+com.kitchenos.connect.control.ControlProfile.ID,CloudSession::machineCatalog);JSONArray rows=result.getJSONArray("recipes"),ready=new JSONArray();for(int i=0;i<rows.length();i++)try{ready.put(MachineProgram.card(rows.getJSONObject(i)));}catch(Exception ignored){}main.post(()->{if(destroyed)return;machineLoading=false;catalogueRefreshFailed=false;catalogueRefreshed=SystemClock.elapsedRealtime();boolean changed=!ready.toString().equals(readyCatalogue.toString());if(changed)readyCatalogue=ready;getSharedPreferences("public-catalogue",0).edit().putString("machineItems",ready.toString()).apply();if(changed&&Arrays.asList("recipes","favorites").contains(page))render();warmMachineRecipes();});}catch(Exception e){main.post(()->{machineLoading=false;catalogueRefreshFailed=true;recipeError=e.getMessage();if(!destroyed&&page.equals("recipes"))render();});}});}
    private void warmMachineRecipes(){/* Existing machine catalogue is read separately. No automatic paid adaptation while browsing. */}

    private JSONArray allPrepared(){JSONArray result=new JSONArray();for(int i=0;i<privateCatalogue.length();i++)if(MachineProgram.executable(privateCatalogue.optJSONObject(i).optJSONObject("machineProgram")))result.put(privateCatalogue.optJSONObject(i));for(int i=0;i<readyCatalogue.length();i++)if(MachineProgram.executable(readyCatalogue.optJSONObject(i).optJSONObject("machineProgram")))result.put(readyCatalogue.optJSONObject(i));return result;}
    private void readPrivateCache(){try{JSONArray variants=new MachineProgramStore(this).all(),rows=new JSONArray();for(int i=0;i<variants.length();i++)rows.put(MachineProgram.card(variants.getJSONObject(i)));if(!rows.toString().equals(privateCatalogue.toString()))privateCatalogue=rows;}catch(Exception e){recipeError=e.getMessage();}}
    private void loadPrivateCatalogue(){CloudSession session=new CloudSession(this);String uid=session.account().optString("uid");if(uid.isEmpty()){privateCatalogue=new JSONArray();return;}worker.execute(()->{try{JSONArray variants=session.api("/v1/recipes/private",new JSONObject().put("controlProfileId",com.kitchenos.connect.control.ControlProfile.ID)).getJSONArray("recipes");MachineProgramStore store=new MachineProgramStore(this);for(int i=0;i<variants.length();i++)store.save(variants.getJSONObject(i),uid);main.post(()->{if(destroyed||!uid.equals(new CloudSession(this).account().optString("uid")))return;String before=privateCatalogue.toString();readPrivateCache();prepareImports();if(!before.equals(privateCatalogue.toString())&&Arrays.asList("recipes","favorites").contains(page))render();});}catch(Exception ignored){}});}
    private JSONObject importedCard(Recipe recipe)throws Exception{JSONArray ingredients=new JSONArray();for(int i=0;i<recipe.ingredients.length();i++)ingredients.put(new JSONObject().put("name",recipe.ingredients.getString(i)).put("amount",JSONObject.NULL).put("unit","").put("literal",true));return new JSONObject().put("id",recipe.id).put("foodIdentity",recipe.id).put("title",recipe.title).put("servings",recipe.servings).put("ingredients",ingredients).put("importedRecipe",recipe.json()).put("private",true);}
    private void openImported(Recipe recipe){openImported(recipe,0);}
    private void openImported(Recipe recipe,int servings){JSONArray cards=allPrepared();JSONObject other=null;for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i);try{if(card.optString("id").equals(recipe.id)||MachineProgram.asRecipe(card).id.equals(recipe.id)){if(servings==0||card.optJSONObject("machineProgram").optInt("servings")==servings){openRecipe(card);requestedServings=servings;return;}other=card;}}catch(Exception ignored){}}try{openRecipe(other==null?importedCard(recipe):other);requestedServings=servings;if(other!=null&&servings>0)prepareRecipe(current,true,servings);}catch(Exception e){error(e);}}

    private List<Recipe> pendingImports(){HashSet<String> prepared=new HashSet<>();JSONArray all=allPrepared();for(int i=0;i<all.length();i++){JSONObject card=all.optJSONObject(i);prepared.add(card.optString("id"));try{prepared.add(MachineProgram.asRecipe(card).id);}catch(Exception ignored){}}ArrayList<Recipe> result=new ArrayList<>();for(Recipe recipe:KitchenRecipes.all(this))if(!prepared.contains(recipe.id))result.add(recipe);return result;}
    private void prepareImports(){/* Imports are prepared only through the explicit recipe action. */}
    private void prepareRecipe(JSONObject recipe,boolean open){prepareRecipe(recipe,open,requestedServings>0?requestedServings:new KitchenStore(this).snapshot().optJSONObject("preferences").optInt("servings",2));}
    private void prepareRecipe(JSONObject recipe,boolean open,int portions){
        if(recipe==null||open&&preparingRecipe)return;if(portions<1||portions>20){if(open)showDialog(new AlertDialog.Builder(this).setTitle("Portionen für diesen Kochvorgang").setMessage("Wähle 1–20 Portionen. Die Anleitung wird für die Menge neu vorbereitet.").setPositiveButton("Portionen wählen",(d,w)->changePortions(Math.max(1,Math.min(20,portions)))).setNegativeButton("Später",null));return;}CloudSession preparationSession=new CloudSession(this);String owner=preparationSession.account().optString("uid");if(owner.isEmpty()){if(open)new KitchenPages(this,legacyNavigation()).account();return;}
        if(!new KitchenStore(this).snapshot().optJSONObject("preferences").optBoolean("aiConsent")){if(open)KitchenConsent.ai(this,()->prepareRecipe(recipe,true,portions));return;}
        JSONObject imported=recipe.optJSONObject("importedRecipe");if(imported!=null&&imported.optInt("servings")==0){if(!open)return;LinearLayout box=col();box.addView(text("Für wie viele Portionen ist das geteilte Originalrezept gedacht? Die Angabe fehlt in der Quelle.",23,Ui.INK,false));ParameterDial servings=new ParameterDial(this,"Portionen",1,20,1,2);servings.unknown();box.addView(servings,new LinearLayout.LayoutParams(-1,dp(220)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Portionen des Originalrezepts").setView(box).setNegativeButton("Später",null).setPositiveButton("Weiter",(d,w)->{if(!servings.known())return;try{imported.put("servings",servings.value());prepareRecipe(recipe,true,portions);}catch(Exception e){error(e);}}).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);servings.onChanged(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));RotaryDialogs.bind(this,dialog);return;}
        cancelPreparation();final long epoch=preparationEpoch;final CancellationSignal cancellation=new CancellationSignal();preparationCancellation=cancellation;if(open){preparingRecipe=true;preparationError="";render();}preparationTimeout=()->{if(preparationCurrent(owner,epoch,cancellation)){cancelPreparation();if(open&&page.equals("detail")){preparationError="Die Vorbereitung hat noch nicht rechtzeitig geantwortet. Bitte versuche es erneut.";render();}}};main.postDelayed(preparationTimeout,120000);preparationWork=preparation.submit(()->{try{if(!preparationCurrent(owner,epoch,cancellation))return;String id=recipe.getString("id");String request=open?UUID.randomUUID().toString():UUID.nameUUIDFromBytes(("machine-v2:"+com.kitchenos.connect.control.ControlProfile.ID+":"+id+":"+portions+":"+recipe.optString("updatedAt")+":"+owner+":"+warmRetry).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();JSONObject input=new JSONObject().put("requestId",request).put("recipeId",id).put("servings",portions).put("consent",true).put("controlProfileId",com.kitchenos.connect.control.ControlProfile.ID);if(imported!=null)input.put("recipe",imported);if(!preparationCurrent(owner,epoch,cancellation))return;JSONObject result=preparationTransport.prepare(preparationSession,input,cancellation);if(!preparationCurrent(owner,epoch,cancellation))return;
            if(!result.getBoolean("compatible")){if(open)main.post(()->preparationRejected(recipe,owner,epoch,result.optString("reason")));return;}
            JSONObject prepared=MachineProgram.card(result);if(result.optBoolean("private"))new MachineProgramStore(this).save(result,owner);
            main.post(()->{if(!preparationCurrent(owner,epoch,cancellation))return;finishPreparation(epoch);if(result.optBoolean("private"))readPrivateCache();else{JSONArray next=new JSONArray();for(int i=0;i<readyCatalogue.length();i++)if(!readyCatalogue.optJSONObject(i).optString("id").equals(prepared.optString("id")))next.put(readyCatalogue.optJSONObject(i));next.put(prepared);readyCatalogue=next;getSharedPreferences("public-catalogue",0).edit().putString("machineItems",next.toString()).apply();}if(open&&epoch==preparationEpoch&&page.equals("detail")&&current!=null&&current.optString("id").equals(recipe.optString("id"))){openRecipe(prepared);detailTab="ingredients";requestedServings=portions;go("detail");scroll.post(()->scroll.scrollTo(0,0));}else if(Arrays.asList("home","recipes","favorites").contains(page))render();});
        }catch(Exception e){main.post(()->{if(!preparationCurrent(owner,epoch,cancellation))return;finishPreparation(epoch);recipeError=e.getMessage();if(open&&epoch==preparationEpoch&&page.equals("detail")&&current!=null&&current.optString("id").equals(recipe.optString("id"))){preparationError=CloudConnection.message(e);render();scroll.post(()->scroll.scrollTo(0,0));}else if(readyCatalogue.length()==0&&Arrays.asList("home","recipes").contains(page))render();});}});
    }
    private void cancelPreparation(){
        preparationEpoch++;preparingRecipe=false;preparationError="";if(preparationTimeout!=null){main.removeCallbacks(preparationTimeout);preparationTimeout=null;}
        if(preparationWork!=null){preparationWork.cancel(true);preparationWork=null;preparation.purge();}
        if(preparationCancellation!=null){preparationCancellation.cancel();preparationCancellation=null;}
    }
    private void finishPreparation(long epoch){if(epoch!=preparationEpoch)return;preparingRecipe=false;if(preparationTimeout!=null){main.removeCallbacks(preparationTimeout);preparationTimeout=null;}preparationWork=null;preparationCancellation=null;}
    private boolean preparationCurrent(String owner,long epoch,CancellationSignal cancellation){return !destroyed&&!cancellation.isCanceled()&&!Thread.currentThread().isInterrupted()&&epoch==preparationEpoch&&owner.equals(new CloudSession(this).account().optString("uid"));}
    private void preparationRejected(JSONObject recipe,String owner,long epoch,String reason){
        if(destroyed||epoch!=preparationEpoch||!owner.equals(new CloudSession(this).account().optString("uid")))return;
        finishPreparation(epoch);if(!page.equals("detail")||current==null||!current.optString("id").equals(recipe.optString("id")))return;
        render();showDialog(new AlertDialog.Builder(this).setTitle("Dieses Gericht braucht noch eine Anpassung").setMessage(reason).setPositiveButton("Portionen anpassen",null).setNegativeButton("Andere Rezepte",(d,w)->go("recipes")));
    }
    private void clearCookTracking(){clearRestTimers();coldInterrupted=false;ingredientChecks.clear();weighingStarted.clear();activeCookState=null;cookSessionId="";stepControlStates=new Bundle();weighingRecords=new JSONArray();loadStoredJourney();}
    private JSONObject activeRecipe(){try{return activeCookState==null?null:new JSONObject(activeCookState.getString("recipe",""));}catch(Exception e){return null;}}
    private boolean activeRecipeMatches(){JSONObject active=activeRecipe();return activeCookState!=null&&accountUid.equals(activeCookState.getString("owner",""))&&current!=null&&active!=null&&!current.optString("programDigest").isEmpty()&&current.optString("programDigest").equals(active.optString("programDigest"));}
    private Bundle workingCookState(){
        Bundle state=new Bundle();state.putString("journeyProcess",CookJourneyStore.processMarker());state.putString("owner",accountUid);state.putString("cookId",cookSessionId);if(current!=null)state.putString("recipe",current.toString());state.putInt("step",currentStep);state.putBoolean("modified",cookingModified);state.putBoolean("coldInterrupted",coldInterrupted);Bundle timers=new Bundle();for(Map.Entry<String,Long> timer:restTimers.entrySet())timers.putLong(timer.getKey(),timer.getValue());state.putBundle("restTimers",timers);state.putString("restTimerKey",restTimerKey);state.putLong("restDeadline",restDeadline);state.putLong("savedUptime",SystemClock.elapsedRealtime());state.putStringArrayList("completed",new ArrayList<>(completedSteps));state.putStringArrayList("ingredients",new ArrayList<>(ingredientChecks));state.putStringArrayList("weighingStarted",new ArrayList<>(weighingStarted));state.putBundle("controls",new Bundle(stepControlStates));state.putString("weighing",weighingRecords.toString());
        Bundle edits=new Bundle();for(Map.Entry<String,int[]> entry:stepEdits.entrySet())edits.putIntArray(entry.getKey(),entry.getValue().clone());state.putBundle("edits",edits);Bundle directions=new Bundle();for(Map.Entry<String,Boolean> entry:directionEdits.entrySet())directions.putBoolean(entry.getKey(),entry.getValue());state.putBundle("directions",directions);return state;
    }
    private void restoreWorkingCook(Bundle state,boolean restoreRecipe){
        if(state==null||!accountUid.equals(state.getString("owner","")))return;
        if(restoreRecipe)try{current=new JSONObject(state.getString("recipe",""));}catch(Exception e){return;}
        cookSessionId=state.getString("cookId","");currentStep=state.getInt("step",0);cookingModified=state.getBoolean("modified",true);coldInterrupted=state.getBoolean("coldInterrupted",false);restTimerKey=state.getString("restTimerKey","");restDeadline=SystemClock.elapsedRealtime()>=state.getLong("savedUptime",0)?state.getLong("restDeadline",0):0;restTimers.clear();Bundle timers=state.getBundle("restTimers");if(timers!=null&&current!=null&&CookJourneyStore.processMarker().equals(state.getString("journeyProcess",""))&&SystemClock.elapsedRealtime()>=state.getLong("savedUptime",0))for(String key:timers.keySet())if(key.startsWith(current.optString("programDigest")+":"))restTimers.put(key,timers.getLong(key));
        completedSteps.clear();ArrayList<String> completed=state.getStringArrayList("completed");if(completed!=null)completedSteps.addAll(completed);ingredientChecks.clear();weighingStarted.clear();ArrayList<String> checked=state.getStringArrayList("ingredients");if(checked!=null)ingredientChecks.addAll(checked);ArrayList<String> started=state.getStringArrayList("weighingStarted");if(started!=null)weighingStarted.addAll(started);stepControlStates=state.getBundle("controls")==null?new Bundle():new Bundle(state.getBundle("controls"));
        stepEdits.clear();Bundle edits=state.getBundle("edits");if(edits!=null)for(String key:edits.keySet())if(edits.getIntArray(key)!=null)stepEdits.put(key,edits.getIntArray(key).clone());directionEdits.clear();Bundle directions=state.getBundle("directions");if(directions!=null)for(String key:directions.keySet())directionEdits.put(key,directions.getBoolean(key));try{weighingRecords=new JSONArray(state.getString("weighing","[]"));}catch(Exception ignored){weighingRecords=new JSONArray();}
    }
    private void restoreCookTracking(Bundle saved){
        if(saved==null||!accountUid.equals(saved.getString("ownerUid",""))){loadStoredJourney();return;}
        Bundle active=saved.getBundle("activeCookState"),working=saved.getBundle("workingCookState");boolean processChanged=active!=null&&!CookJourneyStore.processMarker().equals(active.getString("journeyProcess",""));
        if(processChanged){
            loadStoredJourney();if(activeCookState==null&&accountUid.equals(active.getString("owner",""))){activeCookState=new Bundle(active);activeCookState.putBoolean("modified",true);activeCookState.putBoolean("coldInterrupted",true);activeCookState.putBundle("controls",new Bundle());activeCookState.putLong("restDeadline",0);activeCookState.remove("restTimers");}
            if(activeCookState!=null&&working!=null&&activeCookState.getString("cookId","").equals(working.getString("cookId","")))restoreWorkingCook(activeCookState,true);else restoreWorkingCook(working,false);
        }else{if(active!=null&&accountUid.equals(active.getString("owner","")))activeCookState=new Bundle(active);restoreWorkingCook(working,false);if(activeCookState==null)loadStoredJourney();}
    }
    private void checkpointCook(){if(activeCookState!=null&&!cookSessionId.isEmpty()&&cookSessionId.equals(activeCookState.getString("cookId",""))&&accountUid.equals(activeCookState.getString("owner",""))&&current!=null&&program()!=null)activeCookState=workingCookState();if(activeCookState!=null&&accountUid.equals(activeCookState.getString("owner",""))&&accountUid.equals(new CloudSession(this).account().optString("uid"))){Bundle timers=new Bundle();for(Map.Entry<String,Long> timer:restTimers.entrySet())timers.putLong(timer.getKey(),timer.getValue());activeCookState.putBundle("restTimers",timers);String activeTimer=activeCookState.getString("restTimerKey","");long live=restTimers.getOrDefault(activeTimer,0L);activeCookState.putLong("restDeadline",live);if(live<=0)activeCookState.putString("restTimerKey","");activeCookState.putLong("savedUptime",SystemClock.elapsedRealtime());try{new CookJourneyStore(this).save(activeCookState);}catch(Exception e){android.util.Log.w("KitchenOSJourney","Kochstand konnte nicht gespeichert werden");}}}
    private void loadStoredJourney(){try{activeCookState=new CookJourneyStore(this).load();}catch(Exception e){android.util.Log.w("KitchenOSJourney","Gespeicherter Kochstand konnte nicht geladen werden");}}
    private void rememberPanel(){
        if(cookingPanel==null)return;Bundle state=cookingPanel.snapshot();if(!accountUid.equals(state.getString("owner","")))return;String session=state.getString("cookId",""),step=state.getString("stepId","");
        if(session.equals(cookSessionId)){stepControlStates.putBundle(step,state);cookingModified|=state.getBoolean("modified",false);}
        if(activeCookState!=null&&session.equals(activeCookState.getString("cookId",""))){Bundle controls=activeCookState.getBundle("controls");if(controls==null)controls=new Bundle();controls.putBundle(step,state);activeCookState.putBundle("controls",controls);activeCookState.putBoolean("modified",activeCookState.getBoolean("modified")||state.getBoolean("modified"));}
    }
    private void releaseCookingPanel(){if(inlineScale!=null){inlineScale.detach();inlineScale=null;main.removeCallbacks(scaleHeartbeat);ControlClient.get(this).listen(null);}if(cookingPanel==null)return;rememberPanel();cookingPanel.dispose();rememberPanel();cookingPanel=null;}
    private void beginCookState(int step){
        cookSessionId=UUID.randomUUID().toString();currentStep=RecipePresentation.next(program().optJSONArray("steps"),step);completedSteps.clear();ingredientChecks.clear();weighingStarted.clear();cookingModified=false;coldInterrupted=false;stepEdits.clear();directionEdits.clear();stepControlStates=new Bundle();weighingRecords=new JSONArray();clearRestTimers();activeCookState=workingCookState();
    }
    private void restoreActiveWorkingState(){if(activeCookState!=null)restoreWorkingCook(new Bundle(activeCookState),true);}
    private void confirmCancelCook(){
        if(activeCookState==null||!accountUid.equals(activeCookState.getString("owner","")))return;
        String owner=accountUid,cook=activeCookState.getString("cookId","");ControlClient client=ControlClient.get(this);
        showDialog(new AlertDialog.Builder(this).setTitle("Kochen abbrechen?").setMessage("Der gespeicherte Kochstand und seine Timer werden entfernt. Das Rezept und deine Favoriten bleiben erhalten.").setNegativeButton("Weiterkochen",null).setPositiveButton(client.state.ownRun?"Stoppen und abbrechen":"Abbrechen",(dialog,which)->{
            if(!owner.equals(new CloudSession(this).account().optString("uid"))||activeCookState==null||!cook.equals(activeCookState.getString("cookId","")))return;
            if(client.state.ownRun||client.state.stopping||!client.state.pendingAction.isEmpty()){
                if(cookingPanel!=null)cookingPanel.requestStop();else client.stop();awaitCookCancellation(owner,cook,SystemClock.elapsedRealtime()+15000);return;
            }
            if(client.state.uncertainRun||client.state.deviceFaultLatched){Toast.makeText(this,"Bitte zuerst den Maschinenzustand prüfen. Dein Kochstand bleibt erhalten.",Toast.LENGTH_LONG).show();return;}
            forgetCook(owner,cook);
        }));
    }
    private void awaitCookCancellation(String owner,String cook,long deadline){
        if(destroyed||activeCookState==null||!cook.equals(activeCookState.getString("cookId",""))||!owner.equals(new CloudSession(this).account().optString("uid")))return;
        ControlClient client=ControlClient.get(this);client.tick();ControlState state=client.state;
        if(state.connected&&state.fresh(SystemClock.elapsedRealtime())&&state.idleConfirmed&&!state.ownRun&&!state.stopping&&state.pendingAction.isEmpty()&&!state.uncertainRun&&!state.deviceFaultLatched){forgetCook(owner,cook);return;}
        if(SystemClock.elapsedRealtime()>=deadline){Toast.makeText(this,"Der Stopp ist noch nicht bestätigt. Dein Kochstand bleibt erhalten.",Toast.LENGTH_LONG).show();return;}
        main.postDelayed(()->awaitCookCancellation(owner,cook,deadline),200);
    }
    private void forgetCook(String owner,String cook){
        if(activeCookState==null||!cook.equals(activeCookState.getString("cookId",""))||!owner.equals(new CloudSession(this).account().optString("uid")))return;
        releaseCookingPanel();try{new CookJourneyStore(this).clear();}catch(Exception failure){error(failure);return;}clearRestTimers();activeCookState=null;cookSessionId="";stepControlStates=new Bundle();completedSteps.clear();ingredientChecks.clear();weighingStarted.clear();weighingRecords=new JSONArray();stepEdits.clear();directionEdits.clear();coldInterrupted=false;go("home");
    }
    private void resumeActiveCook(){if(confirmNavigation(this::resumeActiveCook))return;if(activeCookState==null||!accountUid.equals(activeCookState.getString("owner","")))return;releaseCookingPanel();checkpointCook();restoreActiveWorkingState();go("step");}
    private void startOrResumeCooking(){if(current==null)return;if(current.optBoolean("planIncomplete")){PlanCompletionDialog.show(this,current,()->{go("plan");loadPlan();});return;}if(program()==null||requestedServings>0&&requestedServings!=program().optInt("servings")){prepareRecipe(current,true);return;}if(activeRecipeMatches()){resumeActiveCook();return;}requestNewCook(0);}
    private void previewStep(int index){if(program()==null)return;try{JSONArray history=activeRecipeMatches()?new JSONArray(activeCookState.getString("weighing","[]")):new JSONArray();StepPreviewDialog.show(this,current,index,activeRecipeMatches()?"Kochen fortsetzen":program()==null?"Mit KI für die Maschine vorbereiten":"Loskochen",this::startOrResumeCooking,history);}catch(Exception e){error(e);}}
    private String coldRestartNotice(){return "Kochstand wiederhergestellt. Unterbrochene Schritte erneut starten; verbleibende Timerzeiten prüfen.";}
    private void requestNewCook(int index){
        if(confirmNavigation(()->requestNewCook(index)))return;stopRestDisplay();
        if(activeCookState==null){releaseCookingPanel();beginCookState(index);go("step");return;}
        JSONObject next=current;JSONObject previous=activeRecipe();showDialog(new AlertDialog.Builder(this).setTitle("Ein anderes Gericht beginnen?").setMessage(previous==null?"Dein bisheriger Kochstand wird durch den neuen Durchgang ersetzt.":"Für „"+previous.optString("title")+"“ ist noch ein Kochstand vorhanden. Mit dem neuen Durchgang beginnst du von vorn.").setNegativeButton("Zurück",null).setPositiveButton("Neues Gericht beginnen",(dialog,which)->{releaseCookingPanel();current=next;beginCookState(index);go("step");}));
    }
    private void restartCurrentRecipe(){if(confirmNavigation(this::restartCurrentRecipe))return;releaseCookingPanel();checkpointCook();JSONObject selected=current;showDialog(new AlertDialog.Builder(this).setTitle("Rezept von vorn beginnen?").setMessage("Ein neuer Kochdurchgang beginnt ohne erledigte Schritte. Der bisherige Kochstand wird ersetzt.").setNegativeButton("Zurück",null).setPositiveButton("Neu beginnen",(dialog,which)->{current=selected;beginCookState(0);go("step");}));}
    private void openStepScale(JSONObject step){
        try{JSONArray ingredients=step.getJSONArray("ingredients");ArrayList<String> ids=new ArrayList<>();for(int i=0;i<ingredients.length();i++)ids.add(step.getString("id")+":"+ingredients.getJSONObject(i).getString("name"));
            startActivityForResult(new Intent(this,ScaleActivity.class).putExtra("instruction",step.optString("instruction")).putExtra("ingredients",ingredients.toString()).putStringArrayListExtra("ingredientIds",ids).putExtra("ownerUid",accountUid).putExtra("programDigest",current.optString("programDigest")).putExtra("stepId",step.getString("id")).putExtra("existingMeasurements",RecipeWeighingHistory.envelope(accountUid,current.optString("programDigest"),step,weighingRecords).toString()),43);
        }catch(Exception e){error(e);}
    }
    private void acceptWeighingResult(int result,Intent data){
        if(result!=RESULT_OK||data==null||current==null||program()==null||!page.equals("step")||!accountUid.equals(new CloudSession(this).account().optString("uid")))return;
        try{String raw=data.getStringExtra("weighingResult");if(raw==null||raw.length()>65536)return;JSONObject receipt=new JSONObject(raw),step=program().getJSONArray("steps").getJSONObject(currentStep);if(receipt.optInt("version")!=1||!accountUid.equals(receipt.optString("ownerUid"))||!current.optString("programDigest").equals(receipt.optString("programDigest"))||!step.optString("id").equals(receipt.optString("stepId"))||!step.optString("kind").equals("weigh"))return;
            JSONArray ingredients=step.getJSONArray("ingredients"),measurements=receipt.getJSONArray("measurements");if(measurements.length()>80)return;measurements=RecipeWeighingHistory.forStep(measurements,step);
            for(int i=0;i<measurements.length();i++){JSONObject measure=measurements.getJSONObject(i);int index=measure.optInt("ingredientIndex",-1);if(index<0||index>=ingredients.length()||!measure.optString("source").equals("device-scale")||measure.optLong("sampleCounter",0)<=0||measure.optLong("observedElapsedRealtime",-1)<0||measure.optLong("confirmedAtEpochMs",0)<=0)continue;String expected=step.optString("id")+":"+ingredients.getJSONObject(index).getString("name");if(!expected.equals(measure.optString("ingredientId"))||!measure.has("tareRaw")||!measure.has("rawWeight")||!measure.has("grams")||measure.getInt("rawWeight")-measure.getInt("tareRaw")!=measure.getInt("grams"))continue;ingredientChecks.add(expected);JSONArray retained=new JSONArray();for(int j=Math.max(0,weighingRecords.length()-199);j<weighingRecords.length();j++)if(!expected.equals(weighingRecords.optJSONObject(j).optString("ingredientId")))retained.put(weighingRecords.optJSONObject(j));retained.put(new JSONObject(measure.toString()));weighingRecords=retained;}
            JSONArray unmeasured=receipt.optJSONArray("confirmedWithoutMeasurement");if(unmeasured!=null&&unmeasured.length()<=80)for(int i=0;i<ingredients.length();i++){String id=step.getString("id")+":"+ingredients.getJSONObject(i).getString("name");for(int j=0;j<unmeasured.length();j++)if(id.equals(unmeasured.optString(j))){ingredientChecks.add(id);cookingModified=true;}}
            JSONArray manual=receipt.optJSONArray("addedByHand");if(manual!=null&&manual.length()<=80)for(int i=0;i<ingredients.length();i++){JSONObject item=ingredients.getJSONObject(i);String unit=item.optString("unit"),id=step.getString("id")+":"+item.getString("name");if(unit.equals("g")||unit.equals("kg"))continue;for(int j=0;j<manual.length();j++)if(id.equals(manual.optString(j)))ingredientChecks.add(id);}
            if(receipt.optBoolean("weighingStarted"))weighingStarted.add(step.getString("id"));
            checkpointCook();if(!inlineMeasurementDelivery)render();else refreshWeighingNavigation(step);
        }catch(Exception ignored){/* Incomplete or unrelated measurements never mark ingredients or machine steps. */}
    }
    private String selectedMode="manual";
    /** Session comparison reference only. Unsupported input stays untouched in the actual dials. */
    private ControlValues modeReference(int[] chosen){if(page.equals("home")&&homeFunction.equals("turbo")&&ControlProfile.supportsIn(ControlProfile.TURBO,chosen[0],chosen[1],chosen[2])&&!reverse)return ControlValues.turbo(chosen[0],chosen[2]);int sign=reverse?-1:1;int[] reference=ControlProfile.supportsLocal(chosen[0],chosen[1],chosen[2]*sign)?chosen:defaults(selectedMode);if(!ControlProfile.supportsLocal(reference[0],reference[1],reference[2]*sign))return new ControlValues(5,0,sign*5);return new ControlValues(reference[0],reference[1],reference[2]*sign);}
    private String[] mode(String id){for(String[] row:MODES)if(row[0].equals(id))return row;return MODES[0];}
    private void modes(){
        FrameLayout intro=new FrameLayout(this);intro.setTag("functions.intro");LinearLayout copy=col();copy.setTag("functions.copy");copy.addView(text("Funktionen anpassen",30,Ui.INK,true));gap(copy,10);copy.addView(text("Gedrückt halten und verschieben. Ein- und Ausblenden im Menü der jeweiligen Funktion.",19,Ui.MUTED,false));
        FrameLayout.LayoutParams copySize=new FrameLayout.LayoutParams(Math.max(dp(260),getResources().getDisplayMetrics().widthPixels-dp(218)),-2);intro.addView(copy,copySize);body.addView(intro,new LinearLayout.LayoutParams(-1,dp(128)));
        LinearLayout catalogue=col();catalogue.setTag("functions.catalogue");functionShelf(catalogue,true);body.addView(catalogue);
    }
    private boolean modeRunnable(String id){return id.equals("scale")||HomeFunctionProfile.get(id).runnable()&&(!id.equals("lidless")||ControlAvailability.LIDLESS);}
    private void openMode(String id){if(confirmNavigation(()->openMode(id)))return;clearHomeCache();if(FunctionSetup.ids().contains(id)){ControlClient.get(this).endLidInterruption();homeFunction=id;homeMotionStill=false;homeIntervals=null;if(id.equals("eggs"))eggGrade=1;HomeFunctionProfile profile=homeProfile();homeSettings=new int[]{profile.seconds,profile.temperature,profile.speed};homeReverse=profile.reverse;if(page.equals("home")&&!ControlClient.get(this).state.ownRun&&ControlClient.get(this).state.pendingAction.isEmpty()&&!ControlClient.get(this).state.uncertainRun){applyHomeFunction(true);if(!id.equals("manual"))homeNumbers.showFunctionPreparation();}else go("home");return;}if(!id.equals(selectedMode)||FunctionSetup.get(id).lockedDirection)reverse=FunctionSetup.get(id).reverse;if(!page.equals("function"))functionOrigin=page;selectedMode=id;if(id.equals("scale")){startActivity(new Intent(this,ScaleActivity.class));return;}go("function");}
    private void function(){
        FunctionSetup setup=FunctionSetup.get(selectedMode);String[] help=ModeHelp.forMode(selectedMode);
        if(java.util.Arrays.asList("stir","whip","mix","chop","puree","emulsify","clean","slow","thicken","rice","eggs").contains(selectedMode)){FunctionVesselView scene=new FunctionVesselView(this,selectedMode,()->reverse,()->stirring==null?0:stirring.value());scene.onCupStart(()->{if(cookingPanel!=null)cookingPanel.confirmSpeed();});body.addView(scene,new LinearLayout.LayoutParams(-1,dp(380)));}else{ImageView photo=new ImageView(this);photo.setTag("function.hero");photo.setBackground(shape(Ui.CARD,24,false));photo.setClipToOutline(true);FunctionPhotography.bindHero(this,photo,selectedMode);body.addView(photo,new LinearLayout.LayoutParams(-1,dp(380)));}gap(body,20);
        heading(mode(selectedMode)[1]);body.addView(text(help[0],22,Ui.MUTED,false));gap(body,18);
        if(selectedMode.equals("eggs"))eggSettings();
        else if(modeRunnable(selectedMode)){
            int[] preset=modeEdits.containsKey(selectedMode)?modeEdits.get(selectedMode):defaults(selectedMode);
            dialRow(preset[0],preset[1],preset[2],v->modeEdits.put(selectedMode,new int[]{duration.value(),temperature.value(),stirring.value()}));applyFunctionLimits();renderDirection();
            cookingPanel=new GuidedControlPanel(this,body,duration,temperature,stirring,()->reverse,modeReference(new int[]{duration.value(),temperature.value(),stirring.value()}),tools,()->go(functionOrigin),selectedMode.equals("lidless"));cookingPanel.setFunctionTitle(mode(selectedMode)[1]);cookingPanel.speedLimit(Math.min(setup.maxSpeed,usesStirrer()?40:100));knob.select(duration);
        }else{body.addView(text("Programm noch nicht verfügbar",24,Ui.INK,true));gap(body,10);body.addView(text(selectedMode.equals("lidless")?ControlAvailability.LIDLESS_REASON:setup.unavailable,22,Ui.MUTED,false));}
        section("So gelingt es",null,null);for(int i=1;i<help.length;i++){body.addView(text(i+". "+help[i],22,Ui.INK,false));gap(body,16);}
    }
    private boolean applyingFunctionLimits;
    private void applyFunctionLimits(){
        if(!page.equals("function")||applyingFunctionLimits||duration==null||temperature==null||stirring==null)return;
        applyingFunctionLimits=true;try{
            FunctionSetup setup=FunctionSetup.get(selectedMode);if(!setup.runnable())return;
            if(setup.lockedDirection)reverse=setup.reverse;
            if(!selectedMode.equals("manual")){
                temperature.choices(setup.minTemperature,setup.maxTemperature);stirring.choices(setup.minSpeed,setup.maxSpeed);duration.choices(1,setup.maxSeconds);
            }else{
                temperature.choices(0,150);stirring.choices(0,100);duration.choices(1,7200);
            }
        }finally{applyingFunctionLimits=false;}
    }
    private void seedAssistantDraft(String prompt){AssistantConversation conversation=AssistantConversation.get(this);String draft=conversation.draft();conversation.draft(draft.isEmpty()?prompt:draft+"\n\n"+prompt);}
    private void eggSettings(){
        android.content.SharedPreferences choices=getSharedPreferences("egg-preferences",0);int count=Math.max(1,Math.min(8,choices.getInt("count",2)));String selected=choices.getString("doneness","Wachsweich");
        body.addView(text("Deine Auswahl für später",23,Ui.MUTED,false));gap(body,18);LinearLayout amount=new LinearLayout(this);amount.setGravity(Gravity.CENTER_VERTICAL);TextView value=text(count+" Eier",38,Ui.INK,true);value.setGravity(Gravity.CENTER);value.setTag("eggs.count");
        Button less=button("−","eggs.less",()->{choices.edit().putInt("count",Math.max(1,count-1)).apply();render();},false),more=button("+","eggs.more",()->{choices.edit().putInt("count",Math.min(8,count+1)).apply();render();},false);less.setEnabled(count>1);more.setEnabled(count<8);amount.addView(less,new LinearLayout.LayoutParams(dp(80),dp(80)));amount.addView(value,new LinearLayout.LayoutParams(0,dp(80),1));amount.addView(more,new LinearLayout.LayoutParams(dp(80),dp(80)));body.addView(amount);gap(body,28);
        body.addView(text("Gargrad",24,Ui.INK,true));gap(body,12);LinearLayout grades=new LinearLayout(this);grades.setTag("eggs.doneness");for(String grade:new String[]{"Weich","Wachsweich","Mittel","Hart"}){Button choice=button(grade,"eggs.grade."+grade,()->{choices.edit().putString("doneness",grade).apply();render();},false);choice.setTextSize(20);choice.setSelected(grade.equals(selected));choice.setBackground(shape(grade.equals(selected)?Ui.SOFT:Ui.CARD,24,grade.equals(selected)));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(78),1);size.setMargins(dp(3),0,dp(3),0);grades.addView(choice,size);}body.addView(grades);gap(body,32);
        body.addView(text("Automatisches Eierkochen wird noch angebunden",25,Ui.INK,true));gap(body,12);body.addView(text("Heizen mit stillstehendem Messer ist in den manuellen Einstellungen verfügbar. Für das automatische Eierprogramm fehlen noch geprüfte Wassermengen und Garzeiten. Deine Eieranzahl und dein Gargrad bleiben gespeichert.",23,Ui.MUTED,false));
    }

    private int[] defaults(String id){return FunctionSetup.get(id).defaults();}
    /** Assistant selection prepares the direction; physical confirmation applies live values. */
    private void toggleDirection(){if(page.equals("function")&&(FunctionSetup.get(selectedMode).lockedDirection||stirring!=null&&stirring.value()>40))return;reverse=!reverse;if(page.equals("home"))homeReverse=reverse;if(page.equals("step")&&program()!=null){JSONObject step=program().optJSONArray("steps").optJSONObject(currentStep);if(step!=null)settingsRemoveDirectionOverride(step.optString("id"));}renderDirection();}
    private void renderDirection(){if(page.equals("home")&&homeNumbers!=null){homeNumbers.directionChanged();tools.invalidate();return;}View v=body.findViewWithTag("direction-label");if(v instanceof ImageButton){ImageButton button=(ImageButton)v;boolean motorOff=stirring!=null&&stirring.value()==0;String label=motorOff?"Motor aus":reverse?"Rühren":"Messer";button.setEnabled(!motorOff&&(temperature==null||temperature.value()<=150)&&(!page.equals("function")||!FunctionSetup.get(selectedMode).lockedDirection&&stirring.value()<=40));button.setContentDescription(label);button.setImageDrawable(new ModeIcon(reverse?"stir":"blade"));button.setSelected(reverse);tools.invalidate();}}
    private void prepareControl(){try{int signed=stirring.value()*(reverse?-1:1);new ControlValues(duration.value(),temperature.value(),signed);startActivity(new Intent(this,ControlActivity.class).putExtra("preparedSeconds",duration.value()).putExtra("preparedTemperature",temperature.value()).putExtra("preparedSpeed",signed).putExtra("stirringAttachment",usesStirrer()));}catch(IllegalArgumentException e){showDialog(new AlertDialog.Builder(this).setTitle("Dieses Programm ist noch nicht freigegeben").setMessage("Diese Kombination passt zu keinem angebundenen Geräteprofil. Deine Auswahl bleibt erhalten.").setPositiveButton("Verstanden",null));}}
    private void pantry(){heading("Was ist da?");body.addView(text("Dein Vorrat ist der Anfang des nächsten guten Gerichts.",22,Ui.MUTED,false));gap(body,18);body.addView(button("Vorrat fotografieren","pantry.photo",()->chat(true),true));gap(body,22);JSONObject data=new KitchenStore(this).snapshot();JSONArray items=data.optJSONArray("pantry");if(items==null||items.length()==0){body.addView(text("Noch nichts erfasst",26,Ui.INK,true));body.addView(text("Teile ein Foto vom Handy oder ergänze Lebensmittel mit dem Drehknopf und der Tastatur.",21,Ui.MUTED,false));}else for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));ImageView photo=new ImageView(this);IngredientPortraits.bind(this,photo,item.optString("name"));row.addView(photo,new LinearLayout.LayoutParams(dp(84),dp(84)));LinearLayout copy=col();copy.addView(text(item.optString("name"),24,Ui.INK,true));copy.addView(text(item.optString("amount").isEmpty()?"Menge offen":item.optString("amount")+" "+item.optString("unit"),18,Ui.MUTED,false));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(18);row.addView(copy,cp);target(row,"pantry."+item.optString("id"),item.optString("name"),()->showDialog(new AlertDialog.Builder(this).setTitle(item.optString("name")).setItems(new String[]{"Angaben bearbeiten","Aufgebraucht"},(d,w)->{if(w==0){StockEditor.show(this,item,()->go("pantry"));return;}try{new KitchenStore(this).removeStock(item.optString("id"));go("pantry");}catch(Exception e){error(e);}})));body.addView(row);gap(body,12);}gap(body,18);body.addView(button("Lebensmittel ergänzen","pantry.add",()->new KitchenPages(this,legacyNavigation()).stockDialog(),false));gap(body,10);body.addView(button("Was kann ich daraus kochen?","pantry.cook",()->chat(false),false));}

    private void chat(boolean photo){if(!photo){openAssistant();return;}if(assistantDialog!=null)assistantDialog.dismiss();assistantDialog=CookingAssistant.showPhotos(this,tools,this::assistantClosed);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==43){acceptWeighingResult(result,data);return;}if(request==42){if(data==null||current==null||!accountUid.equals(new CloudSession(this).account().optString("uid"))||!accountUid.equals(data.getStringExtra("ownerUid"))||!current.optString("programDigest").equals(data.getStringExtra("programDigest")))return;if(data!=null)cookingModified|=data.getBooleanExtra("modifiedRecipe",true);if(result==RESULT_OK&&data!=null){completedSteps.add(data.getStringExtra("stepId"));render();}return;}if(request==41){if(result==RESULT_OK&&data!=null&&data.hasExtra("programVariant")){try{if(!data.getStringExtra("ownerUid").equals(new CloudSession(this).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert");JSONObject variant=new JSONObject(data.getStringExtra("programVariant")),card=MachineProgram.card(variant);if(variant.optBoolean("private")){new MachineProgramStore(this).save(variant,data.getStringExtra("ownerUid"));readPrivateCache();}else{readyCatalogue.put(card);getSharedPreferences("public-catalogue",0).edit().putString("machineItems",readyCatalogue.toString()).apply();}openRecipe(card);}catch(Exception e){error(e);}return;}if(result==RESULT_OK&&data!=null&&data.hasExtra("uiRequest")){try{JSONObject reply=tools.rpc(new JSONObject(data.getStringExtra("uiRequest")));if(reply.has("error"))Toast.makeText(this,reply.getJSONObject("error").optString("message"),Toast.LENGTH_LONG).show();}catch(Exception e){error(e);}return;}if(result==RESULT_OK&&data!=null&&data.hasExtra("recipeId")){for(Recipe recipe:KitchenRecipes.all(this))if(recipe.id.equals(data.getStringExtra("recipeId"))){openImported(recipe);return;}}if(result==RESULT_OK&&data!=null&&data.hasExtra("tab")){String tab=data.getStringExtra("tab");if(Arrays.asList("recipes","home","pantry","modes").contains(tab))page=tab;}render();}}
    private void legacy(String screen){go(screen.equals("kitchen")?"modes":screen);}
    private void registerInteractive(View root,String group){ArrayList<View> views=new ArrayList<>();collectInteractive(root,views);knob.replaceGroup(group,views);tools.reset();captureTools(surface);}
    private void collectInteractive(View view,List<View> result){if(view.isClickable()||view instanceof Button||view instanceof EditText){view.setContentDescription(view.getContentDescription()==null?view instanceof TextView?((TextView)view).getText():"Auswahl":view.getContentDescription());if(view.getTag()==null)view.setTag("ui."+view.getContentDescription());result.add(view);return;}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collectInteractive(((ViewGroup)view).getChildAt(i),result);}
    private void captureTools(View view){if(view.getTag() instanceof String){View item=view;tools.action((String)view.getTag(),view,()->item.performClick());}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)captureTools(((ViewGroup)view).getChildAt(i));}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&wifiPanel!=null){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)wifiPanel.scan();else wifiPanel.refresh();registerInteractive(body,"wifi");}}

    private void showDialog(AlertDialog.Builder builder){AlertDialog dialog=builder.create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void readPlanCache(){try{kalomatikPlan=new KalomatikPlans(this).getItems();}catch(Exception e){kalomatikPlan=new JSONArray();}}
    private void loadPlan(){if(destroyed||planWorker.isShutdown()||planLoading)return;if(new CloudSession(this).account().optString("uid").isEmpty()){if(page.equals("plan"))new KitchenPages(this,legacyNavigation()).account();return;}planLoading=true;planError="";String owner=accountUid;long epoch=++planEpoch;if(page.equals("plan"))render();planWorker.execute(()->{try{KalomatikPlans plans=new KalomatikPlans(this);JSONObject snapshot=plans.refresh();int turns=1;while(snapshot.optBoolean("pending")&&turns++<4)snapshot=plans.refresh();JSONObject completed=snapshot;main.post(()->{if(destroyed||epoch!=planEpoch||!owner.equals(new CloudSession(this).account().optString("uid")))return;planLoading=false;planRefreshed=SystemClock.elapsedRealtime();JSONArray next=completed.optJSONArray("items");boolean changed=!kalomatikPlan.toString().equals(next.toString());kalomatikPlan=next;if(completed.optBoolean("pending")){planError="Weitere Änderungen werden automatisch geladen …";main.postDelayed(()->{if(!destroyed&&screenResumed&&epoch==planEpoch&&owner.equals(new CloudSession(this).account().optString("uid"))&&(page.equals("plan")||page.equals("home")))loadPlan();},30000);}if(page.equals("plan"))render();});}catch(Exception error){main.post(()->{if(destroyed||epoch!=planEpoch||!owner.equals(new CloudSession(this).account().optString("uid")))return;planLoading=false;planError=error.getMessage()==null?"Dein Kochplan konnte gerade nicht geladen werden.":error.getMessage();if(page.equals("plan"))render();});}});}

    private void planPage(){
        heading("Dein Kochplan");java.time.LocalDate today=java.time.LocalDate.now();try{java.time.LocalDate day=java.time.LocalDate.parse(selectedPlanDate);if(day.isBefore(today)||day.isAfter(today.plusDays(9)))selectedPlanDate=today.toString();}catch(Exception invalid){selectedPlanDate=today.toString();}
        PlanDayStrip strip=new PlanDayStrip(this,selectedPlanDate,date->{selectedPlanDate=date;render();});body.addView(strip);registerInteractive(strip,"plan.days");gap(body,16);body.addView(textLink(planLoading?"Wird mit Kalomatik abgeglichen …":"Mit Kalomatik aktualisieren  ↻","plan.refresh",this::loadPlan));if(!planError.isEmpty()){body.addView(text(planError,21,Ui.MUTED,false));gap(body,16);}
        section(java.time.LocalDate.parse(selectedPlanDate).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)),null,null);boolean any=false;
        for(int i=0;i<kalomatikPlan.length();i++){JSONObject meal=kalomatikPlan.optJSONObject(i);if(!selectedPlanDate.equals(meal.optString("date")))continue;any=true;String subtitle=meal.optString("time")+(meal.optString("time").isEmpty()?"":" · ")+meal.optJSONArray("ingredients").length()+" Zutaten";settingsRow("recipes",meal.optString("title"),subtitle,"plan.meal."+meal.optString("id"),()->openPlannedMeal(meal));JSONObject nutrition=meal.optJSONObject("knownNutrition");if(nutrition!=null)body.addView(NutritionSummary.detail(this,nutrition,"Je Portion"));}
        JSONArray local=new KitchenStore(this).snapshot().optJSONArray("plans");List<Recipe> recipes=KitchenRecipes.all(this);for(int i=0;local!=null&&i<local.length();i++){JSONObject item=local.optJSONObject(i);if(!selectedPlanDate.equals(item.optString("date")))continue;for(Recipe recipe:recipes)if(recipe.id.equals(item.optString("recipeId"))){any=true;settingsRow("recipes",recipe.title,item.optInt("servings",recipe.servings)+" Portionen · auf dieser Maschine geplant","plan.localmeal."+recipe.id,()->openImported(recipe,item.optInt("servings",recipe.servings)));break;}}
        if(!any){body.addView(text(planLoading?"Deine Gerichte werden geladen …":"Für diesen Tag ist noch kein Gericht geplant.",25,Ui.MUTED,false));gap(body,18);body.addView(text("Plane auf deinem Handy in Kalomatik. Deine Auswahl erscheint hier automatisch im selben Konto.",22,Ui.MUTED,false));}gap(body,26);body.addView(textLink("Deine Einkaufsliste","plan.local",()->go("local-plan")));
    }

    private void openPlannedMeal(JSONObject planned){try{JSONObject linked=new KalomatikPlanWriter(this).preparedCard(planned);if(linked!=null){openRecipe(linked);return;}PlanRecipeAdapter.Result adapted=PlanRecipeAdapter.adapt(planned,accountUid);if(!adapted.ready){JSONObject card=importedCard(adapted.recipe);card.put("planIncomplete",true).put("planSourceDate",planned.optString("date")).put("planSummary",adapted.summary).put("planMissing",android.text.TextUtils.join("\n",adapted.missingDetails));JSONArray sourceSteps=new JSONArray();for(int i=0;i<adapted.recipe.steps.length();i++)sourceSteps.put(new JSONObject().put("title","Schritt "+(i+1)).put("instruction",adapted.recipe.steps.getString(i)));card.put("guide",new JSONObject().put("steps",sourceSteps));openRecipe(card);return;}Recipe recipe=adapted.recipe;KitchenRecipes.savePrivate(this,recipe,accountUid);Double desired=adapted.requestedCookingServings;int portions=desired==null?0:desired.intValue();if(desired==null||portions<1||portions>20||desired.doubleValue()!=portions){showDialog(new AlertDialog.Builder(this).setTitle("Kochmenge wählen").setMessage("Wähle die Menge für diesen Maschinenlauf. Die Originalmengen aus Kalomatik bleiben die Basis.").setItems(portionLabels(),(d,w)->openImported(recipe,w+1)));}else openImported(recipe,portions);}catch(Exception e){error(e);}}

    private void firstRun(){if(destroyed||isFinishing()||!page.equals("home")||ControlClient.hasActiveSession())return;if(!new CloudSession(this).account().optString("uid").isEmpty()){getSharedPreferences("setup",0).edit().putBoolean("seen",true).apply();return;}if(getSharedPreferences("setup",0).getBoolean("seen",false)||setupDialog!=null&&setupDialog.isShowing())return;startActivity(new Intent(this,SetupActivity.class));}
    private String[] portionLabels(){String[] labels=new String[20];for(int i=0;i<20;i++)labels[i]=(i+1)+(i==0?" Portion":" Portionen");return labels;}
    private void settings(){go("settings");}
    private void loadCategoryPreviews(){
        if(categoryPreviewsLoading||sourceLoading||!browseCatalogue||!query.isEmpty()||recipeCategories.all().isEmpty())return;
        List<String> missing=new ArrayList<>();for(String id:recipeCategories.selected(accountUid))if(!categoryPreviewsLoaded.contains(id))missing.add(id);if(missing.isEmpty())return;
        categoryPreviewsLoading=true;long epoch=sourceEpoch;
        worker.execute(()->{JSONArray merged=new JSONArray();List<String> loaded=new ArrayList<>();for(String id:missing){if(destroyed)break;try{JSONObject response=recipeOffline.page("","",id);JSONArray items=response.getJSONArray("items");for(int i=0;i<items.length();i++)merged.put(items.getJSONObject(i));loaded.add(id);}catch(Exception ignored){}}
            main.post(()->{categoryPreviewsLoading=false;if(destroyed||epoch!=sourceEpoch||!query.isEmpty()||!activeRecipeCategory.isEmpty())return;categoryPreviewsLoaded.addAll(loaded);LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();for(JSONArray list:Arrays.asList(catalogue,merged))for(int i=0;i<list.length();i++){JSONObject row=list.optJSONObject(i);if(row!=null&&row.optString("id").matches("[a-f0-9]{64}")&&row.optJSONArray("ingredients")!=null)unique.putIfAbsent(key(row),row);}catalogue=new JSONArray();for(JSONObject row:unique.values())catalogue.put(row);getSharedPreferences("public-catalogue",0).edit().putString("items",catalogue.toString()).apply();if(page.equals("recipes")&&!loaded.isEmpty())render();});
        });
    }
    private void loadRecipeCategories(){worker.execute(()->{try{JSONObject result=recipeOffline.categories();main.post(()->{if(destroyed)return;try{recipeCategories.update(result);if(page.equals("recipes")||page.equals("recipe-categories"))render();}catch(JSONException ignored){}});}catch(Exception unavailable){/* Keep the last public category snapshot offline. */}});}
    private List<JSONObject> categoryRows(String id,List<JSONObject> values){List<JSONObject> result=new ArrayList<>();for(JSONObject category:recipeCategories.all())if(id.equals(category.optString("id")))for(JSONObject value:values)if(recipeCategories.contains(category,value))result.add(value);return result;}
    private void recipeShelves(List<JSONObject> values){
        if(browseCatalogue)loadCategoryPreviews();
        if(!query.isEmpty())return;List<String> selected=recipeCategories.selected(accountUid);int shown=0;
        for(String id:selected)for(JSONObject category:recipeCategories.all())if(id.equals(category.optString("id"))){List<JSONObject> matches=categoryRows(id,values);if(!matches.isEmpty()){recipeShelf(id,category.optString("title"),matches);shown++;}}
        List<JSONObject> unassigned=new ArrayList<>();for(JSONObject value:values){boolean assigned=false;for(JSONObject c:recipeCategories.all())if(recipeCategories.contains(c,value)){assigned=true;break;}if(!assigned)unassigned.add(value);}
        if(!unassigned.isEmpty())recipeShelf("unassigned",recipeCategories.all().isEmpty()?"Deine Rezepte":"Weitere Rezepte",unassigned);
        else if(shown==0)body.addView(text("Für deine Kategorien sind hier noch keine Rezepte geladen. Wähle in den Einstellungen weitere Rezeptkategorien aus.",22,Ui.MUTED,false));
    }
    private void recipeShelf(String id,String title,List<JSONObject> values){
        TextView heading=text(title+"  ›",25,Ui.INK,true);heading.setGravity(Gravity.CENTER_VERTICAL);
        target(heading,"recipes.all."+id,title+", Kategorie öffnen",()->{activeRecipeCategory=id.equals("unassigned")?"":id;query="";recipePage=0;homeCollectionFilter=id.equals("unassigned")?"Für dich":"";if(browseCatalogue&&!activeRecipeCategory.isEmpty())loadSourceRecipes("",false);else render();});heading.setBackground(shape(Color.TRANSPARENT,12,true));body.addView(heading,new LinearLayout.LayoutParams(-1,dp(46)));
        int width=Math.round((getResources().getDisplayMetrics().widthPixels-dp(56))*.44f);String positionKey=accountUid+":"+browseCatalogue+":"+id;
        StringBuilder signature=new StringBuilder().append(width).append(':').append(Ui.dark);
        List<JSONObject> visible=values.subList(0,Math.min(12,values.size()));for(JSONObject recipe:visible)signature.append(recipe.toString()).append(isFavorite(recipe));
        CachedShelf cached=recipeShelfCache.get(positionKey);RecipeShelf shelf;
        if(cached!=null&&cached.signature.equals(signature.toString())){shelf=cached.view;if(shelf.getParent()!=null)((ViewGroup)shelf.getParent()).removeView(shelf);}
        else{shelf=new RecipeShelf(this,width,pos->{shelfPositions.put(positionKey,pos);lastRecipeMotionAt=SystemClock.uptimeMillis();});shelf.setContentDescription(title);shelf.setTag("recipes.shelf."+id);for(JSONObject recipe:visible)shelf.card(recipePhotoCard(recipe),width);recipeShelfCache.put(positionKey,new CachedShelf(signature.toString(),shelf));while(recipeShelfCache.size()>16)recipeShelfCache.remove(recipeShelfCache.keySet().iterator().next());}
        body.addView(shelf,new LinearLayout.LayoutParams(-1,dp(230)));shelf.restore(shelfPositions.getOrDefault(positionKey,0));gap(body,8);
    }
    private void recipeCategorySettings(){
        heading("Deine Rezeptkategorien");body.addView(text("Wähle die Reihen für deine Rezeptübersicht. Die Kategorien stammen aus der gemeinsamen Rezeptdatenbank.",22,Ui.MUTED,false));gap(body,16);
        List<String> selected=recipeCategories.selected(accountUid);List<JSONObject> ordered=new ArrayList<>();for(String id:selected)for(JSONObject c:recipeCategories.all())if(id.equals(c.optString("id")))ordered.add(c);for(JSONObject c:recipeCategories.all())if(!selected.contains(c.optString("id")))ordered.add(c);
        if(ordered.isEmpty()){body.addView(text("Kategorien sind noch nicht geladen. Deine gespeicherten Rezepte bleiben verfügbar.",22,Ui.MUTED,false));body.addView(button("Erneut laden","categories.retry",this::loadRecipeCategories,false));}
        for(JSONObject c:ordered){String id=c.optString("id");boolean enabled=selected.contains(id);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);View toggle=button((enabled?"✓  ":"○  ")+c.optString("title"),"categories.toggle."+id,()->{List<String> next=recipeCategories.selected(accountUid);if(next.contains(id))next.remove(id);else next.add(id);recipeCategories.save(accountUid,next);render();},false);row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));if(enabled){View up=button("↑","categories.up."+id,()->{List<String> next=recipeCategories.selected(accountUid);int index=next.indexOf(id);if(index>0){Collections.swap(next,index,index-1);recipeCategories.save(accountUid,next);render();}},false);up.setEnabled(selected.indexOf(id)>0);row.addView(up,new LinearLayout.LayoutParams(dp(68),-2));}body.addView(row);gap(body,8);}
        body.addView(button("Standardauswahl","categories.reset",()->{recipeCategories.reset(accountUid);render();},false));body.addView(button("Rezepte ansehen","categories.done",()->{activeRecipeCategory="";go("recipes");},true));
    }
    private void settingsPage(){
        heading("Einstellungen");JSONObject account=new CloudSession(this).account();String name=account.optString("displayName","");if(name.isEmpty())name=account.optString("uid").isEmpty()?"Mit Kalomatik anmelden":"Dein Kalomatik-Konto";
        settingsRow("person",name,account.optString("email","Rezepte und Kochplan auf Handy und Maschine"),"settings.account",()->new KitchenPages(this,legacyNavigation()).account());
        settingsRow("recipes","Rezeptkategorien","Reihen auswählen und anordnen","settings.recipeCategories",()->go("recipe-categories"));
        settingsRow("wifi","WLAN","Netzwerk wählen und verbinden","settings.wifi",()->go("wifi"));
        settingsRow("person","Verbundene Handys","Gleichberechtigte Handys hinzufügen oder entfernen","settings.phones",()->go("receive"));
        settingsRow("wifi","Einrichtung am Handy","WLAN einrichten, Konto verbinden oder lokal starten","settings.setup",()->startActivity(new Intent(this,SetupActivity.class)));
        settingsRow("person","Dein Assistent","Vorlieben, Ernährung und persönliche Hinweise","settings.preferences",()->new KitchenPages(this,legacyNavigation()).preferences());
        gap(body,24);body.addView(text("Kalomatik Pro · Deine Küche ist verbunden",17,Ui.MUTED,false));
    }

    private void themeButtons(LinearLayout host){LinearLayout choices=new LinearLayout(this);choices.setGravity(Gravity.CENTER);choices.setPadding(dp(6),dp(4),dp(6),dp(4));choices.setBackground(shape(Ui.SOFT,32,false));for(String theme:new String[]{"light","dark"}){View choice=iconButton(theme.equals("light")?"sun":"moon",theme.equals("light")?"Helle Darstellung":"Dunkle Darstellung","appearance."+theme,()->{getSharedPreferences("appearance",0).edit().putString("theme",theme).apply();setDrawer(false);render();});choice.setBackground(shape(Ui.dark==theme.equals("dark")?Ui.CARD:0,30,true));choices.addView(choice,new LinearLayout.LayoutParams(dp(60),dp(52)));}host.addView(choices,new LinearLayout.LayoutParams(-2,-2));}

    private void stepFacts(JSONObject step){for(StepFacts.Fact fact:StepFacts.from(step.optString("instruction"))){if(step.optString("kind").equals("rest")&&step.optJSONObject("settings")!=null&&step.optJSONObject("settings").optInt("durationSeconds")>0&&fact.icon.equals("timer"))continue;gap(body,14);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageDrawable(new InterfaceIcon(fact.icon,Ui.ACCENT));row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView copy=text(fact.text,26,Ui.INK,true);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.leftMargin=dp(18);row.addView(copy,size);body.addView(row);}}
    private final Runnable timerAlarms=new Runnable(){public void run(){if(destroyed||restTimers.isEmpty())return;long now=SystemClock.elapsedRealtime();boolean rang=false;for(java.util.Iterator<Map.Entry<String,Long>> it=restTimers.entrySet().iterator();it.hasNext();){Map.Entry<String,Long> timer=it.next();if(timer.getValue()<=now){if(timer.getKey().equals(restTimerKey)&&duration!=null){duration.countdown(0);duration.setEnabled(true);timerActionLabel("Neu starten");}it.remove();rang=true;}}if(rang){android.media.ToneGenerator tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM,85);tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1000);main.postDelayed(tone::release,1200);Toast.makeText(ApplianceActivity.this,"Timer beendet",Toast.LENGTH_LONG).show();checkpointCook();}if(!restTimers.isEmpty())main.postDelayed(this,250);}};
    private void settingsRow(String icon,String title,String subtitle,String id,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(20),dp(18),dp(20));ImageView glyph=new ImageView(this);glyph.setImageDrawable(new InterfaceIcon(icon,Ui.MUTED));row.addView(glyph,new LinearLayout.LayoutParams(dp(28),dp(28)));LinearLayout copy=col();copy.addView(text(title,23,Ui.INK,true));gap(copy,4);copy.addView(text(subtitle,18,Ui.MUTED,false));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(20);cp.rightMargin=dp(12);row.addView(copy,cp);ImageView arrow=new ImageView(this);arrow.setImageDrawable(new InterfaceIcon("next",Ui.MUTED));row.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(20)));target(row,id,title,action);body.addView(row);gap(body,10);}
    private void recipePhoto(){try{startActivityForResult(new Intent(this,KitchenChatActivity.class).putExtra("attachPhoto",true).putExtra("recipePhoto",true).putExtra("initialMessage","Das ist ein abfotografiertes Rezept. Lies die Zutaten, Mengen, Portionen und Anleitung und bereite es für meine Küchenmaschine auf. Frage mich bei unleserlichen Angaben.").putExtra("uiScene",tools.scene().toString()),41);}catch(Exception e){error(e);}}

    private void error(Exception e){Toast.makeText(this,e.getMessage()==null?"Das hat gerade nicht funktioniert.":e.getMessage(),Toast.LENGTH_LONG).show();}
    private void readCache(){try{String saved=getSharedPreferences("public-catalogue",0).getString("items","");if(!saved.isEmpty())catalogue=new JSONArray(saved);String machine=getSharedPreferences("public-catalogue",0).getString("machineItems","");if(!machine.isEmpty())readyCatalogue=new JSONArray(machine);}catch(Exception ignored){}}
    // Home contains machine controls only. Recipe/plan replies must not rebuild
    // its WebView or reset an established scale baseline during a weigh-in.
    private void loadCatalogue(){loadSourceRecipes("",false);}
    private void loadSourceRecipes(String search,boolean append){if(append&&sourceLoading)return;if(!append&&!sourceQuery.equals(search)){catalogue=new JSONArray();sourceCursor="";}sourceError="";sourceLoading=true;loading=true;long epoch=++sourceEpoch;String cursor=append?sourceCursor:"",sourceCategory=activeRecipeCategory;sourceQuery=search;if(page.equals("recipes")&&browseCatalogue)render();worker.execute(()->{if(destroyed||epoch!=sourceEpoch)return;try{JSONObject result=recipeOffline.page(search,cursor,sourceCategory);JSONArray incoming=result.getJSONArray("items");String next=result.optString("nextCursor","");if(next.equals("null"))next="";final String nextCursor=next;main.post(()->{if(destroyed||epoch!=sourceEpoch)return;LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();if(search.isEmpty())try{JSONArray stored=new JSONArray(getSharedPreferences("public-catalogue",0).getString("items","[]"));for(int i=0;i<stored.length();i++){JSONObject row=stored.optJSONObject(i);if(row!=null)unique.put(key(row),row);}}catch(JSONException ignored){}if(append)for(int i=0;i<catalogue.length();i++){JSONObject row=catalogue.optJSONObject(i);if(row!=null)unique.put(key(row),row);}for(int i=0;i<incoming.length();i++){JSONObject row=incoming.optJSONObject(i);if(row!=null&&row.optString("id").matches("[a-f0-9]{64}")&&row.optJSONArray("ingredients")!=null)unique.put(key(row),row);}JSONArray updated=new JSONArray();for(JSONObject row:unique.values())updated.put(row);if(!updated.toString().equals(catalogue.toString()))catalogue=updated;sourceCursor=nextCursor;sourceLoading=loading=false;if(search.isEmpty())getSharedPreferences("public-catalogue",0).edit().putString("items",catalogue.toString()).apply();if(Arrays.asList("recipes","favorites").contains(page))render();warmMachineRecipes();});}catch(Exception e){main.post(()->{if(destroyed||epoch!=sourceEpoch)return;sourceLoading=loading=false;sourceError="Die Rezeptwelt ist gerade nicht erreichbar. Deine gespeicherten Maschinenrezepte bleiben verfügbar.";if(page.equals("recipes"))render();else if(loadingLabel!=null)loadingLabel.setText(sourceError);});}});}

}

