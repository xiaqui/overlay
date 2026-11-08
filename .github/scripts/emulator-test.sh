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
# API24 has repeatedly completed every instrumentation test but then held the Gradle
# connected-test process until the GitHub job timeout. Bound only this known
# emulator/Gradle teardown problem, and accept a timeout solely when the
# *complete* instrumentation summary explicitly reports zero skips/failures.
# A Gradle failure, partial test run, or missing summary remains a CI failure.
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$api" == "24" ]]; then
  log="$(mktemp)"
  trap 'cleanup_crashpad; rm -f "$log"' EXIT
  set +e
  timeout --signal=TERM --kill-after=15s 240s gradle connectedDebugAndroidTest --no-daemon --console=plain 2>&1 | tee "$log"
  result=${PIPESTATUS[0]}
  set -e
  if [[ "$result" == "124" ]]; then
    started="$(sed -nE 's/.*Starting ([0-9]+) tests on .*/\1/p' "$log" | tail -n 1)"
    finished="$(sed -nE 's/.*Tests ([0-9]+)\/([0-9]+) completed\. \(0 skipped\) \(0 failed\).*/\1 \2/p' "$log" | tail -n 1)"
    if [[ -z "$started" || "$started" == "0" || "$finished" != "$started $started" ]] ||
       grep -Eq 'FAILURE: Build failed|BUILD FAILED|There were failing tests' "$log"; then
      echo "::error::API24 Gradle timed out without unambiguous full instrumentation success"
      adb logcat -d -s OverlayBarsTest:I NavbarPrototypeTest:I OverlayPerf:I TestRunner:I AndroidRuntime:E '*:S' || true
      exit 1
    fi
    echo "::warning::API24: $started/$started instrumentation tests passed (0 skipped, 0 failed), but Gradle test teardown exceeded 240s; bounded fallback used"
  elif [[ "$result" != "0" ]]; then
    echo "::error::API24 connected tests exited with status $result"
    adb logcat -d -s OverlayBarsTest:I NavbarPrototypeTest:I OverlayPerf:I TestRunner:I AndroidRuntime:E '*:S' || true
    exit "$result"
  fi
else
  if ! gradle connectedDebugAndroidTest --no-daemon; then
    adb logcat -d -s OverlayBarsTest:I NavbarPrototypeTest:I OverlayPerf:I TestRunner:I AndroidRuntime:E '*:S' || true
    exit 1
  fi
fi
adb logcat -d -s NavbarPrototypeTest:I OverlayPerf:I '*:S' || true

