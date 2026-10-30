package com.kitchenos.connect.ui;

import org.json.JSONArray;
import org.json.JSONObject;

/** Display projection only. IDs, instructions, program digest and run receipts stay intact. */
public final class RecipePresentation {
    private RecipePresentation(){}
    public static boolean omitted(JSONObject step){
        if(step==null||!"prepare".equals(step.optString("kind")))return false;
        JSONArray ingredients=step.optJSONArray("ingredients");if(ingredients==null||ingredients.length()!=0)return false;
        JSONObject settings=step.optJSONObject("settings");if(settings==null||settings.optInt("durationSeconds",0)!=0||settings.optInt("temperatureC",0)!=0||settings.optDouble("speedLevel",0)!=0)return false;
        String title=step.optString("title"),instruction=step.optString("instruction");
        return title.equals("Vor dem Start")&&instruction.equals("Eigene Kalomatik-Fassung für 4 Portionen. Zutaten, Zubehör und Mengen prüfen. Diese Fassung ist technisch geprüft, aber noch nicht nachgekocht. Geräte nur vor Ort nach Vorbereitung starten.")
            ||title.equals("Topf vorbereiten")&&instruction.equals("Messer korrekt einsetzen. Topf und Deckel auf sauberen Sitz prüfen. Vor jeder Zugabe den Stillstand abwarten; vor jedem Maschinenlauf den Deckel schließen.");
    }
    public static int next(JSONArray steps,int index){while(index<steps.length()&&omitted(steps.optJSONObject(index)))index++;return index;}
    public static int previous(JSONArray steps,int index){while(index>=0&&omitted(steps.optJSONObject(index)))index--;return index;}
    public static int count(JSONArray steps){int count=0;for(int i=0;steps!=null&&i<steps.length();i++)if(!omitted(steps.optJSONObject(i)))count++;return count;}
    public static int ordinal(JSONArray steps,int index){int count=0;for(int i=0;i<=index&&i<steps.length();i++)if(!omitted(steps.optJSONObject(i)))count++;return count;}
}
