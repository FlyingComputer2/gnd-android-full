#!/usr/bin/env bash
# Build TX16S EdgeTX wasm (requires wasi-sdk; see EdgeTX tools/build-wasm-modules.sh).
set -euo pipefail
EDGETX_SRC="${EDGETX_SRC:-/home/ubuntu/development/_deps/edgetx-src}"
OUT_DIR="${OUT_DIR:-$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/edgetx}"
mkdir -p "$OUT_DIR"
cd "$EDGETX_SRC"
export FLAVOR="${FLAVOR:-tx16s}"
tools/build-wasm-modules.sh "$EDGETX_SRC" "${EDGETX_SRC}/output"
cp -v "${EDGETX_SRC}/output/edgetx-${FLAVOR}-simulator.wasm" \
  "${OUT_DIR}/edgetx-tx16s-simulator.wasm"
echo "Installed wasm to ${OUT_DIR}/edgetx-tx16s-simulator.wasm"
