# Phase 1 — Native game engine target and standalone SDL3 shared library

Phase 1 of the Android port. It makes two artifacts instead of one:

| Artifact | Built from | Kind | Carries |
|:--|:--|:--|:--|
| `libmain.so` | the `generals` target (`Main/PosixMain.cpp`) | shared library, `OUTPUT_NAME main` | the whole engine, exporting `SDL_main` |
| `libSDL3.so` | the `SDL3-shared` target of the vendored SDL3 3.4.16 tree | shared library, `OUTPUT_NAME SDL3` | SDL3, exporting `JNI_OnLoad` and the `SDL_*` API |

Nothing else in the build changed: the desktop keeps its single executable and its statically linked
SDL3, and no renderer, audio or game code was touched.

Read first, as required: the Phase 0 audit —
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md) (the target's real CMake
shape), [`../audit/02-reference-architecture.md`](../audit/02-reference-architecture.md) (the
reference port's `libmain.so` + `SDL3Main.cpp` model), and
[`../architecture/10-android-entry-point.md`](../architecture/10-android-entry-point.md) (the
`SDL_main` decision this phase builds on).

## 1. The native game target

### 1.1 What it actually is

The game target is **`generals`**. It is declared once, in
`GeneralsMD/Code/CMakeLists.txt`, inside the POSIX block, and its *kind* is the platform's decision
(`CMakeLists.txt:2313-2332`):

```cmake
if(ZH_PLATFORM_ANDROID)
  add_library(generals SHARED ${CMAKE_CURRENT_SOURCE_DIR}/Main/PosixMain.cpp)
  set_target_properties(generals PROPERTIES OUTPUT_NAME main)
else()
  add_executable(generals ${CMAKE_CURRENT_SOURCE_DIR}/Main/PosixMain.cpp)
endif()
target_include_directories(generals PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/Main ${GENERATED})
set_target_properties(generals PROPERTIES CXX_STANDARD 17)
target_link_libraries(generals PRIVATE sdldevice installroot ${ZH_SDL3_TARGET})
```

| Platform | Target name | Kind | Artifact |
|:--|:--|:--|:--|
| Windows | `generals` (`WIN32` executable, `Main/WinMain.cpp`, `CMakeLists.txt:2153`) | executable | `generals.exe` |
| macOS / desktop Linux | `generals` | executable | `generals` |
| Android | `generals` | **shared library** | **`libmain.so`** |

The target keeps the name `generals` on every platform so that the 15 references to
`$<TARGET_FILE:generals>`, the test registrations and the compile definitions below it are unchanged;
only the artifact differs. `add_library(generals SHARED)` with `OUTPUT_NAME main` yields
`libmain.so`, which is SDL3's own default for `SDLActivity` (`SDLActivity.java:268-275`: the main
shared object is `lib` + the last entry of `getLibraries()` + `.so`).

### 1.2 SDL entry point, and `SDL_main` defined exactly once

`Main/PosixMain.cpp` includes `<SDL3/SDL_main.h>` as its first include (`PosixMain.cpp:47`) and
defines one function, `int main(int argc, char *argv[])` (`PosixMain.cpp:469`). On Android
`SDL_main.h` takes the `SDL_PLATFORM_ANDROID` branch:

```c
#define SDL_MAIN_NEEDED      /* the Java class is the entry point */
#define SDL_MAIN_EXPORTED    /* export SDL_main, emit no native main() */
```

which makes `SDLMAIN_DECLSPEC` `SDL_DECLSPEC` (default visibility, so the symbol is exported) and
`#define main SDL_main`. Consequences, all verified in the tree rather than assumed:

- **`SDL_main` is defined exactly once.** `PosixMain.cpp` is the only translation unit in the tree
  that includes `SDL_main.h` (`git grep 'SDL_main.h'` → one hit) and the only one that defines
  `main` (`git grep 'int main'` over `Main/` → one hit).
