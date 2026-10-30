package com.kitchenos.connect.control;

/** KitchenOS trial recipes, not claimed to reproduce a manufacturer's motor curves. */
public enum CleaningProgram {
    QUICK("Kurz",21,45,100,"Rund 30 Sekunden: je Richtung ansteigend 1 → 2 → 3 → 6 → 10, mit kurzen Haltezeiten. Nach Stillstand umkehren, zuletzt auf Stufe 5 nachspülen.","Mindestens 500 ml, höchstens 1 l Wasser. Ohne Aufsätze; Deckel und Messbecher einsetzen. Stillstandswechsel kommen zur Programmzeit hinzu."),
    DOUGH("Teig",135,0,100,"Für rohe Mehl- und Teigreste; ohne Heizung.","Kaltes Wasser verwenden. Große Teigreste vorher entfernen."),
    BASIC("Basis",225,55,100,"Für Reis, Stärke und alltägliche Speisereste.","Wenig geeignetes Handspülmittel. Einweichen und kräftig spülen."),
    FAT("Fett / Karamell",360,85,60,"Für Fettfilme und lösliche Zuckerreste nach dem Braten.","Zunächst mit Wasser testen. Nur wenig für das Gerät geeignetes Mittel; bei Schaum stoppen."),
    INTENSIVE("Reinigung+",315,95,60,"Für anhaftende Rückstände am inneren Topfboden.","Zunächst nur Wasser; kein Handspülmittel im heißen Programm. Ergebnis prüfen und nötigenfalls von Hand nachreinigen.");

    public final String title,purpose,tip;
    public final int seconds,temperature,maxSpeed;
    CleaningProgram(String title,int seconds,int temperature,int maxSpeed,String purpose,String tip){this.title=title;this.seconds=seconds;this.temperature=temperature;this.maxSpeed=maxSpeed;this.purpose=purpose;this.tip=tip;}
    public static CleaningProgram at(int index){return values()[Math.max(0,Math.min(values().length-1,index))];}
    public String information(){return purpose+"\n"+(seconds<60?seconds+" Sekunden":seconds/60+" Min."+(seconds%60==0?"":" "+seconds%60+" Sek."))+" geplante Phasenzeit · "+(temperature==0?"Heizung aus":temperature+" °C Ziel")+" · bis Stufe "+maxSpeed/10+"\n"+tip;}
    public String[] buttonLabels(){return new String[]{"Kurz","Teig","Basis","Fett /\nKaramell","Reinigung+"};}
    /** Approximate total including bounded stops; this is not a heating-time promise. */
    public String selectionDuration(){switch(this){case QUICK:return "ca. 30 Sek.";case DOUGH:return "ca. 3 Min.";case BASIC:return "ca. 5 Min.";case FAT:return "ca. 7 Min.";default:return "ca. 6 Min.";}}
    public String selectionSummary(){switch(this){
        case QUICK:return "Frische, leichte Reste ausspülen.";
        case DOUGH:return "Mehl und Teig kalt lösen.";
        case BASIC:return "Reis, Stärke und Alltagsreste.";
        case FAT:return "Fett und Zuckerreste lösen.";
        default:return "Topfboden heiß einweichen.";
    }}
}
