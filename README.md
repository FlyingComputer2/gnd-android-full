# gnd-android-full

Android ground station with on-device EdgeTX UI, virtual gimbals/switches, and USB rover video from the ground P4. This repository implements **milestone 0 / touch-display prototype scaffolding** described in the planning docs under [`../aidocs/gnd-android-full/`](../aidocs/gnd-android-full/).

## What works in this baseline

| Area | Status |
| --- | --- |
| Portrait RC layout (video, OSD placeholder, EdgeTX nav row, LCD, switches, gimbals) | Implemented |
| Fake EdgeTX adapter + JNI stub (`NativeEdgeTxAdapter`) | Implemented |
| Pinned EdgeTX simulator cross-build | Not started — adapter API is ready |
| USB RGP4 v2 video channel 0 (`rtp-h264-v1`) | Ported from [gnd-android](https://github.com/FlyingComputer2/gnd-android) |
| RC/telemetry USB channel 2 | Not wired |
| CLI / PLN modes | Marked TBA in UI |

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
