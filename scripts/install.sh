#!/usr/bin/env bash
# Installs TV Prompter on an Android TV over network ADB, then opens it.
#
#   scripts/install.sh <tv-ip> [path/to/Prompter.apk]
#
# Turn on USB debugging on the TV first (Settings → System → Developer options) and accept the
# "Allow USB debugging?" prompt on the TV the first time.
set -euo pipefail
cd "$(dirname "$0")/.."
TV=${1:-}
APK=${2:-Prompter.apk}
[ -n "$TV" ] || { echo "usage: scripts/install.sh <tv-ip> [path/to/Prompter.apk]" >&2; exit 1; }
command -v adb >/dev/null || { echo "adb not found: install the Android platform-tools (brew install android-platform-tools)" >&2; exit 1; }
[ -f "$APK" ] || { echo "$APK not found: run scripts/build-apk.sh first, or download it from the latest release" >&2; exit 1; }
case "$TV" in *:*) TARGET=$TV ;; *) TARGET=$TV:5555 ;; esac
adb connect "$TARGET"
adb -s "$TARGET" wait-for-device
adb -s "$TARGET" install -r "$APK"
adb -s "$TARGET" shell am start -n com.merterbil.prompter/.PrompterActivity >/dev/null
echo "installed and opened TV Prompter on $TARGET"
