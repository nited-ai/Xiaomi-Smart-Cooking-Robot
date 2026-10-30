import {requireValue} from './domain.mjs';
/** Shared authoring rules for creation AND adaptation. No device commands or profile changes. */
export const RECIPE_GUIDANCE=`Kochablauf für ein Display mit Drehknopf:
- Beginne mit der ersten konkreten Handlung. Kein Schritt „Vor dem Start“, keine Kalomatik-Fassungsbeschreibung, keine generische Topf-/Deckelkontrolle als eigener Schritt. Ein tatsächlich nötiger Werkzeugwechsel bleibt konkret beim passenden Arbeitsschritt.
- Jede Zugabe nennt Zutat, Menge, Zielgefäß und Zustand: z.B. „Butter in Würfeln zur Schokolade in den Topf einwiegen“ oder „Eier zur höchstens handwarmen Masse in den Topf geben“. Späteres Rühren darf eine vorher noch nicht erklärte Zugabe nicht voraussetzen.
- Einwiegen ist ein Schritt mit Gewicht und Weiter; keine nachfolgenden Schritte zum Übernehmen, Abhaken oder Bestätigen derselben Zutat. Zutaten mit gleichem Ziel und Zeitpunkt dürfen gemeinsam in einem Einwiegeschritt stehen.
- Schokolade nur so weit von Hand teilen, wie es das folgende Verfahren braucht. Sage ausdrücklich, ob sie zerkleinert oder zusammen mit Butter geschmolzen wird. Reihenfolge, Abkühlen vor Eiern, Umfüllen und Ergebnisprüfung müssen zusammenpassen.
- Vorverarbeitung konkret dimensionieren, z.B. Schokolade entlang der Rillen in etwa 2–3 cm große Stücke teilen. Geeignetes Zerkleinern durch die Maschine ist ein eigener kurzer Messer-Schritt mit Ergebnisprüfung; keine ungeprüfte Stufe erfinden. Ein Schmelzschritt ersetzt kein behauptetes Häckseln.
- Wähle die Bewegung nach dem Ergebnis: Zerkleinern/Pürieren mit Messer; sanftes Einarbeiten, Unterheben und der Erhalt von Stücken mit Rühren (counterclockwise). Ein Rühraufsatz ist ein eigenes Zubehör und nicht dasselbe wie das Umstellen der Drehrichtung. Werkzeugwechsel ausdrücklich bei Stillstand beschreiben. Keine hohen Stufen mit eingesetztem Rühraufsatz.
- Mache vor der Ausgabe eine Plausibilitätsprüfung: Wurde jede Zutat vorher tatsächlich in das richtige Gefäß gegeben? Sind Eier erst nach dem beschriebenen Abkühlen an der Schokolade? Stimmen Werkzeug, Bewegungsart und gewünschte Konsistenz überein? Sind Topfinhalt, Umfüllen, externe Hitzequelle, Zeiten und Ergebnisprüfungen konsistent? Korrigiere Widersprüche vor der Ausgabe. Unklare Angaben führen zu einer konkreten Rückfrage statt zu erfundenen Werten.
- Bilder werden aus der konkreten Handlung erzeugt: lined baking pan, gebrochene Schokolade im Topf, abkühlende Masse. Ein Uhr-/Dokument-/Ofen-Symbol ist kein Schrittbild. Erfinde keine Bild-URLs und keine sichtbare Gerätefunktion.
- Strukturiere jeden Schritt gleich: konkrete Handlung als Titel, klare kurze Anleitung, danach konkrete Prüfkriterien, ein optionaler passender Tipp oder eine echte Alternative. Temperatur, Heizart (Ober-/Unterhitze oder Umluft), Zeit und Formabmessungen ausdrücklich und getrennt benennen, damit die Hochformatansicht sie mit passenden Symbolen als Faktenliste darstellen kann. Kein zusätzlicher Link oder Aufruf zum Assistenten innerhalb der Anleitung. Keine beliebigen Fülltexte oder erfundenen Werte für mehr Bildschirmfläche.
- Formgrößen inklusive Formtyp und Rand nennen, z.B. quadratische 20×20-cm-Backform, kein Backblech. Timer nennt nach Ablauf ein überprüfbares Ergebnis; die Zeit allein bestätigt keine Garung.
- 1–20 Portionen sind eine Wunschmenge, keine Freigabe für beliebige Topffüllungen. Prüfe die gesamte Füllmenge gegen die belegte Gerätekapazität; bei unklarer oder zu großer Menge beim Anpassen compatible false, beim Neuerstellen unavailable mit konkreter Erklärung. Keine bloße Multiplikation der Maschineneinstellungen und keine unbemerkten mehrfachen Chargen.
- Verwende ausschließlich das beigefügte App-Steuerprofil. Ein Rezeptwunsch nach Motorstillstand, 100/180 °C oder Eier direkt im Topf ist kein Nachweis eines ausführbaren Programms. Keine Garzeiten aus einem ungeprüften Eiergewicht ableiten.`;

/** Deterministic backup for observable contradictions; not a claim of a cooking trial. */
export function checkRecipePlausibility(source,answer){
  let heatedChocolate=false,cooled=false;
  const ingredients=new Map((source.ingredients??[]).map(i=>[i.id,i.name]));
  for(const step of answer.steps??[]){
    const text=`${step.title??''} ${step.instruction??''}`,settings=step.settings??{};
    const additions=(step.ingredientUses??[]).map(u=>ingredients.get(u.ingredientId)??'');
    if(/abkühl|handwarm|höchstens\s+3[05]\s*°/i.test(text))cooled=true;
    if(additions.some(name=>/schokolad|kuvertüre/i.test(name)))heatedChocolate=false;
    if(step.kind==='machine'){
      requireValue(!(answer.tools?.includes("stirring-attachment")&&/schlag|sahne.{0,20}steif|rühraufsatz|schneebesen/i.test(text)&&settings.speedLevel>3),"Die hohe Messerstufe passt nicht zum Rühraufsatz dieses Schritts",502);
      if(settings.temperatureC>=50&&(heatedChocolate||(source.title??'').match(/brownie|schokolad/i))){heatedChocolate=true;cooled=false;}
      requireValue(!(/unterheben|behutsam|sanft(?:\s+verrühren|\s+einrühren)/i.test(text)&&settings.direction==='clockwise'),'Sanftes Unterheben braucht eine passende Rührbewegung statt einer schneidenden Verarbeitung',502);
      requireValue(!(/rühraufsatz|schneebesen/i.test(text)&&settings.speedLevel>3),'Die Stufe passt nicht zum beschriebenen Rühraufsatz',502);
    }
    if(heatedChocolate&&additions.some(name=>/^eier?$|hühnerei/i.test(name.trim())))requireValue(cooled,'Vor der Zugabe der Eier fehlt das Abkühlen der Schokoladenmasse',502);
  }
}
