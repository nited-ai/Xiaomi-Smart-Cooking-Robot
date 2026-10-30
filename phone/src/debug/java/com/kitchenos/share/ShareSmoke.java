package com.kitchenos.share;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Intent;
import com.kitchenos.common.*;
import org.json.*;

public class ShareSmoke extends Instrumentation {
    private String pairing;
    @Override public void onCreate(Bundle args){super.onCreate(args);pairing=args.getString("pairing","");start();}
    private static void check(boolean value,String message)throws Exception{if(!value)throw new Exception(message);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String html="<script type='application/ld+json'>{\"@graph\":[{\"@type\":[\"Recipe\"],\"name\":\"Äpfel &amp; Öl\",\"recipeIngredient\":[\"200 g Äpfel\"],\"recipeInstructions\":[{\"@type\":\"HowToSection\",\"itemListElement\":[{\"@type\":\"HowToStep\",\"text\":\"<b>Waschen</b> und schneiden.\"}]}]}]}</script>";
        Recipe r=RecipeImporter.fromHtml(html,"https://example.com/recipe","");check(r.title.equals("Äpfel & Öl"),"HTML-Entitäten dekodieren");check(r.steps.optString(0).equals("Waschen und schneiden."),"Abschnitte und HTML-Anleitungen dekodieren");check(r.ingredients.length()==1,"Zutaten lesen");
        Recipe plain=RecipeImporter.prepare("Gemüse\nWaschen\nSchneiden","");check(plain.steps.length()==3&&plain.title.equals("Gemüse"),"Geteilten Text übernehmen");
        check(RecipeImporter.servings("4 Portionen")==4&&RecipeImporter.servings(2)==2,"Eindeutige Portionsangaben übernehmen");
        for(String uncertain:new String[]{"2–4 Personen","12 Kekse","2 Bleche","24 Portionen","2.5 Portionen"})check(RecipeImporter.servings(uncertain)==0,"Mehrdeutige Ausbeute nicht als Portionen raten");
        boolean invalid=false;try{Recipe.parse(r.json().put("id","../../outside").toString());}catch(JSONException e){invalid=true;}check(invalid,"Ungültige Dateipfade ablehnen");
        boolean absent=false;try{RecipeImporter.fromHtml("<html>Keine Rezeptdaten</html>","https://example.com","");}catch(java.io.IOException e){absent=true;}check(absent,"Unlesbare Rezeptseiten erkennen");
        Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Rezept");
        boolean found=false;for(android.content.pm.ResolveInfo info:getTargetContext().getPackageManager().queryIntentActivities(share,0))if(info.activityInfo.packageName.equals("com.kitchenos.share"))found=true;check(found,"Android-Teilen-Ziel registriert");
        if(!pairing.isEmpty()){Pairing device=Pairing.parse(pairing);RecipeSender.send(device,plain);RecipeSender.send(device,plain);result.putString("recipeId",plain.id);}
        result.putString("result","PASS: Recipe JSON-LD/graph/section, plain text, validation, share registration"+(!pairing.isEmpty()?", cross-UID TLS send + retry":""));finish(-1,result);
    }catch(Throwable e){result.putString("failure",e.getClass().getName()+": "+e.getMessage());finish(0,result);}}
}
