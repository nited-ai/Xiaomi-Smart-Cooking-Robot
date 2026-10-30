package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class FunctionProgramTest {
    @Test public void allRunnableProfilesAndPresetsHaveValidValues(){for(String id:com.kitchenos.connect.ui.FunctionSetup.ids()){com.kitchenos.connect.ui.HomeFunctionProfile p=com.kitchenos.connect.ui.HomeFunctionProfile.get(id);if(!p.runnable()||id.equals("manual"))continue;assertEquals(id,"",p.issue(p.seconds,p.temperature,p.speed*(p.reverse?-1:1)));new ControlValues(p.seconds,p.temperature,p.speed*(p.reverse?-1:1),id.equals("lidless"),!id.equals("lidless")&&!id.equals("fry"));for(com.kitchenos.connect.ui.HomeFunctionProfile.Preset preset:p.presets()){assertEquals(id,"",(preset.cleaning==null?p:com.kitchenos.connect.ui.HomeFunctionProfile.cleaning(preset.cleaning)).issue(preset.seconds,preset.temperature,preset.speed*(p.reverse?-1:1)));new ControlValues(preset.seconds,preset.temperature,preset.speed*(p.reverse?-1:1),false,true);}}}
    @Test public void lidlessStillRejectsAllMovement(){com.kitchenos.connect.ui.HomeFunctionProfile p=com.kitchenos.connect.ui.HomeFunctionProfile.get("lidless").lidlessMotion(true);assertEquals(0,p.minSpeed);assertEquals(0,p.maxSpeed);assertEquals("",p.issue(600,100,0));assertFalse(p.issue(600,100,-5).isEmpty());assertFalse(p.issue(600,100,5).isEmpty());}
    @Test public void lidlessStirAllowsOnlyReverseHalfToOne(){com.kitchenos.connect.ui.HomeFunctionProfile p=com.kitchenos.connect.ui.HomeFunctionProfile.get("lidless").lidlessMotion(false);assertEquals("",p.issue(600,100,-5));assertEquals("",p.issue(600,100,-10));assertFalse(p.issue(600,100,-15).isEmpty());assertFalse(p.issue(600,100,5).isEmpty());}
    @Test public void cleanPrefixOccursOnceAndOnlyTailChanges(){for(int seconds:new int[]{90,120,300}){FunctionProgram p=FunctionProgram.create("clean",seconds,45);assertEquals(seconds,p.seconds);assertEquals(14,p.phases.size());assertEquals(seconds-75,p.phases.get(13).seconds);int pulses=0;for(FunctionProgram.Phase phase:p.phases){if(phase.speed==70){assertEquals(0,phase.temperature);pulses++;}}assertEquals(5,pulses);}}
    @Test public void grindTruncatesLastPause(){FunctionProgram p=FunctionProgram.create("grind",20,0);assertEquals(6,p.phases.size());assertEquals(1,p.phases.get(5).seconds);assertTrue(p.phases.get(5).waiting());}
    @Test public void iceHasFivePulses(){FunctionProgram p=FunctionProgram.create("ice",25,0);assertEquals(10,p.phases.size());for(int i=0;i<10;i++){assertEquals(i%2==0?2:3,p.phases.get(i).seconds);assertEquals(i%2==0?60:0,p.phases.get(i).speed);}}
    @Test public void kneadingChangesDirectionOnlyAcrossPause(){FunctionProgram p=FunctionProgram.create("dough",180,0);for(int i=0;i<p.phases.size();i++)assertEquals(new int[]{30,0,-30,0}[i%4],p.phases.get(i).speed);}
    @Test public void kneadingUsesVariablePulsesForBothPresetsAndTruncatesExactly(){
        for(int seconds:new int[]{60,61,120,180,300}){
            FunctionProgram p=FunctionProgram.create("dough",seconds,0);int total=0;
            for(int i=0;i<p.phases.size();i++){
                FunctionProgram.Phase phase=p.phases.get(i);
                assertEquals(Math.min(seconds-total,new int[]{2,2,3,3,4,2}[i%6]),phase.seconds);
                assertEquals(new int[]{30,0,-30,0}[i%4],phase.speed);
                assertEquals(0,phase.temperature);total+=phase.seconds;
            }
            assertEquals(seconds,total);
        }
    }
    @Test public void heatedPauseKeepsHeatButColdPauseIsNoCommand(){assertFalse(FunctionProgram.create("slow",3600,90).phases.get(1).waiting());assertEquals(0,FunctionProgram.create("slow",3600,90).phases.get(1).speed);assertTrue(FunctionProgram.create("stir",60,0).phases.get(1).waiting());}
    @Test public void allPlansStayInsideContract(){for(String id:new String[]{"stir","slow","dough","grind","ice","clean"}){int lo=id.equals("clean")?90:id.equals("slow")?3600:id.equals("dough")?60:id.equals("grind")?4:id.equals("ice")?5:1;int hi=id.equals("slow")?28800:id.equals("stir")?7200:id.equals("grind")||id.equals("ice")?60:300;for(int sec:new int[]{lo,id.equals("slow")?lo+60:lo+1,hi}){FunctionProgram p=FunctionProgram.create(id,sec,id.equals("slow")?90:id.equals("clean")?45:0);assertEquals(sec,p.seconds);for(FunctionProgram.Phase phase:p.phases)if(!phase.waiting())assertTrue(ControlProfile.supportsIn(ControlProfile.MANUAL,phase.seconds,phase.temperature,phase.speed));}}}
    @Test public void slowEightHoursRemainsInBoundedPhases(){FunctionProgram p=FunctionProgram.create("slow",28800,98);assertEquals(1920,p.phases.size());for(int i=0;i<p.phases.size();i++){FunctionProgram.Phase phase=p.phases.get(i);assertEquals(i%2==0?5:25,phase.seconds);assertEquals(i%2==0?-5:0,phase.speed);assertEquals(98,phase.temperature);}}
    @Test public void slowEightHourSequenceCanFinishPastTwoHours(){Rig r=new Rig();r.start("slow",28800,98);for(int i=0;i<1920;i++){r.ack();r.tick(r.starts.get(i).seconds*1000L);r.complete();r.tick(1);}assertFalse(r.runner.active());assertEquals(1920,r.starts.size());assertEquals(0,r.stops);assertEquals("Programm beendet",r.runner.status());}
    @Test(expected=IllegalArgumentException.class) public void tooShortCleanRejected(){FunctionProgram.create("clean",75,45);}
    @Test(expected=IllegalArgumentException.class) public void hotGrindingRejected(){FunctionProgram.create("grind",20,80);}
    static class Rig implements FunctionProgramRunner.Transport {
        final ControlState s=new ControlState();final FunctionProgramRunner runner=new FunctionProgramRunner(s,this);final List<ControlValues> starts=new ArrayList<>();int stops;long now=100;boolean reject;
        Rig(){s.foreground=s.rootVerified=s.connected=s.coverVerified=s.idleConfirmed=true;s.coverOpen=0;s.error=0;s.state=0;s.samples=4;fresh();}
        void fresh(){s.statusAt=s.infoAt=now;}
        void tick(long elapsed){now+=elapsed;fresh();runner.tick(now);}
        public boolean start(ControlValues v){if(reject)return false;assertTrue(s.allows("START",now));starts.add(v);s.submitted("START");s.idleConfirmed=false;s.naturalCompletion=false;return true;}
        public void stop(){stops++;}
        void ack(){s.acceptedCommandSequence=s.commandSequence;s.acceptedCommandStatusSequence=s.statusSequence;s.pendingAction="";s.ownRun=true;s.state=1;s.motorStatus=1;s.remainingSeconds=starts.get(starts.size()-1).seconds;}
        void complete(){s.statusSequence++;s.ownRun=s.stopping=false;s.idleConfirmed=s.naturalCompletion=true;s.state=0;s.motorStatus=s.motorRaw=s.ihStatus=s.ihGear=0;s.remainingSeconds=0;}
        void start(String id,int duration,int temp){assertTrue(runner.start(FunctionProgram.create(id,duration,temp),now));}
    }
    @Test public void timerAloneNeverAdvances(){Rig r=new Rig();r.start("grind",20,0);r.ack();r.tick(6000);assertEquals(1,r.starts.size());assertTrue(r.runner.active());}
    @Test public void coldPauseWaitsAfterConfirmedStop(){Rig r=new Rig();r.start("grind",20,0);r.ack();r.tick(5000);r.complete();r.tick(1);assertEquals(1,r.starts.size());r.tick(1999);assertEquals(1,r.starts.size());r.tick(1);assertEquals(2,r.starts.size());}
    @Test public void stopDuringPausePreventsRestart(){Rig r=new Rig();r.start("ice",25,0);r.ack();r.complete();r.tick(2000);r.runner.cancel("Stop");r.tick(20000);assertEquals(1,r.starts.size());assertFalse(r.runner.active());assertEquals(1,r.stops);}
    @Test public void heatedPauseUsesBoundedZeroMotorCommand(){Rig r=new Rig();r.start("slow",3600,90);r.ack();r.complete();r.tick(5000);assertEquals(2,r.starts.size());ControlValues v=r.starts.get(1);assertEquals(25,v.seconds);assertEquals(0,v.speed);assertEquals(90,v.temperature);}
    @Test public void nonzeroOutputsBlockNextPhase(){Rig r=new Rig();r.start("slow",3600,90);r.ack();r.complete();r.s.motorRaw=1;r.tick(5000);assertEquals(1,r.starts.size());r.s.motorRaw=0;r.tick(1);assertEquals(2,r.starts.size());}
    @Test public void rejectedAckCancelsWithoutRetry(){Rig r=new Rig();r.start("grind",20,0);r.s.pendingAction="";r.tick(1);assertFalse(r.runner.active());r.tick(10000);assertEquals(1,r.starts.size());}
    @Test public void missingAckTimesOut(){Rig r=new Rig();r.start("grind",20,0);r.tick(5001);assertFalse(r.runner.active());assertEquals(1,r.stops);}
    @Test public void missingStopTimesOut(){Rig r=new Rig();r.start("grind",20,0);r.ack();r.tick(17001);assertFalse(r.runner.active());assertEquals(1,r.stops);}
    @Test public void staleDataAbortsAndNeverResumes(){Rig r=new Rig();r.start("grind",20,0);r.ack();r.runner.tick(r.now+1201);assertFalse(r.runner.active());r.tick(1000);assertEquals(1,r.starts.size());}
    @Test public void recordedKneadStartupGapDoesNotStopAcceptedPhase(){
        Rig r=new Rig();r.start("dough",180,0);r.ack();
        r.runner.tick(r.now+556);assertTrue(r.runner.active());assertEquals(0,r.stops);assertEquals(1,r.starts.size());
        r.tick(700);assertTrue(r.runner.active());assertEquals(1,r.starts.size());
    }
    @Test public void runningWatchdogStillBoundsStatusAndInfoIndependently(){
        for(boolean missingStatus:new boolean[]{true,false}){
            Rig r=new Rig();r.start("dough",180,0);r.ack();r.now+=missingStatus?1501:1201;
            if(missingStatus)r.s.infoAt=r.now;else r.s.statusAt=r.now;
            r.runner.tick(r.now);assertFalse(r.runner.active());assertEquals(1,r.stops);
            r.tick(1);assertEquals(1,r.starts.size());
        }
    }
    @Test public void expiredPauseWaitsForFreshDataBeforeNextStart(){
        Rig r=new Rig();r.start("dough",60,0);r.ack();r.complete();r.tick(2000);
        r.tick(1500);r.now+=500;r.runner.tick(r.now);
        assertTrue(r.runner.active());assertEquals(1,r.starts.size());assertEquals(0,r.stops);
        r.tick(1);assertEquals(2,r.starts.size());assertEquals(-30,r.starts.get(1).speed);
    }
    @Test public void newProgramStillRequiresStrictStartFreshness(){
        Rig r=new Rig();r.now+=500;
        assertFalse(r.runner.start(FunctionProgram.create("dough",120,0),r.now));assertTrue(r.starts.isEmpty());
    }
    @Test public void stopFaultLidAndBackgroundAbort(){for(int kind=0;kind<5;kind++){Rig r=new Rig();r.start("grind",20,0);r.ack();if(kind==0)r.s.stopping=true;if(kind==1)r.s.error=2;if(kind==2)r.s.coverOpen=1;if(kind==3)r.s.foreground=false;if(kind==4)r.s.connected=false;r.tick(1);assertFalse(r.runner.active());assertEquals(1,r.stops);}}
    @Test public void fullCleanSequenceCompletesOnce(){Rig r=new Rig();r.start("clean",120,45);for(int i=0;i<14;i++){r.ack();r.tick(r.starts.get(i).seconds*1000L);r.complete();r.tick(1);}assertFalse(r.runner.active());assertEquals(14,r.starts.size());r.tick(10000);assertEquals(14,r.starts.size());assertEquals("Programm beendet",r.runner.status());}
    @Test public void directionSwitchFollowsConfirmedPause(){Rig r=new Rig();r.start("dough",60,0);r.ack();r.complete();r.tick(2000);r.tick(1999);assertEquals(1,r.starts.size());r.tick(1);assertEquals(-30,r.starts.get(1).speed);}
    @Test public void phaseRejectionAborts(){Rig r=new Rig();r.start("clean",120,45);r.ack();r.complete();r.reject=true;r.tick(4000);assertFalse(r.runner.active());assertEquals(1,r.starts.size());}
}
