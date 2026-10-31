# Snapshot and validation status

Updated: 31 October 2026. Stage: **experimental developer preview**.

The factory-session candidate removes the app's mandatory Magisk dependency when
the owner already has factory root ADB. It does not establish a universal factory
entry, persistent root or untouched locked-device compatibility.
[Installation](INSTALLATION.md) · [Root-path audit](STOCK-INSTALL-AUDIT.md).

## Software evidence for Build 192

- Appliance APK and androidTest APK compiled with official Android 34 platform,
  Build Tools 34.0.0, NDK 27.1.12297006 and JDK 21. Both broker ABIs cross-compiled.
- Appliance JVM suite: **338 tests, zero failures/errors/skips**.
- Host installer suite: **50 tests passed**, using fake ADB boundaries.
- Native broker: **22 real Linux tests passed**, using real peer UIDs and synthetic
  bridges. Authentication, fixed arguments, held bridge FD, bounded relay,
  disconnect/expiry, backward clock, admin restrictions and cleanup are exercised.
- Final APK SHA-256:
  `5426c8f3e678c5f8747d4567841c8acddad6c4bba28c9c6038142ca8add91726`.
  This identifies the local tested build; it is not a hardware certification.
- Dedicated Android 10 x86_64 emulator: guarded installer apply, installed APK
  readback, app-UID authenticated ID, native STATUS and host verify succeeded.
  Both a clean install and a guarded update were exercised. The factory transport
  instrumentation passed **18 assertions**, including
  rejection of a non-root socket before sending a token and private-config
  validation and active/uncertain/recovery reconfiguration guards. An optional
  child test ran the real Android bridge with the UART node absent: it returned
  expected missing-UART exit code 7 through the socket Process adapter.
  A mismatching session returned 409. Explicit revocation succeeded;
  subsequent app status returned 404. ID/STATUS never requested UART.
- Final-APK controller/parser replay: **92 pot-removal checks passed**. Real
  Home/controller integration: **56 automatic-pot checks passed**. Both used
  memory-only transport and synthetic telemetry, never appliance control.
- Installed native bridge self-test: **16,910 assertions and 88 pipe scenarios
  passed**, covering temperatures 0–255, diagnostic phases, guards and mandatory
  STOP acknowledgement logic; no UART opened.
- Backend bundle built; **143 tests passed**, zero failures, on Node.js 24.21.0.
  The project declares Node.js 22; CI checks the declared version.

Emulator tests used an isolated AVD, not a connected robot. The emulator's unused
`/dev/ttyS2` node was removed after verifying qemu/ranchu identity, so transport
checks could explicitly refuse any UART-capable environment. No physical robot
was installed, controlled, rebooted or flashed during this work.

The pinned bridge source/header/native artifacts and shared device profile are
unchanged. The new access transport does not requalify physical cooking behavior.
Source review resolved two findings before delivery: shell-writable executable
staging ancestry and cleanup after a backward clock correction. A test-harness
preflight cleanup issue was also fixed: an injected refusal was observed to leave
the configured session usable, followed by the full passing transport suite.
Final publication check passed for **419 files**, including unchanged controller
pins; Gitleaks 8.30.1 directory scan reported no findings. These are packaging
and configured secret checks, not a full security audit.

## Presentation and source provenance

Public source was first exported on 30 October 2026, based on private development
handoff `f3b788d0` plus later control changes. It does not reproduce the last APK
physically installed on the reference: the project board recorded Build 190 on
23 October; later control changes still needed supervised physical validation.

The gallery contains only two native captures from Build 191's 26 October QA set.
They show test/emulated state, not a cooking trial. Build 192 changes access,
without replacing those images with older UI or claiming a new full visual pass.
The public source omits mixed-provenance product photography/3D assets and uses
schematic fallbacks. Private-build visual parity is not established.

## Hardware gates still open

- Original factory entry and **fresh-PC RSA authorization** on a never-modified,
  locked C3; exact firmware, root and existing SELinux conditions must be observed.
- First app-UID authorization and exclusive UART ownership on that unit.
- Fresh telemetry, pot/lid behavior, physical STOP, temperature/motor behavior,
  rotary input and guided-program transitions with the exact candidate APK.
- Normal return to the original app, expiry/revocation, and renewal after an idle
  reboot. Shutdown acknowledgement is never physical-idle proof.
- Persistent locked-device root and boot/recovery trust: **not established**;
  no first-root firmware package is released.

Other open work: Windows-native build portability, appliance-size visual
acceptance (including optical centering of schematic/icon fallbacks), clearly
licensed replacement imagery and separately deployed provider integrations.
Backend mocks do not prove real login, AI providers or service deployment.

No firmware, OEM APK, private signing key, personal backup or device token is
published. Packaging/secret scans are useful checks, not a full security audit.
