package com.kitchenos.connect.ui;
import android.app.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.kitchenos.common.Ui;
/** Reuses the actual editor in a keyboard sheet; closing keeps its existing text. */
public final class EditorKeyboardDialog {
    private static final java.util.WeakHashMap<EditText,Dialog> active=new java.util.WeakHashMap<>();
    public static void bind(Activity activity,EditText editor){editor.setShowSoftInputOnFocus(false);editor.setOnClickListener(v->show(activity,editor));}
    public static void dismiss(EditText editor){Dialog dialog=active.get(editor);if(dialog!=null)dialog.dismiss();}
    public static void show(Activity activity,EditText editor){if(active.containsKey(editor)||!(editor.getParent() instanceof ViewGroup))return;ViewGroup parent=(ViewGroup)editor.getParent();int index=parent.indexOfChild(editor);ViewGroup.LayoutParams original=editor.getLayoutParams();Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);active.put(editor,dialog);parent.removeView(editor);LinearLayout sheet=Ui.column(activity);sheet.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),0);editor.setImeOptions(EditorInfo.IME_ACTION_DONE);editor.setTextSize(27);sheet.addView(editor,new LinearLayout.LayoutParams(-1,Ui.dp(activity,90)));ApplianceKeyboard keys=new ApplianceKeyboard(activity,editor,dialog::dismiss);sheet.addView(keys);editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){dialog.dismiss();return true;}return false;});FrameLayout root=new FrameLayout(activity){@Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){Rect bounds=new Rect();sheet.getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY())){dialog.dismiss();return true;}}return super.dispatchTouchEvent(event);}};root.setOnClickListener(v->dialog.dismiss());root.addView(sheet,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));dialog.setContentView(root);Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();return true;}return keys.dispatch(event);});dialog.setOnDismissListener(d->{editor.clearFocus();sheet.removeView(editor);if(!activity.isDestroyed()){parent.addView(editor,Math.min(index,parent.getChildCount()),original);}active.remove(editor);});dialog.show();w.setLayout(-1,-1);CookingWindows.protectFooter(activity,w);w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());editor.requestFocus();editor.setSelection(editor.length());}
    private EditorKeyboardDialog(){}
}
