#!/usr/bin/env bash
# Runs the instrumented tests on the emulator the runner has booted.
#
# Gradle reads a device's API level once, when it connects; a device still
# settling answers nothing and is skipped as "Unknown API Level", and the
# run ends with "No compatible devices connected". Some images get there
# after the boot is reported complete: the XR headset's system server dies
# and restarts right after boot, the TV restarts adbd. So this script builds
# everything first (Gradle then looks at the device seconds after the settle,
# not minutes of compilation later), waits for the device to answer before
# Gradle starts, and when Gradle still found no usable device, settles again
# and gives it another try, twice. A test failure is never retried; it dumps
# the guest's diagnostics.
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
    # A guest whose system server restarted reports the boot complete a
    # second time, later; and sys.boot_completed keeps its 1 from the first
    # boot while the server is down, so the property alone proves nothing.
    # Gradle asks the device for its properties with a short timeout of its
    # own, so wait for the boot to be complete, for the system server to be
    # up and serving (the package service answers) and for its pid to hold
    # still, three quick answers in a row, before letting Gradle look.
    local ok=0 pid="" last=""
    for _ in $(seq 1 90); do
        pid="$(timeout 5 adb shell pidof system_server 2>/dev/null | tr -d '\r')"
        if [ "$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
           timeout 3 adb shell getprop ro.build.version.sdk >/dev/null 2>&1 &&
           [ -n "$pid" ] && [ "$pid" = "$last" ] &&
           timeout 5 adb shell service check package 2>/dev/null | grep -q "found"; then
            ok=$((ok + 1))
            [ "$ok" -ge 3 ] && break
        else
            ok=0
        fi
        last="$pid"
        sleep 3
    done
    echo "boot complete, system server ${pid:-absent} answering: $([ "$ok" -ge 3 ] && echo yes || echo 'not within the wait')"
    sleep 5
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    # A crash or ANR dialog from some other package would take the window
    # focus, and a key sent to the app would go to the dialog instead.
    adb shell settings put global hide_error_dialogs 1
    adb shell input keyevent 82
}

# Everything the test run compiles, built before the device is looked at.
build_tests() {
    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon 2>&1 | tee gradle-build.log
    grep -q "BUILD SUCCESSFUL" gradle-build.log
}

run_tests() {
    ./gradlew :app:connectedDebugAndroidTest --no-daemon 2>&1 | tee "$1"
    grep -q "BUILD SUCCESSFUL" "$1"
}

no_usable_device() {
    grep -q "No compatible devices connected\|Unknown API Level" "$1"
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

adb wait-for-device
adb logcat -v time > "$LOGCAT_FILE" &

build_tests || exit 1
settle

for attempt in 1 2 3; do
    if run_tests "gradle-run-$attempt.log"; then exit 0; fi
    if [ "$attempt" -ge 3 ] || ! no_usable_device "gradle-run-$attempt.log"; then break; fi
    echo "=== Gradle found no usable device: the guest restarted after boot; settling again for run $((attempt + 1)) ==="
    settle
done
diagnostics
exit 1
