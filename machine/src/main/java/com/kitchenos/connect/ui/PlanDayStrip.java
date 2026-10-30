package com.kitchenos.connect.ui;
import android.view.*;import android.widget.*;import java.time.LocalDate;import java.time.format.DateTimeFormatter;import java.util.*;import com.kitchenos.common.Ui;import com.kitchenos.connect.kitchen.KitViews;
/** The shared Calomatik day strip, with stable native touch and knob targets. */
public final class PlanDayStrip extends HorizontalScrollView {
 public interface Selected {void choose(String date);}
 private final LinkedHashMap<String,Button> cells=new LinkedHashMap<>();private final android.app.Activity host;private String current;
 public PlanDayStrip(android.app.Activity context,String current,Selected selected){super(context);host=context;this.current=current;setTag("plan.days");setHorizontalScrollBarEnabled(false);setClipToPadding(false);LinearLayout days=new LinearLayout(context);addView(days);LocalDate today=LocalDate.now();for(int i=0;i<10;i++){LocalDate value=today.plusDays(i);String date=value.toString();Button day=new Button(context);day.setAllCaps(false);day.setText((i==0?"Heute":value.format(DateTimeFormatter.ofPattern("EEE",Locale.GERMAN)))+"\n"+value.getDayOfMonth());day.setTextSize(23);day.setGravity(Gravity.CENTER);day.setPadding(0,0,0,0);day.setMinWidth(0);day.setMinimumWidth(0);day.setTag("plan.day."+date);day.setContentDescription(value.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)));day.setOnClickListener(v->{selectDate(date);selected.choose(date);});LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(Ui.dp(context,78),Ui.dp(context,100));size.rightMargin=Ui.dp(context,8);days.addView(day,size);cells.put(date,day);}selectDate(current);}
 public void selectDate(String date){current=date;for(Map.Entry<String,Button> row:cells.entrySet()){boolean active=row.getKey().equals(date);row.getValue().setSelected(active);row.getValue().setTextColor(active?0xffffffff:Ui.INK);row.getValue().setBackground(KitViews.shape(host,active?Ui.ACCENT:Ui.SOFT,48,0));}}
 @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(cells!=null)for(Button cell:cells.values())cell.setEnabled(enabled);}
 public String selectedDate(){return current;}
}
