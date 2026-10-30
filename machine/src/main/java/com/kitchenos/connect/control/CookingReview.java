package com.kitchenos.connect.control;

/** Records submitted commands only. This is not evidence of physical cooking success. */
public final class CookingReview {
    private ControlValues original;
    private boolean changed,stopped;
    public void begin(ControlValues expected,boolean previouslyChanged){original=expected;changed=previouslyChanged;stopped=false;}
    public void submitted(String action,ControlValues values){
        if(original==null)return;
        if(!action.equals("START")||values.seconds!=original.seconds||values.temperature!=original.temperature||values.speed!=original.speed||values.lidless!=original.lidless)changed=true;
    }
    public void localStop(){if(original!=null)changed=true;}
    public void stopRequested(){stopped=true;localStop();}
    public boolean stopped(){return stopped;}
    public boolean changed(){return changed;}
}
