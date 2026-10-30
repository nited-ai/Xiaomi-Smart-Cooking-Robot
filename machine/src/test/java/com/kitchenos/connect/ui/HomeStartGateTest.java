package com.kitchenos.connect.ui;
import com.kitchenos.connect.control.ControlState;
import org.junit.Test;
import static org.junit.Assert.*;
public final class HomeStartGateTest {
    private ControlState ready(){ControlState s=new ControlState();s.foreground=s.rootVerified=s.connected=s.coverVerified=s.potVerified=s.idleConfirmed=true;s.coverOpen=0;s.pot=1;s.error=0;s.state=0;s.samples=3;s.statusAt=s.infoAt=1000;return s;}
    private HomeStartGate.Action next(ControlState s){return HomeStartGate.next(s,1000,false);}
    @Test public void missingLinkIsPreparedInStagesWithoutArmingAStart(){
        ControlState s=ready();s.connected=s.rootVerified=false;
        assertEquals(HomeStartGate.Action.ROOT,next(s));
        s.rootChecking=true;assertEquals(HomeStartGate.Action.WAIT,next(s));
        s.rootChecking=false;s.rootVerified=true;assertEquals(HomeStartGate.Action.CONNECT,next(s));
        s.connecting=true;assertEquals(HomeStartGate.Action.WAIT,next(s));
        s.connecting=false;s.connected=true;assertEquals(HomeStartGate.Action.READY,next(s));
        assertEquals("",s.pendingAction);assertEquals(0,s.commandSequence);assertFalse(s.ownRun);
    }
    @Test public void staleOrIncompleteTelemetryNeverBecomesReady(){
        ControlState s=ready();assertEquals(HomeStartGate.Action.READY,next(s));
        s.statusAt=499;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.infoAt=-1;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.samples=2;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
    }
    @Test public void lidPotAndOutputGuardsRemainEffective(){
        ControlState s=ready();s.coverOpen=1;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.pot=0;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.motorStatus=1;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.ihGear=1;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
    }
    @Test public void faultsAndUnknownRunsNeverTriggerReconnection(){
        ControlState s=ready();s.connected=false;s.uncertainRun=true;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.connected=false;s.deviceFaultLatched=true;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.connected=false;s.ownRun=true;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.connected=false;s.potCheck.require();assertEquals(HomeStartGate.Action.BLOCKED,next(s));
    }
    @Test public void pendingCommandsAndStopsNeverTriggerReconnection(){
        ControlState s=ready();s.connected=false;s.pendingAction="START";assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.connected=false;s.stopping=true;assertEquals(HomeStartGate.Action.BLOCKED,next(s));
        s=ready();s.connected=false;s.closing=true;assertEquals(HomeStartGate.Action.WAIT,next(s));
    }
    @Test public void foregroundMustComeFromTheOwningScreen(){
        ControlState s=ready();s.foreground=false;assertEquals(HomeStartGate.Action.BLOCKED,next(s));assertFalse(s.foreground);
    }
}
