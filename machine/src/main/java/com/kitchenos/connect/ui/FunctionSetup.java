package com.kitchenos.connect.ui;
import java.util.*;

/** Function choices inside the pinned contract, never a new hardware capability. */
public final class FunctionSetup {
 public final String id,limits,unavailable;
 public final int seconds,temperature,speed,maxSeconds,minTemperature,maxTemperature,minSpeed,maxSpeed;
 public final boolean reverse,lockedDirection;
 private static final Map<String,FunctionSetup> ALL=new LinkedHashMap<>();
 private FunctionSetup(String id,int sec,int temp,int speed,int maxSec,int minTemp,int maxTemp,int minSpeed,int maxSpeed,boolean reverse,boolean fixed,String limits,String unavailable){this.id=id;seconds=sec;temperature=temp;this.speed=speed;maxSeconds=maxSec;minTemperature=minTemp;maxTemperature=maxTemp;this.minSpeed=minSpeed;this.maxSpeed=maxSpeed;this.reverse=reverse;lockedDirection=fixed;this.limits=limits;this.unavailable=unavailable;ALL.put(id,this);}
 private static void add(String id,int sec,int temp,int speed,int maxSec,int minTemp,int maxTemp,int minSpeed,int maxSpeed,boolean reverse,boolean fixed,String limits){new FunctionSetup(id,sec,temp,speed,maxSec,minTemp,maxTemp,minSpeed,maxSpeed,reverse,fixed,limits,"");}
 private static void pending(String id,String reason){new FunctionSetup(id,0,0,0,0,0,0,0,0,false,true,"",reason);}
 static {
  add("manual",5,0,5,7200,0,150,0,100,true,false,"");
  pending("scale","");
  add("stir",60,0,10,7200,0,100,5,10,true,true,"Messerabdeckung · Rühren 0,5–1 · höchstens 100 °C · ausreichend Flüssigkeit · bis 120 Min.");
  add("water",600,100,0,7200,35,100,0,0,false,true,"35–100 °C · Messer steht still · Stopp bei Zieltemperatur. Internes Zeitlimit 10 Minuten.");
  add("fry",300,140,10,1800,100,180,10,10,true,true,"105–180 °C · rückwärts Stufe 1 · Rührintervalle oder durchgehend · höchstens 30 Min.");
  add("whip",60,0,30,300,0,0,5,40,false,true,"Heizung aus · Stufe 0,5–4 · höchstens 5 Min. Mit Rühraufsatz nie über Stufe 4.");
  add("clean",300,55,30,480,0,95,30,30,false,true,"Fünf automatische Programme · 2–8 Minuten · kalt bis 95 °C");
  add("mix",30,0,50,300,0,0,5,100,false,true,"Heizung aus · Messer 0,5–10 · höchstens 5 Min. Hohe Messerstufen nur bei Topftemperatur unter 60 °C.");
  add("emulsify",60,0,30,300,0,0,5,40,false,true,"Heizung aus · Messer 0,5–4 · höchstens 5 Min.");
  add("warm",7200,70,5,7200,35,90,5,15,true,true,"35–90 °C · nur rückwärts 0,5 / 1 / 1,5 · manuell stoppen · Sicherheitsende nach 120 Min.");
  add("lidless",600,95,10,7200,35,100,0,30,true,true,"35–100 °C · stillstehendes Messer oder Rühren bis 3 · bis 120 Min.");
  pending("eggs","Das automatische Eierprogramm ist noch nicht angebunden. Eieranzahl und Gargrad lassen sich vormerken; daraus wird noch keine Garzeit berechnet.");
  pending("rice","Der abgestimmte Reisablauf für den Gareinsatz ist noch nicht angebunden. Ein einfacher Heizlauf ersetzt dieses Programm nicht.");
  pending("dough","Das Knetprogramm mit wechselnden Messerbewegungen ist noch nicht angebunden.");
  pending("steam","Der vollständige Dampfgarablauf ist noch nicht angebunden. Ein manueller Heizlauf ersetzt die Ablaufüberwachung dieses Programms nicht.");
  add("chop",6,0,60,60,0,0,10,100,false,true,"Heizung aus · Messer 1–10 · kurze Intervalle, höchstens 1 Min. Hohe Messerstufen nur unter 60 °C.");
  add("puree",30,0,60,300,0,0,60,100,false,true,"Heizung aus · Zielstufe 6–10 · sanfter Hochlauf · höchstens 5 Min. Vor hohen Stufen den Topf auf unter 60 °C abkühlen lassen.");
  pending("grind","Das spezielle Mahlprogramm ist noch nicht angebunden. Es wird nicht durch einen ungeprüften Messerlauf ersetzt.");
  pending("ice","Das spezielle Eisprogramm mit seinen Lastgrenzen ist noch nicht angebunden.");
  add("slow",3600,90,5,28800,37,98,5,5,true,true,"1–8 Stunden · 37–98 °C · rückwärts 0,5 mit kurzen Rührphasen und Pausen.");
  pending("sousvide","Für Sous-vide fehlen noch der geprüfte Wasserbadablauf und die zugehörige Zubehörfreigabe. Deshalb gibt es hier noch keinen Start.");
  pending("ferment","Das überwachte Langzeitprogramm ist noch nicht angebunden. Ein auf zwei Stunden begrenzter Heizlauf ersetzt die Fermentation nicht.");
  add("thicken",720,100,20,720,80,100,20,20,false,true,"Ei: 80 °C · 10 Min. Mehl/Stärke: 100 °C · 12 Min. Messer intern Stufe 2; keine automatische Mengen- oder Konsistenzerkennung.");
  add("turbo",1,0,100,2,0,0,100,120,false,true,"1–2 Sekunden · Stufe 10, 11 oder 12 · Heizung aus · Topf unter 60 °C. Nur mit Messer, ohne Messerabdeckung oder Rühraufsatz.");
  pending("caramel","Der mengenabhängige Zuckerablauf für diese Maschine ist noch nicht geprüft. Deshalb bleibt der Start gesperrt; ein Anbraten-Lauf ersetzt das Karamellprogramm nicht.");
 }
 public static FunctionSetup get(String id){FunctionSetup s=ALL.get(id);if(s==null)throw new IllegalArgumentException("Unknown function: "+id);return s;}
 public static List<String> ids(){return new ArrayList<>(ALL.keySet());}
 public boolean runnable(){return unavailable.isEmpty()&&!id.equals("scale");}
 public int[] defaults(){return new int[]{seconds,temperature,speed};}
}
