package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class EggCookingTest {
    private ControlState state(int temperature){
        ControlState s=new ControlState();
        s.connected=s.foreground=s.rootVerified=s.ownRun=s.coverVerified=true;
        s.potCheck.closedInterlock();s.state=1;s.samples=5;s.error=0;s.coverOpen=0;s.pot=1;
        s.statusAt=s.infoAt=1000;s.statusSequence=6;s.acceptedCommandStatusSequence=5;
        s.acceptedCommandSequence=2;s.temperature=temperature;return s;
    }
    @Test public void coldWaterDoesNotConsumeSelectedTime(){
        EggCooking eggs=new EggCooking();eggs.begin(360,2,1000);
        ControlState s=state(95);
        assertFalse(eggs.observe(s,"status",1000,false));
        assertTrue(eggs.preheating());assertEquals(360,eggs.remainingSeconds(60000));
        s.temperature=100;s.statusAt=s.infoAt=61000;
        assertFalse(eggs.observe(s,"status",61000,false));
        assertTrue(eggs.needsRebase());
        eggs.rebasing(3,61000);assertFalse(eggs.rebaseTimedOut(64000));assertTrue(eggs.rebaseTimedOut(64001));
        eggs.rebaseAccepted();assertTrue(eggs.rebased());
        s.acceptedCommandSequence=3;s.acceptedCommandStatusSequence=6;s.statusSequence=7;
        assertEquals(360,eggs.remainingSeconds(61000));
        s.statusAt=s.infoAt=420999;
        assertFalse(eggs.observe(s,"status",420999,false));
        s.statusAt=s.infoAt=421000;
        assertTrue(eggs.observe(s,"status",421000,false));
        assertTrue(eggs.completed());assertEquals(0,eggs.remainingSeconds(421000));
    }
    @Test public void hotWaterStartsOnFirstFreshConfirmedStatus(){
        EggCooking eggs=new EggCooking();eggs.begin(480,2,1000);ControlState s=state(100);
        s.acceptedCommandSequence=1;assertFalse(eggs.observe(s,"status",1000,false));
        s.acceptedCommandSequence=2;s.pendingAction="START";assertFalse(eggs.observe(s,"status",1000,false));
        s.pendingAction="";s.statusSequence=5;assertFalse(eggs.observe(s,"status",1000,false));
        s.statusSequence=6;assertFalse(eggs.observe(s,"info",1000,false));
        assertFalse(eggs.observe(s,"status",1000,false));assertFalse(eggs.preheating());
    }
    @Test public void staleOpenOrMissingPotCannotStartClock(){
        EggCooking eggs=new EggCooking();eggs.begin(60,2,1000);ControlState s=state(100);
        assertFalse(eggs.observe(s,"status",1501,false));
        s.coverOpen=1;assertFalse(eggs.observe(s,"status",1000,false));
        s.coverOpen=0;s.potVerified=true;s.pot=0;
        assertFalse(eggs.observe(s,"status",1000,false));
        assertTrue(eggs.preheating());
    }
    @Test public void missingTargetTimesOutWithoutClaimingCompletion(){
        EggCooking eggs=new EggCooking();eggs.begin(480,2,1000);ControlState s=state(95);
        assertFalse(eggs.observe(s,"status",1000,false));
        assertFalse(eggs.preheatTimedOut(480999));
        assertTrue(eggs.preheatTimedOut(481000));
        assertTrue(eggs.preheatLimit());assertFalse(eggs.completed());
    }
    @Test public void lidPauseCountsElapsedWallTimeOnlyAfterExplicitResume(){
        EggCooking eggs=new EggCooking();eggs.begin(60,2,1000);ControlState s=state(100);
        assertFalse(eggs.observe(s,"status",1000,false));
        s.ownRun=false;s.coverOpen=1;
        assertFalse(eggs.observe(s,"stopped",2000,true));assertTrue(eggs.suspended());
        s.ownRun=true;s.coverOpen=0;s.statusAt=s.infoAt=62000;
        assertFalse(eggs.observe(s,"status",62000,false));
        eggs.resume(3,62000);s.acceptedCommandSequence=3;s.acceptedCommandStatusSequence=6;s.statusSequence=7;
        assertTrue(eggs.observe(s,"status",62000,false));
    }
    @Test public void nativeBudgetIncludesBoundedPreheat(){
        ControlValues values=ControlValues.eggs(480);
        assertEquals(480,values.seconds);assertEquals(480,values.eggSeconds);
        assertEquals("START_MANUAL 480 100 0 POT_CONFIRMED",values.command("START",true));
        assertEquals(480,values.withTemperature(115).withoutHeatBoost().eggSeconds);
        assertEquals("APPLY 480 100 0",ControlValues.eggsAtBoil(480).command("APPLY",false));
        assertEquals(300,ControlValues.eggsRemaining(480,300).seconds);
    }
}
