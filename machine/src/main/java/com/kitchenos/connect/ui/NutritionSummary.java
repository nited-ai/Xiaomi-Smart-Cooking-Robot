package com.kitchenos.connect.ui;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;
import org.json.JSONObject;

/** Source values only; missing nutrients stay absent and are never estimated here. */
public final class NutritionSummary {
    public static String compact(JSONObject values){if(values==null)return "";StringBuilder out=new StringBuilder();add(out,values,"calories"," kcal");add(out,values,"protein"," g Eiweiß");add(out,values,"carbs"," g KH");add(out,values,"fat"," g Fett");return out.toString();}
    private static void add(StringBuilder out,JSONObject values,String key,String unit){Object raw=values.opt(key);if(!(raw instanceof Number))return;double v=((Number)raw).doubleValue();if(!Double.isFinite(v)||v<0||v>1000000)return;if(out.length()>0)out.append(" · ");out.append(String.format(java.util.Locale.GERMAN,key.equals("calories")?"%.0f":"%.1f",v)).append(unit);}
    public static View detail(android.app.Activity context,JSONObject values,String basis){if(compact(values).isEmpty())return new View(context);LinearLayout row=Ui.column(context);row.setTag("recipe.nutrition");row.setPadding(0,Ui.dp(context,12),0,Ui.dp(context,12));Object calories=values.opt("calories");String heading=calories instanceof Number&&Double.isFinite(((Number)calories).doubleValue())&&((Number)calories).doubleValue()>=0?String.format(java.util.Locale.GERMAN,"%.0f kcal · ",((Number)calories).doubleValue())+basis:basis;row.addView(KitViews.text(context,heading,20,Ui.MUTED,false));row.addView(new NutritionBars(context,values,false),new LinearLayout.LayoutParams(-1,Ui.dp(context,98)));return row;}
    private NutritionSummary(){}
}
