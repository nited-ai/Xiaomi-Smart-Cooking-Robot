# Architecture and development approach

## Appliance and control boundary

The reference appliance combines an Android display computer with a separate MCU.
The native client speaks the observed serial protocol over `/dev/ttyS2` at 115200
baud. `machine/src/main/cpp/bridge.c` implements framing, CRC handling, command
tracking, status interpretation and bounded program execution. The `.so` suffix
is the packaging convention for a PIE executable that Android extracts with the
application's native artifacts; this is not manufacturer firmware.

The Java control client starts the privileged bridge process and consumes its
status. UI state distinguishes connection state, telemetry freshness, command
acknowledgement, ownership of a run and confirmed idle. A command being accepted
does not establish the physical behavior of the motor or heater.

`common/control-profile.json` defines the shared device envelope. The backend has
the same contract; the generated Java class and native header implement the
corresponding rules. The Android build checks hashes of the controller source,
header and bundled ARMv7 / x86_64 executables. These checks bind this source
snapshot to artifacts; they do not prove compatibility with a physical device.

The standby helper is separate. Its source and build task are included; it uses
bounded read requests rather than an OEM power-cut command.

## Recipe execution

A recipe guide and an executable machine program are separate objects. Preparation,
weighing, resting and finishing remain human actions. Machine steps carry explicit
duration, temperature, speed and direction. Program validation binds a guide
revision, ingredient usage, device capability and a trusted preparation record.
General recipe text and AI output are not executed as controller commands.

The native interface handles manual and guided workflows. Shared Android classes
support local pairing and recipe transfer from the phone application. This public
snapshot retains existing code structure rather than rewriting the control stack
as part of publication.

## Optional service

Backend modules cover device authentication, consent, bounded generation, private
drafts, recipe publication, catalogue identity and pantry handling. Tests use
synthetic identities, a Firestore test double and mocked providers. Production
hosting configuration, account sessions, provider keys and deployed datasets are
excluded. The included local server needs independently configured Firebase and
model-provider access before it can serve real requests.

## Evidence model

Record source inspection, unit tests, emulator checks and physical observations
separately. A meaningful hardware report binds a source revision and APK hash to
the actual appliance / firmware, describes the user's action and telemetry, and
states the observed physical result. Historical device tests are useful context;
they are not validation of newer combined sources.
