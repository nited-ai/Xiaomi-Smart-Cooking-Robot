package com.kitchenos.connect.kitchen;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.InputFilter;
import com.kitchenos.common.*;
import com.kitchenos.connect.ui.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Appliance messenger. Photo pixels leave the device only on Send with AI consent. */
public final class KitchenChatActivity extends Activity {
    private LinearLayout messages,attachmentRow;
    private EditText input;
    private Button send;
    private ImageButton attach;
    private ScrollView scroll;
    private String boundUid="",draft="",requestId=UUID.randomUUID().toString(),lastRequestSignature="";
    private File photo;
    private String inboxPhotoId="";
    private boolean stockPhoto,busy,destroyed,createRecipe;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private RotaryController knob;
    private ApplianceKeyboard keyboard;
    private AssistantAvatar avatar;
    private JSONArray history=new JSONArray();
    private KitchenPages.Navigation navigation(){return new KitchenPages.Navigation(){
        public void home(String tab){if(!tab.equals("assistant"))openTab(tab);}
        public void recipe(Recipe recipe){setResult(RESULT_OK,new Intent().putExtra("recipeId",recipe.id));finish();}
        public void wifi(){openTab("wifi");}public void receiver(){openTab("receive");}public void vendor(){openTab("kitchen");}
    };}
    private void openTab(String tab){setResult(RESULT_OK,new Intent().putExtra("tab",tab));finish();}
    @Override public void onCreate(Bundle state){super.onCreate(state);boundUid=uid();draft=AssistantConversation.get(this).draft();knob=new RotaryController(this);if(state!=null&&boundUid.equals(state.getString("uid",""))){lastRequestSignature=state.getString("requestSignature","");inboxPhotoId=state.getString("inboxPhotoId","");stockPhoto=state.getBoolean("stockPhoto");createRecipe=state.getBoolean("createRecipe");requestId=state.getString("requestId",requestId);try{history=new JSONArray(state.getString("history","[]"));String path=state.getString("photo","");if(!path.isEmpty())photo=PhotoAttachment.restore(this,path);}catch(Exception ignored){}}else readHistory();if(state==null&&draft.isEmpty()&&getIntent().hasExtra("initialMessage"))draft=getIntent().getStringExtra("initialMessage");render();if(state==null&&getIntent().hasExtra("photoId"))openPhonePhoto(getIntent().getStringExtra("photoId"));if(state==null&&getIntent().getBooleanExtra("attachPhoto",false)){stockPhoto=!getIntent().getBooleanExtra("recipePhoto",false);main.post(this::phonePhotos);}}
    @Override protected void onResume(){super.onResume();if(!uid().equals(boundUid)){boundUid=uid();photo=null;draft=AssistantConversation.get(this).draft();history=new JSONArray();requestId=UUID.randomUUID().toString();readHistory();render();}}
    @Override protected void onDestroy(){destroyed=true;worker.shutdown();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return keyboard!=null&&keyboard.getVisibility()==View.VISIBLE?keyboard.dispatch(event)||super.dispatchKeyEvent(event):knob!=null&&knob.dispatch(event)||super.dispatchKeyEvent(event);}
    @Override public boolean onGenericMotionEvent(MotionEvent event){return knob!=null&&knob.motion(event)||super.onGenericMotionEvent(event);}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("uid",boundUid);state.putString("requestSignature",lastRequestSignature);state.putString("inboxPhotoId",inboxPhotoId);state.putString("draft",input.getText().toString());state.putString("history",history.toString());state.putString("requestId",requestId);state.putBoolean("stockPhoto",stockPhoto);state.putBoolean("createRecipe",createRecipe);if(photo!=null)state.putString("photo",photo.getAbsolutePath());super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(input!=null&&boundUid.equals(uid())){AssistantConversation session=AssistantConversation.get(this);session.draft(input.getText().toString());session.flush();}super.onPause();}
    @Override public void onBackPressed(){if(keyboard!=null&&keyboard.getVisibility()==View.VISIBLE)keyboard.setVisibility(View.GONE);else super.onBackPressed();}
    private String uid(){return new CloudSession(this).account().optString("uid");}
    private Button button(String text,Runnable action){Button b=Ui.button(this,text,v->action.run());b.setTextSize(21);b.setMinHeight(Ui.dp(this,60));b.setContentDescription(text);knob.add(b);return b;}
    private void render(){
        Ui.theme(this);knob.reset();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout surface=Ui.column(this);surface.setBackgroundColor(Ui.BG);setContentView(surface);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(Ui.dp(this,24),Ui.dp(this,14),Ui.dp(this,24),Ui.dp(this,14));header.addView(button("‹",this::finish),new LinearLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,64)));avatar=new AssistantAvatar(this);header.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));LinearLayout title=Ui.column(this);title.setPadding(Ui.dp(this,14),0,0,0);title.addView(KitViews.text(this,"Kalomatik",28,Ui.INK,true));title.addView(KitViews.text(this,"Dein Assistent in der Küche",18,Ui.MUTED,false));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("···",this::menu),new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));surface.addView(header);KitViews.divider(this,surface);
        scroll=new ScrollView(this);scroll.setDefaultFocusHighlightEnabled(false);scroll.setFillViewport(true);scroll.setBackground(new MessengerPaper());messages=Ui.column(this);messages.setPadding(Ui.dp(this,26),Ui.dp(this,24),Ui.dp(this,26),Ui.dp(this,16));scroll.addView(messages);surface.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(history.length()==0){bubble(false,"Was kochen wir heute? Zeig mir deinen Vorrat oder sag mir, worauf du Lust hast.",false);LinearLayout starters=new LinearLayout(this);starters.addView(button("Vorrat zeigen",()->{stockPhoto=true;pickPhoto();}),new LinearLayout.LayoutParams(0,-2,1));starters.addView(button("Rezept aus Vorrat",this::createFromPantry),new LinearLayout.LayoutParams(0,-2,1));starters.addView(button("Vorlieben",()->new KitchenPages(this,navigation()).preferences()),new LinearLayout.LayoutParams(0,-2,1));messages.addView(starters);}else for(int i=0;i<history.length();i++){JSONObject row=history.optJSONObject(i);if(row!=null)bubble(row.optBoolean("user"),row.optString("text"),false);}
        attachmentRow=Ui.column(this);attachmentRow.setPadding(Ui.dp(this,26),0,Ui.dp(this,26),0);surface.addView(attachmentRow);renderPhoto();
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,18));attach=KitViews.imageIcon(this,"ic_attachment","Foto anhängen",this::attachments);knob.add(attach);dock.addView(attach,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));input=Ui.input(this,"Nachricht an Kalomatik …",true);input.setTextSize(22);input.setMinLines(1);input.setMaxLines(4);input.setText(draft);input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable text){if(boundUid.equals(uid())){draft=text.toString();AssistantConversation.get(KitchenChatActivity.this).draft(draft);}}});knob.add(input);dock.addView(input,new LinearLayout.LayoutParams(0,-2,1));send=button("Senden",this::send);send.setTextColor(Ui.ACCENT);dock.addView(send,new LinearLayout.LayoutParams(Ui.dp(this,116),Ui.dp(this,64)));surface.addView(dock);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){send();return true;}return false;});keyboard=new ApplianceKeyboard(this,input,()->keyboard.setVisibility(View.GONE));keyboard.setVisibility(View.GONE);surface.addView(keyboard);input.setOnFocusChangeListener((v,focused)->{if(focused&&!busy)keyboard.setVisibility(View.VISIBLE);});input.setOnClickListener(v->{if(!busy)keyboard.setVisibility(View.VISIBLE);});setBusy(busy);bottom();
    }
    private void menu(){AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Dein Assistent").setItems(new String[]{"Rezept aus meinem Vorrat","Vorlieben & Ernährung","Mein Konto","Rezepte","Unterhaltung leeren"},(d,w)->{if(w==0)createFromPantry();else if(w==1)new KitchenPages(this,navigation()).preferences();else if(w==2)new KitchenPages(this,navigation()).account();else if(w==3)openTab("recipes");else{history=new JSONArray();persistHistory();draft=input.getText().toString();render();}}).create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void attachments(){AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Foto hinzufügen").setItems(new String[]{"Lebensmittel für meinen Vorrat","Bild zur Nachricht","Fotos vom Handy"},(d,w)->{if(w==2){phonePhotos();return;}stockPhoto=w==0;pickPhoto();}).create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void phonePhotos(){JSONArray photos=PhotoInbox.pending(this);if(photos.length()==0){AlertDialog help=new AlertDialog.Builder(this).setTitle("Foto vom Handy übernehmen").setMessage("1. Auf dem Android-Handy ein Foto öffnen.\n2. Teilen → An KitchenOS senden wählen.\n3. Hier die Büroklammer öffnen und das empfangene Foto auswählen.\n\nDu kannst alternativ ein auf der Maschine gespeichertes Bild öffnen.").setNegativeButton("Zurück",null).setPositiveButton("Bild auf der Maschine",(d,w)->pickPhoto()).create();help.show();RotaryDialogs.bind(this,help);return;}String[] titles=new String[photos.length()];for(int i=0;i<titles.length;i++)titles[i]=(photos.optJSONObject(i).optString("intent").equals("pantry")?"Vorratsfoto":"Foto")+" "+(i+1);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Vom Handy").setItems(titles,(d,w)->openPhonePhoto(photos.optJSONObject(w).optString("id"))).create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void openPhonePhoto(String id){if(busy)return;try{JSONObject row=PhotoInbox.get(this,id);photo=PhotoAttachment.restore(this,row.getString("path"));inboxPhotoId=id;stockPhoto=!getIntent().getBooleanExtra("recipePhoto",false)&&row.getString("intent").equals("pantry");requestId=UUID.randomUUID().toString();renderPhoto();}catch(Exception e){notice("Dieses Foto ist nicht mehr verfügbar oder gehört zu einem anderen Konto.");}}
    private void pickPhoto(){if(busy)return;Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(picker,73);}catch(ActivityNotFoundException e){Toast.makeText(this,"Auf diesem Gerät ist keine Bildauswahl installiert. Du kannst Fotos auch vom gekoppelten Handy teilen.",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=73||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();String owner=boundUid;setBusy(true);worker.execute(()->{try{File imported=PhotoAttachment.importPhoto(this,uri);main.post(()->{if(destroyed||!owner.equals(uid())){imported.delete();return;}if(photo!=null)photo.delete();photo=imported;requestId=UUID.randomUUID().toString();renderPhoto();setBusy(false);});}catch(Exception e){main.post(()->{if(!destroyed){setBusy(false);notice(e.getMessage());}});}});}
    private void renderPhoto(){if(attachmentRow==null)return;attachmentRow.removeAllViews();if(photo==null)return;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setImageURI(Uri.fromFile(photo));preview.setContentDescription("Angehängtes Foto");row.addView(preview,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,100)));TextView caption=KitViews.text(this,stockPhoto?"Vorrat erkennen · noch nicht gesendet":"Foto · noch nicht gesendet",18,Ui.MUTED,false);row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));row.addView(button("Entfernen",()->{if(busy)return;if(photo!=null)photo.delete();photo=null;requestId=UUID.randomUUID().toString();renderPhoto();}));attachmentRow.addView(row);}
    private TextView bubble(boolean user,String value,boolean remember){LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,16));box.setBackground(KitViews.shape(this,user?Ui.SOFT:Ui.CARD,24,user?Ui.ACCENT:Ui.LINE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=Ui.dp(this,user?70:0);lp.rightMargin=Ui.dp(this,user?0:40);lp.bottomMargin=Ui.dp(this,18);messages.addView(box,lp);box.addView(KitViews.text(this,user?"Du":"Kalomatik",14,user?Ui.ACCENT:Ui.MUTED,true));TextView text=KitViews.text(this,value,22,Ui.INK,false);text.setLineSpacing(Ui.dp(this,5),1);text.setPadding(0,Ui.dp(this,8),0,0);text.setTextIsSelectable(true);box.addView(text);if(remember){try{history.put(new JSONObject().put("user",user).put("text",value));while(history.length()>60)history.remove(0);persistHistory();}catch(Exception ignored){}}return text;}
    private void setBusy(boolean value){busy=value;if(avatar!=null)avatar.setBusy(value);if(value&&keyboard!=null)keyboard.setVisibility(View.GONE);if(send!=null)send.setEnabled(!value);if(input!=null)input.setEnabled(!value);if(attach!=null)attach.setEnabled(!value);}
    private void createFromPantry(){if(busy)return;if(photo!=null){notice("Sende das Vorratsfoto zuerst, damit Kalomatik die Lebensmittel erfassen kann.");return;}createRecipe=true;String text=input.getText().toString().trim();if(text.isEmpty())input.setText("Überlege dir ein passendes Gericht aus meinem Vorrat und meinen Vorlieben, das ich Schritt für Schritt auf dieser Maschine kochen kann.");send();}
    private void send(){if(busy)return;String request=input.getText().toString().trim();if(request.isEmpty()&&photo==null)return;if(request.isEmpty())request=stockPhoto?"Erkenne die Lebensmittel auf diesem Foto und ergänze meinen Vorrat. Unleserliche Mengen bleiben offen.":"Was siehst du auf diesem Foto?";KitchenStore store=new KitchenStore(this);JSONObject snapshot=store.snapshot(),preferences=snapshot.optJSONObject("preferences");
        if(CloudSession.configured()&&!boundUid.isEmpty()&&!preferences.optBoolean("aiConsent")){KitchenConsent.ai(this,this::send);return;}
        if(photo!=null&&(!CloudSession.configured()||boundUid.isEmpty()||!preferences.optBoolean("aiConsent"))){notice(!CloudSession.configured()?"Die Verbindung zum Online-Assistenten ist noch nicht eingerichtet. Dein Foto bleibt als Entwurf erhalten.":boundUid.isEmpty()?"Melde dich zuerst mit deinem Kalomatik-Konto an. Dein Foto bleibt als Entwurf erhalten.":"Aktiviere KI-Anfragen unter Vorlieben, damit Kalomatik das Foto auswerten darf. Dein Entwurf bleibt erhalten.");return;}
        if(!CloudSession.configured()||boundUid.isEmpty()||!preferences.optBoolean("aiConsent")){localSuggestions(request,store);return;}
        try{snapshot=AssistantGateway.requestSnapshot(this,snapshot);snapshot.put("conversation",ChatHistory.context(this,boundUid));String signature=Pairing.fingerprint((request+":"+snapshot+":"+createRecipe+":"+(photo==null?"":photo.getAbsolutePath())).getBytes(java.nio.charset.StandardCharsets.UTF_8));if(!lastRequestSignature.isEmpty()&&!lastRequestSignature.equals(signature))requestId=UUID.randomUUID().toString();lastRequestSignature=signature;}catch(Exception e){notice("Die Anfrage konnte nicht vorbereitet werden.");return;}
        final String prompt=request,owner=boundUid,id=requestId;final File sentPhoto=photo;final boolean pantryIntent=stockPhoto&&photo!=null&&!recipePhotoRequest(prompt),recipeIntent=createRecipe;final long revision=snapshot.optLong("revision");final JSONObject requestSnapshot=snapshot;setBusy(true);TextView status=bubble(false,"Kalomatik schaut sich deine Küche an …",false);
        worker.execute(()->{
            try{
                String encodedScene=getIntent().getStringExtra("uiScene");
                JSONObject response=AssistantGateway.send(this,owner,id,prompt,sentPhoto,pantryIntent,recipeIntent,encodedScene==null?null:new JSONObject(encodedScene),requestSnapshot);
                main.post(()->{
                    if(destroyed||!owner.equals(uid()))return;
                    try{
                        boolean clarification=response.optString("recipePhotoStatus").equals("needs-clarification");
                        boolean recipeResponse=!response.optString("recipePhotoStatus").isEmpty();
                        int added=response.optInt("addedStock");((View)status.getParent()).setVisibility(View.GONE);
                        bubble(true,prompt+(sentPhoto==null?"":"\n[Foto]"),true);String answer=response.getString("message");
                        if(pantryIntent&&!recipeResponse)answer+="\n\n"+added+" Lebensmittel ergänzt. Bereits vorhandene Lebensmittel werden nicht doppelt angelegt.";
                        bubble(false,answer,true);
                        String nextDraft=clarification?recipePhotoAddendumDraft(prompt):"";draft=nextDraft;input.setText(nextDraft);input.setSelection(input.length());
                        if(clarification){
                            // Keep the same original pixels and inbox item attached to the
                            // explicit answer. A clarification is not a consumed recipe.
                            stockPhoto=false;renderPhoto();
                        }else if(photo==sentPhoto){
                            if(photo!=null)photo.delete();photo=null;
                            if(!inboxPhotoId.isEmpty())PhotoInbox.consumed(this,inboxPhotoId);
                            inboxPhotoId="";renderPhoto();
                        }
                        requestId=UUID.randomUUID().toString();createRecipe=false;
                        JSONObject variant=response.optJSONObject("variant");
                        if(variant!=null){MachineProgram.card(variant);messages.addView(button("Rezept ansehen · "+variant.getJSONObject("program").getString("title"),()->{setResult(RESULT_OK,new Intent().putExtra("programVariant",variant.toString()).putExtra("ownerUid",owner));finish();}));}
                        JSONObject tool=response.optJSONObject("uiRequest");
                        if(tool!=null){Button perform=button(tool.optJSONObject("params").optString("name").equals("kitchen_ui_set_value")?"Einstellung übernehmen":"Auswahl öffnen",()->{setResult(RESULT_OK,new Intent().putExtra("uiRequest",tool.toString()));finish();});messages.addView(perform);}
                        setBusy(false);bottom();
                    }catch(Exception e){setBusy(false);status.setText(e.getMessage());}
                });
            }catch(Exception e){main.post(()->{if(!destroyed&&owner.equals(uid())){setBusy(false);if(e instanceof CloudSession.ApiException&&((CloudSession.ApiException)e).code.equals("job_failed"))requestId=UUID.randomUUID().toString();status.setText((e.getMessage()==null?"Die Anfrage konnte nicht abgeschlossen werden.":e.getMessage())+" Dein Entwurf bleibt erhalten.");bottom();}});}
        });
    }
    private static String recipePhotoAddendumDraft(String previous){
        String prefix="Nachtrag zum selben Rezeptfoto: ",value=previous==null?"":previous.trim();
        return value.toLowerCase(Locale.GERMAN).startsWith(prefix.trim().toLowerCase(Locale.GERMAN))?value+(value.endsWith(";")?" ":"; "):prefix;
    }
    private boolean recipePhotoRequest(String message){
        String text=message.toLowerCase(Locale.GERMAN);
        return getIntent().getBooleanExtra("recipePhoto",false)||text.contains("rezeptfoto")||text.contains("omas rezept")
                ||text.contains("rezept")&&(text.contains("maschine")||text.contains("aufbereit")||text.contains("einlesen")||text.contains("scann"));
    }

    private void localSuggestions(String request,KitchenStore store){input.setText("");bubble(true,request,true);List<KitchenLogic.Match> matches=KitchenLogic.suggestions(KitchenRecipes.all(this),store.snapshot().optJSONArray("pantry"),store.snapshot().optJSONObject("preferences"),LocalDate.now());bubble(false,matches.isEmpty()?"Der Online-Assistent ist noch nicht verbunden. In deiner gespeicherten Sammlung finde ich aktuell keinen passenden Vorschlag.":"Passende Rezepte aus deiner gespeicherten Sammlung. Diese Auswahl wird lokal aus deinem Vorrat ermittelt.",true);for(int i=0;i<Math.min(3,matches.size());i++){KitchenLogic.Match match=matches.get(i);KitViews.meal(this,messages,match.recipe,match.covered+" von "+match.total+" Zutatenarten gefunden",()->navigation().recipe(match.recipe));}if(matches.isEmpty())messages.addView(button("Rezepte entdecken",()->openTab("recipes")));bottom();}
    private void notice(String message){AlertDialog dialog=new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Verstanden",null).create();dialog.show();RotaryDialogs.bind(this,dialog);}
    private void bottom(){scroll.post(()->scroll.smoothScrollTo(0,messages.getHeight()));}
    private String historyKey(){try{return "chat-"+Pairing.fingerprint(boundUid.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private void readHistory(){history=ChatHistory.read(this,boundUid);}
    private void persistHistory(){ChatHistory.save(this,boundUid,history);}
    private final class MessengerPaper extends Drawable{private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas c){c.drawColor(Ui.BG);p.setColor(Ui.dark?0xff1e2420:0xffe8ece8);float step=Ui.dp(KitchenChatActivity.this,22);for(float x=0;x<getBounds().width();x+=step)for(float y=0;y<getBounds().height();y+=step)c.drawCircle(x,y,.65f*getResources().getDisplayMetrics().density,p);}public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}}
}
