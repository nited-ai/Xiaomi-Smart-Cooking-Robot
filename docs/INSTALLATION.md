# Install your own cooking interface

[Overview](../README.md) · [Root-path audit](STOCK-INSTALL-AUDIT.md) · [Validation status](STATUS.md)

Your robot should be open to your recipes and your ideas. The new **factory
session** lets our app use a narrowly scoped local root broker started through
the original factory Network ADB service. It needs no Magisk or firmware flash.

**Experimental development route:** factory root ADB was observed on our previously
unlocked reference. A never-modified locked robot and authorization of a fresh PC
still need a supervised test. Emulator checks do not prove that factory entry.
Stop at any failed checkpoint; do not treat this as a supported consumer installer.

## 1. Check your starting point

| Robot state | Route |
| --- | --- |
| Exact C3 profile below, factory ADB is UID 0, SELinux already Permissive | Factory session below |
| Prepared robot with working `/sbin/magisk` | Existing Magisk route in section 8 |
| Other firmware, unauthorized PC, non-root ADB or Enforcing SELinux | Stop and report the checkpoint; no unlock/flash/policy bypass is provided |

The physical-device guard requires this exact profile:

```text
device: CM0801K_EN
board: exdroid
hardware: sun50iw10p1
Android: 10 / SDK 29
ABI: armeabi-v7a
incremental: eng.songxi.20221008.190443
fingerprint: Allwinner/CM0801K_EN/CM0801K_EN:10/QP1A.191105.004/eng.songxi.20221008.190443:user/release-keys
```

A match is eligibility, not proof of locked-unit support. There is no released
first-root firmware ZIP. Do not use old RC2/wifi-r4 packages or phone fastboot recipes.

## 2. Prepare Windows

Install Git, Python 3.10+, JDK 17 or 21, and the official Android SDK: Platform
Tools, Platform 34, Build Tools 34.0.0 and NDK 27.1.12297006. Android Studio's SDK
Manager can install these. The native build currently uses the Windows NDK host.
Use a trusted home network shared by PC and robot; keep ADB port 5555 off the internet.
You also need a phone capable of creating the temporary hotspot below.

In PowerShell, replace both tool paths:

```powershell
git clone https://github.com/nited-ai/Xiaomi-Smart-Cooking-Robot.git
if ($LASTEXITCODE -ne 0) { throw 'Clone failed.' }
Set-Location Xiaomi-Smart-Cooking-Robot
$env:JAVA_HOME = 'C:\Path\To\Your\jdk-21'
$env:ANDROID_HOME = 'C:\Path\To\Your\Android\Sdk'
$Adb = (Resolve-Path "$env:ANDROID_HOME/platform-tools/adb.exe").Path
$Java = (Resolve-Path "$env:JAVA_HOME/bin/java.exe").Path
& $Adb version
python --version
```

## 3. Open the original factory entry

Finish cooking and confirm physical idle. Preserve existing data. This route does
not require a reset, screen-lock change or OTA package.

1. In the original interface, open recipe search to show the keyboard. Hold comma
   and open **Android Keyboard Settings (AOSP)**.
2. Open **Text correction → My dictionary**. Use top-left Up navigation to reach
   Android Settings, then **Network & Internet → Wi-Fi**.
3. Connect to your normal home Wi-Fi first and wait for **Connected**. This saves
   the network the robot should return to.
4. Create a phone hotspot with this exact **17-character ASCII name**:

   ```text
   /*nam_looc_etvc*/
   ```

   Both slashes, both asterisks and both underscores belong to the name. Set your
   own password; 2.4 GHz WPA2 is a useful compatibility choice.
5. Connect the robot to it. Once connected, a factory keypad should appear.
   Enter **1123**, then select **NET ADB**. Leave other factory controls alone.
6. Turn off the phone hotspot. The reference returned to its saved home Wi-Fi
   while Network ADB remained enabled. Dismiss a returning keypad with Exit.
   Find the robot's current IP in the router or Android Wi-Fi details; the
   maintenance screen can still show its old hotspot IP.

