#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Runs docs/PROJECT_MODEL.md §13's P11 device scenario against a real, connected device or
# emulator: builds and installs the app and its instrumentation APK, runs
# ProjectSaveDeviceTest, force-stops the app (a real process death, not just a backgrounding),
# then runs ProjectRestoreDeviceTest in the fresh process that follows. Owner-run only: this
# container has no phone and no emulator (CLAUDE.md law 4), so this script itself is untested
# here -- read it carefully before the first real run.
#
# Usage: tools/device/p11-save-kill-restore.sh [device-serial]
# An explicit serial is passed to every adb call as `-s <serial>`; omit it when exactly one
# device is attached.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

SERIAL="${1:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

APPLICATION_ID="com.asoc.typewright"
TEST_APPLICATION_ID="${APPLICATION_ID}.test"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"

echo "==> Building the app and its instrumentation APK"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}" ./gradlew :app-android:assembleDebug :app-android:assembleDebugAndroidTest

APP_APK=$(find app-android/build/outputs/apk/debug -name "*.apk" | head -1)
TEST_APK=$(find app-android/build/outputs/apk/androidTest/debug -name "*.apk" | head -1)
if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
  echo "Could not find the built APKs under app-android/build/outputs/apk/" >&2
  exit 1
fi

echo "==> Installing $APP_APK"
"${ADB[@]}" install -r -g "$APP_APK"
echo "==> Installing $TEST_APK"
"${ADB[@]}" install -r -g "$TEST_APK"

run_instrumented_class() {
  local class="$1"
  echo "==> Running $class"
  local output
  # -w: wait for the instrumentation to finish before returning, printing its own results.
  output=$("${ADB[@]}" shell am instrument -w -e class "com.asoc.typewright.app.android.$class" \
    "$TEST_APPLICATION_ID/$TEST_RUNNER" 2>&1)
  echo "$output"
  if echo "$output" | grep -q "FAILURES!!!"; then
    echo "$class failed" >&2
    exit 1
  fi
  if ! echo "$output" | grep -qE "OK \([0-9]+ tests?\)"; then
    echo "$class did not report a clean OK result; treating this as a failure" >&2
    exit 1
  fi
}

run_instrumented_class "ProjectSaveDeviceTest"

echo "==> Force-stopping $APPLICATION_ID (a real process death, not a backgrounding)"
"${ADB[@]}" shell am force-stop "$APPLICATION_ID"

run_instrumented_class "ProjectRestoreDeviceTest"

echo "==> P11 save/kill/restore device scenario passed"
