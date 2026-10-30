package com.kitchenos.connect.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import com.kitchenos.common.Ui;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Minute clock with German date labels, independent of the appliance's system locale. */
public final class KitchenClock extends TextView {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm   EEE, dd. MMM",Locale.GERMAN);
    private final Runnable tick=new Runnable(){public void run(){setText(format.format(ZonedDateTime.now()));main.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    public KitchenClock(Context context){super(context);setTag("header.clock");setTextSize(28);setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));setLetterSpacing(-.02f);setTextColor(Ui.INK);setGravity(Gravity.CENTER);setSingleLine(true);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();tick.run();}
    @Override protected void onDetachedFromWindow(){main.removeCallbacks(tick);super.onDetachedFromWindow();}
}
