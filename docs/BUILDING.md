# Building and local development

For the full device setup sequence, including the original factory Network ADB
entry and APK installation, use [the installation guide](INSTALLATION.md).
This page describes the build and configuration.

## Backend tests

Use Node.js 22. From `backend/`, run `npm ci`, `npm run build`, then `npm test`.
The build bundles the Firebase browser authentication client from `web/auth.js`.
Tests use mocks; they do not require cloud credentials or invoke real AI providers.
Bulk catalogue/image-library datasets and their dependent test suites are outside
this initial publication.

## Android

Install JDK 17 or 21, Android SDK Platform 34, Build Tools 34.0.0 and Android NDK
27.1.12297006. The wrapper uses Gradle 8.5 and Android Gradle Plugin 8.2.2.
Set `JAVA_HOME` and `ANDROID_HOME`, or create an ignored `local.properties` with
your own `sdk.dir`. The standby compiler task currently uses the Windows NDK host
path. Linux/macOS portability is an open contribution area.

From PowerShell at the repository root:

```powershell
./gradlew.bat :machine:testApplianceUnitTest :machine:assembleAppliance :phone:assembleDebug :phone-link:assembleDebug
```

The appliance variant is non-debuggable, uses the local debug signing certificate
and retains the application's existing target SDK 28 for its appliance-specific
Android Wi-Fi behavior. It is intended for sideload research, not a Play Store
release. Your new debug certificate does not match an existing private device
installation; building does not grant installation or replacement privileges.

The bridge executables are project-owned, pinned development artifacts included
with their complete C source and generated header. The standby helper and new
factory-access broker are built from source by Gradle. The broker targets API 29
for both appliance ARMv7 and emulator x86_64. A bridge rebuild uses NDK clang, target
`armv7a-linux-androideabi26` or `x86_64-linux-android26`, and flags
`-O2 -fPIE -pie -Wall -Wextra -Werror`. Compiler/toolchain differences can change
artifact hashes. Review changes and verify behavior before updating pins; a hash
change is not a validation procedure.

No SDK, private keystore, OEM framework, decompiled application or firmware image
is included. Photographic and 3D assets with mixed provenance are omitted. Some
screens consequently use existing schematic fallbacks or have no bundled photo.
This edition has not had an appliance-size visual acceptance pass.

## Network and cloud configuration

The Android `kitchenApiBase` property defaults to an empty string. Configure a
compatible service explicitly when testing cloud features:

```powershell
./gradlew.bat :machine:assembleAppliance -PkitchenApiBase=https://your-service.example
```

Existing public catalogue / recipe image access uses `https://app.kalomatik.ai`.
Some image helpers and content examples retain public Kalomatik asset URLs.
Account authentication uses Firebase / Google endpoints. Local pairing uses HTTPS
on port 8765. Voice and AI providers are backend integrations requiring separately
configured credentials and consent. Review the source before enabling them.
An empty cloud property does not make every user-initiated catalogue feature
offline; this release does not include an offline switch for all public URLs.

For the included standalone server (`cd backend; npm start`), supply:

- `FIREBASE_WEB_CONFIG`: JSON containing your `apiKey`, `authDomain`, `projectId`
  and `appId` (client configuration, not a service-account private key).
- `PUBLIC_ORIGIN`: your HTTPS origin, with no path or query.
- `AUTH_PROVIDERS`: enabled providers, comma-separated: `google`, `microsoft`,
  `facebook`, `apple`, `email`. Enable the selected providers in your own project.
- Application Default Credentials for your own Firebase / Google project.
- `VERTEX_LOCATION` and `VERTEX_MODEL` for the chosen Vertex model.

The service checks configuration on startup. Running it is a separate integration
exercise: configure hosting, provider login, account rules, attestation, consent
and quotas for your own environment. Production deployment files and a hosted
Kalomatik service are not supplied by this repository.

## Access tooling and emulator tests

The standard-library Python installer is `tools/factory_access.py`; start with
`--help` and the installation guide. Run host policy tests with:

```powershell
python -m unittest discover -s tools/tests -p test_factory_access.py
```

On Linux, run `sudo python3 tools/tests/test_factory_broker.py`. It compiles the
real broker and synthetic child executables into private temporary directories,
uses real Linux peer UIDs and sockets, and never opens an appliance UART. Root
is needed solely to exercise different UID credentials; CI runs on a disposable
Ubuntu runner.

Build `:machine:assembleApplianceAndroidTest` for the dedicated-emulator suites.
Use only your own Android 10 emulator, verify `ro.kernel.qemu=1` and no `/dev/ttyS2`,
then install the appliance APK and its androidTest APK. Instrumentation components:

- `com.kitchenos.connect.test/com.kitchenos.connect.tests.FactoryAccessInstrumentation`
  with `-e requireFactory true` after provisioning: app-UID identity and transport checks.
  Optional `-e exerciseChild true` also checks the actual bridge's missing-UART
  failure and socket exit handling; the suite requires `/dev/ttyS2` to be absent.
- `com.kitchenos.connect.test/com.kitchenos.connect.tests.PotRemovalRegressionInstrumentation`:
  real controller/parser with memory-only transport.
- `com.kitchenos.connect.test/com.kitchenos.connect.tests.PotAutomationInstrumentation`:
  real Home/controller integration with synthetic telemetry and memory-only transport.

`am instrument -w` must report the suite's success text; ADB exit status alone
does not establish test success. Use `--test-emulator` only with an `emulator-*`
serial; both that serial form and qemu are required. It cannot relax a physical
device's profile guard. Keep token-bearing configs and test artifacts out of Git.

## Hardware boundary

An ordinary Android install cannot access the appliance UART. The reference
development environment used privileged/root access. The new factory session
uses existing root ADB without a boot/recovery flash or persistent-root install.
Compatibility of an untouched locked appliance is unverified. Do not issue motor/heater commands during an
unsupervised development session or while another process owns the serial port.
