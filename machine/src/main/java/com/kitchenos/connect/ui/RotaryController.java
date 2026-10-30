package com.kitchenos.connect.ui;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.ScrollView;
import java.util.*;

/** C3 knob: verified original KnobManager dispatch is F3=clockwise, F4=counterclockwise, F5=press. */
public final class RotaryController {
    public interface HomeInput {void turn(int delta,long time);void press();void hold();default void edited(View view){}default void finished(View view){}}
    private HomeInput homeInput;private final android.os.Handler pressHandler=new android.os.Handler(android.os.Looper.getMainLooper());private boolean holdDelivered;
    private final Runnable homeHold=()->{if(this.pressedAt>=0&&homeNavigation()){longPress=holdDelivered=true;homeInput.hold();}};
    public void homeInput(HomeInput input){homeInput=input;}
    private boolean homeNavigation(){return cycling()&&homeInput!=null;}
    public void cancelPress(){pressHandler.removeCallbacks(homeHold);pressedAt=-1;longPress=holdDelivered=false;}
    public interface Adjustable {void turn(int delta);void editing(boolean value);default void turnTimed(int delta,long eventTime){turn(delta);}}
    private final Activity activity;
    private final Runnable back;
    private java.util.function.BooleanSupplier homeLongPress;
    public void onHomeLongPress(java.util.function.BooleanSupplier action){homeLongPress=action;}
    private final ArrayList<View> targets=new ArrayList<>();
    private final HashMap<String,List<View>> groups=new HashMap<>();
    private final HashMap<View,ReadingRegion> readingRegions=new HashMap<>();
    private final HashMap<View,View> confirmations=new HashMap<>();
    private final HashMap<View,Runnable> confirmActions=new HashMap<>();
    private final HashMap<View,java.util.function.IntConsumer> turnActions=new HashMap<>();
    private View selected,scope;
    private List<View> cookingCycle;private Runnable cookingPress;
    public void cookingPress(Runnable action){cookingPress=action;}
    private boolean editing,longPress;
    private long pressedAt=-1,lastClockwiseTurn=-1,lastCounterclockwiseTurn=-1;
    public RotaryController(Activity activity){this(activity,activity::onBackPressed);}
    public RotaryController(Activity activity,Runnable back){this.activity=activity;this.back=back;C3RotaryBroadcast.bind(activity.getWindow());}
    public void reset(){cancelPress();homeInput=null;scope=null;cookingCycle=null;cookingPress=null;deselect(selected);for(ReadingRegion region:readingRegions.values())region.selected(false);readingRegions.clear();confirmations.clear();confirmActions.clear();turnActions.clear();targets.clear();groups.clear();selected=null;editing=false;pressedAt=-1;longPress=false;lastClockwiseTurn=lastCounterclockwiseTurn=-1;}
    /** Home's physical knob stays within the cooking values and its visible action. */
    public void cookingCycle(List<View> views){cookingCycle=new ArrayList<>(views);}
    private boolean cycling(){return cookingCycle!=null&&scope==null;}
    public void confirmNext(View current,View next){confirmations.put(current,next);}
    public void confirmAction(View current,Runnable action){confirmActions.put(current,action);}
    public void turnAction(View current,java.util.function.IntConsumer action){turnActions.put(current,action);}
    public void add(View view){if(!targets.contains(view))targets.add(view);view.setFocusable(true);view.setFocusableInTouchMode(view instanceof android.widget.EditText||adjustable(view)!=null);view.setTag(com.kitchenos.connect.R.id.rotary_target,Boolean.TRUE);view.setOnFocusChangeListener((v,has)->{if(has)select(v);});}
    /** Register ordinary instruction text as a knob-scrollable region, not a hardware action. */
    public ReadingRegion addReadingRegion(View instruction,ScrollView scroll){
        ReadingRegion region=new ReadingRegion(instruction,scroll);readingRegions.put(instruction,region);add(instruction);
        instruction.setOnClickListener(view->edit(view));return region;
    }
    private Adjustable adjustable(View view){return view instanceof Adjustable?(Adjustable)view:readingRegions.get(view);}
    private void deselect(View view){if(view==null)return;view.setSelected(false);Adjustable control=adjustable(view);if(control!=null)control.editing(false);ReadingRegion reader=readingRegions.get(view);if(reader!=null)reader.selected(false);}
    public void replaceGroup(String name,List<View> views){List<View> old=groups.put(name,views);String label=null;if(old!=null){if(old.contains(selected)){label=String.valueOf(selected.getContentDescription());deselect(selected);selected=null;editing=false;}targets.removeAll(old);}for(View view:views)if(!targets.contains(view))add(view);if(label!=null)for(View view:views)if(label.equals(String.valueOf(view.getContentDescription()))){select(view);break;}}
    public void select(View view){if(cycling()&&!cookingCycle.contains(view)||selected==view)return;deselect(selected);selected=view;editing=false;view.setSelected(true);ReadingRegion reader=readingRegions.get(view);if(reader!=null)reader.selected(true);view.invalidate();
        // A touch-focusable Button consumes its first tap just to acquire focus.
        // Knob selection is tracked here independently of Android touch focus.
        view.setFocusableInTouchMode(view instanceof android.widget.EditText||adjustable(view)!=null);view.requestFocus();}
    public void edit(View view){if(cycling()&&!cookingCycle.contains(view))return;select(view);Adjustable control=adjustable(view);if(control!=null){editing=true;control.editing(true);if(homeNavigation())homeInput.edited(view);}}
    public void toggleEdit(View view){if(selected==view&&editing){deselect(selected);selected=null;editing=false;view.clearFocus();}else edit(view);}
    /** Touch confirmation ends editing without invoking the knob's physical START action. */
    public void finishTouchEdit(View view){if(selected==view&&editing){editing=false;Adjustable control=adjustable(view);if(control!=null)control.editing(false);if(homeNavigation())homeInput.finished(view);}}
    public void touchOutside(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_DOWN||!editing||selected==null)return;Rect bounds=new Rect();selected.getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY())){View previous=selected;deselect(previous);selected=null;editing=false;previous.clearFocus();}}
    public void scope(View root){cancelPress();deselect(selected);selected=null;editing=false;scope=root;}
    private boolean inScope(View view){if(scope==null)return true;if(view instanceof android.widget.Button&&("control.stop".equals(view.getTag())||"STOPP".contentEquals(((android.widget.Button)view).getText())||"home.start".equals(view.getTag())&&("Stoppen".contentEquals(((android.widget.Button)view).getText())||"Abbrechen".contentEquals(((android.widget.Button)view).getText()))))return true;for(View p=view;p!=null;){if(p==scope)return true;ViewParent parent=p.getParent();p=parent instanceof View?(View)parent:null;}return false;}
    private ArrayList<View> visible(){ArrayList<View> list=new ArrayList<>();for(View v:cycling()?cookingCycle:targets)if(v.isShown()&&v.isEnabled()&&inScope(v))list.add(v);return list;}
    public void turn(int direction){turn(direction,android.os.SystemClock.uptimeMillis());}
    private void turn(int direction,long eventTime){
        if(homeNavigation()){homeInput.turn(direction,eventTime);return;}
        if(selected!=null&&(!selected.isShown()||!selected.isEnabled()||cycling()&&!cookingCycle.contains(selected))){deselect(selected);selected=null;editing=false;}
        if(editing&&adjustable(selected)!=null){java.util.function.IntConsumer action=turnActions.get(selected);if(action!=null)action.accept(direction);else adjustable(selected).turnTimed(direction,eventTime);return;}
        ArrayList<View> list=visible();if(list.isEmpty())return;int index=list.indexOf(selected);index=index<0?(direction>0?0:list.size()-1):Math.floorMod(index+direction,list.size());View next=list.get(index);select(next);next.requestFocus();
        if(cycling()){if(adjustable(next)!=null)edit(next);return;}
        ReadingRegion reader=readingRegions.get(next);if(reader!=null){reader.revealStart();return;}
        ViewParent p=next.getParent();while(p instanceof View){if(p instanceof FunctionCarousel){((FunctionCarousel)p).reveal(next);break;}if(p instanceof ScrollView){Rect rect=new Rect();next.getDrawingRect(rect);((ScrollView)p).offsetDescendantRectToMyCoords(next,rect);((ScrollView)p).smoothScrollTo(0,Math.max(0,rect.centerY()-((ScrollView)p).getHeight()/2));break;}p=p.getParent();}
    }
    public void press(){if(homeNavigation()){homeInput.press();return;}if(cycling()&&cookingPress!=null){cookingPress.run();return;}if(selected!=null&&(!selected.isShown()||!selected.isEnabled())){deselect(selected);selected=null;editing=false;}if(selected==null){turn(1);return;}if(editing){editing=false;adjustable(selected).editing(false);Runnable action=confirmActions.get(selected);if(action!=null){action.run();return;}View next=confirmations.get(selected);if(next!=null&&next.isShown()&&next.isEnabled()&&inScope(next)){select(next);if(adjustable(next)!=null)edit(next);next.requestFocus();}return;}if(readingRegions.containsKey(selected))edit(selected);else selected.performClick();}
    private void previousCookingValue(){
        int index=cookingCycle.indexOf(selected);for(int i=Math.max(0,index-1);i>=0;i--){View view=cookingCycle.get(i);if(view.isShown()&&view.isEnabled()){if(adjustable(view)!=null)edit(view);else select(view);return;}}
    }
    public boolean dispatch(KeyEvent event){
        int code=event.getKeyCode();if(code==133&&C3RotaryBroadcast.consumesRawClockwise(activity.getWindow()))return true;if(code!=133&&code!=134&&code!=135&&code!=KeyEvent.KEYCODE_DPAD_CENTER&&code!=KeyEvent.KEYCODE_ENTER)return false;
        // Let the IME keep Enter while a text field is active.
        if((code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_DPAD_CENTER)&&activity.getCurrentFocus() instanceof android.widget.EditText)return false;
        if(code==133||code==134){if(event.getAction()==KeyEvent.ACTION_UP){long at=event.getEventTime();if(code==133&&lastClockwiseTurn!=at){lastClockwiseTurn=at;turn(1,at);}else if(code==134&&lastCounterclockwiseTurn!=at){lastCounterclockwiseTurn=at;turn(-1,at);}}return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(pressedAt<0&&event.getRepeatCount()>0)return true;if(pressedAt<0){pressedAt=event.getEventTime();longPress=holdDelivered=false;if(homeNavigation())pressHandler.postDelayed(homeHold,700);}if(event.getEventTime()-pressedAt>=(homeNavigation()?700:2000))longPress=true;return true;}
        if(event.getAction()==KeyEvent.ACTION_UP){if(pressedAt<0)return true;boolean held=longPress||pressedAt>=0&&event.getEventTime()-pressedAt>=(homeNavigation()?700:2000);boolean delivered=holdDelivered;cancelPress();if(event.isCanceled())return true;if(held&&homeNavigation()){if(!delivered)homeInput.hold();return true;}if(held){if(cycling())previousCookingValue();else if(editing){editing=false;if(adjustable(selected)!=null)adjustable(selected).editing(false);}else if(code!=135||homeLongPress==null||!homeLongPress.getAsBoolean())back.run();}else press();return true;}
        return true;
    }
    public boolean motion(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_SCROLL)return false;float scroll=event.getAxisValue(MotionEvent.AXIS_SCROLL);if(scroll==0)scroll=-event.getAxisValue(MotionEvent.AXIS_VSCROLL);if(scroll==0)return false;turn(scroll>0?1:-1,event.getEventTime());return true;}
    public boolean isEditing(){return editing;}
    public String selection(){return selected==null?"":String.valueOf(selected.getContentDescription());}
}
