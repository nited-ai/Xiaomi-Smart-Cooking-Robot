package com.kitchenos.connect.control;

/** Correlated diagnostic receipt. No positive pot-presence claim or start grant. */
public final class PotCheckState {
    public enum Result { UNKNOWN, CHECKING, MISSING, CLEAR_WINDOW }
    private long id;
    private Result result=Result.UNKNOWN;
    private boolean missing,required;
    public boolean active(){return result==Result.CHECKING;}
    public Result result(){return result;}
    public long id(){return id;}
    public boolean missing(){return missing;}
    public boolean required(){return required||active();}
    public void require(){required=true;if(!active())result=Result.UNKNOWN;}
    public void reset(){id=0;result=Result.UNKNOWN;missing=required=false;}
    public void reconnect(){id=0;result=Result.UNKNOWN;required|=missing;}
    public boolean begin(long next){if(next<=id||next<=0||active())return false;id=next;result=Result.CHECKING;return true;}
    public boolean complete(long receipt,String outcome,boolean idleConfirmed){
        if(!active()||receipt!=id||!idleConfirmed)return false;
        result=outcome.equals("missing")?Result.MISSING:outcome.equals("clear_window")?Result.CLEAR_WINDOW:Result.UNKNOWN;
        if(result==Result.MISSING)missing=true;
        else if(result==Result.CLEAR_WINDOW)missing=false;
        required=result==Result.UNKNOWN;
        return true;
    }
    public void invalidate(){require();}
    /** Live combined lid/vessel interlock; no IH diagnostic or positive sensor claim. */
    public void closedInterlock(){if(!active()){missing=required=false;result=Result.UNKNOWN;}}
    public void aborted(){required|=active()||missing;result=Result.UNKNOWN;}
}
