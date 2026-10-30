import {requireValue,hash} from './domain.mjs';
import {readFileSync} from 'node:fs';
export const controlProfile=Object.freeze(JSON.parse(readFileSync(new URL('../integrations/control-profile.json',import.meta.url),'utf8')));
const legacy=JSON.parse(readFileSync(new URL('../integrations/control-profile-legacy.json',import.meta.url),'utf8'));
export const legacyProfile=Object.freeze({...legacy,modes:[{id:'normal',secondsMin:legacy.secondsMin,secondsMax:legacy.secondsMax,temperatureOff:true,temperatureMin:legacy.temperatureMin,temperatureMax:legacy.temperatureMax,temperatureStep:1,speedRawMin:legacy.speedRawMin,speedRawMax:legacy.speedRawMax,speedRawStep:legacy.speedRawStep,directions:['clockwise','counterclockwise']}]});
/** Missing capability is a legacy client, never implicit permission for wider outputs. */
export function resolveControlProfile(id){requireValue(id===undefined||id===legacyProfile.id||id===controlProfile.id,'Unbekanntes Steuerprofil');return id===controlProfile.id?controlProfile:legacyProfile;}
export function programKey(stem,profile,revision){return stem+'-'+hash(JSON.stringify([profile.id,revision])).slice(0,12);}
export function profilePromptFor(profile){return profile.id===controlProfile.id?profilePrompt:'Steuerprofil dieser App: 1–600 Sekunden; Heizung aus oder ganzzahlige 35–95 °C; Stufe 0,5–3 in 0,5er-Schritten; clockwise oder counterclockwise. Kein Motor 0, keine höhere Temperatur, längere Laufzeit oder höhere Stufe. Erhalte eine notwendige Verarbeitung als konkreten menschlichen Handgriff; kein stilles Abschwächen eines Maschinenprogramms.';}
export function profileFor(seconds,temp,speed,profile=controlProfile){
  if(!Number.isInteger(seconds)||!Number.isInteger(temp)||!Number.isInteger(speed))return null;
  for(const mode of profile.modes){
    if(seconds<mode.secondsMin||seconds>mode.secondsMax)continue;
    const heat=mode.temperatureValues?mode.temperatureValues.includes(temp):(mode.temperatureOff&&temp===0||temp>=mode.temperatureMin&&temp<=mode.temperatureMax&&temp%mode.temperatureStep===0);
    const direction=speed<0?'counterclockwise':'clockwise';
    const motor=mode.speedRawValues?mode.speedRawValues.includes(speed):Math.abs(speed)>=mode.speedRawMin&&Math.abs(speed)<=mode.speedRawMax&&speed%mode.speedRawStep===0;
    if(heat&&motor&&mode.directions.includes(direction))return mode.id;
  }return null;
}
export function executable(program,profile=controlProfile){return program?.steps?.every(step=>step.kind!=='machine'||profileFor(step.settings?.durationSeconds,step.settings?.temperatureC,step.settings?.speedLevel*10*(step.settings?.direction==='counterclockwise'?-1:1),profile)!==null)===true;}
export const profilePrompt=`Verwende die zusammengehörigen Produktprofile, kein beliebiges Kombinieren ihrer Maximalwerte:
- Normal kochen: 1–7200 Sekunden, Heizung aus oder 35–100 °C, Stufe0,5–3 in0,5er-Schritten, beide Richtungen.
- Messer ohne Heizung: höchstens300 Sekunden, nur vorwärts, Stufe0,5–3 in0,5er-Schritten und ganzzahlige4–10. Topf vor Beginn unter60 °C; keine heißen Flüssigkeiten und keine Rühraufsätze bei hohen Stufen. Aktive Heizanforderung0.
- Braten: höchstens1800 Sekunden,100–180 °C in5-Grad-Schritten, fest Rühren aufStufe1 (counterclockwise). Kein Motor0 und kein Braten mit frei gewählter hoher Stufe. Der Sollwert ist der Topfbodensensor, keine garantierte Temperatur des Lebensmittels.
Neue Temperaturvorgaben in5-Grad-Schritten. Vorhandene normale Ganzzahlwerte35–100 bleiben als Alt-Rezeptwerte erhalten und werden nicht still gerundet. Prüfe jede Kombination gegen dieses Profil. Keine automatisch verlängerten Läufe. Rühraufsätze haben eigene Zubehörgrenzen; eine Stufenfreigabe ist keine Eignung jedes Aufsatzes. Das Rezept bleibt ein Vorschlag und ist kein behaupteter Kochversuch.`;
