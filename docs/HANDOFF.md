# Handoff — EdgeTX-first

## Working path (TX16S)

1. Build wasm: `scripts/build-edgetx-wasm.sh` → `app/src/main/assets/edgetx/edgetx-tx16s-simulator.wasm`
2. APK links `libgndfull_edgetx.so` (WAMR host + `WasmEdgeTxAdapter`)
3. UI maps gimbals → analog LH/LV/RH/RV, switches → `simuSetSwitch` (-1/0/1), thin TX16S key strip (RTN/ENT/TELE/PAGE) → `simuSetKey`, LCD touch → `simuTouchDown/Up` (MDL/SYS via Quick Menu on LCD)
4. EdgeTX audio: `simuQueueAudio` → `AudioTrackEdgeTxAudioAdapter` (32 kHz mono PCM16)
5. WAMR WASI: host SD dir is `/sdcard/Download/FlyingComputer/edgetx/sd` (falls back to app-private `files/edgetx/sd`); preopened as `/::<host>`; `RADIO`/`MODELS` yaml is never overwritten by the seed zip (see `configure_wasi_preopens` in `edgetx_wasm_host.cpp`)
6. `EdgeTxAdapterFactory.createAuto()` uses WASM when the asset exists, else `FakeEdgeTxAdapter`

## CRSF (channel path + telemetry injection)

- `CrsfChannelsFrame`: 16-channel `0x16` frame builder matching EdgeTX `createCrossfireChannelsFrame` (status byte `0x02` = CH5 arming mode). Not sent anywhere yet: the rover link is still MSP `0x4201`
- Rover RC (`0x4201`, 17 B): steer CH1, throttle CH3, `aux` = CH6 → rover CH2 servo, `aux2` = CH7 → rover CH3 servo. Rovers older than `aux2` accept only 15 B, so flash the rover first
- `simuSendTelemetry` wired via JNI (`WasmEdgeTxAdapter.sendCrossfireTelemetry`); rover `0x4220` → CRSF link statistics (LQ from rover RC loss; RSSI / SNR 0, the rover has none)

## Deferred (per user)

- Enable bench RC: `UsbGroundSession(..., enableRc=true)` in `MainActivity`
- Full CRSF → rover_sample transport (USB still MSP today)

## Native libs

| Library | Role |
| --- | --- |
| `gndfull_edgetx` | WAMR + EdgeTX wasm host |
| `gndfull_receiver` | USB video RTP depay only |
