package com.kitchenos.connect.control;
import java.util.*;
import org.json.JSONObject;
/** Pure measurement bookkeeping: no device command or inferred ingredient mass. */
public final class ScaleReadingTracker {
    private final ArrayDeque<Integer> values=new ArrayDeque<>();
    private final ArrayDeque<Long> times=new ArrayDeque<>();
    private Integer baseline;private int lastSample,tareSample,raw;private long observed=-1,tareAt=-1;private boolean valid;
    public void reset(){invalidateSamples();baseline=null;lastSample=0;tareSample=0;observed=tareAt=-1;}
    /** Discard pressure-disturbed samples while retaining the explicit baseline. */
    public void invalidateSamples(){values.clear();times.clear();valid=false;}
    public void observe(ControlState s,long now){
        boolean usable=s.connected&&!s.closing&&s.samples>=3&&s.statusAt>=0&&now>=s.statusAt&&(s.state==0||s.state==11)&&s.error==0&&!s.ownRun&&!s.stopping&&!s.uncertainRun&&s.pendingAction.isEmpty()&&Math.abs((long)s.weightRaw)<=5000;
        if(!usable){reset();return;}
        // A new connection/counter or clock rollback invalidates the old bowl baseline.
        // Keep the last observed counter even while stale so duplicates cannot rebuild stability.
        if(observed>=0&&(s.samples<lastSample||s.statusAt<observed||now<observed))reset();
        if(now-s.statusAt>=500){invalidateSamples();return;}
        // A brief gap on the SAME connection loses stability, not the user's tare.
        if(observed>=0&&s.statusAt-observed>=500)invalidateSamples();
        if(s.samples==lastSample)return;
        valid=true;lastSample=s.samples;observed=s.statusAt;raw=s.weightRaw;values.add(raw);times.add(observed);
        // Keep the former ~600 ms observation window even at a higher polling rate.
        while(values.size()>5&&observed-times.getFirst()>600){values.removeFirst();times.removeFirst();}
    }
    public boolean fresh(long now){return valid&&observed>=0&&now>=observed&&now-observed<500;}
    public boolean stable(long now){return fresh(now)&&values.size()>=5&&observed-times.getFirst()>=400&&Collections.max(values)-Collections.min(values)<=3;}
    private int median(){ArrayList<Integer> sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(sorted.size()/2);}
    public boolean tare(long now){if(!stable(now))return false;baseline=median();raw=baseline;tareSample=lastSample;tareAt=observed;values.clear();times.clear();return true;}
    public boolean hasTare(){return baseline!=null;}
    /** Live display follows every observation; only tare/receipts use the stable median. */
    public int displayedGrams(){return raw-(baseline==null?0:baseline);}
    /** The confirmation label must name the same filtered mass as its receipt. */
    public int stableGrams(){return values.isEmpty()?displayedGrams():median()-(baseline==null?0:baseline);}
    public boolean canConfirm(long now){return baseline!=null&&stable(now)&&lastSample>tareSample&&observed>tareAt&&median()-baseline>0;}
    public JSONObject confirm(String id,int index,Double target,long now,long wallTime)throws Exception{
        if(id==null||id.isEmpty()||!canConfirm(now))throw new IllegalStateException("Für diese Zutat zuerst Tara wählen und ein frisches stabiles Gewicht abwarten.");
        return new JSONObject().put("ingredientId",id).put("ingredientIndex",index).put("grams",median()-baseline).put("unit","g").put("targetGrams",target==null?JSONObject.NULL:target).put("tareRaw",baseline).put("rawWeight",median()).put("tareSampleCounter",tareSample).put("tareObservedElapsedRealtime",tareAt).put("sampleCounter",lastSample).put("observedElapsedRealtime",observed).put("confirmedAtEpochMs",wallTime).put("source","device-scale");
    }
}
