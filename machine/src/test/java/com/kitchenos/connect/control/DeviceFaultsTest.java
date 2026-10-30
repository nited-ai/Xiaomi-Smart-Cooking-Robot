package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceFaultsTest {
    @Test public void distinguishesOvercurrentFromBlockedBlade(){
        assertTrue(DeviceFaults.describe(16).contains("Motorüberstrom"));
        assertTrue(DeviceFaults.describe(20).contains("Motor blockiert"));
        assertTrue(DeviceFaults.describe(20).contains("ausschalten"));
    }
    @Test public void unknownCodesRemainVisible(){
        for(int code:new int[]{6,9,30,255})assertTrue(DeviceFaults.describe(code).contains("Gerätefehler "+code));
    }
    @Test public void avoidsContradictoryOemSensorAndStepperLabels(){
        for(int code:new int[]{22,23,25,26}){
            assertTrue(DeviceFaults.describe(code).contains("Temperatursensor"));
            assertFalse(DeviceFaults.describe(code).contains("Kurzschluss"));
        }
        assertFalse(DeviceFaults.describe(28).contains("Schrittmotor"));
        assertTrue(DeviceFaults.describe(254).contains("Kommunikation"));
    }
}
