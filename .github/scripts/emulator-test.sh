#!/usr/bin/env bash
set -euo pipefail
# Keep trap and Gradle in one shell: emulator-runner splits inline script lines.
cleanup_crashpad() {
  pkill -TERM -x crashpad_handle || true
  sleep 2
  pkill -KILL -x crashpad_handle || true
}
trap cleanup_crashpad EXIT
gradle connectedDebugAndroidTest --no-daemon
