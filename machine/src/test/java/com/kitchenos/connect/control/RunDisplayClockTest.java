package com.kitchenos.connect.control;
import org.junit.Test;
import static org.junit.Assert.*;
public class RunDisplayClockTest {
    private final ControlState s=new ControlState();private final RunDisplayClock clock=s.displayClock;
    private void submit(String action,int seconds){s.submitted(action);clock.submitted(action,seconds,s);}
    private int status(int state,long elapsed){s.ownRun=true;s.state=state;s.elapsed=elapsed;s.statusSequence++;return clock.remaining(s);}
    private void ack(){s.acceptedCommandSequence=s.commandSequence;s.acceptedCommandStatusSequence=s.statusSequence;s.pendingAction="";}
    @Test public void waitsThroughAckAndVesselCheck(){submit("START",5);s.remainingSeconds=2;assertEquals(5,status(0,87));ack();assertEquals(5,status(9,87));assertEquals(5,status(9,0));assertEquals(5,status(1,0));assertEquals(4,status(1,1));}
    @Test public void zeroNeedsConfirmedIdle(){submit("START",5);ack();assertEquals(1,status(1,5));assertEquals(1,status(11,5));s.ownRun=false;s.stopping=false;s.idleConfirmed=true;s.naturalCompletion=true;assertEquals(0,clock.remaining(s));}
    @Test public void delayedUiUsesMcuElapsedNotWallTime(){submit("START",60);ack();assertEquals(53,status(1,7));assertEquals(53,clock.remaining(s));assertEquals(42,status(1,18));}
    @Test public void settingsOnlyDoesNotResetTime(){submit("START",60);ack();assertEquals(50,status(1,10));submit("APPLY_SETTINGS",60);ack();assertEquals(49,status(1,11));}
    @Test public void timeChangeAnchorsAfterAck(){submit("START",60);ack();assertEquals(50,status(1,10));submit("APPLY",30);assertEquals(50,status(1,11));ack();assertEquals(30,status(1,12));assertEquals(28,status(1,14));}
    @Test public void rejectedTimeChangeKeepsPreviousClock(){submit("START",60);ack();assertEquals(50,status(1,10));submit("APPLY",30);s.pendingAction="";assertEquals(48,status(1,12));}
    @Test public void resumeAcceptsFirmwarePhaseReset(){submit("START",60);ack();status(1,10);submit("RESUME",50);ack();assertEquals(50,status(3,10));assertEquals(48,status(1,2));}
    @Test public void newRunDiscardsPreviousElapsed(){submit("START",5);ack();status(1,5);submit("START",20);assertEquals(20,status(9,5));ack();assertEquals(20,status(1,0));}
    @Test public void missingUiClockUsesExistingTelemetry(){s.remainingSeconds=17;assertEquals(17,clock.remaining(s));}
}
