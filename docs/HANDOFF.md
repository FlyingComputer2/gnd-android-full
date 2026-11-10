# Handoff

## Wired adapters (main)

| Layer | Types | Role |
| --- | --- | --- |
| EdgeTX | `EdgeTxAdapterFactory`, `FakeEdgeTxAdapter`, `NativeEdgeTxAdapter` | Mixer/LCD seam; fake default, JNI stub selectable |
| Control | `ControlSession` | 20 ms refresh, arm/disarm, menu failsafe, key events |
| RC map | `EdgeTxOutputMapper`, `RoverRcFrame` | CH1/CH3 → steer/thr; SA arm; MSP 0x4201 |
| MSP | `MspV2` | DVB-S2 CRC encode/decode |
| USB | `UsbGroundSession`, `RcUsbHandler` | ch0 video + ch2 `rc-v1` on one `UsbProxy` |
| Publish | `RoverControlBridge` | 50 Hz RC over USB when ch2 open |
| Video OSD | `VideoMetrics`, `OsdPresenter` | RTP/AU counters + USB telemetry labels |

## Arm policy (bench)

SA up (lower row) with neutral throttle transitions `READY` → `ARMED`. SA down disarms. Menu open forces neutral/disarm. USB drop → `FAILSAFE`.

## Not done

- Real EdgeTX simulator linked behind `NativeEdgeTxAdapter`
- USB clock channel 3
- Configurable channel map UI

## Tests

`WireTest`, `FakeEdgeTxAdapterTest`, `MspV2Test`, `EdgeTxOutputMapperTest`, native `receiver_test`.
