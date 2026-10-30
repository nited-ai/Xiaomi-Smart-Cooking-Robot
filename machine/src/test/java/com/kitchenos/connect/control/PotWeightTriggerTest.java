package com.kitchenos.connect.control;
import org.junit.Test;
import static org.junit.Assert.*;

public class PotWeightTriggerTest {
    @Test public void largeDropWaitsForTwoSecondsOfFreshStableSamples(){PotWeightTrigger t=new PotWeightTrigger();int sample=0;for(int at=0;at<=1000;at+=50)t.observe(2200,++sample,at,true);for(int at=1050;at<3050;at+=50){t.observe(20,++sample,at,true);assertFalse(t.ready());}assertTrue(t.observe(20,++sample,3050,true));assertTrue(t.ready());t.consumed();assertFalse(t.ready());}
    @Test public void tareAndSmallIngredientChangesCannotTrigger(){PotWeightTrigger t=new PotWeightTrigger();int sample=0;for(int at=0;at<=2000;at+=50)t.observe(at<1000?2200:1800,++sample,at,true);assertFalse(t.ready());}
    @Test public void repeatedSamplesCannotManufactureRestTime(){PotWeightTrigger t=new PotWeightTrigger();for(int i=0;i<=20;i++)t.observe(2200,i,i*50,true);t.observe(0,21,1050,true);t.observe(0,21,4000,true);assertFalse(t.ready());}
    @Test public void observationGapRetainsBaselineButNotQuietTime(){PotWeightTrigger t=new PotWeightTrigger();int sample=0;for(int at=0;at<=1000;at+=50)t.observe(2200,++sample,at,true);t.observe(2200,++sample,1050,false);for(int at=1100;at<=3000;at+=50)t.observe(0,++sample,at,true);assertFalse(t.ready());assertTrue(t.observe(0,++sample,3100,true));}
    @Test public void halfSecondGapCannotAdoptAnAbsentPotAsNewBaseline(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);t.observe(0,2,50,true);int sample=2;for(int at=550;at<2550;at+=50)assertFalse(t.observe(0,++sample,at,true));assertTrue(t.observe(0,++sample,2550,true));}
    @Test public void parkedCookCannotAdvanceSettlingOrChangeReference(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);t.pause();for(int i=2;i<30;i++)assertFalse(t.observe(3000,i,i*50,false));for(int i=30;i<70;i++)assertFalse(t.observe(0,i,i*50,true));assertTrue(t.observe(0,70,3500,true));}
    @Test public void invalidWeightStillDiscardsReference(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);t.observe(6000,2,50,true);for(int i=3;i<70;i++)assertFalse(t.observe(0,i,i*50,true));}
    @Test public void unchangedUnknownReceiptNeverRetriesAfterTelemetryGap(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);for(int i=2;i<70;i++)assertFalse(t.observe(1850,i,650+i*50,true));}
    @Test public void menuRemovalSurvivesPauseAndCounterRestartButNeedsNewQuietWindow(){
        PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,90,1000);t.pause();
        for(int i=1;i<=8;i++)assertFalse(t.observe(0,i,10000+i*50,false));
        t.pause();assertFalse(t.settled());
        for(int i=1;i<=40;i++)assertFalse(t.observe(0,i,20000+i*50,true));
        assertTrue(t.observe(0,41,22050,true));
    }
    @Test public void unchangedOrLidOnlyMenuReturnDoesNotRequestDiagnosis(){
        for(int grams:new int[]{1850,2147}){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,90,1000);t.pause();
            for(int i=1;i<=60;i++)assertFalse(t.observe(grams,i,20000+i*50,true));assertTrue(t.settled());}
    }
    @Test public void unknownReceiptDoesNotRetryAcrossMenuReturn(){
        PotWeightTrigger t=new PotWeightTrigger();t.reference(0,90,1000);t.pause();
        for(int i=1;i<=60;i++)assertFalse(t.observe(0,i,20000+i*50,true));
    }
    @Test public void returningLoadWaitsBeforeRequestingAnyCheck(){PotWeightTrigger t=new PotWeightTrigger();for(int i=0;i<=20;i++)assertFalse(t.observe(0,i,i*50,true));assertFalse(t.observe(2150,21,1050,true));assertFalse(t.ready());for(int i=22;i<=61;i++)t.observe(2150,i,i*50,true);assertTrue(t.ready());}
    @Test public void bothMeasuredBareAndCoveredVesselsTrigger(){for(int mass:new int[]{1850,2147,3800}){PotWeightTrigger t=new PotWeightTrigger();t.reference(mass,1,0);for(int i=2;i<=42;i++)t.observe(0,i,(i-1)*50,true);assertTrue(t.ready());}}
    @Test public void lidAndCupAloneCannotTrigger(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);for(int i=2;i<=80;i++)t.observe(2147,i,(i-1)*50,true);assertFalse(t.ready());}
    @Test public void partialLiftDoesNotReplaceTheReferenceAfterOnlyOneSecond(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);int sample=1;for(int at=50;at<=1050;at+=50)t.observe(1000,++sample,at,true);for(int at=1100;at<=3100;at+=50)t.observe(0,++sample,at,true);assertTrue(t.ready());}
    @Test public void transientLiftAndReturnCancelsTheCandidate(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);for(int i=2;i<=20;i++)t.observe(0,i,(i-1)*50,true);for(int i=21;i<=80;i++)t.observe(1850,i,(i-1)*50,true);assertFalse(t.ready());}
    @Test public void touchAndReleaseRestartTheFullQuietPeriod(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,1,0);t.touch(true);int sample=1;for(int at=50;at<=3000;at+=50)t.observe(0,++sample,at,true);assertFalse(t.ready());t.touch(false);for(int at=3050;at<5050;at+=50)t.observe(0,++sample,at,true);assertFalse(t.ready());t.observe(0,++sample,5050,true);assertTrue(t.ready());}
    @Test public void consumedChangeCannotRetryWithoutAnotherLoadChange(){PotWeightTrigger t=new PotWeightTrigger();t.reference(0,1,0);for(int i=2;i<=42;i++)t.observe(1850,i,(i-1)*50,true);assertTrue(t.ready());t.consumed();t.reference(1850,43,2100);for(int i=44;i<150;i++)assertFalse(t.observe(1850,i,2100+(i-43)*50,true));assertFalse(t.ready());}
    @Test public void counterRollbackDropsTheOldReference(){PotWeightTrigger t=new PotWeightTrigger();t.reference(1850,90,1000);for(int i=1;i<60;i++)t.observe(0,i,1000+i*50,true);assertFalse(t.ready());}
}
