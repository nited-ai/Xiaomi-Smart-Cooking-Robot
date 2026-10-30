package com.kitchenos.connect.ui;
import org.json.JSONObject;
import java.util.*;
/** Calomatik guide roles: estimated preparation, cooking and rest; never sum parallel work. */
public final class RecipeTiming {
 public static String summary(JSONObject timing){if(timing==null)return "";String[][] fields={{"prepMinutes","Vorbereitung"},{"cookMinutes","Garen"},{"restMinutes","Ruhe"}};ArrayList<String> parts=new ArrayList<>();for(String[] field:fields){Object raw=timing.opt(field[0]);if(!(raw instanceof Number))continue;double value=((Number)raw).doubleValue();if(!Double.isFinite(value)||value<=0||value>4320)continue;String amount=String.format(Locale.GERMAN,value==Math.floor(value)?"%.0f":"%.1f",value);parts.add(field[1]+" "+amount+" Min.");}return parts.isEmpty()?"":"ca. "+android.text.TextUtils.join(" · ",parts);}
 private RecipeTiming(){}
}
