#!/usr/bin/env bash
#       Copyright 2026 İlyas Akın
#       Additional terms under GNU GPL section 7 apply: see LICENSE.md.
#
#       This program is free software: you can redistribute it and/or modify
#       it under the terms of the GNU General Public License as published by
#       the Free Software Foundation, either version 3 of the License, or
#       (at your option) any later version.
#
#       This program is distributed in the hope that it will be useful,
#       but WITHOUT ANY WARRANTY; without even the implied warranty of
#       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#       GNU General Public License for more details.
#
#       You should have received a copy of the GNU General Public License
#       along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Package the Android build of Zero Hour Reforged into an APK. The Gradle half of
# scripts/build/android/build-android.sh: that script produces the engine as libmain.so,
# this one stages it (and SDL3's Java glue) into the android/ application project and runs
# Gradle to package the APK.
#
#   scripts/build/android/package-android.sh                debug APK from build-android-arm64/
#   scripts/build/android/package-android.sh --build-dir D  another native build tree
#   scripts/build/android/package-android.sh --release      assembleRelease instead of assembleDebug
#   scripts/build/android/package-android.sh --install      adb install the APK to the connected device
#
# The native build tree must already hold libmain.so (run build-android.sh first); the NDK must
# be named (ANDROID_NDK_HOME / ANDROID_NDK_ROOT, or --ndk) for libc++_shared.so, and the SDK
# (ANDROID_SDK_ROOT / ANDROID_HOME, or --sdk) for Gradle. The SDL3 Java glue comes from the
# vendored SDL3 tree (Libraries/Source/SDL3), the exact version the engine linked.
#
# What is staged into android/app/src/main (both gitignored, re-staged every run):
#   jniLibs/arm64-v8a/libmain.so        the engine, built from Main/PosixMain.cpp
#   jniLibs/arm64-v8a/libSDL3.so        SDL3 3.4.16 as a standalone shared library (SDL_SHARED on)
#   jniLibs/arm64-v8a/libc++_shared.so  the NDK's shared C++ runtime (ANDROID_STL=c++_shared)
#   java-sdl/org/libsdl/app/*.java      SDL3's Java glue (SDLActivity et al), the same version as
#                                       libSDL3.so: both come from Libraries/Source/SDL3
#
# libSDL3.so and libmain.so are the two libraries SDLActivity loads, in that order
# (ZeroHourActivity.getLibraries). SDL3's JNI_OnLoad lives in libSDL3.so, so it is not optional.
#
# Nothing is worked around: a missing library is a failure with its name, not a flag that hides it.

set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
VARIANT=Debug
DO_INSTALL=0
GRADLE=

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
BUILD="$ROOT/build-android-arm64"
ANDROID_DIR="$ROOT/android"
JNILIBS="$ANDROID_DIR/app/src/main/jniLibs/arm64-v8a"
JAVA_SDL="$ANDROID_DIR/app/src/main/java-sdl"
SDL3_JAVA="$ROOT/GeneralsMD/Code/Libraries/Source/SDL3/android-project/app/src/main/java/org/libsdl/app"

fail() { echo "[package] ERROR: $1" >&2; exit 1; }

usage() {
  sed -n '/^#   scripts\/build\/android\/package-android.sh/,/^#$/p' "$0" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

  --sdk DIR      Android SDK root (default: $ANDROID_SDK_ROOT, $ANDROID_HOME)
  --ndk DIR      Android NDK root (default: $ANDROID_NDK_HOME, $ANDROID_NDK_ROOT)
  --build-dir D  native build tree holding libmain.so (default: <root>/build-android-arm64)
  --release      assembleRelease instead of assembleDebug
  --install      adb install the built APK to the connected device
  -h, --help     this text
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --sdk) SDK="$2"; shift 2 ;;
    --sdk=*) SDK="${1#*=}"; shift ;;
    --ndk) NDK="$2"; shift 2 ;;
    --ndk=*) NDK="${1#*=}"; shift ;;
    --build-dir) BUILD="$2"; shift 2 ;;
    --build-dir=*) BUILD="${1#*=}"; shift ;;
    --release) VARIANT=Release; shift ;;
    --install) DO_INSTALL=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument '$1' (try --help)" ;;
  esac
done

# --- the native engine ---------------------------------------------------------------------------
ENGINE="$BUILD/libmain.so"
[ -f "$ENGINE" ] || fail "libmain.so was not found at $ENGINE.
[package]        Build it first:  scripts/build/android/build-android.sh
[package]        (or point --build-dir at the tree that holds it)."

