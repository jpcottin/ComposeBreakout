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
#   AVD_BY_HAND=1                write the AVD's ini files without asking avdmanager
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
# A plain step has no adb on its PATH (the emulator-runner action adds the
# platform-tools itself): the boot poll below and run-instrumented.sh after
# it need it, in this step and the next ones.
export PATH="$SDK/platform-tools:$SDK/emulator:$PATH"
[ -n "${GITHUB_PATH:-}" ] && printf '%s\n%s\n' "$SDK/platform-tools" "$SDK/emulator" >> "$GITHUB_PATH"

if [ "${SKIP_INSTALL:-0}" != 1 ]; then
    # The image is a large download; a dropped manifest or a truncated
    # archive has failed a leg before anything ran. Three tries.
    for attempt in 1 2 3; do
        if yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --install "$PKG" platform-tools emulator > /dev/null; then break; fi
        echo "system image download failed (attempt $attempt)"; sleep 20
        [ "$attempt" = 3 ] && exit 1
    done
fi

IMGDIR="$SDK/system-images/android-${API_LEVEL}/${TARGET}/${ABI}"
[ -f "$IMGDIR/system.img" ] || { echo "ERROR: no system image at $IMGDIR"; ls -la "$IMGDIR" 2>&1; exit 1; }
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
CONFIG="$AVD_HOME/$AVD.avd/config.ini"
rm -rf "$AVD_HOME/$AVD.avd" "$AVD_HOME/$AVD.ini"
if [ "${AVD_BY_HAND:-0}" != 1 ]; then
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd --force -n "$AVD" \
        --abi "${TAG}/${ABI}" --device "$PROFILE" --package "$PKG" 2>&1 || echo "avdmanager exit code: $?"
fi
if [ ! -f "$CONFIG" ]; then
    # avdmanager has produced nothing at all on a runner, without a word:
    # the AVD is then written by hand, the way the preview-emulator action
    # does it. A TV profile gets a 1080p landscape panel, anything else a
    # phone's portrait one.
    echo "avdmanager produced no AVD; writing the ini files by hand"
    case "$PROFILE" in
        tv_*) W=1920; H=1080; DENSITY=320 ;;
        *) W=1080; H=2400; DENSITY=420 ;;
    esac
    mkdir -p "$AVD_HOME/$AVD.avd"
    printf 'avd.ini.encoding=UTF-8\npath=%s/%s.avd\npath.rel=avd/%s.avd\ntarget=android-%s\n' \
        "$AVD_HOME" "$AVD" "$AVD" "$API_LEVEL" > "$AVD_HOME/$AVD.ini"
    cat > "$CONFIG" << CFG
AvdId=$AVD
avd.ini.displayname=$AVD
avd.ini.encoding=UTF-8
abi.type=$ABI
hw.cpu.arch=$ABI
image.sysdir.1=system-images/android-${API_LEVEL}/${TARGET}/${ABI}/
tag.id=$TAG
tag.display=$TAG
hw.device.name=$PROFILE
PlayStore.enabled=no
hw.lcd.density=$DENSITY
hw.lcd.width=$W
hw.lcd.height=$H
hw.keyboard=yes
hw.dPad=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
CFG
fi
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
# The emulator pre-authorises the host's adb key by reading
# ~/.android/adbkey.pub when the guest's adbd comes up. On a fresh runner
# no key exists until adb first runs, and this script's first adb call is
# the boot poll, after the launch: whether the key was there in time was
# a race, lost as "emulator-5554 unauthorized" and a 15-minute timeout.
# So the key is made before the launch.
if [ ! -f "$HOME/.android/adbkey.pub" ]; then
    mkdir -p "$HOME/.android"
    adb keygen "$HOME/.android/adbkey" >/dev/null 2>&1 || true
fi
adb start-server >/dev/null 2>&1 || true
ls -la "$HOME/.android/adbkey.pub" 2>&1
"$SDK/emulator/emulator" -version 2>/dev/null | head -1
"$SDK/emulator/emulator" -accel-check 2>&1 | head -5
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
        echo "----- emulator output, head -----"; head -n 150 "$EMULATOR_LOG"
        echo "----- emulator output, tail -----"; tail -n 150 "$EMULATOR_LOG"
        echo "----- adb -----"; adb devices -l 2>&1; timeout 10 adb -s "$SERIAL" shell getprop 2>&1 | grep -E 'sys.boot|ro.build.version.sdk|init.svc.(bootanim|zygote|adbd)|dev.bootcomplete' | head
        exit 1
    fi
    kill -0 "$!" 2>/dev/null || { echo "ERROR: the emulator exited"; tail -n 200 "$EMULATOR_LOG"; exit 1; }
    sleep 5
done
echo "boot complete on $SERIAL after $(( ${BOOT_TIMEOUT:-900} - deadline + $(date +%s) )) s"
