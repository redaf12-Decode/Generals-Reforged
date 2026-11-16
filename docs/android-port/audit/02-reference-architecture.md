# Reference Android port audit

Read-only audit of the **reference** repository for the Android port,
<https://github.com/redaF12-Tech/GeneralsZH-Android-Port>, at commit
`51966b1` ("main", 2026-11-09). No file in either repository was changed while producing it.

Its purpose is to establish, before any Android code is written into the target
(Generals Reforged, audited in [`01-target-architecture.md`](01-target-architecture.md)), *what
already exists in the reference*, *where it lives*, and *whether the target has the symbols it
assumes*. Everything below was located in the reference checkout and is cited by path and symbol;
where the target differs, the difference is stated and the target symbol is cited too.

> The reference is a full, shipped Android port (campaign, skirmish, Challenge and online all
> run on device — `docs/port/ANDROID_PORT.md` header). It is **not** a drop-in patch set for the
> target: the two trees are different codebases that share an EA 2003 engine ancestor. This audit
> separates what is *portable infrastructure* from what is *bound to the reference's own engine
> layout*.

## 1. Repository and branch facts

| Item | Reference | Target (`Generals-Reforged`) |
|:--|:--|:--|
| Default branch | `main` | `main` |
| HEAD at audit time | `51966b1` (2026-11-09) | `109962c` (shallow, grafted) |
| Tracked files | 6370 | 8790 |
| Android app present | yes (`android/`, 69 files) | **no** |
| `__ANDROID__` occurrences | 102 in 35 files | **0** |
| Shared mobile guard | `SAGE_MOBILE_PLATFORM` (3 files) | **absent** |
| Top-level CMake | `CMakeLists.txt` + `CMakePresets.json` | none; single entry `GeneralsMD/Code/CMakeLists.txt` |

Tracked files by top-level directory (reference):

| Path | Files | Contents |
|:--|--:|:--|
| `Core/` | 2020 | Shared engine (`GameEngine`, `GameEngineDevice`, `Libraries`, `Tools`) |
| `GeneralsMD/` | 1399 | Zero Hour `Code/` + `Run/` |
| `Generals/` | 1221 | Base Generals `Code/` + `Run/` |
| `GeneralsOnlineRelease/` | 694 | NGMP online backend binaries + data (Windows) |
| `tools/` | 473 | `dxvk-refcount-litmus`, `translation-work` |
| `docs/` | 158 | `port/ANDROID_PORT.md`, `port/TOUCH_CONTROLS.md`, `WORKDIR/` |
| `scripts/` | 114 | `build/{android,ios,linux,macos}`, `update`, `qa`, `tooling` |
| `android/` | 69 | Gradle shell + Java activities + resources |
| `Patches/` | 45 | DXVK Android/iOS/Mali patches, `SagePatch` |
| `cmake/` | 39 | `sdl3.cmake`, `dx8.cmake`, `openal.cmake`, `adrenotools.cmake`, triplets |
| `.github/` | 35 | 12 workflows (plus `workflows/old/`) incl. `build-android.yml` |
| `ios/` | 8 | iOS stub, `project.yml`, config |
| `vcpkg.json` | 1 | vcpkg manifest |

The single most important structural difference: the reference has a **`Core/` tree** holding the
shared engine, and builds Zero Hour *and* Generals from it. The target has **no `Core/`**; its
shared engine, device layer and libraries all live under `GeneralsMD/Code` (see
`01-target-architecture.md` §2). Reference paths of the form `Core/...` therefore have **no
target counterpart** and must be re-expressed as `GeneralsMD/Code/...` (or `Generals/Code/...`)
when porting anything.

## 2. What the reference added for Android

`docs/port/ANDROID_PORT.md` §1 is the reference's own file manifest. Restated with the target's
paths in mind:

