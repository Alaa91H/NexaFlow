#!/usr/bin/env bash
# NexaFlow — ROM integration builder for Evolution X (cnb / Android 17, API 37) — Linux/macOS
# Usage:  bash rom/install-rom-integration.sh [--push-adb] [--magisk]
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"
echo "== NexaFlow Evolution X (cnb / API 37) — ROM build =="
echo "[1/3] Building release APK..."
./gradlew assembleRelease --warning-mode all
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
echo "APK: $APK ($(du -h "$APK" | cut -f1))"
echo "[2/3] Copying to rom/prebuilt/NexaFlow.apk..."
mkdir -p rom/prebuilt
cp -f "$APK" rom/prebuilt/NexaFlow.apk
echo "Done"
echo "[Check] 16 KB alignment..."
python3 scripts/check_16kb.py "$APK" || echo "WARN: 16KB check failed"
if [[ "${1:-}" == "--push-adb" || "${1:-}" == "--magisk" ]]; then
  DEV=$(adb devices | awk '/device$/{print $1; exit}')
  [[ -n "$DEV" ]] || { echo "No adb device"; exit 1; }
  echo "Device: $DEV"
  if [[ "${1:-}" == "--magisk" || "${2:-}" == "--magisk" ]]; then
    adb -s "$DEV" push rom/prebuilt/NexaFlow.apk /sdcard/NexaFlow-rom.apk
    echo "Pushed to /sdcard/NexaFlow-rom.apk — use in-app ROM Integration or magisk --install-module"
  fi
  if [[ "${1:-}" == "--push-adb" || "${2:-}" == "--push-adb" ]]; then
    adb -s "$DEV" install -r "$APK"
  fi
fi
echo "== Done. Copy rom/ to vendor/nexaflow/prebuilt/ and inherit NexaFlow.mk in your device tree =="
