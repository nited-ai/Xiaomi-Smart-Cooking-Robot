package com.kitchenos.connect.ui;

/** Measured finger travel only. A brief inward flick need not cross a quarter screen. */
public final class RootSwipeDecision {
    private RootSwipeDecision() {}
    /** Sparse edge events can make Android's terminal VelocityTracker report zero.
     * Use actual end-to-end travel for brief gestures; reject a measured reversal. */
    public static float flickVelocity(float offset,float excursion,long elapsed,float density){
        if(elapsed<=0||elapsed>250||density<=0||excursion-Math.abs(offset)>12*density)return 0;
        return offset*1000f/elapsed;
    }
    public static boolean accept(float offset,int width,int direction,float velocity,float density){
        if(width<=0||density<=0||offset*direction<=0)return false;
        return Math.abs(offset)>width*.18f ||
            Math.abs(offset)>=80*density && velocity*direction>=650*density;
    }
}
