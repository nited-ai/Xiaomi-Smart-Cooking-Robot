package com.kitchenos.connect.ui;

/** Shared wall clock phase for the native question and the Canvas measuring cup.
 * Sampling locally avoids sending animation frames over the WebView bridge. */
public final class HomeCupPulse {
    public static float alpha(long wallMillis) {
        return (float)(.60 + .40 * Math.cos(Math.floorMod(wallMillis, 1800L) * Math.PI / 900));
    }
    private HomeCupPulse() {}
}
