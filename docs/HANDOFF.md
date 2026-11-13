# Handoff — EdgeTX-first

## Working path (TX16S)

1. Build wasm: `scripts/build-edgetx-wasm.sh` → `app/src/main/assets/edgetx/edgetx-tx16s-simulator.wasm`
2. APK links `libgndfull_edgetx.so` (WAMR host + `WasmEdgeTxAdapter`)
3. UI maps gimbals → analog LH/LV/RH/RV, switches → `simuSetSwitch` (-1/0/1), nav keys → `simuSetKey`, LCD touch → `simuTouchDown/Up`
4. `EdgeTxAdapterFactory.createAuto()` uses WASM when the asset exists, else `FakeEdgeTxAdapter`

## CRSF (EdgeTX external module)

Set the model's **External RF** to **CRSF** (MDL → Model Setup). The app is the "module":

- The wasm is built with `scripts/edgetx-patches/` (applied by `scripts/build-edgetx-wasm.sh`): the simulator's external S.Port becomes a host bridge, `simuExtModuleTxRead` (bytes EdgeTX sends) and `simuExtModuleRxPush` (bytes to EdgeTX's CRSF parser, CRC checked).
- `ControlSession` drains the external module every 20 ms (`CrsfStreamParser`, newest `0x16` frame). `RoverControlBridge` maps it to rover RC `0x4201` (17 B) at 50 Hz; no fresh frame (100 ms, External RF off) = nothing sent = rover failsafe.
- Channels: CH1/CH2 unused, CH3 throttle, CH4 steering (rover servo CH1), CH5 arm (EdgeTX CRSF arming mode CH5 ≥ 1750 µs, so an unmixed CH5 at 1500 µs never arms; or the Switch-mode status bit), CH6 mode (≤ 1250 µs manual = rover mode 0, ≥ 1750 µs rate = mode 1, in between / unmixed = the rover's own `mode` param), CH7 rover servo CH2 (`aux`), CH8 rover servo CH3 (`aux2`). One RC frame per CRSF frame drained (no second timer). Menu open / radio off / USB failsafe: nothing is sent, so the rover centres steering and holds CH2/CH3 at `ch2_failsafe` / `ch3_failsafe`. RC `t_us` is ground time from krono over USB channel 3 (`SynchClient`), so the rover's `rc_lat_ms` is real. The rover itself arms only with neutral throttle.
- Telemetry (rover `0x4220`, 42 B) → CRSF frames into EdgeTX: link statistics (1RSS / 2RSS = ground→rover RSSI at the rover C6, RQly / TQly from rover RC loss), battery (RxBt = VBUS, Curr = motor bridge), attitude (Ptch / Roll / Yaw), flight mode (FM: MAN / RATE / HOLD, `*` = disarmed, FS, FAULT). MDL → Telemetry → Discover new sensors.
- Rover firmware with 17 B RC and 42 B telemetry first (rov-esp32p4 `fix/rover-servo-channels`).

## Native libs

| Library | Role |
| --- | --- |
| `gndfull_edgetx` | WAMR + EdgeTX wasm host |
| `gndfull_receiver` | USB video RTP depay only |
