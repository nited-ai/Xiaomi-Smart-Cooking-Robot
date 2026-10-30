package com.kitchenos.connect.control;

/** Temporary setpoint headroom for a locally controlled heating run. */
final class HeatingBoost {
    private ControlValues requested;
    private long commandSequence;
    private boolean boosted,lowering,lowered;
    private long overLimitSince=-1;
    private long lastOverLimitAt=-1;
    private static final long OVER_LIMIT_MS=30000;
    private static final long MAX_SAMPLE_GAP_MS=1500;

    static int target(ControlValues values,int profile,int currentTemperature){
        if(!values.heatBoost||values.temperature<=0||values.lidless||values.turbo||currentTemperature>=values.temperature)return values.temperature;
        int maximum=profile==ControlProfile.MANUAL?150:profile==ControlProfile.NORMAL?100:values.temperature;
        int candidate=Math.min(maximum,(values.temperature*115+50)/100);
        return candidate>values.temperature&&ControlProfile.supportsIn(profile,values.seconds,candidate,values.speed)?candidate:values.temperature;
    }

    void sent(ControlValues values,int deviceTarget,long sequence){
        boolean sameTarget=requested!=null&&requested.temperature==values.temperature&&!values.waterHeating;
        requested=values.heatBoost&&!values.waterHeating?values:null;
        commandSequence=sequence;
        boosted=deviceTarget>values.temperature;
        lowering=lowered=false;
        if(!sameTarget)overLimitSince=lastOverLimitAt=-1;
    }

    boolean shouldLower(ControlState state,String event,long now){
        return requested!=null&&boosted&&!lowering&&!lowered&&event.equals("status")&&state.ownRun&&state.allows("APPLY_SETTINGS",now)
                &&state.acceptedCommandSequence>=commandSequence&&state.statusSequence>state.acceptedCommandStatusSequence
                &&state.temperature>=requested.temperature&&state.temperature<=250;
    }

    ControlValues requested(){return requested;}
    void lowering(ControlValues values){requested=values;boosted=false;lowering=true;lowered=false;}
    void lowered(){boosted=lowering=false;lowered=true;}
    boolean loweringInFlight(){return lowering;}
    boolean tooHot(ControlState state,String event,long now){
        if(!event.equals("status"))return false;
        if(requested==null||!state.ownRun||!state.fresh(now)||state.temperature<=requested.temperature+5){
            overLimitSince=lastOverLimitAt=-1;
            return false;
        }
        if(overLimitSince<0||now<lastOverLimitAt||now-lastOverLimitAt>MAX_SAMPLE_GAP_MS){overLimitSince=now;}
        lastOverLimitAt=now;
        return now-overLimitSince>OVER_LIMIT_MS;
    }
    void cancel(){requested=null;boosted=lowering=lowered=false;overLimitSince=lastOverLimitAt=-1;}
}
