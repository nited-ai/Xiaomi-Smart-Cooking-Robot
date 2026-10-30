package com.kitchenos.connect.ui;
import android.content.Context;

/** Function limit only. No physical attachment selection or persisted tool assumption. */
public final class AccessorySelection {
 public static final String UNKNOWN="",LIMIT="Beim Rühren höchstens Stufe 4 wählen.";
 public static final String COVER_LIMIT="Mit Messerabdeckung höchstens Stufe 1 und 100 °C wählen.";
 private static String function="manual";
 public static void function(Context context,String id){function=id==null?"manual":id;}
 public static boolean known(Context context){return true;}
 public static boolean stirrer(Context context){return function.equals("stir")||function.equals("whip");}
 /** Selected illustration only; this does not detect or confirm a physical attachment. */
 public static String visualTool(Context context,boolean whiskSelected){return visualTool(function,whiskSelected);}
 public static String visualTool(String id,boolean whiskSelected){return "whip".equals(id)||whiskSelected?"whisk":"stir".equals(id)?"cover":"rice".equals(id)||"eggs".equals(id)?"basket":"steam".equals(id)?"steam":"blade";}
 public static void stirrer(Context context,boolean selected){function(context,selected?"whip":"manual");}
 private static boolean cover(){return java.util.Arrays.asList("stir","slow","lidless").contains(function);}
 public static String issue(Context context,int speed){return cover()&&Math.abs((long)speed)>10?COVER_LIMIT:function.equals("whip")&&Math.abs((long)speed)>40?LIMIT:"";}
 public static String issue(Context context,int speed,int temperature){return cover()&&temperature>100?COVER_LIMIT:issue(context,speed);}
 private AccessorySelection(){}
}
