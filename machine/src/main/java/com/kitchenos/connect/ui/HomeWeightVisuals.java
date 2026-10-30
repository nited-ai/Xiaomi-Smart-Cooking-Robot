package com.kitchenos.connect.ui;

/** Presentation assumptions only; never a capacity limit or a volume measurement. */
public final class HomeWeightVisuals {
    public static final int DIAL_MAX_GRAMS=2500,DECORATIVE_FULL_GRAMS=2000,FILL_STEP_GRAMS=100,FILL_MIN_GRAMS=25;
    public static float dialFraction(int grams){return Math.max(0,Math.min(DIAL_MAX_GRAMS,grams))/(float)DIAL_MAX_GRAMS;}
    /** Keep a 500-g landmark emphasized throughout its inclusive ±10-g band. */
    public static boolean nearDialLandmark(int grams){int remainder=Math.floorMod(grams,500);return remainder<=10||remainder>=490;}
    public static int fillStep(Integer grams){return grams==null?-1:Math.max(0,Math.min(20,grams/FILL_STEP_GRAMS));}
    public static float fillFraction(Integer grams){return grams==null?-1:grams<FILL_MIN_GRAMS?0:Math.min(DECORATIVE_FULL_GRAMS,grams)/(float)DECORATIVE_FULL_GRAMS;}
    private HomeWeightVisuals(){}
}
