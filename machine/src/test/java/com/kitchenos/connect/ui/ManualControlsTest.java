package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualControlsTest {
    @Test public void manualValuesReachRequestedLimitsWithoutChangingRecipeProfiles(){
        for(int seconds:new int[]{1,240,3600,7200})for(int heat:new int[]{0,45,100,150})for(int speed:new int[]{-40,-30,-5,5,30,40})
            assertEquals("START_MANUAL "+seconds+" "+heat+" "+speed+" POT_CONFIRMED",new ControlValues(seconds,heat,speed,false,true).command("START",true));
        assertTrue(ControlProfile.supportsManual(7200,150,0));
        assertTrue(ControlProfile.supportsManual(7200,0,100));
        for(int speed=-100;speed<=100;speed+=5)if(speed!=0)assertTrue(ControlProfile.supportsManual(7200,0,speed));
        assertTrue(ControlProfile.supportsManual(7200,37,-35));
        assertTrue(ControlProfile.supportsManual(60,37,-45));
        assertFalse(ControlProfile.supports(7200,150,-40));
        assertEquals(0,ControlProfile.profile(7200,150,-40));
    }
    @Test public void invalidCombinationsAndUnconfirmedStartsStayBlocked(){
        for(int[] tuple:new int[][]{{7201,45,0},{60,151,10},{60,34,10},{60,100,-65},{60,0,-33},{60,100,100},{60,0,0}}){
            try{new ControlValues(tuple[0],tuple[1],tuple[2],false,true);fail("Unsupported manual tuple");}catch(IllegalArgumentException expected){}
        }
        try{new ControlValues(60,45,0,false,true).command("START",false);fail("Pot confirmation required");}catch(IllegalArgumentException expected){}
        try{new ControlValues(60,45,0,true,true);fail("Manual does not weaken the lidless mode");}catch(IllegalArgumentException expected){}
    }
    @Test public void thirtyFiveDegreesAreNotConvertedToFortyFiveInStartOrLiveChanges(){
        ControlValues values=new ControlValues(60,35,15,false,true);
        assertEquals("START_MANUAL 60 35 15 POT_CONFIRMED",values.command("START",true));
        assertEquals("APPLY_SETTINGS 35 15",values.command("APPLY_SETTINGS",false));
    }
    @Test public void manualRunBudgetCannotBeRenewedByChangingTime(){
        RunBudget budget=new RunBudget();budget.begin(1000,ControlProfile.MANUAL);
        assertTrue(budget.permits(7200,1000));assertFalse(budget.permits(7200,2000));
        assertTrue(budget.expired(7204000));
    }
    @Test public void postStartPotErrorRemainsDistinctAfterTheMcuReturnsToIdle(){
        ControlState state=new ControlState();state.foreground=state.connected=state.coverVerified=true;
        state.samples=5;state.statusAt=state.infoAt=1000;state.coverOpen=1;state.error=0;
        state.deviceFaultLatched=true;state.lastDeviceError=2;
        assertEquals(HomeVesselState.Lid.POT_MISSING,HomeVesselState.from(state,1100).lid);
        assertEquals(HomeVesselState.Lid.POT_MISSING,HomeVesselState.from(state,1500).lid);
        state.deviceFaultLatched=false;
        assertEquals(HomeVesselState.Lid.OPEN,HomeVesselState.from(state,1100).lid);
        assertEquals(HomeVesselState.Lid.UNKNOWN,HomeVesselState.from(state,1500).lid);
    }
}