| Reference file | Purpose | Target counterpart |
|:--|:--|:--|
| `CMakePresets.json` (`android-vulkan`) | NDK toolchain + vcpkg + `arm64-v8a` + API 28 | **none** (no presets file) |
| `cmake/triplets/arm64-android.cmake` | Pins API level for vcpkg deps | **none** |
| `cmake/meson-arm64-android-cross.ini.in` | DXVK meson cross file | **none** (no DXVK) |
| `cmake/dx8.cmake` `elseif(ANDROID)` | Builds DXVK d3d8/d3d9 from local fork | **none** (no DXVK) |
| `Patches/dxvk-android.patch` | Unversioned `.so` names for APK packaging | **none** |
| `cmake/sdl3.cmake` | Android: no system libpng/TIF/WEBP | target SDL3 is configured inline in `GeneralsMD/Code/CMakeLists.txt:900-913` |
| `Core/Libraries/Source/WWVegas/WW3D2/CMakeLists.txt` | `SAGE_USE_FREETYPE` + Freetype on Android, fontconfig excluded | target uses vendored FreeType unconditionally (`CMakeLists.txt:946`) |
| `Core/Libraries/Source/WWVegas/WW3D2/render2dsentence.{h,cpp}` | Bundled-font locator for iOS **and** Android | target `glyphrasteriser` + FreeType path |
| `GeneralsMD/Code/Main/CMakeLists.txt` | Android: `z_generals` → `libmain.so`, not an executable | target `Main/CMakeLists.txt` — **needs the same split** |
| `GeneralsMD/Code/Main/SDL3Main.cpp` | Env bootstrap, immersive fullscreen, hints, log rotation | target entry is `Main/PosixMain.cpp` (no SDL bootstrap) |
| `GeneralsMD/Code/GameEngineDevice/Source/SDL3GameEngine.cpp` | Touch gesture machine + lifecycle render gate | target `SdlDevice/Common/SdlGameEngine.cpp` (no touch) |
| `GeneralsMD/Code/GameEngineDevice/{Include,Source}/SDL3Device/GameClient/TouchInput.{h,cpp}` | Gesture *semantics* against engine rules | **absent in target** |
| `android/` | Gradle shell, activities, resources | **absent in target** |
| `scripts/build/android/{build,package}-android-zh.sh` | Build + stage jniLibs/Java/assets + assemble | **absent in target** |
| `vcpkg.json` | `fontconfig` excluded on Android; `ffmpeg` on | target vendors deps via `Tools/vendor.sh`, no vcpkg |

## 3. Reference platform architecture

### 3.1 Entry point and app model

The reference keeps two platform entry points, as the target does, but adds a third mode:

- `GeneralsMD/Code/Main/WinMain.cpp` — Windows.
- `GeneralsMD/Code/Main/SDL3Main.cpp` — POSIX **and** mobile. On Android, `SDL3Main.cpp` includes
  `<SDL3/SDL_main.h>` (line 50), which renames `main()` to `SDL_main`; SDL's `SDLActivity` Java
  shell loads the game as a shared library and calls it. `CreateGameEngine()` is at line 447 and
  instantiates `SDL3GameEngine`; `GameMain()` (in `GeneralsMD/Code/GameEngine/Source/Common/GameMain.cpp:37`)
  then runs `TheGameEngine->init()` and `->execute()`.
- The game is built as **`libmain.so`**, not an executable, under Android
  (`GeneralsMD/Code/Main/CMakeLists.txt`: `if(ANDROID) add_library(z_generals SHARED) … OUTPUT_NAME main`).
  A second engine variant `libmain60.so` (60 Hz) is selected at runtime by
  `GeneralsZHActivity.getLibraries()`.

The target's equivalent entry is `Main/PosixMain.cpp` (`main():438`, `CreateGameEngine():627`)
building the `generals` **executable** (`CMakeLists.txt:2168-2171`). This is the first structural
change Android requires in the target: a shared-library target and an SDL `main` rename.

### 3.2 Platform engine split

