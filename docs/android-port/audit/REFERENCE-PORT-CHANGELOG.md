# Reference port changelog

Each entry records one change imported from
`louay0khaled/Zero-Hour-Reforged-Android` branch `android/phase2-bootstrap`
(tip `a8ab00a8d9df61c108e1f86577e299f75ce29c93`) into this repository's branch
`agent/android-arm64-application-port`. The reference repository was read only.

The reference *branch* tip is `a8ab00a8`; the individual changes were authored in
earlier commits on that branch, named below.

## 1. Android font resolution

- **Reference commit**: `84773058` (`glyphrasteriser.cpp`, `#elif defined(__ANDROID__)`).
- **Reference file**: `GeneralsMD/Code/Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp`
  (`resolve()`).
- **Target file**: same path.
- **Reason**: `resolve()` had no Android branch. Android defines `__linux__` but the
  target's `ZH_DESKTOP_LINUX` is 0 on Android, so `ZH_GLYPHS_FONTCONFIG` is 0 and
  the macOS branch ran, reading `/System/Library/Fonts/Supplemental/`. That
  directory does not exist on Android, so every substituted face failed to
  resolve and the game drew no text.
- **Adaptation**: the branch is gated on the target's `ZH_ANDROID`
  (`Platform/PlatformPredicates.h`) instead of the reference's
  `defined(__ANDROID__)`, so it is decided the same way as the rest of the target.
  The body is byte-for-byte the reference's: `/system/fonts/` candidates per
  substitution family, each probed with `fileExists()`, with `Roboto-Regular.ttf`
  as the last resort. A language-registered font still wins, as before.
- **Validation**: the block was extracted and compiled standalone with
  `g++ -std=c++17 -Wall -Wextra` (clean). The branch is compile-time and only
  taken on an Android configure; runtime font availability needs a device.

## 2. `ShaderClass` direct-constructor call

- **Reference commit**: `cefa1bc3`.
- **Reference file**: `GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DInGameUI.cpp`.
- **Target file**: same path, line 225.
- **Reason**: `m_shaderClass = ShaderClass::ShaderClass(SC_ALPHA);` calls a
  constructor directly, which GCC/Clang reject (reproduced: `cannot call
  constructor 'S::S' directly`). Inside `#ifdef _DEBUG`, so the Release CI never
  saw it.
- **Adaptation**: none (`ShaderClass::ShaderClass(SC_ALPHA)` → `ShaderClass(SC_ALPHA)`).
- **Validation**: reproduced the error with `g++`; the fix is the reference's exact
  line. Guarded by `_DEBUG`, so the Release CI link is unchanged.

## 3. `__int64` → `Int64`

- **Reference commits**: `3196815b`, `963b6cc3`, `1f41958b`, `99430b08`.
- **Reference files**: `GameEngine.cpp`, `SubsystemInterface.cpp`, `GameLogic.cpp`.
- **Target files**: same paths (15 occurrences).
- **Reason**: `__int64` is an MSVC spelling; Clang rejects it as a type name
  (reproduced). All sites are inside `DUMP_PERF_STATS`/`DO_UNIT_TIMINGS`.
- **Adaptation**: none — `Int64` is `int64_t` from `Lib/BaseType.h`, the same
  type, and is what the reference used.
- **Validation**: no occurrences of `__int64` remain in the three files; the type
  is identical, so the change is behaviour-preserving.

## 4. `OutputDebugString` → `DEBUG_LOG`

- **Reference commits**: `914c8e62`, `f4a0c4fe`.
- **Reference files**: `W3DView.cpp`, `W3DRoadBuffer.cpp`.
- **Target files**: same paths (`W3DView.cpp:1716`, `W3DRoadBuffer.cpp:1475,1528`).
- **Reason**: `OutputDebugString` is a Win32 API with no Android shim; those sites
  are inside `_DEBUG`/`_INTERNAL`.
- **Adaptation**: none — `DEBUG_LOG` is already used in both files.
- **Validation**: `DEBUG_LOG` is defined in `Common/Debug.h`, which both files
  already reach (both use it elsewhere). Guarded, so the Release build is
  unchanged.

