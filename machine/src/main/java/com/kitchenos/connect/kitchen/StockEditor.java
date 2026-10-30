package com.kitchenos.connect.kitchen;

import android.app.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import org.json.JSONObject;

/** Correct a pantry recognition using the appliance's ordinary scoped keyboard. */
public final class StockEditor {
    public static AlertDialog show(Activity activity,JSONObject item,Runnable onSaved){
        KitchenStore store=new KitchenStore(activity);String id=item.optString("id");LinearLayout form=Ui.column(activity);form.setPadding(Ui.dp(activity,20),Ui.dp(activity,8),Ui.dp(activity,20),Ui.dp(activity,12));
        form.addView(Ui.text(activity,"Name und Menge kannst du nach der Fotoerkennung jederzeit korrigieren.",19));
        form.addView(Ui.title(activity,"Lebensmittel"));EditText name=Ui.input(activity,"Name",false);name.setTag("stock.edit.name");name.setText(item.optString("name"));name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});form.addView(name);
        form.addView(Ui.title(activity,"Menge"));EditText amount=Ui.input(activity,"Unbekannte Menge leer lassen",false);amount.setTag("stock.edit.amount");amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setText(item.optString("amount"));form.addView(amount);
        final String[] unit={item.optString("unit")};Button units=Ui.button(activity,unitLabel(unit[0]),null);units.setTag("stock.edit.unit");units.setOnClickListener(v->{String[] labels=new String[KitchenLogic.UNITS.length+1];labels[0]="Ohne Mengenangabe";System.arraycopy(KitchenLogic.UNITS,0,labels,1,KitchenLogic.UNITS.length);AlertDialog select=new AlertDialog.Builder(activity).setTitle("Einheit").setItems(labels,(dialog,index)->{unit[0]=index==0?"":KitchenLogic.UNITS[index-1];units.setText(unitLabel(unit[0]));}).create();select.show();Ui.fitDialog(activity,select);});form.addView(units,new LinearLayout.LayoutParams(-1,Ui.dp(activity,60)));
        form.addView(Ui.title(activity,"Haltbar bis"));EditText expires=Ui.input(activity,"Optional: JJJJ-MM-TT",false);expires.setTag("stock.edit.expires");expires.setText(item.optString("expires"));form.addView(expires);
        TextView error=Ui.text(activity,"",18);error.setTextColor(Ui.ACCENT);error.setTag("stock.edit.error");form.addView(error);ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Vorrat korrigieren").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Änderungen speichern",null).create();dialog.show();Ui.fitDialog(activity,dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTag("stock.edit.save");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{store.updateStock(id,name.getText().toString(),amount.getText().toString(),unit[0],expires.getText().toString().trim());dialog.dismiss();if(onSaved!=null)onSaved.run();}catch(Exception problem){error.setText(problem.getMessage()==null?"Bitte Angaben prüfen.":problem.getMessage());}});return dialog;
    }
    private static String unitLabel(String unit){return "Einheit: "+(unit.isEmpty()?"ohne Mengenangabe":unit);}
    private StockEditor(){}
}
