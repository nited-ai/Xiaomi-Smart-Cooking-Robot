package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;

/** Local UI interlock; the root helper independently verifies current MCU idle. */
public final class StandbyPolicy {
    private StandbyPolicy() {}
    public static String refusal(ControlState state, boolean timerRunning) {
        if (timerRunning) return "Bitte zuerst den laufenden Timer beenden.";
        if (state.ownRun || state.stopping || !state.pendingAction.isEmpty())
            return "Standby ist während eines Kochlaufs nicht verfügbar.";
        if (state.uncertainRun || state.deviceFaultLatched || state.rootChecking)
            return "Bitte zuerst den Maschinenzustand prüfen.";
        if (state.busy()) return "Bitte zuerst die Maschinensteuerung trennen.";
        return "";
    }
}
