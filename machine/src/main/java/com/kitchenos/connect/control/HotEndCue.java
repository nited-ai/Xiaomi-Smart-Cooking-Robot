package com.kitchenos.connect.control;

/** One audible reminder per accepted cooking run, never per interval or repaint. */
public final class HotEndCue {
    private int starts;
    private boolean armed;
    public boolean observe(int acceptedStarts,boolean programActive,boolean confirmedIdle,int temperature,boolean fresh){
        if(acceptedStarts<starts)armed=false;
        if(acceptedStarts>starts)armed=true;
        starts=acceptedStarts;
        if(!armed||programActive||!confirmedIdle)return false;
        armed=false;
        return fresh&&temperature>150;
    }
}
