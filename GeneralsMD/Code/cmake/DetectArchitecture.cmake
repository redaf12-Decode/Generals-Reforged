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
# Architecture detection for the Zero Hour Reforged build.
#
# Kept in a file of its own, apart from CMakeLists.txt, so the whole matrix can be checked without a
# compiler, a toolchain or the vendored sources: Tests/test_architecture_detection.cmake includes this
# file under `cmake -P` and checks every row.  One function, no side effects, no reads of anything but
# its arguments - the caller passes the CMake variables in, so the same call answers the same way
# whatever the host is.
#
# Why it exists.  The only architecture tests in the tree used to be
# `CMAKE_CXX_COMPILER_ARCHITECTURE_ID STREQUAL "ARM64"`, which is MSVC's own spelling and is empty
# under Clang and GCC.  That reads correctly on a Windows ARM64 build and reads as "not ARM64"
# everywhere else, so it cannot answer the question an Android build asks: Android's arm64-v8a is
# AArch64.  This function normalises the spellings once, so a test says "is this AArch64" rather than
# naming one compiler's macro.
#
# Three hints, because no single variable is reliable across toolchains:
#
#   CMAKE_SYSTEM_PROCESSOR        the target's processor.  "aarch64" under the NDK, "arm64" on Apple
#                                 Silicon, "x86_64"/"AMD64" on a desktop, but a toolchain that does
#                                 not set it leaves CMake's host-derived default in place.
#   CMAKE_CXX_COMPILER_ARCHITECTURE_ID  MSVC's own spelling ("ARM64", "X64"); empty under Clang and GCC.
#   ANDROID_ABI                   the NDK's own ABI name ("arm64-v8a", "armeabi-v7a", "x86_64"); empty
#                                 off Android.  This is the one the NDK toolchain always sets, so an
#                                 Android configure is answered even if the first two are not.
#
# It is a separate axis from the platform (cmake/DetectPlatform.cmake): a platform can be any
# architecture, and Android on arm64 is not "Linux on x86-64" - it is Android, on AArch64.

function(zh_detect_architecture zh_system_processor zh_compiler_arch_id zh_android_abi
        zh_out_x86_64 zh_out_aarch64 zh_out_known)
  set(_zh_x86_64 FALSE)
  set(_zh_aarch64 FALSE)
  set(_zh_known FALSE)

  string(TOLOWER "${zh_system_processor}" _zh_proc)
  string(TOLOWER "${zh_compiler_arch_id}" _zh_id)
  string(TOLOWER "${zh_android_abi}" _zh_abi)

  foreach(_zh_candidate "${_zh_proc}" "${_zh_id}" "${_zh_abi}")
    # AArch64: the NDK's "aarch64", Apple's "arm64", the ABI name "arm64-v8a", the ARMv8 family.
    # "armeabi-v7a" (32-bit) deliberately does not match.
    if(_zh_candidate MATCHES "^(aarch64|arm64.*|armv8.*)$")
      set(_zh_aarch64 TRUE)
      set(_zh_known TRUE)
    elseif(_zh_candidate MATCHES "^(x86_64|amd64|x64)$")
      set(_zh_x86_64 TRUE)
      set(_zh_known TRUE)
    endif()
  endforeach()

  set(${zh_out_x86_64} ${_zh_x86_64} PARENT_SCOPE)
  set(${zh_out_aarch64} ${_zh_aarch64} PARENT_SCOPE)
  set(${zh_out_known} ${_zh_known} PARENT_SCOPE)
endfunction()
