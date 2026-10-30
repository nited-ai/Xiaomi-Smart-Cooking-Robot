package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeVesselStateTest {
    private ControlState live(){ControlState s=new ControlState();s.foreground=true;s.connected=true;s.samples=5;s.statusAt=s.infoAt=1000;s.coverVerified=true;s.coverOpen=0;s.potVerified=true;s.pot=1;s.state=1;s.error=0;return s;}
    @Test public void staleOrUnverifiedLidNeverLooksClosed(){ControlState s=live();assertEquals(HomeVesselState.Lid.CLOSED,HomeVesselState.from(s,1100).lid);assertEquals(HomeVesselState.Lid.UNKNOWN,HomeVesselState.from(s,1500).lid);s.coverVerified=false;assertEquals(HomeVesselState.Lid.UNKNOWN,HomeVesselState.from(s,1100).lid);s.coverVerified=true;s.coverOpen=-1;assertEquals(HomeVesselState.Lid.UNKNOWN,HomeVesselState.from(s,1100).lid);}
    @Test public void openingOverridesAnOwnedRun(){ControlState s=live();s.ownRun=true;s.coverOpen=1;HomeVesselState v=HomeVesselState.from(s,1100);assertEquals(HomeVesselState.Lid.OPEN,v.lid);assertFalse(v.cupInsertionCue);assertTrue(v.hint().contains("Deckel schließen"));}
    @Test public void closedLidDoesNotConfirmTheCup(){ControlState s=live();HomeVesselState v=HomeVesselState.from(s,1100);assertFalse(v.cupInsertionCue);assertEquals("Messbecher einsetzen",v.hint());s.ownRun=true;assertEquals("Messbecher einsetzen",HomeVesselState.from(s,1100).hint());s.foreground=false;assertFalse(HomeVesselState.from(s,1100).cupInsertionCue);}
    @Test public void missingBowlIsNotMisreportedAsAnOpenLid(){ControlState s=live();s.pot=0;s.coverOpen=1;HomeVesselState v=HomeVesselState.from(s,1100);assertEquals(HomeVesselState.Lid.POT_MISSING,v.lid);assertEquals("Mixtopf einsetzen",v.hint());assertFalse(v.cupInsertionCue);}
    @Test public void runningAloneNeverClaimsCupPlacement(){ControlState s=live();s.ownRun=true;s.state=3;assertFalse(HomeVesselState.from(s,1100).cupInsertionCue);s.state=1;assertFalse(HomeVesselState.from(s,1100).cupInsertionCue);}
    @Test public void errorTwoShowsMissingBowlWithoutInventingPotBit(){ControlState s=live();s.potVerified=false;s.pot=-1;s.error=2;s.coverOpen=1;assertEquals(HomeVesselState.Lid.POT_MISSING,HomeVesselState.from(s,1100).lid);s.error=1;assertEquals(HomeVesselState.Lid.OPEN,HomeVesselState.from(s,1100).lid);}
    @Test public void confirmedCupReachesPresentation(){ControlState s=live();HomeCupState cup=new HomeCupState();assertTrue(cup.confirm(s,1100));HomeVesselState v=HomeVesselState.from(s,1100,cup);assertTrue(v.cupInsertionCue);assertEquals("Messbecher bestätigt",v.hint());}
}
