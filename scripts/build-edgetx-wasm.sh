#!/usr/bin/env bash
# Build TX16S EdgeTX wasm (requires wasi-sdk; see EdgeTX tools/build-wasm-modules.sh).
set -euo pipefail
EDGETX_SRC="${EDGETX_SRC:-/home/ubuntu/development/_deps/edgetx-src}"
OUT_DIR="${OUT_DIR:-$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/edgetx}"
VENV="${EDGETX_VENV:-/home/ubuntu/development/_deps/edgetx-venv}"
if [[ ! -x "${VENV}/bin/python" ]]; then
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q pydantic jinja2 asciitree pillow lz4 pyelftools typing_extensions
fi
export PATH="${VENV}/bin:${PATH}"
# Low parallelism: ~7.5 GiB RAM, no swap on this worker.
export MAX_JOBS="${MAX_JOBS:-1}"
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-1}"
mkdir -p "$OUT_DIR"
cd "$EDGETX_SRC"
# gnd patches on the EdgeTX simulator (external module S.Port bridge, ...).
PATCH_DIR="$(cd "$(dirname "$0")" && pwd)/edgetx-patches"
for p in "$PATCH_DIR"/*.patch; do
  [[ -e "$p" ]] || continue
  if git apply --reverse --check "$p" 2>/dev/null; then
    echo "already applied: $(basename "$p")"
  else
    git apply "$p"
    echo "applied: $(basename "$p")"
  fi
done
export FLAVOR="${FLAVOR:-tx16s}"
tools/build-wasm-modules.sh "$EDGETX_SRC" "${EDGETX_SRC}/output"
cp -v "${EDGETX_SRC}/output/edgetx-${FLAVOR}-simulator.wasm" \
  "${OUT_DIR}/edgetx-tx16s-simulator.wasm"
echo "Installed wasm to ${OUT_DIR}/edgetx-tx16s-simulator.wasm"
