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
# Platform detection for the Zero Hour Reforged build.
#
# Kept in a file of its own, apart from CMakeLists.txt, so the whole matrix can be tested without a
# compiler, a toolchain or the vendored sources: Tests/test_platform_detection.cmake includes this
# file under `cmake -P` and checks every row.  One function, no side effects, no reads of anything
# but its arguments - the caller passes the CMake variables in, so the same call answers the same
# way whatever the host is.
#
# Android is a platform of its own, not a refinement of desktop Linux.  It has bionic libc, no X11
# or Wayland, no fork() or execv(), and its own application lifecycle.  It shares the POSIX umbrella
# with macOS and Linux - ZH_PLATFORM_POSIX stays TRUE, because that is what selects the SDL3 window,
# the event loop, the entry point and the D3D9-shaped device - but ZH_PLATFORM_LINUX must never be
# TRUE for it.  That one rule is what keeps the desktop-Linux specifics (LLD and mold, fontconfig,
# the X11 and Wayland link dependencies, the Steam Runtime packaging) out of an Android build: they
# are all gated on ZH_PLATFORM_LINUX, so they follow from this function without touching them.
#
# Windows, Linux and macOS keep exactly the answers they had before Android existed.

function(zh_detect_platform zh_system_name zh_android_flag
        zh_out_windows zh_out_posix zh_out_macos zh_out_linux zh_out_android)
  set(_zh_windows FALSE)
  set(_zh_posix FALSE)
  set(_zh_macos FALSE)
  set(_zh_linux FALSE)
  set(_zh_android FALSE)

  if(zh_system_name STREQUAL "Windows")
    set(_zh_windows TRUE)
  else()
    # Everything that is not Windows is POSIX: the umbrella, and the source set that carries the
    # SDL3 platform layer (decision 3).
    set(_zh_posix TRUE)

    # Android is tested first and on its own, never as a case of Linux.  CMAKE_SYSTEM_NAME is
    # "Android" under the NDK toolchain, and CMake sets the ANDROID variable with it; either one is
    # enough, so a toolchain that names the system but not the variable (or the reverse) is still
    # seen.  zh_android_flag is that variable, passed in as its value ("" when undefined).
    if(zh_system_name STREQUAL "Android" OR zh_android_flag)
      set(_zh_android TRUE)
    elseif(zh_system_name STREQUAL "Darwin")
      set(_zh_macos TRUE)
    elseif(zh_system_name STREQUAL "Linux")
      set(_zh_linux TRUE)
    endif()
  endif()

  set(${zh_out_windows} ${_zh_windows} PARENT_SCOPE)
  set(${zh_out_posix}   ${_zh_posix}   PARENT_SCOPE)
  set(${zh_out_macos}   ${_zh_macos}   PARENT_SCOPE)
  set(${zh_out_linux}   ${_zh_linux}   PARENT_SCOPE)
  set(${zh_out_android} ${_zh_android} PARENT_SCOPE)
endfunction()
