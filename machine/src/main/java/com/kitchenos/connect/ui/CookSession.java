package com.kitchenos.connect.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Process-local receipts for one step of one particular cook. No I/O or device commands. */
public final class CookSession {
    private static final String PROCESS=UUID.randomUUID().toString();
    private static final Map<String,CookSession> SESSIONS=new LinkedHashMap<>();
    private static final Map<Object,String> CURRENT_RUNS=new java.util.WeakHashMap<>();
    private final String owner,cookId,digest,stepId,key;
    private final Object clientIdentity;
    private String runToken="";
    private int entryStarts;
    private boolean submitted,accepted,idleReceipt,interrupted,modified,delivered;

    private CookSession(String owner,String cookId,String digest,String stepId,Object identity){
        this.owner=owner;this.cookId=cookId;this.digest=digest;this.stepId=stepId;clientIdentity=identity;
        key=owner+"\n"+cookId+"\n"+digest+"\n"+stepId;
    }
    public static synchronized CookSession open(String owner,String cookId,String digest,String stepId,Object identity){
        String key=owner+"\n"+cookId+"\n"+digest+"\n"+stepId;CookSession found=SESSIONS.get(key);
        if(found!=null&&found.clientIdentity==identity)return found;
        CookSession created=new CookSession(owner,cookId,digest,stepId,identity);SESSIONS.put(key,created);
        while(SESSIONS.size()>128)SESSIONS.remove(SESSIONS.keySet().iterator().next());return created;
    }
    public synchronized void submitted(int previousAcceptedStarts){
        entryStarts=previousAcceptedStarts;submitted=true;accepted=false;idleReceipt=false;interrupted=false;delivered=false;
        runToken=UUID.randomUUID().toString();synchronized(CookSession.class){CURRENT_RUNS.put(clientIdentity,runToken);}
    }
    public synchronized boolean ownsCurrentRun(){synchronized(CookSession.class){return !runToken.isEmpty()&&runToken.equals(CURRENT_RUNS.get(clientIdentity));}}
    public synchronized boolean acceptsRunMetadata(int acceptedStarts){return submitted&&!idleReceipt&&!interrupted&&ownsCurrentRun()&&acceptedStarts<=entryStarts+1;}
    public synchronized void observe(int acceptedStarts,boolean confirmedIdle,boolean ownRun,boolean pendingStart,boolean uncertain,boolean fault,int error,boolean completedNaturally){
        if(!submitted||idleReceipt||interrupted||!ownsCurrentRun())return;
        // Exact counter, not ">": a later run cannot certify this step.
        if(acceptedStarts>entryStarts+1){interrupted=true;modified=true;return;}
        if(acceptedStarts==entryStarts+1)accepted=true;
        if(accepted&&completedNaturally&&acceptedStarts==entryStarts+1&&confirmedIdle&&!ownRun&&!pendingStart&&!uncertain&&!fault&&error==0)idleReceipt=true;
    }
    /** A leave/QUIT before a receipt is interruption, not successful recipe completion. */
    public synchronized void leaving(){if(submitted&&!idleReceipt){interrupted=true;modified=true;}}
    public synchronized void markModified(boolean value){modified|=value;}
    /** A user's STOP does not certify that the prescribed processing time finished. */
    public synchronized void stopped(){if(submitted&&!idleReceipt){interrupted=true;modified=true;}}
    public synchronized boolean hasRun(){return submitted;}
    public synchronized boolean interrupted(){return interrupted;}
    public synchronized boolean modified(){return modified;}
    public synchronized boolean canComplete(){return submitted&&accepted&&idleReceipt&&!delivered;}
    public synchronized boolean deliver(){if(!canComplete())return false;delivered=true;return true;}
    public synchronized Snapshot snapshot(){return new Snapshot(PROCESS,owner,cookId,digest,stepId,runToken,entryStarts,submitted,accepted,idleReceipt,interrupted,modified,delivered);}
    /** Only an existing in-process record may restore a receipt. Bundles cannot manufacture it. */
    public synchronized boolean restore(Snapshot state){
        if(state==null||!owner.equals(state.owner)||!cookId.equals(state.cookId)||!digest.equals(state.digest)||!stepId.equals(state.stepId))return false;
        modified|=state.modified;
        boolean live; synchronized(CookSession.class){live=SESSIONS.get(key)==this;}
        if(PROCESS.equals(state.process)&&live){
            if(runToken.equals(state.runToken))return true;
            // A stale saved View must not overwrite a newer in-process rerun.
            if(!runToken.isEmpty())return false;
        }
        // A process restart (or evicted/unknown receipt) must never turn counters into success.
        if(state.submitted){submitted=true;accepted=false;idleReceipt=false;interrupted=true;modified=true;delivered=false;runToken="";}
        else if(state.interrupted){interrupted=true;modified=true;}
        return false;
    }
    public static final class Snapshot {
        public final String process,owner,cookId,digest,stepId,runToken;
        public final int entryStarts;
        public final boolean submitted,accepted,idleReceipt,interrupted,modified,delivered;
        public Snapshot(String process,String owner,String cookId,String digest,String stepId,String runToken,int entryStarts,boolean submitted,boolean accepted,boolean idleReceipt,boolean interrupted,boolean modified,boolean delivered){
            this.process=process;this.owner=owner;this.cookId=cookId;this.digest=digest;this.stepId=stepId;this.runToken=runToken;this.entryStarts=entryStarts;this.submitted=submitted;this.accepted=accepted;this.idleReceipt=idleReceipt;this.interrupted=interrupted;this.modified=modified;this.delivered=delivered;
        }
    }
}
