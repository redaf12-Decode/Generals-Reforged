#!/usr/bin/env bash
#	Copyright 2026 İlyas Akın
#	Additional terms under GNU GPL section 7 apply: see LICENSE.md.
#
#	This program is free software: you can redistribute it and/or modify
#	it under the terms of the GNU General Public License as published by
#	the Free Software Foundation, either version 3 of the License, or
#	(at your option) any later version.
#
#	This program is distributed in the hope that it will be useful,
#	but WITHOUT ANY WARRANTY; without even the implied warranty of
#	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#	GNU General Public License for more details.
#
#	You should have received a copy of the GNU General Public License
#	along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Cross-compile Zero Hour Reforged for Android, arm64-v8a. The Android half of build-linux.sh and
# build-macos.sh: it checks the SDK, the NDK and the tools, configures CMake with the NDK toolchain
# (android.toolchain.cmake, beside this file), and builds into a tree of its own so the desktop
# builds are untouched.
#
#   scripts/build/android/build-android.sh                 Release into build-android-arm64/
#   scripts/build/android/build-android.sh --config Debug  another configuration
#   scripts/build/android/build-android.sh --target generals  one target
#   scripts/build/android/build-android.sh --clean         delete the tree and start again
#   scripts/build/android/build-android.sh --no-build      configure only, then stop
#
# The SDK and NDK are found through ANDROID_SDK_ROOT / ANDROID_HOME and ANDROID_NDK_HOME /
# ANDROID_NDK_ROOT, or named with --sdk and --ndk. Both are required: the NDK to compile, the SDK to
# package against. What each is missing is named, with the command that installs it.
#
# The target is arm64-v8a, API 28, Clang, c++_shared and C++17 - the numbers android.toolchain.cmake
# states once. Nothing here links a desktop library or reuses a Linux binary: the CMakeLists gates
# those on ZH_PLATFORM_LINUX, which Android never sets (Task 2.1), so an Android configure cannot
# reach them. Nothing here turns a dependency off to get past it either: a missing dependency is a
# failure with its name, not a flag that hides it.

set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
ABI=arm64-v8a
API=28
CONFIG=Release
TARGET=
CLEAN=
DO_BUILD=1
JOBS=
CMAKE=
VERBOSE=

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
SRC="$ROOT/GeneralsMD/Code"
BUILD="$ROOT/build-android-arm64"
TOOLCHAIN="$(cd "$(dirname "$0")" && pwd)/android.toolchain.cmake"

fail() { echo "[android] ERROR: $1" >&2; exit 1; }
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

usage() {
  sed -n '/^#   scripts\/build\/android\/build-android.sh/,/^#$/p' "$0" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

  --sdk DIR      Android SDK root (default: $ANDROID_SDK_ROOT, $ANDROID_HOME)
  --ndk DIR      Android NDK root (default: $ANDROID_NDK_HOME, $ANDROID_NDK_ROOT)
  --build-dir D  build tree (default: <root>/build-android-arm64)
  --config C     Release | RelWithDebInfo | Debug (default: Release)
  --abi A        only arm64-v8a is supported
  --api N        Android API level (default: 28)
  --target T     build one target instead of the default set
  --clean        remove the build tree first
  --no-build     configure only
  --jobs N       parallel build jobs (default: the toolchain's own)
  --verbose      pass the configure and build output through instead of logging it
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
    --config) CONFIG="$2"; shift 2 ;;
    --config=*) CONFIG="${1#*=}"; shift ;;
    --abi) ABI="$2"; shift 2 ;;
    --abi=*) ABI="${1#*=}"; shift ;;
    --api) API="$2"; shift 2 ;;
    --api=*) API="${1#*=}"; shift ;;
    --target) TARGET="$2"; shift 2 ;;
    --target=*) TARGET="${1#*=}"; shift ;;
    --clean) CLEAN=1; shift ;;
    --no-build) DO_BUILD=0; shift ;;
    --jobs) JOBS="$2"; shift 2 ;;
    --jobs=*) JOBS="${1#*=}"; shift ;;
    --verbose) VERBOSE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument '$1' (try --help)" ;;
  esac
done

case "$ABI" in
  arm64-v8a) ;;
  *) fail "--abi $ABI: only arm64-v8a is supported by this port." ;;
esac
case "$API" in
  ''|*[!0-9]*) fail "--api $API: an API level is a number, for example 28." ;;
  *) [ "$API" -ge 21 ] || fail "--api $API: the baseline is 21 or higher; the port uses 28." ;;
esac
case "$(lower "$CONFIG")" in
  release) CONFIG=Release ;;
  relwithdebinfo) CONFIG=RelWithDebInfo ;;
  debug) CONFIG=Debug ;;
  *) fail "--config $CONFIG: Release, RelWithDebInfo or Debug." ;;
esac