| Concern | Reference | Target |
|:--|:--|:--|
| Abstract POSIX engine | (no separate class; `SDL3GameEngine` is it) | `PosixGameEngine` (abstract) |
| Concrete POSIX engine | `SDL3GameEngine` (`GeneralsMD/Code/GameEngineDevice/Source/SDL3GameEngine.cpp`) | `SdlGameEngine : PosixGameEngine` (`SdlDevice/Common/SdlGameEngine.cpp`) |
| Factories | `SDL3GameEngine::createGameLogic/GameClient/Radar/AudioManager/…` (lines 2609+) | `SdlGameEngine::createGameLogic/GameClient/Radar/AudioManager/…` |
| Event pump | `SDL3GameEngine` (SDL3 events + gestures) | `SdlGameEngine::serviceWindowsOS` (`SdlGameEngine.cpp:331`) |
| Filesystem | `SDL3GameEngine::createLocalFileSystem → StdLocalFileSystem` (line 2597) | `PosixLocalFileSystem` / `PosixLocalFile` |

Both use the same W3D device classes (`W3DGameLogic`, `W3DGameClient`, `W3DRadar`,
`W3DModuleFactory`). The reference's `SDL3GameEngine` is where the entire Android-specific
behaviour lives (window flags, resolution injection, touch, lifecycle gate), so it is the single
file whose *responsibilities* — not its contents — map onto the target's `SdlGameEngine`.

### 3.3 SDL3 version

- Reference: SDL3 **3.4.2** via `FetchContent` (`cmake/sdl3.cmake:22`), SDL3_image 3.4.0.
- Target: SDL3 **3.4.16**, pinned by commit in `Tools/vendor.sh:442` (`fa2c02bb…`).

Same 3.4 line; the target is newer. Any SDL3 API the reference uses for Android exists in the
target's version, so no downgrade is implied.

## 4. Rendering architecture (the largest divergence)

The two ports render through **completely different stacks**, and this is the single most
important finding for the Android work.

| | Reference | Target |
|:--|:--|:--|
| Default backend | **native GLES3** via `Core/Libraries/Source/d3d8gles/` (since v1.2.1) | **SDL3 GPU** (Vulkan on Linux, Metal on macOS) |
| Opt-in backend | **DXVK 2.6** d3d8/d3d9 `.so` → Vulkan | — |
| D3D8 entry | `DX8Wrapper::Init()` dlopens `libdxvk_d3d8.so`, or binds `Direct3DCreate8_GLES` (`Core/Libraries/Source/WWVegas/WW3D2/dx8wrapper.cpp:750`) | `PosixDirect3D9` → `PosixDevice9` → `SdlGpuFrame` |
| Device creation | DXVK's `IDirect3D8`/`IDirect3DDevice8` | `SdlGpuFrame::Create` → `SDL_CreateGPUDevice(SPIRV\|MSL)` (`SdlGpuFrame.cpp:192-197`) |
| Shaders | HLSL compiled by DXVK's own `d3d9` compiler at runtime | HLSL → SPIR-V ahead of time via `SDL3_Compile_HLSL_To_SPIRV` |
| Swapchain | DXVK WSI (`Sdl3WsiDriver`, patched) | `SDL_WaitAndAcquireGPUSwapchainTexture` |
| GLES backend files | `d3d8gles/{include/d3d8gles.h,src/d3d8gles.cpp,gles_dispatch.cpp}`, `d3d8gles_ShouldUseVulkanBackend()` | **absent** |

The reference's own note (`docs/port/ANDROID_PORT.md:68-72`) confirms `dx8wrapper.cpp` needed *no
Android change* because its Linux branch already `dlopen`s a bare `libdxvk_d3d8.so`, which the
Android linker namespace resolves from the APK. **That path does not exist in the target**: the
target has no DXVK and no `d3d8gles`; its renderer is already a native SDL3-GPU translation of the
D3D9 API (`PosixDevice9`, `PosixD3D9Caps`, `PosixResources9`, `SdlPipelineCache`,
`SdlProgramCache`, `SdlResourceMirror` — all under
`GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/`).

