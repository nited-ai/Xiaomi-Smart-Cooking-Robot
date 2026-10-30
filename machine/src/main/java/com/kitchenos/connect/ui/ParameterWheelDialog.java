package com.kitchenos.connect.ui;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;
import java.util.*;

/** Local touch editor. Merely opening or scrolling it never submits a machine action. */
public final class ParameterWheelDialog {
 public static Dialog show(Activity activity,ParameterDial dial,Runnable committed,Runnable stopped){
  Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(activity,24),dp(activity,20),dp(activity,24),dp(activity,24));content.setBackground(KitViews.shape(activity,Ui.CARD,30,0));content.setTag("parameter.editor");
  String id=String.valueOf(dial.getTag());boolean time=id.endsWith("duration"),temperature=id.endsWith("temperature");
  TextView title=KitViews.text(activity,time?"Zeit":temperature?"Temperatur":"Stufe",28,Ui.INK,true);content.addView(title);LinearLayout row=new LinearLayout(activity);content.addView(row);
  RotaryController knob=new RotaryController(activity,dialog::cancel);ArrayList<Wheel> wheels=new ArrayList<>();
  if(time){
   int maxMinutes=dial.maximum()/60;Wheel minutes=new Wheel(activity,"Min",sequence(0,maxMinutes,1),dial.value()/60);Wheel seconds=new Wheel(activity,"Sek",sequence(0,59,1),dial.value()%60);minutes.setTag("parameter.editor.minutes");seconds.setTag("parameter.editor.seconds");
   minutes.changed=()->{seconds.limitTo(minutes.value()==maxMinutes?dial.maximum()%60:59);};minutes.changed.run();
   final int[] secondsAtTouch={seconds.value()};minutes.touchStarted=()->secondsAtTouch[0]=seconds.value();minutes.touchCancelled=()->seconds.choose(secondsAtTouch[0]);wheels.add(minutes);wheels.add(seconds);
  }else{
   ArrayList<Integer> permitted=new ArrayList<>();for(int value=dial.minimum();value<=dial.maximum();value++)if(dial.accepts(value))permitted.add(value);
   // An existing recipe value remains visible even if it requires an explicit correction.
   if(!permitted.contains(dial.value())){permitted.add(dial.value());Collections.sort(permitted);}
   Wheel value=new Wheel(activity,temperature?"Â°C":"Stufe",permitted,dial.value());value.speed=!temperature;value.setTag("parameter.editor.value");wheels.add(value);
  }
  for(Wheel wheel:wheels){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(activity,NumberWheelStyle.HEIGHT_DP),1);size.setMargins(dp(activity,6),0,dp(activity,6),0);row.addView(wheel,size);knob.add(wheel);wheel.setOnClickListener(v->knob.edit(wheel));}
  LinearLayout actions=new LinearLayout(activity);content.addView(actions);Button cancel=Ui.button(activity,"ZurÃ¼ck",v->dialog.cancel());cancel.setTag("parameter.editor.cancel");Button done=Ui.button(activity,"Ãœbernehmen",v->{int value=time?wheels.get(0).value()*60+wheels.get(1).value():wheels.get(0).value();if(value<dial.minimum()||value>dial.maximum()||!dial.accepts(value)&&value!=dial.value()){Toast.makeText(activity,"WÃ¤hle einen Wert im verfÃ¼gbaren Bereich.",Toast.LENGTH_SHORT).show();return;}dial.value(value);dialog.dismiss();if(committed!=null)committed.run();});done.setTag("parameter.editor.apply");
  Button stop=null;if(stopped!=null){stop=Ui.button(activity,"Stoppen",v->{dialog.dismiss();stopped.run();});stop.setTag("parameter.editor.stop");}
  for(Button button:stop==null?new Button[]{cancel,done}:new Button[]{cancel,stop,done}){button.setAllCaps(false);button.setTextSize(24);button.setTextColor(Ui.INK);button.setBackground(ControlFocus.background(activity,button==stop?0xffa63038:button==done?Ui.ACCENT:Ui.SOFT));if(button==stop)button.setTextColor(0xffffffff);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(activity,78),1);size.setMargins(dp(activity,6),0,dp(activity,6),0);actions.addView(button,size);knob.add(button);}
  for(int i=0;i<wheels.size()-1;i++)knob.confirmNext(wheels.get(i),wheels.get(i+1));knob.confirmNext(wheels.get(wheels.size()-1),done);
  dialog.setContentView(content);dialog.setCanceledOnTouchOutside(true);com.kitchenos.connect.ui.C3RotaryBroadcast.bind(dialog.getWindow());dialog.setOnKeyListener((d,key,event)->knob.dispatch(event));dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(dp(activity,700),activity.getResources().getDisplayMetrics().widthPixels-dp(activity,48)),ViewGroup.LayoutParams.WRAP_CONTENT);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.22f;window.setAttributes(params);knob.edit(wheels.get(0));return dialog;
 }
 private static ArrayList<Integer> sequence(int min,int max,int step){ArrayList<Integer> values=new ArrayList<>();for(int i=min;i<=max;i+=step)values.add(i);return values;}
 private static int dp(Activity a,int value){return Ui.dp(a,value);}
 static final class Wheel extends View implements RotaryController.Adjustable {
  final ArrayList<Integer> values;final Paint paint=new Paint(3);final String unit;
  final HomeControlMath.WheelDrag drag=HomeControlMath.WheelDrag.direct();
  final HomeControlMath.ZeroStopDrag bounds=new HomeControlMath.ZeroStopDrag();
  final TouchMotionBridge bridge=new TouchMotionBridge();
  final Shader fade;final PorterDuffXfermode fadeMode=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
  int index,ceiling=Integer.MAX_VALUE,startIndex;boolean speed,editing,moved,holding;
  float down,position,lead;Runnable changed,touchStarted,touchCancelled;android.animation.ValueAnimator settling;
  final Runnable frame=new Runnable(){public void run(){
   if(!holding||!moved){stopBridge();return;}
   long now=android.os.SystemClock.uptimeMillis();lead=(bridge.frame(now)-bridge.measured())/HomeControlMath.DIRECT_ROW_DP;
   invalidate();if(bridge.needsFrame(now))postOnAnimation(this);
  }};
  Wheel(Activity a,String unit,ArrayList<Integer> values,int initial){super(a);this.values=values;this.unit=unit;index=Math.max(0,values.indexOf(initial));position=index;fade=NumberWheelStyle.fade(NumberWheelStyle.WINDOW_DP*getResources().getDisplayMetrics().density);setContentDescription(unit);setFocusable(true);setDefaultFocusHighlightEnabled(false);}
  int maximum(){int at=values.size()-1;while(at>0&&values.get(at)>ceiling)at--;return at;}
  int value(){return values.get(index);}
  void select(int next){next=Math.max(0,Math.min(maximum(),next));if(index!=next){index=next;if(changed!=null)changed.run();}invalidate();}
  void choose(int value){cancelSettle();stopBridge();select(Math.max(0,values.indexOf(value)));position=index;invalidate();}
  void limitTo(int value){ceiling=value;if(index>maximum())choose(values.get(maximum()));else invalidate();}
  public void turn(int delta){cancelSettle();stopBridge();select(index+delta);position=index;invalidate();}
  public void editing(boolean value){editing=value;invalidate();}
  String text(int index){int value=values.get(index);return speed?value==0?"Aus":String.format(Locale.GERMAN,"%.1f",value/10.0):unit.equals("°C")&&value==0?"Aus":String.format(Locale.GERMAN,unit.equals("Min")||unit.equals("Sek")?"%02d":"%d",value);}
  float visualPosition(){float visible=Math.max(0,Math.min(maximum(),position+(holding?lead:0)));return holding?bounds.constrainVisual(visible):visible;}
  protected void onDraw(Canvas c){
   float density=getResources().getDisplayMetrics().density,step=HomeControlMath.DIRECT_ROW_DP*density,window=NumberWheelStyle.WINDOW_DP*density,center=window/2;
   paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.SOFT);c.drawRoundRect(0,center-34*density,getWidth(),center+34*density,18*density,18*density,paint);
   float visual=visualPosition();int base=(int)Math.floor(visual);int layer=c.saveLayer(0,0,getWidth(),window,null);c.clipRect(0,0,getWidth(),window);
   paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
   for(int i=-2;i<=3;i++){
    int at=base+i;if(at<0||at>maximum())continue;float distance=at-visual;if(Math.abs(distance)>2.5f)continue;
    float depth=Math.max(0,(float)Math.cos(distance*Math.PI/6)),weight=depth*depth;
    paint.setTextSize((26+48*weight*weight*weight)*density);paint.setColor(at==index?Ui.ACCENT:Ui.MUTED);
    c.drawText(text(at),getWidth()/2f,center+distance*step-(paint.ascent()+paint.descent())/2,paint);
   }
   paint.setShader(fade);paint.setXfermode(fadeMode);paint.setAlpha(255);c.drawRect(0,0,getWidth(),window,paint);paint.setShader(null);paint.setXfermode(null);c.restoreToCount(layer);
   paint.setTextSize(21*density);paint.setColor(editing?Ui.ACCENT:Ui.MUTED);c.drawText(unit,getWidth()/2f,getHeight()-10*density,paint);
  }
  void cancelSettle(){if(settling!=null){settling.cancel();settling=null;}}
  void stopBridge(){removeCallbacks(frame);bridge.finish();lead=0;}
  float travel(MotionEvent e){
   float density=getResources().getDisplayMetrics().density;
   for(int i=0;i<e.getHistorySize();i++){float steps=drag.move((down-e.getHistoricalY(i))/density,e.getHistoricalEventTime(i));bridge.sample(steps*HomeControlMath.DIRECT_ROW_DP,e.getHistoricalEventTime(i));}
   float steps=drag.move((down-e.getY())/density,e.getEventTime());bridge.sample(steps*HomeControlMath.DIRECT_ROW_DP,e.getEventTime());return steps;
  }
  void measuredMove(float steps,MotionEvent e){
   boolean wasHeld=bounds.holding();position=bounds.move(steps,(down-e.getY())/getResources().getDisplayMetrics().density);
   if(wasHeld||bounds.holding()){lead=0;bridge.reset(steps*HomeControlMath.DIRECT_ROW_DP,e.getEventTime());}
   select(Math.round(position));
  }
  void settle(){
   cancelSettle();settling=android.animation.ValueAnimator.ofFloat(position,index);settling.setDuration(160);settling.setInterpolator(new android.view.animation.DecelerateInterpolator(2));
   settling.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});settling.start();
  }
  public boolean onTouchEvent(MotionEvent e){
   float density=getResources().getDisplayMetrics().density;
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     cancelSettle();stopBridge();down=e.getY();startIndex=index;position=index;drag.reset(e.getEventTime());bounds.reset(position,0,maximum(),values.get(0)==0?0:-1);bridge.reset(0,e.getEventTime());moved=false;holding=true;
     if(touchStarted!=null)touchStarted.run();getParent().requestDisallowInterceptTouchEvent(true);performClick();return true;
    case MotionEvent.ACTION_MOVE:
     float steps=travel(e);if(!moved&&Math.abs(e.getY()-down)<ViewConfiguration.get(getContext()).getScaledTouchSlop())return true;
     moved=true;measuredMove(steps,e);removeCallbacks(frame);postOnAnimation(frame);return true;
    case MotionEvent.ACTION_UP:
     float visible=visualPosition();
     if(moved)measuredMove(travel(e),e);
     else{float distance=(e.getY()/density-NumberWheelStyle.WINDOW_DP/2f)/HomeControlMath.DIRECT_ROW_DP;if(Math.abs(distance)>.6f&&Math.abs(distance)<2.45f)select(index+Math.round(distance));}
     stopBridge();holding=false;position=visible;settle();getParent().requestDisallowInterceptTouchEvent(false);return true;
    case MotionEvent.ACTION_CANCEL:
     float cancelledVisual=visualPosition();stopBridge();holding=false;select(startIndex);if(touchCancelled!=null)touchCancelled.run();position=cancelledVisual;settle();getParent().requestDisallowInterceptTouchEvent(false);return true;
    default:return true;
   }
  }
  public boolean performClick(){super.performClick();return true;}
  protected void onDetachedFromWindow(){holding=false;stopBridge();cancelSettle();super.onDetachedFromWindow();}
  public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.NumberPicker");info.setText(text(index)+" "+unit);info.addAction(4096);info.addAction(8192);}
  public boolean performAccessibilityAction(int action,Bundle args){if(action==4096){turn(1);return true;}if(action==8192){turn(-1);return true;}return super.performAccessibilityAction(action,args);}
 }
 private ParameterWheelDialog(){}
}
