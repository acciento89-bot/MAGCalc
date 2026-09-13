#!/usr/bin/env bash
set -euo pipefail

: "${PACKAGE_NAME:?PACKAGE_NAME is required}"
: "${WAIT_TEXT:?WAIT_TEXT is required}"
: "${SECOND_ACTION:?SECOND_ACTION is required}"
: "${OUTPUT_DIR:?OUTPUT_DIR is required}"

readonly apk_path="$GITHUB_WORKSPACE/android/app/build/outputs/apk/debug/app-debug.apk"
readonly output_dir="$GITHUB_WORKSPACE/$OUTPUT_DIR"
readonly ui_dump_path="${RUNNER_TEMP:-/tmp}/current-window.xml"

current_focus() {
  adb shell dumpsys window | grep "mCurrentFocus=" || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/current-window.xml >/dev/null
  adb pull /sdcard/current-window.xml "$ui_dump_path" >/dev/null
  cat "$ui_dump_path"
}

assert_no_system_dialog() {
  local ui
  ui="$(dump_ui)"
  if grep -Eqi "System UI (isn't|is not) responding|isn't responding|is not responding|keeps stopping|Close app" <<<"$ui"; then
    echo "System error dialog detected; refusing to capture." >&2
    printf '%s\n' "$ui" >&2
    return 1
  fi
}

wait_for_foreground() {
  local attempt
  local focus
  for attempt in $(seq 1 30); do
    focus="$(current_focus)"
    if [[ "$focus" == *"$PACKAGE_NAME"* ]]; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for $PACKAGE_NAME to become the foreground app." >&2
  current_focus >&2
  return 1
}

wait_for_text() {
  local attempt
  local ui
  for attempt in $(seq 1 30); do
    ui="$(dump_ui)"
    if grep -Fq "$WAIT_TEXT" <<<"$ui"; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for UI text: $WAIT_TEXT" >&2
  printf '%s\n' "$ui" >&2
  return 1
}

launch_app() {
  adb shell am force-stop "$PACKAGE_NAME"
  adb shell am start -W -n "$PACKAGE_NAME/.MainActivity"
  wait_for_foreground
  wait_for_text
  assert_no_system_dialog
}

assert_clean_foreground() {
  local focus
  focus="$(current_focus)"
  if [[ "$focus" != *"$PACKAGE_NAME"* ]]; then
    echo "Expected $PACKAGE_NAME in mCurrentFocus; refusing to capture." >&2
    printf '%s\n' "$focus" >&2
    return 1
  fi
  assert_no_system_dialog
}

tap_by_text() {
  local coordinates
  dump_ui >/dev/null
  coordinates="$(python3 - "$ui_dump_path" "$SECOND_TEXT" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
target = sys.argv[2]
for node in root.iter('node'):
    if target in (node.attrib.get('text', ''), node.attrib.get('content-desc', '')):
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
        if match:
            left, top, right, bottom = map(int, match.groups())
            print((left + right) // 2, (top + bottom) // 2)
            break
else:
    raise SystemExit(f'Could not find tappable UI text: {target}')
PY
)"
  read -r x y <<<"$coordinates"
  adb shell input tap "$x" "$y"
}

mkdir -p "$output_dir"
rm -f "$output_dir"/*.png
adb install -r "$apk_path"
adb shell settings put global hide_error_dialogs 1
adb shell cmd locale set-app-locales "$PACKAGE_NAME" --user 0 de-DE
launch_app
assert_clean_foreground
adb exec-out screencap -p > "$output_dir/01-current-ui.png"

case "$SECOND_ACTION" in
  tap)
    if [[ -n "${SECOND_TEXT:-}" ]]; then
      tap_by_text
    else
      adb shell input tap 540 2150
    fi
    sleep 3
    ;;
  dark)
    adb shell cmd uimode night yes
    sleep 2
    launch_app
    ;;
  swipe)
    adb shell input swipe 540 1900 540 650 500
    sleep 2
    ;;
  *)
    echo "Unsupported SECOND_ACTION: $SECOND_ACTION" >&2
    exit 1
    ;;
esac

wait_for_foreground
assert_clean_foreground
adb exec-out screencap -p > "$output_dir/02-current-ui-detail.png"

python3 - "$output_dir" <<'PY'
import hashlib
import struct
import sys
from pathlib import Path

paths = sorted(Path(sys.argv[1]).glob('*.png'))
assert len(paths) == 2, paths
digests = set()
for path in paths:
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    width, height = struct.unpack('>II', data[16:24])
    assert (width, height) == (1080, 2400), (path, width, height)
    digests.add(hashlib.sha256(data).hexdigest())
assert len(digests) == 2, 'The two screenshots must show distinct real UI states'
PY
