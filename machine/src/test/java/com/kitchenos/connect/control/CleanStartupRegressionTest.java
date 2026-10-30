package com.kitchenos.connect.control;

import org.junit.Test;
import static org.junit.Assert.*;

/** Replays the two accepted pre-clean starts recorded on 22 October at 19:57. */
public class CleanStartupRegressionTest {
    @Test public void acceptedCleanStartSurvivesRecordedStatusGaps() {
        for (int[] age : new int[][]{{575,528},{569,691}}) {
            FunctionProgramTest.Rig r = new FunctionProgramTest.Rig();
            r.s.temperature = 45;
            r.start("clean",120,45);
            r.ack();
            // ACK arrived while the MCU still reported idle, before motor startup.
            r.s.state = 0;
            r.s.motorStatus = 0;
            r.now += 800;
            r.s.statusAt = r.now-age[0];
            r.s.infoAt = r.now-age[1];
            r.runner.tick(r.now);
            assertTrue(r.runner.active());
            assertEquals(0,r.stops);
            assertEquals(1,r.starts.size());
            for (int i=0;i<14;i++) {
                if(i>0)r.ack();
                r.tick(r.starts.get(i).seconds*1000L);
                r.complete();
                r.tick(1);
            }
            assertFalse(r.runner.active());
            assertEquals("Programm beendet",r.runner.status());
            assertEquals(14,r.starts.size());
            assertEquals(0,r.stops);
        }
    }
}
