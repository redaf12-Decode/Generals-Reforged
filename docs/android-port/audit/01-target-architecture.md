# Target repository architecture audit

Read-only audit of the **Generals Reforged** target repository,
<https://github.com/redaf12-Decode/Generals-Reforged>, for the Android port. No source file,
build file, workflow or dependency was changed while producing it.

Reference repository for the Android work: <https://github.com/redaF12-Tech/GeneralsZH-Android-Port>.

Everything below was located in the checked-out tree and is cited by path and symbol. Where a
component is *not* present it says so, rather than assuming it exists.

## 1. Repository and branch facts

| Item | Value |
|:--|:--|
| Default branch | `main` (`109962c`, untouched) |
| Branch audited | `docs/android-port-audit-01` |
| HEAD at audit time | `baa7dee` — "build(android): add NDK ARM64 cross compilation setup" |
| Clone depth | shallow (`git rev-parse --is-shallow-repository` → `true`) |
| Tracked files | 8797 |
| Remote | `origin` → `https://github.com/redaf12-Decode/Generals-Reforged.git` |

This revision of the audit is against `baa7dee`, which follows the two Android build tasks
(Task 2.1, platform detection — `bdb37a7`; Task 2.2, the NDK cross-compilation setup — `baa7dee`).
Sections that the two commits changed are marked "(after Task 2.1/2.2)"; the earlier revision read
`109962c`, before either landed.

Tracked files by top-level directory:

| Path | Files | Contents |
|:--|--:|:--|
| `GeneralsMD/` | 5108 | Zero Hour game, engine, device layer, libraries, tests |
| `Generals/` | 2873 | Base Generals sources (`Code/`, `Run/`), no CMake of its own |
| `docs/` | 731 | Port docs, references, releases, `docs/android-port/audit/` |
| `Tools/` | 22 | Small host tools (`Balance`, `Chroma`, `PathLab`, `RandomMapLab`, `ScbTool`, `water-tuner`) |
| `.github/` | 22 | `workflows/`, `scripts/`, `media/`, PR template |
| `scripts/` | 2 | `scripts/build/android/` — the Android toolchain file and build driver (Task 2.2) |

Root scripts: `build-linux.sh`, `build-macos.sh`, `build.bat`, `extract-game.sh`, and the
PowerShell checks `dx11-check.ps1`, `gate-60hz.ps1`, `lan-play.ps1`, `replay-check.ps1`,
`shadow-check.ps1`, `tree-check.ps1`, `windows-ci.ps1`, `ai-batch.ps1`.

There is **no top-level `CMakeLists.txt`**. The single CMake entry point is
`GeneralsMD/Code/CMakeLists.txt` (3477 lines), used as `cmake -S GeneralsMD/Code` by
`GeneralsMD/Code/Tools/build-posix.sh` (`SRC="$ROOT/GeneralsMD/Code"`, line 64) and `build.bat`.
There is **no `CMakePresets.json`** anywhere; the Android configure is driven by
`scripts/build/android/build-android.sh` and its `android.toolchain.cmake` (Task 2.2) instead.

### 1.1 Android assets in the target (after Task 2.1/2.2)

At the original audit revision (`109962c`) this section read "Android assets are absent from the
target": no `__ANDROID__`, no Android build files. Two commits changed that, and the honest state
now is **build scaffolding present, app shell absent**:

- **Platform detection knows Android** (Task 2.1). `cmake/DetectPlatform.cmake` (a function of its
  arguments only, so it is testable without a toolchain) sets `ZH_PLATFORM_ANDROID` from
  `CMAKE_SYSTEM_NAME STREQUAL "Android"` or the `ANDROID` variable, tests it *before* the Linux case,
  and **never sets `ZH_PLATFORM_LINUX` for it**. `ZH_PLATFORM_POSIX` stays TRUE. It is registered as
  the `platform_detection_check` test (`GeneralsMD/Code/Tests/test_platform_detection.cmake`), which
  exercises every row including the Android ones under `cmake -P`.
- **Cross-compilation is wired** (Task 2.2): `scripts/build/android/android.toolchain.cmake` (a thin
  layer over the NDK's own toolchain file: ABI `arm64-v8a`, `ANDROID_PLATFORM android-28`,
  `ANDROID_STL c++_shared`, Clang, C++17) and `scripts/build/android/build-android.sh` (validates
  SDK/NDK/tools, configures Ninja Release into `build-android-arm64/`). A configure succeeds.

What is still **absent** — and this is the part the reference has and the target does not:
`android/` (Gradle project, `AndroidManifest.xml`, activities, `res/`), any Java, `jniLibs/`,
`apk/`, `vcpkg.json`, `CMakePresets.json`, and any Android-specific `__ANDROID__` code. The 13
`__ANDROID__` occurrences in the tree are all **inside vendored third-party sources** (SDL3's own
headers and backends, miniaudio, glslang, SPIRV-Cross) — `git grep __ANDROID__` over the engine's
own `GeneralsMD/Code` sources returns nothing. This audit maps the existing macOS/Linux/Windows
abstraction and the two Android build commits, not an app shell.

## 2. Architecture overview

The engine is EA's 2003 Generals/Zero Hour code. The port keeps that architecture and adds a
POSIX platform layer beside the original Win32 one. Three layers matter for Android:

1. **Game engine** (`GeneralsMD/Code/GameEngine`) — platform-neutral game logic, client, GUI,
   network, and subsystem management. It reaches the platform only through the abstract
   `GameEngine` factories (`GameEngine.h:228-240`).
2. **Device layer** (`GeneralsMD/Code/GameEngineDevice`) — one directory per platform:
   `Win32Device/`, `PosixDevice/`, `SdlDevice/`, plus the shared `W3DDevice/` renderer and
   `MilesAudioDevice/`, `VideoDevice/`.
3. **Vendored libraries** (`GeneralsMD/Code/Libraries`) — WWVegas (WW3D2, WWLib, WWMath,
   WWSaveLoad, Miles6), plus fetched third-party code (SDL3, FreeType, glslang, SPIRV-Cross,
   miniaudio, FFmpeg, zlib, LZH-Light, mbedTLS, GameSpy).

Platform selection happens once, in `GeneralsMD/Code/CMakeLists.txt:34-36`, which calls the
`zh_detect_platform` function in `cmake/DetectPlatform.cmake`:

```cmake
# CMakeLists.txt:34
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/DetectPlatform.cmake)
zh_detect_platform("${CMAKE_SYSTEM_NAME}" "${ANDROID}"
  ZH_PLATFORM_WINDOWS ZH_PLATFORM_POSIX ZH_PLATFORM_MACOS ZH_PLATFORM_LINUX ZH_PLATFORM_ANDROID)
```

`zh_detect_platform` (`cmake/DetectPlatform.cmake`) sets `ZH_PLATFORM_WINDOWS` when the system name is
`Windows`; otherwise `ZH_PLATFORM_POSIX`, then **`ZH_PLATFORM_ANDROID` first**, then `Darwin` →
`ZH_PLATFORM_MACOS` and `Linux` → `ZH_PLATFORM_LINUX`:

