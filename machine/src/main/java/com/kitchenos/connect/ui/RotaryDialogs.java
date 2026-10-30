package com.kitchenos.connect.ui;

import android.app.*;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;

/** A dialog owns knob events while open; the covered page cannot receive clicks. */
public final class RotaryDialogs {
    /** Branded interruption prompt; keep the existing cancel-first knob routing. */
    public static void bindInterruption(Activity activity,AlertDialog dialog){
        bind(activity,dialog);
        dialog.getWindow().setBackgroundDrawable(com.kitchenos.connect.kitchen.KitViews.shape(activity,Ui.BG,28,Ui.LINE));
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null){message.setTextSize(24);message.setTextColor(Ui.MUTED);message.setLineSpacing(Ui.dp(activity,6),1);}
        int titleId=activity.getResources().getIdentifier("alertTitle","id","android");
        TextView title=dialog.findViewById(titleId);
        if(title!=null){title.setTextSize(32);title.setTextColor(Ui.INK);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));}
        Button stay=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(stay.getParent() instanceof LinearLayout){LinearLayout actions=(LinearLayout)stay.getParent();actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,28));}
        int index=0;
        for(int action:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}){
            Button button=dialog.getButton(action);button.setAllCaps(false);button.setTextSize(24);button.setTextColor(Ui.ACCENT);
            button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));button.setGravity(Gravity.CENTER);button.setSingleLine(true);
            button.setPadding(Ui.dp(activity,20),0,Ui.dp(activity,20),0);button.setMinHeight(Ui.dp(activity,104));button.setMinimumHeight(Ui.dp(activity,104));
            button.setStateListAnimator(null);button.setElevation(0);button.setDefaultFocusHighlightEnabled(false);
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
            for(int state:new int[]{android.R.attr.state_selected,android.R.attr.state_focused})states.addState(new int[]{state},com.kitchenos.connect.kitchen.KitViews.shape(activity,Ui.SOFT,52,Ui.ACCENT));
            states.addState(new int[]{},com.kitchenos.connect.kitchen.KitViews.shape(activity,android.graphics.Color.TRANSPARENT,52,Ui.ACCENT));
            button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ee6542),states,null));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,Ui.dp(activity,104),action==AlertDialog.BUTTON_NEGATIVE?1:2);size.leftMargin=Ui.dp(activity,index++==0?0:14);button.setLayoutParams(size);
        }
    }
    public static void bind(Activity activity, AlertDialog dialog) {
        if(Boolean.TRUE.equals(dialog.getWindow().getDecorView().getTag(com.kitchenos.connect.R.id.rotary_target)))return;dialog.getWindow().getDecorView().setTag(com.kitchenos.connect.R.id.rotary_target,Boolean.TRUE);enlarge(activity,dialog);RotaryController knob=new RotaryController(activity,dialog::cancel);
        View decor=dialog.getWindow().getDecorView();
        decor.post(()->{collect(decor,knob);Button cancelButton=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(cancelButton!=null&&cancelButton.isShown()){knob.select(cancelButton);cancelButton.requestFocus();}});
        decor.setOnTouchListener((v,event)->{knob.touchOutside(event);return false;});
        com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->{
            if(key==135&&event.getAction()==KeyEvent.ACTION_UP&&event.getEventTime()-event.getDownTime()>=2000){dialog.cancel();return true;}
            return knob.dispatch(event);
        });
        // A press without turning first must not approve a cooking run.
        Button cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(cancel!=null&&cancel.isShown()){knob.select(cancel);cancel.requestFocus();}
        Ui.fitDialog(activity,dialog);
    }
    private static void enlarge(Activity activity,AlertDialog dialog){
        TextView message=dialog.findViewById(android.R.id.message);if(message!=null){message.setTextSize(25);message.setLineSpacing(Ui.dp(activity,5),1);}
        int titleId=activity.getResources().getIdentifier("alertTitle","id","android");TextView title=dialog.findViewById(titleId);if(title!=null)title.setTextSize(30);
        Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE),negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(positive!=null&&positive.getParent() instanceof ViewGroup){ViewGroup oldBar=(ViewGroup)positive.getParent();ViewParent parent=oldBar.getParent();if(parent instanceof ViewGroup){ViewGroup container=(ViewGroup)parent;int barIndex=container.indexOfChild(oldBar);ViewGroup.LayoutParams barParams=oldBar.getLayoutParams();LinearLayout actions=new LinearLayout(activity);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(Ui.dp(activity,16),Ui.dp(activity,8),Ui.dp(activity,16),Ui.dp(activity,16));
            for(int action:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}){Button button=dialog.getButton(action);if(button==null||button.getVisibility()==View.GONE)continue;((ViewGroup)button.getParent()).removeView(button);button.setAllCaps(false);button.setTextSize(25);button.setMinHeight(Ui.dp(activity,82));button.setMinimumHeight(Ui.dp(activity,82));button.setTextColor(action==AlertDialog.BUTTON_POSITIVE?0xff171716:Ui.INK);button.setBackground(com.kitchenos.connect.kitchen.KitViews.shape(activity,action==AlertDialog.BUTTON_POSITIVE?Ui.ACCENT:Ui.CARD,18,0));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,Ui.dp(activity,82));size.topMargin=Ui.dp(activity,8);actions.addView(button,size);}
            container.removeView(oldBar);barParams.height=ViewGroup.LayoutParams.WRAP_CONTENT;barParams.width=ViewGroup.LayoutParams.MATCH_PARENT;container.addView(actions,barIndex,barParams);}}

    }
    private static void collect(View view,RotaryController knob){
        if(view instanceof ListView){ListView list=(ListView)view;for(int i=0;i<list.getChildCount();i++){View child=list.getChildAt(i);final int position=list.getFirstVisiblePosition()+i;child.setClickable(true);child.setOnClickListener(v->list.performItemClick(v,position,list.getItemIdAtPosition(position)));knob.add(child);}return;}
        if(view instanceof ParameterDial){knob.add(view);view.setOnClickListener(v->knob.toggleEdit(v));return;}
        if(view instanceof Button||view instanceof CheckBox||view instanceof EditText){knob.add(view);return;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),knob);
    }
    private RotaryDialogs(){}
}
