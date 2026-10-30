package com.kitchenos.connect.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.*;

/** Compact history entry in the Home header, clear of the moving scale label. */
public final class TemperaturePanel extends LinearLayout {
    private final ControlClient client;
    private final TemperatureChartView chart;
    private AlertDialog detail;
    private final Runnable tick=new Runnable(){public void run(){refresh();if(isAttachedToWindow())postDelayed(this,500);}};
    public TemperaturePanel(Context c){
        super(c);client=ControlClient.get(c);setTag("temperature.panel");setOrientation(VERTICAL);setPadding(dp(6),0,dp(6),0);
        TextView open=label(16,Ui.INK);open.setText("Verlauf ›");addView(open);
        chart=new TemperatureChartView(c,client.temperatureHistory,true);addView(chart,new LayoutParams(-1,dp(30)));
        setFocusable(true);setClickable(true);setOnClickListener(v->showGraph());refresh();
    }
    private int dp(int n){return Ui.dp(getContext(),n);}
    private TextView label(int size,int color){TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(color);v.setMaxLines(1);return v;}
    public void refresh(){
        TemperatureHistory h=client.temperatureHistory;boolean fresh=client.state.foreground&&client.state.connected&&h.fresh(SystemClock.elapsedRealtime());
        String number=fresh?h.temperature()+" °C":h.temperature()>0?"Zuletzt "+h.temperature()+"°":"— °C";
        String status=!fresh?"Messwert fehlt":h.heating(SystemClock.elapsedRealtime())?"Heizt · Ziel "+h.target()+"°":h.target()>0?"Ziel "+h.target()+" °C":h.hot()?"Restwärme":"Topftemperatur";
        chart.invalidate();setContentDescription(number+". "+status+". Temperaturverlauf ansehen");
    }
    private void showGraph(){
        if(detail!=null)return;
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView current=label(23,Ui.INK);current.setMaxLines(3);content.addView(current);
        TemperatureChartView graph=new TemperatureChartView(getContext(),client.temperatureHistory,false);content.addView(graph,new LayoutParams(-1,dp(280)));
        TextView legend=label(17,Ui.MUTED);legend.setMaxLines(4);legend.setText("Orange: gemessen · Gestrichelt: angefordert\nTopfsensor, keine Kerntemperatur. Lücken bedeuten fehlende Daten. Bis zu 60 Minuten Verlauf.");content.addView(legend);
        detail=new AlertDialog.Builder(getContext()).setTitle("Temperaturverlauf").setView(content).setPositiveButton("Schließen",null).setNegativeButton("Stoppen",null).create();
        final Runnable update=new Runnable(){public void run(){if(detail==null)return;TemperatureHistory h=client.temperatureHistory;boolean fresh=client.state.connected&&client.state.foreground&&h.fresh(SystemClock.elapsedRealtime());current.setText((h.hot()?"Vorsicht, sehr heiß · ":"")+(fresh?h.temperature()+" °C":"Messwert fehlt")+(h.target()>0?"\nAngefordert: "+h.target()+" °C":""));graph.invalidate();detail.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(client.state.ownRun||client.state.stopping||client.state.uncertainRun?VISIBLE:GONE);postDelayed(this,500);}};
        detail.setOnDismissListener(d->{removeCallbacks(update);detail=null;});detail.show();
        detail.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{client.stop();if(detail!=null)detail.dismiss();});post(update);
        if(getContext() instanceof android.app.Activity)RotaryDialogs.bind((android.app.Activity)getContext(),detail);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);if(detail!=null)detail.dismiss();super.onDetachedFromWindow();}
}
