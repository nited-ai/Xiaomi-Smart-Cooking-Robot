package com.kitchenos.connect.ui;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.control.TemperatureHistory;

/** Header warning survives navigation, STOP and missing measurements. */
public final class HeatWarningView extends TextView {
    private final View normalTitle;
    private final ControlClient client;
    private final Runnable tick=new Runnable(){public void run(){refresh();if(isAttachedToWindow())postDelayed(this,500);}};
    public HeatWarningView(Context c,View normalTitle){super(c);this.normalTitle=normalTitle;client=ControlClient.get(c);setTag("temperature.warning");setGravity(Gravity.CENTER);setTextColor(0xffff8064);setTextSize(21);setMaxLines(2);setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);setVisibility(GONE);}
    public void refresh(){
        TemperatureHistory h=client.temperatureHistory;boolean hot=h.hot();setVisibility(hot?VISIBLE:GONE);normalTitle.setVisibility(hot?INVISIBLE:VISIBLE);
        boolean live=client.state.foreground&&client.state.connected&&h.fresh(SystemClock.elapsedRealtime());
        String text="Vorsicht, sehr heiß\n"+(live?h.temperature()+" °C · Topf nicht berühren":"Restwärme · Messwert fehlt");
        if(!text.contentEquals(getText()))setText(text);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
}
