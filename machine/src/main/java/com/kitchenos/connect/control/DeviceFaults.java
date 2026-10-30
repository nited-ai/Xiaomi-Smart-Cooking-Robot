package com.kitchenos.connect.control;
/** Error mapping verified in original APK 1.6.5 (REA-POT-ANALYSIS-2026-10-19.md).
 * An error message does not prove a separate readable pot-presence bit. */
public final class DeviceFaults {
    public static String describe(int code){
        String reason;
        switch(code){
            case 1:return "Topfdeckel prüfen und richtig schließen (Gerätefehler 1).";
            case 2:return "Topf prüfen und korrekt auf die Maschine setzen (Gerätefehler 2).";
            case 3:reason="Überspannung an der Heizung";break;
            case 4:reason="Unterspannung an der Heizung";break;
            case 5:reason="Topfboden zu heiß. Gerät abkühlen lassen";break;
            case 7:reason="Heizungselektronik zu heiß. Gerät abkühlen lassen";break;
            case 8:reason="Fehler am Temperatursensor der Heizungselektronik";break;
            case 10:reason="Fehler am Temperatursensor des Topfbodens";break;
            case 11:reason="Kommunikation mit der Heizung gestört";break;
            case 12:reason="Fehler an der Induktionsheizung";break;
            case 13:case 14:reason="Topfverriegelung gestört";break;
            case 15:reason="Speicherfehler der Gerätesteuerung";break;
            case 16:reason="Motorüberstrom. Gerät ausschalten und Füllmenge prüfen";break;
            case 17:reason="Überspannung am Motor";break;
            case 18:reason="Unterspannung am Motor";break;
            case 19:reason="Motorphasen- oder Motorsensorfehler";break;
            case 20:reason="Motor blockiert. Gerät ausschalten und vor dem Prüfen des Messers den Netzstecker ziehen";break;
            case 21:reason="Motor zu heiß. Gerät abkühlen lassen";break;
            case 22:case 23:reason="Fehler am Temperatursensor des Motors";break;
            case 24:reason="Motorelektronik zu heiß. Gerät abkühlen lassen";break;
            case 25:case 26:reason="Fehler am Temperatursensor der Motorelektronik";break;
            case 27:reason="Kommunikation mit dem Motor gestört";break;
            case 28:reason="Fehler im Kühlsystem";break;
            case 29:reason="Kommunikation mit der Waage gestört";break;
            case 254:reason="Kommunikation mit der Gerätesteuerung gestört";break;
            default:return "Gerätefehler "+code+". Ursache vor weiterem Kochen prüfen.";
        }
        return reason+" (Gerätefehler "+code+").";
    }
    private DeviceFaults(){}
}