# --- the SDK and the NDK, named one by one when something is missing -----------------------------
[ -n "$SDK" ] || fail "the Android SDK was not found. Set ANDROID_SDK_ROOT (or ANDROID_HOME), or pass --sdk DIR.
[android]        Install it with the command line tools from
[android]        https://developer.android.com/studio#command-line-tools-only :
[android]          sdkmanager --install 'platform-tools' 'platforms;android-$API' 'build-tools;35.0.0'"
[ -d "$SDK" ] || fail "--sdk $SDK is not a directory."
[ -d "$SDK/platforms" ] || fail "$SDK has no platforms/ directory, so it is not an Android SDK.
[android]        Install a platform:  sdkmanager --install 'platforms;android-$API'"
SDK_PLATFORM=$(ls "$SDK/platforms" 2>/dev/null | sed -n 's/^android-\([0-9][0-9]*\)$/\1/p' | sort -n | tail -n 1)
[ -n "$SDK_PLATFORM" ] || fail "$SDK/platforms holds no android-<n> platform.
[android]        Install one:  sdkmanager --install 'platforms;android-$API'"
[ "$SDK_PLATFORM" -ge "$API" ] || fail "the SDK has platform android-$SDK_PLATFORM but the build needs android-$API or newer.
[android]        Install it:  sdkmanager --install 'platforms;android-$API'"

[ -n "$NDK" ] || fail "the Android NDK was not found. Set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT), or pass --ndk DIR.
[android]        Install NDK r26 or newer, for example:
[android]          sdkmanager --install 'ndk;27.2.12479018'   (then ANDROID_NDK_HOME=\$SDK/ndk/27.2.12479018)"
[ -d "$NDK" ] || fail "--ndk $NDK is not a directory."
[ -f "$NDK/build/cmake/android.toolchain.cmake" ] || fail "$NDK is not an Android NDK: build/cmake/android.toolchain.cmake is missing.
[android]        Point --ndk or ANDROID_NDK_HOME at the NDK root, not at its build/ or toolchains/ subdirectory."
NDK_REV=$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties" 2>/dev/null | head -n 1)
NDK_MAJOR="${NDK_REV%%.*}"
if [ -n "$NDK_MAJOR" ] && [ "$NDK_MAJOR" -lt 26 ] 2>/dev/null; then
  fail "NDK $NDK_REV is too old; r26 or newer is needed (r27 is what the port is measured with)."
fi

# --- the tools -----------------------------------------------------------------------------------
missing=
need() { for tool in "$@"; do command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"; done; }
need git curl tar ninja python3

[ -n "$CMAKE" ] || CMAKE=$(command -v cmake 2>/dev/null || true)
for try in /opt/homebrew/bin/cmake /usr/local/bin/cmake; do
  [ -n "$CMAKE" ] || { [ -x "$try" ] && CMAKE="$try"; }
done
[ -n "$CMAKE" ] || missing="$missing cmake"
if [ -n "$CMAKE" ]; then
  CMAKE_VERSION=$("$CMAKE" --version | sed -n '1s/^cmake version //p')
  CMAKE_MAJOR=${CMAKE_VERSION%%.*}
  CMAKE_REST=${CMAKE_VERSION#*.}
  CMAKE_MINOR=${CMAKE_REST%%.*}
  if [ "${CMAKE_MAJOR:-0}" -lt 3 ] || { [ "${CMAKE_MAJOR:-0}" -eq 3 ] && [ "${CMAKE_MINOR:-0}" -lt 20 ]; }; then
    fail "cmake $CMAKE_VERSION is too old; GeneralsMD/Code/CMakeLists.txt needs 3.20 or newer."
  fi
fi

# The NDK ships Clang per host; the host tag names the directory under toolchains/llvm/prebuilt.
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) NDK_HOST=linux-x86_64 ;;
  Linux-aarch64|Linux-arm64) NDK_HOST=linux-x86_64 ;;
  Darwin-x86_64) NDK_HOST=darwin-x86_64 ;;
  Darwin-arm64) NDK_HOST=darwin-arm64 ;;
  *) NDK_HOST= ;;
esac
NDK_CLANG=""
if [ -n "$NDK_HOST" ]; then
  NDK_CLANG=$(ls "$NDK/toolchains/llvm/prebuilt/$NDK_HOST/bin/aarch64-linux-android$API-clang" 2>/dev/null || true)
fi
[ -n "$NDK_CLANG" ] || missing="$missing aarch64-linux-android$API-clang (in $NDK/toolchains/llvm/prebuilt/${NDK_HOST:-<host>}/bin)"

if [ -n "$missing" ]; then
  fail "missing:$missing
[android]        Debian or Ubuntu:  sudo apt-get install cmake ninja-build git curl python3
[android]        macOS (Homebrew):  brew install cmake ninja git curl python3"
fi

echo "[android] sdk:    $SDK (platform android-$SDK_PLATFORM)"
echo "[android] ndk:    $NDK${NDK_REV:+ (r$NDK_REV)}"
echo "[android] cmake:  $CMAKE ($CMAKE_VERSION)"
echo "[android] target: abi=$ABI api=$API stl=c++_shared c++17"
echo "[android] config: $CONFIG, generator Ninja"
echo "[android] tree:   $BUILD"

