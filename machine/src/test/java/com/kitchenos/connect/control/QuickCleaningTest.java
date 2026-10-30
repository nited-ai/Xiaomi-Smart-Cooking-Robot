package com.kitchenos.connect.control;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class QuickCleaningTest {
 static class Rig extends FunctionProgramTest.Rig {
  final List<ControlValues> updates=new ArrayList<>();boolean rejectUpdate;
  public boolean adjust(ControlValues values){if(rejectUpdate)return false;assertTrue(s.allows("APPLY_SETTINGS",now));updates.add(values);s.submitted("APPLY_SETTINGS");return true;}
  void reply(){s.acceptedCommandSequence=s.commandSequence;s.acceptedCommandStatusSequence=s.statusSequence;s.pendingAction="";s.statusSequence++;}
  void sample(long millis){now+=millis;s.statusSequence++;fresh();runner.tick(now);}
  void begin(){assertTrue(runner.start(FunctionProgram.cleaning(CleaningProgram.QUICK),now));ack();sample(1);}
  void curve(FunctionProgram.Phase phase){
   long base=now;
   for(FunctionProgram.SpeedChange step:phase.changes){sample(base+step.second*1000L-now);assertTrue(runner.active());assertEquals(step.speed,updates.get(updates.size()-1).speed);assertEquals(step.speed,runner.speed());reply();}
   sample(base+phase.seconds*1000L-now);
  }
  void boundedStop(){s.stopping=true;s.stopReason="Eingestellte Laufzeit erreicht";sample(1);assertTrue(runner.active());s.ownRun=s.stopping=false;s.idleConfirmed=true;s.state=0;s.motorStatus=0;s.motorRaw=1;sample(1);assertTrue(runner.active());s.motorRaw=0;sample(1);}
 }
 @Test public void ascendingHoldsFitThirtySecondsIncludingNativeReserves(){
  FunctionProgram p=FunctionProgram.cleaning(CleaningProgram.QUICK);assertEquals(21,p.seconds);assertEquals(3,p.phases.size());
  assertEquals(30,p.seconds+3*p.phases.size());
  int[] speeds={20,30,60,100,10};int[] seconds={1,3,5,7,9};
  for(int i=0;i<2;i++){FunctionProgram.Phase phase=p.phases.get(i);int sign=i==0?1:-1;assertEquals(10,phase.seconds);assertEquals(sign*10,phase.speed);assertEquals(5,phase.changes.size());
   for(int j=0;j<speeds.length;j++){assertEquals(seconds[j],phase.changes.get(j).second);assertEquals(sign*speeds[j],phase.changes.get(j).speed);}
  }
  assertEquals(50,p.phases.get(2).speed);assertEquals(1,p.phases.get(2).seconds);assertTrue(p.phases.get(2).changes.isEmpty());
  assertTrue(CleaningProgram.QUICK.information().contains("21 Sekunden"));assertTrue(CleaningProgram.QUICK.tip.contains("500 ml"));assertFalse(CleaningProgram.QUICK.information().contains("0 Minuten"));
 }
 @Test public void completeCurveUsesThreeStartsAndSettingsOnlyWithoutResettingTime(){
  Rig r=new Rig();r.begin();FunctionProgram p=FunctionProgram.cleaning(CleaningProgram.QUICK);int i=0;
  for(FunctionProgram.Phase phase:p.phases){if(i>0){r.ack();r.sample(1);}int starts=r.starts.size();r.curve(phase);assertEquals(starts,r.starts.size());r.boundedStop();i++;}
  assertFalse(r.runner.active());assertEquals("Programm beendet",r.runner.status());assertEquals(3,r.starts.size());assertEquals(10,r.updates.size());assertEquals(0,r.stops);
  for(ControlValues update:r.updates)assertEquals("APPLY_SETTINGS 45 "+update.speed,update.command("APPLY_SETTINGS",true));
  r.sample(10000);assertEquals(3,r.starts.size());
 }
 @Test public void everyUpdateCanBeStoppedWithoutLaterMotion(){
  for(int stopAt=0;stopAt<10;stopAt++){
   Rig r=new Rig();r.begin();int seen=0;boolean cancelled=false;
   for(FunctionProgram.Phase phase:FunctionProgram.cleaning(CleaningProgram.QUICK).phases){
    if(r.starts.size()>1){r.ack();r.sample(1);}long base=r.now;
    for(FunctionProgram.SpeedChange step:phase.changes){r.sample(base+step.second*1000L-r.now);if(seen++==stopAt){int count=r.updates.size(),starts=r.starts.size();r.runner.cancel("Stoppen");r.reply();r.complete();r.sample(10000);assertFalse(r.runner.active());assertEquals(count,r.updates.size());assertEquals(starts,r.starts.size());cancelled=true;break;}r.reply();}
    if(cancelled)break;r.sample(base+phase.seconds*1000L-r.now);r.boundedStop();
   }
   assertTrue(cancelled);
  }
 }
 @Test public void rejectedMissingAndLateUpdatesAbortWithoutCatchup(){
  for(int mode=0;mode<4;mode++){
   Rig r=new Rig();r.begin();if(mode==0)r.rejectUpdate=true;r.sample(mode==3?1800:1000);
   if(mode==1){r.s.pendingAction="";r.sample(1);}if(mode==2)r.sample(1201);
   assertFalse("mode "+mode,r.runner.active());int count=r.updates.size();r.sample(10000);assertEquals(count,r.updates.size());assertEquals(1,r.starts.size());
  }
 }
 @Test public void allInterlocksStillAbortWhileChangingSpeed(){
  for(int fault=0;fault<7;fault++){Rig r=new Rig();r.begin();r.sample(1000);r.reply();
   if(fault==0)r.s.coverOpen=1;if(fault==1)r.s.error=2;if(fault==2)r.s.foreground=false;if(fault==3)r.s.connected=false;if(fault==4)r.s.uncertainRun=true;if(fault==5)r.s.potCheck.require();if(fault==6){r.s.stopping=true;r.s.stopReason="Sitzungsbudget erreicht";}
   r.sample(1);assertFalse(r.runner.active());assertEquals(1,r.starts.size());assertEquals(1,r.updates.size());}
 }
 @Test public void firstCommandStillRequiresFreshStartAndChangesCannotReverse(){
  Rig r=new Rig();r.now+=500;assertFalse(r.runner.start(FunctionProgram.cleaning(CleaningProgram.QUICK),r.now));
  try{new FunctionProgram.Phase("bad",5,45,10,new int[][]{{1,-10}});fail();}catch(IllegalArgumentException expected){}
 }
}
