#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p app/build/reports
  adb pull /sdcard/Android/data/pl.colsztok/files/screenshots app/build/reports/screenshots || true
  adb logcat -d > app/build/reports/emulator-log.txt || true
}
trap collect EXIT
gradle connectedDebugAndroidTest --no-daemon
