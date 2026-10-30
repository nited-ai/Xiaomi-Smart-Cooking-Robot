package com.kitchenos.connect;

import android.app.Instrumentation;
import android.os.*;
import com.kitchenos.common.Recipe;
import com.kitchenos.connect.kitchen.*;
import com.kitchenos.connect.control.*;
import org.json.*;
import java.util.*;
import java.time.LocalDate;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Emulator-only domain/state checks. No root call, UART, heater or motor access. */
public final class KitchenSmoke extends Instrumentation {
    private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args==null?"":args.getString("mode","");start();}
    private void check(boolean pass,String reason)throws Exception{if(!pass)throw new Exception(reason);}
    private Recipe recipe(String title,String...ingredients)throws Exception{return new Recipe(UUID.randomUUID().toString(),title,"",new JSONArray(Arrays.asList(ingredients)),new JSONArray().put("Zutaten nach Anleitung zubereiten."),2);}
    private JSONObject plan(Recipe r,int servings,int base)throws Exception{return new JSONObject().put("recipeId",r.id).put("servings",servings).put("baseServings",base).put("date",LocalDate.now().toString());}
    private JSONObject status(boolean armed,int state)throws Exception{return new JSONObject("{\"type\":\"status\",\"state\":0,\"coverOpen\":0,\"potPresent\":-1,\"error\":0,\"samples\":3,\"temperature\":22,\"motorRaw\":0,\"weightRaw\":10,\"elapsed\":0,\"coverMappingVerified\":true,\"potMappingVerified\":false,\"armed\":false}").put("armed",armed).put("state",state);}
    private JSONObject info(int switches)throws Exception{return new JSONObject().put("type","info").put("version",1).put("ihVersion",1).put("motorVersion",1).put("switches",switches);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        check(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"),"Diese Prüfung ist ausschließlich für den isolierten Emulator");
        LocalDate today=LocalDate.now();Recipe rice=recipe("Reispfanne","200 g Reis","300 g Tomaten");
        JSONArray stock=new JSONArray().put(KitchenLogic.stock("1","Reis","0,3","kg",today.plusDays(2).toString())).put(KitchenLogic.stock("2","Tomaten","0.5","kg",""));
        List<KitchenLogic.Need> needs=KitchenLogic.shopping(Arrays.asList(rice),new JSONArray().put(plan(rice,2,2)).put(plan(rice,2,2)),stock,today);
        check(needs.size()==2,"Vorrat über zwei Gerichte genau einmal reservieren");
        check(needs.get(0).amount.compareTo(new java.math.BigDecimal("100"))==0&&needs.get(1).amount.compareTo(new java.math.BigDecimal("100"))==0,"kg/g normalisieren und Restmengen berechnen");
        check(KitchenLogic.shopping(Arrays.asList(rice),new JSONArray().put(plan(rice,4,2)),stock,today).get(0).amount.compareTo(new java.math.BigDecimal("100"))==0,"Portionen hochskalieren");
        Recipe salt=recipe("Portionsprüfung","3 g Salz");
        check(KitchenLogic.shopping(Arrays.asList(salt),new JSONArray().put(plan(salt,1,3)),new JSONArray(),today).get(0).amount.compareTo(java.math.BigDecimal.ONE)==0,"Vor der Division multiplizieren, keine 0.999999 g");
        Recipe unknown=recipe("Mengen offen","Reis nach Bedarf");check(KitchenLogic.shopping(Arrays.asList(unknown),new JSONArray().put(plan(unknown,2,2)),stock,today).get(0).amount==null,"Unbekannte Menge bleibt unbekannt");
        JSONArray expired=new JSONArray().put(KitchenLogic.stock("3","Reis","500","g",today.minusDays(1).toString()));
        check(KitchenLogic.suggestions(Arrays.asList(rice),expired,new JSONObject(),today).get(0).covered==0,"Abgelaufenen Vorrat nicht als verfügbar vorschlagen");
        check(KitchenLogic.suggestions(Arrays.asList(rice),stock,new JSONObject().put("dislikes","Tomaten"),today).isEmpty(),"Abneigungen beachten");
        check(!KitchenLogic.digest(rice).equals(KitchenLogic.digest(new Recipe(rice.id,rice.title,"",rice.ingredients,new JSONArray().put("Anderer Schritt"),2))),"Kocherfahrung an Rezeptinhalt binden");
        check(Recipe.parse(rice.json().toString()).servings==2,"Portionen über Transport erhalten");
        ControlValues values=new ControlValues(5,0,5);boolean rejected=false;try{values.command("START",false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Topfbestätigung vor Start erzwingen");
        for(int[] invalid:new int[][]{{0,0,5},{601,0,5},{5,96,5},{5,20,5},{5,0,Integer.MIN_VALUE},{5,0,0},{5,0,7}}){rejected=false;try{new ControlValues(invalid[0],invalid[1],invalid[2]);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Ungültige Steuerwerte ablehnen");}
        ControlState control=new ControlState();control.begin();control.rootVerified=control.foreground=true;control.accept(new JSONObject().put("type","ready"),1000);control.accept(status(false,0),1000);control.accept(info(0x82),1000);
        check(control.allows("START",1001),"Geschlossener Deckel und frische Daten erlauben Bestätigungsdialog");check(!control.allows("START",1500),"500ms alte Daten sperren Start");
        control.accept(info(0x02),1001);check(!control.allows("START",1002),"Physisch bestätigtes Deckelbit prüfen");control.accept(info(0x82),1002);
        control.pendingAction="START";check(!control.allows("START",1002),"Doppelstart bei ausstehender Antwort sperren");control.ended();check(control.uncertainRun,"Abbruch nach Startanforderung ohne Idle bleibt unbestätigt");
        control=new ControlState();control.begin();control.rootVerified=control.foreground=true;control.accept(new JSONObject().put("type","ready"),1000);control.accept(status(true,1),1000);control.accept(new JSONObject().put("type","stopped"),1000);control.accept(status(true,1),1001);control.ended();check(control.uncertainRun,"Stoppbestätigung des ersten Laufs gilt nicht für einen zweiten");
        control=new ControlState();control.begin();control.accept(status(false,0),1000);JSONObject malformed=status(false,0);malformed.remove("temperature");try{control.accept(malformed,2000);}catch(JSONException expected){}check(control.statusAt==1000,"Fehlerhaften Status nicht teilweise als frisch übernehmen");
        control=new ControlState();control.begin();control.rootVerified=control.foreground=true;control.accept(new JSONObject().put("type","ready"),1000);control.accept(status(false,8).put("error",28),1000);control.accept(status(false,0),1001);control.accept(info(0x82),1001);check(control.deviceFaultLatched&&control.lastDeviceError==28&&!control.allows("START",1002),"Fehler 28 durch späteren Leerlauf nicht verlieren");control.ended();control.begin();check(control.deviceFaultLatched,"Fehler über Neuverbindung sichtbar halten");
        Recipe privateRecipe=recipe("Privater Kontobereich","100 g Reis");RecipeStore alice=new RecipeStore(getTargetContext(),"smoke-alice");try{alice.save(privateRecipe);check(new RecipeStore(getTargetContext(),"smoke-bob").all().isEmpty(),"Getrennte persönliche Rezeptspeicher");for(Recipe r:KitchenRecipes.all(getTargetContext()))check(!r.id.equals(privateRecipe.id),"Gastbereich liest keine privaten Entwürfe");}finally{alice.delete(privateRecipe);}
        File kitchenFile=new File(getTargetContext().getFilesDir(),"kitchen-state-v1.json");byte[] saved=kitchenFile.exists()?java.nio.file.Files.readAllBytes(kitchenFile.toPath()):null;try{java.nio.file.Files.write(kitchenFile.toPath(),"{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));boolean blocked=false;try{new KitchenStore(getTargetContext()).addStock("Reis","100","g","");}catch(IllegalStateException expected){blocked=true;}check(blocked&&new String(java.nio.file.Files.readAllBytes(kitchenFile.toPath()),java.nio.charset.StandardCharsets.UTF_8).equals("{broken"),"Beschädigten Küchenspeicher nicht mit leerem Zustand überschreiben");}finally{if(saved==null)kitchenFile.delete();else java.nio.file.Files.write(kitchenFile.toPath(),saved);}
        File nativeFile=new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"libkitchenos_bridge.so");java.lang.Process fixture=new ProcessBuilder(nativeFile.getAbsolutePath(),"--selftest").redirectErrorStream(true).start();
        check(fixture.waitFor(10,TimeUnit.SECONDS),"Native Selftest läuft zu lange");ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=fixture.getInputStream().read(buffer))!=-1)output.write(buffer,0,count);String nativeOutput=output.toString("UTF-8");check(fixture.exitValue()==0&&nativeOutput.contains("PASS"),"Native Protokoll-Fixtures: "+nativeOutput);
        if(mode.equals("seed")){
            new RecipeStore(getTargetContext()).save(rice);KitchenStore kitchen=new KitchenStore(getTargetContext());kitchen.addStock("Reis","300","g",today.plusDays(2).toString());kitchen.addStock("Tomaten","500","g",today.plusDays(3).toString());kitchen.plan(rice.id,today.toString(),4,2);
        }
        result.putString("result","PASS: pantry, portion scaling, shopping reservations, expiry, dislikes, recipe revisions, state freshness, lid mapping, manual pot confirmation, repeated-run uncertainty, native --selftest (pipes only)");finish(-1,result);
    }catch(Throwable error){android.util.Log.e("KitchenOSKitchenSmoke","Failed",error);result.putString("failure",error.toString());finish(0,result);}}
}
