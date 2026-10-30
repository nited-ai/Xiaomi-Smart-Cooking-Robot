# Contributing

KitchenOS is an early source release. Small, focused changes with reproducible
evidence are the most useful contributions.

1. Open an issue describing the behavior or improvement and the relevant module.
2. Work in a branch. Keep unrelated formatting and generated files out of the diff.
3. Run the relevant existing tests and the publication checker. Include their
   output summary and distinguish unit, emulator and hardware evidence.
4. Open a pull request with the problem, resulting behavior and remaining limits.

Changes to the controller require explicit review of the profile, source and
artifact hashes. Never solve a failed pin check by silently accepting a new hash.
Record why the control semantics changed and what was verified. Hardware tests
must be supervised, on identified compatible hardware, with a working stop path
and recovery plan. Software test results do not authorize unattended cooking.

Use synthetic examples for recipes, identities and tokens. Do not submit secrets,
private account data, raw device logs, firmware, decompiled manufacturer code or
artwork without clear redistribution rights. Dependencies retain their licenses;
add attribution when introducing external source or assets.

Contributions of original code are submitted under this repository's MIT license.
This does not change the licenses of third-party components or grant trademark
rights.
