package com.kitchenos.connect.control;
import org.junit.Test;
import static org.junit.Assert.*;
import com.kitchenos.connect.ui.HomeFunctionProfile;

public class FryingProgramTest {
    @Test public void allRhythmsUseFryContractAndExactFiniteBudget(){
        for(FryingRhythm rhythm:FryingRhythm.values())for(int t:new int[]{105,140,150,180})for(int seconds:new int[]{1,3,4,13,14,30,1800}){
            FunctionProgram p=FunctionProgram.frying(seconds,t,rhythm);int total=0;
            for(int i=0;i<p.phases.size();i++){
                FunctionProgram.Phase phase=p.phases.get(i);assertTrue(phase.seconds>0);total+=phase.seconds;
                if(i%2==0){assertEquals(-10,phase.speed);assertEquals(t,phase.temperature);assertFalse(phase.values().manual);assertTrue(ControlProfile.supportsIn(ControlProfile.FRY,phase.seconds,t,-10));assertTrue(phase.values().command("START",true).startsWith("START "));}
                else{assertEquals(0,phase.temperature);assertEquals(0,phase.speed);assertTrue(phase.waiting());assertTrue(phase.seconds<=rhythm.pauseSeconds);}
            }assertEquals(seconds,total);assertEquals(seconds,p.seconds);
        }
    }
    @Test public void profileRejectsForwardAndOffGridHeat(){HomeFunctionProfile p=HomeFunctionProfile.get("fry");assertTrue(p.automatic());assertTrue(p.timeControl());assertTrue(p.temperatureControl());assertFalse(p.speedControl());assertEquals(3,p.presets().length);for(int s:new int[]{0,10,-5,-20})assertFalse(p.issue(300,140,s).isEmpty());assertFalse(p.issue(300,141,-10).isEmpty());assertEquals("",p.issue(300,180,-10));}
    @Test(expected=IllegalArgumentException.class) public void tooLongIsRejected(){FunctionProgram.frying(1801,180,FryingRhythm.SHORT);}
    @Test(expected=IllegalArgumentException.class) public void tooHotIsRejected(){FunctionProgram.frying(300,185,FryingRhythm.SHORT);}
}
