# Handoff — EdgeTX-first

## Working path (TX16S)

1. Build wasm: `scripts/build-edgetx-wasm.sh` → `app/src/main/assets/edgetx/edgetx-tx16s-simulator.wasm`
2. APK links `libgndfull_edgetx.so` (WAMR host + `WasmEdgeTxAdapter`)
3. UI maps gimbals → analog LH/LV/RH/RV, switches → `simuSetSwitch` (-1/0/1), thin TX16S key strip (RTN/ENT/TELE/PAGE) → `simuSetKey`, LCD touch → `simuTouchDown/Up` (MDL/SYS via Quick Menu on LCD)
4. EdgeTX audio: `simuQueueAudio` → `AudioTrackEdgeTxAudioAdapter` (32 kHz mono PCM16)
5. WAMR WASI: host SD dir is `/sdcard/Download/FlyingComputer/edgetx/sd` (falls back to app-private `files/edgetx/sd`); preopened as `/::<host>`; `RADIO`/`MODELS` yaml is never overwritten by the seed zip (see `configure_wasi_preopens` in `edgetx_wasm_host.cpp`)
6. `EdgeTxAdapterFactory.createAuto()` uses WASM when the asset exists, else `FakeEdgeTxAdapter`

## Deferred (per user)

- Rover USB RC / `RoverControlBridge` (set `UsbGroundSession(..., enableRc=true)` when ready)
- S.Port / HAL telemetry adapters

## Native libs

| Library | Role |
| --- | --- |
| `gndfull_edgetx` | WAMR + EdgeTX wasm host |
| `gndfull_receiver` | USB video RTP depay only |