```cmake
if(zh_system_name STREQUAL "Android" OR zh_android_flag)
  set(_zh_android TRUE)
elseif(zh_system_name STREQUAL "Darwin")
  set(_zh_macos TRUE)
elseif(zh_system_name STREQUAL "Linux")
  set(_zh_linux TRUE)
endif()
```

"Not Windows" is `ZH_PLATFORM_POSIX`; macOS and Linux are refinements used only for the few things
that genuinely differ (window, audio, CPU detection). **Android is a third POSIX system** (Task 2.1):
it keeps `ZH_PLATFORM_POSIX` — the SDL3 window, the event loop, the D3D9-shaped device — and sets
`ZH_PLATFORM_ANDROID`, and it **never sets `ZH_PLATFORM_LINUX`**, so every desktop-Linux specific
(LLD/mold, fontconfig, the X11/Wayland link dependencies, Steam Runtime packaging) stays out of an
Android build by construction. The decision is one function so
`Tests/test_platform_detection.cmake` can check every row under `cmake -P`.

Preprocessor scale in `GeneralsMD/Code` (`.cpp`/`.h`/`.inl`): `_WIN32` ≈ 756 occurrences across 135
files, `__linux__` ≈ 304 across 50 files, `__APPLE__` ≈ 12 files. **The NDK defines `__linux__`** (and
`__unix__` and `__ANDROID__`) — measured with the NDK r27c `aarch64-linux-android28-clang -dM -E` — so
every `#if defined(__linux__)` in this tree also matches Android. See §9.1.

## 3. Important source files and their responsibilities

### 3.1 Entry points

| File | Symbol | Responsibility |
|:--|:--|:--|
| `GeneralsMD/Code/Main/PosixMain.cpp` | `int main(int,char**)` (line 438) | POSIX entry: crash handlers, locale, SDL app metadata, install-root choice, window request, then `GameMain` |
| `GeneralsMD/Code/Main/PosixMain.cpp` | `GameEngine *CreateGameEngine(void)` (line 627) | Returns `NEW SdlGameEngine(s_windowRequest)` |
| `GeneralsMD/Code/Main/WinMain.cpp` | `WinMain` | Windows entry (unchanged Win32 path) |
| `GeneralsMD/Code/GameEngine/Source/Common/GameMain.cpp` | `void GameMain(int,char**)` (line 37) | `CreateGameEngine()` → `init` → `execute` → delete |
| `GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp` | `GameEngine::init(int,char**)` (line 888) | Subsystem construction, in a fixed order |
| `GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp` | `GameEngine::update(void)` (line 3114) | One frame of engine work |
| `GeneralsMD/Code/GameEngine/Source/Common/GameEngine.cpp` | `initSubsystem(...)` (line 205) | Registers a subsystem and its INI paths |

### 3.2 Platform engines (factory providers)

| File | Symbol | Responsibility |
|:--|:--|:--|
| `GeneralsMD/Code/GameEngineDevice/Include/PosixDevice/Common/PosixGameEngine.h` | `class PosixGameEngine : public GameEngine` | Abstract POSIX base; provides file-system, network, web-browser factories |
| `…/Source/PosixDevice/Common/PosixGameEngine.cpp` | `createLocalFileSystem`, `createArchiveFileSystem`, `createNetwork`, `createWebBrowser` | POSIX factories |
| `GeneralsMD/Code/GameEngineDevice/Include/SdlDevice/Common/SdlGameEngine.h` | `class SdlGameEngine : public PosixGameEngine` | Window, SDL video, event pump, audio manager, all W3D factories |
| `…/Source/SdlDevice/Common/SdlGameEngine.cpp` | `init` (156), `createWindow` (173), `serviceWindowsOS` (331), `createAudioManager` (395) | SDL3 platform implementation |
| `…/Include/Win32Device/Common/Win32GameEngine.h` | `class Win32GameEngine` | Windows counterpart (DirectInput, Miles, Direct3D) |

`PosixGameEngine::serviceWindowsOS` is deliberately empty — the event pump belongs to the
subclass with a window (`PosixGameEngine.h:48`). This is the seam an Android engine would occupy:
a sibling of `SdlGameEngine` that owns the Android window and the lifecycle events.

### 3.3 File system, install, configuration

| File | Responsibility |
|:--|:--|
| `…/PosixDevice/Common/PosixLocalFileSystem.cpp` / `.h` | POSIX local file system (case-insensitive path resolution, overlays) |
| `…/PosixDevice/Common/PosixLocalFile.cpp` / `.h` | One open file |
| `…/PosixDevice/Common/PosixFileResolutionDump.cpp` / `.h` | `-dumpFileResolution` diagnostics |
| `…/PosixDevice/Install/PosixInstallRoot.cpp` | Finds the player's Zero Hour install; own static lib `installroot` |
| `GeneralsMD/Code/Libraries/Source/WWVegas/WWLib/zhio.h` | `zh_fopen` family — C-runtime file calls that go through `PosixPath_Resolve` off Windows |
| `…/GameEngine/Source/Common/System/ExecutableDirectory.cpp` | The running executable's directory (`/proc/self/exe` on Linux, `_NSGetExecutablePath` on macOS) |
| `…/GameEngine/Source/Common/System/registry.cpp` | `Registry.ini` in the user data directory replaces the Windows registry off Windows (line 196) |
| `…/GameEngine/Include/Common/EarlyOptions.h` | Reads `Options.ini`/`Registry.ini` **before** the engine exists; also computes the user data directory (`composeUserDataDirectory`, line 302) |
| `…/GameEngine/Include/Common/EarlyCommandLine.h` | Reads the process command line before `main` (`GetCommandLineW` / `_NSGetArgv` / `/proc/self/cmdline`) |

User-data conventions (`EarlyOptions.h:287-297`): `USER_FOLDERS_APPLE` → `~/Library/Application
Support/<leaf>`, `USER_FOLDERS_XDG` → `$XDG_DATA_HOME/<leaf>` else `~/.local/share/<leaf>`. The
override is `Registry.ini`'s own value. `GameState::getSaveDirectory` (`GameState.cpp:982`)
concatenates `"Save\\"` onto `TheGlobalData->getPath_UserData()` — save games, replays and the
user's `Options.ini` all live under the user-data directory.

### 3.4 Input, IME

| File | Responsibility |
|:--|:--|
| `…/SdlDevice/GameClient/SdlInput.cpp` / `SdlInput.h` | SDL3 events → engine keyboard/mouse/text (`SdlInput_dispatch`) |
| `…/SdlDevice/GameClient/SdlKeyboard.cpp` / `SdlKeyTable.cpp` | SDL scancodes → DirectInput (DIK) codes |
| `…/SdlDevice/GameClient/SdlMouse.cpp` | SDL mouse → `MouseIO`, in game pixels |
| `…/SdlDevice/GameClient/AniCursor.cpp` | `.ANI` cursors → SDL cursors |
| `…/GameEngine/Source/GameClient/GUI/IMEManagerPosix.cpp` | Text composition (the `WM_IME_*` counterpart) |
| `…/W3DDevice/Include/W3DDevice/GameClient/W3DGameClient.h:152-153` | Off Windows: `createKeyboard()` → `SdlKeyboard`, `createMouse()` → `W3DMouse` |

