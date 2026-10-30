package com.kitchenos.connect.ui;

/** One Home workspace; selected functions constrain the same three controls. */
public final class HomeFunctionProfile {
    public final String id,title,tool,detail;
    public final int seconds,temperature,speed,maxSeconds,minTemperature,maxTemperature,minSpeed,maxSpeed;
    public final boolean reverse,fixedDirection;
    public boolean intervals;
    public com.kitchenos.connect.control.FryingRhythm frying=com.kitchenos.connect.control.FryingRhythm.SHORT;
    public com.kitchenos.connect.control.CleaningProgram cleaning;
    public static HomeFunctionProfile cleaning(com.kitchenos.connect.control.CleaningProgram c){HomeFunctionProfile p=new HomeFunctionProfile("clean","Vorspülen","blade",c.information(),c.seconds,c.temperature,30,c.seconds,c.temperature,c.temperature,30,30,false,true).intervals(true);p.cleaning=c;return p;}
    public HomeFunctionProfile intervals(boolean value){intervals=value&&com.kitchenos.connect.control.FunctionProgram.supported(id);return this;}
    private HomeFunctionProfile(String id,String title,String tool,String detail,int seconds,int temperature,int speed,int maxSeconds,int minTemp,int maxTemp,int minSpeed,int maxSpeed,boolean reverse,boolean fixed){
        this.id=id;this.title=title;this.tool=tool;this.detail=detail;this.seconds=seconds;this.temperature=temperature;this.speed=speed;this.maxSeconds=maxSeconds;minTemperature=minTemp;maxTemperature=maxTemp;this.minSpeed=minSpeed;this.maxSpeed=maxSpeed;this.reverse=reverse;fixedDirection=fixed;
    }
    public static boolean supports(String id){return "manual".equals(id)||"stir".equals(id)||"eggs".equals(id)||"fry".equals(id);}
    public boolean temperatureOnly(){return id.equals("water");}
    public boolean fixedDuration(){return id.equals("thicken");}
    public static int thickenSeconds(int temperature){return temperature==80?600:720;}
    public boolean directControls(){return true;}
    public boolean runnable(){return supports(id)||FunctionSetup.get(id).runnable()||java.util.Arrays.asList("dough","grind","ice","clean","rice","steam").contains(id);}
    public String unavailableReason(){return FunctionSetup.get(id).unavailable;}
    public HomeFunctionProfile lidlessMotion(boolean still){return !id.equals("lidless")?this:new HomeFunctionProfile(id,title,"cover",detail,600,95,still?0:5,7200,35,100,still?0:5,still?0:10,true,true);}
    public static HomeFunctionProfile get(String id){
        switch(id){
            case "dough":return new HomeFunctionProfile(id,"Teig kneten","blade","2 s kneten / 2 s Pause · 3 s / 3 s · 4 s / 2 s · Richtung abwechselnd",180,0,30,300,0,0,30,30,false,true).intervals(true);
            case "grind":return new HomeFunctionProfile(id,"Mahlen","blade","5 s Stufe 8 · 2 s Pause",20,0,80,60,0,0,80,80,false,true).intervals(true);
            case "ice":return new HomeFunctionProfile(id,"Eis zerkleinern","blade","2 s Stufe 6 · 3 s Pause",25,0,60,60,0,0,60,60,false,true).intervals(true);
            case "clean":return cleaning(com.kitchenos.connect.control.CleaningProgram.BASIC);
            case "slow":return new HomeFunctionProfile(id,"Langsam garen","cover","5 s rückwärts 0,5 · 25 s Pause",3600,90,5,28800,37,98,5,5,true,true).intervals(true);
            case "rice":return new HomeFunctionProfile(id,"Reis kochen","basket","Reis im Gareinsatz · Wasser nach passendem Geräte-Rezept",1500,100,0,2400,100,100,0,0,false,true);
            case "steam":return new HomeFunctionProfile(id,"Dampfgaren","steam","Garphase ab Dampf · ausreichend Wasser nach Rezept",1500,100,0,7200,100,100,0,0,false,true);
            case "warm":return new HomeFunctionProfile(id,"Erwärmen","blade","Bis 90 °C · rückwärts 0,5 / 1 / 1,5 · manuell stoppen",7200,70,5,7200,35,90,5,15,true,true);
            case "stir":return new HomeFunctionProfile(id,"Rühren","cover","Messerabdeckung · ausreichend Flüssigkeit · bis 100 °C",60,0,5,7200,0,100,5,10,true,true);
            case "eggs":return new HomeFunctionProfile(id,"Eier kochen","basket","Gareinsatz · Messer steht · Garzeit nach Rezept anpassen",480,100,0,1800,100,100,0,0,false,true);
            case "fry":return new HomeFunctionProfile(id,"Anbraten","blade","Dabeibleiben und Bräunung beobachten · ohne Aufsatz",300,140,10,1800,105,180,10,10,true,true);
            case "manual":return new HomeFunctionProfile("manual","Manuell kochen","blade","Zeit, Temperatur und Stufe frei wählen",5,0,0,7200,0,150,0,100,true,false);
            default:FunctionSetup s=FunctionSetup.get(id);FunctionPageContent.Data content=FunctionPageContent.get(id);return new HomeFunctionProfile(id,content.title,content.tool,s.limits,Math.max(1,s.seconds),s.temperature,s.speed,Math.max(1,s.maxSeconds),s.minTemperature,s.maxTemperature,s.minSpeed,s.maxSpeed,s.reverse,s.lockedDirection);
        }
    }
    public boolean timeControl(){return runnable()&&!temperatureOnly()&&!fixedDuration()&&!id.equals("rice")&&minSeconds()!=maxSeconds;}
    public boolean temperatureControl(){return runnable()&&minTemperature!=maxTemperature;}
    public boolean speedControl(){return runnable()&&!puree()&&!intervals&&minSpeed!=maxSpeed;}
    public boolean puree(){return id.equals("puree");}
    public boolean automatic(){return intervals||puree()||id.equals("fry");}
    public com.kitchenos.connect.control.FunctionProgram program(int seconds,int temperature,int speed){return id.equals("fry")?com.kitchenos.connect.control.FunctionProgram.frying(seconds,temperature,frying):puree()?com.kitchenos.connect.control.FunctionProgram.puree(seconds,speed):cleaning!=null?com.kitchenos.connect.control.FunctionProgram.cleaning(cleaning):com.kitchenos.connect.control.FunctionProgram.create(id,seconds,temperature);}
    public int minSeconds(){return cleaning!=null?cleaning.seconds:id.equals("warm")?7200:id.equals("slow")?3600:id.equals("thicken")?600:id.equals("clean")?90:id.equals("rice")?300:java.util.Arrays.asList("dough","steam","eggs").contains(id)?60:id.equals("grind")?4:id.equals("ice")?5:1;}
    public static final class Preset {public final String label;public com.kitchenos.connect.control.FryingRhythm frying;public com.kitchenos.connect.control.CleaningProgram cleaning;public final int seconds,temperature,speed;Preset(String label,int seconds,int temperature,int speed){this.label=label;this.seconds=seconds;this.temperature=temperature;this.speed=speed;}}
    public Preset[] presets(){switch(id){
        case "clean":com.kitchenos.connect.control.CleaningProgram[] programs=com.kitchenos.connect.control.CleaningProgram.values();Preset[] options=new Preset[programs.length];for(int i=0;i<programs.length;i++){com.kitchenos.connect.control.CleaningProgram c=programs[i];options[i]=new Preset(c.buttonLabels()[i],c.seconds,c.temperature,30);options[i].cleaning=c;}return options;
        case "fry":com.kitchenos.connect.control.FryingRhythm[] rhythms=com.kitchenos.connect.control.FryingRhythm.values();Preset[] choices=new Preset[rhythms.length];for(int i=0;i<rhythms.length;i++){choices[i]=new Preset(rhythms[i].label,seconds,temperature,speed);choices[i].frying=rhythms[i];}return choices;
        case "water":return new Preset[]{new Preset("40 °C",600,40,0),new Preset("60 °C",600,60,0),new Preset("95 °C",600,95,0),new Preset("100 °C",600,100,0)};
        case "rice":return new Preset[]{new Preset("Weißreis",1500,100,0),new Preset("Vollkorn",2400,100,0)};
        case "steam":return new Preset[]{new Preset("Gemüse",1200,100,0),new Preset("Kartoffeln",1800,100,0)};
        case "dough":return new Preset[]{new Preset("Brot / Pizza",180,0,30),new Preset("Weicher Teig",120,0,30)};
        case "chop":return new Preset[]{new Preset("Grob",3,0,40),new Preset("Mittel",6,0,60),new Preset("Fein",8,0,70)};
        case "puree":return new Preset[]{new Preset("Grob",30,0,60),new Preset("Cremig",30,0,80),new Preset("Fein",30,0,100)};
        case "whip":return new Preset[]{new Preset("Sahne",60,0,30),new Preset("Eiweiß",120,0,35)};
        case "thicken":return new Preset[]{new Preset("Ei · 80 °C · 10 Min.",600,80,20),new Preset("Mehl/Stärke · 100 °C · 12 Min.",720,100,20)};
        default:return new Preset[0];
    }}
    public String issue(int seconds,int temperature,int signedSpeed){
        if(id.equals("turbo"))return seconds>=1&&seconds<=2&&temperature==0&&(signedSpeed==100||signedSpeed==110||signedSpeed==120)?"":"Turbo: 1 oder 2 Sekunden und Stufe 10, 11 oder 12 wählen.";
        if(!runnable())return id.equals("scale")?"Die Waage befindet sich oben am Mixtopf.":unavailableReason();
        if(fixedDuration()&&(temperature!=80&&temperature!=100||seconds!=thickenSeconds(temperature)||signedSpeed!=20))return "Andicken: Ei bei 80 °C für 10 Minuten oder Mehl/Stärke bei 100 °C für 12 Minuten; Messerstufe 2 ist fest.";
        if(id.equals("rice")&&(seconds!=1500&&seconds!=2400||temperature!=100||signedSpeed!=0))return "Reiskochen: Weißreis 25 oder Vollkorn 40 Minuten, 100 °C und Messerstillstand sind fest.";
        if(id.equals("slow")&&(seconds<3600||seconds>28800||seconds%60!=0||signedSpeed!=-5))return "Langsam garen: 1 bis 8 Stunden in Minutenschritten, fest rückwärts Stufe 0,5.";
        if(seconds<minSeconds()||seconds>maxSeconds)return "Wähle eine Zeit bis "+maxSeconds/60+" Minuten.";
        if(temperature<minTemperature||temperature>maxTemperature||temperature>0&&temperature<35)return "Temperatur: "+(minTemperature==0?"aus oder 35":minTemperature)+"–"+maxTemperature+" °C.";
        int magnitude=Math.abs(signedSpeed);
        if(magnitude<minSpeed||magnitude>maxSpeed||fixedDirection&&signedSpeed!=0&&(signedSpeed<0)!=reverse)return id.equals("eggs")?"Beim Eierkochen bleibt das Messer stehen.":id.equals("stir")?"Mit Messerabdeckung nur rückwärts auf 0,5 oder 1 rühren.":"Stufe oder Richtung passt nicht zu dieser Funktion.";
        if(puree()&&signedSpeed%10!=0)return "Pürieren: ganze Zielstufe von 6 bis 10 wählen.";
        if(id.equals("fry"))try{program(seconds,temperature,signedSpeed);}catch(IllegalArgumentException e){return e.getMessage();}
        if(intervals)try{if(cleaning!=null)com.kitchenos.connect.control.FunctionProgram.cleaning(cleaning);else com.kitchenos.connect.control.FunctionProgram.create(id,seconds,temperature);}catch(IllegalArgumentException e){return e.getMessage();}
        return "";
    }
    public static int eggSeconds(int grade){return new int[]{360,480,600,720}[Math.max(0,Math.min(3,grade))];}
    private HomeFunctionProfile(){throw new AssertionError();}
}
