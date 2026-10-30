package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.kitchenos.common.Ui;
import org.json.JSONObject;

/** Calomatic meal-card grammar: four gram bars, scaled to the largest known value. */
public final class NutritionBars extends View {
    private static final String[] KEYS={"protein","fat","carbs","fiber"},LABELS={"Protein","Fett","KH","Ballastst."};
    private static final int[] COLORS={0xfffa7e78,0xfff6c553,0xff5e91e9,0xff54bf78};
    private final double[] grams=new double[4];private final Paint paint=new Paint(3);private final boolean onPhoto;
    public NutritionBars(Context context,JSONObject values,boolean onPhoto){super(context);this.onPhoto=onPhoto;values(values);setTag("nutrition.bars");}
    public void values(JSONObject values){StringBuilder description=new StringBuilder(values!=null&&values.optBoolean("_estimated")?"Geschätzte Makros in Gramm je Portion. ":"Makroverhältnis in Gramm je Portion. ");
        for(int i=0;i<4;i++){Object raw=values==null?null:values.opt(KEYS[i]);double value=raw instanceof Number?((Number)raw).doubleValue():Double.NaN;grams[i]=Double.isFinite(value)&&value>=0&&value<=1000000?value:Double.NaN;description.append(LABELS[i]).append(": ").append(Double.isFinite(grams[i])?String.format(java.util.Locale.GERMAN,"%.0f g",grams[i]):"unbekannt").append(". ");}
        setContentDescription(description);invalidate();
    }
    public double grams(int index){return grams[index];}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,w=getWidth()/4f;double max=0;for(double value:grams)if(Double.isFinite(value))max=Math.max(max,value);
        for(int i=0;i<4;i++){float x=w*(i+.5f),top=6*d,bottom=getHeight()-34*d,barWidth=13*d;paint.setColor(onPhoto?0x35ffffff:Ui.LINE);c.drawRoundRect(x-barWidth/2,top,x+barWidth/2,bottom,barWidth/2,barWidth/2,paint);
            if(Double.isFinite(grams[i])&&max>0&&grams[i]>0){paint.setColor(COLORS[i]);float h=(float)(grams[i]/max)*(bottom-top);c.drawRoundRect(x-barWidth/2,bottom-h,x+barWidth/2,bottom,barWidth/2,barWidth/2,paint);}
            paint.setColor(onPhoto?Color.WHITE:Ui.INK);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(15*d);c.drawText(Double.isFinite(grams[i])?String.format(java.util.Locale.GERMAN,"%.0f g",grams[i]):"—",x,getHeight()-16*d,paint);paint.setTextSize(12*d);paint.setTypeface(Typeface.DEFAULT);paint.setColor(onPhoto?0xffefefed:Ui.MUTED);c.drawText(LABELS[i],x,getHeight()-2*d,paint);
        }
    }
}
