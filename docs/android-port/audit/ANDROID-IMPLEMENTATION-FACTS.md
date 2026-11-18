# Android implementation facts — what the branch actually does

Read-only audit of the **Android implementation on this branch**
(`agent/android-arm64-application-port`) of `redaf12-Decode/Generals-Reforged`. Every statement
below was read from the checked-out tree at the time of writing and is cited by path. Where a
capability is absent it says so, and where it cannot be decided from the repository it is marked
**Unknown** rather than guessed.

This document is the factual base for
[`ANDROID-HARDWARE-COMPATIBILITY.md`](ANDROID-HARDWARE-COMPATIBILITY.md) and for the Android project
documentation at [`../../../android/README.md`](../../../android/README.md). It is a companion to the
design records under [`../architecture/`](../architecture/) and does not replace them.

## Classification

| Class | Meaning |
|:--|:--|
| **Verified** | Present in the tree and unambiguous; the file and symbol are named below. |
| **Partial** | Implemented, but with a gap or a limitation stated in the same row. |
| **Not Implemented** | Explicitly absent; the absence is verifiable in the tree. |
| **Unknown** | Cannot be decided from the repository (typically needs a device). |

Nothing here is **device-tested**: no Android device or emulator was used, and no build was run for
this audit. "Verified" means "verified in the source", never "verified on hardware".

---

## 1. Application structure

