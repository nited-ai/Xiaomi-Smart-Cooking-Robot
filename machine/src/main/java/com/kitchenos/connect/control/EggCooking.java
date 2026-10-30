package com.kitchenos.connect.control;

/** A local egg clock. The MCU's longer run time remains a hard fallback limit. */
public final class EggCooking {
    public static final int PREHEAT_SECONDS=480;
    private static final long PREHEAT_MS=PREHEAT_SECONDS*1000L;
    private boolean active,suspended,completed,preheatLimit;
    private boolean needsRebase,rebasing,rebased;
    private int selectedSeconds;
    private long command,startedAt,boilAt=-1,rebaseCommand=-1,rebaseAt=-1;

    public void begin(int seconds,long sequence,long now){
        active=true;suspended=completed=preheatLimit=needsRebase=rebasing=rebased=false;selectedSeconds=seconds;
        command=sequence;startedAt=now;boilAt=rebaseCommand=rebaseAt=-1;
    }
    public void resume(long sequence,long now){command=sequence;startedAt=now;suspended=false;needsRebase=rebasing=false;}
    public void adjust(int seconds){if(active&&seconds>0)selectedSeconds=seconds;}
    public void cancel(){active=suspended=needsRebase=rebasing=false;}
    public boolean active(){return active;}
    public boolean preheating(){return active&&boilAt<0;}
    public boolean startedCooking(){return active&&boilAt>=0;}
    public int elapsedSeconds(long now){return boilAt<0?0:(int)Math.max(0,(now-boilAt)/1000);}
    public boolean suspended(){return active&&suspended;}
    public boolean completed(){return completed;}
    public boolean preheatLimit(){return preheatLimit;}
    public boolean needsRebase(){return active&&needsRebase&&!rebasing;}
    public int selectedSeconds(){return selectedSeconds;}
    public void rebasing(long sequence,long now){command=rebaseCommand=sequence;rebaseAt=now;needsRebase=false;rebasing=true;}
    public boolean rebasing(){return active&&rebasing;}
    public boolean rebaseTimedOut(long now){return active&&!suspended&&rebasing&&now-rebaseAt>3000;}
    public void rebaseAccepted(){rebasing=false;rebased=true;}
    public boolean rebased(){return rebased;}
    public int remainingSeconds(long now){
        if(!active)return completed?0:-1;
        if(boilAt<0)return selectedSeconds;
        return (int)Math.max(0,(selectedSeconds*1000L-Math.max(0,now-boilAt)+999)/1000);
    }
    public boolean preheatTimedOut(long now){
        if(!active||suspended||boilAt>=0||now-startedAt<PREHEAT_MS)return false;
        preheatLimit=true;return true;
    }
    /** Returns true only when an owned, confirmed run has used the selected time. */
    public boolean observe(ControlState s,String event,long now,boolean lidInterrupted){
        if(!active)return false;
        if(lidInterrupted&&(event.equals("stopping")||event.equals("stopped")||!s.ownRun||s.coverOpen==1)){suspended=true;return false;}
        if(event.equals("closed")||event.equals("stopped")||event.equals("stopping")||s.error!=0||s.deviceFaultLatched||!s.connected){cancel();return false;}
        if(suspended||!event.equals("status")||!s.ownRun||!s.foreground
                ||s.acceptedCommandSequence<command||!s.pendingAction.isEmpty()||!s.fresh(now)
                ||s.statusSequence<=s.acceptedCommandStatusSequence||s.potMissing()
                ||!s.coverVerified||s.coverOpen!=0||s.temperature<35||s.temperature>250)return false;
        if(boilAt<0){
            if(s.temperature<100)return false;
            boilAt=now;needsRebase=true;
        }
        if(now-boilAt<selectedSeconds*1000L)return false;
        completed=true;cancel();return true;
    }
}
