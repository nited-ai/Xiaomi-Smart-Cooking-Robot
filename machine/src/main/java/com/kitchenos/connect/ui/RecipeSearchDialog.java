package com.kitchenos.connect.ui;

import android.app.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;

/** One visible search editor and the same app keyboard as the assistant. */
public final class RecipeSearchDialog {
    public interface Search { void submit(String query); }
    public static Dialog show(Activity activity,String query,Search search){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=Ui.column(activity);sheet.setTag("search.sheet");
        EditText editor=Ui.input(activity,"Rezept oder Zutat suchen",false);editor.setTag("search.editor");editor.setTextSize(28);editor.setSingleLine(true);editor.setImeOptions(EditorInfo.IME_ACTION_SEARCH);editor.setText(query);editor.setSelectAllOnFocus(false);
        editor.setBackgroundColor(Ui.dark?0xff171918:0xfff0f1ed);editor.setPadding(Ui.dp(activity,24),0,Ui.dp(activity,24),0);
        LinearLayout.LayoutParams fieldSize=new LinearLayout.LayoutParams(-1,Ui.dp(activity,86));sheet.addView(editor,fieldSize);
        ApplianceKeyboard keyboard=new ApplianceKeyboard(activity,editor,dialog::dismiss);keyboard.setTag("search.keyboard");sheet.addView(keyboard);
        FrameLayout root=new FrameLayout(activity){@Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){Rect bounds=new Rect();sheet.getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY())){dialog.dismiss();return true;}}return super.dispatchTouchEvent(event);}};
        root.setOnClickListener(v->dialog.dismiss());FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(sheet,size);
        editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){String value=editor.getText().toString().trim();dialog.dismiss();search.submit(value);return true;}return false;});
        dialog.setContentView(root);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(.12f);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();return true;}return keyboard.dispatch(event);});
        dialog.show();window.setLayout(-1,-1);window.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());editor.requestFocus();editor.setSelection(editor.length());return dialog;
    }
    private RecipeSearchDialog(){}
}
