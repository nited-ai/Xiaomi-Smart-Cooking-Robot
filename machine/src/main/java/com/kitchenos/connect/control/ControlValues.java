package com.kitchenos.connect.control;

/** Same bounds for local circles and validated, non-exported guided-step intents. */
public final class ControlValues {
    public final int seconds, temperature, speed;
    public final boolean lidless,manual,turbo;
    public final boolean waterHeating,heatBoost;
    /** Selected egg time starts at the first confirmed 100 °C status; native preheat is capped. */
    public final int eggSeconds;
    public static ControlValues eggs(int selectedSeconds){
        if(selectedSeconds<1||selectedSeconds>1800)throw new IllegalArgumentException("Eier-Garzeit: 1 Sekunde bis 30 Minuten.");
        return new ControlValues(EggCooking.PREHEAT_SECONDS,100,0,false,true,false,false,true,selectedSeconds);
    }
    public static ControlValues eggsAtBoil(int selectedSeconds){
        if(selectedSeconds<1||selectedSeconds>1800)throw new IllegalArgumentException("Eier-Garzeit: 1 Sekunde bis 30 Minuten.");
        return new ControlValues(selectedSeconds,100,0,false,true,false,false,true,selectedSeconds);
    }
    public static ControlValues eggsRemaining(int selectedSeconds,int remainingSeconds){
        if(selectedSeconds<1||selectedSeconds>1800||remainingSeconds<1||remainingSeconds>1800)throw new IllegalArgumentException("Eier-Garzeit: 1 Sekunde bis 30 Minuten.");
        return new ControlValues(remainingSeconds,100,0,false,true,false,false,true,selectedSeconds);
    }
    public static ControlValues water(int temperature){if(temperature<35||temperature>100)throw new IllegalArgumentException("Wasser: 35–100 °C");return new ControlValues(600,temperature,0,false,true,false,true,true);}
    public ControlValues(int seconds,int temperature,int speed) {
        this(seconds,temperature,speed,false);
    }
    public ControlValues(int seconds,int temperature,int speed,boolean lidless) {this(seconds,temperature,speed,lidless,false);}
    public ControlValues(int seconds,int temperature,int speed,boolean lidless,boolean manual) {
        this(seconds,temperature,speed,lidless,manual,false);
    }
    public static ControlValues turbo(int seconds,int speed){return new ControlValues(seconds,0,speed,false,false,true);}
    private ControlValues(int seconds,int temperature,int speed,boolean lidless,boolean manual,boolean turbo) {this(seconds,temperature,speed,lidless,manual,turbo,false,false);}
    private ControlValues(int seconds,int temperature,int speed,boolean lidless,boolean manual,boolean turbo,boolean waterHeating,boolean heatBoost) {this(seconds,temperature,speed,lidless,manual,turbo,waterHeating,heatBoost,0);}
    private ControlValues(int seconds,int temperature,int speed,boolean lidless,boolean manual,boolean turbo,boolean waterHeating,boolean heatBoost,int eggSeconds) {
        this.waterHeating=waterHeating;this.heatBoost=heatBoost;
        if(turbo&&(lidless||manual||!ControlProfile.supportsIn(ControlProfile.TURBO,seconds,temperature,speed)))throw new IllegalArgumentException("Turbo: 1 oder 2 Sekunden, Stufe 10, 11 oder 12, Heizung aus.");
        if(lidless&&manual)throw new IllegalArgumentException("Manueller Modus benötigt den geschlossenen Deckel.");
        if(lidless&&!ControlProfile.supportsIn(ControlProfile.NORMAL,seconds,temperature,speed))
            throw new IllegalArgumentException("Ohne Deckel kochen: höchstens 100 °C und Stufe 3, maximal 120 Minuten.");
        if(!turbo&&(manual?!ControlProfile.supportsManual(seconds,temperature,speed):!ControlProfile.supportsLocal(seconds,temperature,speed)))
            throw new IllegalArgumentException(manual?ControlProfile.manualReason(seconds,temperature,speed):ControlProfile.reason(seconds,temperature,speed));
        this.seconds=seconds;this.temperature=temperature;this.speed=speed;this.lidless=lidless;this.manual=manual;this.turbo=turbo;this.eggSeconds=eggSeconds;
    }
    public ControlValues boostHeating(){
        if(temperature<=0||turbo||lidless)throw new IllegalArgumentException("Für diese Einstellung ist kein Heizvorlauf freigegeben.");
        return new ControlValues(seconds,temperature,speed,lidless,manual,turbo,waterHeating,true,eggSeconds);
    }
    public ControlValues withoutHeatBoost(){return new ControlValues(seconds,temperature,speed,lidless,manual,turbo,waterHeating,false,eggSeconds);}
    public ControlValues withTemperature(int value){return new ControlValues(seconds,value,speed,lidless,manual,turbo,waterHeating,heatBoost,eggSeconds);}
    public static ControlValues fromInputs(String minutes,String seconds,int temperatureProgress,int speedProgress,boolean reverse) {
        int m,s;
        try{m=Integer.parseInt(minutes);s=Integer.parseInt(seconds);}catch(NumberFormatException e){throw new IllegalArgumentException("Bitte Minuten und Sekunden als ganze Zahlen eingeben.");}
        if(m<0||m>10||s<0||s>59||temperatureProgress<0||temperatureProgress>61||speedProgress<0||speedProgress>5)
            throw new IllegalArgumentException("Zeit oder Reglerwert liegt außerhalb des manuellen Bereichs.");
        return new ControlValues(m*60+s,temperatureProgress==0?0:temperatureProgress+34,(speedProgress+1)*5*(reverse?-1:1));
    }
    public String command(String action,boolean potConfirmed) {
        if(turbo&&!action.equals("START"))throw new IllegalArgumentException("Turbo ist ein einzelner Impuls. Für neue Werte zuerst stoppen und erneut starten.");
        if(action.equals("APPLY_SETTINGS"))return action+" "+temperature+" "+speed;
        if(!action.equals("START")&&!action.equals("RESUME")&&!action.equals("APPLY"))throw new IllegalArgumentException("Unbekannte Steueraktion");
        boolean starts=action.equals("START")||action.equals("RESUME");
        if(starts&&!potConfirmed)throw new IllegalArgumentException("Topf vor jedem Start und Fortsetzen bestätigen.");
        return (turbo?"START_TURBO":manual&&action.equals("START")?"START_MANUAL":lidless&&action.equals("START")?"START_OPEN":action)+" "+seconds+" "+temperature+" "+speed+(starts?" POT_CONFIRMED":"");
    }
}
