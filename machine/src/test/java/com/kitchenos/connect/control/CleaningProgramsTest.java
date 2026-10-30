package com.kitchenos.connect.control;

import com.kitchenos.connect.ui.HomeFunctionProfile;
import org.junit.Test;
import static org.junit.Assert.*;

public class CleaningProgramsTest {
 @Test public void recordedHeatedPhaseDeadlineContinuesOnlyAfterConfirmedZeroOutputs(){
  for(CleaningProgram c:CleaningProgram.values()){
   if(c==CleaningProgram.QUICK)continue; // The timed curve has its own full replay.
   FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();assertTrue(r.runner.start(FunctionProgram.cleaning(c),r.now));r.ack();
   r.tick(11000);r.s.stopReason="Eingestellte Laufzeit erreicht";r.s.stopping=true;r.tick(1);
   assertTrue("Native bounded phase end is not a cancelled program: "+c,r.runner.active());assertEquals(1,r.starts.size());
   r.s.ownRun=r.s.stopping=false;r.s.idleConfirmed=true;r.s.state=0;r.s.statusSequence++;r.s.motorStatus=0;r.s.motorRaw=90;r.tick(1);
   assertEquals(1,r.starts.size());r.s.motorRaw=0;r.s.ihGear=120;r.tick(1);assertEquals(1,r.starts.size());
   r.s.ihGear=0;r.tick(1);assertTrue(r.runner.active());
   assertTrue(r.runner.status().contains("einweichen"));assertEquals(c.temperature==0?1:2,r.starts.size());
  }
 }
 @Test public void deadlineCannotExcuseEarlyUnacknowledgedOrProtectiveStops(){
  for(String reason:new String[]{"Stopp angefordert","Statusantwort fehlt","Infoantwort fehlt","Sitzungsbudget erreicht","Schutzstopp: Temperatur",""}){
   FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();assertTrue(r.runner.start(FunctionProgram.cleaning(CleaningProgram.FAT),r.now));r.ack();r.tick(11000);
   r.s.stopReason=reason;r.s.stopping=true;r.tick(1);assertFalse(reason,r.runner.active());assertEquals(1,r.starts.size());
  }
  for(boolean acknowledged:new boolean[]{true,false}){
   FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();assertTrue(r.runner.start(FunctionProgram.cleaning(CleaningProgram.FAT),r.now));if(acknowledged)r.ack();
   r.s.stopReason="Eingestellte Laufzeit erreicht";r.s.stopping=true;r.tick(acknowledged?7999:11000);assertFalse(r.runner.active());assertEquals(1,r.starts.size());
  }
 }
 @Test public void manualEnvelopeAllowsHeatedTenThroughEightyAndSixAbove(){
  for(int temp:new int[]{0,35,55,60,79,80,81,95,100,150})for(int speed=-100;speed<=100;speed+=5){
   boolean expected=(temp>0||speed!=0)&&(temp<=80||Math.abs(speed)<=60);
   assertEquals(temp+" / "+speed,expected,ControlProfile.supportsManual(300,temp,speed));
  }
  assertFalse(ControlProfile.supportsManual(300,151,10));
  assertFalse(ControlProfile.supportsManual(300,80,105));
  assertFalse(ControlProfile.supportsIn(ControlProfile.MOTOR,300,55,100));
 }
 @Test public void fiveDistinctPlansAreFiniteAndHaveThePromisedPeaks(){
  assertEquals(5,CleaningProgram.values().length);
  for(CleaningProgram c:CleaningProgram.values()){
   FunctionProgram p=FunctionProgram.cleaning(c);int total=0,peak=0,heat=0;boolean reverse=false;
   assertEquals(c==CleaningProgram.QUICK?10:20,p.phases.get(0).speed);assertEquals(c==CleaningProgram.QUICK?10:8,p.phases.get(0).seconds);
   for(FunctionProgram.Phase phase:p.phases){
    assertTrue(phase.seconds>0);total+=phase.seconds;peak=Math.max(peak,Math.abs(phase.speed));heat=Math.max(heat,phase.temperature);reverse|=phase.speed<0;
    if(!phase.waiting())assertTrue(ControlProfile.supportsManual(phase.seconds,phase.temperature,phase.speed));
    if(Math.abs(phase.speed)>60)assertEquals(c.temperature,phase.temperature);
    for(FunctionProgram.SpeedChange change:phase.changes)peak=Math.max(peak,Math.abs(change.speed));
   }
   assertEquals(c.seconds,total);assertEquals(total,p.seconds);assertEquals(c.maxSpeed,peak);assertEquals(c.temperature,heat);assertTrue(reverse);
   HomeFunctionProfile home=HomeFunctionProfile.cleaning(c);assertEquals(c.seconds,home.minSeconds());assertEquals(c.seconds,home.maxSeconds);
   assertEquals("",home.issue(c.seconds,c.temperature,30));assertFalse(home.issue(c.seconds+1,c.temperature,30).isEmpty());
  }
 }
 @Test public void staticPlansFinishExactlyOnceWithTheRealRunner(){
  for(CleaningProgram c:CleaningProgram.values()){
   if(c==CleaningProgram.QUICK)continue;
   FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();FunctionProgram p=FunctionProgram.cleaning(c);
   assertTrue(r.runner.start(p,r.now));int sent=0;
   for(FunctionProgram.Phase phase:p.phases){
    if(phase.waiting()){r.tick(phase.seconds*1000L-1);assertTrue(r.runner.active());r.tick(1);}
    else{
     ControlValues v=r.starts.get(sent++);assertEquals(phase.speed,v.speed);assertEquals(phase.temperature,v.temperature);assertEquals(phase.seconds,v.seconds);
     r.ack();r.tick(phase.seconds*1000L);r.complete();r.s.motorRaw=1;r.tick(1);assertEquals(sent,r.starts.size());r.s.motorRaw=0;r.tick(1);
    }
   }
   assertEquals(sent,r.starts.size());assertEquals("Programm beendet",r.runner.status());assertFalse(r.runner.active());r.tick(10000);assertEquals(sent,r.starts.size());
  }
 }
 @Test public void stopAtEveryPhasePreventsFurtherCleaning(){
  for(CleaningProgram c:CleaningProgram.values()){
   if(c==CleaningProgram.QUICK)continue;
   FunctionProgram p=FunctionProgram.cleaning(c);
   for(int cancelAt=0;cancelAt<p.phases.size();cancelAt++){
    FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();assertTrue(r.runner.start(p,r.now));
    for(int i=0;i<cancelAt;i++){FunctionProgram.Phase phase=p.phases.get(i);if(phase.waiting())r.tick(phase.seconds*1000L);else{r.ack();r.tick(phase.seconds*1000L);r.complete();r.tick(1);}}
    int starts=r.starts.size();r.runner.cancel("Stoppen");r.tick(100000);assertFalse(r.runner.active());assertEquals(starts,r.starts.size());assertEquals(1,r.stops);
   }
  }
 }
 @Test public void startupGapsAndLostTelemetryRetainTheirDifferentMeaning(){
  for(CleaningProgram c:CleaningProgram.values()){
   FunctionProgramTest.Rig r=new FunctionProgramTest.Rig();assertTrue(r.runner.start(FunctionProgram.cleaning(c),r.now));r.ack();
   r.runner.tick(r.now+691);assertTrue(r.runner.active());assertEquals(0,r.stops);
   r.runner.tick(r.now+1201);assertFalse(r.runner.active());assertEquals(1,r.stops);r.tick(2000);assertEquals(1,r.starts.size());
  }
 }
 @Test public void shorterBudgetsKeepStrongPassesAndTemperatureTargets(){
  int[] budgets={21,135,225,360,315},oldBudgets={21,180,300,480,420},temperatures={45,0,55,85,95};
  for(CleaningProgram c:CleaningProgram.values()){
   int i=c.ordinal();FunctionProgram plan=FunctionProgram.cleaning(c);assertEquals(budgets[i],plan.seconds);assertEquals(temperatures[i],c.temperature);
   if(c==CleaningProgram.QUICK)continue;assertEquals(oldBudgets[i]*3/4,plan.seconds);
   int forward=0,backward=0,peakSeconds=0;for(FunctionProgram.Phase p:plan.phases){if(p.name.equals("Spülen vorwärts")){assertEquals(8,p.seconds);forward++;}if(p.name.equals("Spülen rückwärts")){assertEquals(8,p.seconds);backward++;}if(Math.abs(p.speed)==c.maxSpeed)peakSeconds+=p.seconds;}
   assertEquals(c==CleaningProgram.DOUGH?3:4,forward);assertEquals(forward,backward);assertEquals(13,peakSeconds);
  }
  assertTrue(CleaningProgram.DOUGH.information().contains("2 Min. 15 Sek."));assertTrue(CleaningProgram.BASIC.information().contains("3 Min. 45 Sek."));
 }
}
