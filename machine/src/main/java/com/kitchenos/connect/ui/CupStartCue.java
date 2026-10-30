package com.kitchenos.connect.ui;
/** Three finite red pulses after START submission; visual only, no device authorization. */
public final class CupStartCue {
    public static final long DURATION_MS=840;
    public static float red(long now,long started){long elapsed=now-started;if(started<=0||elapsed<0||elapsed>=DURATION_MS)return 0;long phase=elapsed%280;return phase>=140?0:(float)(.9*Math.sin(Math.PI*phase/140));}
    private CupStartCue(){}
}
