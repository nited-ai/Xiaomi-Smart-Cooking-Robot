package com.kitchenos.connect.ui;
import org.junit.Test;
import static org.junit.Assert.*;

public class FunctionShortcutsTest {
    private int[] choices(String id,int kind,int part,int min,int max){return FunctionShortcuts.choices(HomeFunctionProfile.get(id),kind,part,min,max,v->v>=min&&v<=max&&(kind!=2||v%10==0||v<=30&&v%5==0));}
    @Test public void pureeMinutesRespectFiveMinuteLimit(){assertArrayEquals(new int[]{1,5},choices("puree",0,0,1,300));assertArrayEquals(new int[]{1,5,10,20,30},choices("puree",0,1,1,300));}
    @Test public void narrowSpeedRangesKeepIntermediateAndHalfStages(){assertArrayEquals(new int[]{60,70,80,90,100},choices("puree",2,0,60,100));assertArrayEquals(new int[]{5,10,15},choices("warm",2,0,5,15));assertArrayEquals(new int[]{5,10},choices("stir",2,0,5,10));assertArrayEquals(new int[]{100,110,120},choices("turbo",2,0,100,120));}
    @Test public void shortFunctionsDoNotOfferLongTimeIncrements(){assertArrayEquals(new int[]{1},choices("chop",0,0,1,60));assertArrayEquals(new int[]{1,2},choices("turbo",0,1,1,2));}
    @Test public void temperaturesStayInFunctionWithoutFryingEscape(){assertArrayEquals(new int[]{35,40,60,80,90},choices("warm",1,0,35,90));assertArrayEquals(new int[]{37,60,80,90,98},choices("slow",1,0,37,98));assertArrayEquals(new int[]{0,40,60,80,100},choices("stir",1,0,0,100));}
    @Test public void onlyIndependentControlsRemain(){
        assertTrue(HomeFunctionProfile.get("puree").timeControl());assertFalse(HomeFunctionProfile.get("puree").speedControl());
        for(String id:new String[]{"dough","grind","ice","steam","eggs"}){HomeFunctionProfile p=HomeFunctionProfile.get(id);assertTrue(id,p.timeControl());assertFalse(id,p.temperatureControl());assertFalse(id,p.speedControl());}
        for(String id:new String[]{"mix","whip","emulsify","chop","turbo"}){HomeFunctionProfile p=HomeFunctionProfile.get(id);assertTrue(id,p.timeControl());assertFalse(id,p.temperatureControl());assertTrue(id,p.speedControl());}
        for(String id:new String[]{"scale","caramel","ferment","sousvide"}){HomeFunctionProfile p=HomeFunctionProfile.get(id);assertFalse(id,p.timeControl());assertFalse(id,p.temperatureControl());assertFalse(id,p.speedControl());}
        assertFalse(HomeFunctionProfile.get("stir").intervals(true).speedControl());
    }
}
