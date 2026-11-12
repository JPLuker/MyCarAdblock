#!/usr/bin/env bash
# CI smoke test for the actual installed base-only Android APK, not a Compose/JVM test.
# Mirrors the startup crash reproduced on a physical phone before b5038d3.
set -euo pipefail
PKG=io.github.sirionero.mycar.dev
ACT="$PKG/io.github.sirionero.mycar.MyCarActivity"
APK="build/myCar-debug.apk"

test -f "$APK"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$ACT"
sleep 8

PID=$(adb shell pidof "$PKG" | tr -d '\r' || true)
if [[ -z "$PID" ]]; then
  echo "FAIL: $PKG is no longer running after launch."
  adb logcat -d -b crash | tail -120
  adb logcat -d -v brief -s AndroidRuntime:E ActivityManager:E | tail -120
  exit 1
fi
echo "PASS: base-only myCar Debug launched, still running (pid $PID)."
if adb logcat -d -b crash | grep -Eq "FATAL EXCEPTION|Fatal signal|ClassNotFoundException: org.mozilla"; then
  echo "FAIL: Android recorded a crash during startup."
  adb logcat -d -b crash | tail -120
  exit 1
fi

# Smoke-test the real launch state with a second activity start, not merely package install.
adb shell am start -W -n "$ACT"
sleep 3
if [[ -z "$(adb shell pidof "$PKG" | tr -d '\r' || true)" ]]; then
  echo "FAIL: app died during second launch."
  adb logcat -d -b crash | tail -120
  exit 1
fi
echo "PASS: base-only APK survived startup and activity reopen."
