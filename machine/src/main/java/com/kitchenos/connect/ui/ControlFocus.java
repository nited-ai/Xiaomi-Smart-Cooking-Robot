package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;

/** Contrasting selection frame remains legible on orange and red actions. */
public final class ControlFocus {
    public static android.graphics.drawable.Drawable background(android.app.Activity context,int color){
        return background(context,color,44);
    }
    public static android.graphics.drawable.Drawable background(android.app.Activity context,int color,int radius){
        int inset=Ui.dp(context,4);StateListDrawable states=new StateListDrawable();
        LayerDrawable focus=new LayerDrawable(new android.graphics.drawable.Drawable[]{KitViews.shape(context,Ui.INK,radius,0),KitViews.shape(context,Ui.BG,Math.max(0,radius-3),0),KitViews.shape(context,color,Math.max(0,radius-6),0)});
        focus.setLayerInset(1,inset,inset,inset,inset);focus.setLayerInset(2,inset+Ui.dp(context,3),inset+Ui.dp(context,3),inset+Ui.dp(context,3),inset+Ui.dp(context,3));
        states.addState(new int[]{-android.R.attr.state_enabled},KitViews.shape(context,Ui.CARD,radius,0));states.addState(new int[]{android.R.attr.state_selected},focus);states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},KitViews.shape(context,color,radius,0));
        return new RippleDrawable(ColorStateList.valueOf(0x30171716),states,null);
    }
    private ControlFocus(){}
}