If any menu/overlay is missing, stop and record the model and firmware. Do not
substitute guessed factory broadcasts.

## 4. Connect and inspect

```powershell
$RobotIp = Read-Host 'Robot IP on your home network'
$Target = "${RobotIp}:5555"
& $Adb connect $Target
if ($LASTEXITCODE -ne 0) { throw 'Could not connect.' }
& $Adb -s $Target get-state
if ($LASTEXITCODE -ne 0) { throw 'Device not authorized or reachable.' }
python tools/factory_access.py inspect --adb $Adb --serial $Target
if ($LASTEXITCODE -ne 0) { throw 'Eligibility check failed.' }
```

Authorize only your own PC if Android displays an RSA prompt. `unauthorized` is
a failed checkpoint. The reference PC was already authorized; accepting a fresh
PC on an untouched unit remains unproven. Keep inspection output private if it
identifies your network.

Expected: JSON confirming the supported profile, root ADB and ABI. The tool checks
UID 0 and already-Permissive SELinux. It does not run `adb root` or change policy.
Every operation uses an explicit target to avoid selecting another device.

## 5. Build and prepare — no installation yet

```powershell
& ./gradlew.bat --no-daemon :machine:testApplianceUnitTest :machine:assembleAppliance
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$Apk = (Resolve-Path 'machine/build/outputs/apk/appliance/machine-appliance.apk').Path
Get-FileHash -LiteralPath $Apk -Algorithm SHA256
python tools/factory_access.py prepare --adb $Adb --serial $Target --sdk $env:ANDROID_HOME --java $Java --apk $Apk
if ($LASTEXITCODE -ne 0) { throw 'Preparation failed.' }
```

Expected: `BUILD SUCCESSFUL`, then a dry-run report with APK hash, version,
certificate and native hashes. Preparation reads device/package state without
installing, starting the broker or opening UART. Back up your signing key privately;
Gradle normally uses `%USERPROFILE%\.android\debug.keystore`.

Signature conflicts and downgrades are refused. Never uninstall or clear data to
force an update. An older development APK without the guarded bootstrap receiver
also needs a **separate supervised migration**: confirm physical idle, leave and
normally disconnect its controller, preserve its original key/data, then perform
a normal same-signature APK update. The installer deliberately does not automate
that process restart. New stock units have no existing project APK to migrate.

## 6. Install and authorize

Keep the robot physically idle, the stock cooking app closed and our control
screen closed. Use a new state filename for every apply attempt:

```powershell
$State = Join-Path $HOME 'xiaomi-robot-session-01.json'
python tools/factory_access.py apply --adb $Adb --serial $Target --sdk $env:ANDROID_HOME --java $Java --apk $Apk --state $State --lease-seconds 3600 --apply --acknowledge-experimental --confirm-idle
if ($LASTEXITCODE -ne 0) { throw 'Installation not confirmed. Retain the session record.' }
python tools/factory_access.py verify --adb $Adb --serial $Target --sdk $env:ANDROID_HOME --java $Java --state $State
if ($LASTEXITCODE -ne 0) { throw 'App authorization not confirmed.' }
```

Expected: `appAuthorizationVerified: true`, `opensUart: false`, and `verified: true`
on verify. This proves the installed app UID authenticated to the root broker;
it does not prove physical idle or UART/cooking compatibility.

The token-free PC record binds device, APK and session. Keep it for revocation.
The broker executes from the installed APK's protected native-library path;
its pinned bridge and config live in root-private `/data/local/xcr-access/<session>/`.
Android peer credentials and a random token bind access to the app. Only ID,
SERVE and PROBE are available; no general root shell or new TCP service is exposed.

The PC may disconnect after provisioning. Default lease: one hour; range:
60–14400 seconds. **The session does not survive reboot.** After expiry/reboot,
revoke the old configuration and create a new session. Plan a lease longer than
the supervised experiment. Expiry closes the bridge and requests graceful stop;
it cannot certify physical idle. Failed provisioning retains a record: try guarded
`stop` below; very early failures may need checkpoint-specific investigation.

