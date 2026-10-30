package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;
import com.kitchenos.connect.ui.HomeFunctionProfile;

public class PureeProgramTest {
 @Test public void everyDurationAndTargetIsBoundedMonotonicAndUnheated(){
  for(int seconds=1;seconds<=300;seconds++)for(int target=60;target<=100;target+=10){
   FunctionProgram p=FunctionProgram.puree(seconds,target);assertEquals(seconds,p.seconds);assertEquals(1,p.phases.size());FunctionProgram.Phase phase=p.phases.get(0);assertEquals(10,phase.speed);assertEquals(0,phase.temperature);
   int stage=10,second=0;for(FunctionProgram.SpeedChange point:phase.changes){assertEquals(++second,point.second);assertEquals(stage+10,point.speed);assertTrue(point.second<seconds);assertTrue(point.speed<=target);stage=point.speed;}
   assertEquals(Math.min(seconds*10,target),stage);
  }
 }
 @Test public void invalidTimeAndStagesAreRejected(){for(int[] bad:new int[][]{{0,60},{301,100},{30,50},{30,110},{30,-60},{30,65}})try{FunctionProgram.puree(bad[0],bad[1]);fail();}catch(IllegalArgumentException expected){}}
 @Test public void completeRunReachesAllTargetsWithoutRestart(){for(int target=60;target<=100;target+=10){QuickCleaningTest.Rig r=new QuickCleaningTest.Rig();FunctionProgram p=FunctionProgram.puree(30,target);assertTrue(r.runner.start(p,r.now));r.ack();r.sample(1);r.curve(p.phases.get(0));r.boundedStop();assertFalse(r.runner.active());assertEquals(1,r.starts.size());assertEquals(target/10-1,r.updates.size());for(ControlValues v:r.updates)assertEquals("APPLY_SETTINGS 0 "+v.speed,v.command("APPLY_SETTINGS",true));}}
 @Test public void stopAtEveryStageCannotRestart(){for(int stop=0;stop<=9;stop++){QuickCleaningTest.Rig r=new QuickCleaningTest.Rig();assertTrue(r.runner.start(FunctionProgram.puree(30,100),r.now));r.ack();r.sample(1);for(int i=0;i<stop;i++){r.sample(1000);r.reply();}r.runner.cancel("Stoppen");int changes=r.updates.size();r.complete();r.sample(10000);assertFalse(r.runner.active());assertEquals(changes,r.updates.size());assertEquals(1,r.starts.size());}}
 @Test public void lateRejectedOrMissingUpdatesCannotCatchUp(){for(int fault=0;fault<4;fault++){QuickCleaningTest.Rig r=new QuickCleaningTest.Rig();assertTrue(r.runner.start(FunctionProgram.puree(30,100),r.now));r.ack();r.sample(1);if(fault==0)r.rejectUpdate=true;r.sample(fault==3?1800:1000);if(fault==1){r.s.pendingAction="";r.sample(1);}if(fault==2)r.sample(1201);assertFalse(r.runner.active());int changes=r.updates.size();r.sample(20000);assertEquals(changes,r.updates.size());assertEquals(1,r.starts.size());}}
 @Test public void allSafetyInterruptionsCancelRamp(){for(int fault=0;fault<7;fault++){QuickCleaningTest.Rig r=new QuickCleaningTest.Rig();assertTrue(r.runner.start(FunctionProgram.puree(30,100),r.now));r.ack();r.sample(1);if(fault==0)r.s.coverOpen=1;if(fault==1)r.s.error=2;if(fault==2)r.s.foreground=false;if(fault==3)r.s.connected=false;if(fault==4)r.s.uncertainRun=true;if(fault==5)r.s.potCheck.require();if(fault==6){r.now+=1501;}r.runner.tick(r.now);assertFalse(r.runner.active());assertEquals(0,r.updates.size());}}
 @Test public void profileKeepsEditableTargetAndValidPresets(){HomeFunctionProfile p=HomeFunctionProfile.get("puree");assertTrue(p.automatic());assertFalse(p.intervals);assertEquals(30,p.seconds);assertEquals(60,p.speed);assertEquals(300,p.maxSeconds);for(HomeFunctionProfile.Preset preset:p.presets()){assertEquals("",p.issue(preset.seconds,0,preset.speed));assertEquals(10,p.program(preset.seconds,0,preset.speed).phases.get(0).speed);}assertFalse(p.issue(30,0,65).isEmpty());}
}
