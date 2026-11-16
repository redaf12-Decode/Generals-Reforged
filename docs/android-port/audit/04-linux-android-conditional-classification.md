# 04 — Android platform detection: classifying every `__linux__` site

**Task 2.1 — Establish independent Android platform detection.** Branch `android-arm64-port`,
based on the audit branch `docs/android-port-audit-01` (`45340c4`). This document records what was
found, what was changed, and what is deliberately left for a later task.

It continues
[`../architecture/12-platform-macro-audit.md`](../architecture/12-platform-macro-audit.md)
(which resolves question 7 of
[`01-target-architecture.md`](01-target-architecture.md#11-unresolved-questions)) and implements its
decision.

## The problem, restated

The NDK's Clang defines `__linux__` for Android targets as well as `__ANDROID__`:

```
$ echo | aarch64-linux-android28-clang++ -dM -E -x c++ - | grep -E '__linux__|__ANDROID__'
#define __ANDROID__ 1
#define __linux__  1
```

So `defined(__linux__)` is **not** "desktop Linux": every Linux-only guard in the tree also compiles
on Android, and any site that means *desktop* Linux does the desktop-Linux thing on a device where it
is wrong. On the **CMake** side this was already solved — `cmake/DetectPlatform.cmake` gives Android
`ZH_PLATFORM_POSIX` and never `ZH_PLATFORM_LINUX` — but those are CMake variables and do not reach
C++ (they appear **0 times** in C++ before this task). C++ has only the compiler macros to work with.

## What was already in place (Task 2.1, commit `bdb37a7`)

`zh_detect_platform()` (`cmake/DetectPlatform.cmake`) tests Android **first and on its own**:

```cmake
if(zh_system_name STREQUAL "Android" OR zh_android_flag)
  set(_zh_android TRUE)
elseif(zh_system_name STREQUAL "Darwin")
  ...
elseif(zh_system_name STREQUAL "Linux")
  set(_zh_linux TRUE)
endif()
```

`ZH_PLATFORM_ANDROID` is set, `ZH_PLATFORM_POSIX` stays TRUE, and `ZH_PLATFORM_LINUX` is never set
for Android. `Tests/test_platform_detection.cmake` checks the whole matrix (`platform_detection_check`).
Windows, Linux and macOS keep exactly the answers they had before. This task does **not** change that;
it builds the C++ half.

## The C++ half: `Platform/PlatformPredicates.h` (new)

`Libraries/Include/Platform/PlatformPredicates.h` is the C++ twin of `DetectPlatform.cmake`. It spells
the two predicates once, where C++ can read them:

| Predicate | Definition | Means |
|:--|:--|:--|
| `ZH_ANDROID` | `defined(__ANDROID__)` | the Android target (bionic, no X11/Wayland, no fontconfig, no `fork()`) |
| `ZH_DESKTOP_LINUX` | `defined(__linux__) && !defined(__ANDROID__)` | the Linux that has a desktop window server and the desktop-only system libraries |

Verified with the toolchains:

```
$ printf '#include "Platform/PlatformPredicates.h"\nZH_ANDROID ZH_DESKTOP_LINUX\n' | \
      g++ -I …/Libraries/Include -E -P -x c++ - | tail -1
0 1
$ … | aarch64-linux-android28-clang++ -I …/Libraries/Include -E -P -x c++ - | tail -1
1 0
```

The header is a header-only macro pair, so every target that already has `Libraries/Include` on its
include path (a directory-wide `include_directories()` at `CMakeLists.txt:123`, plus explicit
`PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/Libraries/Include` on the WWVegas targets) reaches it with no
CMake change.

## The classification of all 200 `__linux__` sites

`__linux__` appears **200 times in 55 files** across `GameEngine/`, `GameEngineDevice/`,
`Libraries/Source/WWVegas/`, `Libraries/Include/` and `Main/` (excluding `Tests/` and the new
header). The largest single site is `W3DDevice/GameClient/Water/W3DWater.cpp` (70).

Each site falls into one of the classes from doc 12:

| Class | Meaning | Treatment | Sites |
|:--|:--|:--|:--|
| **A** | Linux-kernel behaviour, also true on Android | keep `defined(__linux__)` | `posixpath.cpp` (inotify), `udp.cpp` (`SO_REUSEADDR`, `IP_PKTINFO`), `GameMemory.cpp` (`malloc_usable_size`), `EarlyCommandLine.h` (`/proc/self/cmdline`) |
| **B** | desktop-Linux intent | narrow to `ZH_DESKTOP_LINUX` | renderer feature toggles, the capture/timing harness, Flatpak/app-id, fontconfig, X11/Wayland, Steam Runtime |
| **C** | Linux code that is *wrong* on Android | add an `__ANDROID__` branch | `/proc/self/exe` as "the app's own path" |

The classification is not re-derived here; doc 12 lists every site. This task fixes the sites where
the wrong branch is either **proven to break the Android build** or **provably wrong at run time**,
and records the rest as follow-up (see *Left for later*).

### Proven by compiling for Android

Two independent sources of evidence were used, not assumption:

1. **The Task 2.2 keep-going log** (recorded in `01-target-architecture.md` §9): 132 compile errors
   from `<sys/timeb.h>`, an FFmpeg host/ARM link mismatch, and GameSpy's `pthread_cancel`.
2. **A fresh syntax-only sweep** run for this task: every first-party file that contains `__linux__`
   was compiled with the NDK r27c compiler (`aarch64-linux-android28-clang++`, the exact command
   from the Android `compile_commands.json`). Of the 33 engine/device files, **13 failed before any
   change**: 11 on `<sys/timeb.h>` reached through `PreRTS.h`, and 2 (`CrashHandlerPosix.cpp`,
   `PosixDevice9Draw.cpp`) on `backtrace`. The broader first-party sweep (37 files, including the
   WWVegas renderer files under `Libraries/Source`) shows the same shape.

After the `<sys/timeb.h>` fix (below), the same sweep leaves **exactly two** failures, both the
`backtrace()`/`execinfo.h` question that
[`../architecture/09-ndk-api-and-backtrace.md`](../architecture/09-ndk-api-and-backtrace.md) owns as
a separate task (`backtrace` is `__INTRODUCED_IN(33)`; the port is API 28).

## Changes made (all minimal, all guarded)

### 1. `Platform/PlatformPredicates.h` — new (see above)

### 2. `GameEngine/Include/Precompiled/PreRTS.h:117` — `sys/timeb.h` on Android

bionic has no `<sys/timeb.h>`. Nothing in the tree calls `ftime` or uses `struct timeb` — the only
occurrences of the token are this include and the Windows-side twin above it — so Android skips it:

```c
// <sys/timeb.h> (ftime, struct timeb) is a glibc/Darwin header Android's bionic does not have.  Nothing
// in the tree calls ftime - the only uses of "timeb" are this include - so Android skips it and loses
// nothing.  See docs/android-port/architecture/12-platform-macro-audit.md.
#if !defined(__ANDROID__)
#include <sys/timeb.h>
#endif
```

This is the single change that clears 13 of 15 first-party Android compile failures.

### 3. `GameEngine/Source/Common/System/ExecutableDirectory.cpp:43,64,67` — Class C

`readExecutablePath()` read `readlink("/proc/self/exe", …)` under `__linux__`. On Android that path is
the **zygote** (`/system/bin/app_process64`), not the app's own code (`libmain.so` loaded by
`SDLActivity`), so `getExecutableDirectory()` would return `/system/bin/`. The `__APPLE__`/`__linux__`
chain also left an `#else #error` gap for Android. The Linux branch is narrowed to `ZH_DESKTOP_LINUX`
and Android gets its own branch returning `""` — the value the function already documents as "cannot
be had whole", which the callers (MiniLog, MemoryInit) treat as "use the app-private directory".

### 4. `Main/PosixMain.cpp:449` — Flatpak app-id (Class B)

`#ifdef __linux__` → `#if ZH_DESKTOP_LINUX`. Android has no `.desktop` file and no `FLATPAK_ID`, and
`SDL_SetAppMetadata` is SDL's own concern there. The change compiles harmlessly on Android either way,
but the guard now says what it means.

### 5. `Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp:40` — fontconfig (Class B)

`#if !defined(__APPLE__) || defined(ZH_GLYPHS_USE_FONTCONFIG)` pulled in `<fontconfig/fontconfig.h>`,
which the NDK sysroot does not ship. Verified: the NDK fails on that include, and after the change the
file compiles clean for `aarch64-linux-android28`. The guard is now
`#if ZH_DESKTOP_LINUX || defined(ZH_GLYPHS_USE_FONTCONFIG)`, so Android takes the same no-fontconfig
(file-name) branch as macOS. Android's own font discovery is a separate item (doc 11); the comment
says so.

## Validation

| Check | Result |
|:--|:--|
| `platform_detection_check` (matrix, `cmake -P`) | unchanged, still the pre-existing pass |
| NDK syntax sweep, all first-party `__linux__` files | 15 failures → **2**, both `backtrace` (doc 09's task) |
| `glyphrasteriser.cpp`, `ExecutableDirectory.cpp`, `PosixMain.cpp`, `GameEngine.cpp` on **Linux** | `-fsyntax-only` clean; `glyphrasteriser` and `test_platform_predicates` also built and linked |
| `test_platform_predicates` on Linux | **passes** (via ctest; it is a user-data-guarded test) |
| `test_platform_predicates` for Android (NDK) | **builds and links** (`libmain`-shaped toolchain, arm64-v8a) |
| Android CMake configure | succeeds; `build.ninja` contains **0** `fontconfig` and **0** `fuse-ld` references, confirming the CMake separation holds |
| Predicate values across toolchains | desktop g++ `0 1`; aarch64 and x86_64 NDK both `1 0` |

## New test

`Tests/test_platform_predicates.cpp` (registered as `add_lib_test(test_platform_predicates)`) asserts
the host's own answer and the cross-host invariants (`ZH_ANDROID` and `ZH_DESKTOP_LINUX` are never
both 1; `ZH_DESKTOP_LINUX` is 0 whenever `__ANDROID__` is defined). On an Android build it is the
proof that the trap is closed; on desktop it pins the answer.

## Left for later (deliberately not changed here)

- **`backtrace()` / `<execinfo.h>`** in `StackDumpPosix.cpp`, `CrashHandlerPosix.cpp`,
  `PosixDevice9Timing.cpp`, `PosixDevice9Draw.cpp` — the swap to `_Unwind_Backtrace` + `dladdr` is
  [`../architecture/09-ndk-api-and-backtrace.md`](../architecture/09-ndk-api-and-backtrace.md)'s task.
  The sweep shows these are now the only two files that fail.
- **FFmpeg** (`Tools/ffmpeg-build-posix.sh` builds for the host; the arch axis is Apple-only) and
  **GameSpy's `pthread_cancel`** — both are doc 11 (dependency inventory) tasks, not platform
  detection.
- **The Class-B renderer feature gates** (`W3DWater.cpp` and the other ~190 sites): they are not
  build breakers and their correct Android answer (available, or desktop-harness-only) is the
  renderer/feature decision doc 12 defers. Narrowing them now, without that decision, would risk
  changing what the renderer does on desktop. They remain `defined(__linux__)` and are enumerated in
  doc 12.
- **`EarlyCommandLine.h`** `/proc/self/cmdline`: it compiles on Android and the SDL entry point
  passes the real arguments through `nativeRunMain`, so it is inert (doc 01 §9.1). No change.

## Files changed

| File | Change |
|:--|:--|
| `Libraries/Include/Platform/PlatformPredicates.h` | **new** — `ZH_ANDROID`, `ZH_DESKTOP_LINUX` |
| `GameEngine/Include/Precompiled/PreRTS.h` | guard `<sys/timeb.h>` with `!defined(__ANDROID__)` |
| `GameEngine/Source/Common/System/ExecutableDirectory.cpp` | `ZH_DESKTOP_LINUX` + new `ZH_ANDROID` branch |
| `Main/PosixMain.cpp` | `#ifdef __linux__` → `#if ZH_DESKTOP_LINUX` |
| `Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp` | fontconfig guard → `ZH_DESKTOP_LINUX` |
| `Tests/test_platform_predicates.cpp` | **new** — the predicate test |
| `CMakeLists.txt` | register `test_platform_predicates` |
| `BuildFingerprint.manifest` | regenerated (adds the two new files) |

No Linux, Windows or macOS behaviour changed: `ZH_DESKTOP_LINUX` is 1 on desktop Linux exactly where
`__linux__` was 1, and 0 everywhere else; on macOS and Windows the predicate header is inert
(`ZH_ANDROID` 0, `ZH_DESKTOP_LINUX` 0), and `sys/timeb.h` is still included because
`__ANDROID__` is undefined there.
