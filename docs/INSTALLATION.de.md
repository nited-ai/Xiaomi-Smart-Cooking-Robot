# Loslegen: Root-Zugang prüfen und unsere Software installieren

[English](INSTALLATION.md) · [Zur Projektübersicht](../README.md)

Hier geht es Schritt für Schritt von der Originaloberfläche zum Gerätezugang und
anschließend zur Installation unserer App. Die PC-Befehle sind für **Windows
PowerShell** geschrieben. Lies zuerst, welcher Weg zu deinem Roboter passt.

## 1. Wo stehst du gerade?

| Dein Ausgangspunkt | Dein nächster Schritt |
| --- | --- |
| Passender C3 mit funktionierendem Magisk unter `/sbin/magisk` | Verbindung prüfen, App bauen und installieren |
| Originalgerät ohne Magisk | Den Wartungszugang öffnen und das Ergebnis prüfen. Root über ADB allein reicht unserer App noch nicht; Abschnitt 5 erklärt die offene Stelle |
| Anderes Modell, andere Firmware oder unbekannter Gerätezustand | Erst Modell und Softwarestand erfassen; die Kompatibilität ist nicht belegt |

**Unsere Referenz:** CM0801K_EN / C3, Android 10 / SDK 29. Der Zugangsversuch
verwendete Build `eng.songxi.20221008.190443`, Board `exdroid` und Hardware
`sun50iw10p1`. Dieses Gerät war zuvor bereits entsperrt und repariert worden.
Eine vollständige Erstinstallation auf einem unangetasteten, gesperrten Gerät
haben wir noch nicht nachgewiesen.

Aktuell gibt es **keine öffentliche fertige APK und kein freigegebenes
Erst-Root-Paket**. Du baust die App aus dem Quellcode. Für diesen öffentlichen
Stand stehen die Prüfung der endgültigen APK und reale Geräteversuche noch aus.

Beende zuerst jeden Kochvorgang und warte auf bestätigten Stillstand. Sichere
vorhandene Daten. Diese Anleitung benötigt keinen Werksreset, keine Änderung der
Bildschirmsperre und kein OTA-Update. Ein Versuch mit der Bildschirmsperre hatte
auf der Referenz eine SystemUI-Absturzschleife ausgelöst.
Setze vor dem Öffnen unserer App den Topf ein und schließe/verriegele den Deckel.
Lass das für die ersten Verbindungsprüfungen so; Abschnitt 8 erklärt die
automatischen aktiven Topfprüfungen der App.

## 2. PC und Netzwerk vorbereiten