# --- the compiler cache (optional) ---------------------------------------------------------------
# ccache, when it is on PATH, is handed to CMake as the compiler launcher for C and C++ (CMake's own
# CMAKE_<LANG>_COMPILER_LAUNCHER, which the NDK's Clang honours). It is optional: a machine without
# ccache configures exactly as before. The directory, the size cap and the compression come from the
# environment (CCACHE_DIR, CCACHE_MAXSIZE, CCACHE_COMPRESS), which CI sets and restores with
# actions/cache; locally, ccache's own defaults apply. A ccache hit is a content match - compiler,
# flags and preprocessed source - so it never returns a stale object for changed code.
CCACHE_LAUNCHER=$(command -v ccache 2>/dev/null || true)
if [ -n "$CCACHE_LAUNCHER" ]; then
  echo "[android] ccache: $CCACHE_LAUNCHER (dir: ${CCACHE_DIR:-<ccache default>})"
else
  echo "[android] ccache: not found; compiling without a compiler cache"
fi

if [ -n "$CLEAN" ]; then
  echo "[android] removing $BUILD"
  rm -rf "$BUILD"
fi
mkdir -p "$BUILD"

# --- configure -----------------------------------------------------------------------------------
# Ninja is single-config: the configuration is baked in at configure time, so a change to --config has
# to configure again rather than silently keep the tree's old one.
cached_config=
cached_launcher=
if [ -e "$BUILD/CMakeCache.txt" ]; then
  cached_config=$(sed -n 's/^CMAKE_BUILD_TYPE:[^=]*=\(.*\)$/\1/p' "$BUILD/CMakeCache.txt" | head -n 1)
  # The launcher is compared too, so installing or removing ccache between runs re-configures rather
  # than leaving the tree with a launcher it no longer has (or a stale one it gained).
  cached_launcher=$(sed -n 's/^CMAKE_CXX_COMPILER_LAUNCHER:[^=]*=\(.*\)$/\1/p' "$BUILD/CMakeCache.txt" | head -n 1)
fi
if [ ! -e "$BUILD/CMakeCache.txt" ] || [ ! -e "$BUILD/build.ninja" ] || [ "$cached_config" != "$CONFIG" ] \
   || [ "$cached_launcher" != "$CCACHE_LAUNCHER" ]; then
  echo "[android] configuring $SRC -> $BUILD"
  cfg_log="$BUILD/android-configure.log"
  cfg_args=(-S "$SRC" -B "$BUILD" -G Ninja
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN"
    -DCMAKE_BUILD_TYPE="$CONFIG"
    -DANDROID_NDK_HOME="$NDK"
    -DANDROID_ABI="$ABI"
    -DANDROID_PLATFORM="android-$API"
    -DANDROID_STL=c++_shared
    -DCMAKE_CXX_STANDARD=17
    -DCMAKE_CXX_STANDARD_REQUIRED=ON)
  [ -n "$CCACHE_LAUNCHER" ] && cfg_args+=(-DCMAKE_C_COMPILER_LAUNCHER="$CCACHE_LAUNCHER"
                                          -DCMAKE_CXX_COMPILER_LAUNCHER="$CCACHE_LAUNCHER")
  if [ -n "$VERBOSE" ]; then
    "$CMAKE" "${cfg_args[@]}" || fail "configure failed (see the output above)."
  elif "$CMAKE" "${cfg_args[@]}" >"$cfg_log" 2>&1; then
    tail -n 3 "$cfg_log" | sed 's/^/[android] /'
  else
    echo "[android] configure FAILED" >&2
    tail -n 40 "$cfg_log" >&2
    echo "[android] full configure log: $cfg_log" >&2
    fail "cmake could not configure the Android build. The error above is the cause; the log has it in full."
  fi
else
  echo "[android] $BUILD is already configured for $CONFIG"
fi

if [ "$DO_BUILD" = 0 ]; then
  echo "[android] configure only (--no-build); done."
  exit 0
fi

# --- build ---------------------------------------------------------------------------------------
build_args=(--build "$BUILD")
[ -n "$TARGET" ] && build_args+=(--target "$TARGET")
[ -n "$JOBS" ] && build_args+=(--parallel "$JOBS")
echo "[android] building${TARGET:+ target $TARGET}"
# ccache statistics before the build, so the delta this run produced is visible next to the after
# numbers below (and in the CI log).
[ -n "$CCACHE_LAUNCHER" ] && { echo "[android] ccache stats before:"; "$CCACHE_LAUNCHER" -s; }
build_log="$BUILD/android-build.log"
if [ -n "$VERBOSE" ]; then
  "$CMAKE" "${build_args[@]}" || fail "build failed (see the output above)."
elif "$CMAKE" "${build_args[@]}" >"$build_log" 2>&1; then
  tail -n 3 "$build_log" | sed 's/^/[android] /'
else
  echo "[android] build FAILED" >&2
  tail -n 40 "$build_log" >&2
  echo "[android] full build log: $build_log" >&2
  fail "the Android build failed. The error above is the cause; the log has it in full."
fi
[ -n "$CCACHE_LAUNCHER" ] && { echo "[android] ccache stats after:"; "$CCACHE_LAUNCHER" -s; }
echo "[android] done: $BUILD${TARGET:+/$TARGET}"
