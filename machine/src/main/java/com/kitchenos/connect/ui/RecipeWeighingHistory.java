package com.kitchenos.connect.ui;

import org.json.*;

/** Historical ingredient receipts only. They never prove a current scale reading or cooking run. */
public final class RecipeWeighingHistory {
    private RecipeWeighingHistory() {}
    public static JSONObject find(JSONArray records,String ingredientId){
        for(int i=records.length()-1;i>=0;i--){JSONObject row=records.optJSONObject(i);if(row!=null&&ingredientId.equals(row.optString("ingredientId")))return row;}
        return null;
    }
    public static JSONArray forStep(JSONArray records,JSONObject step)throws JSONException{
        JSONArray result=new JSONArray(),ingredients=step.getJSONArray("ingredients");
        for(int i=0;i<Math.min(80,ingredients.length());i++){
            String id=step.getString("id")+":"+ingredients.getJSONObject(i).getString("name");JSONObject row=find(records,id);
            if(row==null||row.optInt("ingredientIndex",-1)!=i||!"device-scale".equals(row.optString("source"))||!validInteger(row,"grams")||!validInteger(row,"rawWeight")||!validInteger(row,"tareRaw"))continue;
            long grams=row.getLong("grams"),raw=row.getLong("rawWeight"),tare=row.getLong("tareRaw");
            if(grams<=0||grams>10000||Math.abs(raw)>10000||Math.abs(tare)>10000||raw-tare!=grams||row.optLong("sampleCounter",0)<=0||row.optLong("observedElapsedRealtime",-1)<0||row.optLong("confirmedAtEpochMs",0)<=0)continue;
            JSONObject safe=new JSONObject().put("ingredientId",id).put("ingredientIndex",i).put("grams",grams).put("tareRaw",tare).put("rawWeight",raw).put("sampleCounter",row.getLong("sampleCounter")).put("observedElapsedRealtime",row.getLong("observedElapsedRealtime")).put("confirmedAtEpochMs",row.getLong("confirmedAtEpochMs")).put("source","device-scale");
            double target=row.optDouble("targetGrams",Double.NaN);safe.put("targetGrams",Double.isFinite(target)&&target>0&&target<=10000?target:JSONObject.NULL);result.put(safe);
        }
        return result;
    }
    public static JSONObject envelope(String owner,String digest,JSONObject step,JSONArray records)throws JSONException{
        return new JSONObject().put("version",1).put("ownerUid",owner).put("programDigest",digest).put("stepId",step.getString("id")).put("measurements",forStep(records,step));
    }
    private static boolean validInteger(JSONObject row,String key){Object value=row.opt(key);if(!(value instanceof Number))return false;double number=((Number)value).doubleValue();return Double.isFinite(number)&&number==Math.rint(number)&&Math.abs(number)<=Integer.MAX_VALUE;}
}
