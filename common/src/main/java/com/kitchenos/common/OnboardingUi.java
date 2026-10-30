package com.kitchenos.common;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Scoped setup components, using the existing Kalomatik palette and provider artwork. */
public final class OnboardingUi {
    public static LinearLayout page(Activity a,String title,String intro){
        LinearLayout page=Ui.page(a);
        page.setPadding(page.getPaddingLeft(),Ui.dp(a,24),page.getPaddingRight(),Ui.dp(a,32));
        TextView brand=Ui.text(a,"Kalomatik",18);brand.setTypeface(null,Typeface.BOLD);brand.setPadding(0,0,0,Ui.dp(a,28));page.addView(brand);
        TextView heading=Ui.title(a,title);heading.setTextSize(a.getPackageName().equals("com.kitchenos.connect")?32:28);if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);page.addView(heading);
        if(!intro.isEmpty()){TextView text=Ui.text(a,intro,a.getPackageName().equals("com.kitchenos.connect")?22:17);text.setTextColor(Ui.MUTED);text.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,24));page.addView(text);}
        return page;
    }
    public static void add(LinearLayout parent,View child){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(parent.getContext(),8);parent.addView(child,lp);
    }
    public static Button secondary(Activity a,String label,View.OnClickListener click){Button b=Ui.button(a,label,click);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ee6542),null,null));return b;}
    public static View provider(Activity a,String id,String label,View.OnClickListener click){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(Ui.dp(a,60));row.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Ui.CARD);bg.setCornerRadius(Ui.dp(a,16));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ee6542),bg,null));
        TextView text=Ui.text(a,label,17);text.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);row.addView(text,lp);
        row.setContentDescription(label);row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);row.setFocusable(true);row.setOnClickListener(click);row.setTag("account.provider."+id);return row;
    }
    public static Drawable logo(Activity a,String id){
        return null; // Provider names remain text; no third-party logos are bundled.
    }
    private OnboardingUi(){}
}
