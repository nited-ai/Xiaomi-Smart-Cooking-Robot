package com.kitchenos.common;

import android.app.*;
import android.content.res.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Native port of the pinned Kalomatik UI Kit 0.1.0 roles and component geometry. */
public final class Ui {
    public static int BG=0xff171918,CARD=0xff212422,INK=0xfff2f3ee,MUTED=0xffa7aea8,LINE=0xff353a36,SOFT=0xff35241e;
    public static final int ACCENT=0xffee6542;
    public static boolean dark=true;
    public static int dp(Activity a,int n){return dp((android.content.Context)a,n);}
    public static int dp(android.content.Context context,int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    public static void theme(Activity a){
        String chosen=a.getSharedPreferences("appearance",0).getString("theme","dark");
        dark=chosen.equals("dark")||chosen.equals("system")&&(a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        BG=dark?0xff171918:0xfffbfbfa;CARD=dark?0xff212422:0xffffffff;INK=dark?0xfff2f3ee:0xff171716;MUTED=dark?0xffa7aea8:0xff65655f;LINE=dark?0xff353a36:0xffe9e9e6;SOFT=dark?0xff35241e:0xfffff0e9;
        int style=a.getResources().getIdentifier(dark?"KitchenOSDarkTheme":"KitchenOSLightTheme","style",a.getPackageName());if(style!=0)a.setTheme(style);
        if(android.os.Build.VERSION.SDK_INT>=29)a.getWindow().getDecorView().setForceDarkAllowed(false);
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);int systemUi=dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;if(a.getPackageName().equals("com.kitchenos.connect"))systemUi|=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;a.getWindow().getDecorView().setSystemUiVisibility(systemUi);
    }
    public static TextView text(Activity a,String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setPadding(0,dp(a,6),0,dp(a,6));return t;}
    public static TextView title(Activity a,String s){TextView t=text(a,s,23);t.setTypeface(null,Typeface.BOLD);return t;}
    public static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout page(Activity a){
        theme(a);FrameLayout root=new FrameLayout(a);root.setBackgroundColor(BG);if(android.os.Build.VERSION.SDK_INT>=35&&a.getApplicationInfo().targetSdkVersion>=35)root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});ScrollView scroll=new ScrollView(a);scroll.setDefaultFocusHighlightEnabled(false);scroll.setFillViewport(true);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=column(a);int padding=Math.max(dp(a,20),(a.getResources().getDisplayMetrics().widthPixels-dp(a,a.getPackageName().equals("com.kitchenos.connect")?800:480))/2+dp(a,20));page.setPadding(padding,dp(a,6),padding,dp(a,108));scroll.addView(page);a.setContentView(root);return page;
    }
    private static GradientDrawable shape(int color,int line,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(line!=0)d.setStroke(1,line);return d;}
    public static Button button(Activity a,String s,View.OnClickListener click){Button b=new Button(a);b.setStateListAnimator(null);b.setElevation(0);b.setText(s);b.setAllCaps(false);b.setTextSize(a.getPackageName().equals("com.kitchenos.connect")?20:16);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,INK}));b.setMinHeight(dp(a,a.getPackageName().equals("com.kitchenos.connect")?80:48));if(a.getPackageName().equals("com.kitchenos.connect"))b.setTextSize(24);b.setPadding(dp(a,16),dp(a,8),dp(a,16),dp(a,8));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ee6542),shape(CARD,LINE,dp(a,24)),null));b.setOnClickListener(click);return b;}
    public static Button primary(Activity a,String s,View.OnClickListener click){Button b=button(a,s,click);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40171716),shape(ACCENT,0,dp(a,24)),null));b.setTextColor(0xff171716);b.setTypeface(null,Typeface.BOLD);return b;}
    public static EditText input(Activity a,String hint,boolean multi){EditText e=new EditText(a);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(a.getPackageName().equals("com.kitchenos.connect")?22:16);e.setSingleLine(!multi);e.setMinHeight(dp(a,48));e.setPadding(dp(a,13),dp(a,12),dp(a,13),dp(a,12));StateListDrawable field=new StateListDrawable();field.addState(new int[]{android.R.attr.state_focused},shape(dark?0xff272b28:0xffffffff,dark?0xffa7aea8:0xff888888,dp(a,15)));field.addState(new int[]{},shape(dark?0xff272b28:0xffffffff,LINE,dp(a,15)));e.setBackground(field);LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-1,-2);margins.bottomMargin=dp(a,8);e.setLayoutParams(margins);if(a.getPackageName().equals("com.kitchenos.connect"))try{Class.forName("com.kitchenos.connect.ui.ApplianceTextEntry").getMethod("attach",Activity.class,EditText.class).invoke(null,a,e);}catch(ReflectiveOperationException ignored){}return e;}
    public static LinearLayout card(Activity a,LinearLayout parent){LinearLayout c=column(a);c.setPadding(dp(a,20),dp(a,12),dp(a,20),dp(a,16));c.setBackground(shape(CARD,0,dp(a,28)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,12),0,dp(a,8));parent.addView(c,p);return c;}
    public static LinearLayout section(Activity a,LinearLayout parent){LinearLayout c=column(a);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,12),0,dp(a,16));parent.addView(c,p);View line=new View(a);line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(a,1)));return c;}
    public static void fitDialog(Activity a,AlertDialog dialog){if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-dp(a,24),dp(a,a.getPackageName().equals("com.kitchenos.connect")?720:480)),-2);if(a.getPackageName().equals("com.kitchenos.connect")&&dialog.getWindow()!=null)dialog.getWindow().getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());if(a.getPackageName().equals("com.kitchenos.connect"))try{Class.forName("com.kitchenos.connect.ui.RotaryDialogs").getMethod("bind",Activity.class,AlertDialog.class).invoke(null,a,dialog);}catch(ReflectiveOperationException ignored){}}
    public static void homeActions(Activity a,LinearLayout page,Runnable favorites,Runnable add,Runnable assistant){
        FrameLayout root=(FrameLayout)((View)page.getParent()).getParent();LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER);
        // Reserve the action area rather than painting controls over recipe text.
        ((FrameLayout.LayoutParams)((View)page.getParent()).getLayoutParams()).bottomMargin=dp(a,96);page.setPadding(page.getPaddingLeft(),page.getPaddingTop(),page.getPaddingRight(),dp(a,24));
        String[] assets={"ic_favorite","ic_add","assistant_plate"},labels={"Favoriten öffnen","Lebensmittel hinzufügen","Assistent öffnen"};Runnable[] actions={favorites,add,assistant};
        for(int i=0;i<3;i++){ImageButton b=new ImageButton(a);b.setContentDescription(labels[i]);int icon=a.getResources().getIdentifier(assets[i],"drawable",a.getPackageName());if(i==2)b.setImageDrawable(new PlateDrawable(true));else b.setImageResource(icon);b.setScaleType(ImageView.ScaleType.FIT_CENTER);int inset=i==2?0:i==0?dp(a,10):dp(a,22);b.setPadding(inset,inset,inset,inset);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(i==1?ACCENT:i==2?0:dark?0xe6212422:0xe6f5f5f3);if(i==0)circle.setStroke(dp(a,2),ACCENT);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ee6542),circle,null));final Runnable action=actions[i];b.setOnClickListener(v->action.run());int size=dp(a,i==1?68:48);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(size,size);if(i>0)p.leftMargin=dp(a,14);row.addView(b,p);}
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,dp(a,68),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=dp(a,20);root.addView(row,p);
    }
}
