package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.TemperatureHistory;
import java.util.List;

/** Draws observations, never extrapolates through missing telemetry. */
public final class TemperatureChartView extends View {
    private final TemperatureHistory history;
    private final boolean compact;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line=new Path();
    public TemperatureChartView(Context c,TemperatureHistory history,boolean compact){super(c);this.history=history;this.compact=compact;setTag(compact?"temperature.sparkline":"temperature.graph");setContentDescription("Temperaturverlauf. Orange: gemessen. Gestrichelt: angefordert. Lücken: keine Messdaten.");}
    @Override protected void onDraw(Canvas c){
        float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
        float left=(compact?5:44)*d,right=w-8*d,top=8*d,bottom=h-(compact?6:32)*d;
        List<TemperatureHistory.Point> values=history.points();
        p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(15*d);p.setStrokeWidth(d);p.setStyle(Paint.Style.STROKE);p.setPathEffect(null);
        int max=200;for(TemperatureHistory.Point v:values)if(v.temperature>200)max=250;
        for(int value=0;value<=max;value+=100){float y=bottom-(bottom-top)*value/max;p.setColor(Ui.LINE);c.drawLine(left,y,right,y,p);if(!compact){p.setStyle(Paint.Style.FILL);p.setColor(Ui.MUTED);c.drawText(""+value,2*d,y+5*d,p);p.setStyle(Paint.Style.STROKE);}}
        if(values.isEmpty()){if(!compact){p.setStyle(Paint.Style.FILL);p.setColor(Ui.MUTED);c.drawText("Noch keine Messdaten",left,top+40*d,p);}return;}
        long first=values.get(0).time,last=values.get(values.size()-1).time;float span=Math.max(10000,last-first);
        for(int target=1;target>=0;target--){
            line.reset();TemperatureHistory.Point previous=null;
            for(TemperatureHistory.Point v:values){float x=left+(right-left)*(v.time-first)/span,y=bottom-(bottom-top)*(target==1?v.target:v.temperature)/max;
                if(previous==null||v.gap||v.time-previous.time>5000)line.moveTo(x,y);
                else {if(target==1)line.lineTo(x,bottom-(bottom-top)*previous.target/max);line.lineTo(x,y);}
                previous=v;
            }
            p.setStyle(Paint.Style.STROKE);p.setColor(target==1?Ui.MUTED:Ui.ACCENT);p.setStrokeWidth((target==1?1.3f:2.5f)*d);p.setPathEffect(target==1?new DashPathEffect(new float[]{5*d,4*d},0):null);c.drawPath(line,p);
        }
        p.setPathEffect(null);p.setStyle(Paint.Style.FILL);p.setColor(Ui.ACCENT);
        // Isolated observations remain visible on either side of a data gap.
        for(int i=0;i<values.size();i++){
            TemperatureHistory.Point v=values.get(i);
            if(i==0||v.gap||i+1<values.size()&&(values.get(i+1).gap||values.get(i+1).time-v.time>5000))
                c.drawCircle(left+(right-left)*(v.time-first)/span,bottom-(bottom-top)*v.temperature/max,2*d,p);
        }
        TemperatureHistory.Point end=values.get(values.size()-1);c.drawCircle(left+(right-left)*(end.time-first)/span,bottom-(bottom-top)*end.temperature/max,3*d,p);
        if(!compact){p.setColor(Ui.MUTED);p.setTextSize(15*d);java.text.DateFormat time=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.GERMANY);c.drawText(time.format(new java.util.Date(first)),left,h-6*d,p);String tail=time.format(new java.util.Date(last));c.drawText(tail,right-p.measureText(tail),h-6*d,p);}
    }
}
