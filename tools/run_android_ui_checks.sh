#!/usr/bin/env bash
# Preserve test failure while collecting the screenshots needed to diagnose it.
set -uo pipefail
gradle connectedDebugAndroidTest --no-daemon
test_status=$?
mkdir -p app/build/ui-qa
adb pull /sdcard/Android/data/com.uplb.punla/files/ui-qa/. app/build/ui-qa/
screenshot_status=$?
if [ "$test_status" -ne 0 ]; then
  exit "$test_status"
fi
exit "$screenshot_status"