Consequence: the reference's *graphics* work (DXVK, GLES backend, driver patches, AdrenoTools,
Turnip) is **not reusable** in the target. What *is* reusable is the surrounding platform work:
making the SDL3-GPU renderer survive Android's window/context model. The target's
`SDL_CreateGPUDevice` already requests `SPIRV|MSL`; on Android the driver reports SPIRV (Vulkan)
only, which the target supports — but window claiming, surface sizing and
`SDL_WINDOW_HIGH_PIXEL_DENSITY` behaviour all need the Android treatment the reference applies in
`SDL3GameEngine.cpp`/`SDL3Main.cpp`.

## 5. Input and touch (the largest target gap)

The target's input is keyboard/mouse only:
`SdlDevice/GameClient/{SdlInput.h,SdlInput.cpp,SdlKeyboard.*,SdlMouse.*}` with the API
`SdlInput_dispatch / _install / _gameWindow / _toGamePixels / _scaleToGame / _resetWheel`
(`SdlInput.h:42-60`). There is **no touch, gesture or pen code anywhere** in the target
(confirmed by `01-target-architecture.md` §9.2 and by grep).

The reference's touch layer is two files, both new:

- `GeneralsMD/Code/GameEngineDevice/Include/SDL3Device/GameClient/TouchInput.h` (137 lines) —
  the gesture *vocabulary*: `tap`, `doubleTap`, `beginAiming`, `armedTargetValid`, `reportUiHold`,
  `cancelOrDeselect`, `lookAtRadarPoint`, plus predicates `hasArmedCommand()`,
  `hasControllableSelection()`.
- `GeneralsMD/Code/GameEngineDevice/Source/SDL3Device/GameClient/TouchInput.cpp` (536 lines) —
  the gesture state machine and its resolution against engine rules.

Its design principle (`docs/port/TOUCH_CONTROLS.md`, and
`docs/WORKDIR/lessons/LESSON-touch-input-is-not-a-mouse.md`) is that battlefield input does **not**
synthesize `MSG_RAW_MOUSE_*`; gestures are resolved directly through the engine:

```cpp
TheTacticalView->pickDrawable(&screenPoint, forceAttack, pickType);
TheTacticalView->screenToTerrain(&screenPoint, &worldPos);
TheGameClient->evaluateContextCommand(draw, &worldPos, DO_COMMAND);   // or EVALUATE_ONLY
```

**Target symbol check** — the primitives this depends on *do* exist in the target:

| Symbol | Target location | Present |
|:--|:--|:--|
| `View::pickDrawable` | `GeneralsMD/Code/GameEngine/Include/GameClient/View.h:129` | yes |
| `View::screenToTerrain` | `View.h:233` | yes |
| `GameClient::evaluateContextCommand` | `Include/GameClient/GameClient.h:127` | yes |
| `CommandTranslator::evaluateContextCommand` | `Include/GameClient/CommandXlat.h:51` | yes |
| `LookAtXlat` scroll modes (`SCROLL_RMB`, `SCROLL_SCREENEDGE`) | `Include/GameClient/LookAtXlat.h:56-58` | yes |
| `LookAtXlat::isRightDragPanning` | `LookAtXlat.h:46` | yes |

**Target symbols the reference's touch layer assumes that are *missing* in the target** (parity
gaps to close before porting gestures):

