package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;
import com.kitchenos.connect.control.ScaleReadingTracker;

/** Home-only scale filtering. Touch pressure never becomes a measurement or tare. */
public final class HomeScaleState {
    private final ScaleReadingTracker readings=new ScaleReadingTracker();
    private long settleUntil;private boolean touching,pendingTare;private Integer shown,trustedShown;
    public void reset(){readings.reset();shown=trustedShown=null;pendingTare=touching=false;settleUntil=0;}
    public void touch(boolean down,long now){if(down&&shown!=null&&trustedShown!=null)shown=trustedShown;touching=down;settleUntil=now+800;readings.invalidateSamples();}
    public void tare(){pendingTare=true;}
    public boolean pendingTare(){return pendingTare;}
    public boolean settling(long now){return touching||now<settleUntil;}
    public boolean stable(long now){return !settling(now)&&readings.stable(now);}
    public String text(){return shown==null?"— g":shown+" g";}
    public Integer grams(){return shown;}
    public void observe(ControlState s,long now){
        if(!s.foreground){reset();return;}
        // A reconnect during a held touch must not admit the finger's pressure.
        if(!s.connected||s.closing){readings.reset();shown=null;pendingTare=false;return;}
        if(s.potMissing()){readings.reset();shown=null;pendingTare=false;return;}
        // A heavy ingredient can request the same diagnostic. Preserve tare
        // until a missing-vessel result actually confirms removal.
        if(s.potCheck.required()){readings.invalidateSamples();shown=null;pendingTare=false;return;}
        if(s.ownRun||s.stopping||!s.pendingAction.isEmpty()){readings.invalidateSamples();pendingTare=false;return;}
        if(s.error!=0||s.uncertainRun||s.samples<3||(s.state!=0&&s.state!=11)||Math.abs((long)s.weightRaw)>5000){readings.reset();shown=null;pendingTare=false;return;}
        if(s.statusAt<0||now<s.statusAt||now-s.statusAt>=500){readings.observe(s,now);shown=null;return;}
        if(settling(now))return;
        readings.observe(s,now);
        if(!readings.hasTare())shown=null;
        if(readings.stable(now)){
            if(pendingTare||!readings.hasTare()){
                if(readings.tare(now)){pendingTare=false;shown=trustedShown=0;}
            }
        }
        // Touch suppression and stable zeroing stay independent of the live display.
        if(readings.hasTare()){shown=readings.displayedGrams();if(readings.stable(now))trustedShown=readings.stableGrams();}
    }
}