# --- SDL3, the engine's standalone platform library ----------------------------------------------
# The Android build configures SDL3 with SDL_SHARED on and SDL_STATIC off, so the build tree holds
# libSDL3.so beside libmain.so and libmain.so names it in DT_NEEDED. It has to reach the APK:
# SDLActivity loads "SDL3" before "main", and SDL3's JNI_OnLoad - which registers the activity's
# native methods - is in libSDL3.so.
SDL3_LIB="$BUILD/libSDL3.so"
[ -f "$SDL3_LIB" ] || fail "libSDL3.so was not found at $SDL3_LIB.
[package]        The Android build produces it from the vendored SDL3 tree (SDL_SHARED on), as part
[package]        of the engine's link: scripts/build/android/build-android.sh --target generals."

# --- the NDK, for libc++_shared.so ---------------------------------------------------------------
[ -n "$NDK" ] || fail "the Android NDK was not found. Set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT), or pass --ndk DIR.
[package]        libc++_shared.so comes from the same NDK the engine was built with."
LIBCXX=$(ls "$NDK"/toolchains/llvm/prebuilt/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so 2>/dev/null | head -n 1)
[ -n "$LIBCXX" ] || fail "libc++_shared.so was not found in the NDK sysroot under $NDK.
[package]        Expected toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"

# --- SDL3's Java glue, from the tree the engine linked -------------------------------------------
[ -d "$SDL3_JAVA" ] || fail "SDL3's Java glue was not found at $SDL3_JAVA.
[package]        The SDL3 tree is fetched, not committed: run GeneralsMD/Code/Tools/vendor.sh."

# --- the SDK and Gradle --------------------------------------------------------------------------
[ -n "$SDK" ] || fail "the Android SDK was not found. Set ANDROID_SDK_ROOT (or ANDROID_HOME), or pass --sdk DIR."
[ -d "$SDK" ] || fail "--sdk $SDK is not a directory."
[ -d "$SDK/platforms" ] || fail "$SDK has no platforms/ directory, so it is not an Android SDK.
[package]        Install one:  sdkmanager --install 'platforms;android-35' 'build-tools;35.0.0'"
SDK_PLATFORM=$(ls "$SDK/platforms" 2>/dev/null | sed -n 's/^android-\([0-9][0-9]*\)$/\1/p' | sort -n | tail -n 1)
[ -n "$SDK_PLATFORM" ] || fail "$SDK/platforms holds no android-<n> platform."
[ "$SDK_PLATFORM" -ge 35 ] || fail "the SDK has platform android-$SDK_PLATFORM but the app builds against android-35.
[package]        Install it:  sdkmanager --install 'platforms;android-35'"

if [ -x "$ANDROID_DIR/gradlew" ]; then
  GRADLE="$ANDROID_DIR/gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  fail "no Gradle found: neither android/gradlew nor a 'gradle' on PATH.
[package]        Install Gradle 8.x, or open android/ once in Android Studio to generate the wrapper."
fi

echo "[package] engine:  $ENGINE"
echo "[package] sdl3:    $SDL3_LIB"
echo "[package] ndk:     $NDK"
echo "[package] sdk:     $SDK (platform android-$SDK_PLATFORM)"
echo "[package] gradle:  $GRADLE"
echo "[package] variant: $VARIANT"

# --- 1. stage the native libraries ---------------------------------------------------------------
rm -rf "$JNILIBS"
mkdir -p "$JNILIBS"
cp "$ENGINE" "$JNILIBS/libmain.so"
cp "$SDL3_LIB" "$JNILIBS/libSDL3.so"
cp "$LIBCXX" "$JNILIBS/libc++_shared.so"
echo "[package] staged native libraries:"
ls -la "$JNILIBS"

# --- 2. stage SDL3's Java glue -------------------------------------------------------------------
rm -rf "$JAVA_SDL"
mkdir -p "$JAVA_SDL/org/libsdl/app"
cp "$SDL3_JAVA"/*.java "$JAVA_SDL/org/libsdl/app/"
echo "[package] staged SDL3 Java glue ($(ls "$JAVA_SDL/org/libsdl/app" | wc -l | tr -d ' ') files)"

# --- 3. package ----------------------------------------------------------------------------------
( cd "$ANDROID_DIR" && "$GRADLE" --no-daemon "assemble$VARIANT" )

APK="$ANDROID_DIR/app/build/outputs/apk/$(printf '%s' "$VARIANT" | tr '[:upper:]' '[:lower:]')/app-$(printf '%s' "$VARIANT" | tr '[:upper:]' '[:lower:]').apk"
[ -f "$APK" ] || fail "the build did not produce $APK"
echo "[package] APK: $APK"

if [ "$DO_INSTALL" = 1 ]; then
  command -v adb >/dev/null 2>&1 || fail "adb not found on PATH"
  adb install -r "$APK"
  echo "[package] installed. Game data goes to a folder the player picks at first start."
fi
