# Stock installation: audit and candidate

Audit date: 31 October 2026. [Installation steps](INSTALLATION.md).

We want owners to be able to use their robot without depending on the vendor's
cloud or a private developer setup. A guide that ends at “install Magisk” leaves
the hardest step to the reader. We traced that gap through the original factory
service, the previous first-root OTA and the app's actual privilege boundary.

## What the old route proves

| Stage | Evidence | Limit |
| --- | --- | --- |
| Original factory entry | Reference observations and matching SystemControl behavior: hotspot `/*nam_looc_etvc*/`, code 1123, NET ADB | Other firmware and first-PC authorization not established |
| Root ADB without Magisk | UID 0 on original boot after reset on the reference | Reference had already been unlocked/repaired; not a never-modified locked unit |
| Signed first-root OTA | Reference updater acceptance and post-boot readbacks for an earlier candidate | Does not transfer to later package revisions or locked boot trust |
| Historical installer helper | Source explicitly requires an unlocked bootloader | Cannot serve as a locked-unit first-root method |
| Later Wi-Fi candidate | Build manifests preserve experimental restrictions | Unresolved Wi-Fi/USB behavior; not approved for general installation |

The historical packer also depends on frozen private inputs: a particular APK
and signer, full original/root images, external Magisk package, OEM update binary
and signing material. It is not a reproducible public-source installer. Earlier
RC2 evidence is not acceptance of the later wifi-r4 candidate. Internal manifests
still recorded `productionFleetApproved=false`, `newPackageRecoveryInstalled=false`
and `secondMachineAccepted=false`.

Offline signature/key matches do not establish a locked robot's boot-chain trust.
The separate SBOOT/TOC enforcement of `boot` and `vbmeta` remains unresolved;
a reference log mentioning no RotPK does not prove the state of retail units.
The old rollback handles detected write failures, not power loss or every
post-boot failure, and supplies no general standalone restore/resume workflow.
Those packages are not included in this repository.

## The prepared route: no boot writes

The new candidate uses factory root ADB to start a **bounded development session**:

```text
Owner PC → root ADB → local authenticated broker
                              ↑
                  app UID + private token
                              ↓
                 unchanged serial bridge → MCU
```

The normal APK remains an ordinary Android app. A DUMP-protected receiver verifies
an ID exchange under that app's own UID before storing its private configuration.
The app checks the socket server is UID 0 before disclosing its token. The broker
accepts only ID, SERVE and PROBE, fixes bridge arguments and permits one child.
No TCP root service, arbitrary command interface or persistent startup hook is added.

The installer verifies model/firmware/root/SELinux, APK signature/version/hash,
installed UID and native readbacks. It launches the broker from the installed
Package Manager native-library directory; private bridge/config storage is under
`/data/local/xcr-access`, below a verified root-owned non-writable parent. It avoids
shell-owned non-sticky `/data/local/tmp` for executable trust. Binary staging uses
ADB push: Windows shell stdin can truncate at byte 0x1a, so hashes are checked
before any staged bridge executes.

The session defaults to one hour, has a four-hour maximum, survives PC disconnect
and ends at reboot. The broker uses monotonic elapsed time. Expiry/disconnect
closes the existing bridge input and requests graceful termination; it does not
escalate to SIGKILL or claim physical idle. ID/STATUS/provisioning never open UART.
SERVE/PROBE inherit the existing bridge's OEM-app force-stop and UART behavior.
Access guards therefore complement, rather than replace, supervised physical checks.

No boot, AVB, recovery, system, bootloader or Wi-Fi-driver writes are required.
No SELinux change, factory reset or data-clearing workaround is included.
Existing Magisk access remains when no factory configuration exists; an invalid
factory configuration fails closed rather than silently switching access paths.

## Acceptance still needed on a real untouched robot

1. Record original model/firmware, never-modified history and locked state through
   available read-only observations. Keep a supervised recovery plan appropriate
   to that actual unit; a reference-device backup is not a recovery image for another.
2. Use a fresh owner PC key and the original factory menus. Observe authorization,
   Network ADB UID 0 and existing SELinux state without changing them.
3. Run dry-run prepare, install the verified owner-built APK and authenticate the
   app UID. Confirm no partition changes and no UART during identity checks.
4. Under supervision, establish exclusive control and verify fresh telemetry,
   physical STOP, pot/lid handling, motor/temperature and program transitions.
5. Confirm normal disconnection, lease expiry, explicit revocation, original-app
   return and a new session after reboot. Test reboot only while physically idle.

Until those observations exist, **untouched locked-device support and persistent
root are not claimed**. The candidate closes the software runtime dependency on
Magisk; it cannot replace the missing factory-access/hardware experiment.
See [STATUS](STATUS.md) for the exact software verification completed.

General references: [Android ADB](https://developer.android.com/tools/adb) and
[upstream Magisk installation](https://topjohnwu.github.io/Magisk/install.html).
Neither is a manufacturer-approved root/unlock procedure for this robot.
