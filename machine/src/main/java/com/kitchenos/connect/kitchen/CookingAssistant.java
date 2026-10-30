package com.kitchenos.connect.kitchen;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.ui.*;
import java.io.File;
import org.json.*;

/** Owned dialog: the control Activity stays resumed and keeps its existing watchdog.
 * Leaving the app still pauses that Activity and disconnects exactly as before. */
public final class CookingAssistant {
    private final Activity activity;
    private final ApplianceTools tools;
    private final ControlClient controls;
    private final String uid;
    private final Dialog dialog;private AlertDialog memoryDialog;
    private final RotaryController knob;
    private final AssistantConversation session;
    private final Runnable sessionChanged=this::refreshSession;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout messages,photoRow;
    private final ScrollView scroll;
    private final EditText editor;
    private final ImageButton send,attach,close;
    private final AssistantVoiceInput voice;private final AssistantAvatar avatar;private boolean listening;private final boolean pantryReminder;private Button stop;private TextView machineStatus;
    private final ApplianceKeyboard keyboard;
    private final FrameLayout overlay;
    private final LinearLayout conversation,thread,input;private FrameLayout identity;private LinearLayout header;private float anchorX=16,anchorY=28,dragX,dragY,dragAnchorX,dragAnchorY;private boolean headerDragging,identityLeft;
    private final FrostedPanel frost;
    private final View launcher;
    private final View protectedFooter;
    private final View.OnLayoutChangeListener controlsLayout=(v,l,t,r,b,ol,ot,or,ob)->fitWindow();
    private JSONArray history;
    private File photo;
    private boolean busy,closed,rendering;
    private final boolean cooking;private final String functionId;
    private final Runnable onClosed;

