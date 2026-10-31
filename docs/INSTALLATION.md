# Get started: device access and installation

[Deutsch](INSTALLATION.de.md) · [Project overview](../README.md)

This guide takes you from the original interface to a checked ADB connection,
then to building and installing our app on a prepared robot. Commands below are
for **Windows PowerShell**. Read the route table before changing your appliance.

## 1. Choose your starting point

| Your robot | What you can do now |
| --- | --- |
| Compatible C3 with working Magisk at `/sbin/magisk` | Follow the connection checks, build and install steps below |
| Stock C3, Magisk absent | Try the documented factory Network ADB entry and check the result. Root ADB alone does not provide the runtime our app requires; read section 5 before proceeding |
| Different model / firmware, inaccessible settings or unknown bootloader state | Collect the model and software versions first. Compatibility is not established |

**Scope:** CM0801K_EN / C3 reference, Android 10 / SDK 29. The factory-access
experiment used build `eng.songxi.20221008.190443`, board `exdroid`, hardware
`sun50iw10p1`. The reference had previously been unlocked and repaired. We have
not demonstrated a complete first-time installation on an untouched locked unit.

There is currently **no public ready-to-install APK or first-root firmware
package**. This is a source-build installation. The public snapshot and some
control changes still need final-APK and supervised physical validation.

Before starting: finish cooking, wait for confirmed idle, and preserve your
existing data. Do not reset the robot, change its screen lock, or open an OTA
package as part of this guide. A lock-screen experiment on the reference caused
a SystemUI crash loop. The steps here do not need it.
Before opening our app, fit the pot and close/lock the lid. Keep that state for
the first connection checks; automatic active pot diagnostics are explained in section 8.

## 2. Prepare your PC and network