There is **no touch, gesture or pen handling anywhere in `SdlDevice`** (grep for `FINGER`, `Touch`,
`Gesture`, `SDL_EVENT_FINGER` is empty). Mouse and keyboard only — the single largest input gap for
a touch-only Android device.

### 3.5 Audio and video

| File | Responsibility |
|:--|:--|
| `GeneralsMD/Code/Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp` | The Miles Sound System 6.5 API reimplemented on miniaudio, so `MilesAudioManager.cpp` compiles unchanged |
| `…/GameEngineDevice/Source/MilesAudioDevice/MilesAudioManager.cpp` | The game's own audio manager (no platform code) |
| `…/SdlDevice/Common/SdlGameEngine.cpp:395` | `SdlGameEngine::createAudioManager` — returns the Miles manager, or a silent one for hidden/`-offscreen` runs unless `ZH_ALLOW_AUDIO=1` |
| `…/GameEngineDevice/Source/VideoDevice/Bink/BinkVideoPlayer.cpp` | Bink movies, decoded through FFmpeg off Windows |
| `…/Libraries/Source/FFmpeg/dist` and `dist-arm64` | **Windows** FFmpeg import libraries + `.dll` (x64 and ARM64, `ffmpeg-build.sh`); the POSIX build never links them — it builds FFmpeg from the source tarball instead (§9.8) |

### 3.6 CD and browser

| File | Responsibility |
|:--|:--|
| `…/PosixDevice/Common/PosixCDManager.cpp` | `CreateCDManager()` → `PosixCDManager`, which creates no drives |
| `…/Win32Device/Common/Win32CDManager.cpp` | Windows counterpart |
| `…/GameEngine/Source/GameNetwork/WOLBrowser/WebBrowserPosix.cpp` | `createWebBrowser()` off Windows; `W3DWebBrowser.cpp` (a COM object) is excluded from POSIX builds |

## 4. Game initialization flow

### 4.1 `main` (`PosixMain.cpp:438`)

1. `installCrashHandlers()` (line 442) — first statement, before any thread.
2. `setlocale(LC_TIME, "")` — the one locale category the game may set.
3. `#ifdef __linux__`: `SDL_SetAppMetadata("Zero Hour Reforged", VERSION_STRING, …)` — Wayland/X11
   window app-id, matched to the `.desktop` file; uses `FLATPAK_ID` when set.
