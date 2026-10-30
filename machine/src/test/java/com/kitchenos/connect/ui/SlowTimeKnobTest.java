package com.kitchenos.connect.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SlowTimeKnobTest {
    @Test public void deliberateTurnsStayAtOneMinute(){
        SlowTimeKnob knob=new SlowTimeKnob();
        for(int i=0;i<40;i++)assertEquals(60,knob.seconds(1,1000+i*300));
    }
    @Test public void sustainedFastTurnsCrossHoursThroughMinutes(){
        SlowTimeKnob knob=new SlowTimeKnob();int total=0,first=0,last=0;
        for(int i=0;i<20;i++){
            int delta=knob.seconds(1,1000+i*60);if(i==0)first=delta;last=delta;
            assertTrue(delta>=60&&delta<=300);assertEquals(0,delta%60);total+=delta;
        }
        assertEquals(60,first);assertEquals(300,last);
        assertTrue(total>=3600&&total<7200);
    }
    @Test public void reversalPauseAndNewEditResetAcceleration(){
        SlowTimeKnob knob=new SlowTimeKnob();
        for(int i=0;i<25;i++)knob.seconds(1,1000+i*40);
        assertEquals(-60,knob.seconds(-1,2000));
        for(int i=0;i<25;i++)knob.seconds(-1,2040+i*40);
        assertEquals(-60,knob.seconds(-1,4000));
        knob.reset();assertEquals(-60,knob.seconds(-1,4040));
        assertEquals(0,knob.seconds(0,4041));
        assertEquals(-60,knob.seconds(-1,4040));
    }
}