- **No native `main` is emitted** on Android, because `SDL_MAIN_EXPORTED` suppresses SDL's own
  `main` wrapper, and SDL3 itself defines no `SDL_main` (SDL3 3.4.16 has no `src/main/android/`
  directory; `SDL_main` is produced only by the app's own translation unit).
- **`SDLActivity` finds it**: `getMainFunction()` returns `SDL_main` and `nativeRunMain`
  (`SDL_android.c`) `dlopen`s the library with `RTLD_GLOBAL` and `dlsym`s that name.
- On macOS/Linux the same include leaves `main` an ordinary `main`, so the desktop entry point is
  unchanged.

### 1.3 Android source selection and compile definitions

| Concern | How Android is selected | Verified |
|:--|:--|:--|
| Platform | `cmake/DetectPlatform.cmake` sets `ZH_PLATFORM_ANDROID`, keeps `ZH_PLATFORM_POSIX`, and never sets `ZH_PLATFORM_LINUX` (`CMakeLists.txt:34-36`) | `Tests/test_platform_detection.cmake` covers every row |
| Architecture | `cmake/DetectArchitecture.cmake` sets `ZH_ARCH_AARCH64` from `CMAKE_SYSTEM_PROCESSOR`/`ANDROID_ABI`; a non-AArch64 Android configure is refused with the fix named (`CMakeLists.txt:52-58`) | `Tests/test_architecture_detection.cmake` |
| Code-level predicate | `GameEngine/Include/Platform/PlatformPredicates.h` derives `ZH_ANDROID`, `ZH_DESKTOP_LINUX` from `__ANDROID__`/`__linux__`; used by `PosixMain.cpp`, `ExecutableDirectory.cpp`, `SdlTouch`, `SdlAndroidPlatform` | `Tests/test_platform_predicates.cpp` asserts `ZH_ANDROID == 1` under the NDK and `ZH_ANDROID && ZH_DESKTOP_LINUX` is impossible |
| ABI / API / STL | `scripts/build/android/android.toolchain.cmake`: `arm64-v8a`, `android-28`, `c++_shared`, Clang, C++17 | recorded in the build tree's `CMakeCache.txt`; CI asserts `ANDROID_PLATFORM=android-28` |

The Android game target inherits its compile definitions from the libraries it links
(`sdldevice`, `gameengine`), not from a private list, so there is one definition of the platform
predicates for the whole engine. No new compile definition was needed by this phase.

**Android does not inherit desktop-Linux dependencies**, and by construction rather than by a
guard: every desktop-Linux-only dependency in the file is gated on `ZH_PLATFORM_LINUX` (fontconfig in
`glyphrasteriser`, the X11/Wayland and LLD/mold settings, the icon generator, the Steam Runtime
packaging, the FFmpeg build-from-tarball's desktop arm). Android never sets that variable, so those
branches cannot run. `ci-android-test.yml` re-checks the result from the generated Ninja files
(`-lfontconfig`, `-lX11`, `-lxkbcommon`, `-lwayland-client` must be absent).

## 2. The standalone SDL3 library

### 2.1 The SDL3 in the tree

| Item | Value | Where |
|:--|:--|:--|
| Version | **3.4.16** | `Libraries/Source/SDL3` after `Tools/vendor.sh`; `project(SDL3 LANGUAGES C VERSION "3.4.16")` |
| Pin | commit `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c` | `GeneralsMD/Code/Tools/vendor.sh:463` |
| Source | fetched, not committed | `Libraries/Source/SDL3/.gitignore` ignores everything but itself |
| Java glue | `android-project/app/src/main/java/org/libsdl/app/*.java`, from the **same** tree | staged by `scripts/build/android/package-android.sh` |

The Java glue and `libSDL3.so` therefore cannot drift: both come from the one fetched tree.

### 2.2 SDL3's own CMake facts this phase relies on

Read from the pinned tree's `CMakeLists.txt` and sources:

| Fact | Evidence |
|:--|:--|
| Both library types are supported; the shared target is `SDL3-shared` (alias `SDL3::SDL3-shared`), the static one `SDL3-static` (alias `SDL3::SDL3-static`) | `CMakeLists.txt:465-467`, `481-483` |
| `SDL3::SDL3` always exists and resolves to the shared target when one was built, else the static one | `CMakeLists.txt:502-506` |
| `SDL_SHARED` / `SDL_STATIC` are plain cache options, forced by us | `CMakeLists.txt:194-232` |
| `SDL_ANDROID_JAR` defaults to off when SDL3 is a subproject (`SDL3_MAINPROJECT` off), so no Java toolchain is needed at configure | `CMakeLists.txt:11-13`, `294` |
| Android video/GPU support is native to the platform: `SDL_VIDEO_DRIVER_ANDROID`, Vulkan via `SDL_VIDEO_VULKAN`/`SDL_GPU_VULKAN`; no GLES is required | `CMakeLists.txt:1417-1600` |
| The shared library is linked with the version script `src/dynapi/SDL_dynapi.sym`; Android **requires** a linker that supports it (a configure error otherwise) | `CMakeLists.txt:521-532` |
| That script exports `JNI_OnLoad` and the `SDL_*` API and hides everything else (`local: *`) | `src/dynapi/SDL_dynapi.sym:3`, `1275` |
| On Android SDL3 sets no `VERSION`/`SOVERSION` (only `UNIX AND NOT ANDROID` gets them), so the file is `libSDL3.so` with no version suffix and the linker's default `DT_SONAME` of `libSDL3.so` | `CMakeLists.txt:4004-4008` |
| A **static** SDL3 on Android adds `-Wl,-u,JNI_OnLoad` to its consumers so the JNI entry point survives the archive | `CMakeLists.txt:1649-1650` |

### 2.3 Before and after

| | Before this phase | After |
|:--|:--|:--|
| SDL3 library type | `SDL_STATIC ON`, `SDL_SHARED OFF` on **every** platform | Android: `SDL_SHARED ON`, `SDL_STATIC OFF`. Desktop: unchanged (`SDL_STATIC ON`, `SDL_SHARED OFF`) |
| SDL3 targets in the build | `SDL3-static` only | Android: `SDL3-shared` only; desktop: `SDL3-static` only |
| Link name used by the engine | `SDL3::SDL3-static`, written 31 times | `${ZH_SDL3_TARGET}`, written 31 times, set once |
| `JNI_OnLoad` | inside `libmain.so` | in `libSDL3.so` |
| APK native payload | `libmain.so`, `libc++_shared.so` | `libmain.so`, `libSDL3.so`, `libc++_shared.so` |

### 2.4 The linkage strategy: one variable, no second implementation

`GeneralsMD/Code/CMakeLists.txt:940-988` is the only place the choice is made:

```cmake
if(ZH_PLATFORM_ANDROID)
  set(SDL_SHARED ON  CACHE BOOL "" FORCE)
  set(SDL_STATIC OFF CACHE BOOL "" FORCE)
else()
  set(SDL_SHARED OFF CACHE BOOL "" FORCE)
  set(SDL_STATIC ON  CACHE BOOL "" FORCE)
endif()
add_subdirectory(${LIBSRC}/SDL3 ${CMAKE_CURRENT_BINARY_DIR}/SDL3)

if(ZH_PLATFORM_ANDROID)
  if(NOT TARGET SDL3::SDL3-shared)   # SDL3 builds only the type it was asked for
    message(FATAL_ERROR "...")
  endif()
  set(ZH_SDL3_TARGET SDL3::SDL3-shared)
else()
  if(NOT TARGET SDL3::SDL3-static)
    message(FATAL_ERROR "...")
  endif()
  set(ZH_SDL3_TARGET SDL3::SDL3-static)
endif()
```

Every one of the 31 SDL3 link lines in the file now names `${ZH_SDL3_TARGET}` (the engine's own
libraries and every test/probe: `sdlinput`, `sdldevice`, `posixd3d9`, `sdl3shadercompile`,
`sdl3_shadercross`, `sdl_gpu_probe`, `w3d_view`, the `test_sdl_*` and `test_renderer_*` families,
`water_preview`, `ffref_*`). `SDL3::Headers` (one line, a header-only interface) is left alone.

Why a variable and not an alias: SDL3 defines `SDL3::SDL3` to mean "whichever type was built", so
using it would silently accept a misconfigured tree. Naming the exact type, and checking the target
exists before the first link line, turns that mistake into a configure error that says which option
is wrong. It also makes the "no second SDL3 implementation" rule mechanical: there is no link line
left that could pull the static archive into an Android build.

`generals` also names SDL3 directly (`CMakeLists.txt:2332`), not only through `sdldevice`, so
`libmain.so`'s `DT_NEEDED` carries `libSDL3.so` even if `sdldevice`'s link ever became private.

**Preventing the static embed.** With `SDL_STATIC OFF` the archive does not exist in an Android build
tree, so it cannot be linked. The two failure modes that would reintroduce it are both loud: a link
line naming `SDL3::SDL3-static` fails the configure ("target not found"), and a `libSDL3.so` missing
from the APK fails the ELF/APK audit in `ci-android-app.yml`.

**Preventing duplicate definitions.** Only one SDL3 library is linked, and `libSDL3.so`'s version
script hides everything it does not export, so the loader resolves exactly one definition of each
`SDL_*`. `libmain.so` must define no `SDL_*` of its own except `SDL_main` (the app's entry point);
`ci-android-app.yml` asserts that from the dynamic symbol table.

### 2.5 SONAME and exported symbols

| Object | SONAME | Must export | Must not |
|:--|:--|:--|:--|
| `libmain.so` | `libmain.so` | `SDL_main` | any other `SDL_*` definition |
| `libSDL3.so` | `libSDL3.so` | `JNI_OnLoad`, `SDL_Init`, `SDL_CreateGPUDevice`, `SDL_GetAndroidInternalStoragePath`, `SDL_GetAndroidActivity`, `SDL_GetAndroidJNIEnv`, … (the whole API) | — (the version script hides the rest) |

The Android-relevant names are in the version script, checked against the pinned commit:
`JNI_OnLoad`, `SDL_GetAndroidActivity`, `SDL_GetAndroidInternalStoragePath`, `SDL_GetAndroidJNIEnv`,
`SDL_Init`, `SDL_CreateGPUDevice`. `SDL_dynapi.sym` also stamps the exported symbols with the version
node `SDL3_0.0.0`, so `libmain.so` records version requirements against `libSDL3.so`; bionic ignores
symbol versions, so this has no runtime effect — but the two objects belong together, which is why
they are built from one tree and packaged from one build.

## 3. Runtime consistency

| Concern | How it is kept consistent |
|:--|:--|
| ARM64 | Both objects are built by the same NDK toolchain in the same configure (`android.toolchain.cmake`: `arm64-v8a`). A non-AArch64 Android configure is refused at `CMakeLists.txt:52-58`. |
| Android API | Both are built at `ANDROID_PLATFORM=android-28` from the same cache entry; nothing raises SDL3's floor. |
| C++ runtime | Both objects name `libc++_shared.so` (`ANDROID_STL=c++_shared`): `libmain.so` because the engine is C++, `libSDL3.so` because the NDK toolchain puts the shared STL in the `DT_NEEDED` of every shared object it links, although SDL3's Android sources are C (found by the first CI run, §6.1). One copy is packaged from the same NDK and both load it, so the process has one C++ runtime and SDL3 contains no second copy. |
| JNI visibility | `JNI_OnLoad` is exported by `libSDL3.so` through the version script (default visibility), so `System.loadLibrary("SDL3")` registers `SDLActivity`'s native methods. `libmain.so` needs no `JNI_OnLoad` of its own. |
| SDL3 initialization ownership | Unchanged: the engine calls `SDL_Init`/`SDL_CreateGPUDevice` through `SdlGameEngine`/`SdlGpuFrame`; there is one SDL3 instance, in `libSDL3.so`, and the engine is its only user. Nothing in the app shell initializes SDL. |
| Native loading order | `ZeroHourActivity.getLibraries()` returns `{"SDL3", "main"}` — SDL3 first, so its `JNI_OnLoad` runs before `SDL_main`; `libmain.so`'s `DT_NEEDED` on `libSDL3.so` also lets the loader resolve it automatically. |
| `SDL_main` handling | Exactly one definition, in `libmain.so`, exported with default visibility; SDL3 defines none and `SDL_MAIN_EXPORTED` suppresses a native `main`. |

## 4. Files changed

| File | Change |
|:--|:--|
| `GeneralsMD/Code/CMakeLists.txt` | SDL3 block: `SDL_SHARED`/`SDL_STATIC` chosen per platform, targets checked, `ZH_SDL3_TARGET` defined (`940-988`); 31 link lines switched from `SDL3::SDL3-static` to `${ZH_SDL3_TARGET}`; `generals` links `${ZH_SDL3_TARGET}` directly (`2332`) |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/ZeroHourActivity.java` | `getLibraries()` returns `{"SDL3", "main"}`; the class documentation describes the two-library model |
| `android/app/src/test/kotlin/io/github/olcayseygan/zerohourreforged/ThemeIsolationTest.kt` | The SDL contract test asserts the new library list |
| `android/app/build.gradle` | Comments updated: `jniLibs` holds three libraries |
| `scripts/build/android/package-android.sh` | Requires and stages `libSDL3.so` beside `libmain.so`; header documents the loading order |
| `android/README.md` | Platform-layer row, APK payload and build description now say `libSDL3.so` |
| `.github/workflows/ci-android-app.yml` | ELF audit classifies `libSDL3.so` as packaged, audits `libSDL3.so` itself, flips the "SDL3 is static" check to "one implementation in `libSDL3.so`"; APK audit expects three libraries and exactly one SDL3 shared object |
| `.github/workflows/ci-android-test.yml` | Compiles `SDL3-shared`, asserts `libSDL3.so` exists and is named in `libmain.so`'s `DT_NEEDED` |
| `docs/android-port/architecture/16-android-application-shell.md`, `18-native-dependency-packaging.md` | One-line "superseded by this document" notes on the paragraphs that described static SDL3 |

Untouched, deliberately: `Main/PosixMain.cpp` (the entry point needed no change), every renderer and
audio source, `Tools/vendor.sh` (same SDL3 pin), `android.toolchain.cmake`/`build-android.sh` (same ABI,
API and STL), the Gradle plugin versions, the manifest and the permissions.

## 5. Static validation performed

No application build was run locally. What was run:

1. **CMake target graph inspection.** Every SDL3 link site was enumerated (`grep -n 'SDL3::'`), then
   re-enumerated after the change: 31 sites, all now `${ZH_SDL3_TARGET}`; the only remaining literal
   `SDL3::` names are the two target-existence guards and `SDL3::Headers`. A block-structure scan of
   the 3676-line `CMakeLists.txt` (if/endif, foreach/endforeach, function/endfunction, macro/endmacro)
   reports no unbalanced block, and a scope scan confirms every `${ZH_SDL3_TARGET}` use is inside an
   `if(ZH_PLATFORM_POSIX)` block and after its definition.
2. **The selection logic itself**, executed under `cmake -P` with the option block extracted verbatim:
   `ANDROID=TRUE → SDL_SHARED=ON, SDL_STATIC=OFF`; `ANDROID=FALSE → SDL_SHARED=OFF, SDL_STATIC=ON`;
   the other options (`SDL_VIDEO`, `SDL_TEST_LIBRARY`, …) unchanged in both cases.
3. **Android compile-definition audit.** One `main`/`SDL_main` in the tree; one `SDL_main.h` include;
   `ZH_ANDROID`/`ZH_ARCH_AARCH64` sourced from the NDK's own macros and the toolchain file; no
   Android path reaches a `ZH_PLATFORM_LINUX` branch.
4. **SDL3 linkage audit.** SDL3's pinned `CMakeLists.txt` read for the shared target name, the
   `SDL3::SDL3` alias rule, the version script requirement on Android, the absence of `SOVERSION` on
   Android, and the static-only `-Wl,-u,JNI_OnLoad`; the version script read for the exported names
   the engine and the Java glue need.
5. **Java and shell checks.** `bash -n` on `package-android.sh`; both edited workflows parse as YAML
   (`preflight`/job structure intact).
6. **Symbol-export and ELF-dependency verification plan** (below) — written into CI, where it runs on
   a real NDK and a real link, since neither exists in this workspace.

## 6. Verification plan (what CI proves, and where)

`ci-android-app.yml`, "Audit the native dependencies from the ELF":

- `libmain.so`: `ELF64`, `AArch64`, `DYN`, `SONAME libmain.so`, exports `SDL_main`;
  `DT_NEEDED` classified into Android system libraries, `libSDL3.so` and `libc++_shared.so`; both
  packaged names required; no desktop library; **imports** `SDL_*` and **defines** none except
  `SDL_main`; stamped `android-28`.
- `libSDL3.so`: exists; `ELF64`, `AArch64`, `DYN`; `SONAME libSDL3.so`; exports `JNI_OnLoad`,
  `SDL_Init`, `SDL_CreateGPUDevice`, `SDL_GetAndroidInternalStoragePath`; its own `DT_NEEDED` is
  Android system libraries plus the packaged `libc++_shared.so` (see §6.1 and the C++ runtime row
  above), and nothing else.

`ci-android-app.yml`, "Inspect the packaged APK":

- exactly `lib/arm64-v8a/{libmain.so,libSDL3.so,libc++_shared.so}`, one ABI, no duplicates, exactly
  one `libSDL3.so`;
- the packaged `libmain.so`'s dependency set equals the built one's (and names `libSDL3.so`);
- the packaged `libSDL3.so` still has SONAME `libSDL3.so` and exports `JNI_OnLoad` after AGP's strip;
- every non-system `DT_NEEDED` of `libmain.so` exists in the APK.

`ci-android-test.yml`: `SDL3-shared` compiles and produces `libSDL3.so`; the engine links and its
`DT_NEEDED` names `libSDL3.so`, whose SONAME and machine match.

### 6.1 What the first CI run found

The first run of `ci-android-app.yml` on this branch (run 44806441122, 2026-11-20) linked `libmain.so`
and then stopped at "the build did not produce `build-android-arm64/libSDL3.so`". The shared/static
choice was right: CMake's `if(NOT TARGET SDL3::SDL3-shared)` guard passed, so the shared target
existed and `libmain.so` linked it. The **location** was wrong. SDL3 is a subproject
(`add_subdirectory(.../SDL3 ${CMAKE_BINARY_DIR}/SDL3)`), and CMake puts a subproject's library in
that subproject's own binary directory, so the artifact was
`build-android-arm64/SDL3/libSDL3.so` - while this document, `package-android.sh` and both workflows
look for it beside the engine.

Phase 2 fixed it in `GeneralsMD/Code/CMakeLists.txt`: the shared target gets
`LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}`, so `libmain.so` and `libSDL3.so` are produced in one
directory. Nothing else about the two-artifact design changed.

Three further runs each found one more thing that only a real build could show, all recorded in
`ANDROID_SDL3_APPLICATION_PACKAGING.md` §8.1: an `SDL_`-prefix test that rejected SDL_shadercross's
`SDL_ShaderCross_*`, a `JNI_OnLoad$` match that could never see `JNI_OnLoad@@SDL3_0.0.0` (SDL3's
version script versions every export), and this table's C++ runtime row, which said `libSDL3.so`
needs no C++ runtime when the NDK puts `libc++_shared.so` in its `DT_NEEDED`.

Both workflows must be dispatched by hand for this branch (`gh workflow run <file> --ref <branch>`):
their `push` triggers list the older port branches only.

## 7. Remaining blockers

1. **Nothing has been built or run for this phase.** CI is the first compile; no device has loaded
   `libSDL3.so` yet. The two-library load order is SDL3's documented contract, not something observed
   on hardware here.
2. **APK size.** `libSDL3.so` adds a few megabytes that static linking folded into `libmain.so`. That
   is the cost of the standalone platform library; nothing else in the payload changed.
3. **The pair is atomic.** `libmain.so` names `libSDL3.so` in `DT_NEEDED` and carries symbol-version
   requirements against it, so the two must always ship together and `libSDL3.so` must keep its
   SONAME. A build that stages one without the other fails to start; `package-android.sh` refuses to
   run when `libSDL3.so` is absent, and CI checks the packaged pair.
4. **The Gradle shell still excludes `libandroidx.graphics.path.so`**, so the APK's native payload
   stays exactly the engine's three libraries. Any future Compose screen that iterates a `Path` must
   revisit that exclusion and the CI assertion together.
5. **No device-level verification of SDL3's own Android backends** (Vulkan device creation, surface
   lifecycle) exists yet; that remains the device checklist of the earlier phases.

## 8. Provenance

- Target repository `redaf12-Decode/Generals-Reforged`, baseline `agent/ci-dependency-aware-pipeline`
  (`1acd42b`), work on `agent/android-sdl3-android-completion`.
- SDL3 facts: the pinned tree at commit `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c` (3.4.16) —
  `CMakeLists.txt`, `src/dynapi/SDL_dynapi.sym`, `include/SDL3/SDL_main.h`,
  `android-project/app/src/main/java/org/libsdl/app/SDLActivity.java`.
- Reference port `redaF12-Tech/GeneralsZH-Android-Port` was consulted for the model only (its
  `cmake/sdl3.cmake` also builds SDL3 shared, `SDL_SHARED ON`/`SDL_STATIC OFF`); none of its code was
  copied, and none of its DXVK/GLES/adrenotools work is involved.
- No application build was run locally. The checks in §5 are the ones that were actually executed.
