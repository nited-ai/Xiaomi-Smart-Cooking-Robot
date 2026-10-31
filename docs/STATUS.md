# Snapshot and validation status

Publication date: 2026-10-30.

Public presentation updated on 2026-10-31 under the name **Xiaomi Smart Cooking
Robot**. The gallery contains only the two native UI captures from Build 191's
2026-10-26 QA set. It does not include older UI revisions. These are existing
development / emulator captures, not new hardware validation or a public-APK
visual acceptance test. The application/control implementation is unchanged by
this presentation update.

An English and German setup guide was added on 2026-10-31. Its command examples
were checked against the current source and documented reference entry. Writing
the guide did not perform a new root, APK installation or hardware trial. Root
Network ADB and the app's required Magisk runtime remain separate prerequisites;
the stock locked-device persistent-root gap is still open.

This is a fresh-history export of the development worktree based on private
handoff commit `f3b788d0`, including later uncommitted control changes. It does not
claim to reproduce the last physically installed APK. The project board recorded
Build 190 as installed on 2026-10-23; later Build 191 control changes were checked
offline and their physical heating / motor behavior remained unverified.

## Publication verification

- Backend authentication-client bundle: built successfully with esbuild.
- Included backend suite: **143 tests passed, 0 failed** on Node.js 24.21.0.
  The repository declares Node.js 22; CI is configured to check that version.
- Publication checker: controller source, generated header, both native artifacts
  and shared contracts match their recorded hashes. The configured secret-pattern
  and excluded-file checks passed. These checks are not a full security audit.
- Gitleaks 8.30.1 directory scan: no findings, with redacted reporting.
- Appliance/common Java sources compile against the official Android 34 API jar
  using local resource-ID and BuildConfig stand-ins. This checks Java types and
  syntax, including the public-edition artwork fallback; it does not build an APK
  or validate Android resources, packaging, execution or visual alignment.
- Android APK compilation, emulator acceptance, screenshot review and physical
  appliance validation were not performed for this public snapshot. The Android
  SDK / NDK needed for a rebuild were unavailable in this environment.

## Open work

- Rebuild the Android APKs in a fully provisioned Windows SDK / NDK environment.
- Run final-APK emulator and supervised hardware acceptance before any appliance
  release. Confirm the stop path, telemetry freshness, pot / cover behavior,
  temperature behavior, rotary input and guided-program transitions.
- Inspect the public source edition at the appliance display size. Omitted photos
  and 3D assets mean private-build visual parity is not established. Existing
  line icons were copied unchanged. The new schematic artwork fallback still
  needs rendered screenshot review, including optical centering wherever used
  inside a circle; no visual acceptance claim is made.
- Port the Windows NDK host path and improve first-time developer setup.
- Replace mixed-origin assets with clearly licensed originals.
- Independently deploy and validate service integrations before enabling account,
  AI or cloud write features. Mock tests do not prove provider login or deployment.

No firmware was flashed, no device was installed or controlled, and no production
service was deployed while preparing this source release.
