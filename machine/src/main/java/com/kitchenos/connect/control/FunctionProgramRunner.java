package com.kitchenos.connect.control;

/** Foreground-only sequence. Every movement is bounded by the existing native bridge.
 * No phase advances on a timer alone: ACK, a recognized bounded phase end, fresh zero outputs
 * and the bridge's confirmed stop are required before another START. */
public final class FunctionProgramRunner {
    public interface Transport {boolean start(ControlValues values);default boolean adjust(ControlValues values){return false;}default boolean stir(int speed){return false;}void stop();}
    private final Transport transport;private final ControlState state;
    private FunctionProgram program;private int index,completed,changeIndex,currentSpeed;private long entered,deadline,command=-1,commandAt,motionAt=-1;private boolean waiting,dispatching;private String result="";
    public FunctionProgramRunner(ControlState state,Transport transport){this.state=state;this.transport=transport;}
    public boolean active(){return program!=null;}
    public int speed(){return active()?currentSpeed:0;}
    public String status(){if(!active())return result;FunctionProgram.Phase p=program.phases.get(index);return p.motorOnlyChanges?(currentSpeed==0?"Heizen · Messerpause":"Rückwärts rühren · Stufe 1"):p.name+" · "+(index+1)+"/"+program.phases.size();}
    public int remaining(long now){if(!active())return -1;FunctionProgram.Phase p=program.phases.get(index);int phase=waiting||p.motorOnlyChanges?Math.max(0,p.seconds-(int)((now-entered)/1000)):!p.changes.isEmpty()?motionAt<0?p.seconds:Math.max(0,p.seconds-(int)((now-motionAt)/1000)):state.acceptedCommandSequence!=command||state.statusSequence<=state.acceptedCommandStatusSequence||state.remainingSeconds<0?p.seconds:Math.min(p.seconds,state.displayRemainingSeconds());return program.seconds-completed-p.seconds+phase;}
    public boolean start(FunctionProgram plan,long now){
        if(active()||plan==null||!state.allows("START",now))return false;
        program=plan;index=completed=0;command=-1;result="";
        // Each native phase remains bounded; allow settling for the slow-cooking sequence.
        long budget=plan.seconds*1000L+plan.phases.size()*5000L+10000;
        deadline=now+(plan.id.equals("slow")?budget:Math.min(7200000L,budget));
        return enter(now);
    }
    private boolean idle(){return !state.ownRun&&!state.stopping&&state.pendingAction.isEmpty()&&state.idleConfirmed&&(state.state==0||state.state==11)&&state.ihStatus==0&&state.ihGear==0&&state.motorStatus==0&&state.motorRaw==0;}
    // Match bridge.c watch_run for an already submitted phase. The stricter
    // 500 ms status limit in fresh() authorizes START, not continued execution.
    private boolean telemetryAlive(long now){return state.connected&&state.samples>=3&&state.statusAt>=0&&state.infoAt>=0&&now>=state.statusAt&&now>=state.infoAt&&now-state.statusAt<=1500&&now-state.infoAt<=1200;}
    private boolean phaseEnded(long now){
        // Heated MCU profiles may hold their countdown while heating. The bridge
        // still stops at the submitted duration plus its fixed STOP reserve.
        // Only this exact terminal reason may finish a bounded, acknowledged
        // phase; telemetry faults, user STOP and the session budget never do.
        return state.naturalCompletion||state.acceptedCommandSequence==command
            &&now-entered>=program.phases.get(index).seconds*1000L
            &&state.stopReason.equals("Eingestellte Laufzeit erreicht");
    }
    private boolean enter(long now){
        FunctionProgram.Phase p=program.phases.get(index);entered=commandAt=now;motionAt=-1;changeIndex=0;currentSpeed=p.speed;waiting=p.waiting();command=-1;
        if(waiting){if(!idle()){cancel("Intervall ohne bestätigten Stillstand abgebrochen");return false;}return true;}
        dispatching=true;boolean sent;try{sent=transport.start(p.values());}finally{dispatching=false;}
        if(!sent){cancel("Intervall nicht angenommen · Programm angehalten");return false;}command=state.commandSequence;return true;
    }
    public void tick(long now){
        if(!active()||dispatching)return;
        if(!state.foreground||!state.rootVerified||!telemetryAlive(now)||state.closing||state.uncertainRun||state.deviceFaultLatched||state.error!=0||!state.coverVerified||state.coverOpen!=0||state.potMissing()||state.potCheck.required()||state.potCheck.active()){cancel("Programm unterbrochen · Gerät und Verbindung prüfen");return;}
        if(now>=deadline){cancel("Gesamtzeit erreicht · Programm angehalten");return;}
        FunctionProgram.Phase p=program.phases.get(index);
        if(p.motorOnlyChanges&&state.state==3){cancel("Anbraten unterbrochen · kein automatisches Fortsetzen");return;}
        if(waiting){if(!idle()){cancel("Unerwarteter Zustand während der Pause");return;}if(now-entered<p.seconds*1000L)return;}
        else{
            if(state.stopping&&!phaseEnded(now)){cancel("Programm angehalten");return;}
            if(state.acceptedCommandSequence!=command){if(state.pendingAction.isEmpty()||now-commandAt>(changeIndex>0?1200:5000))cancel("Intervallbestätigung fehlt · Programm angehalten");return;}
            if(now-entered>(p.seconds+12L)*1000){cancel("Intervallende nicht bestätigt · Programm angehalten");return;}
            if(!p.changes.isEmpty()){
                if((state.stopping||idle())&&changeIndex<p.changes.size()&&!(p.motorOnlyChanges&&phaseEnded(now))){cancel("Stufenfolge unvollständig · Programm angehalten");return;}
                if(!state.stopping&&!idle()&&state.ownRun&&state.state==1&&state.statusSequence>state.acceptedCommandStatusSequence){
                    if(motionAt<0)motionAt=now;
                    if(changeIndex<p.changes.size()){
                        FunctionProgram.SpeedChange next=p.changes.get(changeIndex);long due=motionAt+next.second*1000L;
                        // MCU preparation may consume part of the original native time budget.
                        // Truncate trailing motor changes instead of extending heat or sending
                        // a restart after that budget. Normal STOP still needs confirmation.
                        if(p.motorOnlyChanges&&(due>=entered+p.seconds*1000L||now>=entered+p.seconds*1000L))return;
                        if(now>=due){
                            // Never replay a queue of missed impulses after UI/transport delay.
                            if(now-due>750){cancel("Stufenwechsel verspätet · Programm angehalten");return;}
                            if(!state.fresh(now))return;
                            dispatching=true;boolean sent;try{sent=p.motorOnlyChanges?transport.stir(next.speed):transport.adjust(new ControlValues(p.seconds,p.temperature,next.speed,false,true));}finally{dispatching=false;}
                            if(!sent){cancel("Stufenwechsel nicht angenommen · Programm angehalten");return;}
                            currentSpeed=next.speed;changeIndex++;command=state.commandSequence;commandAt=now;return;
                        }
                    }
                }
            }
            if(!idle())return;
            if(!phaseEnded(now)||state.statusSequence<=state.acceptedCommandStatusSequence){cancel("Intervall unerwartet beendet · Programm angehalten");return;}
        }
        // A gap inside the running watchdog may delay a transition, never
        // authorize the next movement using an old idle observation.
        if(!state.fresh(now))return;
        completed+=p.seconds;if(++index==program.phases.size()){program=null;result="Programm beendet";return;}
        enter(now);
    }
    public void cancel(String reason){if(!active())return;program=null;result=reason;state.userIssue=reason;transport.stop();}
}
