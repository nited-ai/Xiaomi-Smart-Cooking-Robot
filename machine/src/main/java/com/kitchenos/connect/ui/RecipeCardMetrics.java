package com.kitchenos.connect.ui;

import org.json.*;
import java.util.Locale;

/** Compact card values. Unknown values never become zero; source totals take priority. */
public final class RecipeCardMetrics {
    private static double number(Object raw,double max){if(!(raw instanceof Number))return Double.NaN;double value=((Number)raw).doubleValue();return Double.isFinite(value)&&value>=0&&value<=max?value:Double.NaN;}
    private static String minutes(double value,boolean estimated){return (estimated?"ca. ":"")+String.format(Locale.GERMAN,"%.0f",Math.ceil(value))+" Min.";}
    public static String totalTime(JSONObject recipe){
        JSONObject timing=recipe.optJSONObject("timing");if(timing==null)timing=recipe.optJSONObject("guide");
        double total=number(timing==null?null:timing.opt("totalMinutes"),12960);
        if(!Double.isFinite(total)||total<=0)total=number(recipe.opt("totalMinutes"),12960);
        if(Double.isFinite(total)&&total>0)return minutes(total,timing!=null&&timing.optBoolean("estimated"));
        // Older public cards expose their source duration as minutes, not machine timer sums.
        if(recipe.optJSONObject("machineProgram")==null){total=number(recipe.opt("minutes"),12960);if(Double.isFinite(total)&&total>0)return minutes(total,false);}
        // A conservative planning estimate includes every phase. Parallel work can shorten it;
        // do not present this budget as a measured elapsed duration or sum incomplete fields.
        if(timing!=null){total=0;for(String field:new String[]{"prepMinutes","cookMinutes","restMinutes"}){double part=number(timing.opt(field),4320);if(!Double.isFinite(part))return "Zeit offen";total+=part;}if(total>0)return minutes(total,true);}
        return "Zeit offen";
    }
    public static String compactTime(JSONObject recipe){String value=totalTime(recipe);return (value.equals("Zeit offen")?"—":value.replace("ca. ","").replace(" Min.",""))+" MIN";}
    public static String compactPortions(JSONObject recipe){String value=portions(recipe);return (value.equals("Portionen offen")?"—":value.replace(" Portionen","").replace(" Portion",""))+" Port.";}
    public static String compactIngredients(JSONObject recipe){JSONArray items=recipe.optJSONArray("ingredients");return (items==null||items.length()==0?"—":String.valueOf(items.length()))+" Zut.";}
    public static String ingredients(JSONObject recipe){JSONArray items=recipe.optJSONArray("ingredients");return items==null||items.length()==0?"Zutaten offen":items.length()+(items.length()==1?" Zutat":" Zutaten");}
    public static String portions(JSONObject recipe){JSONObject program=recipe.optJSONObject("machineProgram");double value=number(program!=null?program.opt("servings"):recipe.has("portion")?recipe.opt("portion"):recipe.opt("servings"),50);return !Double.isFinite(value)||value<=0?"Portionen offen":String.format(Locale.GERMAN,value==Math.floor(value)?"%.0f":"%.1f",value)+(value==1?" Portion":" Portionen");}
    public static String calories(JSONObject values){double value=number(values==null?null:values.opt("calories"),1000000);return !Double.isFinite(value)?"— kcal":(values.optBoolean("_estimated")?"ca. ":"")+String.format(Locale.GERMAN,"%.0f",value)+" kcal";}
    private RecipeCardMetrics(){}
}
