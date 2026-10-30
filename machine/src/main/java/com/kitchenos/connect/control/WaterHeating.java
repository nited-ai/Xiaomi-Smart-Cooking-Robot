package com.kitchenos.connect.control;

/** Local water completion; the native ten-minute budget and watchdogs remain active. */
public final class WaterHeating {
    static final long HOLD_MS=20000;
    private static final long LOWER_ACK_TIMEOUT_MS=3000,OVER_LIMIT_MS=30000,MAX_SAMPLE_GAP_MS=1500;
    private boolean active,reached,limit,lowering,holding;
    private int target;
    private long command,lowerCommand,lowerAt,holdAt,overLimitSince=-1,lastOverLimitAt=-1;
    public void begin(boolean enabled,int temperature,long sequence){active=enabled;reached=limit=lowering=holding=false;target=temperature;command=sequence;lowerCommand=lowerAt=holdAt=-1;overLimitSince=lastOverLimitAt=-1;}
    public boolean active(){return active;}
    public void target(int value){target=value;}
    public void cancel(){active=lowering=holding=false;overLimitSince=lastOverLimitAt=-1;}
    public void clearResult(){if(!active)reached=limit=false;}
    private boolean validStatus(ControlState s,String event,long now){
        return event.equals("status")&&s.ownRun&&s.foreground&&s.acceptedCommandSequence>=command
                &&s.pendingAction.isEmpty()&&s.fresh(now)&&s.statusSequence>s.acceptedCommandStatusSequence
                &&!s.potMissing()&&s.coverVerified&&s.coverOpen==0&&s.temperature>=35&&s.temperature<=250;
    }
    public boolean shouldLower(ControlState s,String event,long now){
        return active&&!reached&&!lowering&&!holding&&validStatus(s,event,now)
                &&s.allows("APPLY_SETTINGS",now)&&s.temperature>=target;
    }
    public void lowering(long sequence,long now){reached=lowering=true;holding=false;lowerCommand=sequence;lowerAt=now;}
    public boolean loweringInFlight(){return active&&lowering;}
    public void lowered(long now){lowering=false;holding=true;holdAt=now;}
    public boolean loweringTimedOut(long now){return lowering&&lowerAt>=0&&now-lowerAt>LOWER_ACK_TIMEOUT_MS;}
    public boolean holdTimedOut(long now){return active&&holding&&holdAt>=0&&now-holdAt>HOLD_MS+5000;}
    public boolean tooHot(ControlState s,String event,long now){
        if(!active||!reached||!event.equals("status")||!s.ownRun||!s.fresh(now)||s.temperature<=target+5){
            overLimitSince=lastOverLimitAt=-1;return false;
        }
        if(s.temperature>=target+10)return true;
        if(overLimitSince<0||now<lastOverLimitAt||now-lastOverLimitAt>MAX_SAMPLE_GAP_MS)overLimitSince=now;
        lastOverLimitAt=now;
        return now-overLimitSince>OVER_LIMIT_MS;
    }
    public boolean observe(ControlState s,String event,long now){
        if(!active)return false;
        if(event.equals("stopping")&&(s.naturalCompletion||s.stopReason.equals("Eingestellte Laufzeit erreicht")))limit=true;
        if(event.equals("closed")||event.equals("stopped")||event.equals("stopping")||s.stopping||s.error!=0||s.deviceFaultLatched||!s.connected){cancel();return false;}
        if(event.equals("ack")&&s.pendingAction.isEmpty()&&s.acceptedCommandSequence<command){cancel();return false;}
        if(!holding||!validStatus(s,event,now)||s.acceptedCommandSequence<lowerCommand||now-holdAt<HOLD_MS)return false;
        cancel();return true;
    }
    public String status(ControlState s){
        if(limit)return "Zeitlimit · Ziel nicht bestätigt";
        if(!reached||s.idleConfirmed&&!s.stopping&&!s.ownRun)return "";
        return s.stopping?"Ziel erreicht · Stoppen …":lowering?"Ziel erreicht · Sollwert wird gesenkt":holding?"Ziel erreicht · Temperatur halten":"Ziel erreicht";
    }
}
