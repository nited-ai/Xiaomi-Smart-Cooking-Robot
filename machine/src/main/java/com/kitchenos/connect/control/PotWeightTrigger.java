package com.kitchenos.connect.control;

/** Fresh raw-weight change is a diagnostic request, never a presence result. */
public final class PotWeightTrigger {
    // User measured ~1850g bare / ~2147g with lid and cup. Lid-only changes
    // (~280-300g) stay below this threshold; a filled vessel also qualifies.
    public static final int CHANGE_GRAMS=1700,SETTLE_MS=2000;
    private int sample=-1,anchor,min,max;
    private long at=-1,stableAt=-1;
    private boolean baseline,candidate,ready,touching,settled;
    public void reset(){sample=-1;at=stableAt=-1;baseline=candidate=ready=settled=false;}
    /** Keep only the stable reference across a deliberate display/connection pause.
     * Old samples and quiet time never count toward the new observation window. */
    public void pause(){sample=-1;at=stableAt=-1;candidate=ready=settled=false;}
    public boolean ready(){return ready;}
    public boolean settled(){return settled;}
    public void consumed(){reset();}
    public void touch(boolean down){touching=down;stableAt=-1;ready=settled=false;}
    /** A completed stopped diagnostic supplies the reference for its next change. */
    public void reference(int grams,int sequence,long time){
        reset();if(time<0||Math.abs((long)grams)>5000)return;
        anchor=min=max=grams;sample=sequence;at=stableAt=time;baseline=true;
    }
    /** Returns true once per stable change; duplicate samples cannot advance time. */
    public boolean observe(int grams,int sequence,long time,boolean eligible){
        // Missing telemetry is an observation gap, not evidence of a new load.
        // Preserve the reference, but require an entirely new quiet window.
        if(!eligible){pause();return false;}
        if(time<0||Math.abs((long)grams)>5000||at>=0&&time<at){reset();return false;}
        if(sequence==sample)return false;
        if(sequence<sample)reset();
        else if(at>=0&&time-at>=500)pause();
        sample=sequence;boolean wasReady=ready;
        if(stableAt<0||grams<min-15||grams>max+15){min=max=grams;stableAt=time;ready=false;}
        else{min=Math.min(min,grams);max=Math.max(max,grams);if(max-min>20){min=max=grams;stableAt=time;ready=false;}}
        candidate=baseline&&Math.abs((long)anchor-grams)>=CHANGE_GRAMS;
        if(!candidate)ready=false;
        if(touching){stableAt=-1;ready=false;}
        else if(candidate&&time-stableAt>=SETTLE_MS)ready=true;
        else if(!candidate&&time-stableAt>=(baseline?SETTLE_MS:800)){anchor=grams;baseline=true;}
        settled=!touching&&stableAt>=0&&time-stableAt>=SETTLE_MS;
        at=time;return ready&&!wasReady;
    }
}
