package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class ControlTelemetryTest {
    @Test public void correlatesHeaterAndStoppedMotorWithoutCallingItMeasuredPower(){
        String raw="0101803264000001010000000000fffe0000000900000001";
        String line=ControlTelemetry.frame("status",raw,1234,100,-10);
        assertTrue(line.contains("\"receivedElapsedMs\":1234"));
        assertTrue(line.contains("\"temperatureRaw\":100"));
        assertTrue(line.contains("\"ihControlRaw\":128"));
        assertTrue(line.contains("\"ihPowerRaw\":50"));
        assertTrue(line.contains("\"motorRaw\":0"));
        assertTrue(line.contains("\"requestedSpeed\":-10"));
        assertFalse(line.contains("watts"));
    }
    @Test public void preservesRawMotorControlByteWithoutInventingAmpsOrViscosity(){
        String line=ControlTelemetry.frame("info","0c0401050102030405060708090a0b0c0d0e8000",456,0,10);
        assertTrue(line.contains("\"motorControlRaw\":7"));
        assertTrue(line.contains("\"ihCurrentRaw\":2"));
        assertFalse(line.contains("motorCurrent"));
        assertFalse(line.contains("viscosity"));
    }
    @Test public void rejectsIncompleteNonHexAndUnknownFrames(){
        assertNull(ControlTelemetry.frame("info","00",0,0,0));
        assertNull(ControlTelemetry.frame("status","zz0000000000000000000000000000000000000000000000",0,0,0));
        assertNull(ControlTelemetry.frame("ack","",0,0,0));
        assertNull(ControlTelemetry.frame("info",null,0,0,0));
    }
    @Test public void retainsUnchangedSamplesAndMotorTransitions(){
        String moving="010180326400010101000000100000000000000900000001";
        String stopped="010180326400000101000000000000000000000900000001";
        assertTrue(ControlTelemetry.frame("status",moving,1,100,-10).contains("\"motorRaw\":16"));
        assertTrue(ControlTelemetry.frame("status",stopped,2,100,-10).contains("\"motorRaw\":0"));
        assertNotEquals(ControlTelemetry.frame("status",stopped,2,100,-10),ControlTelemetry.frame("status",stopped,3,100,-10));
    }
}
