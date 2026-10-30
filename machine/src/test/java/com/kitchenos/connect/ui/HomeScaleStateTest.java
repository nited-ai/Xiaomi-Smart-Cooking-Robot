package com.kitchenos.connect.ui;
import com.kitchenos.connect.control.ControlState;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeScaleStateTest {
    private final HomeScaleState scale=new HomeScaleState();private final ControlState state=new ControlState();private long now=1000;
    private void samples(int grams,int count){for(int i=0;i<count;i++){now+=50;state.samples++;state.statusAt=now;state.weightRaw=grams;scale.observe(state,now);}}
    private void baseline(){state.foreground=state.connected=true;state.state=0;state.error=0;state.samples=3;samples(1000,15);assertEquals("0 g",scale.text());}
    @Test public void touchPressureIsFrozenAndCannotBecomeTare(){baseline();samples(1123,15);assertEquals("123 g",scale.text());scale.touch(true,now);scale.tare();samples(1450,10);assertEquals("123 g",scale.text());assertTrue(scale.pendingTare());scale.touch(false,now);samples(1450,10);assertEquals("123 g",scale.text());samples(1123,15);samples(1123,15);assertEquals("0 g",scale.text());assertFalse(scale.pendingTare());}
    @Test public void firstSampleImmediatelyShowsAddedAndRemovedWeight(){baseline();samples(1100,15);samples(1400,1);assertEquals("400 g",scale.text());samples(1100,1);assertEquals("100 g",scale.text());samples(1100,15);assertEquals("100 g",scale.text());}
    @Test public void tareSurvivesMotorRunOnSameConnection(){baseline();samples(1120,15);state.ownRun=true;state.state=1;samples(1600,20);state.ownRun=false;state.state=0;samples(1120,15);assertEquals("120 g",scale.text());}
    @Test public void staleOrFaultedWeightIsRemoved(){baseline();samples(1120,15);now+=600;scale.observe(state,now);assertEquals("— g",scale.text());samples(1120,15);assertEquals("120 g",scale.text());state.error=1;samples(1120,1);assertEquals("— g",scale.text());}
    @Test public void reconnectDiscardsOldBaseline(){baseline();state.connected=false;scale.observe(state,now);state.connected=true;state.samples=3;samples(1200,15);assertEquals("0 g",scale.text());}
    @Test public void counterRollbackImmediatelyRemovesOldGrams(){baseline();samples(1123,15);assertEquals("123 g",scale.text());state.samples=3;samples(1250,1);assertEquals("— g",scale.text());samples(1250,15);assertEquals("0 g",scale.text());}
    @Test public void reconnectCannotZeroAHeldFinger(){baseline();scale.touch(true,now);state.connected=false;scale.observe(state,now);state.connected=true;state.samples=3;samples(1400,30);assertEquals("— g",scale.text());scale.touch(false,now);samples(1000,30);assertEquals("0 g",scale.text());}
    @Test public void runCancelsAnUnfinishedTareRequest(){baseline();scale.tare();assertTrue(scale.pendingTare());state.ownRun=true;state.state=1;samples(1500,2);assertFalse(scale.pendingTare());state.ownRun=false;state.state=0;samples(1120,15);assertEquals("120 g",scale.text());}
    @Test public void tareAfterLidRemovalUsesTheSameNetWeightForFill(){
        baseline();state.coverVerified=true;state.coverOpen=1;samples(700,15);
        assertEquals(Integer.valueOf(-300),scale.grams());scale.tare();samples(700,15);
        assertEquals(Integer.valueOf(0),scale.grams());assertEquals(0,HomeWeightVisuals.fillFraction(scale.grams()),0);
        samples(1000,15);assertEquals("300 g",scale.text());assertEquals(.15f,HomeWeightVisuals.fillFraction(scale.grams()),.00001f);
        scale.tare();samples(1000,15);assertEquals("0 g",scale.text());assertEquals(0,HomeWeightVisuals.fillFraction(scale.grams()),0);
        samples(1250,15);assertEquals("250 g",scale.text());assertEquals(.125f,HomeWeightVisuals.fillFraction(scale.grams()),.00001f);
    }
    @Test public void disconnectDropsThePreviousReading(){baseline();samples(1600,15);scale.tare();samples(1600,15);state.connected=false;scale.observe(state,now);assertNull(scale.grams());state.connected=true;samples(1600,15);assertEquals(Integer.valueOf(0),scale.grams());}
    @Test public void heavyIngredientCheckPreservesTareAndNetWeight(){baseline();samples(2800,10);assertEquals("1800 g",scale.text());state.potCheck.require();samples(2800,10);assertNull(scale.grams());state.potCheck.begin(1);samples(2800,10);state.potCheck.complete(1,"clear_window",true);samples(2800,15);assertEquals("1800 g",scale.text());assertEquals(Integer.valueOf(1800),scale.grams());}
    @Test public void knobPressRollsBackShortPressureBeforeRun(){baseline();samples(1120,15);samples(1270,2);assertEquals("270 g",scale.text());scale.touch(true,now);assertEquals("120 g",scale.text());state.ownRun=true;state.state=9;samples(1270,15);scale.touch(false,now);samples(1270,15);assertEquals("120 g",scale.text());}
    @Test public void rotaryEventsExtendSettlingButDoNotChangeTare(){baseline();scale.touch(false,now);samples(1300,10);assertEquals("0 g",scale.text());scale.touch(false,now);samples(1300,10);assertEquals("0 g",scale.text());samples(1000,20);assertEquals("0 g",scale.text());samples(1250,15);assertEquals("250 g",scale.text());}
}
