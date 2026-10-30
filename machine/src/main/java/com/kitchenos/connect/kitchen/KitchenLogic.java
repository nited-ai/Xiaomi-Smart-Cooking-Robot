package com.kitchenos.connect.kitchen;

import com.kitchenos.common.Recipe;
import org.json.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Deterministic pantry/recipe logic; it never invents a recipe or claims AI output. */
public final class KitchenLogic {
    public static final String[] UNITS={"g","kg","ml","l","Stück","EL","TL","Packung"};
    private static final Pattern QUANTITY=Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g|ml|l|Stück|EL|TL|Packung)\\s+(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    public static String key(String s){return s.toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");}
    public static JSONObject stock(String id,String name,String amount,String unit,String expires)throws Exception {
        if(name.trim().isEmpty()||name.length()>100||!(Arrays.asList(UNITS).contains(unit)||unit.isEmpty()&&amount.trim().isEmpty()))throw new IllegalArgumentException("Zutat und Einheit prüfen");
        String quantity=amount.trim().isEmpty()?"":number(amount).stripTrailingZeros().toPlainString();
        if(!expires.isEmpty())LocalDate.parse(expires);
        return new JSONObject().put("id",id).put("name",name.trim()).put("amount",quantity).put("unit",unit).put("expires",expires);
    }
    private static BigDecimal number(String text){try{BigDecimal value=new BigDecimal(text.trim().replace(',','.'));if(value.signum()<=0||value.compareTo(new BigDecimal("1000000"))>0||value.scale()>6)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw new IllegalArgumentException("Menge als positive Zahl eingeben (maximal 1.000.000).");}}
    public static final class Ingredient {
        public final String name,unit;public final BigDecimal amount;
        Ingredient(String name,String unit,BigDecimal amount){this.name=name;this.unit=unit;this.amount=amount;}
    }
    public static Ingredient ingredient(String text){Matcher match=QUANTITY.matcher(text.trim());if(match.matches())try{return canonical(match.group(3),match.group(2),number(match.group(1)));}catch(IllegalArgumentException ignored){}return new Ingredient(text.trim(),"",null);}
    private static Ingredient canonical(String name,String unit,BigDecimal amount){
        unit=unit.toLowerCase(Locale.GERMAN);if(unit.equals("kg"))return new Ingredient(name,"g",amount==null?null:amount.multiply(new BigDecimal("1000")));if(unit.equals("l"))return new Ingredient(name,"ml",amount==null?null:amount.multiply(new BigDecimal("1000")));
        return new Ingredient(name,unit,amount);
    }
    private static boolean matches(String ingredient,String stock){String a=key(ingredient),b=key(stock);return !b.isEmpty()&&(a.equals(b)||(" "+a+" ").contains(" "+b+" "));}
    public static final class Match {
        public final Recipe recipe;public final int covered,total;public final List<String> missing;public final boolean expiresSoon;
        Match(Recipe recipe,int covered,List<String> missing,boolean soon){this.recipe=recipe;this.covered=covered;this.total=recipe.ingredients.length();this.missing=missing;this.expiresSoon=soon;}
    }
    public static List<Match> suggestions(List<Recipe> recipes,JSONArray pantry,JSONObject preferences,LocalDate today){
        List<Match> result=new ArrayList<>();String dislikes=preferences.optString("dislikes");
        for(Recipe recipe:recipes){int covered=0;boolean soon=false,excluded=false;List<String> missing=new ArrayList<>();
            for(String unwanted:dislikes.split("[,;\\n]"))if(!unwanted.trim().isEmpty()&&key(recipe.title+" "+recipe.ingredients.toString()).contains(key(unwanted)))excluded=true;
            if(excluded||recipe.ingredients.length()==0)continue;
            for(int j=0;j<recipe.ingredients.length();j++){
                Ingredient ingredient=ingredient(recipe.ingredients.optString(j));boolean found=false;
                for(int i=0;i<pantry.length();i++){JSONObject item=pantry.optJSONObject(i);if(item==null||expired(item,today)||!matches(ingredient.name,item.optString("name")))continue;found=true;String date=item.optString("expires");if(!date.isEmpty()&&!LocalDate.parse(date).isAfter(today.plusDays(3)))soon=true;}
                if(found)covered++;else missing.add(ingredient.name);
            }
            result.add(new Match(recipe,covered,missing,soon));
        }
        result.sort((a,b)->{int coverage=Double.compare((double)b.covered/b.total,(double)a.covered/a.total);if(coverage!=0)return coverage;if(a.expiresSoon!=b.expiresSoon)return a.expiresSoon?-1:1;return a.recipe.title.compareToIgnoreCase(b.recipe.title);});return result;
    }
    public static boolean expired(JSONObject item,LocalDate today){String date=item.optString("expires");try{return !date.isEmpty()&&LocalDate.parse(date).isBefore(today);}catch(Exception e){return true;}}
    public static final class Need {
        public final String name,unit;public BigDecimal amount;public final Set<String> dishes=new LinkedHashSet<>();
        Need(String name,String unit,BigDecimal amount){this.name=name;this.unit=unit;this.amount=amount;}
        public String display(){return amount==null?name+" · Menge prüfen":amount.stripTrailingZeros().toPlainString()+" "+unit+" "+name;}
    }
    /** Reserve each known stock quantity once across all planned meals. Unknown quantities stay unknown. */
    public static List<Need> shopping(List<Recipe> recipes,JSONArray plans,JSONArray pantry,LocalDate today){
        Map<String,Recipe> byId=new HashMap<>();for(Recipe r:recipes)byId.put(r.id,r);
        List<JSONObject> ordered=new ArrayList<>();for(int i=0;i<plans.length();i++){JSONObject plan=plans.optJSONObject(i);if(plan!=null&&plan.optString("date").compareTo(today.toString())>=0)ordered.add(plan);}ordered.sort(Comparator.comparing(o->o.optString("date")));
        Map<String,BigDecimal> available=new HashMap<>();
        for(int i=0;i<pantry.length();i++)try{JSONObject item=pantry.getJSONObject(i);if(expired(item,today)||item.optString("amount").isEmpty())continue;Ingredient stock=canonical(item.getString("name"),item.getString("unit"),number(item.getString("amount")));String k=key(stock.name)+"|"+stock.unit;available.merge(k,stock.amount,BigDecimal::add);}catch(Exception ignored){}
        Map<String,Need> needed=new LinkedHashMap<>();
        for(JSONObject plan:ordered){Recipe recipe=byId.get(plan.optString("recipeId"));if(recipe==null)continue;
            int base=plan.optInt("baseServings",recipe.servings),servings=plan.optInt("servings",0);
            boolean portionsKnown=base>=1&&base<=20&&servings>=1&&servings<=20;
            for(int i=0;i<recipe.ingredients.length();i++){
                Ingredient item=ingredient(recipe.ingredients.optString(i));String k=key(item.name)+"|"+item.unit;BigDecimal remaining=item.amount==null||!portionsKnown?null:item.amount.multiply(new BigDecimal(servings)).divide(new BigDecimal(base),6,java.math.RoundingMode.CEILING);
                if(remaining!=null){BigDecimal stock=available.getOrDefault(k,BigDecimal.ZERO),used=stock.min(remaining);available.put(k,stock.subtract(used));remaining=remaining.subtract(used);if(remaining.signum()==0)continue;}
                Need need=needed.get(k);if(need==null){need=new Need(item.name,item.unit,remaining);needed.put(k,need);}else if(need.amount!=null&&remaining!=null)need.amount=need.amount.add(remaining);else need.amount=null;need.dishes.add(recipe.title);
            }
        }
        return new ArrayList<>(needed.values());
    }
    public static String digest(Recipe recipe)throws Exception{return com.kitchenos.common.Pairing.fingerprint(new JSONObject().put("title",recipe.title).put("ingredients",recipe.ingredients).put("steps",recipe.steps).put("servings",recipe.servings).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
