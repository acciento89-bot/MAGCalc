#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Cloud disposable runner required'; exit 2; }
cd "$GITHUB_WORKSPACE"
proof="$GITHUB_WORKSPACE/.github/native-acceptance/proof"
mkdir -p "$proof"
serial=emulator-5554
avd=MAGNativeAcceptanceApi35
cleanup() {
  rc=$?; trap - EXIT; set +e
  timeout 10s adb -s "$serial" emu kill > "$proof/emulator-stop.txt" 2>&1
  for attempt in {1..15}; do
    if ! timeout 3s adb devices | grep -Fq "$serial"; then break; fi
    sleep 1
  done
  timeout 15s "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" delete avd -n "$avd" > "$proof/avd-delete.txt" 2>&1
  timeout 10s emulator -list-avds > "$proof/remaining-avds.txt" 2>&1
  avd_rc=$?
  timeout 5s adb devices > "$proof/remaining-devices.txt" 2>&1
  device_rc=$?
  python3 .github/native-acceptance/verify_cleanup.py --proof "$proof" --avd "$avd" --serial "$serial" --avd-rc "$avd_rc" --device-rc "$device_rc"
  if [[ $? != 0 ]]; then echo 'Owned cloud device cleanup not verified' > "$proof/cleanup-failed.txt"; rc=1; fi
  exit "$rc"
}
trap cleanup EXIT
# Give the stock image time to finish boot UI; never dismiss error/ANR dialogs.
sleep 20
python3 .github/native-acceptance/run_acceptance.py --serial "$serial" \
  --product android/app/build/outputs/apk/debug/app-debug.apk \
  --host .github/native-acceptance/host/app/build/outputs/apk/debug/app-debug.apk \
  --test .github/native-acceptance/host/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --proof "$proof"
