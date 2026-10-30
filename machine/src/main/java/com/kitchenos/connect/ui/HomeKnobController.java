package com.kitchenos.connect.ui;

import android.view.View;
import android.widget.Button;
import com.kitchenos.connect.control.ControlClient;
import com.kitchenos.connect.control.ControlState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Values, final function choices and guarded START; running selection always includes STOP. */
public final class HomeKnobController implements RotaryController.HomeInput {
    private final RotaryController knob;private final HomeNumberControls numbers;
    private final ParameterDial time,temperature,speed;private final GuidedControlPanel panel;
    private int slot;private boolean stop;private long editRevision;private String functionId="";
    private final ArrayList<Button> options=new ArrayList<>();
    public HomeKnobController(RotaryController knob,HomeNumberControls numbers,ParameterDial time,ParameterDial temperature,ParameterDial speed,GuidedControlPanel panel){this.knob=knob;this.numbers=numbers;this.time=time;this.temperature=temperature;this.speed=speed;this.panel=panel;}
    private ParameterDial view(int at){return at==0?time:at==1?temperature:speed;}
    private boolean completedTurbo(){ControlState state=ControlClient.get(numbers.getContext()).state;return numbers.functionId().equals("turbo")&&state.naturalCompletion&&state.idleConfirmed&&!state.ownRun;}
    private boolean riceChoicesShowing(){return !options.isEmpty()&&options.get(0).isShown();}
    private int riceChoiceSlot(){return 4+Math.max(0,Math.min(numbers.selectedFunctionOption(),options.size()-1));}
    private void cycle(){List<View> views=new ArrayList<>(Arrays.asList(time,temperature,speed,numbers.startButton()));options.clear();options.addAll(numbers.functionOptions());views.addAll(options);knob.cookingCycle(views);}
    public void configure(boolean ignored){
        functionId=numbers.functionId();cycle();knob.cookingPress(null);knob.homeInput(this);stop=numbers.stopShowing();choose(stop?3:functionId.equals("rice")?(riceChoicesShowing()?riceChoiceSlot():3):Math.min(slot,2),!stop&&!functionId.equals("rice")&&!completedTurbo());
    }
    public void refresh(){
        boolean changed=!functionId.equals(numbers.functionId()),choicesChanged=!options.equals(numbers.functionOptions());
        if(changed||choicesChanged){functionId=numbers.functionId();cycle();}
        boolean next=numbers.stopShowing();
        // Selecting a completed Turbo duration must not consume its display-only zero.
        if(next!=stop){stop=next;choose(stop?3:functionId.equals("rice")?(riceChoicesShowing()?riceChoiceSlot():3):0,!stop&&!functionId.equals("rice")&&!completedTurbo());}
        else if(changed)choose(functionId.equals("rice")?(riceChoicesShowing()?riceChoiceSlot():3):0,!functionId.equals("rice"));
        else if(functionId.equals("rice")&&!stop&&slot==3&&riceChoicesShowing())choose(riceChoiceSlot(),false);
        else if(slot>=4){if(slot-4>=options.size()||!options.get(slot-4).isShown())choose(3,false);else if(choicesChanged)choose(slot,false);}
    }
    private void choose(int at,boolean edit){
        if(slot<3)knob.finishTouchEdit(view(slot));
        if(at<3&&(numbers.functionId().equals("water")||numbers.functionId().equals("thicken"))||at==0&&numbers.functionId().equals("warm"))at=1;
        if(numbers.functionId().equals("clean")&&at<3)at=stop?3:4+Math.max(0,Math.min(numbers.selectedFunctionOption(),options.size()-1));
        if(at<3&&view(at).getVisibility()!=View.VISIBLE){
            int first=-1;for(int i=0;i<3;i++)if(view(i).getVisibility()==View.VISIBLE&&view(i).isEnabled()){first=i;break;}
            if(first>=0)at=first;
        }
        slot=at;
        if(slot==3){knob.select(numbers.startButton());numbers.knobFocus(-1,false);return;}
        if(slot>=4){knob.select(options.get(slot-4));numbers.focusFunctionOption(slot-4);numbers.knobFocus(-1,false);return;}
        ParameterDial selected=view(slot);knob.select(selected);if(edit&&selected.isEnabled()&&selected.isShown())knob.edit(selected);numbers.knobFocus(slot,knob.isEditing());
    }
    private void advance(int direction,boolean edit){
        int count=stop?4:3,next=slot;
        for(int i=0;i<count;i++){next=Math.floorMod(next+direction,count);if(next==3||view(next).isShown()&&view(next).isEnabled()){choose(next,edit&&next<3);numbers.knobChoices(next);return;}}
    }
    private void controls(){if(!temperature.isShown()&&!time.isShown())numbers.showFunctionControls();}
    private void chooseOptions(){int selected=numbers.selectedFunctionOption();numbers.showFunctionOptions();cycle();choose(4+Math.min(selected,options.size()-1),false);}
    @Override public void turn(int delta,long eventTime){
        refresh();
        if(numbers.functionId().equals("rice")&&!stop){
            controls();if(slot<4){chooseOptions();return;}
            if(slot>=4&&delta!=0)choose(4+Math.floorMod(slot-4+(delta>0?1:-1),options.size()),false);
            return;
        }
        if(numbers.functionId().equals("clean")&&stop){choose(3,false);return;}
        if(numbers.cleaningSelectionPending()){boolean selected=slot>=4&&numbers.cleaningSelectionShowing();chooseOptions();if(delta!=0&&selected)choose(4+Math.floorMod(slot-4+(delta>0?1:-1),options.size()),false);return;}
        controls();
        if(!stop&&slot>=4){if(delta!=0)choose(4+Math.floorMod(slot-4+(delta>0?1:-1),options.size()),false);return;}
        if(!stop&&slot==3){if(delta!=0){if(delta<0&&!options.isEmpty())chooseOptions();else{choose(0,true);numbers.knobChoices(0);}}return;}
        if(stop&&!knob.isEditing()){if(delta!=0)advance(delta>0?1:-1,false);return;}
        ParameterDial selected=view(slot);if(!selected.isEnabled())return;
        if(!knob.isEditing())knob.edit(selected);
        if(slot==0)time.turnTimed(delta,eventTime);else if(slot==1)temperature.turnTimed(delta,eventTime);else numbers.turnSpeed(delta);
        if(delta!=0)numbers.knobChoices(slot);
    }
    @Override public void press(){
        refresh();ControlState state=ControlClient.get(numbers.getContext()).state;
        if(state.potCheck.active()||state.pendingAction.equals("CHECK_POT")){panel.pressHomeKnob();return;}
        if(numbers.functionId().equals("rice")&&!stop&&slot<4){chooseOptions();return;}
        if(numbers.cleaningSelectionPending()&&(!numbers.cleaningSelectionShowing()||slot<4)){chooseOptions();return;}
        if(slot==3){if(numbers.startButton().isShown()&&numbers.startButton().isEnabled())numbers.startButton().performClick();return;}
        controls();
        if(!stop&&slot>=4){Button selected=options.get(slot-4);if(selected.isShown()&&selected.isEnabled())selected.performClick();refresh();if(!stop&&!numbers.functionId().equals("rice")&&!numbers.functionId().equals("clean"))choose(3,false);return;}
        ParameterDial selected=view(slot);
        if(!stop&&completedTurbo()&&!knob.isEditing()){if(selected.isEnabled()){knob.edit(selected);numbers.knobChoices(slot);}return;}
        if(stop&&!knob.isEditing()){if(selected.isEnabled()){knob.edit(selected);numbers.knobChoices(slot);}return;}
        if(!stop&&!numbers.functionId().equals("manual")){
            if(knob.isEditing()){boolean changed=selected.changeRevision()!=editRevision;knob.finishTouchEdit(selected);if(changed)panel.confirmValues(slot==0);}
            for(int next=slot+1;next<3;next++)if(view(next).isShown()&&view(next).isEnabled()){choose(next,true);numbers.knobChoices(next);return;}
            numbers.dismissShortcuts();
            if(numbers.functionId().equals("eggs")){numbers.requestFunctionStart();refresh();if(!stop)choose(0,true);return;}
            if(numbers.functionId().equals("warm")){choose(3,false);return;}
            if(!options.isEmpty()&&!numbers.functionId().equals("water"))chooseOptions();else{numbers.requestFunctionStart();refresh();if(!stop)choose(numbers.functionId().equals("water")?1:3,numbers.functionId().equals("water"));}
            return;
        }
        // Manual cooking retains its accepted nonzero-speed short start and zero-speed cycle.
        if(!stop&&slot==2&&speed.value()!=0){
            if(knob.isEditing())knob.finishTouchEdit(speed);
            numbers.dismissShortcuts();panel.pressHomeKnob();refresh();if(!stop)choose(2,true);return;
        }
        if(knob.isEditing()){boolean changed=selected.changeRevision()!=editRevision;knob.finishTouchEdit(selected);if(changed&&!state.ownRun)panel.confirmValues(slot==0);}
        advance(1,true);
    }
    @Override public void hold(){
        refresh();ControlClient client=ControlClient.get(numbers.getContext());if(client.lidInterruption.active()&&!client.state.ownRun&&!client.state.stopping){panel.pressHomeKnob();return;}if(stop){panel.requestStop();return;}
        if(numbers.functionId().equals("rice")){press();return;}
        if(numbers.pureeSelectionPending()){chooseOptions();return;}
        if(numbers.cleaningSelectionPending()||slot>=4){press();return;}
        if(numbers.functionId().equals("warm")||numbers.eggTimeSelected()){choose(3,false);return;}
        if(slot<3&&knob.isEditing())knob.finishTouchEdit(view(slot));
        numbers.dismissShortcuts();panel.pressHomeKnob();
    }
    @Override public void edited(View selected){
        if(selected==time)slot=0;else if(selected==temperature)slot=1;else if(selected==speed)slot=2;else return;
        editRevision=view(slot).changeRevision();numbers.knobFocus(slot,true);
    }
    @Override public void finished(View selected){if(slot<3&&selected==view(slot))numbers.knobFocus(slot,false);}
    public int slot(){return slot;}
}
