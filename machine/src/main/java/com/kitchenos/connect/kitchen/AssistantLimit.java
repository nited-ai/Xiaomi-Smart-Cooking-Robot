package com.kitchenos.connect.kitchen;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats a confirmed backend limit without mistaking provider throttling for a daily budget. */
public final class AssistantLimit {
    public static String message(String code,JSONObject status){
        if("provider_rate_limit".equals(code))return "Der KI-Anbieter begrenzt gerade die Anfragen. Bitte später erneut versuchen.";
        if(!"personal_daily_limit".equals(code)&&!"service_daily_limit".equals(code))return "";
        JSONObject budget=status==null?null:status.optJSONObject("budget");String reset="";
        if(budget!=null)try{reset=" Neues Tagesbudget ab "+DateTimeFormatter.ofPattern("dd.MM. 'um' HH:mm",Locale.GERMAN).withZone(ZoneId.systemDefault()).format(Instant.parse(budget.getString("resetAt")))+" Uhr.";}catch(Exception ignored){}
        String message="service_daily_limit".equals(code)?"Das Tagesbudget des KI-Dienstes ist erreicht.":"Dein Tagesbudget für KI-Anfragen ist erreicht.";
        if("personal_daily_limit".equals(code)&&budget!=null&&budget.optInt("limit",0)>0&&budget.optInt("used",-1)>=budget.optInt("limit"))message="Dein Tagesbudget ist erreicht: "+budget.optInt("used")+" von "+budget.optInt("limit")+" KI-Anfragen. Chat und Rezeptaufbereitung teilen sich dieses Budget.";
        return message+reset+"";
    }
    private AssistantLimit(){}
}
