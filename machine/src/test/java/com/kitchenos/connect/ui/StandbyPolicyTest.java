package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;
import org.junit.Test;
import static org.junit.Assert.*;

public final class StandbyPolicyTest {
    @Test public void idleDisconnectedPermitsIndependentHardwarePreflight() {
        assertEquals("", StandbyPolicy.refusal(new ControlState(), false));
    }
    @Test public void backgroundTimerPreventsSleepEvenOnHome() {
        assertFalse(StandbyPolicy.refusal(new ControlState(), true).isEmpty());
    }
    @Test public void allLiveOrUncertainControllerStatesPreventSleep() throws Exception {
        for (String flag : new String[]{"ownRun", "stopping", "uncertainRun",
                "deviceFaultLatched", "rootChecking", "connecting", "connected", "closing"}) {
            ControlState state = new ControlState();
            ControlState.class.getField(flag).setBoolean(state, true);
            assertFalse(flag, StandbyPolicy.refusal(state, false).isEmpty());
        }
        for (String action : new String[]{"START", "PAUSE", "RESUME", "APPLY", "STOP"}) {
            ControlState state = new ControlState();
            state.pendingAction = action;
            assertFalse(action, StandbyPolicy.refusal(state, false).isEmpty());
        }
    }
}
