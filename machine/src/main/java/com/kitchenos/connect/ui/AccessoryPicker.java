package com.kitchenos.connect.ui;
import android.app.Activity;
import android.widget.*;
import com.kitchenos.common.Ui;
import java.util.function.BooleanSupplier;

/** Only a local touch or knob press can attest the machine's selected attachment. */
public final class AccessoryPicker extends LinearLayout {
 private boolean refreshed,lastKnown,lastStirrer;private final Activity activity;private final Button blade,stirrer;private final TextView label;
 public AccessoryPicker(Activity activity,RotaryController knob,BooleanSupplier canChoose,Runnable changed){super(activity);this.activity=activity;setOrientation(VERTICAL);setTag("control.attachment");label=new TextView(activity);label.setTextSize(18);label.setTextColor(Ui.MUTED);addView(label);
  LinearLayout choices=new LinearLayout(activity);addView(choices);blade=Ui.button(activity,"Messer",v->{if(!canChoose.getAsBoolean()){Toast.makeText(activity,"Vor dem Werkzeugwechsel die Maschine stoppen.",Toast.LENGTH_LONG).show();return;}AccessorySelection.stirrer(activity,false);refresh();changed.run();});stirrer=Ui.button(activity,"Rühraufsatz · max. 4",v->{if(!canChoose.getAsBoolean()){Toast.makeText(activity,"Vor dem Werkzeugwechsel die Maschine stoppen.",Toast.LENGTH_LONG).show();return;}AccessorySelection.stirrer(activity,true);refresh();changed.run();});
  int i=0;for(Button button:new Button[]{blade,stirrer}){button.setTag(i++==0?"control.attachment.blade":"control.attachment.stirrer");button.setAllCaps(false);button.setTextSize(18);button.setMinHeight(Ui.dp(activity,54));button.setDefaultFocusHighlightEnabled(false);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,Ui.dp(activity,56),1);size.rightMargin=Ui.dp(activity,8);choices.addView(button,size);if(knob!=null)knob.add(button);}refresh();
 }
 public void refresh(){boolean known=AccessorySelection.known(activity),selected=AccessorySelection.stirrer(activity);if(refreshed&&known==lastKnown&&selected==lastStirrer)return;refreshed=true;lastKnown=known;lastStirrer=selected;label.setText(known?"Gewähltes Werkzeug":"Eingesetztes Werkzeug wählen");for(Button button:new Button[]{blade,stirrer}){boolean active=known&&(button==stirrer)==selected;button.setSelected(active);button.setTextColor(Ui.INK);button.setBackground(ControlFocus.background(activity,active?Ui.SOFT:Ui.CARD));}}
}
