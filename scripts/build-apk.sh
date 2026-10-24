#!/usr/bin/env bash
# Builds Prompter.apk from source with the Android command-line tools (no Gradle, no Android Studio).
#
#   scripts/build-apk.sh
#
# Finds the JDK through JAVA_HOME (or Homebrew's openjdk@17) and the SDK through ANDROID_HOME /
# ANDROID_SDK_ROOT (or Homebrew's android-commandlinetools). It needs "platforms;android-34" and
# "build-tools;34.0.0" (override with ANDROID_PLATFORM / BUILD_TOOLS).
#
# Signing: with no settings it signs with your Android debug key (~/.android/debug.keystore, created
# if missing). To sign with your own release key, set:
#   PROMPTER_KEYSTORE=/path/to/release.keystore PROMPTER_KEYSTORE_PASS=... [PROMPTER_KEY_ALIAS=...]
# OUT=path/to/file.apk changes where the APK is written (default: Prompter.apk in the repo root).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

if [ -z "${JAVA_HOME:-}" ] && command -v brew >/dev/null && brew --prefix openjdk@17 >/dev/null 2>&1; then
  JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] || { echo "JDK 17 not found: set JAVA_HOME" >&2; exit 1; }
export JAVA_HOME
J=$JAVA_HOME/bin

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ] && command -v brew >/dev/null; then SDK="$(brew --prefix)/share/android-commandlinetools"; fi
BT=$SDK/build-tools/${BUILD_TOOLS:-34.0.0}
AJ=$SDK/platforms/${ANDROID_PLATFORM:-android-34}/android.jar
[ -x "$BT/aapt2" ] && [ -f "$AJ" ] || {
  echo "Android SDK not found at '$SDK': set ANDROID_HOME, then run" >&2
  echo "  sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\"" >&2
  exit 1
}

OUT=${OUT:-$ROOT/Prompter.apk}
B=$(mktemp -d)
trap 'rm -rf "$B"' EXIT
mkdir -p "$B/classes" "$B/dex"

"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" --manifest AndroidManifest.xml -A assets "$B/res.zip"
"$J/javac" -nowarn -source 8 -target 8 -bootclasspath "$AJ" -d "$B/classes" src/com/merterbil/prompter/*.java 2>&1 \
  | grep -v 'bootstrap classpath\|source value 8\|target value 8\|To suppress warnings\|^warning: \[options\]\|^[0-9]* warnings\?$' || true
[ -f "$B/classes/com/merterbil/prompter/PrompterActivity.class" ] || { echo "javac failed" >&2; exit 1; }
"$BT/d8" --min-api 26 --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"

if [ -n "${PROMPTER_KEYSTORE:-}" ]; then
  [ -n "${PROMPTER_KEYSTORE_PASS:-}" ] || { echo "set PROMPTER_KEYSTORE_PASS too" >&2; exit 1; }
  KS=$PROMPTER_KEYSTORE
  KS_PASS=env:PROMPTER_KEYSTORE_PASS
  ALIAS_ARGS=()
  [ -n "${PROMPTER_KEY_ALIAS:-}" ] && ALIAS_ARGS=(--ks-key-alias "$PROMPTER_KEY_ALIAS")
  KIND="release key"
else
  KS=$HOME/.android/debug.keystore
  if [ ! -f "$KS" ]; then
    mkdir -p "$HOME/.android"
    "$J/keytool" -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
  fi
  KS_PASS=pass:android
  ALIAS_ARGS=(--ks-key-alias androiddebugkey)
  KIND="debug key"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "$KS_PASS" ${ALIAS_ARGS[@]+"${ALIAS_ARGS[@]}"} --out "$OUT" "$B/aligned.apk"
rm -f "$OUT.idsig"
echo "built $OUT (signed with your $KIND)"
