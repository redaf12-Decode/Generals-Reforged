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
# CMake toolchain for the Zero Hour Reforged Android build, arm64-v8a.
#
# A thin, explicit layer over the NDK's own toolchain file.  The NDK file (build/cmake/
# android.toolchain.cmake) knows how to find clang, the sysroot, the C++ runtime and the per-API
# system libraries; everything this project decides is stated here, once, so a configure cannot
# drift from the numbers the port is written against:
#
#   ABI            arm64-v8a          (the only ABI the port targets)
#   API            28                 (Android 9; the reference port's floor)
#   compiler       Clang              (the only compiler the NDK ships)
#   STL            c++_shared         (libc++_shared.so, staged into the APK by the packager)
#   C++ standard   17                 (the engine's; the lobby client's C++20 is a Windows-only add-on)
#
# It is the CMakeLists.txt, not this file, that refuses the parts of the desktop build that do not
# exist on Android (X11/Wayland, fontconfig, LLD/mold, the FFmpeg build-from-tarball, the desktop
# executable).  Those are gated on ZH_PLATFORM_LINUX, which Android never sets (Task 2.1), so
# nothing here has to unset them - and nothing here may link a desktop library.
#
# Use it as:
#
#   cmake -S GeneralsMD/Code -B build-android-arm64 -G Ninja \
#     -DCMAKE_TOOLCHAIN_FILE=scripts/build/android/android.toolchain.cmake \
#     -DCMAKE_BUILD_TYPE=Release
#
# which is what scripts/build/android/build-android.sh runs after it has checked the SDK and the
# NDK.  ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) must point at the NDK, as the NDK file requires; the
# build script sets it from --ndk or from the environment.

# The NDK path, from whichever name the environment uses.  The NDK file reads ANDROID_NDK_HOME and
# errors clearly when it is unset, so the name is normalised here rather than guessed there.
if(NOT DEFINED ANDROID_NDK_HOME AND DEFINED ENV{ANDROID_NDK_HOME})
  set(ANDROID_NDK_HOME "$ENV{ANDROID_NDK_HOME}")
endif()
if(NOT DEFINED ANDROID_NDK_HOME AND DEFINED ENV{ANDROID_NDK_ROOT})
  set(ANDROID_NDK_HOME "$ENV{ANDROID_NDK_ROOT}")
endif()
if(NOT DEFINED ANDROID_NDK_HOME)
  message(FATAL_ERROR
    "ANDROID_NDK_HOME is not set, so the NDK toolchain cannot be found.\n"
    "Set it to an NDK r26 or newer (r27 is what the port is measured with), for example:\n"
    "  export ANDROID_NDK_HOME=/path/to/android-ndk-r27c\n"
    "scripts/build/android/build-android.sh sets this for you from --ndk or the environment.")
endif()

# --- the decisions above, as the variables the NDK file reads --------------------------------
set(ANDROID_ABI "arm64-v8a" CACHE STRING "Android ABI")
set(ANDROID_PLATFORM "android-28" CACHE STRING "Android API level baseline")
set(ANDROID_STL "c++_shared" CACHE STRING "Android C++ runtime; libc++_shared.so ships in the APK")
set(ANDROID_ARM_MODE "arm" CACHE STRING "32-bit ARM mode; irrelevant to arm64-v8a, kept explicit")
# The toolchain and compiler are not selectable: the NDK ships only Clang now, and naming it keeps
# the NDK from warning about the removed GCC.  The API level must not be raised past the baseline by
# a newer default, so the platform above is a cache entry the NDK file will honour.
set(ANDROID_TOOLCHAIN "clang" CACHE STRING "NDK toolchain; clang is the only one shipped")

# --- the project's own language level, stated for the whole tree ------------------------------
# Every target in GeneralsMD/Code/CMakeLists.txt already sets CXX_STANDARD 17 itself, so this only
# covers a target that forgets and the vendored projects; CXX_STANDARD_REQUIRED stops a compiler
# from quietly accepting the flag and compiling to an older standard.
set(CMAKE_CXX_STANDARD 17 CACHE STRING "The engine's C++ standard")
set(CMAKE_CXX_STANDARD_REQUIRED ON CACHE BOOL "")
set(CMAKE_CXX_EXTENSIONS OFF CACHE BOOL "GNU extensions off, as the desktop build has them")

# --- hand the rest to the NDK ----------------------------------------------------------------
include(${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake)
