#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
GRADLE_HOME="${GRADLE_HOME:-$HOME/gradle/gradle-8.11.1}"
export ANDROID_HOME="$SDK"
export PATH="$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

mkdir -p "$SDK/cmdline-tools"
if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  TMP="$(mktemp -d)"
  curl -fsSL -o "$TMP/cmdline-tools.zip" \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$TMP/cmdline-tools.zip" -d "$TMP"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TMP"
fi

if [[ ! -d "$SDK/platforms/android-35" ]]; then
  yes | sdkmanager --sdk_root="$SDK" --licenses >/dev/null
  sdkmanager --sdk_root="$SDK" \
    'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' \
    'ndk;27.2.12479018' 'cmake;3.22.1'
fi

if [[ ! -x "$GRADLE_HOME/bin/gradle" ]]; then
  mkdir -p "$(dirname "$GRADLE_HOME")"
  TMP="$(mktemp -d)"
  curl -fsSL -o "$TMP/gradle.zip" \
    https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
  unzip -q "$TMP/gradle.zip" -d "$TMP"
  rm -rf "$GRADLE_HOME"
  mv "$TMP/gradle-8.11.1" "$GRADLE_HOME"
  rm -rf "$TMP"
fi

printf 'sdk.dir=%s\n' "$SDK" >"$ROOT/local.properties"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"

cd "$ROOT"
if [[ "$(uname -m)" != "x86_64" ]]; then
  echo "This worker is $(uname -m): Android SDK host tools (aapt2/NDK clang) need x86_64." >&2
  echo "Use GitHub Actions: gh workflow run 'Android and protocol checks' --ref $(git branch --show-current)" >&2
  exit 2
fi
gradle testDebugUnitTest assembleDebug --stacktrace 2>&1 | tee "$ROOT/build-apk.log"
echo "APK(s):"
ls -la "$ROOT/app/build/outputs/apk/debug/"*.apk
