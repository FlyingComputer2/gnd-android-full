# Agent instructions

Read [README.md](README.md) and the linked [`aidocs/gnd-android-full`](../aidocs/gnd-android-full/) plan before changing architecture.

## Preserve

- **EdgeTX seam:** UI and transport talk only through `com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter`. Real simulator integration replaces `FakeEdgeTxAdapter` / JNI stub; do not embed mixer logic in Activities.
- **USB video:** Channel 0 remains receive-only `rtp-h264-v1` per RGP4 v2 (ported from `gnd-android`). One USB reader/writer, per-channel credits and bounded workers.
- **Safety:** Opening the hamburger menu or losing focus must call `ControlSession.setMenuOpen(true)` / failsafe paths — never leave stale stick output armed.
- **Modes:** RC is the only implemented main view; CLI/PLN stay explicitly unavailable until implemented.

## Verify

Use the Gradle and CMake commands in README. Hardware phone/P4 tests are not claimed by CI.
