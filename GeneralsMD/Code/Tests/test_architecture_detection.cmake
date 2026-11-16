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
# The architecture matrix of cmake/DetectArchitecture.cmake, checked with `cmake -P` - no compiler,
# no toolchain, no vendored sources.  The function takes the CMake variables as arguments, so every
# row of the matrix is exercised here whatever the host is, including the Android arm64 rows, which a
# configure on this x86_64 machine could not otherwise reach.
#
# Run by ctest as architecture_detection_check, and by hand as:
#   cmake -P GeneralsMD/Code/Tests/test_architecture_detection.cmake

include(${CMAKE_CURRENT_LIST_DIR}/../cmake/DetectArchitecture.cmake)

set(_zh_failures "")

# expect_arch(name processor compiler_id android_abi want_x86_64 want_aarch64 want_known): the three
# answers the function must give, then the invariant every answer must satisfy whatever the row.
macro(expect_arch name processor compiler_id android_abi want_x86_64 want_aarch64 want_known)
  zh_detect_architecture("${processor}" "${compiler_id}" "${android_abi}" got_x86_64 got_aarch64 got_known)
  set(_zh_ok TRUE)
  if(NOT "${want_x86_64}" STREQUAL "${got_x86_64}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_aarch64}" STREQUAL "${got_aarch64}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_known}" STREQUAL "${got_known}")
    set(_zh_ok FALSE)
  endif()
  if(_zh_ok)
    message(STATUS "  ok   ${name}: x86_64=${got_x86_64} aarch64=${got_aarch64} known=${got_known}")
  else()
    list(APPEND _zh_failures "${name}: want x86_64=${want_x86_64} aarch64=${want_aarch64} known=${want_known}, got x86_64=${got_x86_64} aarch64=${got_aarch64} known=${got_known}")
    message(STATUS "  FAIL ${name}: want x86_64=${want_x86_64} aarch64=${want_aarch64} known=${want_known}, got x86_64=${got_x86_64} aarch64=${got_aarch64} known=${got_known}")
  endif()

  # No answer may ever be both x86-64 and AArch64.
  if(got_x86_64 AND got_aarch64)
    list(APPEND _zh_failures "${name}: ZH_ARCH_X86_64 and ZH_ARCH_AARCH64 both set")
  endif()
endmacro()

message(STATUS "architecture detection matrix:")

# Android arm64-v8a, in each spelling a toolchain may use.  These rows are the whole point of the
# module: none of them may be read as x86-64.
expect_arch("Android arm64 (processor)"  "aarch64"   ""      "arm64-v8a" FALSE TRUE  TRUE)
expect_arch("Android arm64 (abi only)"   ""          ""      "arm64-v8a" FALSE TRUE  TRUE)
expect_arch("Android arm64 (host leaks)" "x86_64"    ""      "arm64-v8a" TRUE  TRUE  TRUE)
# Apple Silicon and a Windows ARM64 build.
expect_arch("macOS arm64"                "arm64"     ""      ""          FALSE TRUE  TRUE)
expect_arch("Windows ARM64 (MSVC id)"    "ARM64"     "ARM64" ""          FALSE TRUE  TRUE)
# The x86-64 rows the old tests assumed, kept exactly.
expect_arch("Linux x86_64"               "x86_64"    ""      ""          TRUE  FALSE TRUE)
expect_arch("Windows x64 (MSVC id)"      "AMD64"     "X64"   ""          TRUE  FALSE TRUE)
expect_arch("Android x86_64 (abi)"       ""          ""      "x86_64"    TRUE  FALSE TRUE)
# 32-bit ARM is not AArch64, and neither is an architecture this build does not name.
expect_arch("Android armeabi-v7a"        ""          ""      "armeabi-v7a" FALSE FALSE FALSE)
expect_arch("unknown (riscv64)"          "riscv64"   ""      ""          FALSE FALSE FALSE)
expect_arch("empty"                      ""          ""      ""          FALSE FALSE FALSE)

if(_zh_failures)
  string(REPLACE ";" "\n  - " _zh_report "${_zh_failures}")
  message(FATAL_ERROR "architecture detection: ${_zh_report}")
endif()
message(STATUS "architecture detection: all rows pass")