You need a Windows PC, a phone capable of creating a hotspot, your home Wi-Fi,
and [Google's SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools).
Extract Platform Tools locally. You also need Git and the build tools in section 6.

Keep the PC and robot on a trusted home network. Network ADB opens a maintenance
port; do not expose it through a router port-forward or a public network.

In PowerShell, set the actual path to your downloaded `adb.exe`:

```powershell
$Adb = (Resolve-Path 'C:\Android\platform-tools\adb.exe').Path
& $Adb version
if ($LASTEXITCODE -ne 0) { throw 'ADB is not ready.' }
```

**You should see:** an Android Debug Bridge version. Keep this PowerShell window
open; later commands reuse `$Adb` and `$Target`.

## 3. Open the original factory Network ADB entry

On the robot's original Xiaomi interface:

1. Open recipe search so that the on-screen keyboard appears.
2. Hold the comma key. Open **Android Keyboard Settings (AOSP)**.
3. Open **Text correction → My dictionary**. Use the top-left Up/back navigation
   to reach the main Android Settings screen.
4. Open **Network & Internet → Wi-Fi**. Connect the robot to your normal home
   Wi-Fi first and wait until it says **Connected**. This saves its return network.
5. On your phone create a temporary hotspot named exactly:

   ```text
   /*nam_looc_etvc*/
   ```

   The slashes, both asterisks and underscores are part of the name. Set your own
   password. A simple 2.4 GHz / WPA2 hotspot is a useful compatibility choice.
6. Connect the robot to that hotspot through Android Wi-Fi settings. Once it is
   actually connected, a numeric maintenance overlay should appear.
7. Enter **1123**. The fourth digit opens the ADB tool. Select **NET ADB**.
   Use only this documented control; other factory-test controls are outside this guide.
8. Turn the phone hotspot off. On the reference, the robot returned automatically
   to its saved home Wi-Fi while Network ADB remained enabled. The service screen
   can stay open. If a keypad reappears, dismiss it with its Exit control.
9. Find the robot's current home-network IP in your router or Android Wi-Fi details.
   The service screen can still display its previous hotspot IP.

**You should see:** the robot back on your home network. The PC stays on that
network throughout. If the overlay does not appear, stop at that checkpoint and
record the model / firmware; do not substitute guessed broadcasts or shell commands.

## 4. Connect and verify which device you reached

Use the robot's actual IP, not an address copied from someone else's report:

```powershell
$RobotIp = Read-Host 'Robot IP on your home network'
$Target = "${RobotIp}:5555"
& $Adb connect $Target
if ($LASTEXITCODE -ne 0) { throw 'Could not connect.' }
& $Adb -s $Target get-state
if ($LASTEXITCODE -ne 0) { throw 'Robot is not authorized/reachable.' }
& $Adb -s $Target shell getprop ro.product.model
& $Adb -s $Target shell getprop ro.product.device
& $Adb -s $Target shell getprop ro.build.version.sdk
& $Adb -s $Target shell getprop ro.build.fingerprint
& $Adb -s $Target shell id
```

If Android asks you to authorize this PC's debugging key, confirm your own PC on
the robot. The reference PC was already authorized; first-PC authorization on a
fresh unit remains a separate check. `unauthorized` is not a successful connection.

**You should see:** `device`, the identity of your own robot and SDK `29` on the
reference firmware. `uid=0(root)` means the **ADB shell** has root. A UID such as
`2000(shell)` does not. A successful `adb connect` alone proves neither result.
Every device command uses `-s $Target` to avoid selecting another phone or emulator.

## 5. Check the root runtime the app needs

These are different things:

- **Root ADB:** the PC's maintenance shell runs as root.
- **Magisk runtime:** the installed Android app can request its own root process.

The current client launches `/sbin/magisk su`. Check that exact runtime:

```powershell
& $Adb -s $Target shell ls -l /sbin/magisk
if ($LASTEXITCODE -ne 0) { throw 'Required Magisk runtime is absent. Read section 5; do not continue with device control.' }
& $Adb -s $Target shell /sbin/magisk su -c id
if ($LASTEXITCODE -ne 0) { throw 'Magisk root check failed.' }
```

**You should see:** the executable and an identity containing `uid=0(root)`.
A root grant to the ADB shell does not automatically grant root to our app.
The reference used Magisk 30.7; a different version or executable location needs
its own compatibility check. Do not create a replacement symlink just to pass this step.

### If Magisk is absent

**This is the current gap for a stock-device beginner.** Installing the Magisk
manager APK alone does not install its boot/runtime components.

The project's historical signed first-root OTA was tested on the previously
unlocked reference. Later Wi-Fi/USB failures remain unresolved; internal experiment
permission was not a general consumer release. We do not distribute those packages
as a first-time installer.

The [official Magisk installation guide](https://topjohnwu.github.io/Magisk/install.html)
describes an unlocked bootloader and an appropriate image patched on the same
device. It is general upstream guidance, not a verified C3 unlock/flash procedure.
This project has no established unlock command for an untouched locked C3. Do not
apply phone fastboot recipes, another robot's boot image, or an old RC2/wifi-r4 ZIP.

At this checkpoint you can explore/build the source, but full appliance control
must wait for a verified device-specific runtime installation. For help, open an
issue with sanitized model, firmware, bootloader history and the failed checkpoint.

## 6. Build the software

On Windows install **JDK 17**, **Android SDK Platform 34**, **Build Tools 34.0.0**
and **NDK 27.1.12297006** using Android Studio's SDK Manager. The native helper
currently expects the Windows NDK host. Download tools from
[Android Studio](https://developer.android.com/studio), not a third-party bundle.

```powershell
git clone https://github.com/nited-ai/Xiaomi-Smart-Cooking-Robot.git
if ($LASTEXITCODE -ne 0) { throw 'Clone failed.' }
Set-Location Xiaomi-Smart-Cooking-Robot
$env:JAVA_HOME = 'C:\Path\To\Your\jdk-17'
$env:ANDROID_HOME = 'C:\Path\To\Your\Android\Sdk'
& ./gradlew.bat :machine:testApplianceUnitTest :machine:assembleAppliance :phone:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed; do not install partial output.' }
$Apk = (Resolve-Path './machine/build/outputs/apk/appliance/machine-appliance.apk').Path
Get-FileHash -LiteralPath $Apk -Algorithm SHA256
```

Replace the two tool paths with your real installations. Run these commands from
the cloned repository, not from a private development/archive folder.

**You should see:** `BUILD SUCCESSFUL`, the appliance APK and its SHA-256.
The hash identifies your own build; it is not a hardware-validation certificate.
Keep your local signing key private and backed up. A fresh debug key will not
update an existing installation signed by someone else's key.
Gradle's default local debug key is normally `%USERPROFILE%\.android\debug.keystore`.

## 7. Install and open the appliance app

Continue only after confirming the robot identity and the required runtime:

```powershell
& $Adb -s $Target install --no-streaming -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed. Keep the existing installation/data.' }
& $Adb -s $Target shell dumpsys package com.kitchenos.connect |
    Select-String -Pattern 'versionCode=|versionName='
& $Adb -s $Target shell am start -n com.kitchenos.connect/.ui.ApplianceActivity
if ($LASTEXITCODE -ne 0) { throw 'App did not open.' }
```

**You should see:** `Success`, package/version details, then our interface on the
robot. The internal package name remains `com.kitchenos.connect`.
On first launch, the setup page can appear before the main interface. If the
robot is already on your home Wi-Fi, choose **Ohne Konto lokal starten**
(start locally without an account). You do not need a cloud account or phone
pairing for this initial local path. Avoid starting another setup hotspot here.
On the first root request, grant access to that installed app in Magisk's
Superuser view. The UI should then be able to establish its local control connection.
The public build uses schematic artwork where private development images were omitted.

`-r` requests an update with existing data retained. If Android reports
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a downgrade error, stop. Do not uninstall,
clear app data, or add a downgrade flag to force the command through.

## 8. First checks at the counter

**The app includes automatic active pot diagnostics.** With an open lid, a
sufficient settled weight change can request `CHECK_POT`, which sends a short
MCU diagnostic START/STOP sequence with zero phase settings. This is not purely
passive telemetry, and zero settings do not establish electrical inactivity.
For initial checks keep the pot installed and the lid closed/locked. Do not
remove/reinsert the pot, vary its load or open the lid as an installation test.

Before trying a program, check in that unchanged idle state:

1. Is the local connection established, and is the displayed state current?
2. Do the pot and lid indications match the stationary appliance? A retained image
   or “checking” state is not proof of fresh telemetry. The cup uses your explicit confirmation.
3. Are faults or access errors visible? Resolve them before any cook start.
4. Read the display and settings information first. Defer knob presses, start
   buttons and function/program selection to a separate functional test: some
   selections intentionally request a start directly after the device checks.

A supervised diagnostic or cooking test is a separate step. This guide does not
instruct you to start a cooking program. The public snapshot's
[remaining validation work](STATUS.md) still applies.

If the app's Wi-Fi list is empty, Android 10 requires location permission and
enabled location services for scanning. Grant the app's location permission and
enable Location through normal Android Settings, then retry. Do not change the
screen lock or flash a Wi-Fi driver as a setup shortcut.

### Optional Android sharing app

Install `phone/build/outputs/apk/debug/phone-debug.apk` on your **phone**, using
normal Android sideloading. Put phone and robot on the same home Wi-Fi. On the
robot open **Settings → Verbundene Handys** (connected phones), enable
**Handy-Empfang einschalten** if offered, and display the pairing QR code.

Use the **phone's camera** to scan it; the standalone phone APK has no embedded
scanner. The current QR opens an HTTPS handoff at `app.kalomatik.ai/machine/`,
so this camera route depends on that web page and its handoff to the phone app.
It is separate from local appliance control. If it does not hand off, stop the
optional pairing step rather than assuming the phone is connected.

For a manual developer fallback, use a QR reader that can show the full decoded
QR text. Extract and URL-decode only the fragment after `#`: it must begin with
`kitchenos://pair` or `kitchenos://setup`. Paste that raw URI under **Maschine
koppeln** in the phone app. The entire HTTPS envelope is not accepted there.
Keep the code private: it contains the local pairing invitation.

After successful pairing, share a supported HTTPS recipe link or recipe text
from Android's Share menu. The phone app appears as **An KitchenOS senden**.
See [network and service configuration](BUILDING.md#network-and-cloud-configuration)
for the separate online catalogue, account and AI dependencies.

## 9. Finish maintenance and keep a way back

Disconnect the PC when finished:

```powershell
& $Adb disconnect $Target
```

This closes the PC connection; **it does not disable the robot's ADB listener**.
Use Android's USB-debugging control to disable debugging when reachable, then
verify that a new PC connection is refused. The factory tool also has a NORMAL
button, but its shutdown behavior has not been validated by this guide; do not
assume a screen change alone closes port 5555. After a reboot, the factory entry
may need to be repeated for another maintenance session.

The APK step does not replace the original app or flash partitions. To return
to the original interface, stop any run, confirm idle, leave our app and open
the original application normally. Do not run competing controller clients.
Keep existing app data and the signing key; do not use a factory reset as rollback.
If Android itself stops booting, this app-install guide is not a firmware recovery
procedure. Our root experiments had a separate prepared recovery path.

## If a checkpoint fails

| Symptom | First thing to check |
| --- | --- |
| No factory keypad | Exact hotspot name, actual Connected state, firmware match |
| Cannot connect / stale IP | Robot's current home-network IP, same network, no guest-client isolation |
| `unauthorized` | Confirm your own PC's authorization request on the robot |
| ADB is root, app says access missing | Magisk runtime/path and the **app's** Superuser grant |
| Signature / downgrade error | Original signing key and compatible version; preserve existing data |
| Device state missing or stale | Stop at state verification; a successful APK install is not controller compatibility |

## Evidence and sources

The factory sequence is based on our 19 October reference-device observations
and matching original SystemControl/MidFactoryTest behavior. A later original-boot
test repeated root Network ADB with Magisk absent, on the same previously unlocked
reference. No new device installation was performed while writing this guide.

- [Android Debug Bridge documentation](https://developer.android.com/tools/adb)
- [Official Platform Tools](https://developer.android.com/tools/releases/platform-tools)
- [Official Magisk installation guide](https://topjohnwu.github.io/Magisk/install.html)
- [Project access boundaries](ROOT-ACCESS.md), [build guide](BUILDING.md), [validation status](STATUS.md)