4. Critical-section globals, then `chooseOverlays(argc, argv, overlays)`.
5. `chooseInstallRoot(...)` then `chdir(root)` and `PosixPath_Set_Overlays(overlays)`.
6. `PosixPath_Set_Root_Read_Only(!writableRoot)` — the install root is read-only unless
   `-writableRoot` (a harness switch, never a player's).
7. Window mode settled from `Options.ini` (`getEarlyOptionInt("WindowMode", …)`) then the command
   line (`-win`, `-fullscreen`, `-headless`, `-offscreen`, …) into `s_windowRequest`.
8. `GameMain(argc, argv)` (line 583), then `SdlGameEngine_releaseWindow()` (line 584), then
   `shutdownMemoryManager()`.

### 4.2 `GameMain` (`GameMain.cpp:37`)

```cpp
TheGameEngine = CreateGameEngine();   // → NEW SdlGameEngine(s_windowRequest)
TheGameEngine->init(argc, argv);
TheGameEngine->execute();
GameEngine *engine = TheGameEngine;   // global cleared first: teardown can pump the window
TheGameEngine = NULL;
delete engine;
```

### 4.3 `SdlGameEngine::init` (`SdlGameEngine.cpp:156`)

Calls `createWindow()` (line 173) **before** `GameEngine::init`, mirroring WinMain creating its
window before `GameMain`. `createWindow`:
- returns immediately when `m_request.headless` (no SDL video at all);
- `startOffscreen()` when `m_request.offscreen`;
- else `SDL_Init(SDL_INIT_VIDEO)`, `SDL_SetHint(SDL_HINT_VIDEO_MAC_FULLSCREEN_SPACES, "0")` for
  non-windowed macOS, then makes the `SDL_Window`.

The destructor hands the window on to `SdlGameEngine_releaseWindow` because SDL's video must outlive
the GPU device: under Vulkan, quitting the video unloads the Vulkan library and the device's later
destroy would call into it (comment at `SdlGameEngine.cpp:131-136`).

### 4.4 `GameEngine::init` subsystem order (`GameEngine.cpp:980` onward)

Fixed order via `initSubsystem(...)`; the load-bearing early entries:

| Line | Subsystem |
|--:|:--|
| 980 | `TheLocalFileSystem` |
| 999 | `TheArchiveFileSystem` (must follow the local file system) |
| 1060 | `TheWritableGlobalData` (loads `Data/INI/GameData.ini`) |
| 1112 | `TheDeepCRCSanityCheck` |
| 1114 | `TheGameText` |
| 1124-1133 | `TheScienceStore`, `TheMultiplayerSettings`, `TheTerrainTypes`, `TheTerrainRoads`, `TheGlobalLanguageData`, `TheCDManager` |
| 1140 | `TheAudio` (`createAudioManager()`) |
| 1168-1177 | `TheFunctionLexicon`, `TheModuleFactory`, `TheMessageStream`, `TheSidesList`, `TheCaveSystem`, `TheRankInfoStore`, `ThePlayerTemplateStore`, `TheParticleSystemManager` |
| 1187-1229 | FX, weapon, OCL, locomotor, special-power, damage-FX, armor stores, `TheBuildAssistant`, `TheThingFactory` |
| 1249-1276 | `TheUpgradeCenter`, `TheCommandStore`, `TheGameClient`, `TheAI`, `TheGameLogic`, `TheTeamFactory`, `TheCrateSystem`, `ThePlayerList`, `TheRecorder`, `TheRadar`, `TheVictoryConditions` |
| 1290-1316 | `TheMetaMap`, `TheActionManager`, `TheGameStateMap`, `TheGameState`, `TheGameResultsQueue` |

`TheGameClient` (line 1256) is what makes the W3D window, keyboard and mouse.

## 5. Rendering initialization flow

WW3D2 and W3DDevice are written against Direct3D 9. Rather than rewrite them, the port gives them a
**D3D9-shaped device of its own**, declared by `GeneralsMD/Code/Libraries/Include/Platform/D3D9Posix.h`
(header comment, lines 19-36: "545 of its names appear in the code the POSIX build compiles"). On
Windows the SDK's own `d3d9.h` is used; the POSIX header refuses to compile there.

Chain, top to bottom:

1. `W3DDisplay::init` (`W3DDevice/GameClient/W3DDisplay.cpp:1055`) → `WW3D::Init(ApplicationHWnd)`
   (line 1133), then prelit mode, screen UV bias, texture bit depth, `setWindowed`,
   `DX8Wrapper::Set_Requested_MultiSample_Level`, `Set_Requested_VSync`, etc.
2. `WW3D::Set_Render_Device(...)` (`W3DDisplay.cpp:1226`/`1236`, inside `W3DDisplay::init`) → `DX8Wrapper::Create_Device`
   (`WW3D2/dx8wrapper.cpp:835`) → `D3DInterface->CreateDevice(...)` (lines 903/916/940).
3. Off Windows `D3DInterface` is a `PosixDirect3D9`
   (`PosixDevice/Render/PosixDirect3D9.cpp`): `Direct3DCreate9` (line 31) returns
   `new PosixDirect3D9()`.
4. `PosixDirect3D9::CreateDevice` (line 104) constructs `new PosixDevice9(this, window, parameters)`
   and calls `created->Create_Gpu_Frame(offscreen)`.
5. `PosixDevice9::Create_Gpu_Frame` (`PosixDevice9.cpp:231`) → `SdlGpuFrame::Create` (line 238).
6. `SdlGpuFrame::Create` (`SdlGpuFrame.cpp:192`):
   - `SDL_CreateGPUDevice(SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL, debug, NULL)` —
     SPIR-V for Vulkan (Linux) and MSL for Metal (macOS); `debug` is on only with `ZH_GPU_DEBUG`;
   - `SDL_ClaimWindowForGPUDevice` (the `RenderWindow` is the `SDL_Window*`);
   - `Sdl_Select_Depth_Format(...)` picks a depth/stencil format the device supports;
   - `Create_Targets(width, height)` makes the back buffer.

`RenderWindow` is `HWND` on Windows and an opaque `struct RenderWindowOpaque *` on POSIX
(`Platform/RenderTypes.h`). `PosixDevice9`'s caches are created in `Create_Gpu_Frame`:
`Programs = new SdlProgramCache(...)`, `Pipelines = new SdlPipelineCache(...)`,
`Samplers = new SdlSamplerCache(...)` (lines 243-248).

### 5.1 The SDL3 GPU rendering architecture

All under `GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/`. The `posixd3d9` static
library is where `d3d9.lib` sits on Windows (`CMakeLists.txt:1466-1478`).

| Component | Files | Role |
|:--|:--|:--|
| **SdlGpuFrame** | `SdlGpuFrame.{h,cpp}`, `SdlGpuFrameBatch.cpp`, `SdlGpuFramePathTracing.cpp`, `SdlGpuFrameSmoke.cpp`, `SdlGpuFrameSunShadow.cpp` | The frame on SDL3 GPU: the `SDL_GPUDevice`, the claimed window, the back buffer and depth targets, the transfer/arena byte pools, the command buffer per frame. `Create` at line 192, `Present` at line 1892. `ArenaByte` (line 150) and `BatchDrawLimit` (line 466) are the resource-streaming and batching knobs; `ZH_GPU_BATCH_DRAWS` sets the batch draw limit (default 2048, line 184) |
| **SdlPipelineCache** | `SdlPipelineCache.{h,cpp}` | Key → `SDL_GPUGraphicsPipeline`. The key is render states, program, vertex format, primitive and target (header line 89); a flexible vertex format maps D3D vertex elements to SDL3 vertex attributes at D3's locations (header lines 50-53); sampler descriptions come from D3D9 sampler states (header lines 123-125) |
| **SdlProgramCache** | `SdlProgramCache.{h,cpp}` | HLSL → GPU shader programs, compiled ahead of need and cached |
| **SdlResourceMirrors** | `SdlResourceMirror.{h,cpp}` | CPU-side mirror of GPU resources: format mapping (how a level goes up, BC support, `bc_ok`), upload bytes per level, expansion to B8G8R8A8 (header lines 59-69) |
| **SdlGpuStorage** | `SdlGpuStorage.{h,cpp}` | Validated storage updates; `SdlStorageAllocator` defaults to the real SDL allocator |
| **Post-processing** | `SdlPostProcess.cpp`, `SdlPostAtmosphere.cpp`, `SdlPostDistortion.cpp`, `SdlPostOcclusion.cpp`, `SdlPostShader.cpp` | Full-screen passes |
| **Device shards** | `PosixDevice9Draw.cpp`, `…Resources.cpp`, `…Lighting.cpp`, `…Post.cpp`, `…SunShadow.cpp`, `…Timing.cpp`, `…Warmup.cpp`, `…Capture.cpp` | The `PosixDevice9` implementation split by concern |
| **Resources** | `PosixResources9.cpp`, `PosixImage.cpp`, `PosixImageOps.cpp`, `PosixPixelCodec.cpp` | Texture and surface handling |
| **Scene effects** | `SdlHazardCloud*.cpp`, `SdlGpuParticles.cpp`, `SdlReachOverlay*.cpp`, `SdlSceneCapture.cpp` | Volumetric clouds, GPU particles, reach overlay, capture |

Shader compilation: `sdl3shadercompile.cpp` + `sdl3storage.cpp`
(`Libraries/Source/WWVegas/WW3D2/`), target `sdl3shadercompile`
(`CMakeLists.txt:1037-1040`), linking SDL3, `sdl3_shadercross` (line 1032) and glslang. HLSL stays
the single source language; SPIR-V and MSL are derived (PORTING.md decision 4). The generators
`ffshader.cpp`, `ffvertex.cpp`, `engineshader.cpp` are separate small libraries.

Present: `DX8Wrapper::End_Scene` → `Direct3D11_End_Scene(flip_frames)`; `DX8Device::Present()` calls
`_Get_D3D_Device()->Present(...)`. On POSIX the device's `Present` is `SdlGpuFrame::Present`
(`SdlGpuFrame.cpp:1892`); `-dx11present` owns the window on Windows. `-offscreen` presents into a
texture with `PosixDevice9::Present_Offscreen(hz)` (line 253), paced by `ZH_OFFSCREEN_HZ`.

## 6. Platform abstraction map

| Concern | Windows | POSIX (macOS + Linux) | Notes |
|:--|:--|:--|:--|
| Entry point | `Main/WinMain.cpp` (`WinMain`) | `Main/PosixMain.cpp` (`main`) | `#include <SDL3/SDL_main.h>` |
| Window / events / timers | Win32 `WndProc` | `SdlDevice/Common/SdlGameEngine.cpp` (`createWindow`, `serviceWindowsOS`) | `serviceWindowsOS` pumps `SDL_PollEvent` |
| Displays | Win32 | `SdlDevice/Common/SdlDisplays.cpp` (`TheSdlDisplays`) | `Monitors.h` table |
| Message box / folder dialog | Win32 | `SdlDevice/Common/SdlMessageBox.cpp`, `SdlPanel.cpp` | |
| Keyboard / mouse | DirectInput (`Win32DIKeyboard`, `Win32DIMouse`, `Win32Mouse`) | `SdlKeyboard`, `W3DMouse` over `SdlMouse` | factory split in `W3DGameClient.h:141-153` |
| Text / IME | GDI + `WM_IME_*` | FreeType (`glyphrasteriser.cpp`) + `IMEManagerPosix.cpp` | |
| Local file system | `Win32LocalFileSystem.cpp` | `PosixLocalFileSystem.cpp` | |
| BIG archive | `Win32BIGFile*.cpp` | same two files, built into `posixdevice` | one copy keeps mount order/CRC identical |
| Install discovery | registry | `PosixInstallRoot.cpp` (`installroot`) | |
| Configuration store | registry | `Registry.ini` (`registry.cpp:196`) | |
| CD | `Win32CDManager.cpp` | `PosixCDManager.cpp` | |
| Audio | Miles (`Miles6/xaudio2/miles_xaudio2.cpp`) | Miles on miniaudio (`Miles6/miniaudio/miles_miniaudio.cpp`) | `MilesAudioManager.cpp` unchanged |
| Video (Bink) | Bink API | FFmpeg | |
| Renderer | D3D9 / D3D11 | D3D9-shaped `PosixDevice9` on SDL3 GPU | `Platform/D3D9Posix.h` |
| Executable path | Win32 API | `/proc/self/exe`, `_NSGetExecutablePath` | `ExecutableDirectory.cpp` |
| Command line | `GetCommandLineW` | `_NSGetArgv` / `/proc/self/cmdline` | `EarlyCommandLine.h` |
| Crash reporting | SEH translator + filter | `CrashHandlerPosix.cpp` (signal handlers, `backtrace`) | |
| Network | Win32 sockets | BSD sockets (`udp.cpp`) | Linux-specific `SO_REUSEADDR` handling at `udp.cpp:206`, broadcast bind at `udp.cpp:423` |
| Threading | Win32 threads | `std::thread` (`JobSystem.cpp`, `LogicThread.cpp`) | |

## 7. Build scripts and CMake configuration

- `build-linux.sh` → `exec …/GeneralsMD/Code/Tools/build-posix.sh linux "$@"`.
- `GeneralsMD/Code/Tools/build-posix.sh` — checks the toolchain, fetches dependencies, configures
  with `cmake -S "$SRC" -B "$BUILD"`, builds, runs `ctest`. Distro package hints at lines 114-116
  (apt/dnf/pacman) include the X11/Wayland/Vulkan/fontconfig development packages.
- `GeneralsMD/Code/Tools/vendor.sh` — fetches and patches third-party code: SDL3 3.4.16 (pinned to a
  commit, not a tag), SDL_shadercross `1ff05bec`, glslang `vulkan-sdk-1.4.357.0`, SPIRV-Cross
  `1a616956`, FreeType 2.14.3, miniaudio 0.11.25, zlib 1.1.4, LZH-Light 1.0, mbedTLS 3.6.7,
  FFmpeg 8.1.2 (source tarball), GameSpy. `vendor.ps1` is the Windows side and does **not** fetch
  SDL3 (Windows keeps Win32Device).
- `GeneralsMD/Code/Tools/linux-packages/CMakeLists.txt` — CPack `.deb`/`.rpm` over the portable
  folder (glibc 2.31 baseline).
- `GeneralsMD/Code/Tools/linux-check.sh` — configures, builds and runs ctest inside a
  glibc/libstdc++ compatibility container (package list at lines 144-148).
- `.github/workflows/linux-release.yml` — the Linux build/release workflow: prepares the runner,
  fetches verified tools (`.github/scripts/linux-ci-tools.sh`), resolves an immutable SDK image,
  builds the engine-only portable folder, builds tests, runs CTest with Lavapipe and Xvfb
  (software rendering, no game data), and only on a tag publishes the release.
- `.github/workflows/commit-messages.yml` — Conventional Commits check on pull requests.
- `CONTRIBUTING.md:21` — commits are English, Conventional Commits, imperative, lowercase, no
  trailing full stop, subject under 72 characters.

**Android build path (after Task 2.2).** There is no `CMakePresets.json`; Android is driven by two
new files under `scripts/build/android/`:

- `android.toolchain.cmake` — a thin layer over the NDK's own `build/cmake/android.toolchain.cmake`.
  It normalises `ANDROID_NDK_HOME`/`ANDROID_NDK_ROOT`, then fixes the port's numbers once: ABI
  `arm64-v8a`, `ANDROID_PLATFORM android-28`, `ANDROID_STL c++_shared`, Clang, `CMAKE_CXX_STANDARD 17`.
  It states that it is the `CMakeLists.txt`, not this file, that keeps the desktop-only parts out —
  because they are gated on `ZH_PLATFORM_LINUX`, which Android never sets.
- `build-android.sh` — validates `ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME` (or `--sdk`/`--ndk`), checks
  the host tools, then configures Ninja/Release into `build-android-arm64/` (a tree of its own, so
  the desktop builds are untouched). Flags: `--config`, `--target`, `--clean`, `--no-build`.

A configure with these files succeeds (`CMAKE_SYSTEM_NAME` → `Android`, `CMAKE_C_COMPILER` → the NDK
`clang`), so the *configure* half is proven; a full build and link is not yet (see §12).

Key CMake targets: `generals` (the executable; `PosixMain.cpp` on POSIX, `CMakeLists.txt:2168-2171`),
`sdldevice`, `posixdevice`, `installroot`, `posixd3d9`, `w3ddevice`, `gameengine`, `sdl3shadercompile`,
`sdl3_shadercross`, `sdlinput`, `glyphrasteriser`, `miniaudio`. 137 `add_library`/`add_executable`
and 131 `add_test` calls. CMake minimum 3.20. POSIX compilers get
`-fno-delete-null-pointer-checks` (`CMakeLists.txt:59-61`) because EA's code depends on null-`this`
checks; Linux links with LLD or mold (lines 68-78) because GNU ld cannot resolve the engine's
circular static libraries.

Build options (`CMakeLists.txt:197, 275-287`): `RELEASE_DEBUG_LOGGING` (ON), `ZH_SANITIZE` (empty),
plus `ZH_GAME_DATA`, `ZH_APP_BUNDLE_ID`, `ZH_X86_64_GENERALS`, `ZH_PORTABLE_CMAKE`, and
`ZH_D3DX_PORTABLE`. Runtime toggles include `ZH_GPU_DEBUG`, `ZH_GPU_BATCH_DRAWS`,
`ZH_GPU_VERTEX_STAGING_PROFILE`, `ZH_GPU_CREATION_TRACE`, `ZH_ALLOW_AUDIO`, `ZH_UNATTENDED`,
`ZH_OFFSCREEN_FRAMES`, `ZH_OFFSCREEN_HZ`, `ZH_DATA_DIR`.

## 8. Existing Linux dependencies

From `build-posix.sh:114-116` and `linux-check.sh:144-148`:

- Build: `cmake`, `ninja-build`, `g++`/`gcc-c++`, `make`, `pkg-config`, `python3`, `unifdef`, `lld`.
- Windowing / GPU: `libx11-dev`, `libxext-dev`, `libxcursor-dev`, `libxi-dev`, `libxfixes-dev`,
  `libxrandr-dev`, `libxss-dev`, `libxtst-dev`, `libwayland-dev`, `libxkbcommon-dev`,
  `wayland-protocols`, `libegl-dev`, `libdrm-dev`, `libgbm-dev`, `libvulkan-dev`, `spirv-tools`.
- Text: `libfontconfig-dev` (FreeType is vendored).

Vendored (not system) libraries: SDL3, miniaudio, FreeType, glslang, SPIRV-Cross, FFmpeg, zlib,
LZH-Light, mbedTLS, GameSpy. Linux runs the renderer on **Vulkan through SDL3 GPU**; macOS uses
Metal. Verified on Ubuntu 26.04.1 / GCC 16 / RTX 4060 with driver 595.91.07 (`docs/linux.md:397-420`).

## 9. Potential Android compatibility problems

Each item is grounded in a located file, not assumed.

**Three things were reproduced against the NDK during this audit** and are the concrete blockers, in
build order: (1) `<sys/timeb.h>` in the precompiled header does not exist on bionic (132 compile
errors in the Task 2.2 keep-going log); (2) the FFmpeg custom command builds for the host because its
cross-compile arch is Apple-only, so linking fails with `is incompatible with aarch64linux`; (3)
GameSpy's `pthread_cancel` is absent from bionic. `backtrace()` is a fourth, smaller one (§9.5). The
rest of the section is the map of what has to change around them.

### 9.1 Platform guards: the `__linux__` trap, not `#error` guards

The important correction over the first revision of this audit: **the `#error` guards do not fire on
Android.** Platform detection now sets `ZH_PLATFORM_ANDROID` (Task 2.1, §2), and — the load-bearing
fact — **the NDK defines `__linux__`** (and `__unix__`, `linux`, `__ANDROID__`). Measured with the
NDK r27c compiler:

```
$ aarch64-linux-android28-clang -dM -E - </dev/null | grep -E '__linux__|__ANDROID__|__unix__'
#define __ANDROID__ 1
#define __linux__ 1
#define __unix__ 1
```

So every `#if defined(__linux__)` in this tree also matches Android, and the guards below take their
**Linux branch** rather than the `#else #error`. The consequence is worse than a loud failure: the
build succeeds and the code silently does the desktop-Linux thing on a device where it is wrong.

| File | Guard shape | What Android does | Fires `#error`? |
|:--|:--|:--|:--|
| `GameEngine/Include/Common/EarlyCommandLine.h:90-151` | `#if _WIN32 … #else #if __APPLE__ … #elif __linux__ … #else #error` | Takes `__linux__`: reads `/proc/self/cmdline` (line 151). Compiles, but the process args are the app's, not the game's; SDL passes the real ones through `nativeRunMain` | **no** |
| `GameEngine/Source/Common/System/ExecutableDirectory.cpp:54-65` | `#if __APPLE__ … #elif __linux__ … #else #error` | Takes `__linux__`: `readlink("/proc/self/exe")` (line 62). Compiles, but on Android that is the zygote/`app_process`, not the game's `.so` | **no** |
| `GameEngine/Source/Common/System/GameMemory.cpp:278-283` | `#if __APPLE__ … #elif __linux__ … #else #error` | Takes `__linux__`: `malloc_usable_size` (line 281). **Compiles and links**: bionic declares it in `<malloc.h>` and exports it from `libc.so` at API 28 (verified) | **no** |
| `GameEngine/Source/Common/System/CrashHandlerPosix.cpp:73-82` | `#if __APPLE__ … #elif __linux__ … #else #error` | Takes `__linux__`: includes `<execinfo.h>` (line 77), then calls `backtrace()`. `<execinfo.h>` exists in the NDK sysroot, but `backtrace` is `__INTRODUCED_IN(33)` — at API 28 the declaration is absent, so the **call fails to compile** ("call to undeclared function 'backtrace'") | **no** — the Linux branch fails to compile instead |
| `GameEngine/Source/GameNetwork/udp.cpp:224-228` | `#if IP_RECVDSTADDR … #elif IP_PKTINFO … #else #error` | Takes `IP_PKTINFO` (defined on Android; verified). Compiles; bionic socket behaviour is its own reading | **no** |
| `Libraries/Include/Platform/D3D9Posix.h:44-46` | `#if _WIN32 && !D3D9POSIX_CHECKER #error` | Not `_WIN32` → no error. This is the POSIX device header, and Android is POSIX | **no** |
| `Libraries/Include/Platform/MSVCCompat.h:53` | `#if _UNIX #error` | Android defines neither `_UNIX` nor Westwood's switch (GameSpy's patch renames it to `GSI_UNIX`) | **no** |