| Reference symbol | Reference location | Target status |
|:--|:--|:--|
| `View::userScrollBy`, `userZoom`, `userSetPosition`, `userSetAngle`, `userSetPitch`, `userLookAt`, … (the `doUserAction` family) | `Core/GameEngine/Include/GameClient/View.h:215-232` | **absent** — target `View.h` has only `setZoomLimited`/`isZoomLimited` (`View.h:125-126`), `setAngle`, `setPitch`, `zoomIn/zoomOut`, `zoomCamera` |
| `LookAtXlat::isPointerScrollActive`, `cancelScrolling` | `Core/GameEngine/Include/GameClient/LookAtXlat.h:72,85` | **absent** — target has `isRightDragPanning` only |
| `GadgetListBoxTouchScroll*` | reference `GadgetListBox` | **absent** |
| `ControlBar::addBuilderPageButtons`, `registerBuilderPageImages` | reference `ControlBar` | **absent** |

So the touch *concept* is portable, but the target's `View` needs a user-action surface
(the reference's `doUserAction` template) and its `LookAtXlat` needs the
pointer-scroll cancel hook, before the reference's `TouchInput.cpp` logic can be adapted.

## 6. Application shell (Java) — reusable design, not reusable code

The reference's `android/` tree is a complete, shipped launcher. It is *generalsx-specific*
(package `com.generalsx.zerohour`, game-specific screens) but its **structure** is the reusable
part for the target:

| File | Responsibility | Portability |
|:--|:--|:--|
| `GeneralsZHActivity.java` | Extends `SDLActivity`; names native libs; extracts APK assets; routes to Setup when no game data; `getArguments()` supplies `-gxSafeInsets`, replay extras | pattern reusable; package/screens specific |
| `SetupActivity.java` | First-run UI: game folder picker, render backend, sim rate, diagnostics | pattern reusable |
| `UpdateManager.java` | ECDSA P-256 (`SHA256withECDSA`) signature check of a signed `manifest.json` on an `updates` branch; GZIP download | **note:** the *target* already has its own update system (GitHub-Release-asset based, per project memory) — do not copy this one |
| `DataPackInstaller.java` | GeneralsOnline CDN data-pack fetch | GeneralsOnline-specific |
| `FolderPickerActivity`, `LogViewerActivity`, `NetworkDiagnosticsActivity`, `ReplayCheckActivity` | Auxiliary screens | pattern reusable |
| `InsetUtil.java` | Display-cutout/rounded-corner insets → `-gxSafeInsets` | pattern reusable |
| `LocaleHelper.java` | Per-app locale | reusable |
| `AndroidManifest.xml`, `res/values*/strings.xml`, adaptive icons | App metadata, 13 locales | metadata specific |

Native-side companion pieces (target has none): `GeneralsMD/Code/Main/AndroidCrashHandler.cpp`
(installs a signal handler as an `__attribute__((constructor))`, line 489, *before* `JNI_OnLoad`,
so a crash in ELF constructors or early `SDL_main` is still caught; writes `crash.log` derived from
`getuid()/100000`), `GXLogging.h` (the launcher's logging switch, read from
`/data/user/<uid>/<app>/files/logging_off`), `GXSafeArea.{h,cpp}` (HUD insets),
`GXRemoteConfig.h`.

## 7. Build system and CI

The reference builds the native side **outside Gradle** with the `android-vulkan` CMake preset, then
Gradle only packages staged `.so`s:

```
CMakePresets.json "android-vulkan"
  inherits default-vcpkg, Ninja, RelWithDebInfo
  VCPKG_TARGET_TRIPLET arm64-android
  VCPKG_CHAINLOAD_TOOLCHAIN_FILE $env{ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake
  ANDROID_ABI arm64-v8a, ANDROID_PLATFORM android-28, ANDROID_STL c++_shared
  SAGE_USE_DX8=OFF, SAGE_USE_SDL3=ON, SAGE_USE_OPENAL=ON, SAGE_USE_MOLTENVK=OFF
  SAGE_DXVK_USE_LOCAL_FORK=ON, RTS_BUILD_OPTION_FFMPEG=ON, RTS_BUILD_GENERALS=OFF
```

- `scripts/build/android/build-android-zh.sh` requires `ANDROID_NDK_HOME` (NDK r26+) and
  `VCPKG_ROOT`; builds `libmain.so` + DXVK d3d8/d3d9.
- `scripts/build/android/package-android-zh.sh` stages `jniLibs/arm64-v8a` (libmain, SDL3,
  SDL3_image, openal, gamespy, DXVK, `libc++_shared.so`), `java-sdl/` (SDL's `org/libsdl/app`
  Java, so the shell matches the linked SDL), and `assets/gamedata/` (fonts, `dxvk.conf`,
  `DefaultOptions.ini`).
- `scripts/build/android/build-dual-hz.sh` builds both tick rates into one APK.
- CI `.github/workflows/build-android.yml`: JDK 17, Android SDK cmdline-tools + **NDK
  27.2.12479018**, Gradle 8.9, `android-vulkan` preset, verifies artifacts, strips symbols,
  packages APK, verifies the APK carries every library `libmain.so` needs, uploads, and on a tag
  creates a GitHub Release.
- `android/app/build.gradle`: `namespace com.generalsx.zerohour`, `compileSdk 35`, `minSdk 28`,
  `targetSdk 35`, `abiFilters 'arm64-v8a'`, fixed in-repo debug keystore,
  `packagingOptions.jniLibs.useLegacyPackaging true` (AdrenoTools needs on-disk `.so` files),
  root `com.android.application` 8.5.2.

**Target status:** no presets, no `android/`, no Gradle, no `vcpkg.json`, no NDK usage. The target
vendors its dependencies with `Tools/vendor.sh` (SDL3, miniaudio, FreeType, glslang, SPIRV-Cross,
FFmpeg, zlib, LZH-Light, mbedTLS, GameSpy) and builds with `Tools/build-posix.sh`
(`cmake -S GeneralsMD/Code`). An Android build for the target therefore needs a **new CMake
preset/toolchain path** (the target has no `CMakePresets.json` to extend) and a **new Gradle
project**; neither can be copied verbatim, but the reference's preset fields, staging script shape
and `build.gradle` settings are a direct template.

## 8. Audio, video, text, filesystem

| Subsystem | Reference (Android) | Target | Reuse |
|:--|:--|:--|:--|
| Audio | OpenAL-soft 1.24.2 (`cmake/openal.cmake`, `SAGE_USE_OPENAL`), OpenSL/AAudio backends; Linux ALSA workarounds excluded on Android | `MilesAudioManager` over `Miles6/miniaudio/miles_miniaudio.cpp` (miniaudio, which also supports AAudio/OpenSL ES) | target audio path is *already* miniaudio-based; no OpenAL needed |
| Video | FFmpeg 8.1 (vcpkg, static) | FFmpeg 8.1.2 vendored (`vendor.sh:610`); Windows `.lib` only | target must add an Android FFmpeg build |
| Text | FreeType + bundled `fonts/` (no fontconfig on Android) | vendored FreeType 2.14.3, `glyphrasteriser` | bundled-font locator pattern is reusable |
| Local FS | `StdLocalFileSystem` (`Core/GameEngineDevice/Source/StdDevice/Common/`) | `PosixLocalFileSystem` / `PosixLocalFile` | same role; Android storage scoping is new work |
| Install root | — | `PosixInstallRoot.cpp` (Steam libraries, `Registry.ini` `InstallPath`) | **no Android equivalent in reference either**; the reference requires the user to point at a game folder |
| Config store | `Registry.ini` | `registry.cpp` → `Registry.ini` | same |

The reference's Android env bootstrap (in `SDL3Main.cpp`) sets `HOME` to internal storage, the
working directory to the external files dir (or its `GameData/`), DXVK cache to the app cache dir,
redirects stderr to a rotatable log file, and seeds `Options.ini` — a checklist the target will
need in equivalent form, minus the DXVK pieces.

## 9. Compatibility matrix — reference technique → target feasibility

| Reference Android technique | Target prerequisite | Feasible as-is? |
|:--|:--|:--|
| Game as `libmain.so` + `SDLActivity` | split `generals` target into a shared library on Android | yes — mechanical CMake change |
| `SDL_main` rename | include `<SDL3/SDL_main.h>` in the entry file | yes — target already includes it |
| Immersive fullscreen, `HIGH_PIXEL_DENSITY`, internal-resolution injection | apply in `SdlGameEngine::createWindow` | yes — window creation is already isolated there |
| Touch gesture machine | `View` user-action API + `LookAtXlat` cancel hook (see §5) | **partial** — target is missing the `doUserAction` surface |
| List-drag scrolling | `GadgetListBoxTouchScroll*` in `GadgetListBox` | **no** — must be written |
| Builder-page command bar | `ControlBar::addBuilderPageButtons` | **no** — must be written |
| Safe-area insets | a `GXSafeArea`-equivalent + HUD inset reads | yes — new small module |
| Crash handler as ELF constructor | `AndroidCrashHandler.cpp` pattern | yes — target has `CrashHandlerPosix.cpp` to adapt |
| DXVK / GLES backend / AdrenoTools / Turnip | — | **no** — target renders through SDL3 GPU, not D3D8 translation |
| OpenAL audio | — | **no need** — target is miniaudio already |
| vcpkg triplet + preset | new preset file (target has none) | yes — new file |
| Gradle shell + staging script | new `android/` project | yes — template reusable |

## 10. Licences and obligations

- Reference engine code is **GPL-3.0** (`LICENSE.md`), same licence family as the target.
- Reference-added Android files (`TouchInput.*`, `AndroidCrashHandler.cpp`, `GXLogging.h`,
  `GXSafeArea.*`, `d3d8gles/*`) carry the EA GPL-3.0 header plus a `GeneralsX @…` provenance line.
  Any adaptation into the target must preserve attribution.
- Third-party in the reference: **libadrenotools** is BSD-2-Clause (`cmake/adrenotools.cmake:2`);
  **d3d8gles** is derived from `Lolendor/Generals-WebAssembly`'s `d3d8webgl` (GPL-3.0 header).
  Neither is needed by the target (no DXVK/AdrenoTools path).

## 11. Key findings

1. **Different renderer stacks.** Reference: GLES3/DXVK (D3D8 translation). Target: native SDL3
   GPU (`PosixDevice9` + `SdlGpuFrame`). None of the reference's graphics work transfers.
2. **The target has zero Android scaffolding.** No `android/`, no presets, no `__ANDROID__`, no
   `SAGE_MOBILE_PLATFORM`.
3. **Touch is the biggest functional gap**, and the target's `View` lacks the user-action API the
   reference's gesture layer calls. `pickDrawable`, `screenToTerrain` and
   `evaluateContextCommand` *are* present, so the *approach* is viable.
4. **Audio is simpler in the target** (miniaudio already, no OpenAL needed).
5. **No `Core/` in the target** — every reference `Core/...` path must be re-expressed under
   `GeneralsMD/Code/...`.
6. **The app shell is a template, not a copy** — package, screens and update system are
   reference-specific; the target already has its own update mechanism.
7. **The reference is a complete, shipped port** and its `docs/port/` and `docs/WORKDIR/lessons/`
   are a durable record of the bug classes Android bring-up hits (touch-vs-mouse, driver
   fragmentation, D3D/GL rasterization differences) — worth reading before writing target code.

## 12. Provenance

- Reference: `https://github.com/redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`, branch `main`.
- Target: `https://github.com/redaf12-Decode/Generals-Reforged` @ `109962c`, branch `main`
  (audited in `01-target-architecture.md`).
- Reference docs consulted: `docs/port/ANDROID_PORT.md` (538 lines),
  `docs/port/TOUCH_CONTROLS.md`, `docs/WORKDIR/TOUCH-PARITY-MATRIX.md`,
  `docs/WORKDIR/lessons/`.
- Read-only: no source, build, workflow or dependency file in either repository was modified.
