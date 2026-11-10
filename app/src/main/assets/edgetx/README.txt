Place edgetx-tx16s-simulator.wasm here (scripts/build-edgetx-wasm.sh).

SD + English sounds: scripts/fetch-edgetx-sd-assets.sh writes edgetx-sd-seed.zip
(c480x272 layout + SOUNDS/en from EdgeTX v2.12.3 releases). CI runs this before
assemble. On first launch, WasmEdgeTxAdapter extracts into files/edgetx/sd/.

Without wasm, the app falls back to FakeEdgeTxAdapter for UI dev only.