Two further Linux-vs-macOS branches assume a desktop BSD-socket behaviour and are worth a separate
reading on bionic: `udp.cpp:206` (`SO_REUSEADDR` sharing) and `udp.cpp:423` (the broadcast bind).

The same trap governs the renderer: `__linux__` appears in 9 renderer files (audit
[`03-rendering-subsystem.md`](03-rendering-subsystem.md) §7), all modern-water feature gates that
would switch on for Android because the NDK defines `__linux__`. Whether that is wanted, or those
gates need to be narrower than `__linux__`, is a real question the port has to answer.

### 9.2 No touch input

`SdlDevice` handles only keyboard, mouse and text (§3.4). A touch-only device has no pointer and no
keyboard; a virtual on-screen keyboard, touch-to-mouse synthesis and gesture handling (pan, pinch
zoom, two-finger scroll) do not exist and would have to be added at the `SdlInput_dispatch` seam
plus an on-screen control overlay. The reference repository's `docs/port/TOUCH_CONTROLS.md` and
`docs/WORKDIR/TOUCH-PARITY-MATRIX.md` are the counterpart work that has no target-side analogue.

### 9.3 Asset and install layout

`PosixInstallRoot.cpp` finds an installed Zero Hour by scanning Steam libraries and known folder
names (`ZERO_HOUR_FOLDER_NAMES`, `steamLibraries`, `libraryfolders.vdf`) and by the archives
`INIZH.big` / `Textures.big`. Android apps ship assets inside the APK/AAB (compressed, no
`/proc/self/exe`, no Steam) and cannot use this discovery or `chdir` to a read-only root the same
way. `EarlyOptions.h`'s user-data conventions (`USER_FOLDERS_APPLE`/`USER_FOLDERS_XDG`) likewise do
not match Android's app-private files directory. `zhio.h`'s path resolution assumes a case-sensitive
volume with real paths.

