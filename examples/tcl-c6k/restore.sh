#!/usr/bin/env bash
# Restore the original Google TV home on the inspected TCL C6K (Android 14).
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
PY=tools/venv/bin/python3.13
[ -x "$PY" ] || PY=tools/venv/bin/python
"$PY" -I tools/tv.py 'pm enable --user 0 com.google.android.tungsten.setupwraith'
"$PY" -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'
"$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity'
"$PY" -I tools/tv.py 'am start -a android.intent.action.MAIN -c android.intent.category.HOME'
result=$("$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
printf '%s\n' "$result"
[[ "$result" == *com.google.android.apps.tv.launcherx/* ]] || exit 1
