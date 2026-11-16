// The platform predicates the Android port reads, so that a condition says what it means.  The NDK's
// Clang defines __linux__ for Android as well as __ANDROID__, so "defined(__linux__)" alone is not
// "desktop Linux": every Linux-only guard compiles on Android too.  These spell the distinction once,
// where the C++ can read it.  See docs/android-port/architecture/12-platform-macro-audit.md.
// Distributed under the repository's GPL-3.0 terms; see LICENSE.md.
#pragma once

// Android: bionic libc, no X11 or Wayland, no fontconfig, no fork()/execv(), its own application
// lifecycle.  The NDK defines it for every Android target; CMake's ZH_PLATFORM_ANDROID is the
// build-side twin (cmake/DetectPlatform.cmake).
#if defined(__ANDROID__)
#define ZH_ANDROID 1
#else
#define ZH_ANDROID 0
#endif

// Desktop Linux: the Linux that has a desktop window server and the desktop-only system libraries
// (fontconfig, X11/Wayland, the Steam Runtime, LLD/mold).  __linux__ is true on Android too, so it is
// not this - the whole reason this header exists.  CMake's ZH_PLATFORM_LINUX is the build-side twin.
#if defined(__linux__) && !defined(__ANDROID__)
#define ZH_DESKTOP_LINUX 1
#else
#define ZH_DESKTOP_LINUX 0
#endif
