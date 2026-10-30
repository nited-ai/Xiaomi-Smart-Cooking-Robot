package com.kitchenos.connect.control;

import org.json.*;

/** Main-thread state; freshness is measured by the receiving app's monotonic clock. */
public final class ControlState {
    public final PotCheckState potCheck=new PotCheckState();
    public final RunDisplayClock displayClock=new RunDisplayClock();
    public int displayRemainingSeconds(){return displayClock.remaining(this);}
    public boolean rootVerified,rootChecking,connecting,connected,closing,foreground,ownRun,stopping,uncertainRun;
    public boolean recovering;
    public boolean coverVerified,potVerified,idleConfirmed,everOwnedRun;
    public boolean lidlessSupported;
    public boolean potCheckSupported;
    public boolean deviceFaultLatched;
    public int lastDeviceError,acceptedStarts;
    public int potResultWeight;
    public int potVersionSamples;
    public long commandSequence,acceptedCommandSequence=-1,statusSequence,acceptedCommandStatusSequence;
    public int state=-1,coverOpen=-1,pot=-1,error=255,samples,temperature,motorRaw,weightRaw;
    public int mcuVersion=-1,ihVersion,motorVersion,switches;
    public int ihStatus,ihGear,motorStatus;
    public int remainingSeconds=-1;
    public boolean naturalCompletion;public String stopReason="";
    public long elapsed,statusAt=-1,infoAt=-1;
    public String lastEvent="Bereit. Verbindung nur nach deiner Bestätigung.";
    public String pendingAction="";
    public String userIssue="";
    public String visualIssue="";
    public boolean busy(){return connecting||connected||closing||recovering;}
    /** Error 2 is documented; the separate pot bit is not mapped on every firmware. */
    public boolean potMissing(){return error==2||deviceFaultLatched&&lastDeviceError==2||potVerified&&pot==0||potCheck.missing();}
    /** A connected recovery needs an acknowledged stop and current, idle outputs. */
    public boolean canInspect(long now){return canInspect(now,false);}
    public boolean canInspect(long now,boolean lidless){
        if(!foreground||recovering||rootChecking||connecting||closing||stopping||ownRun||potCheck.active()||!pendingAction.isEmpty()||(!uncertainRun&&!deviceFaultLatched))return false;
        if(!connected)return true;
        return rootVerified&&fresh(now)&&idleConfirmed&&(state==0||state==11)&&error==0&&coverVerified&&(lidless?ControlAvailability.LIDLESS&&lidlessSupported&&mcuVersion==12&&coverOpen>=0:coverOpen==0)&&(!potVerified||pot==1)&&ihStatus==0&&ihGear==0&&motorStatus==0&&motorRaw==0;
    }
    public void begin(){
        displayClock.reset();potCheck.reconnect();potVersionSamples=0;visualIssue="";
        naturalCompletion=false;stopReason="";remainingSeconds=-1;
        userIssue="";connecting=true;connected=false;closing=false;ownRun=false;stopping=false;idleConfirmed=false;everOwnedRun=false;pendingAction="";
        lidlessSupported=potCheckSupported=false;mcuVersion=-1;coverVerified=false;potVerified=false;samples=0;statusAt=infoAt=-1;coverOpen=pot=-1;state=-1;error=255;
    }
    public void accept(JSONObject o,long now)throws JSONException {
        String type=o.getString("type");
        if(type.equals("potCheck")){
            long id=o.getLong("id");String stage=o.getString("stage");
            if(stage.equals("checking")){if(!potCheck.begin(id))throw new JSONException("Ungültige Topfprüfung");idleConfirmed=false;}
            else if(stage.equals("complete")){
                String outcome=o.getString("outcome");
                if((outcome.equals("missing")&&(!o.getBoolean("startAcked")||o.getInt("error")!=2))||(outcome.equals("clear_window")&&(!o.getBoolean("startAcked")||o.getInt("phaseSamples")<10||o.getInt("error")!=0)))throw new JSONException("Unbelegtes Topfergebnis");
                if(!o.getBoolean("confirmedIdle")||!idleConfirmed||!potCheck.complete(id,o.getString("outcome"),true))throw new JSONException("Topfprüfung ohne korrelierten Stopp");
                potResultWeight=weightRaw;
                pendingAction="";
            }else throw new JSONException("Unbekannte Topfprüfphase");
            return;
        }
        boolean diagnostic=o.optBoolean("diagnostic",false);
        if(diagnostic&&(!potCheck.active()||o.optLong("potCheckId",0)!=potCheck.id()))throw new JSONException("Nicht zugeordnete Topfprüfung");
        if(type.equals("ready")){connecting=false;connected=true;lidlessSupported=o.optBoolean("lidlessSupported",false);potCheckSupported=o.optBoolean("potCheckSupported",false);}
        else if(type.equals("status")){
            // Read every required field before committing any fresh observation.
            int nextState=o.getInt("state"),nextCover=o.getInt("coverOpen"),nextPot=o.getInt("potPresent"),nextError=o.getInt("error");
            int nextSamples=o.getInt("samples"),nextTemp=o.getInt("temperature"),nextMotor=o.getInt("motorRaw"),nextWeight=o.getInt("weightRaw");
            long nextElapsed=o.getLong("elapsed");boolean nextCoverVerified=o.getBoolean("coverMappingVerified"),nextPotVerified=o.getBoolean("potMappingVerified"),nextOwned=o.getBoolean("armed");
            if(nextSamples<1||nextCover< -1||nextCover>1||nextPot< -1||nextPot>1||nextError<0||nextError>255||nextTemp<0||nextTemp>255||nextElapsed<0)throw new JSONException("Ungültige Statuswerte");
            statusSequence++;state=nextState;coverOpen=nextCover;pot=nextPot;error=nextError;samples=nextSamples;temperature=nextTemp;motorRaw=nextMotor;weightRaw=nextWeight;elapsed=nextElapsed;
            if(nextError!=0&&!(diagnostic&&(nextError==1||nextError==2))){lastDeviceError=nextError;deviceFaultLatched=true;}
            ihStatus=o.optInt("ihStatus",0);ihGear=o.optInt("ihGear",0);motorStatus=o.optInt("motorStatus",0);
            remainingSeconds=o.optInt("remainingSeconds",-1);
            coverVerified=nextCoverVerified;potVerified=nextPotVerified;ownRun=nextOwned;everOwnedRun|=nextOwned;if(nextOwned)idleConfirmed=false;statusAt=now;
        }else if(type.equals("info")){
            int nextMcu=o.getInt("version"),nextIh=o.getInt("ihVersion"),nextMotor=o.getInt("motorVersion"),nextSwitches=o.getInt("switches");
            if(nextSwitches<0||nextSwitches>255)throw new JSONException("Ungültiges Deckelsignal");
            mcuVersion=nextMcu;ihVersion=nextIh;motorVersion=nextMotor;switches=nextSwitches;infoAt=now;
            potVersionSamples=nextMcu==12&&nextIh==4&&nextMotor==5?Math.min(2,potVersionSamples+1):0;
            if(coverVerified)coverOpen=(switches&0x80)!=0?0:1;
        }else if(type.equals("sent")&&o.getInt("opcode")==0x80){if(!diagnostic){ownRun=true;everOwnedRun=true;}idleConfirmed=false;naturalCompletion=false;stopReason="";}
        else if(type.equals("stopping")){stopping=true;pendingAction="";stopReason=o.optString("message");naturalCompletion=stopReason.equals("Kochzeit beendet");}
        else if(type.equals("stopped")){ownRun=false;stopping=false;idleConfirmed=true;uncertainRun=false;pendingAction="";}
        else if(type.equals("error")){pendingAction="";}
        else if(type.equals("ack")){int op=o.getInt("opcode");boolean success=o.getBoolean("success"),matches=pendingAction.equals("START")&&op==0x80||pendingAction.equals("RESUME")&&op==0x85||(pendingAction.equals("APPLY")||pendingAction.equals("FRY_MOTOR"))&&op==0x88||pendingAction.equals("PAUSE")&&op==0x81;if(success&&matches){if(pendingAction.equals("START"))acceptedStarts++;acceptedCommandSequence=commandSequence;acceptedCommandStatusSequence=statusSequence;}if(!success||matches)pendingAction="";}
        else if(type.equals("closed")){
            idleConfirmed=o.getBoolean("confirmedIdle");connected=false;
            if(idleConfirmed){ownRun=false;stopping=false;uncertainRun=false;}
        }
    }
    /** Correlate a terminal ACK with the single locally submitted operation. */
    public void submitted(String action){commandSequence++;pendingAction=action.equals("APPLY_SETTINGS")?"APPLY":action;}
    public boolean fresh(long now){return connected&&!closing&&samples>=3&&statusAt>=0&&infoAt>=0&&now>=statusAt&&now>=infoAt&&now-statusAt<500&&now-infoAt<1200;}
    public boolean safe(long now){return safe(now,false);}
    public boolean safe(long now,boolean lidless){return foreground&&rootVerified&&fresh(now)&&!potCheck.required()&&!potCheck.missing()&&coverVerified&&(lidless?ControlAvailability.LIDLESS&&lidlessSupported&&mcuVersion==12&&coverOpen>=0:coverOpen==0)&&(!potVerified||pot==1)&&error==0&&!stopping&&!uncertainRun&&!deviceFaultLatched;}
    public boolean allows(String action,long now){
        return allows(action,now,false);
    }
    public boolean allows(String action,long now,boolean lidless){
        if(!safe(now,lidless)||!pendingAction.isEmpty())return false;
        if(action.equals("START"))return !ownRun&&(state==0||state==11)&&ihStatus==0&&ihGear==0&&motorStatus==0&&motorRaw==0;
        if(action.equals("RESUME"))return ownRun&&state==3;
        if(action.equals("APPLY")||action.equals("APPLY_SETTINGS"))return ownRun&&(state==1||state==3||state==9);
        if(action.equals("PAUSE"))return ownRun&&(state==1||state==9);
        return false;
    }
    public String startReason(long now){
        return startReason(now,false);
    }
    public String startReason(long now,boolean lidless){
        if(uncertainRun||recovering)return "Maschinenzustand wird automatisch geprüft. Start wartet auf bestätigten Stillstand.";
        if(potCheck.active())return "Topfprüfung läuft";
        if(potCheck.missing())return "Mixtopf einsetzen und korrekt aufsetzen";
        if(potCheck.required())return potCheckWaitReason();
        if(deviceFaultLatched)return DeviceFaults.describe(lastDeviceError);
        if(!foreground)return "Steuerseite ist nicht aktiv";
        if(!rootVerified)return "Root-Zugang für diese App noch nicht bestätigt";
        if(closing)return "Verbindung wird beendet";
        if(!connected)return connecting?"Verbindung wird vorbereitet":"Bitte verbinden";
        if(!fresh(now))return "Frische Status- und Infodaten fehlen";
        if(!coverVerified||coverOpen<0)return "Topf-/Deckelverriegelung noch nicht bestätigt";
        if(lidless&&!ControlAvailability.LIDLESS)return ControlAvailability.LIDLESS_REASON;
        if(lidless&&!lidlessSupported)return "Die installierte Gerätesteuerung unterstützt Kochen ohne Deckel noch nicht.";
        if(lidless&&mcuVersion!=12)return "Kochen ohne Deckel ist nur für die geprüfte MCU-Version 12 verfügbar.";
        if(!lidless&&coverOpen!=0)return "Topf und Deckel prüfen und richtig verriegeln";
        if(potVerified&&pot!=1)return "Topf fehlt";
        if(error!=0)return DeviceFaults.describe(error);
        if(stopping)return "Stoppbestätigung wird abgewartet";
        if(!pendingAction.isEmpty())return "Geräteantwort wird abgewartet";
        if(ownRun)return "Eigener Lauf aktiv";
        if(state!=0&&state!=11)return "Gerät ist nicht im Leerlauf";
        if(ihStatus!=0||ihGear!=0||motorStatus!=0||motorRaw!=0)return "Motor und Heizung laufen noch aus. Bitte kurz warten.";
        return "";
    }
    public String potCheckWaitReason(){return coverOpen==0?"Topfprüfung ausstehend · Deckel öffnen":"Topfprüfung ausstehend";}
    public void ended(){
        uncertainRun|=(everOwnedRun||ownRun||stopping||pendingAction.equals("START")||potCheck.active()||pendingAction.equals("CHECK_POT"))&&!idleConfirmed;
        potCheck.aborted();
        connected=connecting=closing=ownRun=stopping=false;statusAt=infoAt=-1;pendingAction="";
    }
}
