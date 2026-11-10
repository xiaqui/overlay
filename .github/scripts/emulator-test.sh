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
test_args=()
if [ "${OVERLAY_TEST_API:-}" = 24 ]; then
  test_args+=('-Pandroid.testInstrumentationRunnerArguments.class=com.xiaqui.overlay.BatteryFinishTest,com.xiaqui.overlay.SettingsUiTest')
fi
if ! gradle connectedDebugAndroidTest --no-daemon "${test_args[@]}"; then
  timeout --signal=TERM --kill-after=5s 15s adb logcat -d -s OverlayBarsTest:I NavbarPrototypeTest:I OverlayPerf:I TestRunner:I AndroidRuntime:E '*:S' || true
  exit 1
fi
timeout --signal=TERM --kill-after=5s 15s adb logcat -d -s NavbarPrototypeTest:I OverlayPerf:I '*:S' || true


