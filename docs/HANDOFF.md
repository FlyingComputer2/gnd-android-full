# Handoff — EdgeTX-first

## Working path (TX16S)

1. Build wasm: `scripts/build-edgetx-wasm.sh` → `app/src/main/assets/edgetx/edgetx-tx16s-simulator.wasm`
2. APK links `libgndfull_edgetx.so` (WAMR host + `WasmEdgeTxAdapter`)
3. UI maps gimbals → analog LH/LV/RH/RV, switches → `simuSetSwitch` (-1/0/1), nav keys → `simuSetKey`, LCD touch → `simuTouchDown/Up`
4. `EdgeTxAdapterFactory.createAuto()` uses WASM when the asset exists, else `FakeEdgeTxAdapter`

## Deferred (per user)

- Rover USB RC / `RoverControlBridge` (set `UsbGroundSession(..., enableRc=true)` when ready)
- S.Port / HAL telemetry adapters

## Native libs

| Library | Role |
| --- | --- |
| `gndfull_edgetx` | WAMR + EdgeTX wasm host |
| `gndfull_receiver` | USB video RTP depay only |
