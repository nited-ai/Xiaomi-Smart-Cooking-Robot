package com.kitchenos.connect.ui;

/** Pure navigation/timer decisions; observing these never connects to the machine. */
public final class CookingNavigation {
    private CookingNavigation() {}
    public static boolean requiresInterruption(boolean ownRun,String pendingAction,boolean uncertainRun){
        return ownRun||"START".equals(pendingAction)||uncertainRun;
    }
    public static boolean sameTimer(String timerKey,String currentStepKey){
        return timerKey!=null&&!timerKey.isEmpty()&&timerKey.equals(currentStepKey);
    }
}
