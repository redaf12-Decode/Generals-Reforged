# 12 — Platform preprocessor audit: `__linux__`, `__APPLE__`, `_WIN32`, `__ANDROID__`

Resolves **question 7 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("How should the existing `__linux__` preprocessor conditions be handled on Android, which defines
`__linux__` but is not desktop Linux?"). Read-only: no source, build or dependency file was changed.
No code was changed — the implementation is Phase 2.

## The finding that drives everything

**The NDK's Clang defines `__linux__` for Android targets.** Measured:

```
$ echo | aarch64-linux-android28-clang -dM -E -x c - | grep -E '__linux__|__ANDROID__'
#define __ANDROID__ 1
#define __linux__  1
```

The same holds for `clang++` (C++). So **every one of the 200 `__linux__` sites in the engine,
device and WWVegas sources compiles its Linux branch on Android.** There is no "the Linux code is
not compiled on Android" assumption to rely on: any `__linux__` site that means *desktop* Linux will
run on Android unless it is narrowed.

The target already anticipated this on the **CMake** side — `cmake/DetectPlatform.cmake` gives
Android `ZH_PLATFORM_POSIX` and never `ZH_PLATFORM_LINUX`, so X11/Wayland, fontconfig, LLD/mold and
the Steam Runtime packaging stay out of an Android build. But those are **CMake** macros
(`ZH_PLATFORM_*` appear **0 times** in C++ — verified), so they do **not** protect C++ code. In C++
the only platform macros in play are `__linux__`, `__APPLE__`, `_WIN32` and `__ANDROID__`.

The reference port hit exactly this and documented it
(`redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`, `GeneralsMD/Code/Main/SDL3Main.cpp:260-263`):
*"Android defines `__linux__` too, but has neither PipeWire nor PulseAudio — forcing
`ALSOFT_DRIVERS=pulse,alsa` here would silently mute the device… Desktop Linux only"*, guarded
`#if defined(__linux__) && !defined(__ANDROID__)`. **That is the pattern this decision adopts.**

## Decision

In C++, `__linux__` must **not** be treated as "desktop Linux". The 200 sites fall into three
classes, and each is handled as follows:

| Class | Meaning | Treatment |
|:--|:--|:--|
| **A — Linux-kernel behaviour, also true on Android** | inotify, `SO_REUSEADDR`/broadcast, `malloc_usable_size`, `fcntl`/`readlink`, `/proc` best-effort | **Keep `defined(__linux__)`** — correct on Android as written |
| **B — desktop-Linux intent** | desktop renderer feature toggles, desktop capture/timing harness hooks, Flatpak/app-id, PipeWire/OpenAL, X11/Wayland, fontconfig | **Narrow to `defined(__linux__) && !defined(__ANDROID__)`** (or a named predicate) |
| **C — Linux code that is *wrong* on Android** | `/proc/self/exe` as "the app's own path" | **Add an `__ANDROID__` branch** |

The recommended mechanism, consistent with the build's own design, is to keep using the compiler's
`__linux__`/`__ANDROID__` in C++ (a `ZH_PLATFORM_*` C++ macro would duplicate what
`DetectPlatform.cmake` already decides, and CMake already decides the source set). Where a site
means "desktop Linux", spell it `defined(__linux__) && !defined(__ANDROID__)`; where it means
"anywhere the SDL3-GPU renderer runs", use the existing `ZH_MODERN_WATER`-style predicate
(`Libraries/Include/Platform/ModernWaterBuild.h`) rather than `__linux__` directly.

## Evidence by site

### Class A — works on Android unchanged (keep `__linux__`)

- **`Libraries/Source/WWVegas/WWLib/posixpath.cpp:33,90,118`** — `inotify` for directory-change
  notification. Bionic **has** `<sys/inotify.h>` at API 28 (`inotify_init1`,
  `inotify_add_watch` present, no `__INTRODUCED_IN` gate). The code already degrades gracefully:
  `poll_directory_changes()` returns `fd >= 0` and `watch_directory()` returns `false` if the watch
  fails (`:109,124`), and `listing_of` rescans when notifications are unavailable (`:203,223`). So
  even a kernel that refuses inotify (or an SELinux policy that does) just falls back to rescanning —
  the correct posture for Android.
- **`GameEngine/Source/GameNetwork/udp.cpp:206,423`** — `SO_REUSEADDR` semantics and limited-broadcast
  delivery are Linux *kernel* behaviour, identical on Android. Correct as written.
- **`GameEngine/Source/Common/System/GameMemory.cpp:58,280`** — `malloc_usable_size` from
  `<malloc.h>`. Bionic provides it (`malloc.h:117`). Works.
- **`GameEngine/Include/Common/EarlyCommandLine.h:105`** — `<fcntl.h>`/`<unistd.h>`. Works.
- **`GameEngine/Source/Common/System/CrashHandlerPosix.cpp`**, **`StackDumpPosix.cpp`** — `backtrace`
  (Class C in the API sense; see [doc 09](09-ndk-api-and-backtrace.md), which is the separate
  backtrace question). These are handled there, not here.

### Class B — desktop-Linux intent (narrow the guard)

These sites mean "the desktop Linux build" or "the desktop renderer's opt-in features". They compile
on Android today and would either enable a desktop-only feature or run desktop-only instrumentation:

- **Renderer feature toggles gated on `__linux__`** (the Modern water and sun-shadow paths):
  `GameEngineDevice/Source/W3DDevice/GameClient/Water/W3DWater.cpp` (70 sites),
  `.../W3DWaterTracks.cpp`, `.../Water/W3DWaterPathScene.{h,cpp}`, `.../W3DVolumetricShadow.cpp`
  (11), `.../W3DSnow.cpp` (10), `.../W3DShaderManager.cpp`, `.../W3DView.cpp`, `.../W3DDisplay.cpp`,
  `.../WorldHeightMap.cpp`, `Libraries/Source/WWVegas/WW3D2/engineshader.cpp` (4),
  `dx8wrapper.{cpp,h}` (5), `SdlGpuFrame.cpp:2067`, `SdlProgramCache.cpp:298`, and the
  `Libraries/Include/Platform/{Water*,SunShadowMode,ModernWaterBuild}.h` headers. **Decision:**
  Android runs the same SDL3-GPU renderer as Linux (doc 07), so these features should be **available**
  on Android — but the guard should say so explicitly. Use `ZH_MODERN_WATER` (already
  `__linux__ || _WIN32`) or a new `ZH_SDL_GPU_RENDERER` predicate for the feature set, and
  `defined(__linux__) && !defined(__ANDROID__)` for anything that is genuinely a *desktop harness*
  knob (e.g. `ZH_WATER_CAPTURE_CLOCK`, `ZH_SUN_SHADOW_MODE` env reads in
  `SunShadowMode.h:23`, `WaterTuning.h:61,205`, `WaterCleanProfile.h:30`,
  `WaterCleanSpectrum.h:22`, `WaterFftSampling.h:40,407,416,1566`). The `-d3d9`/headless tests stay
  as they are.
- **Desktop load-timing / capture instrumentation**: `GameEngine/Source/GameLogic/System/GameLogic.cpp:87,1317,2480`,
  `GameEngine/Source/Common/System/SaveGame/GameState.cpp:60,767,841,1745`,
  `GameEngine/Source/GameLogic/System/ScenarioDrill.cpp:2145,2239`,
  `GameEngine/Source/Common/GameEngine.cpp:1761,1783`. These call `PosixGpuTimingLoadMarker`,
  `GameEngine_isShadowCaptureClock`, `WaterCaptureClock` — the desktop capture/benchmark harness.
  **Decision:** keep them desktop-only (`&& !defined(__ANDROID__)`); Android does not need the
  frame-capture clock, and running it would only add overhead.
- **Desktop UI/hotkey surface**: `GameEngine/Include/Common/MessageStream.h:320`,
  `GameEngine/Include/GameClient/HotKey.h:217`, `GameEngine/Source/GameClient/MessageStream/{CommandXlat.cpp:58,3912,MetaEvent.cpp:202,691,HotKey.cpp:927}`.
  These add `MSG_META_CYCLE_WATER_REFLECTION`, its default keybinding, and the
  `HOTKEYS_FIXED_ORDER_KEYS_DEFAULT = FALSE` desktop default. **Decision:** the water-reflection
  cycle is a renderer feature, so it may stay on Android (or be dropped — a UI call); the
  **hotkey default** (`FALSE` on Linux vs `TRUE` elsewhere, `HotKey.h:217`) is a desktop key-mapping
  decision and Android should take the **`FALSE`** (desktop) value or its own — this is a gameplay-UI
  decision to settle in Phase 2, not an architecture blocker. Either way the guard should be explicit.
- **`Main/PosixMain.cpp:448`** — `SDL_SetAppMetadata` with the Flatpak id. Already
  `#if defined(__linux__)`; on Android there is no Flatpak and no `.desktop` file, so this must be
  `&& !defined(__ANDROID__)` (or the Android branch omitted). It already compiles harmlessly, but the
  intent must be narrowed.
- **`Libraries/Source/WWVegas/WW3D2/dx8wrapper.cpp:106`** — includes `Platform/RendererName.h`
  under `__linux__`; `engineshader.cpp:684` adds `WATER_ANALYTIC_ELISION`. Same renderer-feature
  class as above.

### Class C — Linux code that is wrong on Android (add an `__ANDROID__` branch)

- **`GameEngine/Source/Common/System/ExecutableDirectory.cpp:42,61`** —
  `readExecutablePath` reads `readlink("/proc/self/exe", …)`. On Android `/proc/self/exe` is the
  **zygote/app_process** image (`/system/bin/app_process64`), **not** the app's own code — the app's
  code is `libmain.so` loaded by `SDLActivity` (doc 10). So `getExecutableDirectory()` would return
  `/system/bin/` on Android. Callers: `Main/PosixMain.cpp:294,398` (install-root step 5 — not reached
  on Android because the picker runs first, doc 08), **`GameEngine/Source/Common/MiniLog.cpp:42`**
  (the log file's directory) and `GameEngine/Source/Common/System/MemoryInit.cpp:777`. **Decision:**
  give `readExecutablePath` an `#elif defined(__ANDROID__)` branch returning `""` (the function
  already documents `""` as "cannot be had whole"), and let Android's callers use app-private storage
  instead (doc 08). This is the one site where the Linux code is actively wrong on Android.

## Verification of the classification

- `__linux__` total in engine/device/WWVegas/`Libraries/Include` (excluding the vendored
  `GameNetwork/GeneralsOnline/json.hpp`, which is a third-party header, and `Tests/`): **200**, in 54 files.
- `__APPLE__` sites: confined to the same families (path/executable handling, renderer, water); same
  three-class treatment, but Android never defines `__APPLE__`, so no `__APPLE__` site runs on
  Android — **no action needed** beyond ensuring no `#elif defined(__APPLE__)` chain leaves an
  Android gap (the `ExecutableDirectory.cpp` chain does, hence Class C).
- `_WIN32` sites: Android never defines `_WIN32`, so Windows branches are inert — **no action**.
- `__ANDROID__` in the target today: **0** occurrences outside vendored libraries. All Android
  branches are Phase-2 work.
- `_UNIX`: the port forbids it (`Platform/MSVCCompat.h:53` `#error`), so it is not a tool here.

## Recommended predicate set (Phase 2)

To make the three classes readable and hard to get wrong, introduce two small predicates in one
header (e.g. `Libraries/Include/Platform/PlatformPredicates.h`), used instead of raw `__linux__`:

- `ZH_DESKTOP_LINUX` → `defined(__linux__) && !defined(__ANDROID__)` — Class B.
- `ZH_HAS_SDL3_GPU_RENDERER` → `defined(__linux__) || defined(__ANDROID__) || defined(_WIN32)` (the
  `ZH_MODERN_WATER` shape, widened) — Class A renderer features.
- Class A kernel behaviour may keep `defined(__linux__)` directly, or use a `ZH_LINUX_KERNEL`
  predicate for symmetry.

This is a **naming/readability** change plus the Class-C `ExecutableDirectory` fix; it introduces no
Android implementation and changes no behaviour on Windows/macOS/Linux.

## Open items handed to Phase 2

1. Add the `__ANDROID__` branch to `ExecutableDirectory.cpp` (return `""`) and route Android's log
   path to app-private storage.
2. Sweep the Class B sites and narrow `__linux__` → `ZH_DESKTOP_LINUX` (or
   `&& !defined(__ANDROID__)`); decide per feature whether Android keeps it (renderer features) or
   not (capture harness, Flatpak app-id, PipeWire/OpenAL).
3. Settle the two UI calls: the water-reflection keybinding and `HOTKEYS_FIXED_ORDER_KEYS_DEFAULT`
   on Android.
4. Consider the predicate header above so future ports cannot reintroduce the "`__linux__` means
   desktop" trap.

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- `__linux__`/`__ANDROID__` macro facts measured with `aarch64-linux-android28-clang -dM -E` against
  NDK r27c (output quoted above); bionic header facts from the same sysroot.
- Reference-port claims cite `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`,
  `GeneralsMD/Code/Main/SDL3Main.cpp` (design guidance only).
- Read-only: no source, build or dependency file was changed.
