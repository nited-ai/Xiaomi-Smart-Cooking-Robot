package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeatingBoostTest {
    private ControlState running(){
        ControlState s=new ControlState();
        s.connected=s.foreground=s.rootVerified=s.ownRun=s.coverVerified=true;
        s.samples=5;s.state=1;s.error=0;s.coverOpen=0;s.statusAt=s.infoAt=1000;
        s.statusSequence=6;s.acceptedCommandStatusSequence=5;s.acceptedCommandSequence=2;
        s.temperature=100;
        return s;
    }

    @Test public void temporarySetpointStaysWithinProfile(){
        assertEquals(115,HeatingBoost.target(new ControlValues(480,100,0,false,true).boostHeating(),ControlProfile.MANUAL,30));
        assertEquals(104,HeatingBoost.target(new ControlValues(480,90,0,false,true).boostHeating(),ControlProfile.MANUAL,30));
        assertEquals(150,HeatingBoost.target(new ControlValues(480,145,0,false,true).boostHeating(),ControlProfile.MANUAL,30));
        assertEquals(100,HeatingBoost.target(new ControlValues(480,100,0).boostHeating(),ControlProfile.NORMAL,30));
        assertEquals(0,HeatingBoost.target(new ControlValues(480,0,5),ControlProfile.MOTOR,30));
        assertEquals(100,HeatingBoost.target(new ControlValues(480,100,0,false,true).boostHeating(),ControlProfile.MANUAL,100));
    }

    @Test public void deviceReceivesBoostButTargetRemainsNominal(){
        ControlValues water=ControlValues.water(100);
        int device=HeatingBoost.target(water,ControlProfile.MANUAL,28);
        assertEquals(100,water.temperature);
        assertEquals("START_MANUAL 600 115 0 POT_CONFIRMED",water.withTemperature(device).command("START",true));
        ControlValues egg=new ControlValues(480,100,0,false,true).boostHeating();
        assertEquals("START_MANUAL 480 115 0 POT_CONFIRMED",egg.withTemperature(HeatingBoost.target(egg,ControlProfile.MANUAL,28)).command("START",true));
        assertEquals("APPLY_SETTINGS 100 0",egg.withoutHeatBoost().command("APPLY_SETTINGS",false));
    }

    @Test public void loweringNeedsFreshPostAckTargetSample(){
        HeatingBoost boost=new HeatingBoost();ControlValues egg=new ControlValues(480,100,0,false,true).boostHeating();boost.sent(egg,115,2);
        ControlState s=running();assertFalse(boost.shouldLower(s,"info",1000));
        s.temperature=99;assertFalse(boost.shouldLower(s,"status",1000));s.temperature=100;
        s.acceptedCommandSequence=1;assertFalse(boost.shouldLower(s,"status",1000));s.acceptedCommandSequence=2;
        s.statusSequence=5;assertFalse(boost.shouldLower(s,"status",1000));s.statusSequence=6;
        assertTrue(boost.shouldLower(s,"status",1000));boost.lowering(egg.withoutHeatBoost());assertFalse(boost.shouldLower(s,"status",1000));
        boost.lowered();assertFalse(boost.shouldLower(s,"status",1000));s.temperature=103;assertFalse(boost.tooHot(s,"status",1000));
        boost.cancel();assertFalse(boost.shouldLower(s,"status",1000));
    }

    @Test public void unsafeOrWaterRunsNeverLower(){
        HeatingBoost boost=new HeatingBoost();boost.sent(ControlValues.water(100),115,2);assertFalse(boost.shouldLower(running(),"status",1000));
        boost.sent(new ControlValues(480,100,0,false,true).boostHeating(),115,2);
        ControlState s=running();s.coverOpen=1;assertFalse(boost.shouldLower(s,"status",1000));
        s.coverOpen=0;s.statusAt=400;assertFalse(boost.shouldLower(s,"status",1000));
        s.statusAt=1000;s.pendingAction="START";assertFalse(boost.shouldLower(s,"status",1000));
    }

    @Test public void alreadyReachedTargetDoesNotLowerAgainButKeepsProtection(){
        HeatingBoost boost=new HeatingBoost();ControlValues warm=new ControlValues(7200,90,-5,false,true).boostHeating();
        assertEquals(90,HeatingBoost.target(warm,ControlProfile.MANUAL,90));
        boost.sent(warm,90,2);
        ControlState s=running();s.temperature=96;
        assertFalse(boost.shouldLower(s,"status",1000));
        assertFalse(boost.tooHot(s,"status",1000));
        for(long now=2000;now<=31000;now+=1000){s.statusAt=s.infoAt=now;assertFalse(boost.tooHot(s,"status",now));}
        s.statusAt=s.infoAt=31001;assertTrue(boost.tooHot(s,"status",31001));
    }

    @Test public void loweringCommandDoesNotRestartContinuousOverheatWindow(){
        HeatingBoost boost=new HeatingBoost();ControlValues warm=new ControlValues(7200,90,-5,false,true).boostHeating();
        boost.sent(warm,104,2);
        ControlState s=running();s.temperature=96;
        for(long now=1000;now<=16000;now+=1000){s.statusAt=s.infoAt=now;assertFalse(boost.tooHot(s,"status",now));}
        ControlValues normal=warm.withoutHeatBoost();boost.sent(normal,90,3);boost.lowering(normal);boost.lowered();
        for(long now=17000;now<=31000;now+=1000){s.statusAt=s.infoAt=now;assertFalse(boost.tooHot(s,"status",now));}
        s.statusAt=s.infoAt=31001;assertTrue(boost.tooHot(s,"status",31001));
    }

    @Test public void protectionNeedsMoreThanThirtyContinuousSecondsAboveFiveDegrees(){
        HeatingBoost boost=new HeatingBoost();ControlValues egg=new ControlValues(480,100,0,false,true).boostHeating();boost.sent(egg,115,2);
        ControlState s=running();s.temperature=105;
        assertFalse(boost.tooHot(s,"status",1000));
        s.temperature=106;s.statusAt=s.infoAt=2000;
        assertFalse(boost.tooHot(s,"status",2000));
        for(long now=3000;now<=32000;now+=1000){s.statusAt=s.infoAt=now;assertFalse(boost.tooHot(s,"status",now));}
        s.statusAt=s.infoAt=32001;assertTrue(boost.tooHot(s,"status",32001));
        s.temperature=105;s.statusAt=s.infoAt=33000;
        assertFalse(boost.tooHot(s,"status",33000));
        s.temperature=106;s.statusAt=s.infoAt=34000;
        assertFalse(boost.tooHot(s,"status",34000));
        s.statusAt=s.infoAt=65001;assertFalse("A data gap cannot prove continuous overheating",boost.tooHot(s,"status",65001));
        for(long now=66001;now<=95001;now+=1000){s.statusAt=s.infoAt=now;assertFalse(boost.tooHot(s,"status",now));}
        s.statusAt=s.infoAt=95002;assertTrue(boost.tooHot(s,"status",95002));
        boost.cancel();assertFalse(boost.tooHot(s,"status",65002));
    }
}