## 7. First control connection is a separate hardware test

Installation/identity checks do not open UART. Opening appliance control does.
The inherited bridge takes ownership and force-stops the original cooking app
(`com.chunmi.multicooker3`). Confirm physical idle and close the stock app first;
never run competing controller clients.

For a supervised first connection, fit the pot and close/lock the lid. Keep that
state unchanged. Open the app in the Android launcher or:

```powershell
& $Adb -s $Target shell am start -n com.kitchenos.connect/.ui.ApplianceActivity
if ($LASTEXITCODE -ne 0) { throw 'App did not open.' }
```

On first setup choose **Ohne Konto lokal starten** (start locally without an
account) if offered. Local control needs no Xiaomi cloud account or phone pairing.
The public build uses schematic artwork where development images were omitted.

**Active automatic pot diagnostics:** with an open lid, a settled weight change
can request `CHECK_POT`, a short diagnostic START/STOP with zero phase settings.
This is not passive telemetry; zero settings do not prove electrical inactivity.
Do not open the lid, remove/reinsert the pot or vary its load as an install test.
Compare fresh pot/lid feedback with the stationary appliance. A retained image or
“checking” state is not fresh telemetry. Resolve faults first. Avoid starts,
knob actions and program selection: some selections intentionally request a start.
Physical STOP, temperature, motor and program acceptance remain separate gates.

## 8. Existing Magisk route

With no factory configuration present, the app retains `/sbin/magisk su`. Check
`/sbin/magisk su -c id` and grant this app access in Magisk's Superuser view.
An invalid/expired factory configuration fails closed; revoke it before using
Magisk again. Installing the manager APK alone does not install the root runtime.
The [upstream Magisk guide](https://topjohnwu.github.io/Magisk/install.html) assumes
an unlocked bootloader; it is not a verified C3 unlock procedure. Persistent root
for untouched locked units remains outside the validated route.

## 9. Revoke and finish maintenance

Stop any run normally, confirm physical idle and leave/disconnect our control
screen. A connected controller can make the guard refuse changes even without a
recipe running. Never kill it to bypass that guard.

```powershell
python tools/factory_access.py stop --adb $Adb --serial $Target --sdk $env:ANDROID_HOME --java $Java --state $State --apply --acknowledge-experimental --confirm-idle
if ($LASTEXITCODE -ne 0) { throw 'Revocation not confirmed. Retain the record.' }
& $Adb disconnect $Target
```

Stop clears matching app authorization through its idle guard, requests broker
shutdown and removes the root token config. Root-private executables remain;
app data, keys and original firmware are preserved. Shutdown acknowledgement is
not physical STOP confirmation. Reopen the original app only after normal release
and confirmed idle. Disconnecting ADB does **not** disable its listener: disable
debugging in normal Android settings when reachable and check a new connection
is refused. The factory NORMAL button's shutdown behavior is unverified.

## Troubleshooting

| Checkpoint | What to inspect |
| --- | --- |
| No keypad | Exact hotspot name, Connected state, firmware |
| Refused/stale connection | Current home IP, same network, guest isolation |
| Unauthorized | Own PC's RSA prompt; fresh-host factory access remains unproven |
| Profile/UID/SELinux mismatch | Preserve state and report sanitized facts; do not weaken guards |
| Signature/downgrade/missing receiver | Original key and supervised idle migration; preserve data |
| Active/uncertain control | Stop normally, confirm physical idle, disconnect control |
| Expired/absent broker | Revoke old session, prepare a new one |
| Access verifies, feedback missing | Stop at the separate hardware-compatibility gate |

Report model, firmware, modification history and failed checkpoint. Keep tokens,
raw backups and keys out of issues. See [the audit](STOCK-INSTALL-AUDIT.md) and
[build/configuration guide](BUILDING.md). [Android's ADB documentation](https://developer.android.com/tools/adb)
explains authorization and explicit device selection; our factory entry is
device-specific reference evidence, not a general Android procedure.
