package com.kitchenos.connect.control;

/** One explicit local START owns a fixed, profile-specific budget, including pauses.
 * APPLY and RESUME may change a phase, but cannot silently renew this budget. */
public final class RunBudget {
    public static final long STOP_RESERVE_MILLIS=3000,MAX_MILLIS=ControlProfile.MAX_SECONDS*1000L+STOP_RESERVE_MILLIS;
    private long deadline=-1;
    public void begin(long now){deadline=now+MAX_MILLIS;}
    // Turbo's extra allowance is for zero-output Strong preparation only.
    // The native pulse watchdog independently stops after at most two seconds.
    public void begin(long now,int profile){int seconds=ControlProfile.maxSeconds(profile);if(seconds<1)throw new IllegalArgumentException("Unbekanntes Kochprofil");deadline=now+seconds*1000L+STOP_RESERVE_MILLIS+(profile==ControlProfile.TURBO?10000:0);}
    public long remaining(long now){return deadline<0?0:Math.max(0,deadline-now);}
    public boolean permits(int seconds,long now){return deadline>=0&&seconds>0&&seconds*1000L+STOP_RESERVE_MILLIS<=remaining(now);}
    public boolean expired(long now){return deadline>=0&&now>=deadline;}
}