### 9.4 Executable-path and command-line assumptions

`ExecutableDirectory.cpp` reads `/proc/self/exe` (Linux) or `_NSGetExecutablePath` (macOS);
`EarlyCommandLine.h` reads `/proc/self/cmdline` or `_NSGetArgv`. Android has neither, and — the
point of §9.1 — the `#else #error` does **not** catch it, because the NDK defines `__linux__`: the
Linux branch is taken and `/proc/self/cmdline` (EarlyCommandLine.h:151) and `/proc/self/exe`
compile. They are wrong on Android for a different reason than a missing branch: the process is the
zygote/`app_process`, so `/proc/self/cmdline` is not the game's command line and `/proc/self/exe` is
not the game's `.so`. On Android the real arguments arrive through SDL's `nativeRunMain` and the
library path is what the loader was handed, so both need an Android branch rather than the Linux one.

### 9.5 Crash reporting and memory

`CrashHandlerPosix.cpp` includes `<execinfo.h>` (line 77) and calls `backtrace()` under its `__linux__`
branch. `<execinfo.h>` *is* in the NDK sysroot, but `backtrace`/`backtrace_symbols`/`backtrace_symbols_fd`
are `__INTRODUCED_IN(33)` — at the port's `android-28` baseline the declaration is absent and the
call fails to compile (`call to undeclared function 'backtrace'`; verified with the NDK compiler at
API 28 vs API 33). So this is a **compile error on the Linux branch**, not the `#error` guard. The
same applies to `PosixDevice9Draw.cpp:1610-1612`, which calls `backtrace` under `#if defined(__linux__)`.
The register reader further down has an `#elif defined(__linux__) && defined(__aarch64__)` branch
(`CrashHandlerPosix.cpp:407`) that reads `uc_mcontext.regs` — the right *shape* for arm64, but it
compiles only once the `backtrace()` calls above it do.
`GameMemory.cpp`'s `malloc_usable_size` (line 280) is the opposite case: bionic declares it and
`libc.so` exports it at API 28, so that branch compiles and links — but it relies on glibc's 16-byte
`calloc` alignment for `MEM_BOUND_ALIGNMENT` (comment near line 3524), and bionic's allocator differs;
the global `new`/`delete` override is disabled only under `ZH_SANITIZER_BUILD` or `__linux__`.

