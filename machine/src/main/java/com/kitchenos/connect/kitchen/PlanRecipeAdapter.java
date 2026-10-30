package com.kitchenos.connect.kitchen;

import com.kitchenos.common.Pairing;
import com.kitchenos.common.Recipe;
import org.json.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private food-only plan transport. Preserves source detail; never emits device commands. */
public final class PlanRecipeAdapter {
    public static final class Result {
        public final Recipe recipe;
        public final Double requestedCookingServings;
        public final List<String> missingDetails;
        public final String summary;
        public final JSONObject metadata;
        public final boolean ready;
        Result(Recipe recipe,Double requested,List<String> missing,String summary,JSONObject metadata){this.recipe=recipe;requestedCookingServings=requested;missingDetails=Collections.unmodifiableList(new ArrayList<>(missing));this.summary=summary;this.metadata=metadata;ready=recipe!=null&&missing.isEmpty();}
    }
    private static String text(JSONObject o,String key,int max){Object v=o==null?null:o.opt(key);if(!(v instanceof String))return "";String s=((String)v).trim();return s.length()<=max&&s.indexOf('\0')<0?s:"";}
    private static BigDecimal number(Object value,double max){if(!(value instanceof Number))return null;try{BigDecimal n=new BigDecimal(value.toString());return n.signum()>0&&n.compareTo(BigDecimal.valueOf(max))<=0?n:null;}catch(Exception e){return null;}}
    private static String decimal(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
    private static String quantity(BigDecimal n,BigDecimal scale){return decimal(n.multiply(scale));}
    private static void missing(List<String> items,String message){if(!items.contains(message))items.add(message);}
    private static void details(StringBuilder out,JSONObject step,String key,String label,double max){Object raw=step.opt(key);if(raw==null||raw==JSONObject.NULL)return;BigDecimal n=raw instanceof Number?new BigDecimal(raw.toString()):null;if(n!=null&&n.signum()>=0&&n.compareTo(BigDecimal.valueOf(max))<=0)out.append('\n').append(label).append(decimal(n));}
    private static String ingredient(JSONObject item,BigDecimal scale,List<String> missing){String name=text(item,"name",160),unit=text(item,"unit",24);if(name.isEmpty()){missing(missing,"Eine Zutat hat noch keinen lesbaren Namen.");return "Zutat · Name offen";}BigDecimal amount=number(item.opt("amount"),1000000);if(amount==null||unit.isEmpty()){missing(missing,"Menge oder Einheit für „"+name+"“ fehlt.");return name+" · "+(amount==null?"Menge offen":decimal(amount))+ (unit.isEmpty()?" · Einheit offen":" "+unit);}if(amount.multiply(scale).compareTo(new BigDecimal("1000000"))>0)missing(missing,"Die normalisierte Menge für „"+name+"“ überschreitet die Rezeptgrenze.");return quantity(amount,scale)+" "+unit+" "+name;}
    private static void appendStrings(JSONArray source,JSONArray target,String label,int maxItems,int maxLength,List<String> missing)throws Exception{if(source==null)return;if(source.length()>maxItems){missing(missing,"Die "+label+"-Liste ist zu lang.");return;}for(int i=0;i<source.length();i++){Object value=source.opt(i);if(value instanceof String&&!((String)value).trim().isEmpty()&&((String)value).length()<=maxLength)target.put(label+": "+((String)value).trim());else missing(missing,"Ein "+label+" ist nicht vollständig lesbar.");}}
    private static void sourceLines(JSONArray target,String value){int from=0;while(from<value.length()){int end=Math.min(from+4096,value.length());if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1)))end--;target.put(value.substring(from,end));from=end;}}
    /** Unknown original portions stay unknown. Fractional known portions are normalized
     * to one serving with an explicit source-basis note; cookingPortions never sets the basis. */
    public static Result adapt(JSONObject card,String uid)throws Exception{
        if(card==null||uid==null||uid.trim().isEmpty())throw new IllegalArgumentException("Bitte Kochplan im angemeldeten Konto öffnen");
        List<String> missing=new ArrayList<>();String title=text(card,"title",160);if(title.isEmpty()){title="Geplantes Gericht";missing(missing,"Der Rezepttitel fehlt.");}
        BigDecimal original=number(card.opt("originalServings"),50),requested=number(card.opt("cookingPortions"),50),scale=BigDecimal.ONE;int base=0;boolean normalized=false;
        if(original==null)missing(missing,"Auf wie viele ursprüngliche Portionen beziehen sich die Planmengen?");
        else if(original.stripTrailingZeros().scale()<=0&&original.compareTo(new BigDecimal("20"))<=0)base=original.intValueExact();
        else{base=1;normalized=true;scale=BigDecimal.ONE.divide(original,18,RoundingMode.HALF_UP);}
        JSONArray ingredients=new JSONArray(),steps=new JSONArray(),input=card.optJSONArray("ingredients");Map<String,JSONObject> byId=new LinkedHashMap<>();
        if(input==null||input.length()==0)missing(missing,"Die Zutatenliste fehlt.");
        else if(input.length()>80)missing(missing,"Die Zutatenliste ist zu lang.");
        else for(int i=0;i<input.length();i++){JSONObject item=input.optJSONObject(i);if(item==null){missing(missing,"Eine Zutat ist unvollständig.");continue;}String id=text(item,"id",100);if(id.isEmpty())id="ingredient-"+i;if(byId.put(id,item)!=null)missing(missing,"Zutatenkennungen sind nicht eindeutig.");ingredients.put(ingredient(item,scale,missing));}
        String basis=original==null?"Originale Portionsbasis offen; Mengen nicht skaliert.":normalized?"Planmengenbasis: "+decimal(original)+" persönliche Portionen. Zutatenmengen für 1 Portion normalisiert (Faktor 1 / "+decimal(original)+"). Originalanweisungen bleiben wörtlich und beziehen sich gegebenenfalls auf die ursprüngliche Basis.":"Originale Planmengen für "+base+" Portionen; gewünschte Kochportionen werden getrennt gewählt.";
        steps.put(basis);JSONObject guide=card.optJSONObject("recipeGuide");JSONArray guideSteps=guide==null?null:guide.optJSONArray("steps");
        if(guide==null||guideSteps==null||guideSteps.length()==0)missing(missing,"Die vollständige Zubereitungsanleitung fehlt.");
        else{
            if(guideSteps.length()>40)missing(missing,"Die Zubereitungsanleitung ist zu lang.");
            String guideTitle=text(guide,"title",160),difficulty=text(guide,"difficulty",160);if(!guideTitle.isEmpty()&&!guideTitle.equals(title))steps.put("Titel der Originalanleitung: "+guideTitle);if(!difficulty.isEmpty())steps.put("Schwierigkeitsgrad: "+difficulty);
            appendStrings(guide.optJSONArray("equipment"),steps,"Benötigte Ausstattung",20,160,missing);
            StringBuilder totals=new StringBuilder();details(totals,guide,"prepMinutes","Vorbereitung in Minuten: ",4320);details(totals,guide,"cookMinutes","Kochen in Minuten: ",4320);details(totals,guide,"restMinutes","Ruhen in Minuten: ",4320);if(totals.length()>0)steps.put(totals.toString().trim());
            Map<String,BigDecimal> used=new LinkedHashMap<>();boolean refsPresent=false;
            for(int i=0;i<Math.min(guideSteps.length(),40);i++){
                JSONObject step=guideSteps.optJSONObject(i);if(step==null){missing(missing,"Schritt "+(i+1)+" fehlt.");continue;}
                String instruction=text(step,"instruction",2400),heading=text(step,"title",160);if(instruction.isEmpty()){missing(missing,"Die Anweisung in Schritt "+(i+1)+" fehlt.");continue;}
                StringBuilder out=new StringBuilder("Schritt "+(i+1)+(heading.isEmpty()?"":": "+heading));JSONArray refs=step.optJSONArray("ingredients");HashSet<String> seen=new HashSet<>();
                if(refs!=null){if(refs.length()>80)missing(missing,"Zu viele Zutatenbezüge in Schritt "+(i+1)+".");for(int j=0;j<Math.min(refs.length(),80);j++){JSONObject ref=refs.optJSONObject(j);if(ref==null){missing(missing,"Ein Zutatenbezug ist unvollständig.");continue;}refsPresent=true;String id=text(ref,"ingredientId",100);BigDecimal share=number(ref.opt("share"),1);JSONObject item=byId.get(id);if(item==null||share==null||!seen.add(id)){missing(missing,"Ein Zutatenanteil in Schritt "+(i+1)+" ist unklar.");continue;}used.put(id,used.getOrDefault(id,BigDecimal.ZERO).add(share));out.append("\nZutatenanteil ").append(decimal(share)).append(" von ").append(id).append(": ").append(ingredient(item,scale.multiply(share),missing));}}
                out.append(normalized?"\nOriginalanweisung (Planmengenbasis "+decimal(original)+" Portionen):\n":"\n").append(instruction);
                details(out,step,"minutes","Dauer in Minuten: ",4320);details(out,step,"temperatureC","Temperatur in °C: ",500);
                String heat=text(step,"heat",1000),cue=text(step,"cue",1000);if(!heat.isEmpty())out.append("\nHeizart: ").append(heat);if(!cue.isEmpty())out.append("\nSo erkennst du das Ergebnis: ").append(cue);
                sourceLines(steps,out.toString());
            }
            if(refsPresent)for(String id:byId.keySet())if(used.getOrDefault(id,BigDecimal.ZERO).subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000001"))>0)missing(missing,"Die Schritte verteilen „"+text(byId.get(id),"name",160)+"“ nicht genau einmal vollständig.");
            appendStrings(guide.optJSONArray("tips"),steps,"Tipp",20,1000,missing);
        }
        if(steps.length()>100)missing(missing,"Die vollständige Quelle überschreitet die Transportgrenze von 100 Textabschnitten.");
        JSONObject metadata=new JSONObject().put("version",1).put("private",true).put("sourceKind","kalomatik-plan").put("originalServings",original==null?JSONObject.NULL:original).put("recipeServings",base).put("normalizedToOne",normalized).put("quantityScale",decimal(scale)).put("basisLabel",basis).put("sourceMealId",text(card,"sourceMealId",200)).put("recipeOrigin",text(card,"recipeOrigin",80));
        String identity=Pairing.fingerprint(new JSONObject().put("owner",uid).put("title",title).put("ingredients",ingredients).put("steps",steps).put("metadata",metadata).toString().getBytes(StandardCharsets.UTF_8));String id=identity.substring(0,8)+"-"+identity.substring(8,12)+"-"+identity.substring(12,16)+"-"+identity.substring(16,20)+"-"+identity.substring(20,32);
        Recipe recipe=null;try{recipe=new Recipe(id,title,"",ingredients,steps,base);}catch(JSONException oversized){missing(missing,"Die vollständige Rezeptquelle ist zu umfangreich für die Übernahme. Die Planangaben bleiben in der Übersicht lesbar.");}String summary=missing.isEmpty()?basis:basis+"\nNoch offen: "+android.text.TextUtils.join(" ",missing);
        return new Result(recipe,requested==null?null:requested.doubleValue(),missing,summary,metadata);
    }
    private PlanRecipeAdapter(){}
}