| Item | Value | Source | Class |
|:--|:--|:--|:--|
| Gradle project | `android/` with `settings.gradle`, `build.gradle`, `gradle.properties`, `app/build.gradle` | `android/*.gradle` | Verified |
| Android Gradle Plugin | `8.7.3` | `android/build.gradle` | Verified |
| Gradle | `8.12` (SDL3 3.4.16's own wrapper version); **no `gradlew` wrapper is committed** — CI provides 8.12 and the packaging script prefers `android/gradlew` if present, else `PATH` | `android/build.gradle` comment, `.github/workflows/ci-android-app.yml` (`GRADLE_VERSION: '8.12'`), `scripts/build/android/package-android.sh` | Verified |
| `namespace` / `applicationId` | `io.github.olcayseygan.zerohourreforged` | `android/app/build.gradle` | Verified |
| `compileSdk` / `targetSdk` | `35` | `android/app/build.gradle` | Verified |
| `minSdk` | `28` (Android 9) | `android/app/build.gradle` | Verified |
| ABI | `arm64-v8a` only (`abiFilters 'arm64-v8a'`) | `android/app/build.gradle`, `scripts/build/android/android.toolchain.cmake` | Verified |
| `versionCode` / `versionName` | `40000` / `4.0.0` | `android/app/build.gradle` | Verified |
| NDK revision | `27.2.12479018` (`ndkVersion`) | `android/app/build.gradle` | Verified |
| Runtime dependencies | None beyond the Android platform. The only dependency is `junit:junit:4.13.2` (`testImplementation`) | `android/app/build.gradle` | Verified |
| Source sets | `src/main/java-sdl` (SDL3 Java glue) and `src/main/jniLibs` added; both staged and gitignored | `android/app/build.gradle`, `android/.gitignore` | Verified |
| `buildTypes` | `debug` (AGP debug keystore) and `release` (`minifyEnabled false`); **no signing config for release** | `android/app/build.gradle` | Partial |
| `lint` | `abortOnError false` (the staged SDL3 glue is upstream code) | `android/app/build.gradle` | Verified |

The application is a thin Java shell: Gradle compiles the Java and packages the APK. The native
engine is **not** built by Gradle — it is cross-compiled by `scripts/build/android/build-android.sh`
and staged by `scripts/build/android/package-android.sh` before Gradle runs
(`android/settings.gradle` header, `docs/android-port/architecture/16-android-application-shell.md`).

## 2. Manifest

| Item | Value | Source | Class |
|:--|:--|:--|:--|
| `SetupActivity` | The home-screen entry point (`MAIN` + `LAUNCHER`), the folder-picker screen | `android/app/src/main/AndroidManifest.xml` | Verified |
| `ZeroHourActivity` | `MAIN` only (no `LAUNCHER`), `landscape`, `singleInstance`, `alwaysRetainTaskState`, `preferMinimalPostProcessing`; extends `org.libsdl.app.SDLActivity` | `AndroidManifest.xml`, `ZeroHourActivity.java` | Verified |
| `configChanges` | The exact set SDL3 3.4.16's own `android-project` declares (orientation, screenSize, screenLayout, uiMode, keyboard, keyboardHidden, …) | `AndroidManifest.xml` | Verified |
| `uses-feature` Vulkan | `android.hardware.vulkan.version` = `0x401000` (**Vulkan 1.1** packed as `major<<22 | minor<<12`), `android:required="false"` | `AndroidManifest.xml` | Verified |
| `uses-feature` touchscreen | `android.hardware.touchscreen`, `android:required="true"` | `AndroidManifest.xml` | Verified |
| Permissions | `android.permission.CHANGE_WIFI_MULTICAST_STATE` (LAN lobby multicast lock) and `android.permission.INTERNET` (LAN lobby UDP sockets). **No storage permission** | `AndroidManifest.xml` | Verified |
| `isGame` / `appCategory` | `true` / `game`; `hardwareAccelerated true`; `allowBackup true` | `AndroidManifest.xml` | Verified |

The manifest declares Vulkan **not required** on purpose: the renderer is SDL3's GPU API, whose only
Android backend is Vulkan, so a device without a usable Vulkan driver fails loudly at start-up
instead of being refused installation (`AndroidManifest.xml` header comment,
`docs/android-port/architecture/07-gpu-backend-decision.md`).

## 3. Native engine, JNI, SDL version, Android integration

| Item | Value | Source | Class |
|:--|:--|:--|:--|
| Engine artifact | `libmain.so` (`add_library(generals SHARED …)` with `OUTPUT_NAME main`) | `GeneralsMD/Code/CMakeLists.txt:2288-2290` | Verified |
| Entry point | `PosixMain.cpp`'s `main()`, renamed to the exported `SDL_main` by `<SDL3/SDL_main.h>` (its first include) | `GeneralsMD/Code/Main/PosixMain.cpp` | Verified |
| Library loaded | `ZeroHourActivity.getLibraries()` returns `{"main"}` only | `ZeroHourActivity.java` | Verified |
| SDL3 version | **3.4.16**, pinned by commit `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c` | `GeneralsMD/Code/Tools/vendor.sh:463` | Verified |
| SDL3 linkage | **Static** into `libmain.so` (`SDL_STATIC ON`, `SDL_SHARED OFF`); the APK carries no `libSDL3.so` | `GeneralsMD/Code/CMakeLists.txt:895-908`, `docs/android-port/architecture/16-android-application-shell.md` §4 | Verified |
| SDL3 JNI | SDL3's own `JNI_OnLoad` lives inside `libmain.so` (kept alive for the static build with `-Wl,-u,JNI_OnLoad`), so SDLActivity's bindings register when the library loads | `docs/android-port/architecture/16-android-application-shell.md` §4 | Verified |
| Project-owned JNI | The only first-party JNI is in `SdlAndroidPlatform.cpp` (the Wi-Fi multicast lock, via `SDL_GetAndroidJNIEnv`/`SDL_GetAndroidActivity`) | `GeneralsMD/Code/GameEngineDevice/Source/SdlDevice/Common/SdlAndroidPlatform.cpp` | Verified |
| Runtime C++ | `libc++_shared.so` (NDK sysroot, `ANDROID_STL=c++_shared`), staged into `jniLibs/arm64-v8a` | `scripts/build/android/package-android.sh`, `docs/android-port/architecture/18-native-dependency-packaging.md` | Verified |
| Compiler / standard | NDK Clang, C++17, `arm64-v8a`, API 28 | `scripts/build/android/android.toolchain.cmake` | Verified |
| Platform predicates | `ZH_ANDROID` / `ZH_DESKTOP_LINUX` (`PlatformPredicates.h`); CMake `ZH_PLATFORM_ANDROID` never sets `ZH_PLATFORM_LINUX` | `GeneralsMD/Code/Libraries/Include/Platform/PlatformPredicates.h`, `GeneralsMD/Code/cmake/DetectPlatform.cmake` | Verified |
| Storage bootstrap | `establishAndroidStorage()` sets `HOME` from `SDL_GetAndroidInternalStoragePath()` before the engine starts | `GeneralsMD/Code/Main/PosixMain.cpp` | Verified |
| Crash unwinder | `_Unwind_Backtrace` + `dladdr` (bionic has no glibc `backtrace()` at API 28) | `GeneralsMD/Code/Libraries/Include/Platform/StackWalk.h` | Verified |

The engine's `DT_NEEDED` set is Android system libraries plus `libc++_shared.so`; every third-party
library (SDL3, miniaudio, FreeType, glslang, SPIRV-Cross, SDL_shadercross, mbedTLS, zlib, LZH-Light,
GameSpy, FFmpeg) is a **static** archive linked into `libmain.so`
(`docs/android-port/architecture/18-native-dependency-packaging.md`). So the APK's native payload is
exactly two files.

## 4. Graphics backend and Vulkan initialisation

| Item | Value | Source | Class |
|:--|:--|:--|:--|
| Renderer | The fork's own D3D9-shaped device (`posixd3d9`) over **SDL3's GPU API** | `GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/` | Verified |
| Android GPU backend | **Vulkan only.** SDL3 3.4.16 GPU has exactly three backends (Metal, D3D12, Vulkan) and no GLES one; Android enables Vulkan (`SDL_GPU_VULKAN 1`) | `docs/android-port/architecture/07-gpu-backend-decision.md`, `13-android-vulkan-rendering.md`, `.github/workflows/ci-android-test.yml` | Verified |
| GLES / DXVK | Not used. `SDL_OPENGL`/`SDL_OPENGLES` are forced off in the POSIX build | `GeneralsMD/Code/CMakeLists.txt:895-908` | Verified |
| Device creation | `SDL_CreateGPUDevice(SPIRV|MSL, …)`; if that fails, `SDL_CreateGPUDeviceWithProperties` retries dropping optional features | `GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/SdlGpuFrame.cpp:216-245` | Verified |
| Feature fallback order | Attempt 0 = all four; then drops, in order, clip distance, indirect draw first instance, depth clamping, anisotropy (5 attempts total) | `SdlGpuDeviceFeatures.{h,cpp}` | Verified |
| Depth clipping | Every graphics pipeline sets `enable_depth_clip = true` so a device made without `depthClamp` is legal | `docs/android-port/architecture/13-android-vulkan-rendering.md` E3 | Verified |
| Cross-format present | Present to a target whose format differs from the back buffer's goes through the gamma pass (SDL's blit does not swizzle; Android's SDR swapchain is `R8G8B8A8` vs the `B8G8R8A8` back buffer) | `docs/android-port/architecture/13-android-vulkan-rendering.md` E5 | Verified |
| Driver name | `Set_Renderer_Name(SDL_GetGPUDeviceDriver(...))` — the engine records "vulkan" as the driver | `GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/PosixDevice9.cpp:243` | Verified |
| Vulkan device-loss handling | Relies on SDL's Vulkan backend recreating the swapchain on `VK_ERROR_OUT_OF_DATE_KHR`/`VK_ERROR_SURFACE_LOST_KHR`; **no port-specific code** | `docs/android-port/architecture/19-android-lifecycle-and-input-integration.md` ("What is deliberately not here") | Partial |

## 5. Required Vulkan API version, extensions, features, formats, shaders

The floor is **SDL3 3.4.16's own GPU requirement set** (`SDL_gpu.h:221-231`), which the engine
inherits unchanged (`docs/android-port/architecture/07-gpu-backend-decision.md`, E2).

| Requirement | Kind | Note | Source | Class |
|:--|:--|:--|:--|:--|
| Vulkan **1.0** | instance API | the API level SDL3 GPU needs | `SDL_gpu.h:221-231` | Verified |
| `VK_KHR_swapchain` | device extension | present on essentially all Android Vulkan drivers | `SDL_gpu.h:224` | Verified |
| `VK_KHR_maintenance1` | device extension | — | `SDL_gpu.h:225` | Verified |
| `independentBlend` | feature | mandatory | `SDL_gpu.h:226` | Verified |
| `imageCubeArray` | feature | mandatory | `SDL_gpu.h:227` | Verified |
| `sampleRateShading` | feature | mandatory | `SDL_gpu.h:231` | Verified |
| `depthClamp` | feature | **optional** — the port drops it (attempt 3) and clips instead | `SdlGpuDeviceFeatures.h`, doc 13 | Verified |
| `shaderClipDistance` | feature | **optional** — nothing writes a clip distance; dropped first | `SdlGpuDeviceFeatures.h` | Verified |
| `drawIndirectFirstInstance` | feature | **optional** — no indirect draw anywhere; dropped second | `SdlGpuDeviceFeatures.h` | Verified |
| `samplerAnisotropy` | feature | **optional** — used when present; dropped last, then the sampler cache falls back to linear | `SdlGpuDeviceFeatures.h` | Verified |

**Shader format:** the engine asks for `SPIRV | MSL`; on Android the driver reports Vulkan, so
**SPIR-V is the format used** and the MSL half is inert. The pipeline is HLSL → glslang → SPIR-V →
`SDL_shadercross` (`SdlProgramCache.cpp`, `docs/android-port/architecture/13-android-vulkan-rendering.md`).
There is no device-side shader requirement beyond SPIR-V support, which is inherent to Vulkan.

**Formats the device may or may not have** are handled by runtime capability queries, not by
requiring them:

| Capability | Behaviour | Source | Class |
|:--|:--|:--|:--|
| BC (S3TC) texture sampling | Not universal on mobile GPUs. `SdlResourceMirror` expands a block-compressed texture to `B8G8R8A8` when the device cannot sample BC (`bc_ok`). Cost is bandwidth/memory, not correctness | `SdlResourceMirror.cpp:267-271`, `docs/android-port/audit/03-rendering-subsystem.md` §6 | Verified |
| Depth/stencil format | `Sdl_Select_Depth_Format` tries `D24_UNORM_S8_UINT` then `D32_FLOAT_S8_UINT` against `SDL_GPUTextureSupportsFormat`; if neither is usable the device creation fails with "no supported depth/stencil render-target format" | `SdlGpuDepthFormat.h`, `SdlGpuFrame.cpp:255-265` | Verified |

The manifest's `0x401000` (`android.hardware.vulkan.version` = 1.1) is a **declaration only** and is
`required="false"`; the application's real minimum is Vulkan 1.0 (SDL3 GPU's floor).