### 9.6 Renderer backend

The POSIX renderer needs `SDL_GPU_SHADERFORMAT_SPIRV` (Vulkan) or `MSL` (Metal)
(`SdlGpuFrame.cpp:196`). Android's SDL3 GPU backend is **Vulkan** (via `SDL_GPU_SHADERFORMAT_SPIRV`),
so the shader path is the same as Linux's, but the vendored SDL3 (`vendor.sh`, pinned 3.4.16) is
configured with `SDL_OPENGL`/`SDL_OPENGLES` forced off (`CMakeLists.txt:903-908`) — the Android
build must keep Vulkan and add the Android video driver. The SDL3 Android Java glue *is* present in
the fetched tree — `Libraries/Source/SDL3/android-project/app/src/main/java/org/libsdl/app/`
(`SDLActivity.java`, `SDL.java`, `SDLSurface.java`, `SDLAudioManager.java`, …) — but the whole
`Libraries/Source/SDL3` tree is **fetched, not committed** (its `.gitignore` ignores everything but
itself), so those files exist only after a `vendor.sh` run and are not in the repository.
`Main/PosixMain.cpp` is an ordinary `main`, not SDL3's `SDL_main` re-entry, so the Android entry path
is missing (see §11 and the reference's `SDL3Main.cpp` pattern).

### 9.7 Windowing, displays and fullscreen

`SdlGameEngine::createWindow` uses `SDL_SetHint(SDL_HINT_VIDEO_MAC_FULLSCREEN_SPACES, …)` and
`SDL_GetWindowSizeInPixels`/points-vs-pixels density maths. `SdlDisplays` builds the `Monitors.h`
table from SDL displays and names them `"\\.\DISPLAY<n>"`. Android has one display, no windowed vs
fullscreen choice, and a surface lifecycle (pause/resume/`surfaceDestroyed`) that SDL3 reports as
different events; the GPU device and swapchain must be recreated on surface loss. Nothing in
`serviceWindowsOS` (`SdlGameEngine.cpp:331`) handles pause/resume.

### 9.8 Audio, video and threading

`miles_miniaudio.cpp` uses miniaudio, which supports AAudio/OpenSL ES on Android, so the audio path
is plausibly reusable, but the null-backend/`ZH_ALLOW_AUDIO` gating in `SdlGameEngine.cpp:392-405`
and the `WAVE_FORMAT_IMA_ADPCM` guard need checking on Android's decoders.

FFmpeg for Android is **not present**, but the target's own path is a source build, not a prebuilt:
`Tools/ffmpeg-build-posix.sh` compiles FFmpeg 8.1.2 from `Libraries/Source/FFmpeg/ffmpeg-8.1.2.tar.xz`
(Bink demuxer + decoders + swscale/swresample only, `--disable-asm --disable-autodetect`, LGPL 2.1).
The two prebuilt `dist/` and `dist-arm64/` trees are **Windows import libraries + `.dll`**
(`ffmpeg-build.sh`, x64 and ARM64), not usable off Windows — the POSIX build never links them.

The FFmpeg step is a **concrete, verified blocker**, and it is a wiring defect, not a portability one.
The CMake block (`CMakeLists.txt:1765-1807`) passes the script
`${CMAKE_C_COMPILER} "${FFMPEG_POSIX_CFLAGS}" "${FFMPEG_POSIX_ARCH}"`, and `FFMPEG_POSIX_ARCH` is set
**only** inside `if(APPLE AND CMAKE_OSX_ARCHITECTURES)` (line 1788-1799). On Android it is `""`, so
the script does not pass `--arch`/`--enable-cross-compile`, and `${CMAKE_C_COMPILER}` is the NDK's
**bare** `clang` (`…/llvm/prebuilt/linux-x86_64/bin/clang`, the cache value — not the
`aarch64-linux-android28-clang` wrapper), which without a `--target` compiles for the **host**. The
result is a host x86_64 `libavformat.a`, and every engine target that links it fails exactly as the
Task 2.2 keep-going build did: `ld.lld: error: ffmpeg/lib/libavformat.a(bink.o) is incompatible with
aarch64linux` (`/tmp/keepgoing.log:5642`; the ninja command for that target ends
`…/bin/clang "" ""`).

Measured during this audit: run the same script with the NDK *target* compiler and a target arch —
`ffmpeg-build-posix.sh <tarball> <work> <prefix> aarch64-linux-android28-clang "" aarch64` — and it
**succeeds**, producing five `ELF64`/`AArch64` static archives (`libavformat.a`, `libavcodec.a`,
`libswscale.a`, `libswresample.a`, `libavutil.a`) with no bionic-hostile undefined symbols
(`llvm-nm -u` shows none of `backtrace`, `malloc_usable_size`, `pthread_cancel`, `__isoc99_*`,
`__libc_*`). So the fix is to give the Android branch of that CMake block a cross-compile target —
the script already supports it; the Apple-only guard is what is missing an `ANDROID` arm.

One header the engine's precompiled header pulls in **does** block the build before any of this:
`GameEngine/Include/Precompiled/PreRTS.h` includes `<sys/timeb.h>` on the non-Windows side (lines 86
and 117), and **bionic has no `sys/timeb.h`** (verified: absent from the NDK r27c sysroot). It is the
single include that produced the bulk of the Task 2.2 keep-going build's compile errors; on Android it
needs a guard or a substitute (`ftime`/`struct timeb` are a legacy BSD interface).

`JobSystem.cpp` and `LogicThread.cpp` use `std::thread` and `std::thread::hardware_concurrency()`;
Android restricts some thread priorities and background CPU use.

### 9.9 Configuration and lifecycle

`Registry.ini` and `Options.ini` are read from the user-data directory before the engine exists
(`EarlyOptions.h`); Android has no such directory until the activity has run once, and app data may
be cleared. The reference repository keeps its own Android scaffolding (`android/app` with
`AndroidManifest.xml`, `build.gradle`, `jniLibs`) — none of that exists here and none may be
introduced by this read-only audit.

### 9.10 GameSpy SDK on bionic

The engine's online layer compiles the real GameSpy SDK
(`GeneralsMD/Code/Libraries/Source/GameSpy`, BSD-3-Clause, built by
`add_subdirectory(.../GameSpy)` at `CMakeLists.txt:875`) rather than stubs. Its Linux platform code
`src/common/linux/gsthreadlinux.c:72` calls `pthread_cancel`, which **bionic does not provide** —
verified: it is neither declared in `<pthread.h>` nor exported by `libc.so` at API 28. This is a
*compile* error under the NDK (implicit declaration rejected), and it was the first GameSpy failure
seen in the Task 2.2 keep-going build. `gsthreadlinux.c` is selected by
`src/common/gsplatformthread.c` under `#elif defined(__linux__)`, which the NDK defines, so Android
takes the Linux platform sources with no change. The reference port works around the one call with a
raw compile option, `-Dpthread_cancel(t)=(0)`, because a pinned FetchContent cannot be source-patched;
the target vendors GameSpy with `Tools/vendor.sh`, so it can either carry the same one-line define or
patch `gsthreadlinux.c`. GameSpy also ships `src/common/macosx/` and `src/common/win32/` but **no
Android directory**, and no `__ANDROID__` anywhere.

## 10. Files most likely to need Android-specific changes

Grouped by seam. (This is a map for later tasks, not a change list.)

1. **Build**: `GeneralsMD/Code/CMakeLists.txt` (platform detection now `cmake/DetectPlatform.cmake`
   at 34-36; SDL3 config 900-935; the `generals` executable 2168-2171), `GeneralsMD/Code/Tools/vendor.sh`
   (SDL3/FFmpeg fetches), the Android build files already added
   (`scripts/build/android/android.toolchain.cmake`, `build-android.sh`), plus the Android Gradle/
   manifest files still absent.
2. **Entry / lifecycle**: `GeneralsMD/Code/Main/PosixMain.cpp` (a new Android entry),
   `SdlDevice/Common/SdlGameEngine.cpp` (`createWindow`, `serviceWindowsOS` for pause/resume).
3. **Platform guards**: `EarlyCommandLine.h`, `ExecutableDirectory.cpp`, `GameMemory.cpp`,
   `CrashHandlerPosix.cpp`, `udp.cpp` (the `__linux__`-branch sites in §9.1), and
   `GameEngine/Include/Precompiled/PreRTS.h` (`<sys/timeb.h>`, §9.8).
4. **Install / file system**: `PosixDevice/Install/PosixInstallRoot.cpp`,
   `PosixDevice/Common/PosixLocalFileSystem.cpp`, `PosixDevice/Common/PosixLocalFile.cpp`,
   `Libraries/Source/WWVegas/WWLib/zhio.h`, `GameEngine/Include/Common/EarlyOptions.h`.
5. **Input**: `SdlDevice/GameClient/SdlInput.cpp`, `SdlMouse.cpp`, `SdlKeyboard.cpp`,
   `W3DDevice/Include/W3DDevice/GameClient/W3DGameClient.h` (touch and on-screen controls).
6. **Rendering**: `PosixDevice/Render/SdlGpuFrame.cpp` (device creation, swapchain, present),
   `SdlGpuDepthFormat.h`, and the SDL3 video/GPU configuration in CMake.
7. **Audio / video**: `Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp`,
   `GameEngineDevice/Source/VideoDevice/Bink/BinkVideoPlayer.cpp`, and the FFmpeg custom-command
   block (`GeneralsMD/Code/CMakeLists.txt:1765-1807` — the Apple-only `FFMPEG_POSIX_ARCH` guard needs
   an Android arm; §9.8).
8. **Threading**: `GameEngine/Source/Common/System/JobSystem.cpp`,
   `GameEngine/Source/Common/System/LogicThread.cpp`.
9. **Configuration**: `GameEngine/Source/Common/System/registry.cpp`,
   `GameEngine/Source/Common/System/SaveGame/GameState.cpp` (save/replay directory).

## 11. Unresolved questions

1. Which Android SDL3 backend is intended — Vulkan only, or also GLES as a fallback? The current
   CMake turns both OpenGL drivers off. (Android's SDL3 GPU path is Vulkan, so Vulkan is the one that
   matters for `PosixDevice9`; GLES would only matter for SDL's 2D renderer, which is off.)
2. How should the Zero Hour game data reach the device — user-provided files in app storage,
   a first-run import, or a sideloaded data pack? Nothing in the tree answers this. The reference
   requires the player to point at a folder and copies nothing.
3. What is the target ABI/API level and NDK version? **Now answered for the build**: arm64-v8a,
   `android-28`, NDK r27c (`scripts/build/android/android.toolchain.cmake`, Task 2.2). The API level
   is the open part: `backtrace()` needs API 33 (§9.5), so either the baseline rises or the crash
   handler takes another route.
4. Is a native-activity or SDLActivity-style entry wanted? The reference's choice is `SDLActivity`
   (game as `libmain.so`, `SDL_main` rename); the target's `PosixMain.cpp` is a plain `main`.
5. Does the build fingerprint / multiplayer CRC (`BuildFingerprint.manifest`) need an Android axis so
   Android players can join the same LAN games?
6. Are the tests (131 `add_test` targets) expected to run under an Android emulator, or only on
   desktop, given several require a GPU and game data?
7. How should `__linux__` be narrowed where Android must differ (the renderer's water gates, the
   crash handler, the `sys/timeb.h` include) — a new `__ANDROID__` guard, or a platform header as
   port rule 2 asks?

## 12. Provenance

- All paths are relative to the repository root and were read at `baa7dee`, branch
  `docs/android-port-audit-01` (default `main` at `109962c`). The first revision of this document
  read `109962c`; this one incorporates the two Android build commits (§1.1) and corrects §9.1.
- No file was created, renamed, deleted or modified during the investigation except this document.
- Compiler facts (§2, §9.1, §9.5, §9.8, §9.10) were measured with the NDK r27c compiler
  (`aarch64-linux-android28-clang`, `-dM -E`, `llvm-nm`, and compiling one-line probes at API 28 and
  33), and the FFmpeg cross-build with `Tools/ffmpeg-build-posix.sh` into a scratch prefix. Nothing
  was written into the repository by those measurements.
- Symbols and line numbers are as of the commit above and may move.
