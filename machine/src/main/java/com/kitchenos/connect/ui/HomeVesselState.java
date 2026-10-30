package com.kitchenos.connect.ui;

import com.kitchenos.connect.control.ControlState;

/** Presentation only. Cup placement requires explicit local user confirmation. */
public final class HomeVesselState {
    public enum Lid { UNKNOWN, OPEN, CLOSED, POT_MISSING }
    public final Lid lid;
    public final boolean cupInsertionCue;
    /** Compatibility with both renderers: cup placement is never inferred from weight. */
    public final boolean cupWeightDetected=false;
    private final boolean userConfirmed;
    private HomeVesselState(Lid lid,boolean cue){this(lid,cue,false);}
    private HomeVesselState(Lid lid,boolean cue,boolean confirmed){this.lid=lid;cupInsertionCue=cue;userConfirmed=confirmed;}
    public static HomeVesselState from(ControlState state,long now){
        return from(state,now,null);
    }
    public static HomeVesselState from(ControlState state,long now,HomeCupState cup){
        if(state.deviceFaultLatched&&state.lastDeviceError==2)return new HomeVesselState(Lid.POT_MISSING,false);
        if(state.potCheck.missing())return new HomeVesselState(Lid.POT_MISSING,false);
        if(!state.foreground||!state.fresh(now))return new HomeVesselState(Lid.UNKNOWN,false);
        if(state.potMissing())return new HomeVesselState(Lid.POT_MISSING,false);
        if(!state.coverVerified||state.coverOpen<0)return new HomeVesselState(Lid.UNKNOWN,false);
        Lid lid=state.coverOpen==0?Lid.CLOSED:Lid.OPEN;
        return new HomeVesselState(lid,lid==Lid.CLOSED&&cup!=null&&cup.displayedInserted(),cup!=null&&cup.inserted());
    }
    public String hint(){switch(lid){
        case OPEN:return "Deckel schließen und verriegeln";
        case CLOSED:return cupInsertionCue?(userConfirmed?"Messbecher bestätigt":"Messbecher als eingesetzt dargestellt"):"Messbecher einsetzen";
        case POT_MISSING:return "Mixtopf einsetzen";
        default:return "Deckelstatus wird geprüft";
    }}
}
