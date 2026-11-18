# Reference branch comparison — Zero-Hour-Reforged-Android `android/phase2-bootstrap`

Phase 2 of the reference-port task. This is the analysis of
`https://github.com/louay0khaled/Zero-Hour-Reforged-Android` branch
`android/phase2-bootstrap` (read-only) against this repository's branch
`agent/android-arm64-application-port`, with a classification for every candidate
component and the evidence behind each one.

Nothing in the reference repository was modified.

## 1. Reference branch and source SHA

| Fact | Value |
| --- | --- |
| Repository | `louay0khaled/Zero-Hour-Reforged-Android` (read-only) |
| Branch analysed | `android/phase2-bootstrap` |
| Branch tip SHA | `a8ab00a8d9df61c108e1f86577e299f75ce29c93` |
| Base commit (merge base with its `main`) | `b37515b18ab42d8f0de2dffd0677139ea521730a` |
| Reference `main` SHA | `b37515b18ab42d8f0de2dffd0677139ea521730a` |
| Commits unique to the branch | ~60 (see §5) |
| Diff vs base | 31 files, +797 / −90 |

The branch tip is *older* than the target in Android-application terms: the
reference is a **build-system bootstrap** (`android/README.md`: "This is a
build-system bootstrap, not a playable Android release yet"), while the target
already ships a full application layer (SAF folder picker, staging, lifecycle,
native runtime integration). Per the task rules, "newer" was not assumed; every
change was judged on its own evidence.

## 2. Target branch and target SHA

| Fact | Value |
| --- | --- |
| Repository | `redaf12-Decode/Generals-Reforged` |
| Branch | `agent/android-arm64-application-port` |
| Tip SHA at audit start | `6e4bdb0754d4602a3fc173d63e6e932c3ea86cd2` |
| Native Android target | `generals` → `libmain.so`, `arm64-v8a`, API 28, `c++_shared` |
| Android build type in CI | **Release** (`ci-android-test.yml`, `ci-android-app.yml`) |
| CI status at tip | "CI Android Test" success, "CI Android App" success, "Linux build and release" **failure** |

## 3. Reference architecture overview

- **Application**: a single `GeneralsReforgedActivity extends SDLActivity`
  (`android/app/src/main/java/org/reforged/generals/GeneralsReforgedActivity.java`)
  that loads `{"SDL3", "GeneralsReforged"}`. SDL3 is built as a **shared** library
  (`SDL_SHARED ON`), so the APK carries `libSDL3.so` plus the engine `.so`.
  Package id `org.reforged.generals`.
- **Gradle**: Kotlin DSL (`build.gradle.kts`), AGP driving CMake via
  `externalNativeBuild`, target `generals`, NDK r28c, `minSdk 28`, `arm64-v8a`
  only. SDL3's Java sources/resources are taken from the vendored SDL3 tree.
- **CMake**: introduces `ZH_PLATFORM_ANDROID`, forces `SDL_SHARED`/`SDL_VULKAN`,
  sets a `ZH_SDL3_TARGET` variable (`SDL3::SDL3-shared` on Android), forces
  GameSpy PIC by scoping `CMAKE_POSITION_INDEPENDENT_CODE` around the GameSpy
  subdirectory, adds `-fno-fast-math`, and builds a **host-side**
  `build_fingerprint` tool for cross-compilation.
- **Native fixes**: a set of small "make it compile under Clang/Android" edits —
  `_MSC_VER` guards on MSVC auto-link pragmas, `Int64` for `__int64`, `strcasecmp`
  for `stricmp`, `gethostname` for `GetComputerName`, `DEBUG_LOG` for
  `OutputDebugString`, `execinfo.h` guards, and an Android branch in the font
  resolver that reads `/system/fonts/`.
- **GameSpy**: a third vendor patch, `gamespy-android-no-pthread-cancel.patch`,
  that makes `gsiCancelThread` a no-op on Android.
- **CI**: one workflow, `android-build.yml`, which assembles a **debug** APK and
  uploads diagnostic logs.

## 4. Target architecture overview

- **Application**: two activities. `SetupActivity` is the launcher and runs the
  SAF folder picker; `ZeroHourActivity extends SDLActivity` loads only
  `{"main"}` because **SDL3 is linked statically** into `libmain.so`. Storage is
  handled by `GameFolderStore`, `GameFolderValidator`, `GameDataStaging` and
  `SafTreeStager`; the engine is rooted with `-root <staged path>`. Package id
  `io.github.olcayseygan.zerohourreforged`.
- **Gradle**: Groovy DSL (`android/app/build.gradle`). Gradle **does not** drive
  CMake; the native engine is cross-compiled by
  `scripts/build/android/build-android.sh` and staged by `package-android.sh`.
- **CMake**: already has a full `ZH_PLATFORM_ANDROID` platform (detected by
  `cmake/DetectPlatform.cmake`), an architecture detector
  (`cmake/DetectArchitecture.cmake`), the SDL3 static build, `Platform/StackWalk.h`
  (a `_Unwind_Backtrace` replacement for glibc's `execinfo`), a host-side
  fingerprint tool, GameSpy PIC handling, and an FFmpeg `--target-os=android`
  cross build.
- **CI**: three workflows — `ci-android-test.yml` (foundation: toolchain, ABI,
  dependency-graph assertions), `ci-android-app.yml` (native build + APK
  contents inspection), `linux-release.yml` (desktop).

The two repositories share the same upstream engine (a Reforged snapshot) but
have diverged substantially: the target's Android work is a superset in the
application and platform-integration layers.

## 5. Android-specific changes in the reference

Grouped by purpose. "Android-specific" means the commit message says so *and* the
diff is gated on Android/Clang/`__ANDROID__`; inherited engine edits are listed
separately.

**Build system (Android-specific)**

| Commit | Change |
| --- | --- |
| `ff06b1f6` | add ARM64 SDL3 shared-library target |
| `a348a332` | configure arm64 SDL3 native app |
| `f6c405ae`, `5a890a5d`, `725853e3` | SDL launcher manifest, app-label resource, SDLActivity subclass |
| `b93b3414`, `aa7b8db1`, `94ff02b1` | Gradle settings/plugin/properties |
| `913aca1e` | enforce ARM64-only Android configuration |
| `fd32f989`, `5203b23e`, `71959d9d`, `b5bd4cec`, `a8fdfe66` | NDK/API baseline docs |
| `15461737`, `c4d09764` | FFmpeg explicit cross target OS |
| `f7647841` | deterministic FP flags on the engine |
| `f6146f45` | require the SDL3 Vulkan backend |
| `7b12c3fb`, `a8ab00a8` | GameSpy PIC |
| `e6bde8c3` | run the fingerprint generator as a **host** tool |
| `75aea1fe`, `038288a3`, `dae1cd88` | GameSpy bionic thread patch |

**Clang/Android portability fixes to engine sources (Android-triggered)**

| Commit | File | Change |
| --- | --- | --- |
| `bd6fa157`, `af3eb191` | `PreRTS.h` | guard `sys/timeb.h` |
| `3196815b`, `963b6cc3`, `1f41958b`, `99430b08` | `GameEngine.cpp`, `SubsystemInterface.cpp`, `GameLogic.cpp` | `__int64` → `Int64` |
| `3d5910f4` | `INI.cpp` | `stricmp` → `strcasecmp` |
| `3d775a5f`, `328b7ad4` | `Recorder.cpp` | `GetComputerName` → `gethostname` |
| `1d19c4b6`, `723a8e18`, `55162fdd` | `GameMemory.cpp`, `debug.h`, `profile.h` | `_MSC_VER` guards on auto-link pragmas |
| `d04cf46c` | `OptionsMenuHotkeys.cpp` | `Common/File.h` → `Common/file.h` |
| `cefa1bc3` | `W3DInGameUI.cpp` | `ShaderClass::ShaderClass(SC_ALPHA)` → `ShaderClass(SC_ALPHA)` |
| `914c8e62`, `06ab636b` | `W3DView.cpp` | `static` forward decls; `OutputDebugString` → `DEBUG_LOG` |
| `f4a0c4fe` | `W3DRoadBuffer.cpp` | `OutputDebugString` → `DEBUG_LOG` |
| `84773058` | `glyphrasteriser.cpp` | Android `/system/fonts/` resolution |
| `6c1d12ff`, `72da28a8`, `9ae63d04`, `500cebcd` | `CrashHandlerPosix.cpp`, `StackDumpPosix.cpp`, `PosixDevice9Timing.cpp` | `execinfo.h` guards / Android fallback |

**Inherited from upstream (not an Android contribution)**: none of the engine
files' *content* is reference-authored except the guards above; the reference's
`CMakeLists.txt` Android block is reference-authored but the target has an
independent, more complete equivalent.

## 6. Existing equivalent functionality in the target

| Reference component | Target equivalent | Verdict |
| --- | --- | --- |
| `PreRTS.h` `sys/timeb.h` guard | Already guarded, with a fuller comment (`PreRTS.h:117-122`) | `ALREADY_IMPLEMENTED` |
| `execinfo` guards (`StackDumpPosix`, `CrashHandlerPosix`, `PosixDevice9Timing`) | `Platform/StackWalk.h` — a `_Unwind_Backtrace` unwinder that works on Android, used by all three | `ALREADY_IMPLEMENTED` (target is better) |
| `profile.h` `_MSC_VER` guard | Present, with the `.deplibs` explanation | `ALREADY_IMPLEMENTED` |
| `GameMemory.cpp` `_MSC_VER` guards | Present, same guard and rationale | `ALREADY_IMPLEMENTED` |
| GameSpy PIC (`7b12c3fb`, `a8ab00a8`) | `zh_android_gamespy_pic()` recursive target walk in `CMakeLists.txt` | `ALREADY_IMPLEMENTED` |
| GameSpy `pthread_cancel` patch | `gamespy-android-thread.patch` + `install_gamespy_android_thread_patch()` | `ALREADY_IMPLEMENTED` |
| Android platform detection | `cmake/DetectPlatform.cmake` + `ZH_PLATFORM_ANDROID` | `ALREADY_IMPLEMENTED` |
| Host-side `build_fingerprint` | `ZH_FINGERPRINT_HOST_TOOL` block | `ALREADY_IMPLEMENTED` |
| FFmpeg cross target OS | `ffmpeg-build-posix.sh` with `--target-os=android` and a sysroot | `ALREADY_IMPLEMENTED` |
| FFmpeg deterministic FP flags | `-ffp-contract=off` is unconditional on Clang/GNU | `ALREADY_IMPLEMENTED` |
| `Common/File.h` → `Common/file.h` | Target already has `Common/file.h` (case-correct) | `ALREADY_IMPLEMENTED` |
| `stricmp` → `strcasecmp` in `INI.cpp` | Target's `INI.cpp` has no `stricmp` and no `ModNamespace` (different snapshot) | `DO_NOT_PORT` (no such code) |
| Shared SDL3 + `{"SDL3","GeneralsReforged"}` Activity | Target links SDL3 statically into `libmain.so` and loads `{"main"}` | `DO_NOT_PORT` (would regress the target's design) |
| `MANAGE_EXTERNAL_STORAGE`-free but SDL-dialog storage | Target uses SAF + staging | `DO_NOT_PORT` |
| `android-build.yml` single debug-APK workflow | Target has `ci-android-test.yml` + `ci-android-app.yml` | `ALREADY_IMPLEMENTED` (target is broader) |
| `commit-messages.yml` legacy allowlist / `ci`/`style` types | Target's `commit-messages.yml` exists; the reference change is branch-local history hygiene | `DO_NOT_PORT` |

## 7. Missing functionality in the target (candidates for import)

Only the items below have no equivalent in the target. Each is analysed in §8.

1. **Android font resolution** — `glyphrasteriser.cpp::resolve()` has **no
   Android branch**. On Android it falls through to the macOS path and reads
   `/System/Library/Fonts/Supplemental/`, which does not exist on Android, so
   every substituted face fails to resolve and the game draws no text.
2. **`ShaderClass::ShaderClass(SC_ALPHA)`** — invalid direct-constructor call,
   inside `#ifdef _DEBUG` (`W3DInGameUI.cpp:225`).
3. **`__int64`** in `GameEngine.cpp`, `SubsystemInterface.cpp`, `GameLogic.cpp`,
   inside `DUMP_PERF_STATS`/`DO_UNIT_TIMINGS`.
4. **`OutputDebugString`** in `W3DView.cpp` and `W3DRoadBuffer.cpp`, inside
   `_DEBUG`/`_INTERNAL`.
5. **`drawDrawableExtents` / `drawAudioLocations` static mismatch** in
   `W3DView.cpp`, inside `_DEBUG`/`_INTERNAL`.
6. **`W3DDisplay.cpp` StatDumpClass `GetModuleFileName`** and
   `Get_Texture_Name()` `%s` misuse, inside `DUMP_PERF_STATS`/`_DEBUG`.
7. **`Recorder.cpp` `GetComputerName`**, inside `_DEBUG`/`_INTERNAL`.

## 8. Reusable components — classification and detail

### 8.1 `PORT` — Android font resolution

- **Reference file**: `GeneralsMD/Code/Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp`
  (`resolve()`, the `#elif defined(__ANDROID__)` branch; commit `84773058`).
- **Target file**: same path; `resolve()` at lines 252-291 has only the
  `ZH_GLYPHS_FONTCONFIG` and macOS `#else` arms.
- **Problem**: Android defines `__linux__` but not `ZH_DESKTOP_LINUX` (the
  target's `PlatformPredicates.h` makes Android's `ZH_DESKTOP_LINUX` 0), so
  `ZH_GLYPHS_FONTCONFIG` is 0 and the macOS `#else` runs. It builds
  `/System/Library/Fonts/Supplemental/Arial.ttf`, which does not exist on
  Android, so `fileExists()` is false and `resolve()` returns false for every
  face. This is a **real runtime defect on the target**, not a compile error.
- **Benefit**: the substituted faces (Arial→Roboto, Times New Roman→Noto Serif,
  Courier New→Droid Sans Mono, Arial Unicode MS→Noto Sans) resolve against
  `/system/fonts/`, so the game can draw text on a device.
- **Compatibility risks**: font file names differ across Android versions and
  vendors. The reference already mitigates this with a `fileExists()` check per
  candidate and a last-resort `Roboto-Regular.ttf`. The target's registered-font
  path (a game-supplied `LocalFontFile`) still runs first, so a player-supplied
  font wins, as before.
- **Required tests**: none automated on a host (the target's
  `test_glyph_rasteriser` is macOS/Linux-only and gated on those files); the
  branch decision is compile-time and can be checked by reading the preprocessed
  output for an Android configure. Device validation is required to confirm
  `/system/fonts/` contents.
- **Dependencies**: none added (no fontconfig, no bundled font files). Roboto,
  Noto and Droid Sans Mono are Android system fonts; nothing is redistributed.

### 8.2 `PORT` — Debug-build compile errors (latent; not compiled by the Release CI)

All of the following are inside `_DEBUG`/`_INTERNAL`/`DUMP_PERF_STATS`/
`DO_UNIT_TIMINGS`, none of which the target's Android Release CI defines. They
therefore do not break the current green CI, but they **are** genuine compile
errors that a Debug configuration would hit, and each is a one-line, mechanical,
behaviour-preserving fix proven by the reference. They are imported so a Debug
Android build is not dead on arrival.

| # | Reference file & section | Target file & line | Problem | Benefit | Risk | Test |
| --- | --- | --- | --- | --- | --- | --- |
| a | `W3DInGameUI.cpp` `DebugHintObject::initData` (`cefa1bc3`) | `W3DInGameUI.cpp:225` | `ShaderClass::ShaderClass(SC_ALPHA)` calls a constructor directly — an error under GCC/Clang (verified: `cannot call constructor 'S::S' directly`) | compiles | none (identical semantics) | none (guarded) |
| b | `GameEngine.cpp`, `SubsystemInterface.cpp`, `GameLogic.cpp` (`3196815b` … `99430b08`) | same, `__int64` sites | `__int64` is an MSVC spelling; Clang rejects it as a type name (verified with g++) | compiles | none (`Int64` = `int64_t` = the same type) | none (guarded) |
| c | `W3DView.cpp`, `W3DRoadBuffer.cpp` (`914c8e62`, `f4a0c4fe`) | same, `OutputDebugString` sites | `OutputDebugString` is a Win32 API with no Android shim | compiles; routes through the engine's own log | none | none (guarded) |
| d | `W3DView.cpp` (`914c8e62`) | `W3DView.cpp:1513,1623` | non-static declaration, later defined `static` — an error under GCC/Clang (verified) | compiles | none | none (guarded) |
| e | `W3DDisplay.cpp` (`06ab636b`) | `W3DDisplay.cpp:217-222`, `5362/5370` | `GetModuleFileName` has no Android shim; `%s` on a `StringClass` is a type mismatch | compiles | none | none (guarded) |
| f | `Recorder.cpp` (`3d775a5f`, `328b7ad4`) | `Recorder.cpp:117/165/200/238` | `GetComputerName` is a Win32 API with no Android shim | compiles; host name still recorded | none | none (guarded) |

For (b) the target's `Debug.h` and `wwprofile.h` already define an `Int64`
typedef/`__int64` under `_UNIX`, but the engine files include `PreRTS.h` →
`Lib/BaseType.h`, which provides `Int64` unconditionally, so `Int64` is the
portable choice.

### 8.3 `ADAPT` — font resolution hardening

The reference's `/system/fonts/` names are taken verbatim. The adaptation is to
keep them behind the target's own `ZH_ANDROID` predicate (from
`Platform/PlatformPredicates.h`) rather than the reference's `defined(__ANDROID__)`,
so the branch is decided the same way the rest of the target decides Android, and
to leave the target's `metricsFace = NULL` semantics (Android has no GDI metric
equivalent to claim) exactly as the reference does.

## 9. Incompatible components

| Component | Why not imported |
| --- | --- |
| Shared SDL3 (`SDL_SHARED ON`, `ZH_SDL3_TARGET = SDL3::SDL3-shared`) | The target deliberately links SDL3 statically into `libmain.so` and the `ZeroHourActivity` doc comment explains why. Changing it would touch the whole link graph and the Activity. |
| Reference `GeneralsReforgedActivity` / manifest | The target has a richer two-activity design with SAF. Replacing it is explicitly forbidden. |
| `ZH_SDL3_TARGET` variable indirection | Unneeded: the target is static-only off Windows. |
| GameSpy PIC via scoped `CMAKE_POSITION_INDEPENDENT_CODE` | The target already has a stronger recursive fix. |
| `android-build.yml` | Replaced by the target's two more capable workflows. |
| `SDL_DIALOG OFF` on Android | The target already excludes the dialog dependency and uses SAF. |
| Reference `Recorder`/`INI`/`OptionsMenuHotkeys` file-level rewrites | Different snapshot; the target's files differ by hundreds of lines. Only the specific guarded lines are relevant. |

## 10. Security and stability risks

- **Fonts**: no proprietary font is bundled. Only system-font *file names* are
  referenced and each is probed with `fileExists()` before use. No new file
  reads outside `/system/fonts/`.
- **`gethostname`**: returns the device host name into a bounded buffer; the
  reference null-terminates defensively and the target keeps that.
- **`OutputDebugString` → `DEBUG_LOG`**: the engine's log is already used in the
  same files; no new output sink.
- **`__int64` → `Int64`**: no width change.
- **No new dependency** is introduced by any imported item, so there is no new
  supply-chain or licensing exposure.
- **Not a runtime validation**: a green APK build does not prove the game runs;
  see §12.

## 11. Dependency and licensing implications

- Imported items add **no** libraries and **no** redistributed files.
- Font names (`Roboto-Regular.ttf`, `Roboto-Bold.ttf`, `NotoSerif-*.ttf`,
  `DroidSansMono.ttf`, `NotoSans-*.ttf`) are Android system fonts. The change
  reads them from the device; it does not ship them.
- `gethostname` is POSIX/bionic; no new header beyond `<unistd.h>`.

## 12. Recommended implementation order

1. Android font resolution (§8.1) — the only item that affects a device run.
2. Debug-build compile fixes (§8.2) — mechanical, low risk, unblock Debug.
3. Report, changelog, commit, push; run `ci-android-test.yml` and
   `ci-android-app.yml`.

Items deliberately left untouched: shared SDL3, the application layer, CI
workflows, GameSpy, FFmpeg, and every file that differs from the reference by
more than the specific guarded lines.

## 13. Not selected — `NEEDS_INVESTIGATION`

- **`OutputDebugString` in `Debug.cpp:432` and `NGMP_Helpers.cpp:195-196`.** These
  are not in the reference diff and are reached in non-`_DEBUG` builds. The
  target compiles today (Release CI green), so there is presumably a shim or a
  platform guard already in place for those specific translation units; this was
  not fully traced and is out of scope for the reference port. Left untouched.

## Implementation Results

### Imported reference components

| Reference commit | Reference file | Target file | Classification |
| --- | --- | --- | --- |
| `84773058` | `glyphrasteriser.cpp` | `glyphrasteriser.cpp` | `PORT` (adapted predicate) |
| `cefa1bc3` | `W3DInGameUI.cpp` | `W3DInGameUI.cpp` | `PORT` |
| `3196815b`, `963b6cc3`, `1f41958b`, `99430b08` | `GameEngine.cpp`, `SubsystemInterface.cpp`, `GameLogic.cpp` | same | `PORT` |
| `914c8e62`, `f4a0c4fe` | `W3DView.cpp`, `W3DRoadBuffer.cpp` | same | `PORT` |
| `06ab636b` | `W3DDisplay.cpp` | `W3DDisplay.cpp` | `PORT` |
| `3d775a5f`, `328b7ad4` | `Recorder.cpp` | `Recorder.cpp` | `PORT` |

### Adapted components

- Font resolution: gated on the target's `ZH_ANDROID` rather than
  `defined(__ANDROID__)`, and kept inside the target's existing `resolve()`
  structure.

### Components intentionally not imported

Shared SDL3, `ZH_SDL3_TARGET`, the reference Activity/manifest, GameSpy PIC and
thread patch, FFmpeg cross flags, the `android-build.yml` workflow,
`OptionsMenuHotkeys.cpp` case fix, `INI.cpp` `strcasecmp` change, and the
`commit-messages.yml` history allowlist — each already implemented, inapplicable
to the target's snapshot, or a design regression (see §6, §9).

### Files modified

- `GeneralsMD/Code/Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp`
- `GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DInGameUI.cpp`
- `GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp`
- `GeneralsMD/Code/GameEngine/Source/Common/System/SubsystemInterface.cpp`
- `GeneralsMD/Code/GameEngine/Source/GameLogic/System/GameLogic.cpp`
- `GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DView.cpp`
- `GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DRoadBuffer.cpp`
- `GeneralsMD/Code/GameEngineDevice/Source/W3DDevice/GameClient/W3DDisplay.cpp`
- `GeneralsMD/Code/GameEngine/Source/Common/Recorder.cpp`

### Dependencies added or changed

None.

### Tests executed

See `REFERENCE-PORT-CHANGELOG.md`. Host-side `g++` reproductions confirmed each
compile-level defect; the Android CI workflows were run on the pushed branch.

### CI workflow results

Recorded in `REFERENCE-PORT-CHANGELOG.md` after the push.

### Remaining issues

- The "Linux build and release" workflow fails at `test_vendor_no_art.sh`
  because that test's fixture does not copy the `*.patch` files that
  `vendor.sh` applies. This is a **pre-existing target defect unrelated to the
  reference port** (the test predates the reference analysis); it is documented
  here and left for a separate fix unless required by this task.

### Features that still require physical-device validation

- Font rendering on a real device (the `/system/fonts/` file names differ across
  Android versions and vendors).
- The game actually launching and drawing (an APK build is not a runtime test).
