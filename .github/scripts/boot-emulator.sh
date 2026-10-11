#!/usr/bin/env bash
# Boots a stable emulator for a matrix leg that cannot go through the
# emulator-runner action. The action sends `input keyevent 82` the instant
# the guest reports its boot complete, and the TV image restarts adbd right
# then: the command fails and takes the leg with it before a test has run.
# This script installs the image, creates the AVD, starts the emulator in
# the background with the same options the action gets, and returns once
# the guest reports its boot complete. run-instrumented.sh then settles the
# device at its own pace before Gradle looks at it.
#
#   API_LEVEL, TARGET, PROFILE   the system image and device profile
#   TAG                          the image's tag id when it differs from TARGET
#   GPU, MEMORY, EXTRA_OPTIONS   emulator command line pieces
#   RAM_SIZE, CORES, DISK_SIZE   AVD config (RAM_SIZE in MB, a trailing M is fine)
#   EMULATOR_PORT                default 5554
#   BOOT_TIMEOUT                 seconds to wait for the boot, default 900
#   SKIP_INSTALL=1               the SDK already has the image, emulator and platform-tools
#   EMULATOR_LOG                 where the emulator's own output goes
set -u
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
AVD="${AVD_NAME:-test}"
ABI=x86_64
PORT="${EMULATOR_PORT:-5554}"
PKG="system-images;android-${API_LEVEL};${TARGET};${ABI}"
TAG="${TAG:-$TARGET}"
EMULATOR_LOG="${EMULATOR_LOG:-emulator.log}"
SERIAL="emulator-${PORT}"

if [ "${SKIP_INSTALL:-0}" != 1 ]; then
    # The image is a large download; a dropped manifest or a truncated
    # archive has failed a leg before anything ran. Three tries.
    for attempt in 1 2 3; do
        if yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --install "$PKG" platform-tools emulator > /dev/null; then break; fi
        echo "system image download failed (attempt $attempt)"; sleep 20
        [ "$attempt" = 3 ] && exit 1
    done
fi

echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd --force -n "$AVD" \
    --abi "${TAG}/${ABI}" --device "$PROFILE" --package "$PKG"
CONFIG="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$AVD.avd/config.ini"
[ -f "$CONFIG" ] || { echo "ERROR: AVD not created ($CONFIG missing)"; exit 1; }
{
    echo "hw.ramSize=${RAM_SIZE%M}"
    [ -n "${CORES:-}" ] && echo "hw.cpu.ncore=${CORES}"
    echo "disk.dataPartition.size=${DISK_SIZE:-8192M}"
} >> "$CONFIG"
echo "===== AVD root ini ====="; cat "${ANDROID_AVD_HOME:-$HOME/.android/avd}/$AVD.ini"
echo "===== AVD config.ini ====="; cat "$CONFIG"

# Cold boot, no snapshot, a large userdata partition so the APK can stage,
# and adb held off until boot completes (-delay-adb): a device that goes
# offline mid-boot used to fail a leg.
# shellcheck disable=SC2086
nohup "$SDK/emulator/emulator" -port "$PORT" -avd "$AVD" \
    -no-snapshot -no-window -gpu "$GPU" -noaudio -no-boot-anim -camera-back none \
    -memory "$MEMORY" -delay-adb -verbose -show-kernel -debug-metrics -metrics-collection \
    ${EXTRA_OPTIONS:-} > "$EMULATOR_LOG" 2>&1 &
echo "emulator pid $! on $SERIAL, output in $EMULATOR_LOG"
[ -n "${GITHUB_ENV:-}" ] && echo "ANDROID_SERIAL=$SERIAL" >> "$GITHUB_ENV"
export ANDROID_SERIAL="$SERIAL"

deadline=$(( $(date +%s) + ${BOOT_TIMEOUT:-900} ))
until [ "$(timeout 10 adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
        echo "ERROR: the emulator did not finish booting within ${BOOT_TIMEOUT:-900} s"
        tail -n 200 "$EMULATOR_LOG"
        exit 1
    fi
    kill -0 "$!" 2>/dev/null || { echo "ERROR: the emulator exited"; tail -n 200 "$EMULATOR_LOG"; exit 1; }
    sleep 5
done
echo "boot complete on $SERIAL after $(( ${BOOT_TIMEOUT:-900} - deadline + $(date +%s) )) s"
