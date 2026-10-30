package com.kitchenos.connect.control;

/** Presentation only: MCU phase seconds, never a watchdog or permission to run. */
public final class RunDisplayClock {
    private boolean active,started,pending,newRun;
    private int target,queuedTarget,last=-1;private long command,baseElapsed;
    public void reset(){active=started=pending=false;last=-1;}
    public void submitted(String action,int seconds,ControlState s){
        if(!action.equals("START")&&!action.equals("APPLY")&&!action.equals("RESUME"))return;
        newRun=action.equals("START");
        if(newRun){active=true;started=false;baseElapsed=0;}
        if(!active)return;
        queuedTarget=seconds;if(newRun){target=seconds;last=seconds;}command=s.commandSequence;pending=true;
    }
    public int remaining(ControlState s){
        if(!active)return s.remainingSeconds;
        if(!s.ownRun&&s.idleConfirmed&&s.naturalCompletion)return 0;
        if(pending){
            if(s.acceptedCommandSequence!=command){if(!s.pendingAction.isEmpty())return last;pending=false;if(newRun){reset();return s.remainingSeconds;}}
            else if(s.statusSequence<=s.acceptedCommandStatusSequence)return last;
            else {target=queuedTarget;last=target;
            // A changed phase duration starts at its first post-ACK MCU observation.
            if(!newRun)baseElapsed=s.elapsed;
            pending=false;}
        }
        if(!s.ownRun)return last;
        if(!started){if(s.state!=1&&s.state!=3)return last;started=true;}
        if(s.state==1||s.state==3){
            // A firmware phase reset on resume/apply restarts its elapsed counter.
            if(s.elapsed<baseElapsed)baseElapsed=0;
            last=(int)Math.max(1,target-Math.max(0,s.elapsed-baseElapsed));
        }
        // Zero means confirmed completion, never an assumed end while still running.
        return last;
    }
}
