package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;

/** Explicit local cup confirmation. Scale readings never establish cup placement. */
public final class HomeCupState {
    private boolean confirmed;
    // Last displayed seated pose. It never authorizes another start.
    private boolean startConfirmed;
    private long lastAt=-1;
    private int lastSample=-1;
    public void reset(){confirmed=startConfirmed=false;lastAt=-1;lastSample=-1;}
    public boolean inserted(){return confirmed;}
    /** Visual cue for an accepted local START, never evidence from a cup sensor. */
    public void indicateStart(){startConfirmed=true;}
    public boolean displayedInserted(){return confirmed||startConfirmed;}
    public boolean confirm(ControlState s,long now){
        observe(s,now);
        if(!usable(s,now)||s.coverOpen!=0||s.ownRun||s.stopping||!s.pendingAction.isEmpty())return false;
        confirmed=true;return true;
    }
    private boolean usable(ControlState s,long now){return s.foreground&&s.fresh(now)&&s.coverVerified&&s.coverOpen>=0&&!s.potMissing()&&s.error==0&&!s.deviceFaultLatched&&!s.uncertainRun;}
    public void observe(ControlState s,long now){
        boolean starting=s.pendingAction.equals("START"),owned=s.ownRun||starting;
        if(!s.foreground||!s.connected||s.closing||s.uncertainRun||s.deviceFaultLatched||s.potMissing()||s.error!=0||s.coverVerified&&s.coverOpen>0){reset();return;}
        if(lastAt>=0&&(s.samples<lastSample||s.statusAt<lastAt)){reset();return;}
        if(confirmed&&owned)startConfirmed=true;
        // Finishing a run does not remove the cup. Keep the seated image until
        // lid opening, lost context, a fault or an explicit reset invalidates it.
        // Keep the current-run photograph seated through a delayed UI sample,
        // while the authorization used by cupReady() still expires as before.
        if(!usable(s,now)){confirmed=false;return;}
        if(lastAt>=0&&s.statusAt-lastAt>=500)confirmed=false;
        if(s.coverOpen!=0)confirmed=false;
        lastAt=s.statusAt;lastSample=s.samples;
    }
}
