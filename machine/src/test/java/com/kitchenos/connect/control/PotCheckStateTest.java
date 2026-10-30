package com.kitchenos.connect.control;
import com.kitchenos.connect.ui.HomeVesselState;
import org.junit.Test;
import static org.junit.Assert.*;

public class PotCheckStateTest {
    private PotCheckState missing(){PotCheckState p=new PotCheckState();assertTrue(p.begin(1));assertTrue(p.complete(1,"missing",true));return p;}
    @Test public void weightChangeCannotClearConfirmedAbsence(){PotCheckState p=missing();p.invalidate();assertTrue(p.missing());assertTrue(p.required());assertTrue(p.begin(2));assertTrue(p.missing());assertFalse(p.complete(1,"clear_window",true));assertFalse(p.complete(2,"clear_window",false));assertTrue(p.missing());assertTrue(p.complete(2,"clear_window",true));assertFalse(p.missing());assertFalse(p.required());}
    @Test public void unknownOrAbortedCheckRetainsAbsence(){PotCheckState p=missing();p.begin(2);p.complete(2,"unknown",true);assertTrue(p.missing());assertTrue(p.required());p.begin(3);p.aborted();assertTrue(p.missing());assertTrue(p.required());}
    @Test public void reconnectDoesNotInventPresenceOrReuseOldIds(){PotCheckState p=missing();p.reconnect();assertTrue(p.missing());assertTrue(p.required());assertTrue(p.begin(1));assertTrue(p.complete(1,"clear_window",true));assertFalse(p.missing());}
    @Test public void missingPhotoPersistsThroughStaleDataAndCheck(){ControlState s=new ControlState();s.potCheck.begin(1);s.potCheck.complete(1,"missing",true);assertEquals(HomeVesselState.Lid.POT_MISSING,HomeVesselState.from(s,1000).lid);s.potCheck.invalidate();s.potCheck.begin(2);assertEquals(HomeVesselState.Lid.POT_MISSING,HomeVesselState.from(s,1000).lid);}
    @Test public void pendingChangeBlocksCookingWithoutInventingMissingSensor(){ControlState s=new ControlState();s.foreground=s.rootVerified=s.connected=s.coverVerified=true;s.samples=5;s.statusAt=s.infoAt=1000;s.coverOpen=0;s.state=0;s.error=0;assertTrue(s.safe(1100));s.potCheck.require();assertFalse(s.safe(1100));assertFalse(s.potVerified);assertFalse(s.potMissing());}
    @Test public void closedInterlockCanResolveAnOldAbsenceWithoutAnIhResult(){PotCheckState p=missing();p.closedInterlock();assertFalse(p.missing());assertFalse(p.required());assertEquals(PotCheckState.Result.UNKNOWN,p.result());}
    @Test public void closedInterlockCannotInterruptAnActiveDiagnostic(){PotCheckState p=missing();p.begin(2);p.closedInterlock();assertTrue(p.active());assertTrue(p.missing());assertTrue(p.required());}
}
