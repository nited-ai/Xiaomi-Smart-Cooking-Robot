package com.kitchenos.connect.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.FrameLayout;
import com.kitchenos.connect.control.ControlClient;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Scene for the selected function. Cup taps delegate to the existing guarded UI. */
public final class FunctionVesselView extends FrameLayout {
    private final HomeVesselWebGLView vessel;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BooleanSupplier reverse;
    private final IntSupplier speed;
    private final HomeCupState cup=new HomeCupState();
    private Runnable cupStart=()->{};
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(!isAttachedToWindow())return;
        if(isShown()){
            vessel.mode(reverse.getAsBoolean(),false,true);vessel.level(speed.getAsInt()/10f);
            long now=SystemClock.elapsedRealtime();cup.observe(ControlClient.get(getContext()).state,now);
            vessel.update(ControlClient.get(getContext()).state,now,cup);
        }
        main.postDelayed(this,200);
    }};
    public FunctionVesselView(Context context,String function,BooleanSupplier reverse,IntSupplier speed){
        super(context);this.reverse=reverse;this.speed=speed;
        setTag("function.hero");vessel=new HomeVesselWebGLView(context);vessel.tool(AccessorySelection.visualTool(function,false));addView(vessel,new LayoutParams(-1,-1));
        vessel.setOnClickListener(v->{com.kitchenos.connect.control.ControlState state=ControlClient.get(context).state;long now=SystemClock.elapsedRealtime();
            if(state.allows("START",now,false)&&cup.confirm(state,now)){vessel.update(state,now,cup);cupStart.run();}});
    }
    public void onCupStart(Runnable action){cupStart=action;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();main.post(refresh);}
    @Override protected void onDetachedFromWindow(){main.removeCallbacks(refresh);cup.reset();super.onDetachedFromWindow();}
}
