package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;

/** A knob press may prepare a read-only link, but never arm a later start. */
final class HomeStartGate {
    enum Action { ROOT, CONNECT, WAIT, READY, BLOCKED }
    static Action next(ControlState s,long now,boolean lidless){
        if(!s.foreground||s.ownRun||s.uncertainRun||s.deviceFaultLatched||s.potCheck.required()||s.potCheck.active()||s.stopping||!s.pendingAction.isEmpty())return Action.BLOCKED;
        if(s.rootChecking||s.connecting||s.closing)return Action.WAIT;
        if(!s.rootVerified)return Action.ROOT;
        if(!s.connected)return Action.CONNECT;
        return s.allows("START",now,lidless)?Action.READY:Action.BLOCKED;
    }
    private HomeStartGate(){}
}
