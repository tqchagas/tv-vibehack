#!/usr/bin/env bash
# Build the launcher APK and install it on the TV as the default home screen.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

wallpaper_resource=""
cleanup_wallpaper() {
  if [ -n "$wallpaper_resource" ]; then rm -f "$wallpaper_resource"; fi
}
trap cleanup_wallpaper EXIT
if [ -n "${TV_WALLPAPER:-}" ]; then
  [ -f "$TV_WALLPAPER" ] || { echo "TV_WALLPAPER is not a file: $TV_WALLPAPER" >&2; exit 1; }
  case "${TV_WALLPAPER##*.}" in
    jpg|JPG|jpeg|JPEG) extension=jpg ;;
    png|PNG) extension=png ;;
    webp|WEBP) extension=webp ;;
    *) echo 'TV_WALLPAPER must be a JPG, PNG, or WebP image' >&2; exit 1 ;;
  esac
  for existing in app/src/main/res/drawable-nodpi/installer_wallpaper.*; do
    [ ! -e "$existing" ] || { echo "Remove stale staged image first: $existing" >&2; exit 1; }
  done
  wallpaper_resource="app/src/main/res/drawable-nodpi/installer_wallpaper.$extension"
  cp "$TV_WALLPAPER" "$wallpaper_resource"
fi
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  | "$PY" -I ../tools/tv.py --batch
if [ -n "${TV_WALLPAPER:-}" ]; then
  "$PY" -I ../tools/tv.py 'am start -n com.gathin.tvhome/.MainActivity --ez select_installer_wallpaper true'
fi
echo "Installed. Press Home on the remote to see it."
