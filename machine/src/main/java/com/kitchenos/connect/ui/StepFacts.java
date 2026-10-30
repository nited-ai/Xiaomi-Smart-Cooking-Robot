package com.kitchenos.connect.ui;

import java.util.*;
import java.util.regex.*;

/** Extract only literal facts from the immutable instruction. Never infer cooking values. */
public final class StepFacts {
    public static final class Fact {public final String icon,text;Fact(String icon,String text){this.icon=icon;this.text=text;}}
    public static List<Fact> from(String instruction){
        List<Fact> facts=new ArrayList<>();if(instruction==null)return facts;
        Matcher temp=Pattern.compile("(?<![\\d−-])(\\d{2,3})\\s*°\\s*C").matcher(instruction);
        if(temp.find())facts.add(new Fact("temperature",temp.group(1)+" °C"));
        if(instruction.contains("Ober-/Unterhitze")||instruction.contains("Ober- und Unterhitze"))facts.add(new Fact("oven-top-bottom","Ober- / Unterhitze"));else if(instruction.contains("Umluft"))facts.add(new Fact("fan","Umluft"));
        Matcher time=Pattern.compile("(?<!\\d)(\\d+(?:\\s*[–−-]\\s*\\d+)?)\\s*(Minuten?|Stunden?|Sekunden?)",Pattern.CASE_INSENSITIVE).matcher(instruction);
        if(time.find())facts.add(new Fact("timer",time.group(1)+" "+time.group(2)));
        Matcher pan=Pattern.compile("(\\d+\\s*[×x]\\s*\\d+)\\s*cm").matcher(instruction);
        if(pan.find())facts.add(new Fact("dimensions",pan.group(1)+" cm"+(instruction.contains("Backform")?" · Backform":"")+(instruction.contains("mit Rand")?" mit Rand":"")));
        return facts;
    }
}
