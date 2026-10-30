package com.kitchenos.connect.ui;
import android.app.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.R;
import com.kitchenos.connect.kitchen.CloudSession;

/** App-scoped text entry for settings, pantry and Wi-Fi. Never changes the system IME. */
public final class ApplianceTextEntry {
    public static void attach(Activity activity,EditText editor){editor.setShowSoftInputOnFocus(false);editor.setOnClickListener(v->{if(editor.getTag(R.id.appliance_keyboard)==null&&editor.isEnabled())show(activity,editor);});}
    public static AlertDialog show(Activity activity,EditText source){String owner=new CloudSession(activity).account().optString("uid");LinearLayout surface=Ui.column(activity);surface.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),0);EditText editor=new EditText(activity);editor.setTag("keyboard.editor");editor.setTextColor(Ui.INK);editor.setHintTextColor(Ui.MUTED);editor.setTextSize(24);editor.setInputType(source.getInputType());editor.setTransformationMethod(source.getTransformationMethod());editor.setFilters(source.getFilters());editor.setText(source.getText());editor.setMaxLines(4);editor.setImeOptions(EditorInfo.IME_ACTION_DONE);surface.addView(editor,new LinearLayout.LayoutParams(-1,-2));final AlertDialog[] holder={null};Runnable apply=()->{if(source.isAttachedToWindow()&&owner.equals(new CloudSession(activity).account().optString("uid"))){source.setText(editor.getText());source.setSelection(source.length());}holder[0].dismiss();};ApplianceKeyboard keyboard=new ApplianceKeyboard(activity,editor,()->holder[0].cancel());surface.addView(keyboard);editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){apply.run();return true;}return false;});AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(source.getHint()==null?"Eingabe":source.getHint()).setView(surface).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->apply.run()).create();holder[0]=dialog;dialog.show();Ui.fitDialog(activity,dialog);com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->keyboard.dispatch(event));editor.requestFocus();editor.setSelection(editor.length());return dialog;}
    private ApplianceTextEntry(){}
}
