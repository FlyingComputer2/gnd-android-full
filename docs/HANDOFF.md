# Handoff — milestone 0

## Delivered

1. New Android app `com.flyingcomputer.gndfull` with portrait RC shell matching the planning SVG structure.
2. `EdgeTxAdapter` API, `FakeEdgeTxAdapter`, and `NativeEdgeTxAdapter` JNI stub in `gndfull_receiver`.
3. `ControlSession` with menu/failsafe neutral publish hook (transport not connected).
4. USB video stack copied from `gnd-android` (Wire, UsbProxy, VideoReceiver, native RTP depay).

## Next (per aidocs milestones)

1. Pin EdgeTX commit and prove `arm64-v8a` simulator build behind `NativeEdgeTxAdapter`.
2. Hardware spike: USB video on target phone + P4.
3. Resolve P4 channel-2 MSP RC contract; map `copyMixedOutputs()` to rover steering/throttle with arming state machine.
4. OSD metrics with explicit measurement points; clock USB channel 3 contract.

## Known gaps

- No Gradle wrapper checked in (same as `gnd-android`); CI uses `gradle/actions/setup-gradle`.
- Spinner shows RC/CLI/PLN but only RC is functional.
- EdgeTX keys and LCD touch are visual only.
- `ControlSession` uses fake adapter by default (`useNativeStub=false` in `MainActivity`).
