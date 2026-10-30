package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeCupStateTest {
    private final HomeCupState cup=new HomeCupState();
    private final ControlState s=new ControlState();
    private long now=1000;
    public HomeCupStateTest(){s.foreground=s.connected=s.coverVerified=true;s.samples=5;s.error=s.state=0;s.coverOpen=1;}
    private void sample(int grams){now+=200;s.samples++;s.statusAt=s.infoAt=now;s.weightRaw=grams;cup.observe(s,now);}
    private void stable(int grams){for(int i=0;i<10;i++)sample(grams);}
    private void close(int delta){stable(1000);s.coverOpen=0;stable(1000+delta);}
    @Test public void twentyToThirtyGramsNeverConfirmCup(){for(int grams:new int[]{20,25,30}){cup.reset();s.coverOpen=1;close(grams);assertFalse("delta "+grams,cup.inserted());}}
    @Test public void pressingAndDriftingWeightsNeverConfirm(){stable(1000);s.coverOpen=0;for(int grams:new int[]{2500,1025,-400,1000,5000,1020,1030})stable(grams);assertFalse(cup.inserted());}
    @Test public void alreadyClosedLidStillRequiresExplicitConfirmation(){s.coverOpen=0;stable(1025);assertFalse(cup.inserted());assertTrue(cup.confirm(s,now));assertTrue(cup.inserted());}
    @Test public void openingRequiresAnotherConfirmation(){close(0);cup.confirm(s,now);s.coverOpen=1;sample(1000);assertFalse(cup.inserted());assertFalse(cup.confirm(s,now));s.coverOpen=0;sample(1025);assertFalse(cup.inserted());assertTrue(cup.confirm(s,now));}
    @Test public void weightChangesDoNotRevokeManualConfirmation(){close(0);cup.confirm(s,now);for(int grams:new int[]{1025,-500,5000,1000}){stable(grams);assertTrue(cup.inserted());}}
    @Test public void runningNeverInventsConfirmation(){close(25);s.ownRun=true;s.state=1;s.motorRaw=400;stable(1025);assertFalse(cup.inserted());}
    @Test public void confirmedCupSurvivesFreshRunningAndPausedTelemetry(){close(0);cup.confirm(s,now);s.ownRun=true;s.state=1;s.motorRaw=400;stable(900);assertTrue(cup.inserted());s.state=3;s.motorRaw=0;stable(1000);assertTrue(cup.inserted());}
    @Test public void staleOrBackgroundClearsConfirmation(){close(0);cup.confirm(s,now);cup.observe(s,now+500);assertFalse(cup.inserted());sample(1000);cup.confirm(s,now);s.foreground=false;sample(1025);assertFalse(cup.inserted());}
    @Test public void faultsAndMissingPotCannotBeConfirmed(){for(int error:new int[]{1,2,3}){s.error=error;sample(1025);assertFalse(cup.confirm(s,now));}s.error=0;s.potVerified=true;s.pot=0;s.coverOpen=0;sample(1025);assertFalse(cup.confirm(s,now));}
    @Test public void reconnectCannotReuseConfirmation(){close(0);cup.confirm(s,now);s.samples=0;sample(1025);assertFalse(cup.inserted());s.samples=100;stable(1025);assertFalse(cup.inserted());}
    @Test public void statusGapDiscardsConfirmation(){close(0);cup.confirm(s,now);now+=500;sample(1000);assertFalse(cup.inserted());}
    @Test public void runningOrPendingStartCannotManuallyConfirm(){close(0);s.ownRun=true;assertFalse(cup.confirm(s,now));s.ownRun=false;s.pendingAction="START";assertFalse(cup.confirm(s,now));s.pendingAction="";s.stopping=true;assertFalse(cup.confirm(s,now));}
    @Test public void delayedSampleKeepsVisualReceiptThroughRunEnd(){
        close(0);assertTrue(cup.confirm(s,now));s.pendingAction="START";sample(1000);s.pendingAction="";s.ownRun=true;
        now+=700;sample(1000);assertFalse(cup.inserted());assertTrue(cup.displayedInserted());
        assertTrue(HomeVesselState.from(s,now,cup).cupInsertionCue);
        s.ownRun=false;sample(1000);assertTrue(cup.displayedInserted());assertFalse(cup.inserted());assertFalse(cup.inserted());
    }
    @Test public void staleOwnedRunCannotReuseVisualReceiptToAuthorizeStart(){
        close(0);cup.confirm(s,now);s.ownRun=true;sample(1000);cup.observe(s,now+500);
        assertFalse(cup.inserted());assertTrue(cup.displayedInserted());
        s.ownRun=false;sample(1000);assertTrue(cup.displayedInserted());assertFalse(cup.inserted());
    }
    @Test public void openingAndFaultsImmediatelyRemoveTheRunVisualReceipt(){
        for(int event=0;event<4;event++){
            s.error=0;s.ownRun=false;s.deviceFaultLatched=false;s.coverOpen=0;sample(1000);cup.confirm(s,now);s.ownRun=true;sample(1000);assertTrue(cup.displayedInserted());
            if(event==0)s.coverOpen=1;else if(event==1)s.error=2;else if(event==2)s.deviceFaultLatched=true;else s.samples=0;
            sample(1000);assertFalse(cup.displayedInserted());assertFalse(cup.inserted());
        }
    }
    @Test public void directStartCueIsVisualOnly(){close(0);s.pendingAction="START";cup.indicateStart();sample(1000);assertTrue(cup.displayedInserted());assertFalse(cup.inserted());s.pendingAction="";s.ownRun=true;sample(1000);assertTrue(cup.displayedInserted());s.ownRun=false;sample(1000);assertTrue(cup.displayedInserted());assertFalse(cup.inserted());}
    @Test public void openLidAlwaysWinsOverDirectStartImage(){close(0);s.pendingAction="START";cup.indicateStart();s.coverOpen=1;sample(1000);assertFalse(cup.displayedInserted());assertFalse(cup.inserted());}
    @Test public void retainedPoseEndsWithLidOpeningOrDisconnect(){
        for(int temperature:new int[]{35,100,180}){
            cup.reset();s.connected=true;s.coverOpen=0;s.ownRun=true;s.temperature=temperature;sample(1000);cup.indicateStart();
            s.ownRun=false;sample(1000);assertTrue(cup.displayedInserted());assertFalse(cup.inserted());
            s.coverOpen=1;sample(1000);assertFalse(cup.displayedInserted());
            s.coverOpen=0;s.ownRun=true;sample(1000);cup.indicateStart();s.connected=false;sample(1000);assertFalse(cup.displayedInserted());
        }
    }
    @Test public void heatWithoutStartNeverInventsCupPlacement(){close(0);s.temperature=180;sample(1000);assertFalse(cup.displayedInserted());assertFalse(cup.inserted());}
}
