package com.kitchenos.share;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import com.kitchenos.common.*;

public class MainActivity extends Activity {
    private EditText content,title;
    private TextView status,device;
    private LinearLayout preview;
    private Button prepare,send;
    private Recipe pending;
    private java.io.File photo;
    private CheckBox photoStock;
    private Button choosePhoto;
    private String photoId=java.util.UUID.randomUUID().toString();
    private boolean busy;
    private String preparedContent,preparedTitle;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=Ui.page(this);page.addView(Ui.title(this,"An KitchenOS senden"));
        page.addView(Ui.text(this,"Rezept am Handy auswählen. Auf der Maschine in Ruhe zubereiten.",20));
        LinearLayout pairing=Ui.card(this,page);device=Ui.text(this,"",19);pairing.addView(device);pairing.addView(Ui.button(this,"Maschine koppeln",v->pairDialog("")));showDevice();
        choosePhoto=Ui.button(this,"Foto vom Vorrat teilen",v->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),51);}catch(Exception e){status.setText("Bildauswahl konnte nicht geöffnet werden.");}});page.addView(choosePhoto);photoStock=new CheckBox(this);photoStock.setText("Lebensmittel für meinen Vorrat erkennen");photoStock.setTextColor(Ui.INK);photoStock.setChecked(true);photoStock.setVisibility(android.view.View.GONE);page.addView(photoStock);
        title=Ui.input(this,"Rezepttitel (optional)",false);page.addView(title);
        content=Ui.input(this,"HTTPS-Rezeptlink oder vollständigen Rezepttext einfügen",true);content.setMinLines(4);page.addView(content);
        prepare=Ui.button(this,"Rezept vorbereiten",v->prepare());page.addView(prepare);
        status=Ui.text(this,"Im Browser oder in einer Rezept-App: Teilen → An KitchenOS senden.",17);page.addView(status);
        preview=Ui.column(this);page.addView(preview);send=Ui.button(this,"An die Maschine senden",v->send());send.setEnabled(false);page.addView(send);
        if(state!=null){content.setText(state.getString("text",""));title.setText(state.getString("title",""));try{pending=Recipe.parse(state.getString("recipe",""));preparedContent=state.getString("preparedContent");preparedTitle=state.getString("preparedTitle");render();}catch(Exception ignored){}}
        else accept(getIntent());
        if(state!=null&&!state.getString("photo","").isEmpty())try{photo=PhotoPixels.restore(this,state.getString("photo"));photoId=state.getString("photoId",photoId);photoStock.setChecked(state.getBoolean("photoStock",true));renderPhoto();}catch(Exception ignored){}
    }
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);accept(intent);}
    private void accept(Intent intent){
        if(Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null){pairDialog(intent.getData().toString());return;}
        if(Intent.ACTION_SEND.equals(intent.getAction())&&intent.getType()!=null&&intent.getType().startsWith("image/")){android.net.Uri image=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(image!=null)loadPhoto(image);return;}
        if(Intent.ACTION_SEND.equals(intent.getAction())){photo=null;title.setVisibility(android.view.View.VISIBLE);content.setVisibility(android.view.View.VISIBLE);prepare.setVisibility(android.view.View.VISIBLE);photoStock.setVisibility(android.view.View.GONE);
            CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(shared!=null&&shared.length()<=100000){content.setText(shared);title.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));pending=null;preview.removeAllViews();send.setEnabled(false);status.setText("Geteiltes Rezept prüfen und vorbereiten.");}
            else status.setText("Diese Freigabe enthält keinen unterstützten Rezepttext.");
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==51&&result==RESULT_OK&&data!=null&&data.getData()!=null)loadPhoto(data.getData());}
    private void loadPhoto(android.net.Uri uri){if(busy)return;busy(true);status.setText("Foto wird vorbereitet und Standortdaten werden entfernt …");new Thread(()->{try{java.io.File file=PhotoPixels.importPhoto(this,uri);runOnUiThread(()->{if(isDestroyed()){file.delete();return;}if(photo!=null)photo.delete();photo=file;photoId=java.util.UUID.randomUUID().toString();pending=null;busy(false);renderPhoto();});}catch(Exception e){runOnUiThread(()->{busy(false);status.setText(e.getMessage());});}},"KitchenOS-Photo").start();}
    private void renderPhoto(){title.setVisibility(android.view.View.GONE);content.setVisibility(android.view.View.GONE);prepare.setVisibility(android.view.View.GONE);photoStock.setVisibility(android.view.View.VISIBLE);preview.removeAllViews();ImageView image=new ImageView(this);image.setImageURI(android.net.Uri.fromFile(photo));image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Foto zum Teilen");preview.addView(image,new LinearLayout.LayoutParams(-1,Ui.dp(this,260)));send.setEnabled(!busy);status.setText("Das Foto wird verschlüsselt als Entwurf an deine Maschine gesendet. Dort im Kalomatik-Assistenten prüfen und senden.");}
    private void sendPhoto(){final Pairing pairing;try{pairing=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));}catch(Exception e){pairDialog("");return;}final java.io.File file=photo;final String id=photoId,intent=photoStock.isChecked()?"pantry":"chat";busy(true);status.setText("Foto wird verschlüsselt gesendet …");new Thread(()->{try{RecipeSender.sendPhoto(this,pairing,new org.json.JSONObject().put("version",1).put("id",id).put("intent",intent).put("image",PhotoPixels.payload(file)));runOnUiThread(()->{if(!isDestroyed()){busy(false);status.setText("Foto auf der Maschine angekommen. Öffne dort den Assistenten und wähle Büroklammer → Fotos vom Handy.");}});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){busy(false);status.setText("Foto konnte nicht gesendet werden. "+e.getMessage());}});}},"KitchenOS-Photo-Send").start();}
    private void showDevice(){try{Pairing p=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));device.setText("Gekoppelt mit "+p.host);}catch(Exception e){device.setText("Noch keine Maschine gekoppelt");}}
    private void pairDialog(String incoming){
        if(!incoming.isEmpty()){openSetup(incoming);return;}
        EditText code=Ui.input(this,"kitchenos://pair?…",true);code.setText(incoming);code.setSaveEnabled(false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Mit deiner Maschine koppeln").setMessage("Auf der Maschine: Menü ··· → Handy verbinden. QR-Code mit der Kamera öffnen oder den Kopplungscode hier einfügen. Prüfe, dass der Code von deiner Maschine stammt.").setView(code).setPositiveButton("Koppeln",null).setNegativeButton("Abbrechen",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{openSetup(code.getText().toString());d.dismiss();}));d.show();
    }
    private void openSetup(String value){try{android.net.Uri uri=android.net.Uri.parse(value.trim());if(!"kitchenos".equals(uri.getScheme())||!("pair".equals(uri.getHost())||"setup".equals(uri.getHost())))throw new IllegalArgumentException();startActivity(new Intent(this,PhoneSetupActivity.class).setData(uri));}catch(Exception error){Toast.makeText(this,"Kopplungscode ungültig oder unvollständig",Toast.LENGTH_LONG).show();}}
    @Override protected void onResume(){super.onResume();if(device!=null)showDevice();}
    private void busy(boolean value){busy=value;prepare.setEnabled(!value);send.setEnabled(!value&&(pending!=null||photo!=null));choosePhoto.setEnabled(!value);photoStock.setEnabled(!value);content.setEnabled(!value);title.setEnabled(!value);}
    private void prepare(){
        if(busy)return;String text=content.getText().toString(),name=title.getText().toString();busy(true);status.setText("Rezept wird gelesen …");
        new Thread(()->{try{Recipe recipe=RecipeImporter.prepare(text,name);runOnUiThread(()->{if(isDestroyed())return;pending=recipe;preparedContent=text;preparedTitle=name;busy(false);render();status.setText("Rezept prüfen. Anschließend an die Maschine senden.");});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;pending=null;preview.removeAllViews();busy(false);status.setText(e.getMessage()==null?"Rezept konnte nicht gelesen werden.":e.getMessage());});}},"KitchenOS-Import").start();
    }
    private void render(){preview.removeAllViews();if(pending==null)return;preview.addView(Ui.title(this,pending.title));
        if(pending.ingredients.length()>0){preview.addView(Ui.text(this,"Zutaten",22));for(int i=0;i<pending.ingredients.length();i++)preview.addView(Ui.text(this,"• "+pending.ingredients.optString(i),18));}
        for(int i=0;i<pending.steps.length();i++)preview.addView(Ui.text(this,(i+1)+". "+pending.steps.optString(i),18));send.setEnabled(!busy);
    }
    private void send(){
        if(busy)return;if(photo!=null){sendPhoto();return;}if(pending==null)return;
        if(!content.getText().toString().equals(preparedContent)||!title.getText().toString().equals(preparedTitle)){status.setText("Text wurde geändert. Bitte Rezept erneut vorbereiten.");return;}
        final Pairing pairing;try{pairing=Pairing.parse(getSharedPreferences("pairing",0).getString("uri",""));}catch(Exception e){pairDialog("");return;}
        Recipe recipe=pending;busy(true);status.setText("Rezept wird verschlüsselt gesendet …");
        new Thread(()->{try{RecipeSender.send(this,pairing,recipe);runOnUiThread(()->{if(isDestroyed())return;busy(false);status.setText("✓ Auf der Maschine gespeichert: "+recipe.title);});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy(false);status.setText("Senden fehlgeschlagen. WLAN, Empfang und Kopplung prüfen. Du kannst dasselbe Rezept erneut senden.\n"+(e.getMessage()==null?"":e.getMessage()));});}},"KitchenOS-Send").start();
    }
    @Override protected void onSaveInstanceState(Bundle b){b.putString("text",content.getText().toString());if(photo!=null){b.putString("photo",photo.getAbsolutePath());b.putString("photoId",photoId);b.putBoolean("photoStock",photoStock.isChecked());}b.putString("title",title.getText().toString());if(pending!=null)try{b.putString("recipe",pending.json().toString());b.putString("preparedContent",preparedContent);b.putString("preparedTitle",preparedTitle);}catch(Exception ignored){}super.onSaveInstanceState(b);}
}
