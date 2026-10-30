package com.kitchenos.connect.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.kitchenos.common.Ui;
import java.util.function.Consumer;

/** Persistent bottom editor: typing updates the results without submitting or closing a dialog. */
public final class RecipeSearchPanel extends LinearLayout {
    private final EditText editor;
    private final ApplianceKeyboard keyboard;
    private boolean restoring,open;
    public RecipeSearchPanel(Activity activity,Consumer<String> changed){
        super(activity);activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);setOrientation(VERTICAL);setTag("recipes.searchPanel");
        editor=Ui.input(activity,"Rezept oder Zutat suchen",false);editor.setTag("search.editor");editor.setTextSize(25);editor.setSingleLine(true);editor.setImeOptions(EditorInfo.IME_ACTION_DONE);editor.setSelectAllOnFocus(false);
        editor.setBackgroundColor(Ui.dark?0xff171918:0xfff0f1ed);editor.setPadding(Ui.dp(activity,24),0,Ui.dp(activity,24),0);
        LinearLayout entry=new LinearLayout(activity);entry.addView(editor,new LayoutParams(0,Ui.dp(activity,72),1));
        android.widget.Button clear=new android.widget.Button(activity);clear.setText("×");clear.setContentDescription("Suchtext löschen");clear.setTag("search.clear");clear.setOnClickListener(v->editor.setText(""));entry.addView(clear,new LayoutParams(Ui.dp(activity,64),Ui.dp(activity,72)));addView(entry);
        keyboard=new ApplianceKeyboard(activity,editor,this::closeEditor);keyboard.setTag("search.keyboard");addView(keyboard);keyboard.setVisibility(GONE);setVisibility(GONE);
        setFocusableInTouchMode(true);requestFocus();editor.setOnFocusChangeListener((v,focused)->keyboard.setVisibility(focused?VISIBLE:GONE));
        editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){closeEditor();return true;}return false;});
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!restoring)changed.accept(s.toString());}public void afterTextChanged(Editable e){}});
    }
    public void query(String value){if(editor.getText().toString().trim().equals(value))return;restoring=true;editor.setText(value);editor.setSelection(editor.length());restoring=false;}
    private void hideSystemKeyboard(){android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(((Activity)getContext()).getWindow().getDecorView().getWindowToken(),0);if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController controller=((Activity)getContext()).getWindow().getInsetsController();if(controller!=null)controller.hide(android.view.WindowInsets.Type.ime());}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();hideSystemKeyboard();post(this::hideSystemKeyboard);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){hideSystemKeyboard();post(this::hideSystemKeyboard);}}
    public void focusEditor(){open=true;setVisibility(VISIBLE);editor.requestFocus();keyboard.setVisibility(VISIBLE);hideSystemKeyboard();post(this::hideSystemKeyboard);}
    public void closeEditor(){open=false;hideSystemKeyboard();setVisibility(GONE);editor.clearFocus();keyboard.setVisibility(GONE);}
    public boolean editing(){return open&&getVisibility()==VISIBLE;}
    public boolean dispatchKeyboard(KeyEvent event){return editing()&&keyboard.dispatch(event);}
}
