package com.kitchenos.connect.ui;

/** Context starters, not guessed machine presets or a second recipe database. */
public final class FunctionAdvice {
    public static String question(String id){switch(id){
        case "lidless":return "Was möchtest du ohne Deckel kochen und in welcher Menge? Beachte höchstens 100 °C und Stufe 3.";
        case "whip":return "Was möchtest du aufschlagen: Sahne, Eiweiß oder etwas anderes?";
        case "stir":return "Was möchtest du verrühren, und soll es erwärmt werden?";
        case "chop":return "Was möchtest du zerkleinern, welche Menge und wie fein?";
        case "puree":return "Was möchtest du pürieren? Ist es bereits weich gegart und wie warm ist es?";
        case "fry":return "Was möchtest du anbraten, in welcher Menge und mit welchem Fett?";
        case "water":return "Wie viel Wasser möchtest du auf welche Temperatur bringen?";
        case "steam":return "Was möchtest du dämpfen und wie groß sind die Stücke?";
        case "ice":return "Möchtest du Eiswürfel zerkleinern oder gefrorenes Obst verarbeiten?";
        case "grind":return "Möchtest du Nüsse, Getreide oder Gewürze mahlen, und wie fein?";
        case "dough":return "Welchen Teig möchtest du kneten und wie viel Mehl verwendest du?";
        case "eggs":return "Wie viele Eier möchtest du kochen und welchen Gargrad magst du?";
        default:return "Was möchtest du zubereiten, und bei welchem Handgriff kann ich dir helfen?";
    }}
    public static String prompt(String id){return question(id)+" Frage mich zuerst nach dem Lebensmittel und der Menge. Berücksichtige passenden Aufsatz, Ausgangstemperatur, Kühlung, Füllmenge und Ergebnisprüfung. Unterscheide belegte Geräteeinstellungen von Tipps; erfinde keine Freigabe oder automatische Aktion. ";}
}
