#!/usr/bin/env bash
# Runs the instrumented tests on the emulator the runner has booted.
#
# Gradle reads a device's API level once, when it connects; a device still
# settling answers nothing and is skipped as "Unknown API Level", and the
# run ends with "No compatible devices connected". Some images get there
# after the boot is reported complete: the XR headset's system server has
# died and restarted right after boot, the TV restarts adbd. So this script
# waits for the device to answer before Gradle starts, and when Gradle
# still found no usable device, settles again and gives it one more try.
# A test failure is never retried; it dumps the guest's diagnostics.
#
#   LOGCAT_FILE   where the guest's logcat goes (default: emulator_logcat.txt)
set -u
LOGCAT_FILE="${LOGCAT_FILE:-emulator_logcat.txt}"

settle() {
    adb wait-for-device
    local level=""
    for _ in $(seq 1 60); do
        level="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
        if [ -n "$level" ] && [ "$(adb get-state 2>/dev/null | tr -d '\r')" = "device" ]; then break; fi
        sleep 2
    done
    echo "device API level: ${level:-unknown}"
    sleep 5
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    adb shell input keyevent 82
}

run_tests() {
    ./gradlew :app:connectedDebugAndroidTest --no-daemon 2>&1 | tee "$1"
    grep -q "BUILD SUCCESSFUL" "$1"
}

diagnostics() {
    echo "=== test run failed; capturing guest diagnostics ==="
    adb root >/dev/null 2>&1 || true
    adb wait-for-device || true
    echo "----- crash logcat buffer -----"
    adb logcat -d -b crash 2>/dev/null | tail -n 300 || true
    echo "----- /data/tombstones -----"
    for f in $(adb shell ls /data/tombstones 2>/dev/null | tr -d '\r'); do
        case "$f" in
            *.pb) ;;
            *) echo "===== $f ====="; adb shell cat "/data/tombstones/$f" 2>/dev/null || true ;;
        esac
    done
}

settle
adb logcat -v time > "$LOGCAT_FILE" &

if run_tests gradle-run-1.log; then exit 0; fi
if grep -q "No compatible devices connected\|Unknown API Level" gradle-run-1.log; then
    echo "=== Gradle found no usable device: the guest restarted after boot; settling again for a second run ==="
    settle
    if run_tests gradle-run-2.log; then exit 0; fi
fi
diagnostics
exit 1
