package com.kitchenos.connect.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class RootSwipeDecisionTest {
    @Test public void sparsePacketsAndStationaryReleaseKeepBriefFlick(){
        assertEquals(1000,RootSwipeDecision.flickVelocity(100,100,100,1),.01);
        assertEquals(-1000,RootSwipeDecision.flickVelocity(-100,100,100,1),.01);
    }
    @Test public void measuredReversalCancelsFlick(){
        assertEquals(0,RootSwipeDecision.flickVelocity(100,130,100,1),0);
        assertEquals(0,RootSwipeDecision.flickVelocity(-100,130,100,1),0);
    }
    @Test public void holdingDoesNotBecomeAFlick(){
        assertEquals(0,RootSwipeDecision.flickVelocity(100,100,500,1),0);
        assertEquals(0,RootSwipeDecision.flickVelocity(100,100,0,1),0);
    }
    @Test public void stationaryUpAndTwoPacketGestureUseSameDecision(){
        assertTrue(RootSwipeDecision.accept(-100,800,-1,RootSwipeDecision.flickVelocity(-100,100,100,1),1));
        assertFalse(RootSwipeDecision.accept(-100,800,-1,RootSwipeDecision.flickVelocity(-100,140,100,1),1));
    }
    @Test public void deliberateDistanceWorksBothWays(){
        assertTrue(RootSwipeDecision.accept(-150,800,-1,0,1));
        assertTrue(RootSwipeDecision.accept(150,800,1,0,1));
    }
    @Test public void shortSlowAndWrongDirectionStayPut(){
        assertFalse(RootSwipeDecision.accept(-100,800,-1,-300,1));
        assertFalse(RootSwipeDecision.accept(-100,800,-1,900,1));
        assertFalse(RootSwipeDecision.accept(100,800,-1,-900,1));
    }
    @Test public void quickMeasuredFlickNeedsMinimumTravel(){
        assertTrue(RootSwipeDecision.accept(-100,800,-1,-900,1));
        assertTrue(RootSwipeDecision.accept(100,800,1,900,1));
        assertFalse(RootSwipeDecision.accept(-60,800,-1,-2000,1));
    }
    @Test public void thresholdsScaleWithDensity(){
        assertTrue(RootSwipeDecision.accept(-200,1600,-1,-1800,2));
        assertFalse(RootSwipeDecision.accept(-120,1600,-1,-1800,2));
        assertFalse(RootSwipeDecision.accept(-200,1600,-1,-900,2));
    }
}