    public static Dialog show(Activity activity,ApplianceTools tools){return show(activity,tools,()->{});}
    public static Dialog show(Activity activity,ApplianceTools tools,Runnable onClosed){CookingAssistant panel=new CookingAssistant(activity,tools,onClosed);panel.dialog.show();panel.dialog.getWindow().getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());panel.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);panel.fitWindow();return panel.dialog;}
    public static Dialog showPhotos(Activity activity,ApplianceTools tools,Runnable onClosed){CookingAssistant panel=new CookingAssistant(activity,tools,onClosed);panel.dialog.show();panel.dialog.getWindow().getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());panel.fitWindow();panel.photos();return panel.dialog;}
    private CookingAssistant(Activity activity,ApplianceTools tools,Runnable onClosed){
        this.onClosed=onClosed;pantryReminder=new PantryNudge(activity).takeReminder(System.currentTimeMillis());
        this.activity=activity;this.tools=tools;controls=ControlClient.get(activity);session=AssistantConversation.get(activity);uid=session.owner();dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);knob=new RotaryController(activity,()->dialog.dismiss());
        JSONObject context;try{context=tools.scene().optJSONObject("context");}catch(Exception ignored){context=null;}
        cooking=context!=null&&java.util.Arrays.asList("step","function","cooking").contains(context.optString("page"));
        functionId=context==null?"":context.optString("functionId");
        launcher=activity.getWindow().getDecorView().findViewWithTag("assistant");
        boolean left=launcher!=null&&launcher.getX()+launcher.getWidth()/2f<activity.getWindow().getDecorView().getWidth()/2f;if(launcher!=null){anchorX=launcher.getX();anchorY=launcher.getY();}identityLeft=left;
        if(launcher!=null)launcher.setVisibility(View.INVISIBLE);
        frost=new FrostedPanel(activity);
        overlay=new FrameLayout(activity){@Override public boolean dispatchTouchEvent(MotionEvent event){dismissKeyboardOutside(event);return super.dispatchTouchEvent(event);}};overlay.setTag("assistant.overlay");overlay.setOnClickListener(v->keyboardHide());
        protectedFooter=activity.getWindow().getDecorView().findViewWithTag("assistant.footer");
        conversation=Ui.column(activity);conversation.setTag("assistant.conversation");
        FrameLayout.LayoutParams lane=new FrameLayout.LayoutParams(-2,-1,Gravity.TOP|(left?Gravity.LEFT:Gravity.RIGHT));
        lane.setMargins(Ui.dp(activity,16),Ui.dp(activity,28),Ui.dp(activity,16),Ui.dp(activity,16));overlay.addView(conversation,lane);
        dialog.setContentView(overlay);dialog.setCanceledOnTouchOutside(false);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(0);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);window.setGravity(Gravity.TOP|Gravity.LEFT);
        header=new LinearLayout(activity);header.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        stop=button("STOPP",controls::stop);stop.setTextSize(18);stop.setMinHeight(Ui.dp(activity,48));stop.setMinimumHeight(Ui.dp(activity,48));stop.setTextColor(0xffef8e93);stop.setVisibility(controls.state.ownRun||controls.state.uncertainRun||controls.state.deviceFaultLatched?View.VISIBLE:View.GONE);frost.apply(stop,false,24);header.addView(stop,new LinearLayout.LayoutParams(Ui.dp(activity,104),Ui.dp(activity,48)));
        machineStatus=KitViews.text(activity,"",16,Ui.MUTED,false);machineStatus.setVisibility(View.GONE);
        scroll=new KitchenScroll(activity);scroll.setFillViewport(false);scroll.setTag("assistant.messages");messages=Ui.column(activity);messages.setGravity(Gravity.TOP);messages.setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,12));thread=Ui.column(activity);thread.addView(messages);scroll.addView(thread);conversation.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot)bottom();});
        photoRow=Ui.column(activity);thread.addView(photoRow);
        input=new LinearLayout(activity);input.setTag("assistant.composer");input.setGravity(Gravity.CENTER_VERTICAL);input.setPadding(Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8));frost.apply(input,false,40);

        editor=new EditText(activity);editor.setTag("assistant.editor");editor.setHint("Nachricht …");editor.setTextSize(27);editor.setTextColor(Ui.INK);editor.setHintTextColor(Ui.MUTED);editor.setBackground(null);editor.setPadding(Ui.dp(activity,8),Ui.dp(activity,10),Ui.dp(activity,8),Ui.dp(activity,10));editor.setMinHeight(Ui.dp(activity,68));editor.setSingleLine(true);editor.setMinHeight(Ui.dp(activity,68));editor.setMinimumHeight(Ui.dp(activity,68));editor.setShowSoftInputOnFocus(false);editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);editor.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){send();return true;}return false;});knob.add(editor);input.addView(editor,new LinearLayout.LayoutParams(0,-2,1));
        send=icon("send","Senden",this::send,true);send.setTag("assistant.send");input.addView(send,new LinearLayout.LayoutParams(Ui.dp(activity,64),Ui.dp(activity,64)));thread.addView(input);
        identity=new FrameLayout(activity);
        avatar=new AssistantAvatar(activity);avatar.setTag("assistant.avatar");avatar.setAmbient(false);identity.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(activity,190),Ui.dp(activity,190)));
        identity.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));identity.addView(header);
        close=icon("chevron-down","Chat zuklappen",()->dialog.dismiss(),false);close.setTag("assistant.close");frost.apply(close,false,36);LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(Ui.dp(activity,64),Ui.dp(activity,64));closeSize.gravity=Gravity.TOP;closeSize.topMargin=Ui.dp(activity,8);identity.addView(close,closeSize);
        identity.removeAllViews();identity.addView(avatar,new FrameLayout.LayoutParams(Ui.dp(activity,190),Ui.dp(activity,190)));identity.addView(header,new FrameLayout.LayoutParams(-2,Ui.dp(activity,48)));identity.addView(close,new FrameLayout.LayoutParams(Ui.dp(activity,64),Ui.dp(activity,64)));conversation.addView(identity,0,new LinearLayout.LayoutParams(-1,Ui.dp(activity,190)));avatar.setOnClickListener(v->dialog.dismiss());avatar.setOnTouchListener(this::dragHeader);
        attach=icon("microphone","Nachricht diktieren",()->startVoice(),true);attach.setTag("assistant.microphone");attach.setVisibility(View.GONE);
        voice=new AssistantVoiceInput(activity,editor,text->{bubble(false,text,false);bottom();},this::voiceState);
        keyboard=new ApplianceKeyboard(activity,editor,this::keyboardHide);keyboard.setTag("assistant.keyboard");keyboard.setVisibility(View.GONE);FrameLayout.LayoutParams keyboardSize=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);overlay.addView(keyboard,keyboardSize);
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(!rendering)session.draft(value.toString());updateComposer();}});
        editor.setOnFocusChangeListener((v,focused)->{if(focused&&!busy)keyboardShow();});editor.setOnClickListener(v->{if(!busy)keyboardShow();});keyboard.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());overlay.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());
        if(protectedFooter!=null)protectedFooter.addOnLayoutChangeListener(controlsLayout);
        com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,code,event)->{if(code==KeyEvent.KEYCODE_BACK&&keyboard.getVisibility()==View.VISIBLE){if(event.getAction()==KeyEvent.ACTION_UP)keyboardHide();return true;}return keyboard.getVisibility()==View.VISIBLE?keyboard.dispatch(event):knob.dispatch(event);});dialog.setOnDismissListener(d->{if(!rendering&&!busy&&!session.awaitingMerge())session.draft(editor.getText().toString());closed=true;if(memoryDialog!=null)memoryDialog.dismiss();voice.close();session.unlisten(sessionChanged);session.flush();main.removeCallbacks(statusTick);if(protectedFooter!=null)protectedFooter.removeOnLayoutChangeListener(controlsLayout);if(launcher!=null&&launcher.isAttachedToWindow())launcher.setVisibility(View.VISIBLE);onClosed.run();});session.listen(sessionChanged);refreshSession();if(cooking)main.post(statusTick);overlay.post(this::layout);bottom();
    }
    private final Runnable statusTick=new Runnable(){public void run(){if(closed)return;com.kitchenos.connect.control.ControlState state=controls.state;long now=SystemClock.elapsedRealtime();boolean fresh=state.connected&&state.statusAt>=0&&now>=state.statusAt&&now-state.statusAt<500;String status=!state.connected?"Maschine nicht verbunden":!fresh?"Maschinenwerte werden aktualisiert …":state.ownRun?(state.state==3?"Pausiert":"Maschine läuft")+" · "+state.temperature+" °C · "+state.elapsed+" s":"Maschine bereit · "+state.temperature+" °C";machineStatus.setText(status);boolean available=state.connected&&!state.stopping;stop.setVisibility(state.ownRun||state.uncertainRun||state.deviceFaultLatched||state.stopping?View.VISIBLE:View.GONE);stop.setEnabled(available);stop.setTextColor(available?0xffef8e93:Ui.MUTED);main.postDelayed(this,500);}};
    private void dismissKeyboardOutside(MotionEvent event){if(keyboard==null||keyboard.getVisibility()!=View.VISIBLE||event.getAction()!=MotionEvent.ACTION_DOWN)return;android.graphics.Rect bounds=new android.graphics.Rect();keyboard.getGlobalVisibleRect(bounds);if(bounds.contains((int)event.getRawX(),(int)event.getRawY()))return;View composer=overlay.findViewWithTag("assistant.composer");(composer==null?editor:composer).getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY()))keyboardHide();}
    private void keyboardShow(){if(input.getParent()!=overlay){((android.view.ViewGroup)input.getParent()).removeView(input);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);lp.leftMargin=Ui.dp(activity,16);lp.rightMargin=Ui.dp(activity,16);overlay.addView(input,lp);}keyboard.setVisibility(View.VISIBLE);overlay.post(this::layout);}
    private void keyboardHide(){keyboard.setVisibility(View.GONE);editor.clearFocus();if(input.getParent()!=thread){((android.view.ViewGroup)input.getParent()).removeView(input);thread.addView(input,new LinearLayout.LayoutParams(-1,-2));}layout();}
    /** Expose the real fixed cooking actions to WindowManager hit testing. An invisible
     * fullscreen dialog must never consume the first tap on the visible STOPP footer. */
    private void fitWindow(){
        View source=activity.getWindow().getDecorView();int height=source.getHeight();
        if(protectedFooter!=null&&protectedFooter.isShown()&&protectedFooter.getHeight()>0){int[] footerAt=new int[2],hostAt=new int[2];protectedFooter.getLocationOnScreen(footerAt);source.getLocationOnScreen(hostAt);height=Math.min(height,footerAt[1]-hostAt[1]);}
        Window window=dialog.getWindow();WindowManager.LayoutParams params=window.getAttributes();height=Math.max(1,height);
        if(params.width!=WindowManager.LayoutParams.MATCH_PARENT||params.height!=height)window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,height);
        overlay.post(this::layout);
    }
    private void layout(){
        if(overlay.getWidth()==0)return;
        View footer=activity.getWindow().getDecorView().findViewWithTag("assistant.footer");int bottom=Ui.dp(activity,16);
        if(footer!=null&&footer.isShown()){int[] at=new int[2],root=new int[2];footer.getLocationOnScreen(at);overlay.getLocationOnScreen(root);bottom=Math.max(bottom,overlay.getHeight()-(at[1]-root[1])+Ui.dp(activity,8));}
        FrameLayout.LayoutParams keys=(FrameLayout.LayoutParams)keyboard.getLayoutParams();int keyboardBottom=Math.max(0,bottom-Ui.dp(activity,8));if(keys.bottomMargin!=keyboardBottom){keys.bottomMargin=keyboardBottom;keyboard.setLayoutParams(keys);}
        FrameLayout.LayoutParams lane=(FrameLayout.LayoutParams)conversation.getLayoutParams();int width=Math.min(Ui.dp(activity,560),Math.round((overlay.getWidth()-Ui.dp(activity,32))*.70f)),inset=bottom+(keyboard.getVisibility()==View.VISIBLE?keyboard.getHeight()+input.getHeight()+Ui.dp(activity,16):0);
        if(input.getParent()==overlay){FrameLayout.LayoutParams composer=(FrameLayout.LayoutParams)input.getLayoutParams();int inputBottom=keyboardBottom+keyboard.getHeight()+Ui.dp(activity,8);if(composer.bottomMargin!=inputBottom){composer.bottomMargin=inputBottom;input.setLayoutParams(composer);}}
        boolean left=anchorX+Ui.dp(activity,95)<overlay.getWidth()/2f;int x=Math.max(Ui.dp(activity,12),Math.min(overlay.getWidth()-width-Ui.dp(activity,12),Math.round(left?anchorX:anchorX+Ui.dp(activity,190)-width))),y=Math.max(Ui.dp(activity,8),Math.min(Math.round(anchorY),overlay.getHeight()-inset-Ui.dp(activity,480)));if(lane.width!=width||lane.bottomMargin!=inset||lane.leftMargin!=x||lane.topMargin!=y||lane.gravity!=(Gravity.TOP|Gravity.LEFT)){lane.gravity=Gravity.TOP|Gravity.LEFT;lane.width=width;lane.leftMargin=x;lane.topMargin=y;lane.rightMargin=0;lane.bottomMargin=inset;conversation.setLayoutParams(lane);}layoutIdentity(width,x);

    }
    private void layoutIdentity(int width,int laneX){int plate=Ui.dp(activity,190),local=Math.max(0,Math.min(width-plate,Math.round(anchorX)-laneX));FrameLayout.LayoutParams av=(FrameLayout.LayoutParams)avatar.getLayoutParams();if(av.leftMargin!=local){av.leftMargin=local;avatar.setLayoutParams(av);}boolean controlsLeft=local>=Ui.dp(activity,180);FrameLayout.LayoutParams fold=(FrameLayout.LayoutParams)close.getLayoutParams();int foldX=Math.max(0,Math.min(width-Ui.dp(activity,64),controlsLeft?local-Ui.dp(activity,72):local+plate+Ui.dp(activity,8))),foldY=Ui.dp(activity,8);if(fold.leftMargin!=foldX||fold.topMargin!=foldY){fold.leftMargin=foldX;fold.topMargin=foldY;close.setLayoutParams(fold);}FrameLayout.LayoutParams controls=(FrameLayout.LayoutParams)header.getLayoutParams();int controlsX=Math.max(0,controlsLeft?fold.leftMargin-Ui.dp(activity,112):fold.leftMargin+Ui.dp(activity,72)),controlsY=Ui.dp(activity,16);if(controls.leftMargin!=controlsX||controls.topMargin!=controlsY){controls.leftMargin=controlsX;controls.topMargin=controlsY;header.setLayoutParams(controls);}}
    private boolean dragHeader(View source,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dragX=e.getRawX();dragY=e.getRawY();dragAnchorX=anchorX;dragAnchorY=anchorY;headerDragging=false;return true;case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-dragX,dy=e.getRawY()-dragY;if(!headerDragging&&Math.hypot(dx,dy)>ViewConfiguration.get(activity).getScaledTouchSlop())headerDragging=true;if(headerDragging){anchorX=Math.max(Ui.dp(activity,8),Math.min(overlay.getWidth()-Ui.dp(activity,198),dragAnchorX+dx));anchorY=Math.max(Ui.dp(activity,8),Math.min(overlay.getHeight()-Ui.dp(activity,480),dragAnchorY+dy));layout();}return true;case MotionEvent.ACTION_UP:if(headerDragging){if(launcher instanceof FloatingAssistant){((FloatingAssistant)launcher).rememberAt(anchorX,anchorY);anchorX=launcher.getX();anchorY=launcher.getY();layout();}}else source.performClick();return true;case MotionEvent.ACTION_CANCEL:headerDragging=false;return true;default:return true;}}
    private ImageButton icon(String name,String label,Runnable action,boolean primary){
        ImageButton view=new ImageButton(activity);view.setImageDrawable(new InterfaceIcon(name,primary?0xffffffff:Ui.INK));view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setPadding(Ui.dp(activity,13),Ui.dp(activity,13),Ui.dp(activity,13),Ui.dp(activity,13));view.setMinimumWidth(0);view.setMinimumHeight(0);view.setContentDescription(label);
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_selected},KitViews.shape(activity,primary?Ui.ACCENT:Ui.SOFT,40,Ui.ACCENT));states.addState(new int[]{android.R.attr.state_focused},KitViews.shape(activity,primary?Ui.ACCENT:Ui.SOFT,40,Ui.ACCENT));states.addState(new int[]{},KitViews.shape(activity,primary?Ui.ACCENT:Color.TRANSPARENT,40,0));view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ee6542),states,null));view.setOnClickListener(v->action.run());knob.add(view);return view;
    }
    private void voiceState(boolean value){listening=value;attach.setSelected(value);attach.setContentDescription(value?(voice.recording()?"Diktat beenden":"Diktat wird erkannt"):"Nachricht diktieren");editor.setHint(value?(voice.recording()?"Ich höre zu …":"Diktat wird erkannt …"):"Nachricht …");editor.setEnabled(!value&&!busy&&!session.awaitingMerge());updateComposer();}
    private void startVoice(){
        if(voice.recording()){voice.toggle();return;}if(busy||session.awaitingMerge())return;keyboardHide();
        if(voice.allowed()){voice.toggle();return;}if(messages.findViewWithTag("assistant.voiceConsent")!=null){bottom();return;}
        LinearLayout choice=Ui.column(activity);choice.setTag("assistant.voiceConsent");choice.setPadding(Ui.dp(activity,16),Ui.dp(activity,12),Ui.dp(activity,16),Ui.dp(activity,12));frost.apply(choice,false,24);choice.addView(KitViews.text(activity,"Mit Kalomatik diktieren",20,Ui.INK,true));TextView disclosure=KitViews.text(activity,AssistantVoiceInput.DISCLOSURE,18,Ui.INK,false);choice.addView(disclosure);knob.addReadingRegion(disclosure,scroll);
        Button allow=button("Diktieren aktivieren",()->{voice.allow();refreshSession();voice.toggle();});allow.setTag("assistant.voiceAllow");choice.addView(allow);Button later=button("Später",this::refreshSession);later.setTag("assistant.voiceLater");choice.addView(later);messages.addView(choice);bottom();
    }
    private void updateComposer(){boolean locked=busy||session.awaitingMerge()||listening;send.setEnabled(!locked&&(photo!=null||editor.getText().toString().trim().length()>0)&&editor.length()<=2000);send.setAlpha(send.isEnabled()?1f:.38f);attach.setEnabled(voice!=null&&voice.recording()||!busy&&!session.awaitingMerge());}
    private Button button(String label,Runnable action){Button view=Ui.button(activity,label,v->action.run());view.setContentDescription(label);view.setMinimumHeight(Ui.dp(activity,62));knob.add(view);return view;}
    private TextView bubble(boolean user,String message,boolean save){LinearLayout box=Ui.column(activity);box.setTag("assistant.bubble");box.setPadding(Ui.dp(activity,16),Ui.dp(activity,10),Ui.dp(activity,16),Ui.dp(activity,14));frost.apply(box,user,24);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=Ui.dp(activity,user?28:0);lp.rightMargin=Ui.dp(activity,user?0:28);lp.bottomMargin=Ui.dp(activity,12);messages.addView(box,lp);box.addView(KitViews.text(activity,user?"Du":"Kalomatik",14,user?Ui.ACCENT:Ui.MUTED,true));TextView text=KitViews.text(activity,message,25,Ui.INK,false);text.setLineSpacing(Ui.dp(activity,3),1);box.addView(text);knob.addReadingRegion(text,scroll);if(save)try{history.put(new JSONObject().put("user",user).put("text",message));ChatHistory.save(activity,uid,history);}catch(Exception ignored){}return text;}
    private void refreshSession(){
        if(closed)return;if(!session.sameOwner()){dialog.dismiss();return;}
        rendering=true;busy=session.busy()||session.dictating();avatar.setBusy(busy);photo=session.photo();history=session.history();
        knob.reset();knob.add(stop);knob.add(close);messages.removeAllViews();
        if(history.length()==0)bubble(false,cooking?"Ich bin hier. Frag mich zum nächsten Handgriff, zu Zutaten oder zu einer neuen Rezeptidee.":"Was beschäftigt dich in deiner Küche? Lass uns über Rezepte, deinen Vorrat oder neue Ideen sprechen.",false);
        else for(int i=0;i<history.length();i++){JSONObject row=history.optJSONObject(i);if(row!=null)bubble(row.optBoolean("user"),row.optString("text"),false);}
        if(!functionId.isEmpty())bubble(false,FunctionAdvice.question(functionId),false);

        int incoming=PhotoInbox.pending(activity).length();if(incoming>0){Button inbox=button(incoming+" Foto"+(incoming==1?"":"s")+" vom Handy",this::photos);inbox.setTag("assistant.inbox");messages.addView(inbox);}
        if(pantryReminder){bubble(false,"Fotografier doch deinen Vorrat mit dem Handy und teile ihn mit KitchenOS. Dann kann ich dir Gerichte mit deinen vorhandenen Zutaten vorschlagen.",false);messages.addView(button("Vorratsfoto vom Handy auswählen",this::photos));}
        if(busy)bubble(false,session.dictating()?"Dein Diktat wird aufgenommen oder erkannt. Du kannst die Aufnahme mit dem Mikrofon beenden.":"Kalomatik denkt nach … Du kannst das Gespräch schließen und weiterkochen.",false);
        if(!session.error().isEmpty())bubble(false,session.error(),false);
        if(session.awaitingMerge()){messages.addView(button("Mit aktuellem Vorrat abgleichen",session::applyPendingStock));messages.addView(button("Fotoergebnis verwerfen",session::discardPendingStock));}
        JSONObject action=session.uiRequest();if(action!=null)messages.addView(button("Auswahl übernehmen",()->{JSONObject applied=tools.rpc(action);if(applied.has("error")){bubble(false,"Die Kochansicht hat sich geändert. Bitte die Auswahl erneut prüfen.",false);bottom();}else{session.clearUiRequest();dialog.dismiss();}}));
        photoRow.removeAllViews();photoRow.setPadding(0,0,0,0);photoRow.setBackground(null);
        if(photo!=null){
            photoRow.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,10));frost.apply(photoRow,false,24);
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);ImageView preview=new ImageView(activity);preview.setImageURI(android.net.Uri.fromFile(photo));preview.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(preview,new LinearLayout.LayoutParams(Ui.dp(activity,72),Ui.dp(activity,72)));
            TextView caption=KitViews.text(activity,session.awaitingMerge()?"Fotoergebnis gespeichert\nAbgleich noch offen":"Foto angehängt\nNoch nicht gesendet",16,Ui.MUTED,false);caption.setPadding(Ui.dp(activity,10),0,0,0);row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));ImageButton remove=icon("close","Foto entfernen",session::removePhoto,false);remove.setTag("assistant.removePhoto");remove.setEnabled(!busy&&!session.awaitingMerge());row.addView(remove,new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));photoRow.addView(row);
        }
        if(!editor.getText().toString().equals(session.draft())){editor.setText(session.draft());editor.setSelection(editor.length());}
        knob.add(attach);knob.add(editor);knob.add(send);editor.setEnabled(!listening&&!busy&&!session.awaitingMerge());if(busy||session.awaitingMerge())keyboardHide();rendering=false;updateComposer();bottom();
    }
    private void photos(){
        if(busy||session.awaitingMerge())return;keyboardHide();JSONArray rows=PhotoInbox.pending(activity);
        if(rows.length()==0){bubble(false,"Teile ein Foto von deinem Android-Handy mit ‚An KitchenOS senden‘. Hier kannst du es anschließend im Bereich Vorrat auswählen, ohne diese Ansicht zu verlassen.",false);bottom();return;}
        View previous=messages.findViewWithTag("assistant.photoPicker");if(previous!=null)return;
        LinearLayout choices=Ui.column(activity);choices.setTag("assistant.photoPicker");choices.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));frost.apply(choices,false,24);choices.addView(KitViews.text(activity,"Fotos vom Handy",18,Ui.INK,true));
        for(int i=0;i<rows.length();i++){JSONObject item=rows.optJSONObject(i);String id=item.optString("id"),label=(item.optString("intent").equals("pantry")?"Vorratsfoto ":"Foto ")+(i+1);Button choice=button(label,()->{try{JSONObject row=PhotoInbox.get(activity,id);session.attach(PhotoAttachment.restore(activity,row.getString("path")),id,row.getString("intent").equals("pantry"));}catch(Exception error){bubble(false,"Das Foto ist nicht mehr verfügbar.",false);bottom();}});choice.setTag("assistant.photoChoice."+id);choices.addView(choice);}
        choices.addView(button("Auswahl schließen",this::refreshSession));messages.addView(choices);bottom();
    }
    private void send(){
        if(busy)return;JSONObject snapshot=new KitchenStore(activity).snapshot();
        if(!uid.isEmpty()&&!snapshot.optJSONObject("preferences").optBoolean("aiConsent")){consent();return;}
        if(uid.isEmpty()){bubble(false,"Bitte melde dich mit deinem Kalomatik-Konto an. Dein Entwurf bleibt erhalten.",false);bottom();return;}
        try{session.draft(editor.getText().toString());session.submit(tools.scene(),snapshot);}catch(Exception error){bubble(false,error.getMessage()==null?"Die Anfrage konnte nicht vorbereitet werden. Dein Entwurf bleibt erhalten.":error.getMessage(),false);bottom();}
    }
    private void rememberPreference(){
        keyboardHide();if(!session.sameOwner())return;try{JSONObject remembering=new KalomatikPlans(activity).sharedPreferences();if(!remembering.optBoolean("rememberKnown")){Toast.makeText(activity,"Deine Einstellung zum Erinnern ist noch nicht bestätigt. Gleiche die Assistenteneinstellungen mit Calomatik ab.",Toast.LENGTH_LONG).show();return;}if(!remembering.optBoolean("remember")){Toast.makeText(activity,"Erinnern ist in deinen Calomatik-Assistenteneinstellungen ausgeschaltet.",Toast.LENGTH_LONG).show();return;}}catch(Exception unavailable){}LinearLayout content=Ui.column(activity);content.setPadding(Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24),Ui.dp(activity,20));content.addView(KitViews.text(activity,"Was soll ich mir merken?",28,Ui.INK,true));content.addView(KitViews.text(activity,"Eine Kochvorliebe oder Essensroutine für deinen Assistenten auf Handy und Maschine. Du kannst sie auf dem Handy ändern oder löschen.",21,Ui.MUTED,false));EditText memory=Ui.input(activity,"Zum Beispiel: Abends mag ich leichte Gerichte",false);memory.setTextSize(24);memory.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});String initial="";for(int i=history.length()-1;i>=0;i--){JSONObject row=history.optJSONObject(i);if(row!=null&&row.optBoolean("user")){initial=row.optString("text");break;}}memory.setText(initial.substring(0,Math.min(240,initial.length())));content.addView(memory);EditorKeyboardDialog.bind(activity,memory);
        AlertDialog confirm=new AlertDialog.Builder(activity).setView(content).setNegativeButton("Zurück",null).setPositiveButton("Merken",null).create();memoryDialog=confirm;confirm.setOnDismissListener(d->{EditorKeyboardDialog.dismiss(memory);memoryDialog=null;});confirm.show();CookingWindows.protectFooter(activity,confirm.getWindow());RotaryDialogs.bind(activity,confirm);confirm.getButton(-1).setOnClickListener(v->{String value=memory.getText().toString().trim();if(value.isEmpty()){memory.setError("Bitte eine Vorliebe eingeben");return;}confirm.getButton(-1).setEnabled(false);new Thread(()->{try{KalomatikCookingMemory writer=new KalomatikCookingMemory(activity);if(!session.sameOwner())throw new java.io.IOException("Dein Konto hat sich geändert.");JSONObject receipt=writer.remember(value);main.post(()->{if(!session.sameOwner()){confirm.dismiss();return;}try{session.confirmedMemory(receipt);}catch(Exception ignored){}confirm.dismiss();});}catch(Exception error){main.post(()->{if(!confirm.isShowing())return;confirm.getButton(-1).setEnabled(true);Toast.makeText(activity,error.getMessage()==null?"Noch nicht gespeichert. Bitte erneut versuchen.":error.getMessage(),Toast.LENGTH_LONG).show();});}},"Calomatik-Cooking-Memory").start();});
    }
    private void consent(){
        keyboardHide();if(messages.findViewWithTag("assistant.consent")!=null){bottom();return;}
        LinearLayout choice=Ui.column(activity);choice.setTag("assistant.consent");choice.setPadding(Ui.dp(activity,16),Ui.dp(activity,12),Ui.dp(activity,16),Ui.dp(activity,12));frost.apply(choice,false,24);choice.addView(KitViews.text(activity,KitchenConsent.AI_TITLE,20,Ui.INK,true));TextView info=KitViews.text(activity,KitchenConsent.AI_MESSAGE,19,Ui.INK,false);choice.addView(info);knob.addReadingRegion(info,scroll);
        Button accept=button("KI verwenden",()->{try{if(KitchenConsent.accept(activity,uid)){refreshSession();send();}}catch(Exception error){bubble(false,"Die Einstellung konnte nicht gespeichert werden.",false);bottom();}});accept.setTag("assistant.consentAccept");choice.addView(accept);Button later=button("Später",this::refreshSession);later.setTag("assistant.consentLater");choice.addView(later);messages.addView(choice);bottom();
    }
    private void bottom(){scroll.post(()->scroll.smoothScrollTo(0,messages.getHeight()));}
}
