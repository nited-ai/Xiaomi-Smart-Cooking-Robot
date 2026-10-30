package com.kitchenos.connect.ui;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public final class VesselAccessoryTest {
    @After public void clear(){AccessorySelection.function(null,"manual");}
    @Test public void selectedToolsRemainDistinct(){
        assertEquals("blade",AccessorySelection.visualTool("manual",false));
        assertEquals("whisk",AccessorySelection.visualTool("manual",true));
        assertEquals("cover",AccessorySelection.visualTool("stir",false));
        assertEquals("whisk",AccessorySelection.visualTool("whip",false));
    }
    @Test public void coverPresetAndCommandsStayInsideDocumentedLimits(){
        FunctionSetup setup=FunctionSetup.get("stir");assertEquals(10,setup.speed);assertEquals(10,setup.maxSpeed);assertEquals(100,setup.maxTemperature);
        AccessorySelection.function(null,"stir");
        assertEquals("",AccessorySelection.issue(null,-10,100));
        assertEquals(AccessorySelection.COVER_LIMIT,AccessorySelection.issue(null,-15,100));
        assertEquals(AccessorySelection.COVER_LIMIT,AccessorySelection.issue(null,-10,105));
    }
    @Test public void whiskAndBareBladeKeepTheirOwnSpeedLimits(){
        AccessorySelection.function(null,"whip");assertEquals("",AccessorySelection.issue(null,40,0));assertFalse(AccessorySelection.issue(null,45,0).isEmpty());
        AccessorySelection.function(null,"manual");assertEquals("",AccessorySelection.issue(null,-40,0));
    }
}
