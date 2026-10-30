package com.kitchenos.connect.control;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class FryMotorContinuityTest {
    @Test public void oneBoundedRunPreservesHeatForEveryRhythm(){
        for(FryingRhythm rhythm:FryingRhythm.values())for(int t:new int[]{105,140,180})for(int seconds:new int[]{1,3,4,13,14,30,1800}){
            FunctionProgram p=FunctionProgram.frying(seconds,t,rhythm);
            assertEquals(1,p.phases.size());assertEquals(seconds,p.seconds);
            FunctionProgram.Phase phase=p.phases.get(0);
            assertEquals(seconds,phase.seconds);assertEquals(t,phase.temperature);assertEquals(-10,phase.speed);
            assertFalse(phase.values().manual);assertTrue(ControlProfile.supportsIn(ControlProfile.FRY,seconds,t,-10));
            int last=0;for(FunctionProgram.SpeedChange c:phase.changes){assertTrue(c.second>last&&c.second<seconds);assertTrue(c.speed==0||c.speed==-10);last=c.second;}
        }
    }
    @Test public void exactMotorBoundaries(){
        FunctionProgram.Phase p=FunctionProgram.frying(30,180,FryingRhythm.SHORT).phases.get(0);
        int[] due={3,13,16,26,29};assertEquals(due.length,p.changes.size());
        for(int i=0;i<due.length;i++){assertEquals(due[i],p.changes.get(i).second);assertEquals(i%2==0?0:-10,p.changes.get(i).speed);}
        assertTrue(FunctionProgram.frying(60,180,FryingRhythm.CONTINUOUS).phases.get(0).changes.isEmpty());
    }
    static class Rig extends FunctionProgramTest.Rig {
        final List<Integer> motors=new ArrayList<>();boolean rejectMotor;
        public boolean stir(int speed){if(rejectMotor)return false;motors.add(speed);s.submitted("FRY_MOTOR");return true;}
        void begin(int seconds){assertTrue(runner.start(FunctionProgram.frying(seconds,180,FryingRhythm.SHORT),now));ack();s.statusSequence++;s.ihStatus=1;s.ihGear=60;tick(0);}
        void motorAck(){s.acceptedCommandSequence=s.commandSequence;s.acceptedCommandStatusSequence=s.statusSequence;s.pendingAction="";s.statusSequence++;}
    }
    @Test public void motorRestKeepsHeaterAndNeverRestartsCook(){Rig r=new Rig();r.begin(30);r.tick(3000);assertEquals(Arrays.asList(0),r.motors);assertEquals(1,r.starts.size());assertEquals(0,r.stops);assertEquals(1,r.s.ihStatus);assertTrue(r.runner.status().contains("Messerpause"));r.motorAck();r.tick(10000);assertEquals(Arrays.asList(0,-10),r.motors);assertEquals(1,r.starts.size());}
    @Test public void cancelInRestNeverRestarts(){Rig r=new Rig();r.begin(30);r.tick(3000);r.motorAck();r.runner.cancel("Stop");r.tick(20000);assertEquals(Arrays.asList(0),r.motors);assertEquals(1,r.stops);assertFalse(r.runner.active());}
    @Test public void staleDataInRestStops(){Rig r=new Rig();r.begin(30);r.tick(3000);r.motorAck();r.runner.tick(r.now+1501);r.tick(20000);assertEquals(Arrays.asList(0),r.motors);assertFalse(r.runner.active());}
    @Test public void rejectedSubmissionStops(){Rig r=new Rig();r.begin(30);r.rejectMotor=true;r.tick(3000);assertFalse(r.runner.active());assertEquals(1,r.stops);assertTrue(r.motors.isEmpty());}
    @Test public void missingAckStopsWithoutRetry(){Rig r=new Rig();r.begin(30);r.tick(3000);r.tick(1201);assertFalse(r.runner.active());assertEquals(1,r.stops);assertEquals(Arrays.asList(0),r.motors);}
    @Test public void lateSwitchIsNotReplayed(){Rig r=new Rig();r.begin(30);r.tick(3751);assertFalse(r.runner.active());assertTrue(r.motors.isEmpty());}
    @Test public void finalRestWaitsForConfirmedStop(){Rig r=new Rig();r.begin(4);r.tick(3000);r.motorAck();r.tick(1000);assertTrue(r.runner.active());r.complete();r.tick(1);assertFalse(r.runner.active());assertEquals("Programm beendet",r.runner.status());assertEquals(1,r.starts.size());}
    @Test public void explicitPauseNeverResumes(){Rig r=new Rig();r.begin(30);r.tick(3000);r.motorAck();r.s.state=3;r.tick(10000);assertFalse(r.runner.active());assertEquals(Arrays.asList(0),r.motors);}
    @Test public void delayedStartupTruncatesTailWithoutExtendingHeatOrRestarting(){
        Rig r=new Rig();assertTrue(r.runner.start(FunctionProgram.frying(30,180,FryingRhythm.SHORT),r.now));
        r.tick(2000);r.ack();r.s.statusSequence++;r.tick(0);assertEquals(28,r.runner.remaining(r.now));
        for(int delay:new int[]{3000,10000,3000,10000}){r.tick(delay);r.motorAck();}
        assertEquals(Arrays.asList(0,-10,0,-10),r.motors);
        r.tick(3000);assertEquals(4,r.motors.size());assertTrue(r.runner.active());
        r.complete();r.s.naturalCompletion=false;r.s.stopReason="Eingestellte Laufzeit erreicht";r.tick(1);
        assertEquals("Programm beendet",r.runner.status());assertFalse(r.runner.active());assertEquals(1,r.starts.size());
    }
}
