#!/usr/bin/env bash
# Destructive only to test packages on a positively identified disposable emulator.
# Never select a physical phone/TV or use an unqualified adb command.
set -euo pipefail
serial="${ANDROID_SERIAL:-emulator-5554}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Relink test requires an emulator serial' >&2; exit 2; }
[[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'Device is not a verified emulator' >&2; exit 2;
}
sdk="${ANDROID_HOME:?Android SDK required}"
tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
work="$(mktemp -d "${RUNNER_TEMP:-/tmp}/uacast-relink.XXXXXX")"
reports=app/build/reports/relink
mkdir -p "$reports"
# Contains only a disposable self-generated signing key and intermediate unsigned test APKs.
trap 'rm -rf -- "$work"' EXIT
keytool -genkeypair -keystore "$work/recipient.p12" -storetype PKCS12 -alias recipient \
  -storepass relink-test-only -keypass relink-test-only -dname 'CN=UA-Cast recipient relink test' \
  -keyalg RSA -keysize 2048 -validity 2 > "$reports/key-generation.log" 2>&1
sign_apk() {
  "$tools/zipalign" -P 16 -f 4 "$1" "$work/aligned.apk"
  "$tools/apksigner" sign --ks "$work/recipient.p12" --ks-key-alias recipient \
    --ks-pass pass:relink-test-only --key-pass pass:relink-test-only --out "$2" "$work/aligned.apk"
  "$tools/apksigner" verify --verbose "$2" >> "$reports/signatures.txt"
  "$tools/zipalign" -c -P 16 4 "$2" >> "$reports/alignment.txt"
}
probe_apk() {
  python3 scripts/relink-software-decoder-apk.py --apk "$1" \
    --library build/software-decoder-relink-probe/libavcodec.so --abi x86_64 --output "$2"
}
app_apk=$(python3 - <<'PY'
import json
from pathlib import Path
root = Path('app/build/outputs/apk/debug')
items = [item for item in json.loads((root / 'output-metadata.json').read_text())['elements'] if not item['filters']]
assert len(items) == 1 and Path(items[0]['outputFile']).name == items[0]['outputFile']
print(root / items[0]['outputFile'])
PY
)
probe_apk "$app_apk" "$work/app-unsigned.apk" > "$reports/app-repack.txt"
sign_apk "$work/app-unsigned.apk" "$work/app.apk"
sign_apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$work/app-test.apk"
# Newly generated recipient signatures intentionally cannot update the publisher-signed packages.
adb -s "$serial" uninstall com.uacastplayer.debug.test > /dev/null 2>&1 || true
adb -s "$serial" uninstall com.uacastplayer.debug > /dev/null 2>&1 || true
adb -s "$serial" install "$work/app.apk"
adb -s "$serial" install "$work/app-test.apk"
timeout --kill-after=5s 180s adb -s "$serial" shell am instrument -w -r \
  -e uacast.relinkProbe true -e class com.uacastplayer.player.SoftwareDecoderWiringInstrumentedTest \
  com.uacastplayer.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$reports/app-runner.txt"
grep -Eq 'OK \(1 test\)' "$reports/app-runner.txt"
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$reports/app-runner.txt"
probe_apk ffmpegdecoder/build/outputs/apk/androidTest/debug/ffmpegdecoder-debug-androidTest.apk "$work/decoder-unsigned.apk" \
  > "$reports/decoder-repack.txt"
sign_apk "$work/decoder-unsigned.apk" "$work/decoder.apk"
adb -s "$serial" uninstall androidx.media3.decoder.ffmpeg.test > /dev/null 2>&1 || true
adb -s "$serial" install "$work/decoder.apk"
timeout --kill-after=5s 300s adb -s "$serial" shell am instrument -w -r -e uacast.relinkProbe true \
  androidx.media3.decoder.ffmpeg.test/androidx.test.runner.AndroidJUnitRunner | tee "$reports/decoder-runner.txt"
grep -Eq 'OK \([1-9][0-9]* tests?\)' "$reports/decoder-runner.txt"
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$reports/decoder-runner.txt"
echo 'Recipient-signed modified library passed app wiring and real software decoding tests'
