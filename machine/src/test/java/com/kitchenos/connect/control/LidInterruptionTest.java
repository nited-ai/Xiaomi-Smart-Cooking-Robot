package com.kitchenos.connect.control;
import org.junit.Test;
import static org.junit.Assert.*;
public class LidInterruptionTest {
    private final ControlState s=new ControlState();private final LidInterruption lid=new LidInterruption();
    private void running(){s.foreground=s.rootVerified=s.connected=s.coverVerified=s.potVerified=s.ownRun=true;s.pot=1;s.samples=5;s.state=1;s.error=0;s.coverOpen=0;s.statusAt=s.infoAt=1000;s.potCheck.closedInterlock();lid.accepted(new ControlValues(60,0,5,false,true));}
    private void open(){s.error=1;s.lastDeviceError=1;s.deviceFaultLatched=true;lid.observe(s,"status",43);}
    private void stop(){s.error=0;s.state=0;s.ownRun=false;s.idleConfirmed=true;lid.observe(s,"stopped",43);}
    @Test public void holdsTimeAndRequiresAcknowledgedIdle(){running();open();assertTrue(lid.active());assertEquals(43,lid.seconds());assertFalse(lid.canClearLidFault(s,1000));stop();assertTrue(lid.canClearLidFault(s,1000));s.deviceFaultLatched=false;assertTrue(lid.ready(s,1000));}
    @Test public void closedLidIsMandatoryForContinue(){running();open();stop();s.deviceFaultLatched=false;s.coverOpen=1;assertFalse(lid.ready(s,1000));s.coverOpen=0;assertTrue(lid.ready(s,1000));}
    @Test public void onlyKnownLidFaultCanClear(){running();open();stop();s.lastDeviceError=2;assertFalse(lid.canClearLidFault(s,1000));}
    @Test public void noOutputCanRemainActive(){running();open();stop();s.motorRaw=1;assertFalse(lid.canClearLidFault(s,1000));s.motorRaw=0;s.ihGear=1;assertFalse(lid.canClearLidFault(s,1000));}
    @Test public void noUnownedOrUnacceptedRecovery(){running();lid.clear();open();assertFalse(lid.active());running();s.ownRun=false;open();assertFalse(lid.active());}
    @Test public void lidlessCookingDoesNotInterrupt(){running();lid.accepted(new ControlValues(60,90,10,true));s.coverOpen=1;lid.observe(s,"info",43);assertFalse(lid.active());}
    @Test public void otherFaultOrMissingPotDiscardsContinuation(){running();open();s.error=2;lid.observe(s,"status",43);assertFalse(lid.active());}
    @Test public void disconnectAndUncertaintyDiscardContinuation(){running();open();s.connected=false;lid.observe(s,"closed",43);assertFalse(lid.active());running();open();s.uncertainRun=true;lid.observe(s,"status",43);assertFalse(lid.active());}
    @Test public void staleOrUnconfirmedStopCannotRecover(){running();open();s.error=0;s.ownRun=false;s.idleConfirmed=true;s.state=0;assertFalse(lid.canClearLidFault(s,1000));lid.observe(s,"stopped",43);assertFalse(lid.canClearLidFault(s,1600));}
    @Test public void noContinuationAfterCompletionOrExplicitCancel(){running();open();lid.clear();assertFalse(lid.active());running();open();s.naturalCompletion=true;lid.observe(s,"stopped",0);assertFalse(lid.active());}
}
