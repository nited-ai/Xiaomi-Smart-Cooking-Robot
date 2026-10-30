package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import com.kitchenos.connect.control.ControlState;
import java.io.ByteArrayInputStream;
import org.json.JSONObject;

/** Local photographic Canvas2D presentation (legacy class/origin name).
 * No controller access or web-origin navigation.
 * Native cup hit testing and existing confirmation/interlocks remain the owner. */
public final class HomeVesselWebGLView extends FrameLayout {
    private static final String ORIGIN="https://c3.local/";
    private final HomeVesselView fallback;
    private final PotHeatView heatWarning;
    private WebView web;
    private final Handler lifecycle=new Handler(Looper.getMainLooper());
    private final long createdAt=SystemClock.elapsedRealtime();
    private boolean interiorPainted,presented;
    private Runnable loadTimeout;
    private boolean retainedForNavigation;
    private final Runnable releaseDetached=()->{if(!isAttachedToWindow()&&!retainedForNavigation)disposeWeb();};
    /** Only the Activity's single cached Home owns this lease; no offscreen drawing. */
    public void retainForNavigation(boolean retain){retainedForNavigation=retain;lifecycle.removeCallbacks(releaseDetached);if(!retain&&!isAttachedToWindow())disposeWeb();}
    private final RectF cupHit=new RectF();
    private HomeVesselState state=HomeVesselState.from(new ControlState(),0);
    private boolean ready,reverse,stirrer,reveal,running,active,fresh,hostVisible=true;
    private float level=1;
    private long lastSent=-10000;
    private String packet="";
    private HomeVesselState.Lid sentLid;
    private String toolOverride;
    private boolean basket,heatFresh,water;
    private int fillStep=-1,temperature;
    private float fillFraction=-1;
    private boolean interacting,cupPrompt;private long cupStartAt;
    private android.animation.ValueAnimator potPulse;private int potProgress=-1;
    private final Runnable endInteraction=()->{interacting=false;send(false);};
    public HomeVesselWebGLView(Context context){
        super(context);setTag("home.bowl");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        fallback=new HomeVesselView(context);fallback.setOnClickListener(v->performClick());addView(fallback,new LayoutParams(-1,-1));
        fallback.setOnLongClickListener(v->performLongClick());
        heatWarning=new PotHeatView(context);
        createWeb();
        addView(heatWarning,new LayoutParams(-1,-1));
    }
    private void createWeb(){
        final Context context=getContext();
        try{
            web=new WebView(context){@Override public boolean onTouchEvent(MotionEvent e){return false;}};
            final WebView owner=web;
            web.setBackgroundColor(Color.TRANSPARENT);web.setAlpha(0);web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);web.setFocusable(false);
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);
            settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            web.addJavascriptInterface(new PresentationBridge(owner),"C3Android");
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                    String url=r.getUrl().toString();
                    if(!url.startsWith(ORIGIN))return empty();
                    String path=r.getUrl().getPath();if(path==null||path.contains("..")||path.contains("\\"))return empty();
                    try{String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".glb")?"model/gltf-binary":path.endsWith(".bin")?"application/octet-stream":path.endsWith(".png")?"image/png":"text/html";
                        return new WebResourceResponse(mime,path.endsWith(".glb")||path.endsWith(".bin")||path.endsWith(".png")?null:"UTF-8",context.getAssets().open("visuals/home-vessel-webgl"+path));
                    }catch(Exception e){return empty();}
                }
                @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())failed();}
                @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){if(web==v)failed();removeView(v);v.destroy();if(web==v)web=null;return true;}
            });
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR){android.util.Log.e("KitchenOSWebGL",m.message());lifecycle.post(()->{if(web==owner)failed();});}return true;}});
            addView(web,new LayoutParams(-1,-1));if(heatWarning!=null)heatWarning.bringToFront();web.loadUrl(ORIGIN+"index.html?embedded=1&variant=transparent");
            WebView loading=web;loadTimeout=()->{if(web==loading&&!ready)failed();};lifecycle.postDelayed(loadTimeout,10000);
        }catch(Exception error){android.util.Log.w("KitchenOSWebGL","Photo fallback",error);failed();}
    }
    private static WebResourceResponse empty(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void disposeWeb(){
        if(loadTimeout!=null)lifecycle.removeCallbacks(loadTimeout);
        ready=presented=false;cupHit.setEmpty();packet="";fallback.setVisibility(VISIBLE);
        if(web!=null){WebView old=web;web=null;old.stopLoading();old.removeJavascriptInterface("C3Android");removeView(old);old.destroy();}
    }
    private void failed(){ready=presented=false;cupHit.setEmpty();if(web!=null){web.setAlpha(0);web.onPause();}fallback.setVisibility(VISIBLE);}
    public boolean isModelReady(){return ready;}
    public HomeVesselState presentation(){return state;}
    public void mode(boolean reverse,boolean stirrer,boolean reveal){if(this.reverse==reverse&&this.stirrer==stirrer&&this.reveal==reveal)return;this.reverse=reverse;this.stirrer=stirrer;this.reveal=reveal;fallback.mode(reverse,stirrer,reveal);send(false);}
    /** Leave GPU time to the native number wheels while the finger is down. */
    public void interaction(boolean down){lifecycle.removeCallbacks(endInteraction);if(down){if(!interacting){interacting=true;send(false);}}else lifecycle.postDelayed(endInteraction,180);}
    public void requireCup(boolean required){if(cupPrompt==required)return;cupPrompt=required;fallback.cupPrompt(required);send(false);}
    public void level(float value){if(level==value)return;level=value;send(false);}
    public void tool(String value){toolOverride=value;send(false);}
    public void water(boolean value){if(water==value)return;water=value;send(false);}
    public void basket(boolean value){basket=value;send(false);}
    public void weight(Integer grams){int next=HomeWeightVisuals.fillStep(grams);float fraction=HomeWeightVisuals.fillFraction(grams);if(next==fillStep&&fraction==fillFraction)return;fillStep=next;fillFraction=fraction;fallback.fillFraction(fraction);send(false);}
    public void update(ControlState live,long now){update(live,now,null);}
    public void update(ControlState live,long now,HomeCupState cup){
        showPotProgress(live,now);
        HomeVesselState next=HomeVesselState.from(live,now,cup);if(next.lid!=state.lid)android.util.Log.i("KitchenOSCover","picture="+next.lid+"; cover="+live.coverOpen+"; statusAgeMs="+(now-live.statusAt)+"; infoAgeMs="+(now-live.infoAt));fresh=live.foreground&&live.fresh(now);
        // A delayed UI sample stops animation but must not lift a cup that was
        // explicitly confirmed for this owned run. Real open/fault events win.
        boolean hold=!fresh&&live.foreground&&live.ownRun&&!live.uncertainRun&&!live.deviceFaultLatched&&live.error==0&&live.coverOpen==0&&cup!=null&&cup.displayedInserted()&&state.lid==HomeVesselState.Lid.CLOSED;
        if(!hold)state=next;
        running=fresh&&live.ownRun&&live.motorStatus!=0&&!live.stopping&&live.state!=3&&state.lid==HomeVesselState.Lid.CLOSED;
        active=fresh&&live.ownRun&&!live.stopping&&live.state!=3&&!live.potMissing();temperature=fresh?live.temperature:0;
        com.kitchenos.connect.control.TemperatureHistory history=com.kitchenos.connect.control.ControlClient.get(getContext()).temperatureHistory;
        heatFresh=live.foreground&&live.connected&&history.fresh(now);
        temperature=heatFresh||history.hot()?history.temperature():0;
        fallback.temperature(temperature);
        heatWarning.completion(com.kitchenos.connect.control.ControlClient.get(getContext()).hotEndAt());
        fallback.update(live,now,cup);String description="Mixtopf. "+(water?"Schematische Wasserfüllung. ":"")+state.hint()+". "+(reverse?"Linkslauf":"Rechtslauf");if(!description.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(description);send(false);
        fallback.setVisibility(presented?INVISIBLE:VISIBLE);if(web!=null)web.setAlpha(presented?1:0);
    }
    public void startHint(){fallback.startHint();}
    public void startCue(){cupStartAt=System.currentTimeMillis();fallback.startCue(cupStartAt);send(true);}
    private void stopPotPulse(){if(potPulse!=null){potPulse.cancel();potPulse=null;}potProgress=-1;setAlpha(1);}
    private void showPotProgress(ControlState live,long now){
        int next=live.potCheck.active()&&live.foreground&&live.fresh(now)?2:live.potCheck.required()?1:0;
        if(next==potProgress)return;stopPotPulse();potProgress=next;
        if(next==2&&isShown()&&android.animation.ValueAnimator.areAnimatorsEnabled()){
            potPulse=android.animation.ValueAnimator.ofFloat(.72f,1f);potPulse.setDuration(550);potPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);potPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);potPulse.addUpdateListener(v->setAlpha((float)v.getAnimatedValue()));potPulse.start();
        }else setAlpha(next==0?1:.72f);
    }
    private void send(boolean force){
        if(!ready||web==null)return;
        try{String selected=toolOverride==null?AccessorySelection.visualTool(getContext(),stirrer):toolOverride;boolean withBasket=selected.equals("basket")||basket&&!selected.equals("whisk")&&!selected.equals("steam");JSONObject o=new JSONObject();o.put("water",water);o.put("lid",state.lid.name());o.put("cup",state.cupInsertionCue);o.put("reverse",reverse);o.put("stirrer",stirrer);o.put("tool",selected.equals("basket")?"blade":selected);o.put("basket",withBasket);o.put("level",level);o.put("reveal",reveal);o.put("fillStep",fresh?fillStep:-1);o.put("fresh",fresh);o.put("running",running);o.put("active",active);o.put("temperature",temperature);o.put("heatFresh",heatFresh);o.put("reducedMotion",!android.animation.ValueAnimator.areAnimatorsEnabled());o.put("heatRetained",!heatFresh&&temperature>100);o.put("visible",hostVisible&&isShown());
            if(!presented&&fallback.hasWeighingPreview()){o.put("previewFill",fallback.previewFraction());o.put("previewOpening",fallback.previewOpening());}
            o.put("fillFraction",fresh?fillFraction:-1);o.put("interacting",interacting);o.put("cupPrompt",cupPrompt);o.put("cupStartAt",cupStartAt);String next=o.toString();long now=SystemClock.elapsedRealtime();
            if(!force&&next.equals(packet)&&(!(water||running||active||heatFresh&&temperature>=75)||now-lastSent<250))return;
            packet=next;lastSent=now;
            if(sentLid!=state.lid){sentLid=state.lid;final long sent=now;
                web.evaluateJavascript("(function(){if(!window.c3)return null;window.c3.updateNative("+next+");return window.c3.state().visualLid;})()",value->android.util.Log.i("KitchenOSCover","renderer="+value+"; bridgeDelayMs="+(SystemClock.elapsedRealtime()-sent)));
            }else web.evaluateJavascript("window.c3&&window.c3.updateNative("+next+")",null);
        }catch(Exception error){failed();}
    }
    private final class PresentationBridge {
        private final WebView owner;PresentationBridge(WebView owner){this.owner=owner;}
        @JavascriptInterface public void ready(){lifecycle.post(()->{if(web!=owner)return;ready=true;
            android.util.Log.i("KitchenOSWebGL","3D vorbereitet nach "+(SystemClock.elapsedRealtime()-createdAt)+" ms");
            // A slow first GLB load can finish after the photo-fallback timeout
            // paused this WebView. Resume its frame loop once the scene is ready.
            if(hostVisible)web.onResume();send(true);});}
        @JavascriptInterface public void presented(){lifecycle.post(()->{if(web!=owner||!ready||presented)return;presented=true;web.setAlpha(1);fallback.setVisibility(INVISIBLE);});}
        @JavascriptInterface public void interiorReady(int milliseconds){lifecycle.post(()->{if(web!=owner||interiorPainted)return;interiorPainted=true;android.util.Log.i("KitchenOSWebGL","Erster Innenausschnitt nach Anforderung: "+Math.max(0,milliseconds)+" ms");});}
        @JavascriptInterface public void cupBounds(double left,double top,double right,double bottom){
            lifecycle.post(()->{if(web!=owner||!ready)return;if(!Double.isFinite(left)||!Double.isFinite(top)||!Double.isFinite(right)||!Double.isFinite(bottom)||left<0||top<0||right>1||bottom>1||right<=left||bottom<=top){cupHit.setEmpty();return;}
                cupHit.set((float)left*getWidth(),(float)top*getHeight(),(float)right*getWidth(),(float)bottom*getHeight());});
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(isLongClickable()&&e.getActionMasked()==MotionEvent.ACTION_DOWN&&e.getY()>getHeight()*.22f)return super.onTouchEvent(e);
        if(!ready)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&(state.lid!=HomeVesselState.Lid.CLOSED||cupHit.isEmpty()||!cupHit.contains(e.getX(),e.getY())))return false;
        return super.onTouchEvent(e);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);hostVisible=visibility==VISIBLE;if(!hostVisible)stopPotPulse();if(web!=null){if(hostVisible)web.onResume();send(true);if(!hostVisible)web.onPause();}}
    @Override protected void onDetachedFromWindow(){stopPotPulse();hostVisible=false;interacting=false;lifecycle.removeCallbacks(endInteraction);send(true);if(web!=null)web.onPause();lifecycle.removeCallbacks(releaseDetached);lifecycle.postDelayed(releaseDetached,2000);super.onDetachedFromWindow();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();lifecycle.removeCallbacks(releaseDetached);hostVisible=true;if(web==null)createWeb();else web.onResume();send(true);}
}
