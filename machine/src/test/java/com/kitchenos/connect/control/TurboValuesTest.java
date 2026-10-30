package com.kitchenos.connect.control;

import com.kitchenos.connect.ui.HomeFunctionProfile;
import org.junit.Test;
import static org.junit.Assert.*;

public class TurboValuesTest {
    private void rejects(Runnable call){try{call.run();fail("Unsupported turbo request accepted");}catch(IllegalArgumentException expected){}}

    @Test public void turboRequiresExplicitDedicatedCommandAndPotConfirmation(){
        for(int seconds:new int[]{1,2})for(int speed:new int[]{100,110,120}){
            ControlValues v=ControlValues.turbo(seconds,speed);
            assertTrue(v.turbo);assertFalse(v.manual);assertFalse(v.lidless);
            assertEquals("START_TURBO "+seconds+" 0 "+speed+" POT_CONFIRMED",v.command("START",true));
            rejects(()->v.command("START",false));
            for(String action:new String[]{"APPLY","APPLY_SETTINGS","RESUME","PAUSE"})rejects(()->v.command(action,true));
        }
    }
    @Test public void noUnboundedDurationOrUnreviewedStage(){
        for(int seconds:new int[]{Integer.MIN_VALUE,-1,0,3,60,Integer.MAX_VALUE})rejects(()->ControlValues.turbo(seconds,120));
        for(int speed:new int[]{Integer.MIN_VALUE,-120,0,90,105,115,125,130,200,Integer.MAX_VALUE})rejects(()->ControlValues.turbo(2,speed));
    }
    @Test public void turboDoesNotExpandManualOrRecipeCapabilities(){
        for(int speed:new int[]{110,120,130}){
            assertFalse(ControlProfile.supportsManual(2,0,speed));
            assertFalse(ControlProfile.supportsLocal(2,0,speed));
            assertFalse(ControlProfile.supports(2,0,speed));
            rejects(()->new ControlValues(2,0,speed));
            rejects(()->new ControlValues(2,0,speed,false,true));
        }
    }
    @Test public void functionContractRejectsHeatReverseAndFractionalStages(){
        HomeFunctionProfile profile=HomeFunctionProfile.get("turbo");
        assertEquals(2,profile.maxSeconds);assertEquals(100,profile.minSpeed);assertEquals(120,profile.maxSpeed);
        assertEquals(0,profile.presets().length);
        for(int speed:new int[]{100,110,120})assertEquals("",profile.issue(2,0,speed));
        for(int[] invalid:new int[][]{{3,0,120},{2,35,120},{2,0,-120},{2,0,105},{2,0,130}})
            assertFalse(profile.issue(invalid[0],invalid[1],invalid[2]).isEmpty());
    }
}
