#!/usr/bin/env bash
# Android emulator smoke test for adding an optional signed split to a running base
# package. Uses PackageInstaller's MODE_INHERIT_EXISTING through pm install-create -p.
# An x86_64 emulator cannot execute our arm64 GeckoView runtime, so only stage the
# architecture-independent feature master to test the on-device installer/signature
# path. This does NOT establish GeckoView/uBO playback or ARM64 ABI compatibility.
set -euo pipefail
PKG=io.github.sirionero.mycar.dev
ARCHIVE=build/myCar-gecko-arm64-v8a.zip
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT

test -f "$ARCHIVE"
unzip -p "$ARCHIVE" cinema_gecko-master.apk > "$DIR/cinema_gecko-master.apk"
test -s "$DIR/cinema_gecko-master.apk"
SIZE=$(wc -c < "$DIR/cinema_gecko-master.apk" | tr -d ' ')
adb push "$DIR/cinema_gecko-master.apk" /data/local/tmp/cinema_gecko-master.apk >/dev/null
session=""
cleanup() {
  if [[ -n "$session" ]]; then
    adb shell pm install-abandon "$session" >/dev/null 2>&1 || true
  fi
  adb shell rm -f /data/local/tmp/cinema_gecko-master.apk >/dev/null 2>&1 || true
}
trap 'cleanup; rm -rf "$DIR"' EXIT

created=$(adb shell pm install-create -r -p "$PKG" | tr -d '\r')
echo "$created"
session=$(sed -n 's/.*\[\([0-9][0-9]*\)\].*/\1/p' <<< "$created")
test -n "$session" || { echo "Could not create inherit-existing install session"; exit 1; }
adb shell pm install-write -S "$SIZE" "$session" cinema_gecko-master.apk /data/local/tmp/cinema_gecko-master.apk
committed=$(adb shell pm install-commit "$session" | tr -d '\r')
session=""
echo "$committed"
[[ "$committed" == Success* ]] || { echo "Optional feature install commit failed"; exit 1; }
adb shell pm path "$PKG" | grep -qi 'cinema_gecko' || {
  echo "Optional GeckoView feature master is absent after install."
  adb shell pm path "$PKG"
  exit 1
}
echo "PASS: Android accepted same-signature dynamic feature master with MODE_INHERIT_EXISTING"
