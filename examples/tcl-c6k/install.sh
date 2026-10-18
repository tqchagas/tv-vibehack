#!/usr/bin/env bash
# Build and install the configured launcher on the inspected TCL firmware.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
export PATH="$JAVA_HOME/bin:$PATH"
PY=tools/venv/bin/python3.13
[ -x "$PY" ] || PY=tools/venv/bin/python
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > launcher/local.properties
(cd launcher && ./gradlew assembleDebug --console=plain)
"$PY" -I tools/b64push.py launcher/app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/tvhome.apk
result=$("$PY" -I tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk')
printf '%s\n' "$result"
[[ "$result" == *Success* ]] || exit 1
"$PY" -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION'
"$PY" -I tools/tv.py 'am start -n com.gathin.tvhome/.MainActivity'
"$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
result=$("$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
printf '%s\n' "$result"
if [[ "$result" == *com.google.android.apps.tv.launcherx/* ]]; then
    # Recovery otherwise intercepts HOME whenever Google TV is disabled.
    "$PY" -I tools/tv.py 'pm disable-user --user 0 com.google.android.tungsten.setupwraith'
    "$PY" -I tools/tv.py 'pm disable-user --user 0 com.google.android.apps.tv.launcherx'
    # Allow the firmware to settle before verifying the default HOME.
    for attempt in {1..10}; do
        sleep 1
        "$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
        result=$("$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
        [[ "$result" == *com.gathin.tvhome/* ]] && break
    done
    printf '%s\n' "$result"
fi
if [[ "$result" != *com.gathin.tvhome/* ]]; then
    echo 'Default home verification failed; restoring Google TV.' >&2
    bash examples/tcl-c6k/restore.sh
    exit 1
fi
"$PY" -I tools/tv.py 'input keyevent KEYCODE_HOME'
