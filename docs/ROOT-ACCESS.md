# Getting closer to your own hardware

**Looking for the actual steps?** Use the new
[device-access and installation guide](INSTALLATION.md) or
[die deutsche Schritt-für-Schritt-Anleitung](INSTALLATION.de.md).
It covers the factory hotspot / NET ADB entry, root checks, source build,
APK installation, first checks and maintenance cleanup. This page records the
evidence boundary behind those steps.

The reason for privileged access is practical: an ordinary Android application
cannot open the appliance's MCU serial port. The control client needs that local
connection to communicate with the motor / heater controller and receive state.
The project-owned bridge is launched with `su`; the reference development setup
used a Magisk runtime.

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

It currently supplies no ready-to-flash image, manufacturer APK, unlock payload,
signing key or one-click root installer. Keeping those out of the initial source
release avoids confusing a reference-device experiment with a supported installation
route for everyone's machine.

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
