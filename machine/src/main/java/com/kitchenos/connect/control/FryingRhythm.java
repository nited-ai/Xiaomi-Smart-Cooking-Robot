package com.kitchenos.connect.control;

/** Motor times, not a claim about measured blade acceleration or browning. */
public enum FryingRhythm {
    SHORT("3 s rühren · 10 s Pause",3,10),
    GENTLE("5 s rühren · 25 s Pause",5,25),
    CONTINUOUS("Durchgehend rühren",0,0);

    public final String label;
    public final int stirSeconds,pauseSeconds;
    FryingRhythm(String label,int stirSeconds,int pauseSeconds){this.label=label;this.stirSeconds=stirSeconds;this.pauseSeconds=pauseSeconds;}
    public static FryingRhythm at(int index){return values()[Math.max(0,Math.min(values().length-1,index))];}
}
