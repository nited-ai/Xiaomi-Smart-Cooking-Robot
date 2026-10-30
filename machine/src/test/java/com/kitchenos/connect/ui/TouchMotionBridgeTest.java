package com.kitchenos.connect.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchMotionBridgeTest {
    @Test public void brakingRemainsEffectiveAtHighReportRates(){
        for(int interval:new int[]{5,10,12,20,40}){
            TouchMotionBridge slowing=new TouchMotionBridge(),steady=new TouchMotionBridge();slowing.reset(0,0);steady.reset(0,0);
            int last=0;
            for(int t=interval;t<=800;t+=interval){
                slowing.sample(t<=400?t*.1f:40+(t-400)*.05f,t);steady.sample(t*.05f,t);
                slowing.frame(t);steady.frame(t);last=t;
            }
            float slowLead=0,steadyLead=0;
            for(int t=last+16;t<last+800;t+=16){slowLead=slowing.frame(t)-slowing.measured();steadyLead=steady.frame(t)-steady.measured();}
            assertTrue("interval="+interval,slowLead<steadyLead*.65f);
        }
    }
    private TouchMotionBridge moving(float speed) {
        TouchMotionBridge bridge=new TouchMotionBridge();bridge.reset(0,0);
        for(int t=40;t<=400;t+=40){bridge.sample(t*speed,t);bridge.frame(t);}
        return bridge;
    }

    @Test public void stationaryContactNeverInventsMotion(){
        TouchMotionBridge bridge=new TouchMotionBridge();bridge.reset(153,0);
        for(int t=16;t<21000;t+=16){bridge.sample(153,t);assertEquals(153,bridge.frame(t),0);}
        assertFalse(bridge.needsFrame(21000));
    }
    @Test public void slowHoldIsBridgedAndThenStopsWithinTwelveDp(){
        TouchMotionBridge bridge=moving(.04f);float before=bridge.frame(416),last=before;
        for(int t=432;t<=752;t+=16){bridge.sample(16,t);float next=bridge.frame(t);assertTrue(next>last);last=next;}
        assertTrue(last-before>8);
        for(int t=768;t<2000;t+=16){bridge.sample(16,t);last=bridge.frame(t);assertTrue(last<=28);}
        assertEquals(16,bridge.measured(),0);assertFalse(bridge.needsFrame(2000));
    }
    @Test public void repeatedSamplesDoNotKeepExtendingPrediction(){
        TouchMotionBridge repeated=moving(.04f),silent=moving(.04f);
        for(int t=416;t<2000;t+=16){repeated.sample(16,t);assertEquals(silent.frame(t),repeated.frame(t),.00001f);}
        assertFalse(repeated.needsFrame(2000));
    }
    @Test public void aRealStopHasTheSameBoundedUncertaintyAsAHold(){
        TouchMotionBridge bridge=moving(.1f);float maximum=40;
        for(int t=416;t<2000;t+=16){bridge.sample(40,t);maximum=Math.max(maximum,bridge.frame(t));}
        assertTrue(maximum>40);assertTrue(maximum<=52);
        bridge.finish();assertEquals(40,bridge.frame(2001),0);assertFalse(bridge.needsFrame(2001));
    }
    @Test public void releaseCancelAndRegripDiscardThePrediction(){
        TouchMotionBridge bridge=moving(.04f);bridge.frame(600);bridge.finish();
        bridge.sample(900,700);assertEquals(16,bridge.frame(800),0);
        bridge.reset(0,900);assertEquals(0,bridge.frame(1000),0);assertFalse(bridge.needsFrame(1000));
    }
    @Test public void measuredReversalWinsImmediately(){
        TouchMotionBridge bridge=moving(.04f);assertTrue(bridge.frame(550)>16);
        bridge.sample(15,560);assertEquals(15,bridge.frame(576),.00001f);
        bridge.sample(14,600);bridge.sample(13,640);assertTrue(bridge.frame(656)<=13);
    }
    @Test public void quickMotionDoesNotCoastForTheSlowHoldHorizon(){
        TouchMotionBridge bridge=moving(1);
        for(int t=416;t<2000;t+=16)assertTrue(bridge.frame(t)<=412);
        assertFalse(bridge.needsFrame(2000));
    }
    @Test public void recoveryNeverAddsPredictedDistanceToTheMeasuredPosition(){
        TouchMotionBridge bridge=moving(.04f);
        for(int t=416;t<720;t+=16)bridge.frame(t);
        for(int t=720;t<1400;t+=40){float real=t*.04f;bridge.sample(real,t);float shown=bridge.frame(t+16);assertEquals(real,bridge.measured(),0);assertTrue(Math.abs(shown-real)<=12);}
        bridge.finish();assertEquals(bridge.measured(),bridge.frame(1500),0);
    }
    @Test public void staleAndInvalidSamplesCannotRestartTheGesture(){
        TouchMotionBridge bridge=moving(.04f);
        bridge.sample(10000,399);bridge.sample(Float.NaN,500);
        assertEquals(16,bridge.measured(),0);assertTrue(Float.isFinite(bridge.frame(600)));
    }
    @Test public void recoveryBehindThePredictionDoesNotBounceBackwards(){
        TouchMotionBridge bridge=moving(.04f);float last=16;
        for(int t=416;t<720;t+=16)last=bridge.frame(t);
        bridge.sample(20,720);
        for(int t=736;t<1000;t+=16){float next=bridge.frame(t);assertTrue(next>=last);last=next;}
        bridge.sample(30,1000);assertTrue(bridge.frame(1016)>=last);
    }
    @Test public void measuredSlowingReducesLeadComparedWithSteadyMotionAtTheSameFinalSpeed(){
        TouchMotionBridge slowing=moving(.1f),steady=moving(.05f);
        for(int t=440;t<=600;t+=40){slowing.sample(40+(t-400)*.05f,t);steady.sample(t*.05f,t);slowing.frame(t);steady.frame(t);}
        float slowLead=0,steadyLead=0;
        for(int t=616;t<1200;t+=16){slowLead=slowing.frame(t)-slowing.measured();steadyLead=steady.frame(t)-steady.measured();}
        assertTrue(slowLead>0);assertTrue(slowLead<steadyLead*.65f);
    }
    @Test public void singlePixelRoundingDoesNotEnableStrongBraking(){
        TouchMotionBridge bridge=moving(.05f);
        bridge.sample(21,440);bridge.frame(440);bridge.sample(24,480);bridge.frame(480);
        float lead=0;for(int t=496;t<1100;t+=16)lead=bridge.frame(t)-bridge.measured();
        assertTrue(lead>10);
    }
    @Test public void acceleratingAgainRestoresTheLongerBridge(){
        TouchMotionBridge bridge=moving(.1f);
        for(int t=440;t<=600;t+=40){bridge.sample(40+(t-400)*.05f,t);bridge.frame(t);}
        for(int t=640;t<=840;t+=40){bridge.sample(50+(t-600)*.1f,t);bridge.frame(t);}
        float lead=0;for(int t=856;t<1500;t+=16)lead=bridge.frame(t)-bridge.measured();
        assertTrue(lead>10);assertTrue(lead<=12);
    }
    @Test public void disablingAdaptiveBrakingRestoresThePreviouslyEvaluatedLongerLead(){
        TouchMotionBridge bridge=new TouchMotionBridge();bridge.adaptiveBraking(false);bridge.reset(0,0);
        for(int t=40;t<=400;t+=40){bridge.sample(t*.1f,t);bridge.frame(t);}
        for(int t=440;t<=600;t+=40){bridge.sample(40+(t-400)*.05f,t);bridge.frame(t);}
        float lead=0;for(int t=616;t<1200;t+=16)lead=bridge.frame(t)-bridge.measured();
        assertTrue(lead>10);assertTrue(lead<=12);
    }
}
