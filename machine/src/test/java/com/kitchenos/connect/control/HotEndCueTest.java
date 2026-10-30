package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HotEndCueTest {
    @Test public void restoredHeatAloneNeverSounds(){HotEndCue c=new HotEndCue();assertFalse(c.observe(0,false,true,180,true));}
    @Test public void waitsForConfirmedStopAndSoundsOnce(){
        HotEndCue c=new HotEndCue();assertFalse(c.observe(1,false,false,180,true));
        assertTrue(c.observe(1,false,true,180,true));assertFalse(c.observe(1,false,true,180,true));
    }
    @Test public void intervalsProduceOnlyOneFinalCue(){
        HotEndCue c=new HotEndCue();assertFalse(c.observe(1,true,false,180,true));assertFalse(c.observe(1,true,true,180,true));
        assertFalse(c.observe(2,true,false,180,true));assertFalse(c.observe(2,true,true,180,true));
        assertTrue(c.observe(2,false,true,180,true));assertFalse(c.observe(2,false,true,180,true));
    }
    @Test public void coldEndDoesNotSoundLaterOnUnrelatedHeat(){
        HotEndCue c=new HotEndCue();assertFalse(c.observe(1,false,false,60,true));assertFalse(c.observe(1,false,true,60,true));assertFalse(c.observe(1,false,true,180,true));
    }
    @Test public void thresholdIsStrictlyAbove150(){
        HotEndCue c=new HotEndCue();c.observe(1,false,false,150,true);assertFalse(c.observe(1,false,true,150,true));
        c.observe(2,false,false,151,true);assertTrue(c.observe(2,false,true,151,true));
    }
    @Test public void missingMeasurementCannotTriggerOrReplay(){
        HotEndCue c=new HotEndCue();c.observe(1,false,false,180,true);assertFalse(c.observe(1,false,true,180,false));assertFalse(c.observe(1,false,true,180,true));
    }
    @Test public void anotherAcceptedRunCanSoundAgain(){
        HotEndCue c=new HotEndCue();c.observe(1,false,false,180,true);assertTrue(c.observe(1,false,true,180,true));
        assertFalse(c.observe(2,false,false,180,true));assertTrue(c.observe(2,false,true,180,true));
    }
}
