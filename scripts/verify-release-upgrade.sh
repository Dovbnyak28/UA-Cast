#!/usr/bin/env bash
# Verifies that a signed release can be installed over the previous one without losing user data.
#
# Usage:
#   scripts/verify-release-upgrade.sh <old.apk> <new.apk> [expected text ...]
#
# Steps:
#   1. apksigner: both APKs verify and carry the same signing certificate (SHA-256 printed).
#   2. SHA-256 of both APK files is printed for the report.
#   3. The old APK is installed fresh on the single connected device/emulator and launched.
#   4. The script pauses so the data set can be created in the old version (two playlists,
#      favourites, an EPG source, a parental-control PIN with a locked channel, a non-default
#      theme/layout). Pass --no-pause after the APK arguments when the data already exists.
#   5. `adb install -r` the new APK, relaunch, and fail on a FATAL in the crash buffer.
#   6. Every [expected text] (for example a playlist or favourite name) must be on the first screen
#      or in the playlist sheet, read through uiautomator.
#
# Requires: adb and apksigner from the Android SDK (ANDROID_HOME or the default Windows SDK path),
# java on PATH, exactly one device in `adb devices`. It never touches signing keys or passwords.
set -euo pipefail

old_apk="${1:?old APK}"; new_apk="${2:?new APK}"; shift 2
pause=1
if [ "${1:-}" = "--no-pause" ]; then pause=0; shift; fi

sdk="${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}"
adb="$sdk/platform-tools/adb"; [ -x "$adb" ] || adb="$sdk/platform-tools/adb.exe"
bt="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"
export MSYS_NO_PATHCONV=1

cert() { java -jar "$bt/lib/apksigner.jar" verify --print-certs "$1" | grep -m1 'certificate SHA-256 digest' | awk '{print $NF}'; }
pkg() { "$bt/aapt" dump badging "$1" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*versionCode='\([^']*\)'.*versionName='\([^']*\)'.*/\1 \2 \3/p"; }

old_cert=$(cert "$old_apk"); new_cert=$(cert "$new_apk")
echo "old: $(pkg "$old_apk") sha256=$(sha256sum "$old_apk" | cut -c1-64) cert=$old_cert"
echo "new: $(pkg "$new_apk") sha256=$(sha256sum "$new_apk" | cut -c1-64) cert=$new_cert"
[ -n "$old_cert" ] && [ "$old_cert" = "$new_cert" ] || { echo "FAIL: signing certificates differ"; exit 1; }

package=$(pkg "$old_apk" | cut -d' ' -f1)
[ "$("$adb" devices | grep -c 'device$')" = 1 ] || { echo "FAIL: need exactly one device"; exit 1; }
echo "device API $("$adb" shell getprop ro.build.version.sdk | tr -d '\r')"

if [ "$pause" = 1 ]; then
  "$adb" uninstall "$package" >/dev/null 2>&1 || true
  "$adb" install "$old_apk" >/dev/null
  "$adb" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  read -r -p "Create the data set in the old version, then press Enter to upgrade... "
fi

"$adb" logcat -c
"$adb" install -r "$new_apk" >/dev/null
"$adb" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
"$adb" shell dumpsys package "$package" | grep -m2 -E 'versionName|versionCode'
if "$adb" logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
  "$adb" logcat -d -b crash | head -40; echo "FAIL: crash after upgrade"; exit 1
fi

"$adb" shell uiautomator dump /sdcard/upgrade-ui.xml >/dev/null
screen=$("$adb" exec-out cat /sdcard/upgrade-ui.xml)
status=0
for text in "$@"; do
  if grep -qF "$text" <<<"$screen"; then echo "found: $text"; else echo "MISSING: $text"; status=1; fi
done
[ "$status" = 0 ] && echo "OK: upgrade kept the expected data" || echo "FAIL: expected data missing"
exit "$status"
