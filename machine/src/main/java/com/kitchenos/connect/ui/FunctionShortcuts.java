package com.kitchenos.connect.ui;

import java.util.ArrayList;
import java.util.function.IntPredicate;

/** Quick choices describe the selected function, within the current dial's limits. */
public final class FunctionShortcuts {
    public static int[] choices(HomeFunctionProfile profile,int kind,int part,int minimum,int maximum,IntPredicate accepts){
        boolean slow=profile.id.equals("slow"),turbo=profile.id.equals("turbo");
        int[] candidates;
        if(kind==0){
            candidates=turbo?new int[]{1,2}:slow?(part==0?new int[]{1,2,3,4}:new int[]{5,10,15,30}):new int[]{1,5,10,20,30};
            int unit=slow?(part==0?3600:60):(part==0?60:1);
            ArrayList<Integer> result=new ArrayList<>();
            for(int value:candidates)if(turbo?accepts.test(value):value*unit<=maximum)result.add(value);
            return array(result);
        }
        ArrayList<Integer> valid=new ArrayList<>();
        if(kind==2){
            for(int value=minimum;value<=maximum;value++)if(accepts.test(value))valid.add(value);
            if(valid.size()<=5)return array(valid);
            candidates=maximum<=40?new int[]{5,10,20,30,40}:new int[]{20,40,60,80,100};
        }else{
            // Manual cooking keeps its explicit entry into frying. A function stays in its own range.
            if(profile.id.equals("manual"))return new int[]{0,60,90,100,180};
            candidates=profile.id.equals("fry")?new int[]{105,120,140,160,180}:slow?new int[]{37,60,80,90,98}:new int[]{minimum,40,60,80,maximum};
        }
        ArrayList<Integer> result=new ArrayList<>();
        for(int value:candidates)if(accepts.test(value)&&!result.contains(value))result.add(value);
        if(result.isEmpty()&&!valid.isEmpty())return array(valid.subList(0,Math.min(5,valid.size())));
        return array(result);
    }
    private static int[] array(java.util.List<Integer> values){int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;}
    private FunctionShortcuts(){}
}
