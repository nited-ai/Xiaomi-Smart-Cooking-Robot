package com.kitchenos.connect.control;

import java.util.*;

/** Finite, inspectable function sequences. Speeds use the controller's tenths. */
public final class FunctionProgram {
    public static final class SpeedChange {
        public final int second,speed;
        SpeedChange(int second,int speed){this.second=second;this.speed=speed;}
    }
    public static final class Phase {
        public final String name; public final int seconds,temperature,speed;
        public final List<SpeedChange> changes;
        private final boolean manual;
        public final boolean motorOnlyChanges;
        Phase(String name,int seconds,int temperature,int speed){this(name,seconds,temperature,speed,new int[0][]);}
        Phase(String name,int seconds,int temperature,int speed,int[][] curve){
            this(name,seconds,temperature,speed,curve,true);
        }
        private Phase(String name,int seconds,int temperature,int speed,int[][] curve,boolean manual){
            this.manual=manual;this.motorOnlyChanges=!manual;
            this.name=name;this.seconds=seconds;this.temperature=temperature;this.speed=speed;
            List<SpeedChange> next=new ArrayList<>();int previous=0;
            for(int[] point:curve){
                if(point.length!=2||point[0]<=previous||point[0]>=seconds||speed==0||
                    (motorOnlyChanges?(speed!=-10||(point[1]!=0&&point[1]!=-10)):Integer.signum(point[1])!=Integer.signum(speed)))throw new IllegalArgumentException("Ungültige gleichgerichtete Stufenfolge");
                if(!motorOnlyChanges)new ControlValues(seconds,temperature,point[1],false,true);
                next.add(new SpeedChange(point[0],point[1]));previous=point[0];
            }
            changes=Collections.unmodifiableList(next);
        }
        public boolean waiting(){return temperature==0&&speed==0;}
        public ControlValues values(){return new ControlValues(seconds,temperature,speed,false,manual);}
    }
    public final String id; public final List<Phase> phases; public final int seconds;
    private FunctionProgram(String id,List<Phase> phases){this.id=id;this.phases=Collections.unmodifiableList(phases);int sum=0;for(Phase p:phases){sum+=p.seconds;if(!p.waiting())p.values();}seconds=sum;}
    public static boolean supported(String id){return Arrays.asList("stir","slow","dough","grind","ice","clean").contains(id);}
    public static FunctionProgram create(String id,int seconds,int temperature){
        int min=id.equals("clean")?90:id.equals("slow")?3600:id.equals("dough")?60:id.equals("grind")?4:id.equals("ice")?5:1;
        int max=id.equals("slow")?28800:id.equals("stir")?7200:id.equals("grind")||id.equals("ice")?60:300;
        if(!supported(id)||seconds<min||seconds>max)throw new IllegalArgumentException("Ungültige Programmdauer");
        if(id.equals("clean")?(temperature<35||temperature>50):id.equals("slow")?(temperature<37||temperature>98):id.equals("stir")?(temperature!=0&&(temperature<35||temperature>100)):temperature!=0)throw new IllegalArgumentException("Ungültige Programmtemperatur");
        if(id.equals("slow")&&seconds%60!=0)throw new IllegalArgumentException("Langsam garen: nur ganze Minuten wählen.");
        List<Phase> out=new ArrayList<>();
        if(id.equals("clean")){
            add(out,"Vorspülen",4,temperature,30);add(out,"Einweichen",18,temperature,0);
            add(out,"Kräftig spülen",5,0,70);add(out,"Einweichen",18,temperature,0);
            for(int i=0;i<4;i++){add(out,"Spülimpuls",1,0,70);add(out,"Pause",2,temperature,0);}
            add(out,"Einweichen",18,temperature,0);add(out,"Nachspülen",seconds-75,temperature,30);
        }else{
            int[][] cycle=id.equals("dough")?new int[][]{{2,30},{2,0},{3,-30},{3,0},{4,30},{2,0},{2,-30},{2,0},{3,30},{3,0},{4,-30},{2,0}}:id.equals("grind")?new int[][]{{5,80},{2,0}}:id.equals("ice")?new int[][]{{2,60},{3,0}}:new int[][]{{5,-5},{25,0}};
            int left=seconds,index=0;while(left>0){int[] p=cycle[index++%cycle.length];int duration=Math.min(left,p[0]);add(out,p[1]==0?"Pause":id.equals("dough")?(p[1]<0?"Kneten rückwärts":"Kneten vorwärts"):id.equals("grind")?"Mahlen":id.equals("ice")?"Eis zerkleinern":"Sanft rühren",duration,temperature,p[1]);left-=duration;}
        }
        return new FunctionProgram(id,out);
    }
    /** One FRY run; automatic rests change only the motor, never the heat or deadline. */
    public static FunctionProgram frying(int seconds,int temperature,FryingRhythm rhythm){
        if(rhythm==null||seconds<1||seconds>1800||temperature<105||temperature>180||temperature%5!=0)
            throw new IllegalArgumentException("Anbraten: bis 30 Minuten, 105–180 °C in 5-Grad-Schritten.");
        List<int[]> curve=new ArrayList<>();
        if(rhythm!=FryingRhythm.CONTINUOUS){
            int due=rhythm.stirSeconds;boolean rest=true;
            while(due<seconds){curve.add(new int[]{due,rest?0:-10});due+=rest?rhythm.pauseSeconds:rhythm.stirSeconds;rest=!rest;}
        }
        return new FunctionProgram("fry",Collections.singletonList(new Phase("Rückwärts rühren · Stufe 1",seconds,temperature,-10,curve.toArray(new int[0][]),false)));
    }
    /** One bounded run; updates never reset its timer. Short runs end during the ramp. */
    public static FunctionProgram puree(int seconds,int target){
        if(seconds<1||seconds>300||target<60||target>100||target%10!=0)throw new IllegalArgumentException("Pürieren: 1–300 Sekunden und Zielstufe 6–10 wählen.");
        List<int[]> curve=new ArrayList<>();
        for(int stage=2;stage<=target/10&&stage-1<seconds;stage++)curve.add(new int[]{stage-1,stage*10});
        return new FunctionProgram("puree",Collections.singletonList(new Phase("Sanft pürieren",seconds,0,10,curve.toArray(new int[0][]))));
    }
    public static FunctionProgram cleaning(CleaningProgram program){
        if(program==CleaningProgram.QUICK)return quickCleaning(program.temperature);
        List<Phase> out=new ArrayList<>();int heat=program.temperature;
        // Deliberate low-speed beginning, then soaking/heating, alternating passes,
        // and a stronger finish. Every phase ends with confirmed zero outputs.
        addClean(out,"Wasser verteilen",8,heat,20);
        int soak=program==CleaningProgram.DOUGH?34:program==CleaningProgram.BASIC?90:180;
        addClean(out,heat==0?"Kalt einweichen":"Erwärmen und einweichen",soak,heat,0);
        int repeats=program==CleaningProgram.DOUGH?3:4;
        for(int i=0;i<repeats;i++){
            addClean(out,"Spülen vorwärts",8,heat,40);
            addClean(out,"Stillstand",2,0,0);
            addClean(out,"Spülen rückwärts",8,heat,-30);
            addClean(out,"Einwirken lassen",5,heat,0);
        }
        addClean(out,"Kräftig spülen",8,heat,program.maxSpeed);
        addClean(out,"Stillstand",3,0,0);
        addClean(out,"Abschlussimpuls",5,heat,program.maxSpeed);
        int used=0;for(Phase phase:out)used+=phase.seconds;
        addClean(out,"Abschließend spülen",program.seconds-used,heat,30);
        return new FunctionProgram("clean",out);
    }
    private static FunctionProgram quickCleaning(int heat){
        List<Phase> out=new ArrayList<>();
        for(int direction:new int[]{1,-1}){
            // Hold 2, 3, 6 and 10 for two seconds each; leave the peak before
            // the native stop reserve. Reversal still requires confirmed zero outputs.
            int[][] curve={{1,direction*20},{3,direction*30},{5,direction*60},{7,direction*100},{9,direction*10}};
            out.add(new Phase(direction>0?"Ansteigend vorwärts · 1 bis 10":"Ansteigend rückwärts · 1 bis 10",10,heat,direction*10,curve));
        }
        // One second plus the existing three-second native stop reserve.
        out.add(new Phase("Nachspülen vorwärts · Stufe 5",1,heat,50));
        return new FunctionProgram("clean",out);
    }
    private static void addClean(List<Phase> phases,String name,int seconds,int temp,int speed){if(seconds>0)phases.add(new Phase(name,seconds,temp,speed));}
    private static void add(List<Phase> phases,String name,int seconds,int temp,int speed){phases.add(new Phase(name,seconds,Math.abs(speed)>40?0:temp,speed));}
}
