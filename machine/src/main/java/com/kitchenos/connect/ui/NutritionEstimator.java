package com.kitchenos.connect.ui;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Ingredient-table estimates; incomplete amounts and unsupported foods remain unknown. */
public final class NutritionEstimator {
    private static final String[] KEYS={"calories","protein","fat","carbs","fiber"};
    private final Map<String,JSONObject> foods=new HashMap<>();
    public NutritionEstimator(Context context){this(read(context));}
    public NutritionEstimator(JSONObject reference){
        JSONArray entries=reference.optJSONArray("foods");
        for(int i=0;entries!=null&&i<entries.length();i++){JSONObject food=entries.optJSONObject(i);if(food==null)continue;JSONArray aliases=food.optJSONArray("aliases");for(int j=0;aliases!=null&&j<aliases.length();j++)foods.put(normalize(aliases.optString(j)),food);}
    }
    private static JSONObject read(Context context){
        try(InputStream in=context.getAssets().open("nutrition-reference.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>131072)throw new IOException("Nährwerttabelle ist zu groß");out.write(bytes,0,n);}return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        }catch(IOException|JSONException error){return new JSONObject();}
    }
    private static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+"," ");}
    private static double positive(Object raw){if(!(raw instanceof Number))return Double.NaN;double number=((Number)raw).doubleValue();return Double.isFinite(number)&&number>0&&number<=1000000?number:Double.NaN;}
    public JSONObject estimate(JSONObject card){
        JSONArray ingredients=card.optJSONArray("ingredients");if(ingredients==null||ingredients.length()==0||ingredients.length()>80)return null;
        JSONObject program=card.optJSONObject("machineProgram"),plan=card.optJSONObject("planMeal");
        Object rawPortions=program!=null?program.opt("servings"):plan!=null?plan.opt("originalServings"):card.has("originalServings")?card.opt("originalServings"):card.opt("portion");
        double portions=positive(rawPortions);if(!Double.isFinite(portions)||portions>50)return null;
        double[] total=new double[KEYS.length];
        for(int i=0;i<ingredients.length();i++){
            JSONObject item=ingredients.optJSONObject(i);if(item==null)return null;
            JSONObject food=foods.get(normalize(item.optString("name")));if(food==null)return null;
            double amount=positive(item.opt("amount"));if(!Double.isFinite(amount))return null;
            String unit=normalize(item.optString("unit"));JSONObject units=food.optJSONObject("gramsPerUnit");if(units==null)return null;
            double factor=unit.equals("g")||unit.equals("gramm")?1:unit.equals("kg")?1000:unit.equals("ml")?units.optDouble("ml",Double.NaN):unit.equals("l")?1000*units.optDouble("ml",Double.NaN):unit.equals("tl")?units.optDouble("tsp",Double.NaN):unit.equals("el")?units.optDouble("tbsp",Double.NaN):Arrays.asList("stück","stueck","stk","stk.").contains(unit)?units.optDouble("piece",Double.NaN):Double.NaN;
            if(!Double.isFinite(factor)||factor<=0)return null;
            JSONObject values=food.optJSONObject("per100g");if(values==null)return null;
            for(int j=0;j<KEYS.length;j++){Object raw=values.opt(KEYS[j]);if(!(raw instanceof Number))return null;double value=((Number)raw).doubleValue();if(!Double.isFinite(value)||value<0)return null;total[j]+=value*amount*factor/100/portions;}
        }
        JSONObject result=new JSONObject();try{for(int i=0;i<KEYS.length;i++){if(!Double.isFinite(total[i])||total[i]>1000000)return null;result.put(KEYS[i],Math.round(total[i]*100)/100.0);}}catch(JSONException impossible){throw new IllegalStateException(impossible);}return result;
    }
}
