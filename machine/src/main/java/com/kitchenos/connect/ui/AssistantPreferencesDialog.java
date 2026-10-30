package com.kitchenos.connect.ui;
import android.app.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.*;
import org.json.JSONObject;
/** Nutrition and cooking preferences grouped around the assistant, in Calomatik's type and spacing. */
public final class AssistantPreferencesDialog {
    public static void show(Activity activity){
        KitchenStore store=new KitchenStore(activity);JSONObject prefs=store.snapshot().optJSONObject("preferences"),remote;try{remote=new KalomatikPlans(activity).sharedPreferences();}catch(Exception unavailable){remote=new JSONObject();}
        LinearLayout content=Ui.column(activity);content.setPadding(Ui.dp(activity,26),Ui.dp(activity,12),Ui.dp(activity,26),Ui.dp(activity,24));content.addView(KitViews.text(activity,"Dein Assistent",32,Ui.INK,true));TextView intro=KitViews.text(activity,"Deine Küche auf Handy und Maschine",22,Ui.MUTED,false);intro.setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,24));content.addView(intro);
        EditText diet=field(activity,content,"Ernährung","Zum Beispiel vegetarisch",value(prefs,remote,"diet")),allergies=field(activity,content,"Allergien & Unverträglichkeiten","Was soll Calomatik berücksichtigen?",value(prefs,remote,"allergies")),dislikes=field(activity,content,"Das mag ich nicht","Zutaten oder Gerichte",value(prefs,remote,"dislikes")),notes=field(activity,content,"Gut zu wissen","Deine Wünsche beim Kochen",prefs.optString("notes")),servings=field(activity,content,"Für wie viele kochst du?","Portionen",String.valueOf(prefs.optInt("servings",2)));servings.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if(!remote.optString("notes").isEmpty()){content.addView(KitViews.text(activity,"Aus deinem Calomatik-Assistenten",22,Ui.INK,true));content.addView(KitViews.text(activity,remote.optString("notes"),21,Ui.MUTED,false));}
        CheckBox ai=new CheckBox(activity);ai.setText("Kalomatik darf meine Kochvorlieben, meinen Vorrat und meinen Kochplan für meine KI-Anfragen verwenden.");ai.setTextSize(22);ai.setTextColor(Ui.INK);ai.setChecked(prefs.optBoolean("aiConsent"));content.addView(ai);CheckBox community=new CheckBox(activity);community.setText("Neue Rezeptentwürfe anonym zur gemeinsamen Sammlung beitragen");community.setTextSize(22);community.setTextColor(Ui.INK);community.setChecked(prefs.optBoolean("communityConsent"));content.addView(community);
        ScrollView scroll=new KitchenScroll(activity);scroll.addView(content);AlertDialog dialog=new AlertDialog.Builder(activity).setView(scroll).setNegativeButton("Zurück",null).setPositiveButton("Speichern",null).create();dialog.show();RotaryDialogs.bind(activity,dialog);dialog.getButton(-1).setOnClickListener(v->{try{int count=Integer.parseInt(servings.getText().toString());if(count<1||count>20)throw new IllegalArgumentException("Wähle 1–20 Portionen.");store.preferences(diet.getText().toString(),allergies.getText().toString(),dislikes.getText().toString(),notes.getText().toString(),count,ai.isChecked(),community.isChecked());dialog.dismiss();}catch(Exception invalid){Toast.makeText(activity,invalid.getMessage()==null?"Bitte Angaben prüfen.":invalid.getMessage(),Toast.LENGTH_LONG).show();}});
    }
    private static String value(JSONObject local,JSONObject remote,String key){String value=local.optString(key);return value.isEmpty()?remote.optString(key):value;}
    private static EditText field(Activity a,LinearLayout host,String title,String hint,String value){TextView label=KitViews.text(a,title,23,Ui.INK,true);label.setPadding(0,Ui.dp(a,18),0,Ui.dp(a,8));host.addView(label);EditText editor=Ui.input(a,hint,false);editor.setText(value);editor.setTextSize(24);editor.setMinHeight(Ui.dp(a,72));host.addView(editor);EditorKeyboardDialog.bind(a,editor);return editor;}
    private AssistantPreferencesDialog(){}
}
