package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;

/** Visual classification, never an interlock or an inferred sensor reading.
 * OEM error names: DeviceStatus in retained APK1.6.5, codes1..29/254.
 */
public final class HomeVesselSignal {
    public enum Kind { NONE, CHECKING, LID, POT, THERMAL, MOTOR, SCALE, CONNECTION, ACTION, GENERAL, STOP_UNCONFIRMED }
    public final Kind kind;
    public final int error;
    private final String issue;
    private HomeVesselSignal(Kind kind,int error){this(kind,error,"");}
    private HomeVesselSignal(Kind kind,int error,String issue){this.kind=kind;this.error=error;this.issue=issue;}
    public static HomeVesselSignal from(ControlState state,long now){
        if(state.uncertainRun)return new HomeVesselSignal(Kind.STOP_UNCONFIRMED,0);
        int fault=state.deviceFaultLatched?state.lastDeviceError:state.fresh(now)?state.error:0;
        if(fault!=0)return new HomeVesselSignal(forError(fault),fault);
        if(state.foreground&&state.connected&&state.potCheck.active()&&state.statusAt>=0&&now>=state.statusAt&&now-state.statusAt<500)return new HomeVesselSignal(Kind.CHECKING,0);
        if(!state.foreground||!state.fresh(now))return new HomeVesselSignal(Kind.CONNECTION,0);
        if(state.potMissing())return new HomeVesselSignal(Kind.POT,2);
        if(state.potCheck.active()||state.pendingAction.equals("CHECK_POT")||state.state==9)
            return new HomeVesselSignal(Kind.CHECKING,0);
        if(state.potCheck.required())return new HomeVesselSignal(Kind.ACTION,0,state.potCheckWaitReason());
        if(!state.coverVerified||state.coverOpen<0)return new HomeVesselSignal(Kind.CONNECTION,0);
        String issue=state.visualIssue.isEmpty()?state.userIssue:state.visualIssue;
        if(!issue.isEmpty())return new HomeVesselSignal(state.coverOpen==1?Kind.LID:Kind.ACTION,0,issue);
        return new HomeVesselSignal(Kind.NONE,0);
    }
    private static Kind forError(int code){
        if(code==1||code==13||code==14)return Kind.LID;
        if(code==2)return Kind.POT;
        if(code==5||code==7||code==21||code==24)return Kind.THERMAL;
        if(code>=16&&code<=27)return Kind.MOTOR;
        if(code==29)return Kind.SCALE;
        if(code==254)return Kind.CONNECTION;
        return Kind.GENERAL;
    }
    public boolean fault(){return kind!=Kind.NONE&&kind!=Kind.CHECKING&&kind!=Kind.CONNECTION||error!=0;}
    public String description(){
        if(!issue.isEmpty())return issue;
        String text;
        switch(kind){
            case CHECKING:text="Topfprüfung läuft";break;
            case LID:text="Deckel oder Verriegelung prüfen";break;
            case POT:text="Mixtopf einsetzen und korrekt aufsetzen";break;
            case THERMAL:text="Temperatursystem prüfen";break;
            case MOTOR:text="Motor oder Messer prüfen";break;
            case SCALE:text="Waage prüfen";break;
            case CONNECTION:text="Gerätesignal nicht bestätigt";break;
            case STOP_UNCONFIRMED:text="Stopp nicht bestätigt. Maschinenzustand vor Ort prüfen";break;
            case GENERAL:text="Gerätefehler. Ursache vor weiterem Kochen prüfen";break;
            default:return "";
        }
        return text+(error==0?"":". Gerätefehler "+error);
    }
}
