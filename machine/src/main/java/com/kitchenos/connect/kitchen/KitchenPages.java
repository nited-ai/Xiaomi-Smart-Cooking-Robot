package com.kitchenos.connect.kitchen;

import android.app.*;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.*;
import com.kitchenos.common.*;
import com.kitchenos.connect.RecipeStore;
import com.kitchenos.connect.control.*;
import org.json.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Kalomatik-inspired product pages, backed by real local data rather than seeded examples. */
public final class KitchenPages {
    public interface Navigation {void home(String tab);void recipe(Recipe r);default void plannedRecipe(Recipe r,int servings){recipe(r);}void wifi();void receiver();void vendor();default LocalDate date(){return LocalDate.now();}default void day(LocalDate date){}}
    private final Activity a;private final Navigation nav;private final KitchenStore store;private final JSONObject data,prefs;
    private final List<Recipe> recipes;
    private static final java.util.concurrent.atomic.AtomicBoolean GENERATING=new java.util.concurrent.atomic.AtomicBoolean();
    public KitchenPages(Activity activity,Navigation navigation){a=activity;nav=navigation;store=new KitchenStore(a);data=store.snapshot();prefs=data.optJSONObject("preferences");recipes=KitchenRecipes.all(a);}
    private void note(String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
    private void error(Exception e){note(e.getMessage()==null?"Änderung konnte nicht gespeichert werden.":e.getMessage());}
    public void header(LinearLayout page,String tab){
        KitViews.brand(a,page,this::menu);
        if(tab.equals("today")||tab.equals("plan"))calendar(page);
        else KitViews.space(a,page,18);
        Ui.homeActions(a,page,()->nav.home("favorites"),this::stockDialog,()->nav.home("assistant"));
    }
    private void menu(){String[] titles={"Heute","Vorrat","Rezepte","Kochplan & Einkauf","Assistent","Küche","Mein Konto"};String[] keys={"today","pantry","recipes","plan","assistant","kitchen","account"};AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Kalomatik").setItems(titles,(d,index)->{if(keys[index].equals("account"))account();else nav.home(keys[index]);}).create();dialog.show();Ui.fitDialog(a,dialog);}
    private void calendar(LinearLayout page){
        LocalDate selected=nav.date();LinearLayout month=new LinearLayout(a);month.setGravity(android.view.Gravity.CENTER_VERTICAL);month.addView(KitViews.icon(a,"‹","Vorige Woche",()->nav.day(selected.minusDays(7))),new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,36)));TextView caption=KitViews.text(a,selected.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.GERMAN))+" · Kochplan",12,Ui.MUTED,true);caption.setGravity(android.view.Gravity.CENTER);caption.setOnClickListener(v->new DatePickerDialog(a,(view,year,m,day)->nav.day(LocalDate.of(year,m+1,day)),selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth()).show());month.addView(caption,new LinearLayout.LayoutParams(0,Ui.dp(a,36),1));month.addView(KitViews.icon(a,"›","Nächste Woche",()->nav.day(selected.plusDays(7))),new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,36)));page.addView(month);
        LinearLayout week=new LinearLayout(a);for(int i=-3;i<=3;i++){LocalDate date=selected.plusDays(i);LinearLayout day=Ui.column(a);day.setGravity(android.view.Gravity.CENTER);day.setPadding(0,Ui.dp(a,7),0,Ui.dp(a,10));TextView name=KitViews.text(a,date.format(DateTimeFormatter.ofPattern("EEEEE",Locale.GERMAN)),11,Ui.MUTED,true);name.setGravity(android.view.Gravity.CENTER);day.addView(name);TextView number=KitViews.text(a,String.valueOf(date.getDayOfMonth()),15,date.equals(selected)?(Ui.dark?0xff92d5a5:0xff287440):Ui.MUTED,true);number.setGravity(android.view.Gravity.CENTER);number.setBackground(KitViews.shape(a,date.equals(selected)?(Ui.dark?0xff23372a:0xffe7f2e9):Ui.CARD,20,Ui.LINE));LinearLayout.LayoutParams circle=new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,36));circle.topMargin=Ui.dp(a,7);day.addView(number,circle);day.setContentDescription(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)));day.setOnClickListener(v->nav.day(date));day.setFocusable(true);week.addView(day,new LinearLayout.LayoutParams(0,-2,1));}page.addView(week);
    }
    public void content(LinearLayout page,String tab){switch(tab){case "today":today(page);break;case "plan":planList(page);shopping(page);break;case "pantry":pantry(page);break;case "assistant":assistant(page);break;case "kitchen":kitchen(page);break;case "favorites":recipes(page,true);break;default:recipes(page,false);}}
    private LinearLayout card(LinearLayout page,String title){LinearLayout c=Ui.section(a,page);c.addView(Ui.title(a,title));return c;}
    private JSONArray pantry(){return data.optJSONArray("pantry");}
    private JSONArray plans(){return data.optJSONArray("plans");}
    private void today(LinearLayout page){
        int count=0,soon=0;for(int i=0;i<plans().length();i++)if(plans().optJSONObject(i).optString("date").equals(nav.date().toString()))count++;
        KitchenRingView ring=new KitchenRingView(a,count,()->nav.home("plan"));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,Ui.dp(a,208));rp.topMargin=Ui.dp(a,4);rp.bottomMargin=Ui.dp(a,4);page.addView(ring,rp);
        for(int i=0;i<pantry().length();i++){JSONObject item=pantry().optJSONObject(i);String date=item.optString("expires");if(!date.isEmpty()&&!KitchenLogic.expired(item,LocalDate.now())&&date.compareTo(LocalDate.now().plusDays(3).toString())<=0)soon++;}
        LinearLayout stats=new LinearLayout(a);String[] names={"Vorrat","Aufbrauchen","Rezepte"},values={String.valueOf(pantry().length()),String.valueOf(soon),String.valueOf(recipes.size())},hints={"Lebensmittel","in drei Tagen","gespeichert"},destinations={"pantry","pantry","recipes"};for(int i=0;i<3;i++){final String target=destinations[i];LinearLayout stat=Ui.column(a);stat.addView(KitViews.text(a,names[i],12,i==1?Ui.ACCENT:Ui.MUTED,true));KitViews.space(a,stat,8);stat.addView(KitViews.text(a,values[i],26,Ui.INK,true));TextView hint=KitViews.text(a,hints[i],11,Ui.MUTED,false);hint.setPadding(0,Ui.dp(a,4),0,Ui.dp(a,10));stat.addView(hint);stat.setOnClickListener(v->nav.home(target));stats.addView(stat,new LinearLayout.LayoutParams(0,-2,1));}page.addView(stats);KitViews.divider(a,page);
        List<KitchenLogic.Need> needs=KitchenLogic.shopping(recipes,plans(),pantry(),LocalDate.now());KitViews.row(a,page,"Einkaufsliste","Aus deinem Kochplan",needs.size()+" offen ›",()->nav.home("plan"));
        KitViews.heading(a,page,"Deine Gerichte","Plan besprechen",()->nav.home("assistant"));Map<String,Recipe> known=new HashMap<>();for(Recipe r:recipes)known.put(r.id,r);boolean planned=false;
        for(int i=0;i<plans().length();i++){JSONObject plan=plans().optJSONObject(i);Recipe r=known.get(plan.optString("recipeId"));if(r!=null&&plan.optString("date").equals(nav.date().toString())){planned=true;KitViews.meal(a,page,r,plan.optInt("servings")+" Portionen geplant",()->nav.recipe(r));}}
        if(!planned){TextView empty=KitViews.text(a,"Plane deinen Tag",19,Ui.INK,true);empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(0,Ui.dp(a,25),0,Ui.dp(a,8));page.addView(empty);TextView help=KitViews.text(a,"Besprich deinen Plan mit dem Assistenten oder wähle ein Rezept aus deiner Sammlung.",14,Ui.MUTED,false);help.setGravity(android.view.Gravity.CENTER);page.addView(help);}
        List<KitchenLogic.Match> suggestions=KitchenLogic.suggestions(recipes,pantry(),prefs,LocalDate.now());if(!suggestions.isEmpty()){KitViews.heading(a,page,"Passt zu deinem Vorrat",null,null);KitchenLogic.Match match=suggestions.get(0);KitViews.meal(a,page,match.recipe,match.covered+" von "+match.total+" Zutatenarten gefunden · Mengen prüfen",()->nav.recipe(match.recipe));}
    }
    private void pantry(LinearLayout page){
        KitViews.heading(a,page,"Dein Vorrat","Hinzufügen",this::stockDialog);
        TextView info=KitViews.text(a,"Was ist da? Plane mit dem, was du bereits zu Hause hast.",14,Ui.MUTED,false);page.addView(info);KitViews.space(a,page,14);
        if(pantry().length()==0)page.addView(Ui.text(a,"Trage dein erstes Lebensmittel über das Plus ein. Mengen und Aufbrauchdatum kannst du ergänzen.",15));
        for(int i=0;i<pantry().length();i++){JSONObject item=pantry().optJSONObject(i);String amount=item.optString("amount"),expires=item.optString("expires");String detail=amount.isEmpty()?"Menge offen":amount+" "+item.optString("unit");if(!expires.isEmpty())detail+=" · "+(KitchenLogic.expired(item,LocalDate.now())?"Datum prüfen: ":"Aufbrauchen bis ")+expires;KitViews.row(a,page,item.optString("name"),detail,null,()->{AlertDialog dialog=new AlertDialog.Builder(a).setTitle(item.optString("name")).setItems(new String[]{"Aufgebraucht / entfernen"},(d,w)->{try{store.removeStock(item.optString("id"));nav.home("pantry");}catch(Exception e){error(e);}}).create();dialog.show();Ui.fitDialog(a,dialog);});}
    }
    private boolean favorite(String id){JSONArray list=data.optJSONArray("favorites");if(list!=null)for(int i=0;i<list.length();i++)if(id.equals(list.optString(i)))return true;return false;}
    private void recipes(LinearLayout page,boolean favoritesOnly){
        KitViews.heading(a,page,favoritesOnly?"Favoriten":"Deine Rezepte",favoritesOnly?"Alle Rezepte":"Entdecken",()->{if(favoritesOnly)nav.home("recipes");else CatalogueDialog.show(a,nav);});
        if(!favoritesOnly){KitViews.row(a,page,"Kalomatik-Rezeptdatenbank","Rezepte aus der gemeinsamen Sammlung",null,()->CatalogueDialog.show(a,nav));KitViews.row(a,page,"Vom Handy empfangen","Rezepte über das Teilen-Menü übernehmen",null,()->nav.receiver());}
        EditText search=Ui.input(a,"Rezepte suchen",false);page.addView(search);KitViews.space(a,page,14);LinearLayout list=Ui.column(a);page.addView(list);Runnable render=()->{list.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.GERMAN);int shown=0;for(Recipe recipe:recipes){if(favoritesOnly&&!favorite(recipe.id)||!recipe.title.toLowerCase(Locale.GERMAN).contains(query))continue;shown++;JSONObject feedback=data.optJSONObject("feedback").optJSONObject(recipe.id);String note="";try{if(feedback!=null&&KitchenLogic.digest(recipe).equals(feedback.optString("digest")))note=feedback.optBoolean("worked")?"Von dir gekocht und lokal bestätigt":"Deine Kocherfahrung ist gespeichert";}catch(Exception ignored){}LinearLayout row=KitViews.meal(a,list,recipe,note,()->nav.recipe(recipe));row.setOnLongClickListener(v->{recipeMenu(recipe);return true;});}if(shown==0)list.addView(Ui.text(a,favoritesOnly?"Merke dir ein Rezept über das Herz im Rezeptdetail.":"Keine passenden Rezepte. Entdecke die Sammlung oder teile ein Rezept vom Handy.",15));};search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});render.run();
    }
    public void recipeMenu(Recipe recipe){String[] options={"Zum Kochplan hinzufügen","Rezept bearbeiten",favorite(recipe.id)?"Favorit entfernen":"Als Favorit merken","Gekocht? Rückmeldung geben","Zur gemeinsamen Sammlung beitragen"};AlertDialog dialog=new AlertDialog.Builder(a).setTitle(recipe.title).setItems(options,(d,w)->{if(w==0)planDialog(recipe);else if(w==1)editRecipe(recipe);else if(w==2)toggleFavorite(recipe);else if(w==3)feedback(recipe);else contribute(recipe);}).create();dialog.show();Ui.fitDialog(a,dialog);}
    public void toggleFavorite(Recipe recipe){try{store.favorite(recipe.id,!favorite(recipe.id));nav.recipe(recipe);}catch(Exception e){error(e);}}
    public boolean isFavorite(Recipe recipe){return favorite(recipe.id);}
    public void requestRecipe(String request,TextView status){generate(request,status);}
    private void assistant(LinearLayout page){
        page.addView(Ui.title(a,"Dein Küchenassistent"));LinearLayout intro=card(page,"Was möchtest du kochen?");intro.addView(Ui.text(a,"Ich beziehe deinen Vorrat, geplante Gerichte und bestätigte Vorlieben ein. Gespeicherte Rezepte kann ich dir schon lokal vorschlagen.",16));
        EditText request=Ui.input(a,"Zum Beispiel: etwas Schnelles aus meinem Vorrat",true);intro.addView(request);TextView status=Ui.text(a,"",17);intro.addView(status);
        intro.addView(Ui.primary(a,"Neue Rezepte mit KI entwickeln",v->generate(request.getText().toString(),status)));
        intro.addView(Ui.button(a,"Vorlieben & Ernährung",v->preferences()));
        page.addView(Ui.text(a,"Vorschläge aus deiner Sammlung",24));List<KitchenLogic.Match> matches=KitchenLogic.suggestions(recipes,pantry(),prefs,LocalDate.now());
        if(matches.isEmpty())page.addView(Ui.text(a,"Für lokale Vorschläge fehlen noch Rezepte mit Zutaten. Teile ein Rezept mit der Maschine und ergänze deinen Vorrat.",20));
        for(KitchenLogic.Match match:matches){LinearLayout c=card(page,match.recipe.title);c.addView(Ui.text(a,match.covered+" von "+match.total+" Zutaten gefunden"+(match.expiresSoon?" · hilft beim Aufbrauchen":""),19));if(!match.missing.isEmpty())c.addView(Ui.text(a,"Noch prüfen / einkaufen: "+android.text.TextUtils.join(", ",match.missing),17));c.addView(Ui.button(a,"Ansehen",v->nav.recipe(match.recipe)));c.addView(Ui.button(a,"Einplanen",v->planDialog(match.recipe)));}
        if(!prefs.optString("allergies").isEmpty())page.addView(Ui.text(a,"Allergien sind in deinen Präferenzen gespeichert. Ein lokaler Zutatenvergleich kann keine Allergensicherheit bestätigen; Rezept und Verpackungen prüfen.",16));
    }
    private void kitchen(LinearLayout page){
        KitViews.heading(a,page,"Deine Küche",null,null);
        KitViews.row(a,page,"Manuell kochen","Zeit, Stufe und Temperatur",null,()->a.startActivity(new Intent(a,ControlActivity.class)));
        KitViews.row(a,page,"WLAN","Netzwerk und Verbindung",null,()->nav.wifi());
        KitViews.row(a,page,"Handy & Rezeptempfang","Verbinden und Rezepte teilen",null,()->nav.receiver());
        KitViews.row(a,page,"Mein Konto","Anmeldung und gemeinsame Sammlung",null,this::account);
        KitViews.row(a,page,"Vorlieben & Ernährung","So koche ich gern",null,this::preferences);
        KitViews.row(a,page,"Darstellung","Hell, Dunkel oder wie das Gerät",null,()->new AlertDialog.Builder(a).setTitle("Darstellung").setItems(new String[]{"Hell","Dunkel","Wie das Gerät"},(d,w)->{a.getSharedPreferences("appearance",0).edit().putString("theme",new String[]{"light","dark","system"}[w]).apply();nav.home("kitchen");}).show());
        KitViews.row(a,page,"Xiaomi-Kochfunktionen","Vorhandene Programme öffnen",null,()->nav.vendor());
        KitViews.space(a,page,24);page.addView(KitViews.text(a,"Kalomatik Küche · Vorschau 0.3",12,Ui.MUTED,false));
    }
    public void stockDialog(){
        stockDialog(null);
    }
    private void stockDialog(KitchenLogic.Need need){
        LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);EditText name=Ui.input(a,"Lebensmittel",false),amount=Ui.input(a,"Menge (optional)",false),expiry=Ui.input(a,"Aufbrauchen bis: JJJJ-MM-TT (optional)",false);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);Spinner unit=new Spinner(a);unit.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,KitchenLogic.UNITS));box.addView(name);box.addView(amount);box.addView(unit);box.addView(expiry);
        if(need!=null){name.setText(need.name);if(need.amount!=null)amount.setText(need.amount.stripTrailingZeros().toPlainString());for(int i=0;i<KitchenLogic.UNITS.length;i++)if(KitchenLogic.UNITS[i].equalsIgnoreCase(need.unit))unit.setSelection(i);}
        AlertDialog d=new AlertDialog.Builder(a).setTitle(need==null?"Vorrat ergänzen":"Gekaufte Menge eintragen").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{store.addStock(name.getText().toString(),amount.getText().toString(),unit.getSelectedItem().toString(),expiry.getText().toString().trim());d.dismiss();nav.home("pantry");}catch(Exception e){error(e);}}));d.show();Ui.fitDialog(a,d);
    }
    public void planDialog(Recipe recipe){
        LinearLayout box=Ui.column(a);box.setPadding(Ui.dp(a,20),Ui.dp(a,10),Ui.dp(a,20),Ui.dp(a,16));final LocalDate[] selected={LocalDate.now()};Button date=Ui.button(a,"Heute · "+selected[0].format(DateTimeFormatter.ofPattern("d. MMMM",Locale.GERMAN)),null);date.setOnClickListener(v->{DatePickerDialog picker=new DatePickerDialog(a,(view,year,month,day)->{selected[0]=LocalDate.of(year,month+1,day);date.setText(selected[0].format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)));},selected[0].getYear(),selected[0].getMonthValue()-1,selected[0].getDayOfMonth());picker.getDatePicker().setMinDate(System.currentTimeMillis()-1000);picker.show();});box.addView(Ui.text(a,"Wann möchtest du kochen?",23));box.addView(date);com.kitchenos.connect.ui.ParameterDial portions=new com.kitchenos.connect.ui.ParameterDial(a,"Portionen",1,6,1,Math.min(6,prefs.optInt("servings",2)));box.addView(portions,new LinearLayout.LayoutParams(-1,Ui.dp(a,220)));
        final com.kitchenos.connect.ui.ParameterDial original=recipe.servings==0?new com.kitchenos.connect.ui.ParameterDial(a,"Originalportionen",1,20,1,2):null;if(original!=null){box.addView(Ui.text(a,"Für wie viele Portionen sind die Mengen im Originalrezept angegeben?",21));original.unknown();box.addView(original,new LinearLayout.LayoutParams(-1,Ui.dp(a,220)));}else box.addView(Ui.text(a,"Die Zutatenliste gilt für "+recipe.servings+" Portionen. Deine Einkaufsliste wird passend berechnet.",19));
        ScrollView scroll=new ScrollView(a);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(a).setTitle(recipe.title+" einplanen").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Einplanen",null).create();dialog.setOnShowListener(v->{if(original!=null){dialog.getButton(-1).setEnabled(false);original.onChanged(value->dialog.getButton(-1).setEnabled(true));}dialog.getButton(-1).setOnClickListener(w->{try{if(original!=null&&!original.known())return;store.plan(recipe.id,selected[0].toString(),portions.value(),recipe.servings>0?recipe.servings:original.value());dialog.dismiss();nav.home("plan");}catch(Exception e){error(e);}});});dialog.show();Ui.fitDialog(a,dialog);
    }
    public void editRecipe(Recipe recipe){
        LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);EditText title=Ui.input(a,"Rezepttitel",false),portions=Ui.input(a,"Portionen (optional)",false),ingredients=Ui.input(a,"Zutaten – eine pro Zeile",true),steps=Ui.input(a,"Schritte – mit Leerzeile trennen",true);title.setText(recipe.title);portions.setInputType(InputType.TYPE_CLASS_NUMBER);if(recipe.servings>0)portions.setText(String.valueOf(recipe.servings));ingredients.setText(android.text.TextUtils.join("\n",jsonLines(recipe.ingredients)));steps.setText(android.text.TextUtils.join("\n\n",jsonLines(recipe.steps)));box.addView(title);box.addView(portions);box.addView(ingredients);box.addView(steps);ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Rezept verbessern").setMessage("Deine Änderung wird als neue Fassung gespeichert. Kocherfahrungen bleiben bei der bisherigen Fassung.").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Neue Fassung speichern",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{String count=portions.getText().toString().trim();Recipe edited=new Recipe(UUID.randomUUID().toString(),title.getText().toString(),recipe.source,new JSONArray(Arrays.asList(ingredients.getText().toString().split("\\r?\\n"))),new JSONArray(Arrays.asList(steps.getText().toString().split("\\r?\\n\\s*\\r?\\n"))),count.isEmpty()?0:Integer.parseInt(count));String uid=new CloudSession(a).account().optString("uid");if(uid.isEmpty())new RecipeStore(a).save(edited);else KitchenRecipes.savePrivate(a,edited,uid);dialog.dismiss();nav.recipe(edited);}catch(Exception e){error(e);}}));dialog.show();Ui.fitDialog(a,dialog);
    }
    private void planList(LinearLayout page){
        KitViews.heading(a,page,"Dein Kochplan","Rezept wählen",()->nav.home("recipes"));Map<String,Recipe> byId=new HashMap<>();for(Recipe r:recipes)byId.put(r.id,r);boolean shown=false;
        for(int i=0;i<plans().length();i++){JSONObject plan=plans().optJSONObject(i);if(plan.optString("date").compareTo(LocalDate.now().toString())<0)continue;shown=true;Recipe r=byId.get(plan.optString("recipeId"));if(r!=null){LinearLayout row=KitViews.meal(a,page,r,plan.optString("date")+" · "+plan.optInt("servings")+" Portionen geplant",()->nav.plannedRecipe(r,plan.optInt("servings")));row.setOnLongClickListener(v->{new AlertDialog.Builder(a).setTitle(r.title).setItems(new String[]{"Aus dem Plan nehmen"},(d,w)->{try{store.removePlan(plan.optString("id"));nav.home("plan");}catch(Exception e){error(e);}}).show();return true;});}else KitViews.row(a,page,"Rezept nicht mehr vorhanden",plan.optString("date"),"Entfernen",()->{try{store.removePlan(plan.optString("id"));nav.home("plan");}catch(Exception e){error(e);}});}
        if(!shown)page.addView(Ui.text(a,"Wähle ein Rezept und plane Datum und Portionen. Daraus entsteht deine Einkaufsliste.",15));
    }
    private void shopping(LinearLayout page){
        KitViews.heading(a,page,"Einkaufsliste",null,null);List<KitchenLogic.Need> needs=KitchenLogic.shopping(recipes,plans(),pantry(),LocalDate.now());
        if(needs.isEmpty())page.addView(Ui.text(a,plans().length()==0?"Plane ein Gericht, um fehlende Zutaten zu sehen.":"Für bekannte Planmengen ist nichts zusätzlich vorgemerkt. Unklare Mengen bitte im Rezept prüfen.",15));
        for(KitchenLogic.Need need:needs)KitViews.row(a,page,need.display(),"Für: "+android.text.TextUtils.join(", ",need.dishes),null,()->stockDialog(need));
    }
    public void preferences(){com.kitchenos.connect.ui.AssistantPreferencesDialog.show(a);}

    public void account(){com.kitchenos.connect.ui.AccountDialog.show(a,()->nav.home("today"));}

    private void feedback(Recipe recipe){
        LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);CheckBox cooked=new CheckBox(a);cooked.setText("Ich habe dieses Rezept selbst gekocht");box.addView(cooked);CheckBox worked=new CheckBox(a);worked.setText("Es hat funktioniert");box.addView(worked);RatingBar stars=new RatingBar(a);stars.setNumStars(5);stars.setStepSize(1);stars.setRating(4);box.addView(stars);EditText comment=Ui.input(a,"Private Notiz zu deiner Kocherfahrung",true);box.addView(comment);
        JSONObject mappings=data.optJSONObject("onlineRecipes"),mapping=mappings==null?null:mappings.optJSONObject(recipe.id);boolean linked=false;try{linked=mapping!=null&&KitchenLogic.digest(recipe).equals(mapping.optString("digest"));}catch(Exception ignored){}
        CheckBox share=new CheckBox(a);share.setText("Sterne und Kocherfolg auch zur Community senden (keine private Notiz)");share.setEnabled(linked&&prefs.optBoolean("communityConsent")&&CloudSession.configured());box.addView(share);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Deine Kocherfahrung").setMessage("Jedes Konto zählt pro Rezeptfassung einmal. Die Rückmeldung beschreibt deine eigene Kocherfahrung.").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Rückmeldung speichern",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{if(!cooked.isChecked())throw new IllegalArgumentException("Bitte nur nach dem eigenen Kochen bestätigen.");int rating=Math.round(stars.getRating());store.feedback(recipe.id,KitchenLogic.digest(recipe),rating,worked.isChecked(),comment.getText().toString());dialog.dismiss();if(share.isChecked())sendFeedback(mapping,rating,worked.isChecked());else{nav.home("recipes");note("Deine Kocherfahrung wurde lokal gespeichert.");}}catch(Exception e){error(e);}}));dialog.show();Ui.fitDialog(a,dialog);
    }
    private void sendFeedback(JSONObject mapping,int rating,boolean worked){
        CloudSession session=new CloudSession(a);String uid=session.account().optString("uid");
        new Thread(()->{try{session.api("/v1/recipes/validate",new JSONObject().put("catalogId",mapping.getString("catalogId")).put("revision",mapping.getString("revision")).put("rating",rating).put("cooked",true).put("worked",worked));a.runOnUiThread(()->{if(!a.isDestroyed()&&uid.equals(new CloudSession(a).account().optString("uid"))){nav.home("recipes");note("Deine Community-Rückmeldung wurde bestätigt.");}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed())note("Lokal gespeichert. Die Online-Übertragung ist fehlgeschlagen; bei Bedarf erneut senden.");});}},"KitchenOS-Feedback").start();
    }
    private void contribute(Recipe recipe){
        CloudSession session=new CloudSession(a);String uid=session.account().optString("uid");if(!CloudSession.configured()||uid.isEmpty()){account();return;}
        if(!prefs.optBoolean("communityConsent")){note("Bitte in deinen Vorlieben Rezeptbeiträge ermöglichen.");return;}
        if(recipe.servings==0||recipe.ingredients.length()==0||recipe.steps.length()==0){note("Für einen Beitrag brauchen wir Zutaten, Schritte und eine bestätigte Portionsbasis.");return;}
        LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);box.addView(Ui.text(a,recipe.title+" · "+recipe.servings+" Portionen",22));if(!recipe.source.isEmpty())box.addView(Ui.text(a,"Quelle: "+recipe.source,16));box.addView(Ui.text(a,"Zutaten\n"+android.text.TextUtils.join("\n",jsonLines(recipe.ingredients))+"\n\nZubereitung\n"+android.text.TextUtils.join("\n",jsonLines(recipe.steps)),18));ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Dieses Rezept zur Sammlung beitragen?").setMessage("Bitte prüfe den sichtbaren Inhalt. Persönliche Angaben kannst du vorher unter ‚Rezept bearbeiten‘ entfernen. Übertragen werden Titel, Quelle, Zutaten, Portionen und Schritte. Der Beitrag beginnt als unbestätigter Entwurf.").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Beitragen",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{dialog.getButton(-1).setEnabled(false);new Thread(()->{try{JSONObject result=session.api("/v1/recipes/contribute",new JSONObject().put("consent",true).put("recipe",recipe.json()));a.runOnUiThread(()->{if(a.isDestroyed())return;try{if(!uid.equals(new CloudSession(a).account().optString("uid")))throw new IllegalStateException("Dein Konto hat sich geändert");new KitchenStore(a).onlineRecipe(recipe.id,KitchenLogic.digest(recipe),result.getString("catalogId"),result.getString("revision"));dialog.dismiss();nav.home("recipes");note("Beitrag in der gemeinsamen Sammlung gespeichert. Noch keine bestätigte Kocherfahrung.");}catch(Exception e){error(e);}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()){dialog.getButton(-1).setEnabled(true);error(e);}});}},"KitchenOS-Contribution").start();}));dialog.show();Ui.fitDialog(a,dialog);
    }
    private static List<String> jsonLines(JSONArray lines){List<String> result=new ArrayList<>();for(int i=0;i<lines.length();i++)result.add(lines.optString(i));return result;}
    private void onlineLibrary(){
        if(!CloudSession.configured()||new CloudSession(a).account().optString("uid").isEmpty()){account();return;}
        new AlertDialog.Builder(a).setTitle("Online-Rezepte").setItems(new String[]{"Gemeinsame Sammlung","Meine KI-Entwürfe"},(d,w)->loadOnline(w==1)).show();
    }
    private void loadOnline(boolean drafts){
        CloudSession session=new CloudSession(a);String uid=session.account().optString("uid");ProgressDialog progress=ProgressDialog.show(a,"Rezepte laden","Einen Moment …",true,false);
        new Thread(()->{try{JSONArray rows=session.api(drafts?"/v1/recipes/drafts":"/v1/recipes/catalog",new JSONObject()).getJSONArray("recipes");a.runOnUiThread(()->{if(a.isDestroyed())return;progress.dismiss();if(!uid.equals(new CloudSession(a).account().optString("uid")))return;
            LinearLayout box=Ui.column(a);box.setPadding(24,8,24,12);if(rows.length()==0)box.addView(Ui.text(a,"Hier gibt es noch keine Rezepte.",20));AlertDialog dialog=new AlertDialog.Builder(a).setTitle(drafts?"Meine KI-Entwürfe":"Gemeinsame Sammlung").setPositiveButton("Schließen",null).create();
            for(int i=0;i<rows.length();i++)try{JSONObject row=rows.getJSONObject(i);Recipe r=Recipe.parse(row.getJSONObject("recipe").toString());LinearLayout c=Ui.card(a,box);c.addView(Ui.text(a,r.title,23));c.addView(Ui.text(a,drafts?"Privater KI-Entwurf · noch nicht von dir gekocht":row.optInt("workedCount")+" Konten melden Kocherfolg · "+row.optInt("validatorCount")+" Rückmeldungen",17));c.addView(Ui.button(a,"In meine Sammlung übernehmen",v->{try{KitchenRecipes.savePrivate(a,r,uid);if(!drafts)new KitchenStore(a).onlineRecipe(r.id,KitchenLogic.digest(r),row.getString("catalogId"),row.getString("revision"));dialog.dismiss();nav.recipe(r);}catch(Exception e){error(e);}}));}catch(Exception ignored){}
            ScrollView scroll=new ScrollView(a);scroll.addView(box);dialog.setView(scroll);dialog.show();Ui.fitDialog(a,dialog);});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()){progress.dismiss();error(e);}});}},"KitchenOS-Online-Recipes").start();
    }
    private void generate(String request,TextView status){
        if(request.trim().isEmpty()){note("Sag dem Assistenten, was dir heute wichtig ist.");return;}if(request.length()>2000){note("Bitte die Anfrage etwas kürzen.");return;}
        if(!prefs.optBoolean("aiConsent")){note("Bitte in deinen Vorlieben zuerst die KI-Verarbeitung freigeben.");return;}
        CloudSession session=new CloudSession(a);String uid=session.account().optString("uid");if(!CloudSession.configured()||uid.isEmpty()){account();return;}
        if(!GENERATING.compareAndSet(false,true)){note("Eine Rezeptanfrage läuft bereits.");return;}
        long revision=data.optLong("revision");status.setText("Der Assistent entwickelt einen Rezeptentwurf …");
        new Thread(()->{String activeFingerprint="";try{
            JSONArray planned=new JSONArray();Map<String,Recipe> known=new HashMap<>();for(Recipe r:recipes)known.put(r.id,r);
            for(int i=0;i<plans().length()&&planned.length()<100;i++){JSONObject p=plans().getJSONObject(i);Recipe r=known.get(p.optString("recipeId"));if(r!=null&&p.optString("date").compareTo(LocalDate.now().toString())>=0)planned.put(new JSONObject().put("title",r.title).put("ingredients",r.ingredients).put("date",p.getString("date")).put("servings",p.getInt("servings")).put("baseServings",p.getInt("baseServings")));}
            JSONObject input=new JSONObject().put("request",request).put("pantry",pantry()).put("preferences",prefs).put("plannedRecipes",planned).put("revision",revision);
            String fingerprint=Pairing.fingerprint((uid+input.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));activeFingerprint=fingerprint;android.content.SharedPreferences jobs=a.getSharedPreferences("assistant-jobs",0);
            String requestId=jobs.getString(fingerprint,"");if(requestId.isEmpty()){requestId=UUID.randomUUID().toString();if(!jobs.edit().putString(fingerprint,requestId).commit())throw new IllegalStateException("Anfrage konnte nicht vorgemerkt werden");}
            JSONObject result=session.api("/v1/recipes/generate",input.put("requestId",requestId));Recipe recipe=Recipe.parse(result.getJSONObject("recipe").toString());
            jobs.edit().remove(fingerprint).commit();
            a.runOnUiThread(()->{if(a.isDestroyed())return;try{if(!uid.equals(new CloudSession(a).account().optString("uid"))||new KitchenStore(a).snapshot().optLong("revision")!=revision)throw new IllegalStateException("Dein Konto oder Küchenkontext hat sich geändert. Der Entwurf bleibt in deinen Online-Entwürfen verfügbar.");KitchenRecipes.savePrivate(a,recipe,uid);nav.recipe(recipe);}catch(Exception e){status.setText(e.getMessage());}});
        }catch(Exception e){if(e instanceof CloudSession.ApiException&&((CloudSession.ApiException)e).code.equals("job_failed")&&!activeFingerprint.isEmpty())a.getSharedPreferences("assistant-jobs",0).edit().remove(activeFingerprint).commit();a.runOnUiThread(()->{if(!a.isDestroyed())status.setText(e.getMessage()==null?"Der Assistent ist gerade nicht erreichbar.":e.getMessage());});}finally{GENERATING.set(false);}},"KitchenOS-Recipe-Assistant").start();
    }
}
