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
# The platform matrix of cmake/DetectPlatform.cmake, checked with `cmake -P` - no compiler, no
# toolchain, no vendored sources.  The function takes the CMake variables as arguments, so every row
# of the matrix is exercised here whatever the host is, including the Android rows, which a
# configure on this machine could not otherwise reach.
#
# Run by ctest as platform_detection_check, and by hand as:
#   cmake -P GeneralsMD/Code/Tests/test_platform_detection.cmake

include(${CMAKE_CURRENT_LIST_DIR}/../cmake/DetectPlatform.cmake)

set(_zh_failures "")

# expect_platform(name system android W P M L A): the five flags the function must answer, then the
# invariants every answer must satisfy whatever the row.
macro(expect_platform name system android want_w want_p want_m want_l want_a)
  zh_detect_platform("${system}" "${android}" got_w got_p got_m got_l got_a)
  set(_zh_ok TRUE)
  if(NOT "${want_w}" STREQUAL "${got_w}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_p}" STREQUAL "${got_p}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_m}" STREQUAL "${got_m}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_l}" STREQUAL "${got_l}")
    set(_zh_ok FALSE)
  endif()
  if(NOT "${want_a}" STREQUAL "${got_a}")
    set(_zh_ok FALSE)
  endif()
  if(_zh_ok)
    message(STATUS "  ok   ${name}: W=${got_w} P=${got_p} M=${got_m} L=${got_l} A=${got_a}")
  else()
    list(APPEND _zh_failures "${name}: want W=${want_w} P=${want_p} M=${want_m} L=${want_l} A=${want_a}, got W=${got_w} P=${got_p} M=${got_m} L=${got_l} A=${got_a}")
    message(STATUS "  FAIL ${name}: want W=${want_w} P=${want_p} M=${want_m} L=${want_l} A=${want_a}, got W=${got_w} P=${got_p} M=${got_m} L=${got_l} A=${got_a}")
  endif()

  # The invariants, checked on the answer itself rather than the row: Windows excludes POSIX, and the
  # three POSIX refinements are mutually exclusive.  This is what "check for conflicting platform
  # macros" means - no answer may ever have two of them set.
  if(got_w AND got_p)
    list(APPEND _zh_failures "${name}: ZH_PLATFORM_WINDOWS and ZH_PLATFORM_POSIX both set")
  endif()
  if(NOT (got_w OR got_p))
    list(APPEND _zh_failures "${name}: neither ZH_PLATFORM_WINDOWS nor ZH_PLATFORM_POSIX set")
  endif()
  if(got_m AND got_l)
    list(APPEND _zh_failures "${name}: ZH_PLATFORM_MACOS and ZH_PLATFORM_LINUX both set")
  endif()
  if(got_m AND got_a)
    list(APPEND _zh_failures "${name}: ZH_PLATFORM_MACOS and ZH_PLATFORM_ANDROID both set")
  endif()
  if(got_l AND got_a)
    list(APPEND _zh_failures "${name}: ZH_PLATFORM_LINUX and ZH_PLATFORM_ANDROID both set - Android must never be Linux")
  endif()
  if(got_w AND (got_m OR got_l OR got_a))
    list(APPEND _zh_failures "${name}: ZH_PLATFORM_WINDOWS with a POSIX refinement set")
  endif()
endmacro()

message(STATUS "platform detection matrix:")

# The rows that existed before Android, which must not have moved.
expect_platform("Windows x64"        "Windows" ""  TRUE  FALSE FALSE FALSE FALSE)
expect_platform("macOS"              "Darwin"  ""  FALSE TRUE  TRUE  FALSE FALSE)
expect_platform("Linux"              "Linux"   ""  FALSE TRUE  FALSE TRUE  FALSE)
# Any other POSIX system: still the umbrella, no refinement - as it was before.
expect_platform("other POSIX"        "FreeBSD" ""  FALSE TRUE  FALSE FALSE FALSE)

# Android, by the two things CMake sets.  Either one alone is enough.
expect_platform("Android (both)"     "Android" "1" FALSE TRUE  FALSE FALSE TRUE)
expect_platform("Android (name only)" "Android" ""  FALSE TRUE  FALSE FALSE TRUE)
expect_platform("Android (variable only)" "Linux" "1" FALSE TRUE FALSE FALSE TRUE)
# Android is never Linux: the row above is the whole point of the change.
# A Windows host with the variable set (a mis-detected toolchain) stays Windows.
expect_platform("Windows (stray flag)" "Windows" "1" TRUE FALSE FALSE FALSE FALSE)

if(_zh_failures)
  string(REPLACE ";" "\n  - " _zh_report "${_zh_failures}")
  message(FATAL_ERROR "platform detection: ${_zh_report}")
endif()
message(STATUS "platform detection: all rows pass")
