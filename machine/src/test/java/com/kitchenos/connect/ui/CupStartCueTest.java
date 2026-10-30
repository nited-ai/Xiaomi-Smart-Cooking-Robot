package com.kitchenos.connect.ui;
import org.junit.Test;import static org.junit.Assert.*;
public class CupStartCueTest {
 @Test public void threeSeparatedRedPeaks(){long at=1000;for(int n=0;n<3;n++){assertEquals(.9f,CupStartCue.red(at+70+n*280,at),.0001f);assertEquals(0,CupStartCue.red(at+210+n*280,at),0);}}
 @Test public void noPulseBeforeOrAfterFiniteWindow(){assertEquals(0,CupStartCue.red(999,1000),0);assertEquals(0,CupStartCue.red(1840,1000),0);assertEquals(0,CupStartCue.red(100000,1000),0);assertEquals(0,CupStartCue.red(1070,0),0);}
 @Test public void boundedSmoothPulse(){for(int t=0;t<=900;t++){float v=CupStartCue.red(1000+t,1000);assertTrue(v>=0&&v<=.9f);}}
}
