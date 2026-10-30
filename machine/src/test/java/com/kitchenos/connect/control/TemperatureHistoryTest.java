package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class TemperatureHistoryTest {
    @Test public void warningSurvivesStopAndClearsOnlyOnFreshCoolReading(){
        TemperatureHistory h=new TemperatureHistory();h.observe(1000,100000,180,180,true,true);assertTrue(h.hot());
        h.observe(3000,102000,179,0,true,false);assertTrue(h.hot());assertFalse(h.heating(3000));
        assertTrue(h.hot());assertFalse(h.fresh(6000));h.observe(6000,105000,100,0,true,false);assertFalse(h.hot());
    }
    @Test public void disconnectedMissingPotOrInvalidTemperatureCannotClearHot(){
        TemperatureHistory h=new TemperatureHistory();h.observe(1000,100000,120,180,true,true);
        h.observe(3000,102000,20,0,false,false);assertTrue(h.hot());assertFalse(h.fresh(3000));
        h.observe(5000,104000,0,0,true,false);assertTrue(h.hot());
        h.observe(7000,106000,255,0,true,false);assertTrue(h.hot());assertEquals(1,h.points().size());
    }
    @Test public void restoringHotSnapshotNeverPretendsToHaveLiveTemperature(){
        TemperatureHistory h=new TemperatureHistory();h.observe(1000,100000,180,180,true,true);
        TemperatureHistory restored=new TemperatureHistory();restored.restore(h.encode());assertTrue(restored.hot());assertFalse(restored.fresh(1000));assertFalse(restored.heating(1000));
        restored.observe(100,110000,140,0,true,false);assertTrue(restored.points().get(1).gap);assertTrue(restored.hot());
    }
    @Test public void normalPlateauIsRecordedAtRegularIntervals(){
        TemperatureHistory h=new TemperatureHistory();for(int t=0;t<=60000;t+=100)h.observe(t,100000+t,105,180,true,true);
        assertEquals(31,h.points().size());assertTrue(h.heating(60000));assertFalse(h.heating(61500));
        assertEquals(105,h.points().get(30).temperature);
    }
    @Test public void lossOfTelemetryCreatesGapInsteadOfInventedRamp(){
        TemperatureHistory h=new TemperatureHistory();h.observe(1000,100000,105,180,true,true);h.observe(9000,108000,150,180,true,true);
        assertEquals(2,h.points().size());assertTrue(h.points().get(1).gap);
    }
    @Test public void historyIsBoundedAndTargetChangeIsRecordedImmediately(){
        TemperatureHistory h=new TemperatureHistory();for(int i=0;i<2000;i++)h.observe(i*2000L,100000+i*2000L,80,100,true,true);
        assertEquals(TemperatureHistory.LIMIT,h.points().size());h.observe(3998001,4098001,80,180,true,true);
        assertEquals(180,h.points().get(h.points().size()-1).target);assertEquals(TemperatureHistory.LIMIT,h.points().size());
    }
    @Test public void backwardReceiptCannotClearOrChangeHotState(){
        TemperatureHistory h=new TemperatureHistory();h.observe(2000,100000,180,180,true,true);h.observe(1000,99000,20,0,true,false);assertTrue(h.hot());assertEquals(180,h.temperature());
    }
    @Test public void fallingWallClockDoesNotReverseGraphAxis(){
        TemperatureHistory h=new TemperatureHistory();h.observe(1000,100000,80,180,true,true);h.observe(4000,90000,90,180,true,true);assertTrue(h.points().get(1).time>h.points().get(0).time);
    }
}