## 5. `W3DView` static forward declarations

- **Reference commit**: `914c8e62`.
- **Reference file**: `W3DView.cpp`.
- **Target file**: same, lines 1513 and 1623.
- **Reason**: `drawDrawableExtents` and `drawAudioLocations` were declared
  non-static but defined `static`, which GCC/Clang reject (reproduced:
  `declared 'extern' and later 'static'`). Inside `_DEBUG`/`_INTERNAL`.
- **Adaptation**: none.
- **Validation**: reproduced the error with `g++`; both declarations now say
  `static`, matching their definitions.

## 6. `W3DDisplay` `StatDumpClass` and `%s` misuse

- **Reference commit**: `06ab636b`.
- **Reference file**: `W3DDisplay.cpp`.
- **Target file**: same, lines 217-241 and 5366/5374.
- **Reason**: `StatDumpClass` called `GetModuleFileName`, a Win32 API with no
  Android shim (inside `DUMP_PERF_STATS`); `fprintf(..., "%s", texture->Get_Texture_Name())`
  passes a `StringClass` to `%s`, a type mismatch (inside `_DEBUG`/`_INTERNAL`).
- **Adaptation**: the Android branch is gated on the target's `ZH_ANDROID` and
  `Platform/PlatformPredicates.h` is included for it; the reference used
  `defined(__ANDROID__)`. The `%s` fix uses `.Peek_Buffer()`, as the reference does.
- **Validation**: `Get_Texture_Name()` returns `const StringClass &`, so
  `.Peek_Buffer()` is the correct conversion; the include was added for the
  `ZH_ANDROID` macro.

## 7. `Recorder` computer name

- **Reference commits**: `3d775a5f`, `328b7ad4`.
- **Reference file**: `Recorder.cpp`.
- **Target file**: same, four sites (lines 115-117, 163-165, 198-200, 236-238).
- **Reason**: `GetComputerName` is a Win32 API with no Android shim; the sites are
  inside `_DEBUG`/`_INTERNAL` (three of them) and one is unconditional.
- **Adaptation**: none — the reference's `recorderGetComputerName()` helper
  (Windows `GetComputerName`, else `gethostname` from `<unistd.h>`) is imported
  as written.
- **Validation**: `<unistd.h>` provides `gethostname` on bionic; the buffer is
  null-terminated defensively. The Windows path is unchanged.

## Not imported

See `REFERENCE-BRANCH-COMPARISON.md` §6 and §9. In brief: shared SDL3 and the
reference Activity/manifest (a design regression against the target's static
SDL3 + SAF design), `ZH_SDL3_TARGET`, GameSpy PIC and the `pthread_cancel`
patch (already implemented), `ffmpeg-build-posix.sh` cross flags (already
implemented), `android-build.yml` (superseded by the target's two workflows),
`OptionsMenuHotkeys.cpp` case fix and `INI.cpp` `strcasecmp` (target's snapshot
has no such code), and the `commit-messages.yml` history allowlist.

## Dependency changes

None.

## CI validation

Commit `6fecf3c`, pushed to `agent/android-arm64-application-port`:

- **CI Android Test** (`44326606243`): **success**. Configured for `arm64-v8a`,
  API 28, `c++_shared`, and compiled a bounded target set under the NDK Clang.
- **CI Android App** (`44326484631`): **success**. Built `libmain.so`, audited
  its ELF dependencies, assembled the APK and inspected its structure, ABI and
  manifest, and ran the game-folder unit tests.

Both workflows compiled the changed sources; the new `glyphrasteriser.cpp`
Android branch and the ported `Recorder.cpp`/`W3DDisplay.cpp` code are on the
paths the App workflow built. The engine fixes inside `_DEBUG`/`DUMP_PERF_STATS`
are not compiled by the Release CI, so they are covered by the host `g++`
reproductions in §2-§6 rather than by CI.

Not validated: a device run. The font branch is only exercised on Android at
runtime, and no APK build proves the game starts or draws.
