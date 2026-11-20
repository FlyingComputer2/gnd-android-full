# gnd-android-full

Android ground station with on-device EdgeTX UI, virtual gimbals/switches, and USB rover video from the ground P4. This repository implements **milestone 0 / touch-display prototype scaffolding** described in the planning docs under [`../aidocs/gnd-android-full/`](../aidocs/gnd-android-full/).

## What works in this baseline

| Area | Status |
| --- | --- |
| Portrait RC layout (thin app bar, video, maximized LCD, TX16S key strip, switches, gimbals) | Implemented |
| EdgeTX TX16S via WAMR + wasm (`WasmEdgeTxAdapter`) | Implemented — requires wasm asset |
| Fake EdgeTX (`FakeEdgeTxAdapter`) | Fallback without wasm |
| USB RGP4 v2 video channel 0 (`rtp-h264-v1`) | Ported from [gnd-android](https://github.com/FlyingComputer2/gnd-android) |
| USB channel 2 `rc-v1` → rover_sample MSP `0x4201` @ 50 Hz | `RoverControlBridge` + `RcUsbHandler` |
| OSD: `ROVTX: <rssi> dbm <rate> mbps <loss>%` (rover → ground, measured by the ground: its link frame `0x4230` on USB ch2), `GNDTX: …` (ground → rover, measured by the rover: telemetry `0x4220` link counters, 58 B), `VIDEO: <fps> fps <jitter> ms <latency> ms <loss>%` (decoder output; RFC 3550 frame jitter; camera-to-decode via the ground clock on USB ch3; RTP sequence gaps over USB = loss after the ground's FEC), `RC: <latency> ms <loss>%` (rover's RC p50 latency and loss). Rate and loss from free-running receiver counters over ~1 s; `---` = unknown or stale (> 2.5 s) | `ui/OsdPresenter`, `transport/rover/LinkRate`, `video/VideoMetrics` — ROVTX needs gnd-esp32p4 with the link frame, GNDTX rate/loss a rover with the 58 B telemetry |
| Custom switches: 8 slots (upper strip 1–4, lower 5–8), each with a label, type `push` (0\|1, held while touched), `toggle` (0,1) or `mpos` (0..n−1, tap left half = down, right half = up) and an EdgeTX input: switch SA–SH (2 or 3 positions; 0 = up) or axis S1 / S2 / LS / RS (any n ≤ 12, evenly spaced −1024..1024). Menu → Switches; saved in SharedPreferences `switches`. Defaults = the old fixed SE SF SG SH / SD SC SB SA layout. A 3-position slot on SF/SH needs that switch set to 3POS in EdgeTX hardware settings | `switches/SwitchConfig`, `ui/SwitchStripView`, `ui/SwitchConfigDialog` |
| USB auto-connect: the ground P4 connects whenever it is plugged (attach intent / broadcast), retries every 2 s after a USB error while still plugged; no menu action | `MainActivity.discoverUsb` |
| USB reconnect resumes driving without an arm-switch cycle (the rover still requires neutral throttle to leave failsafe / arm) | `ControlSession.onUsbConnected` |
| CLI mode: 4 consoles side by side (rov vstreamer 5092, rov l3 2410, gnd l3 2400 on the ground P4, rov fs below) over the ground USB link (channel 4 `cli-v1`, no IP needed), tap to focus, keyboard suggestions + word-by-word completion of every command, argument and value, with enum values listed (modulation, channel, fec, fcs, …) (dropdown + ⇥ common prefix, per-console history kept across app restarts in SharedPreferences `cli_history`); RC keeps sending disarmed hold frames so telemetry stays up | `cli/CliView`, `transport/CliUsbHandler` — needs gnd-esp32p4 with USB channel 4 |
| CLI "rov fs": the rover's files over [udpfs](https://github.com/FlyingComputer2/udpfs) (ground UDP 2440 → L3 upstream 11), as root on its `/sdcard` and `/winject`: `ls [-l]`, `stat`, `get [-r] REMOTE [LOCAL]`, `put [-r] LOCAL REMOTE`, `rm`, `mkdir`, `rmdir`, `mv`, `crc`, `cat`, `info`, `list_open`, `lls`, `help`. Runs in the app (binary datagrams on USB channel 4, port 2440); transfers have a window of 8 requests in flight, resend lost ones, and are checked with the rover's CRC32; a download goes to `<file>.part` first. Local paths are under the storage root | `cli/ScpConsole`, `udpfs/UdpfsClient`, `transport/CliUsbTransport` — needs a rover and ground with upstream 11 |
| FILES mode: a file explorer of the rover's files (the same udpfs console as "rov fs"). Toolbar `^` parent, `<` back, path + `go`, `select`, `upload` (any document from the picker, into the current directory); columns file / type / size / modified, tap a header to sort (directories stay first), `..` and `.` rows, tap a directory to open it. Long-press an entry: select, download (to `scp/`, a directory recursively), crc32, rename, move, delete (a directory must be empty). Select mode (`select`, or long-press → select): tap entries to toggle, `all`, then `download` or `delete` (confirmation lists the names) them in one job; per-entry errors are listed, a link error stops the job. Starts in `/sdcard`; `/` shows `/sdcard` and `/winject`. One operation at a time, shared with "rov fs"; driving blocked like CLI | `files/FilesView`, `files/FileListing` |
| Storage root `Download/FlyingComputer` (`AppStorage`): `edgetx/sd/` (the EdgeTX SD card: models, scripts, sounds) and `scp/` (rover files), visible in Files and to a PC over USB. Needs "All files access" (menu ☰ → Files access); without it the app's private files directory is used | `AppStorage`, `edgetx/EdgeTxSdSeedInstaller` |
| PLN mode | Marked TBA in UI |

## Build

JDK 17, Gradle 8.11.1, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1 (same pins as `gnd-android`):

```sh
cd /home/ubuntu/development/gnd-android-full
gradle testDebugUnitTest assembleDebug
cmake -S app/src/main/cpp -B native-build -DROVER_NATIVE_TESTS=ON
cmake --build native-build -j 4
ctest --test-dir native-build --output-on-failure
```

## Planning source of truth

Detailed architecture, EdgeTX integration, safety, and acceptance criteria live in:

- [Project overview](../aidocs/gnd-android-full/README.md)
- [Architecture](../aidocs/gnd-android-full/architecture.md)
- [EdgeTX adapters](../aidocs/gnd-android-full/edgetx.md)
- [Interface layout](../aidocs/gnd-android-full/interface.md)
- [Video and control](../aidocs/gnd-android-full/video-control.md)
- [Milestones](../aidocs/gnd-android-full/milestones.md)

## Agent handoff

See [AGENTS.md](AGENTS.md) and [docs/HANDOFF.md](docs/HANDOFF.md).
