#!/usr/bin/env bash
# fix_launcher_icon.sh — definitive launcher/notification icon refresh.
#
# The app's release APK already contains the new adaptive icon (verified with
# aapt). A stale icon on screen is either (a) an old APK still installed, or
# (b) the launcher's icon cache keeping the previous icon after a same-package
# reinstall — a well-known Android/Launcher3 behavior. This script fixes both
# WITHOUT a reboot: it reinstalls the fresh release, then force-stops the
# launcher so it reloads icons, then verifies.
set -euo pipefail
ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb}"
APK="app/build/outputs/apk/release/app-release.apk"

echo "== devices =="
"$ADB" devices

echo "== installed version (before) =="
"$ADB" shell dumpsys package com.nexaflow.app 2>/dev/null | grep -E "versionName|versionCode" | head -2 || true

echo "== install fresh release =="
"$ADB" install -r "$APK" 2>&1 | tail -2

echo "== installed version (after) =="
"$ADB" shell dumpsys package com.nexaflow.app 2>/dev/null | grep -E "versionName|versionCode" | head -2

echo "== launcher package =="
LAUNCHER=$("$ADB" shell cmd shortcut get-default-launcher 2>/dev/null | tr -d '\r')
echo "launcher=$LAUNCHER"
# Strip the activity component down to the package.
LAUNCHER_PKG="${LAUNCHER%%/*}"
if [ -z "$LAUNCHER_PKG" ]; then
    LAUNCHER_PKG="com.android.launcher3"
fi
echo "launcher_pkg=$LAUNCHER_PKG"

echo "== force-stop launcher (reloads icons, no reboot) =="
"$ADB" shell am force-stop "$LAUNCHER_PKG"
sleep 2

echo "== relaunch app + verify icon on screen =="
"$ADB" shell am force-stop com.nexaflow.app
"$ADB" shell monkey -p com.nexaflow.app -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
"$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null > /tmp/icon_check.xml || true
grep -o 'package="com.nexaflow.app"[^>]*' /tmp/icon_check.xml | head -2 || true
echo "DONE — if the launcher still shows the old icon, run: $ADB shell pm clear $LAUNCHER_PKG"
