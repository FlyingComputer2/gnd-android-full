#!/usr/bin/env bash
# Bundle EdgeTX SD layout (480x272) + English sound pack for first-run seeding.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$ROOT/app/src/main/assets/edgetx/edgetx-sd-seed.zip}"
VERSION="${SD_VERSION:-2.12.3}"
LAYOUT_URL="https://github.com/EdgeTX/edgetx-sdcard/releases/download/v${VERSION}/c480x272.zip"
SOUNDS_URL="https://github.com/EdgeTX/edgetx-sdcard-sounds/releases/download/v${VERSION}/edgetx-sdcard-sounds-en-${VERSION}.zip"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$(dirname "$OUT")"
curl -fsSL -o "$TMP/layout.zip" "$LAYOUT_URL"
curl -fsSL -o "$TMP/sounds.zip" "$SOUNDS_URL"
mkdir -p "$TMP/merge"
unzip -q "$TMP/layout.zip" -d "$TMP/merge"
unzip -q "$TMP/sounds.zip" -d "$TMP/merge"
( cd "$TMP/merge" && zip -qr "$OUT" . )
echo "Wrote $OUT ($(du -h "$OUT" | awk '{print $1}'))"
