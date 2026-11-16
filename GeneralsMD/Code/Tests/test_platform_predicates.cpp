/*
**      Copyright 2026 İlyas Akın
**      Additional terms under GNU GPL section 7 apply: see LICENSE.md.
**
**      This program is free software: you can redistribute it and/or modify
**      it under the terms of the GNU General Public License as published by
**      the Free Software Foundation, either version 3 of the License, or
**      (at your option) any later version.
**
**      This program is distributed in the hope that it will be useful,
**      but WITHOUT ANY WARRANTY; without even the implied warranty of
**      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**      GNU General Public License for more details.
**
**      You should have received a copy of the GNU General Public License
**      along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// Platform/PlatformPredicates.h: the C++ twin of cmake/DetectPlatform.cmake.  Android's bionic defines
// __linux__ as well as __ANDROID__, so a bare defined(__linux__) is not "desktop Linux" and every
// Linux-only guard would otherwise compile on Android.  The test asserts the host's own answer and the
// invariants that hold on every host - on an Android build it is the proof that ZH_DESKTOP_LINUX is 0
// there even though __linux__ is defined.

#include "Platform/PlatformPredicates.h"

#include "test_harness.h"

// Compile-time proof, not a run-time check.  The two predicates are macros, so the compiler can prove
// the invariants on any host - including the Android one, where the point is that __linux__ is defined
// and ZH_DESKTOP_LINUX must still be 0.  A run-time CHECK would only catch a mistake on a build that
// got far enough to run.
static_assert(ZH_ANDROID == 0 || ZH_ANDROID == 1, "ZH_ANDROID is a 0/1 predicate");
static_assert(ZH_DESKTOP_LINUX == 0 || ZH_DESKTOP_LINUX == 1, "ZH_DESKTOP_LINUX is a 0/1 predicate");
static_assert(!(ZH_ANDROID && ZH_DESKTOP_LINUX), "Android is never desktop Linux");
#if defined(__ANDROID__)
static_assert(ZH_ANDROID == 1, "the NDK defines __ANDROID__, so ZH_ANDROID must be 1");
static_assert(ZH_DESKTOP_LINUX == 0, "__linux__ is defined on Android too, but this is not desktop Linux");
#endif

TEST(platform_predicates_are_boolean)
{
        CHECK(ZH_ANDROID == 0 || ZH_ANDROID == 1);
        CHECK(ZH_DESKTOP_LINUX == 0 || ZH_DESKTOP_LINUX == 1);
}

TEST(platform_predicates_are_mutually_exclusive)
{
        CHECK(!(ZH_ANDROID && ZH_DESKTOP_LINUX));       // Android is never desktop Linux
}

TEST(platform_predicates_match_the_compiler_macros)
{
#if defined(__ANDROID__)
        CHECK(ZH_ANDROID == 1);
        CHECK(ZH_DESKTOP_LINUX == 0);                   // the trap: __linux__ is defined here too
#elif defined(__linux__)
        CHECK(ZH_ANDROID == 0);
        CHECK(ZH_DESKTOP_LINUX == 1);
#else
        CHECK(ZH_ANDROID == 0);                         // Windows, macOS, the BSDs
        CHECK(ZH_DESKTOP_LINUX == 0);
#endif
}
