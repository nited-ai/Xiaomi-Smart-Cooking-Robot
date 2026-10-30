package com.kitchenos.connect.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public final class HomeControlMathTest {
    @Test public void timeNeighborsWrapAcrossBothMinuteAndSecondSeams(){
        assertEquals(120,HomeControlMath.cyclicValue(-1,0,120));
        assertEquals(1,HomeControlMath.cyclicValue(1,0,120));
        assertEquals(119,HomeControlMath.cyclicValue(119,0,120));
        assertEquals(0,HomeControlMath.cyclicValue(121,0,120));
        assertEquals(59,HomeControlMath.cyclicValue(-1,0,59));
        assertEquals(0,HomeControlMath.cyclicValue(60,0,59));
        for(int position=-365;position<=365;position++)assertEquals(HomeControlMath.wheelIndex(0,120,position),HomeControlMath.cyclicValue(position,0,120));
    }
    @Test public void timeNeighborsRespectFunctionLimitsAndLockedSeconds(){
        assertEquals(30,HomeControlMath.cyclicValue(-1,0,30));
        assertEquals(0,HomeControlMath.cyclicValue(31,0,30));
        assertEquals(59,HomeControlMath.cyclicValue(19,20,59));
        assertEquals(20,HomeControlMath.cyclicValue(60,20,59));
        for(int position=-2;position<=2;position++)assertEquals(0,HomeControlMath.cyclicValue(position,0,0));
    }

    @Test public void zeroStopCatchesFastCrossingAndNeedsRealAdditionalTravel(){
        HomeControlMath.ZeroStopDrag drag=new HomeControlMath.ZeroStopDrag();drag.reset(1,-20,20,0);
        assertEquals(0,drag.move(-8,-90),0);assertTrue(drag.holding());
        for(int i=0;i<100;i++)assertEquals(0,drag.move(-8,-90),0);
        assertEquals(0,drag.move(-12,-114),0);assertTrue(drag.holding());
        assertEquals(0,drag.move(-16,-150),0);assertTrue(drag.holding());
        assertEquals(0,drag.move(-20,-165),0);assertTrue(drag.holding());
        assertEquals(-.1f,drag.move(-21,-172.5f),.00001f);assertFalse(drag.holding());
        assertEquals(-.2f,drag.move(-21.1f,-180),.00001f);
    }
    @Test public void zeroStopReversalAndFreshGestureStayFine(){
        HomeControlMath.ZeroStopDrag drag=new HomeControlMath.ZeroStopDrag();drag.reset(1,-20,20,0);
        drag.move(-2,-90);assertEquals(.1f,drag.move(-1.9f,-82.5f),.00001f);
        drag.reset(0,-20,20,0);assertEquals(-.1f,drag.move(-.1f,-7.5f),.00001f);assertFalse(drag.holding());
    }
    @Test public void zeroStopHandlesBothCyclicSeamsAndRepeatedTurns(){
        for(int sign:new int[]{-1,1}){
            HomeControlMath.ZeroStopDrag drag=new HomeControlMath.ZeroStopDrag();drag.reset(sign*59,-10000,10000,60);
            assertEquals(sign*60,drag.move(sign*5,sign*90),0);
            assertEquals(sign*60,drag.move(sign*6,sign*110),0);
            assertEquals(sign*61,drag.move(sign*7,sign*240),0);
            assertEquals(sign*120,drag.move(sign*100,sign*400),0);assertTrue(drag.holding());
        }
    }
    @Test public void zeroStopDoesNotChangeOrdinaryNumbersOrDisabledWheels(){
        HomeControlMath.ZeroStopDrag drag=new HomeControlMath.ZeroStopDrag();drag.reset(95,-10000,10000,121);
        assertEquals(115,drag.move(20,600),0);assertFalse(drag.holding());
        drag.reset(1,-20,20,-1);assertEquals(-7,drag.move(-8,-90),0);
    }
    @Test public void zeroStopClipsPredictionAndRestoresEndpointCorrection(){
        HomeControlMath.ZeroStopDrag drag=new HomeControlMath.ZeroStopDrag();drag.reset(1,-20,20,0);
        assertEquals(.1f,drag.move(-.9f,-67.5f),.00001f);assertEquals(0,drag.constrainVisual(-.06f),0);
        drag.move(-2,-90);assertEquals(0,drag.constrainVisual(-.16f),0);
        drag.reset(1,0,20,0);assertEquals(0,drag.move(-5,-90),0);
        assertEquals(.1f,drag.move(-4.9f,-82.5f),.00001f);
        drag.reset(19,0,20,0);assertEquals(20,drag.move(5,375),0);assertEquals(19.5f,drag.move(4.5f,337.5f),0);
    }
    @Test public void directLatchCentersSmallCorrectionsAndKeepsDirectTravelOutsideShoulders(){
        for(int center=-121;center<=121;center++){
            for(float delta:new float[]{-.05f,-.02f,0,.02f,.05f})assertEquals(center,HomeControlMath.directDetentPosition(center+delta),0);
            for(float delta:new float[]{-.49f,-.4f,-.29f,.29f,.4f,.49f})assertEquals(center+delta,HomeControlMath.directDetentPosition(center+delta),0);
        }
    }
    @Test public void directLatchNeverJumpsReversesOrChangesTheSelectedNumber(){
        float previous=HomeControlMath.directDetentPosition(-122);
        for(int n=-121999;n<=122000;n++){
            float position=n/1000f,shown=HomeControlMath.directDetentPosition(position);
            assertTrue(shown>=previous);assertTrue(shown-previous<.002f);
            assertTrue(Math.abs(shown-position)<.09f);
            assertEquals(Math.round(position),Math.round(shown));previous=shown;
        }
    }
    @Test public void directLatchHasSmoothSymmetricEntryAndExitInEitherDirection(){
        for(float p:new float[]{.06f,.1f,.2f,.28f,.4f,1.04f,1.2f,59.96f})assertEquals(-HomeControlMath.directDetentPosition(p),HomeControlMath.directDetentPosition(-p),.00001f);
        float epsilon=.0001f;
        for(float seam:new float[]{-.28f,-.06f,.06f,.28f}){
            float left=(HomeControlMath.directDetentPosition(seam)-HomeControlMath.directDetentPosition(seam-epsilon))/epsilon;
            float right=(HomeControlMath.directDetentPosition(seam+epsilon)-HomeControlMath.directDetentPosition(seam))/epsilon;
            assertEquals(left,right,.01f);
        }
    }
    @Test public void directFineTravelMatchesScreenDistanceAtEveryReportRate(){
        for(int interval:new int[]{5,10,12,20,40,100})for(float speed:new float[]{.02f,.1f,.25f}){
            HomeControlMath.WheelDrag drag=HomeControlMath.WheelDrag.direct();drag.reset(0);
            for(int t=interval;t<=2400;t+=interval){float distance=t*speed;assertEquals(distance,drag.move(distance,t)*HomeControlMath.DIRECT_ROW_DP,.01f);}
        }
    }
    @Test public void directReversalAndRegripDoNotCarryAccelerationOrDistance(){
        HomeControlMath.WheelDrag drag=HomeControlMath.WheelDrag.direct();drag.reset(0);float before=drag.move(300,100);
        assertEquals(-6,(drag.move(294,110)-before)*HomeControlMath.DIRECT_ROW_DP,.001f);
        drag.reset(200);assertEquals(3,drag.move(3,250)*HomeControlMath.DIRECT_ROW_DP,.001f);
    }
    @Test public void directEndStopDiscardsOutwardTravelForImmediateCorrection(){
        HomeControlMath.DirectDrag drag=new HomeControlMath.DirectDrag();drag.reset(0,-2,3);
        assertEquals(3,drag.move(20),0);assertEquals(2.5f,drag.move(19.5f),0);
        assertEquals(-2,drag.move(-20),0);assertEquals(-1.5f,drag.move(-19.5f),0);
    }
    @Test public void directFastSwipeReachesAbout115WhereSlowSwipeReaches103(){
        for(int interval:new int[]{5,10,20,40}){
            HomeControlMath.WheelDrag slow=HomeControlMath.WheelDrag.direct(),fast=HomeControlMath.WheelDrag.direct();slow.reset(0);fast.reset(0);
            float a=0,b=0;for(int t=interval;t<=2400;t+=interval)a=slow.move(600*t/2400f,t);
            for(int t=interval;t<=400;t+=interval)b=fast.move(600*t/400f,t);
            assertEquals(103,95+Math.round(a));assertTrue(95+Math.round(b)>=114&&95+Math.round(b)<=116);assertTrue(b<=a*2.75f);
        }
    }
    @Test public void directAccelerationBrakesWithoutRecalculatingEarlierTravel(){
        HomeControlMath.WheelDrag drag=HomeControlMath.WheelDrag.direct();drag.reset(0);float before=0;
        for(int t=40;t<=400;t+=40)before=drag.move(t*1.5f,t);
        float after=drag.move(606,440);assertEquals(6/75f,after-before,.0001f);
        for(int t=480;t<=800;t+=40)assertEquals(after,drag.move(606,t),0);
        float caught=drag.move(630,840);assertEquals(24/75f,caught-after,.0001f);
        float beforeReverse=drag.move(636,850);assertEquals(6/75f,beforeReverse-caught,.0001f);
        assertEquals(-3/75f,drag.move(633,860)-beforeReverse,.0001f);
    }
    @Test public void deliberateWheelsNeedThreeTimesTheSlowFingerTravel(){
        HomeControlMath.WheelDrag normal=new HomeControlMath.WheelDrag(),motor=HomeControlMath.WheelDrag.deliberate();normal.reset(0);motor.reset(0);
        float ordinary=0,deliberate=0;
        for(int t=10;t<=1500;t+=10){ordinary=normal.move(t*.2f,t);deliberate=motor.move(t*.2f,t);}
        assertEquals(ordinary/3,deliberate,.0001f);
        HomeControlMath.DetentDrag detents=new HomeControlMath.DetentDrag();detents.reset(0,-7,13);
        motor.reset(2000);detents.move(motor.move(30,2500));assertEquals(0,detents.center());
        detents.move(motor.move(76,3000));assertEquals(1,detents.center());
    }
    @Test public void fastDeliberateTravelStaysBoundedAndReversalReturnsToFineControl(){
        HomeControlMath.WheelDrag motor=HomeControlMath.WheelDrag.deliberate();motor.reset(0);float steps=0;
        for(int t=10;t<=200;t+=10)steps=motor.move(t*1.5f,t);
        assertTrue(steps>300/225f);assertTrue(steps<=2*300/225f);
        assertEquals(-6/225f,motor.move(294,205)-steps,.0001f);
    }
    @Test public void thirdStepPullPreviewsThenCommitsAndHoldsExactlyOneNumber(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(0,-100,100);
        drag.move(.2f);assertEquals(0,drag.center());assertEquals(.2f,drag.previewOffset(),.0001f);
        drag.move(.34f);assertEquals(1,drag.center());assertEquals(0,drag.previewOffset(),0);
        for(int i=0;i<500;i++)drag.move(.34f);assertEquals(1,drag.center());assertEquals(0,drag.previewOffset(),0);
    }
    @Test public void furtherDetentsRequireFurtherActualFingerTravel(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(0,-100,100);
        drag.move(.34f);drag.move(.65f);assertEquals(1,drag.center());
        drag.move(.68f);assertEquals(2,drag.center());drag.move(1.01f);assertEquals(3,drag.center());
    }
    @Test public void autonomousSettleCentersWithoutLosingDistanceOrInventingMovement(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(0,-100,100);
        drag.move(.34f);drag.move(.4f);drag.settled();assertEquals(0,drag.previewOffset(),0);
        drag.move(.4f);assertEquals(0,drag.previewOffset(),0);assertEquals(1,drag.center());
        drag.move(.41f);assertEquals(.01f,drag.previewOffset(),.00001f);drag.move(.68f);assertEquals(2,drag.center());
    }
    @Test public void reversalDoesNotToggleOnSmallJitterButAllowsOneNumberCorrection(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(4,-100,100);drag.move(.34f);
        for(int i=0;i<50;i++){drag.move(.32f);drag.move(.34f);}assertEquals(5,drag.center());
        drag.move(-.01f);assertEquals(4,drag.center());assertEquals(0,drag.previewOffset(),0);
    }
    @Test public void snapEndStopsDiscardOverscrollAndAllowImmediateReversal(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(0,-2,3);
        drag.move(10);assertEquals(3,drag.center());assertEquals(0,drag.previewOffset(),0);
        drag.move(9.65f);assertEquals(2,drag.center());drag.move(-10);assertEquals(-2,drag.center());
        drag.move(-9.65f);assertEquals(-1,drag.center());
    }
    @Test public void snapIsSymmetricAndIndependentOfReportDensity(){
        for(int sign:new int[]{-1,1}){
            HomeControlMath.DetentDrag sparse=new HomeControlMath.DetentDrag(),dense=new HomeControlMath.DetentDrag();sparse.reset(0,-100,100);dense.reset(0,-100,100);
            sparse.move(sign*3.05f);for(int i=1;i<=305;i++)dense.move(sign*i/100f);
            assertEquals(sign*9,sparse.center());assertEquals(sparse.center(),dense.center());
        }
    }
    @Test public void snapWrapAndRegripStartFromTheChosenNumber(){
        HomeControlMath.DetentDrag drag=new HomeControlMath.DetentDrag();drag.reset(59,-10000,10000);drag.move(.34f);
        assertEquals(0,Math.floorMod(drag.center(),60));drag.reset(0,-10000,10000);drag.move(-.34f);
        assertEquals(59,Math.floorMod(drag.center(),60));
    }
    @Test public void beginningWrapsBackToEndInOneTick(){for(int max:new int[]{120,59,30})assertEquals(max,HomeControlMath.dragIndex(0,max,-75));}
    @Test public void endWrapsForwardToBeginning(){for(int max:new int[]{120,59,30})assertEquals(0,HomeControlMath.dragIndex(max,max,75));}
    @Test public void regrippingContinuesFromTheSelectedValue(){for(int max:new int[]{120,59,30}){int index=0;for(int tick=1;tick<=max+1;tick++){index=HomeControlMath.dragIndex(index,max,75);assertEquals(tick%(max+1),index);}}}
    @Test public void briskDragStaysPreciseWithoutLateAcceleration(){for(int max:new int[]{120,59,30}){assertEquals(0,HomeControlMath.dragStrength(max,25));assertEquals(0,HomeControlMath.dragStrength(max,74));assertEquals(1,HomeControlMath.dragStrength(max,75));assertEquals(4,HomeControlMath.dragStrength(max,300));assertEquals(6,HomeControlMath.dragStrength(max,450));assertEquals(7,HomeControlMath.dragIndex(10,max,-225));}}
    @Test public void speedRangeRemainsReachableOnEitherSide(){for(int max:new int[]{6,7,13})for(int room=80;room<=1000;room++){float previous=-1;for(int distance=0;distance<=room;distance++){float position=HomeControlMath.speedPosition(max,distance,room);assertTrue(position>=previous);assertTrue(position<=max);previous=position;}assertEquals(max,(int)previous);}}
    @Test public void onlyLatestTimeMilestoneIsSelected(){assertEquals(-1,HomeControlMath.reachedIncrement(0));assertEquals(10,HomeControlMath.reachedIncrement(19));assertEquals(20,HomeControlMath.reachedIncrement(21));assertEquals(30,HomeControlMath.reachedIncrement(120));}
    @Test public void wheelMovesBeforeFirstDetent(){for(int max:new int[]{6,30,59,120}){float previous=0;for(int distance=1;distance<75;distance++){float position=HomeControlMath.dragPosition(max,distance);assertTrue(position>previous);assertEquals(0,HomeControlMath.dragStrength(max,distance));previous=position;}}}
    @Test public void drawingAndSelectedDetentShareTheSameTravel(){for(int max:new int[]{0,6,30,59,120})for(int distance=0;distance<=4600;distance++){float position=HomeControlMath.dragPosition(max,distance/10f);assertTrue(Float.isFinite(position));assertTrue(position>=0&&position<=max);assertEquals((int)position,HomeControlMath.dragStrength(max,distance/10f));}}
    @Test public void cyclicSnapStaysNearFingerAcrossSeams(){assertEquals(-1,HomeControlMath.nearestPosition(-1.35f,59,60),.001f);assertEquals(60,HomeControlMath.nearestPosition(60.3f,0,60),.001f);assertEquals(121,HomeControlMath.nearestPosition(121.4f,0,121),.001f);assertEquals(0,HomeControlMath.nearestPosition(.4f,0,1),.001f);}
    private float drag(float distance,int duration,int interval){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);float steps=0;for(int t=interval;t<=duration;t+=interval)steps=drag.move(distance*t/duration,t);return steps;}
    @Test public void slowFingerTravelKeepsFineSensitivity(){assertEquals(4,drag(300,1500,10),.001f);assertEquals(-4,drag(-300,1500,10),.001f);}
    @Test public void fastFingerCrossesMoreNumbersOverSameDistance(){float slow=drag(300,1500,10),fast=drag(300,200,10);assertTrue(fast>slow*3);assertTrue(fast<=slow*6);assertEquals(-fast,drag(-300,200,10),.001f);}
    @Test public void changingSpeedNeverRecalculatesEarlierTravel(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);float before=0;for(int t=10;t<=1500;t+=10)before=drag.move(t*.2f,t);float after=drag.move(305,1505);assertTrue(after>before);assertTrue(after-before<=5*6/75f);}
    @Test public void slowingImmediatelyRestoresFineSteps(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);float before=0;for(int t=10;t<=200;t+=10)before=drag.move(t*1.5f,t);assertEquals(6/75f,drag.move(306,250)-before,.001f);}
    @Test public void reversingDoesNotCarryFastGainIntoCorrection(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);float before=0;for(int t=10;t<=200;t+=10)before=drag.move(t*1.5f,t);assertEquals(-6/75f,drag.move(294,205)-before,.001f);}
    @Test public void aHoldAndItsFirstCatchupDoNotInventFastMotion(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);float held=drag.move(15,100);for(int t=110;t<=350;t+=10)assertEquals(held,drag.move(15,t),0);assertEquals(30/75f,drag.move(45,360)-held,.001f);}
    @Test public void catchupBurstAfterChipHoldStaysFine(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);drag.move(15,100);float previous=drag.move(45,350);for(int t=360;t<=500;t+=10){float next=drag.move(45+t-350,t);assertEquals(10/75f,next-previous,.001f);previous=next;}float end=previous;for(int t=510;t<=750;t+=10)end=drag.move(45+t-350,t);assertTrue(end-previous>250/75f*2);}
    @Test public void regrippingClearsAccelerationAndPastTravel(){HomeControlMath.WheelDrag drag=new HomeControlMath.WheelDrag();drag.reset(0);for(int t=10;t<=200;t+=10)drag.move(t*1.5f,t);drag.reset(1000);assertEquals(1,drag.move(75,1500),.001f);assertEquals(1,drag.move(75,2000),0);}
    @Test public void selectedNumberIsNearestVisibleNumberInEitherDirection(){for(int maximum:new int[]{0,30,59,120})for(int start:new int[]{0,maximum})for(int n=-2000;n<=2000;n++){float steps=n/10f;int selected=HomeControlMath.wheelIndex(start,maximum,steps);assertTrue(selected>=0&&selected<=maximum);if(maximum>0)assertEquals(start+steps,HomeControlMath.nearestPosition(start+steps,selected,maximum+1),.5001f);}}
    @Test public void sparseAndDenseMotionReportsHaveComparableAcceleration(){assertEquals(drag(300,200,5),drag(300,200,20),1.1f);}
    @Test public void softDetentsKeepMovingAcrossBothDirectionsAndWrapSeams(){
        float previous=HomeControlMath.detentPosition(-122);
        for(int n=-121999;n<=122000;n++){
            float position=n/1000f,shown=HomeControlMath.detentPosition(position);
            assertTrue(shown>previous);assertTrue(shown-previous<.002f);
            assertEquals(Math.round(position),Math.round(shown));previous=shown;
        }
    }
    @Test public void softDetentsRetainCentersAndSymmetricFineCorrection(){
        for(int n=-121;n<=121;n++){
            assertEquals(n,HomeControlMath.detentPosition(n),0);
            assertEquals(n+.5f,HomeControlMath.detentPosition(n+.5f),.00001f);
            float below=n-HomeControlMath.detentPosition(n-.1f),above=HomeControlMath.detentPosition(n+.1f)-n;
            assertTrue(above>.02f&&above<.06f);assertEquals(below,above,.00002f);
        }
    }
}