## 6. Audio, input, touch, storage, permissions, lifecycle

| Area | What is implemented | Source | Class |
|:--|:--|:--|:--|
| Audio backend | Miles 6.5 API implemented over **miniaudio**; Android backend list named explicitly: **AAudio first, OpenSL ES second** | `GeneralsMD/Code/Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp:1196-1201` | Verified |
| Audio lifecycle | `AIL_ex_device_pause`/`resume` (miniaudio `ma_device_stop`/`start`) driven by SDL background/foreground events | `miles_miniaudio.cpp`, `SdlAndroidPlatform.cpp` | Verified |
| Application lifecycle | `SdlAndroidLifecycle` state machine (pure) + an SDL event watch; stops audio and drops the live touch gesture on backgrounding, once | `SdlAndroidPlatform.cpp`, `docs/android-port/architecture/15-…md`, `19-…md` | Verified |
| Touch input | A native gesture layer (`SdlTouch`) that resolves taps against the engine's own pick/evaluate path and uses real clicks for GUI/placement; two-finger pan/zoom/twist; momentum; area selection | `GeneralsMD/Code/GameEngineDevice/{Include,Source}/SdlDevice/GameClient/SdlTouch.{h,cpp}` | Verified |
| Touch/mouse duplication | `SDL_HINT_TOUCH_MOUSE_EVENTS` set to `"0"` on Android; touch-derived mouse events also dropped by id | `SdlTouch.cpp:1009`, `docs/android-port/architecture/14-…md` | Verified |
| Real mouse/keyboard | Still work unchanged (desktop paths untouched; a real mouse id is not a touch id) | `docs/android-port/architecture/14-…md` | Verified |
| On-screen modifier buttons (force fire, waypoint, add-to-selection) | **Not implemented** (the engine exposes the modes; the buttons are UI work) | `docs/android-port/architecture/14-…md` "What is deliberately not here" | Not Implemented |
| Touch keyboard | Uses SDL's IME path (already works); no custom keyboard | `docs/android-port/architecture/14-…md` | Verified |
| Game-folder picker | `SetupActivity` drives `ACTION_OPEN_DOCUMENT_TREE`, takes the read grant as persistable, validates the tree, stages it once (incrementally) into `getExternalFilesDir(null)/gamedata`, and roots the engine there with `-root` | `SetupActivity.java`, `SafTreeStager.java`, `GameFolderStore.java`, `GameFolderValidator.java` | Verified |
| Storage permission | None declared; the single-tree SAF grant is used instead of `MANAGE_EXTERNAL_STORAGE` | `AndroidManifest.xml`, `docs/android-port/architecture/17-…md` §5 | Verified |
| Writable state | `Options.ini`, saves, replays, the crash report and `Registry.ini` go to app-private internal storage (`USER_FOLDERS_ANDROID`), not the install root | `GameEngine/Include/Common/EarlyOptions.h`, `docs/android-port/architecture/15-…md` §2 | Verified |
| Screen-on | `SDL_DisableScreenSaver()` on install | `SdlAndroidPlatform.cpp` | Verified |
| Wi-Fi multicast lock | Taken through JNI and held for the process (needs `CHANGE_WIFI_MULTICAST_STATE`, which is declared) | `SdlAndroidPlatform.cpp`, `AndroidManifest.xml` | Verified |
| Open a web page | Routed through `SDL_OpenURL` (the engine's existing hook) | `docs/android-port/architecture/15-…md` §3 | Verified |
| Fonts | The glyph rasteriser has an Android branch that probes `/system/fonts/` (Roboto, Noto, Droid Sans Mono) because there is no fontconfig and no macOS font directory | `GeneralsMD/Code/Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp:279-318` | Partial (code present, not device-verified) |

## 7. Native runtime libraries and external game data

| Item | Value | Source | Class |
|:--|:--|:--|:--|
| Libraries in the APK | `lib/arm64-v8a/libmain.so` and `lib/arm64-v8a/libc++_shared.so` only | `.github/workflows/ci-android-app.yml` (APK inspection) | Verified |
| System libraries | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libandroid.so`, and (through SDL3) `libvulkan.so` — provided by the device | `docs/android-port/architecture/18-…md` | Verified |
| APK assets | **None.** No `assets/` directory; no `.big`/`.bik`/game data of any kind | `.github/workflows/ci-android-app.yml` (assertions), `docs/android-port/architecture/18-…md` | Verified |
| Game data | The player supplies their **own** Generals and Zero Hour install; validation requires `INIZH.big` at the install root and the base game's `Textures.big` where the engine looks (the folder, `ZH_Generals/`, or a sibling Generals folder) | `GameFolderValidator.java`, `docs/android-port/architecture/17-…md` §4 | Verified |
| Staged copy space | `SafTreeStager.stage` refuses to start if the plan's bytes exceed `stagingRoot.getUsableSpace()`, so free space ≥ the game-data size is required for the one-time copy | `SafTreeStager.java` (`needed > available`) | Verified |
| Absolute game-data size | **Not stated anywhere in the repository.** The docs call it "several-GB"/"multi-GB" without a figure | `docs/android-port/architecture/17-…md`, `08-…md` | Unknown |

## 8. Build-time dependencies vs end-user runtime requirements

| Build-time (developer machine / CI) | End-user runtime (the device) |
|:--|:--|
| Android SDK (platform 35 + 28, build-tools 35), NDK `27.2.12479018`, JDK 17, Gradle 8.12, CMake ≥ 3.20, Ninja, `git`/`curl`/`unzip`/`xz-utils`/`python3`, optional `ccache` | Android **9+ (API 28)**, **arm64-v8a** CPU, a **Vulkan 1.0** driver with the mandatory set (§5), enough RAM and free storage to hold the player's game data |
| `Tools/vendor.sh` fetches pinned third-party sources (SDL3, FFmpeg, FreeType, glslang, …) | None of these are runtime dependencies: they are statically linked into `libmain.so` |
| The NDK cross-compiles the engine; Gradle packages the APK | The device provides the system libraries and the Vulkan loader |

Sources: `scripts/build/android/build-android.sh`, `.github/workflows/ci-android-app.yml`,
`scripts/build/android/package-android.sh`, `docs/android-port/architecture/18-…md`.

## 9. What is verified vs what is not

**Verified in source** (the basis for the hardware floor in the companion document): minSdk 28,
arm64-v8a only, Vulkan 1.0 + `VK_KHR_swapchain` + `VK_KHR_maintenance1` + `independentBlend` +
`imageCubeArray` + `sampleRateShading`, SPIR-V shaders, runtime fallbacks for BC sampling and depth
format, no storage permission, user-supplied game data, two-file native payload.

**Not verified / open:**

- **No device or emulator run.** No Android device or emulator was used, and no build was run for
  this audit. Everything above is a source fact.
- **No tested minimum SoC.** The repository records no device on which the port has run; the
  hardware recommendation is therefore provisional
  ([`ANDROID-HARDWARE-COMPATIBILITY.md`](ANDROID-HARDWARE-COMPATIBILITY.md)).
- **Font rendering on device.** The Android `/system/fonts/` branch exists but is untested; whether
  text renders correctly on a given vendor's font set is **Unknown** without a device.
- **RAM requirement.** No figure is derived anywhere in the repository; any number is provisional.
- **Vulkan physical-device details.** The Android platform API exposes the *advertised* Vulkan API
  version and hardware level (through `PackageManager.getSystemAvailableFeatures()`:
  `FEATURE_VULKAN_HARDWARE_VERSION.version` is the highest `VkPhysicalDeviceProperties::apiVersion`,
  packed exactly like a Vulkan version, and `FEATURE_VULKAN_HARDWARE_LEVEL.version` is the hardware
  level). It does **not** expose the physical-device name, the vendor/device IDs, or the
  per-extension/per-feature presence; the in-app screen reports the advertised version and level and
  marks the rest "Unable to verify" (see the companion document and the in-app section).
- **Real gameplay performance.** Nothing in the repository measures frame rate on any device; BC-less
  GPUs in particular pay a bandwidth cost. Technical compatibility and acceptable performance are
  different questions and are kept separate here.
