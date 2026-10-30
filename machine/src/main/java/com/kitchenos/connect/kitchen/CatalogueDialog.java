package com.kitchenos.connect.kitchen;

import android.app.*;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.RecipeStore;
import org.json.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit, public database search. No Google account or private preferences are transmitted. */
public final class CatalogueDialog {
    public static void show(Activity a,KitchenPages.Navigation nav){new CatalogueDialog(a,nav).show();}
    private final Activity a;private final KitchenPages.Navigation nav;private final KalomatikCatalogue catalogue=new KalomatikCatalogue();
    private final AtomicInteger generation=new AtomicInteger();private AlertDialog dialog;private LinearLayout results;private TextView status;private EditText query;
    private CatalogueDialog(Activity a,KitchenPages.Navigation nav){this.a=a;this.nav=nav;}
    private void show(){
        LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);box.addView(Ui.text(a,"Rezepte aus der gemeinsamen Kalomatik-Datenbank. Hier werden vollständige Kochanleitungen angezeigt.",16));query=Ui.input(a,"Gericht oder Zutat suchen",false);box.addView(query);box.addView(Ui.button(a,"Rezepte suchen",v->load(query.getText().toString().trim(),"",false)));status=Ui.text(a,"",14);box.addView(status);results=Ui.column(a);box.addView(results);ScrollView scroll=new ScrollView(a);scroll.addView(box);
        dialog=new AlertDialog.Builder(a).setTitle("Kalomatik-Rezeptdatenbank").setView(scroll).setPositiveButton("Schließen",null).create();dialog.setOnDismissListener(d->generation.incrementAndGet());dialog.show();Ui.fitDialog(a,dialog);load("","",false);
    }
    private void load(String term,String cursor,boolean append){
        final int request=generation.incrementAndGet();status.setText("Rezepte werden geladen …");if(!append)results.removeAllViews();
        new Thread(()->{try{JSONObject body=catalogue.search(term,cursor);JSONArray items=body.getJSONArray("items");if(items.length()>12)throw new JSONException("Zu viele Suchergebnisse");a.runOnUiThread(()->{if(a.isDestroyed()||generation.get()!=request)return;try{status.setText(items.length()==0?"Keine passenden vollständigen Rezepte gefunden.":"Aus der gemeinsamen Kalomatik-Sammlung");for(int i=0;i<items.length();i++){JSONObject row=items.getJSONObject(i);String id=row.getString("id"),title=row.getString("title");if(!id.matches("[a-f0-9]{64}")||title.length()>160)continue;LinearLayout card=Ui.section(a,results);card.addView(Ui.title(a,title));JSONObject rating=row.optJSONObject("rating");int count=rating==null?0:rating.optInt("count");card.addView(Ui.text(a,count>0?count+" Bewertungen · Ø "+String.format(java.util.Locale.GERMAN,"%.1f",rating.optDouble("average")):"Noch keine Bewertungen",14));Button open=Ui.button(a,"Rezept übernehmen & öffnen",v->open(id));card.addView(open);}String next=body.optString("nextCursor","");if(!next.isEmpty()&&!next.equals("null"))results.addView(Ui.button(a,"Weitere Rezepte",v->load(term,next,true)));}catch(Exception e){status.setText("Die Katalogantwort konnte nicht gelesen werden.");}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()&&generation.get()==request)status.setText(e.getMessage()==null?"Die Rezeptdatenbank ist gerade nicht erreichbar.":e.getMessage());});}},"KitchenOS-Public-Catalogue").start();
    }
    private void open(String id){
        final int request=generation.incrementAndGet();String uid=new CloudSession(a).account().optString("uid");status.setText("Kochanleitung wird geladen …");
        new Thread(()->{try{Recipe recipe=catalogue.detail(id);a.runOnUiThread(()->{if(a.isDestroyed()||generation.get()!=request)return;try{if(!uid.equals(new CloudSession(a).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert");if(uid.isEmpty())new RecipeStore(a).save(recipe);else KitchenRecipes.savePrivate(a,recipe,uid);dialog.dismiss();nav.recipe(recipe);}catch(Exception e){status.setText(e.getMessage());}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()&&generation.get()==request)status.setText(e.getMessage()==null?"Die Kochanleitung ist gerade nicht verfügbar.":e.getMessage());});}},"KitchenOS-Catalogue-Recipe").start();
    }
}
