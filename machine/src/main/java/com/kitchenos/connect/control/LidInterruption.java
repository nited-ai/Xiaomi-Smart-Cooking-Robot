package com.kitchenos.connect.control;

/** Process-local continuation receipt for a known lid interlock STOP, never an automatic restart. */
public final class LidInterruption {
    private ControlValues accepted;
    private boolean active,stopped,cancelled;
    private int seconds;
    public void clear(){accepted=null;active=stopped=cancelled=false;seconds=0;}
    public void accepted(ControlValues values){accepted=values;active=stopped=cancelled=false;seconds=0;}
    public boolean active(){return active;}
    public void cancelContinuation(){cancelled=true;}
    public boolean cancelled(){return cancelled;}
    public int seconds(){return seconds;}
    public void observe(ControlState s,String type,int priorRemaining){
        if(s.uncertainRun||s.closing||!s.connected||s.error>1||s.potMissing()||s.naturalCompletion){clear();return;}
        if(!active&&accepted!=null&&!accepted.lidless&&s.ownRun&&priorRemaining>0
                &&(s.error==1||s.coverVerified&&s.coverOpen==1)){
            active=true;seconds=priorRemaining;
        }
        if(active&&type.equals("stopped")&&s.idleConfirmed&&!s.ownRun)stopped=true;
    }
    public boolean canClearLidFault(ControlState s,long now){return confirmedIdle(s,now)&&s.lastDeviceError==1;}
    public boolean confirmedIdle(ControlState s,long now){
        return active&&stopped&&s.foreground&&s.rootVerified&&s.fresh(now)&&s.idleConfirmed&&!s.ownRun&&!s.stopping
                &&s.pendingAction.isEmpty()&&(s.state==0||s.state==11)&&s.error==0
                &&!s.uncertainRun&&s.ihStatus==0&&s.ihGear==0
                &&s.motorStatus==0&&s.motorRaw==0;
    }
    public boolean ready(ControlState s,long now){return active&&stopped&&!cancelled&&s.allows("START",now,false);}
}
