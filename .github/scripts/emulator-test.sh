#!/usr/bin/env bash
set -euo pipefail
# Keep trap and Gradle in one shell: emulator-runner splits inline script lines.
cleanup_crashpad() {
  pkill -TERM -x crashpad_handle || true
  sleep 2
  pkill -KILL -x crashpad_handle || true
}
trap cleanup_crashpad EXIT
# Dismiss the OS first-use immersive tutorial, which otherwise consumes the touch-through tap.
adb shell settings put secure immersive_mode_confirmations confirmed
if ! gradle connectedDebugAndroidTest --no-daemon; then
  adb logcat -d -s OverlayBarsTest:I TestRunner:I AndroidRuntime:E '*:S' || true
  exit 1
fi
