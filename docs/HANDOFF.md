# Handoff — EdgeTX-first

## Working path (TX16S)

1. Build wasm: `scripts/build-edgetx-wasm.sh` → `app/src/main/assets/edgetx/edgetx-tx16s-simulator.wasm`
2. APK links `libgndfull_edgetx.so` (WAMR host + `WasmEdgeTxAdapter`)
3. UI maps gimbals → analog LH/LV/RH/RV, switches → `simuSetSwitch` (-1/0/1), nav keys → `simuSetKey`, LCD touch → `simuTouchDown/Up`
4. `EdgeTxAdapterFactory.createAuto()` uses WASM when the asset exists, else `FakeEdgeTxAdapter`

## CRSF (channel path + telemetry injection)

- `CrsfChannelsFrame`: 16-channel `0x16` frame with CH5 arming status `0x02` (EdgeTX CH5 mode)
- `EdgeTxOutputMapper` sets explicit CH5 PWM before packing; rover USB still uses MSP `0x4201`
- `simuSendTelemetry` wired via JNI (`WasmEdgeTxAdapter.sendCrossfireTelemetry`); rover `0x4220` → link stats

## Deferred (per user)

- Enable bench RC: `UsbGroundSession(..., enableRc=true)` in `MainActivity`
- Full CRSF → rover_sample transport (USB still MSP today)

## Native libs

| Library | Role |
| --- | --- |
| `gndfull_edgetx` | WAMR + EdgeTX wasm host |
| `gndfull_receiver` | USB video RTP depay only |