Du brauchst einen Windows-PC, ein Handy mit Hotspot-Funktion, dein Heim-WLAN,
Git und die [offiziellen Android Platform Tools](https://developer.android.com/tools/releases/platform-tools).
Entpacke die Platform Tools auf deinem PC. Für den App-Bau kommen die Werkzeuge
aus Abschnitt 6 dazu.

Verwende ein vertrauenswürdiges Heimnetz. Netzwerk-ADB öffnet einen Wartungsport;
dafür keine Router-Portfreigabe einrichten und kein öffentliches WLAN verwenden.

Öffne PowerShell. Trage den tatsächlichen Pfad deiner `adb.exe` ein:

```powershell
$Adb = (Resolve-Path 'C:\Android\platform-tools\adb.exe').Path
& $Adb version
if ($LASTEXITCODE -ne 0) { throw 'ADB ist noch nicht einsatzbereit.' }
```

**Das solltest du sehen:** eine Versionsanzeige von Android Debug Bridge.
Lass dieses PowerShell-Fenster geöffnet; spätere Befehle verwenden seine Variablen.

## 3. Den originalen WLAN-Wartungszugang öffnen

Auf der ursprünglichen Xiaomi-Oberfläche:

1. Öffne die Rezeptsuche, damit die Bildschirmtastatur erscheint.
2. Halte die Komma-Taste gedrückt und öffne die **Android-Tastatureinstellungen
   (AOSP)**.
3. Öffne **Textkorrektur → Mein Wörterbuch**. Gehe über den Pfeil oben links zur
   übergeordneten Android-Einstellungsübersicht.
4. Öffne **Netzwerk & Internet → WLAN**. Verbinde den Roboter zuerst mit deinem
   normalen Heim-WLAN und warte auf **Verbunden**. Damit ist das Rückkehrnetz gespeichert.
5. Erstelle auf deinem Handy einen vorübergehenden Hotspot mit exakt diesem Namen:

   ```text
   /*nam_looc_etvc*/
   ```

   Beide Schrägstriche, beide Sternchen und die Unterstriche gehören dazu.
   Vergib ein eigenes Passwort. Ein einfacher 2,4-GHz-/WPA2-Hotspot ist ein
   sinnvoller Ausgangspunkt für die Verbindung.
6. Verbinde den Roboter über die Android-WLAN-Einstellungen mit diesem Hotspot.
   Sobald er wirklich verbunden ist, sollte ein numerisches Wartungsfenster erscheinen.
7. Gib **1123** ein. Nach der vierten Ziffer öffnet sich die ADB-Seite.
   Tippe auf **NET ADB**. Andere Wartungs- und Alterungstest-Funktionen gehören
   nicht zu diesem Weg.
8. Schalte den Handy-Hotspot aus. Die Referenz kehrte automatisch ins gespeicherte
   Heim-WLAN zurück; Netzwerk-ADB blieb aktiv. Das Servicemenü darf offen bleiben.
   Ein erneut erscheinendes Zahlenfenster mit dessen Exit-Taste schließen.
9. Lies die aktuelle Heimnetz-IP im Router oder in den Android-WLAN-Details ab.
   Die IP im Servicemenü kann noch die alte Hotspot-Adresse sein.

**Das solltest du sehen:** Der Roboter ist wieder im Heim-WLAN. Der PC bleibt
ebenfalls dort. Erscheint das Zahlenfenster nicht, halte an dieser Stelle an und
notiere Modell sowie Firmware. Ersetze den Menüweg nicht durch geratene Broadcasts
oder Systembefehle.

## 4. Verbindung und Geräteidentität prüfen

Verwende die echte IP deines Roboters:

```powershell
$RobotIp = Read-Host 'Aktuelle IP des Roboters im Heimnetz'
$Target = "${RobotIp}:5555"
& $Adb connect $Target
if ($LASTEXITCODE -ne 0) { throw 'Verbindung fehlgeschlagen.' }
& $Adb -s $Target get-state
if ($LASTEXITCODE -ne 0) { throw 'Roboter nicht erreichbar oder nicht autorisiert.' }
& $Adb -s $Target shell getprop ro.product.model
& $Adb -s $Target shell getprop ro.product.device
& $Adb -s $Target shell getprop ro.build.version.sdk
& $Adb -s $Target shell getprop ro.build.fingerprint
& $Adb -s $Target shell id
```

Falls Android eine Debugging-Freigabe für deinen PC anzeigt, bestätige deinen
eigenen PC am Roboter. Unser Referenz-PC war schon autorisiert; die erste
PC-Freigabe auf einem frischen Gerät ist ein eigener Prüfpunkt.

**Das solltest du sehen:** `device`, die Angaben deines Roboters und auf dem
Referenzstand SDK `29`. `unauthorized` ist noch keine erfolgreiche Verbindung.
`uid=0(root)` bedeutet Root-Rechte für die **ADB-Shell**. Beispielsweise
`uid=2000(shell)` bedeutet das nicht. Ein erfolgreiches `adb connect` allein
beweist noch keine Root-Rechte.

Alle Gerätebefehle enthalten `-s $Target`, damit kein daneben angeschlossenes
Handy oder ein Emulator ausgewählt wird.

## 5. Prüfen, ob unsere App Root verwenden kann

Es gibt zwei verschiedene Zugänge:

- **Root-ADB:** Der PC hat eine privilegierte Wartungsshell.
- **Magisk-Laufzeit:** Die installierte Android-App kann selbst einen Root-Prozess anfordern.

Unser aktueller Client startet `/sbin/magisk su`. Prüfe genau diesen Zugang:

```powershell
& $Adb -s $Target shell ls -l /sbin/magisk
if ($LASTEXITCODE -ne 0) { throw 'Die erforderliche Magisk-Laufzeit fehlt. Abschnitt 5 lesen; noch keine Geraetesteuerung.' }
& $Adb -s $Target shell /sbin/magisk su -c id
if ($LASTEXITCODE -ne 0) { throw 'Magisk-Root-Pruefung fehlgeschlagen.' }
```

**Das solltest du sehen:** die vorhandene Programmdatei und eine Identität mit
`uid=0(root)`. Eine Freigabe für die ADB-Shell ist noch keine Freigabe für unsere App.
Die Referenz verwendete Magisk 30.7. Andere Versionen und andere Programmpfade
benötigen eine eigene Kompatibilitätsprüfung; keinen Ersatz-Symlink anlegen,
um diese Prüfung zu umgehen.

### Wenn Magisk fehlt

**Hier liegt aktuell die offene Stelle für Einsteiger mit einem Originalgerät.**
Die Magisk-Manager-APK zu installieren richtet die Boot-/Root-Laufzeit noch nicht ein.

Unser historisches signiertes Erst-Root-OTA wurde auf der zuvor entsperrten
Referenz geprüft. Spätere WLAN-/USB-Ausfälle sind nicht abschließend geklärt.
Die Freigabe interner Experimente war keine allgemeine Nutzerfreigabe; diese
Pakete werden hier nicht als Erstinstallationslösung verteilt.

Die [offizielle Magisk-Anleitung](https://topjohnwu.github.io/Magisk/install.html)
beschreibt einen entsperrten Bootloader und ein passendes, auf demselben Gerät
gepatchtes Image. Daraus folgt kein nachgewiesener Entsperr-/Flashweg für den C3.
Für einen unangetasteten, gesperrten C3 haben wir noch keinen bestätigten
Entsperrbefehl. Verwende deshalb keine Smartphone-Fastboot-Anleitung, kein
Bootimage eines anderen Roboters und keine alten RC2-/wifi-r4-Pakete.

Du kannst hier bereits den Quellcode ansehen und bauen. Für die echte
Gerätesteuerung fehlt dann noch die verifizierte, zu deinem Gerät passende
Einrichtung der Laufzeit. Ein öffentliches Issue sollte Modell, Firmware,
bisherige Änderungen und den fehlgeschlagenen Prüfpunkt enthalten; persönliche
Seriennummern, Schlüssel und Rohbackups bleiben privat.

## 6. Unsere App bauen

Installiere unter Windows **JDK 17**, **Android SDK Platform 34**,
**Build Tools 34.0.0** und **NDK 27.1.12297006**. Android-Komponenten lassen sich
im SDK Manager von [Android Studio](https://developer.android.com/studio) auswählen.
Der native Hilfsprogramm-Build erwartet derzeit den Windows-NDK-Pfad.

```powershell
git clone https://github.com/nited-ai/Xiaomi-Smart-Cooking-Robot.git
if ($LASTEXITCODE -ne 0) { throw 'Klonen fehlgeschlagen.' }
Set-Location Xiaomi-Smart-Cooking-Robot
$env:JAVA_HOME = 'C:\Path\To\Your\jdk-17'
$env:ANDROID_HOME = 'C:\Path\To\Your\Android\Sdk'
& ./gradlew.bat :machine:testApplianceUnitTest :machine:assembleAppliance :phone:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build fehlgeschlagen; keine unvollstaendige Ausgabe installieren.' }
$Apk = (Resolve-Path './machine/build/outputs/apk/appliance/machine-appliance.apk').Path
Get-FileHash -LiteralPath $Apk -Algorithm SHA256
```

Ersetze die beiden Werkzeugpfade durch deine Installationen. Die Befehle gehören
in den gerade geklonten öffentlichen Projektordner.

**Das solltest du sehen:** `BUILD SUCCESSFUL`, die APK für den Roboter und ihren
SHA-256-Wert. Dieser Wert identifiziert deinen Build; er beweist keine Geräteabnahme.
Bewahre deinen Signaturschlüssel privat auf. Mit einem neu erzeugten Debug-Schlüssel
kannst du eine anders signierte vorhandene App nicht aktualisieren.
Der normale lokale Gradle-Debug-Schlüssel liegt unter
`%USERPROFILE%\.android\debug.keystore`; sichere ihn privat.

## 7. Installieren und öffnen

Fahre fort, wenn Geräteidentität und erforderliche Root-Laufzeit geprüft sind:

```powershell
& $Adb -s $Target install --no-streaming -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Installation fehlgeschlagen. Bestehende App und Daten erhalten.' }
& $Adb -s $Target shell dumpsys package com.kitchenos.connect |
    Select-String -Pattern 'versionCode=|versionName='
& $Adb -s $Target shell am start -n com.kitchenos.connect/.ui.ApplianceActivity
if ($LASTEXITCODE -ne 0) { throw 'App wurde nicht geoeffnet.' }
```

**Das solltest du sehen:** `Success`, Paket-/Versionsangaben und anschließend
unsere Oberfläche am Roboter. Der interne Paketname lautet weiterhin
`com.kitchenos.connect`. Beim ersten Start kann zunächst die Einrichtung erscheinen.
Ist der Roboter bereits im Heim-WLAN, wähle **Ohne Konto lokal starten**.
Für diesen ersten lokalen Weg brauchst du weder Cloud-Konto noch Handy-Kopplung.
Öffne dafür kein weiteres Einrichtungs-WLAN. Erlaube dieser installierten App bei ihrer ersten
Root-Anfrage den Zugriff in Magisks Superuser-Ansicht. Danach sollte sie ihre
lokale Steuerverbindung aufbauen können. Einige Bilder der Entwicklungsoberfläche
sind im öffentlichen Build durch schematische Darstellungen ersetzt.

`-r` fordert eine Aktualisierung mit Erhalt vorhandener Appdaten an. Bei
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` oder einem Downgrade-Fehler anhalten:
nicht vorher deinstallieren, keine Appdaten löschen und kein Downgrade erzwingen.

## 8. Die ersten Prüfungen am Gerät

**Die App hat automatische aktive Topfprüfungen.** Bei offenem Deckel und einer
ausreichenden stabilen Gewichtsänderung kann `CHECK_POT` ausgelöst werden. Das
sendet eine kurze Diagnose-START-/STOP-Folge an den Controller, mit auf null
gesetzten Phasenwerten. Es ist keine rein passive Abfrage; Nullwerte beweisen
keine elektrische Inaktivität. Lass für die ersten Prüfungen den Topf eingesetzt
und den Deckel geschlossen/verriegelt. Topfentnahme, Wiedereinsetzen,
Belastungsänderungen oder Deckelöffnen gehören nicht zum Installationstest.

Prüfe in diesem unveränderten Leerlaufzustand:

1. Ist die lokale Verbindung hergestellt und die Rückmeldung aktuell?
2. Passen Topf- und Deckelanzeige zum unveränderten Zustand? Ein erhaltenes Bild
   oder „Wird geprüft …“ beweist noch keine neue Rückmeldung. Den Messbecher
   bestätigst du selbst.
3. Sind Fehler oder Zugangsprobleme sichtbar? Diese zuerst klären.
4. Lies zunächst nur die Anzeigen und Einstellungsinformationen. Drehknopfdruck,
   Starttasten und Funktions-/Programmauswahl gehören erst in den gesonderten
   Funktionstest: Einige Auswahlen starten absichtlich direkt nach der Prüfung.

Ein beaufsichtigter Diagnose- oder Kochversuch ist ein eigener Schritt. Diese
Anleitung fordert keinen Kochstart an. Die [noch offenen Prüfungen](STATUS.md)
des öffentlichen Quellstands bleiben bestehen.

Ist die WLAN-Liste der App leer, benötigt Android 10 für Scans die Standortberechtigung
der App und eingeschaltete Standortdienste. Erlaube beides über die normalen
Android-Einstellungen und versuche es erneut. Dafür weder die Bildschirmsperre
ändern noch einen WLAN-Treiber flashen.

### Optional: Rezepte vom Android-Handy teilen

Installiere `phone/build/outputs/apk/debug/phone-debug.apk` über die normale
Android-APK-Installation auf deinem **Handy**. Verbinde Handy und Roboter mit
demselben Heim-WLAN. Öffne am Roboter **Einstellungen → Verbundene Handys**,
gegebenenfalls **Handy-Empfang einschalten**, und zeige den Kopplungs-QR-Code an.

Scanne ihn mit der **Handykamera**; die eigenständige Handy-APK hat keinen
eingebauten Scanner. Der aktuelle QR-Code öffnet eine HTTPS-Weiterleitung unter
`app.kalomatik.ai/machine/`. Dieser Kameraweg benötigt die Webseite und deren
Übergabe an die Handy-App; er gehört nicht zum lokalen Steuerpfad des Roboters.
Erfolgt keine Übergabe, halte die optionale Kopplung hier an. Ein gescannter Code
allein bedeutet noch keine erfolgreiche Verbindung.

Als manueller Entwicklerweg: Ein QR-Leser muss den vollständigen decodierten
QR-Text anzeigen können. Extrahiere nur das Fragment nach `#` und decodiere dessen
URL-Zeichen. Es muss mit `kitchenos://pair` oder `kitchenos://setup` beginnen.
Füge diese rohe URI unter **Maschine koppeln** in der Handy-App ein. Die gesamte
HTTPS-Adresse wird dort nicht akzeptiert. Der Code enthält die lokale
Kopplungseinladung und gehört nicht in öffentliche Screenshots oder Issues.

Teile nach erfolgreicher Kopplung einen unterstützten HTTPS-Rezeptlink oder
Rezepttext über Androids Teilen-Menü; die Handy-App heißt dort **An KitchenOS
senden**. Online-Katalog, Anmeldung und KI benötigen ihre gesonderte
[Dienstkonfiguration](BUILDING.md#network-and-cloud-configuration).

## 9. Wartung beenden und zur Originaloberfläche zurückkehren

Trenne die PC-Verbindung, wenn du fertig bist:

```powershell
& $Adb disconnect $Target
```

Das trennt nur den PC; **der ADB-Port am Roboter wird dadurch nicht abgeschaltet**.
Deaktiviere Debugging über die normale Android-Einstellung für USB-Debugging,
wenn erreichbar, und prüfe anschließend, dass keine neue PC-Verbindung zustande
kommt. Die NORMAL-Taste im Werkstattmenü ist hier nicht als Abschaltweg geprüft;
ein anderer Bildschirm beweist keinen geschlossenen Port 5555. Nach einem Neustart
kann für weitere Wartung ein erneuter Menüeinstieg nötig sein.

Die APK-Installation ersetzt die Original-App nicht und schreibt keine Partitionen.
Für die Rückkehr: laufendes Programm stoppen, Stillstand bestätigen lassen, unsere
App verlassen und die Originalanwendung normal öffnen. Keine zwei Steuerprogramme
gleichzeitig verwenden. Daten und Signaturschlüssel behalten; ein Werksreset ist
kein normaler Rückweg für ein App-Update. Startet Android selbst nicht mehr,
reicht diese App-Anleitung nicht als Firmware-Wiederherstellung. Unsere
Root-Versuche hatten einen separat vorbereiteten Wiederherstellungsweg.

## Wenn etwas nicht klappt

| Beobachtung | Zuerst prüfen |
| --- | --- |
| Kein Zahlenfenster | Hotspotname Zeichen für Zeichen, wirklich verbunden, Firmwarestand |
| Keine Verbindung / alte IP | Aktuelle Heimnetz-IP, gleiches Netz, keine Gäste-Isolation |
| `unauthorized` | Eigene PC-Freigabe am Roboter bestätigen |
| ADB ist Root, App meldet fehlenden Zugang | Magisk-Pfad und Superuser-Freigabe der **App** |
| Signatur-/Versionsfehler beim Installieren | Passender ursprünglicher Schlüssel und kompatible Version; Daten erhalten |
| Gerätezustand fehlt oder ist veraltet | Am Prüfpunkt bleiben; eine installierte APK beweist keine Steuerungskompatibilität |

## Grundlage dieser Anleitung

Der Menüweg wurde am 19. Oktober auf der Referenz beobachtet und mit dem
originalen SystemControl-/MidFactoryTest-Verhalten abgeglichen. Ein späterer
Versuch mit Originalboot und ohne Magisk bestätigte Root-Netzwerk-ADB erneut,
weiterhin auf derselben zuvor entsperrten Referenz. Beim Schreiben dieser
Anleitung wurde kein Gerät neu gerootet, installiert oder gesteuert.

- [Offizielle ADB-Dokumentation](https://developer.android.com/tools/adb)
- [Offizielle Platform Tools](https://developer.android.com/tools/releases/platform-tools)
- [Offizielle Magisk-Anleitung](https://topjohnwu.github.io/Magisk/install.html)
- [Zugangsgrenzen](ROOT-ACCESS.md), [Build-Anleitung](BUILDING.md), [Prüfstand](STATUS.md)
