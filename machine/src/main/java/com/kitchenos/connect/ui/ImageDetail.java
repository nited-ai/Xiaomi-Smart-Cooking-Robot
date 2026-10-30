package com.kitchenos.connect.ui;

import android.app.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import java.util.function.Consumer;

/** Inspect a recipe or ingredient without leaving the recipe or losing its scroll position. */
public final class ImageDetail {
    private ImageDetail(){}
    public static void show(Activity activity,String title,Consumer<ImageView> bind){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=Ui.column(activity);root.setPadding(Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,24));root.setBackgroundColor(Ui.BG);
        TextView label=new TextView(activity);label.setText(title);label.setTextSize(28);label.setTextColor(Ui.INK);root.addView(label);
        ImageView image=new ImageView(activity){@Override public void setScaleType(ScaleType ignored){super.setScaleType(ScaleType.FIT_CENTER);}};image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(title);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        Button close=Ui.button(activity,"Zurück",v->dialog.dismiss());root.addView(close);dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);bind.accept(image);RotaryController knob=new RotaryController(activity,dialog::dismiss);knob.add(close);knob.select(close);com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,code,event)->knob.dispatch(event));
    }
}
