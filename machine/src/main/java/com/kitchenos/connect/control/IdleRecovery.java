package com.kitchenos.connect.control;

import org.json.*;

/** Evidence from one exclusive read-only probe, never from cached UI telemetry. */
public final class IdleRecovery {
    private final ControlState observed=new ControlState();
    private boolean ready,closed,invalid;
    private int quietSamples;
    public void accept(String line,long at){
        try{
            JSONObject o=new JSONObject(line);String type=o.getString("type");
            if(closed){invalid=true;return;}
            if(type.equals("ready")){
                if(ready||!ControlProfile.ID.equals(o.getString("profileId")))invalid=true;
                ready=true;observed.accept(o,at);
            }else if(type.equals("info")||type.equals("status")){
                if(!ready)invalid=true;
                if(type.equals("status"))for(String key:new String[]{"ihStatus","ihGear","motorStatus"})o.getInt(key);
                observed.accept(o,at);
                if(type.equals("status"))quietSamples=(observed.state==0||observed.state==11)&&observed.error==0&&!observed.ownRun&&observed.ihStatus==0&&observed.ihGear==0&&observed.motorStatus==0&&observed.motorRaw==0?quietSamples+1:0;
            }else if(type.equals("closed")){
                closed=true;
                if(!o.getBoolean("confirmedIdle")||o.getInt("statusSamples")<3||o.getInt("infoSamples")<1)invalid=true;
            }else if(!type.equals("rxbytes"))invalid=true;
        }catch(JSONException e){invalid=true;}
    }
    public boolean confirmed(long now){
        return !invalid&&ready&&closed&&quietSamples>=3&&observed.fresh(now)&&observed.mcuVersion==12&&observed.ihVersion==4&&observed.motorVersion==5;
    }
    public boolean activeOutputs(long now){return observed.fresh(now)&&(observed.ihStatus!=0||observed.ihGear!=0||observed.motorStatus!=0||observed.motorRaw!=0);}
}
