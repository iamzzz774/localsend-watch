#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/35.0.0
API=$SDK/platforms/android-35/android.jar
rm -rf "$ROOT/build/apk"
mkdir -p "$ROOT/build/apk/gen" "$ROOT/build/apk/classes" "$ROOT/build/apk/dex" "$ROOT/dist" "$ROOT/keystore"
BUILD=$ROOT/build/apk
"$BT/aapt2" compile --dir "$ROOT/src/main/res" -o "$BUILD/resources.zip"
"$BT/aapt2" link -I "$API" --manifest "$ROOT/src/main/AndroidManifest.xml" --java "$BUILD/gen" -o "$BUILD/base.apk" "$BUILD/resources.zip"
find "$ROOT/src/main/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac --release 8 -encoding UTF-8 -cp "$API" -d "$BUILD/classes" @"$BUILD/sources.txt"
jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$BT/d8" --lib "$API" --min-api 26 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q -j "$BUILD/unsigned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEYSTORE=${KEYSTORE:-$ROOT/keystore/localsend-watch-release.jks}
if [ "${UNSIGNED:-0}" = 1 ]; then
  cp "$BUILD/aligned.apk" "$ROOT/dist/localsend-watch-1.1.0-unsigned.apk"
  exit 0
fi
if [ -z "${PASSWORD:-}" ]; then
  if command -v security >/dev/null 2>&1; then
    PASSWORD=$(security find-generic-password -s localsend-watch-release -w)
  else
    printf '%s\n' 'Set KEYSTORE and PASSWORD to your own signing identity, or use UNSIGNED=1.' >&2
    exit 1
  fi
fi
export PASSWORD
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:PASSWORD --key-pass env:PASSWORD --v4-signing-enabled false --out "$ROOT/dist/localsend-watch-1.1.0.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify --verbose "$ROOT/dist/localsend-watch-1.1.0.apk"
"$BT/zipalign" -c -p 4 "$ROOT/dist/localsend-watch-1.1.0.apk"
