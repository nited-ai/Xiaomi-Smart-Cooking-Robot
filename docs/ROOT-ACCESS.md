# Getting closer to your own hardware

**Looking for the actual steps?** Use the new
[device-access and installation guide](INSTALLATION.md) and the
[stock-install audit](STOCK-INSTALL-AUDIT.md). They cover the factory entry,
experimental no-flash session, source build, installation, verification and
revocation. Documentation is maintained in English.

The reason for privileged access is practical: an ordinary Android application
cannot open the appliance's MCU serial port. The control client needs that local
connection to communicate with the motor / heater controller and receive state.
The reference setup launched the project-owned bridge with Magisk `su`. The new
factory-session candidate lets an owner-started local broker launch that same
pinned bridge for the authenticated app UID. It uses existing root ADB, expires
after a bounded lease and does not survive reboot.

## What has been observed

On the CM0801K_EN / C3 reference appliance, development established an original
factory Network ADB entry returning UID 0. A later reset / original-boot experiment
re-established that path with Magisk absent. A separate signed first-root OTA
experiment was accepted by the stock updater and verified with post-boot readbacks.

That reference appliance had **already been bootloader-unlocked and repaired**.
These observations therefore do not establish a universal first-time root method
for an untouched, locked retail appliance. They also do not prove that the same
entry works on another firmware revision.

## What this public repository gives you

- The native Android application, local serial bridge and shared device contract.
- Source you can inspect and extend once your development hardware has the
  required access.
- Offline tests and an explicit separation between software checks and physical
  validation.

It also supplies the guarded Python installer and broker source. These prepare
a development session without Magisk or partition writes. No ready-to-flash image,
manufacturer APK, unlock payload or signing key is included. Factory entry on
never-modified locked units and fresh-PC authorization remain unverified; the
candidate is not a universal root installer.

## Help make the entry reproducible

A useful access report identifies the model, Android / manufacturer firmware
versions, bootloader state, whether the device was previously modified, how access
was obtained, the privilege level actually observed and the recovery path. Use
sanitized descriptions; keep serial numbers, account tokens, raw backups and keys
out of public issues.

Start with [the build guide](BUILDING.md) and review the control code before
connecting it to hardware. Do not share the MCU connection between competing
processes. A working shell and a successful APK install do not validate cooking
behavior.
