package com.kitchenos.connect.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeWeightVisualsTest {
 @Test public void fillStartsAt25GramsWithoutSubtractingTheThreshold(){for(int value:new int[]{-300,0,1,24})assertEquals(0,HomeWeightVisuals.fillFraction(value),0);assertEquals(.0125f,HomeWeightVisuals.fillFraction(25),.000001f);assertEquals(.15f,HomeWeightVisuals.fillFraction(300),.000001f);}

 @Test public void liveFillPreservesExactWeightAndClampsOnlyTheIllustration(){assertEquals(-1,HomeWeightVisuals.fillFraction(null),0);assertEquals(0,HomeWeightVisuals.fillFraction(-100),0);assertEquals(.0495f,HomeWeightVisuals.fillFraction(99),.00001f);assertEquals(.125f,HomeWeightVisuals.fillFraction(250),.00001f);assertEquals(.5f,HomeWeightVisuals.fillFraction(1000),0);assertEquals(1,HomeWeightVisuals.fillFraction(2000),0);assertEquals(1,HomeWeightVisuals.fillFraction(3500),0);}
 @Test public void dialDoublesItsSensitivityAndClampsOnlyThePicture(){assertEquals(.2f,HomeWeightVisuals.dialFraction(500),.0001f);assertEquals(.8f,HomeWeightVisuals.dialFraction(2000),.0001f);assertEquals(1f,HomeWeightVisuals.dialFraction(2700),0f);assertEquals(0f,HomeWeightVisuals.dialFraction(-100),0f);}
 @Test public void decorativeMassUsesTwentyStepsWithARealHundredGramThreshold(){assertEquals(-1,HomeWeightVisuals.fillStep(null));assertEquals(0,HomeWeightVisuals.fillStep(-100));assertEquals(0,HomeWeightVisuals.fillStep(99));assertEquals(1,HomeWeightVisuals.fillStep(100));assertEquals(2,HomeWeightVisuals.fillStep(200));assertEquals(10,HomeWeightVisuals.fillStep(1000));assertEquals(20,HomeWeightVisuals.fillStep(2000));assertEquals(20,HomeWeightVisuals.fillStep(3500));}
 @Test public void dialLandmarksKeepTheirInclusiveTenGramBand(){
  for(int value:new int[]{-10,0,10,490,500,510,990,1000,1010,1490,1510,1990,2010,2490,2500,2510})assertTrue("Emphasize "+value,HomeWeightVisuals.nearDialLandmark(value));
  for(int value:new int[]{-11,11,489,511,989,1011,1234,2489,2511})assertFalse("Normal size "+value,HomeWeightVisuals.nearDialLandmark(value));
 }
}
