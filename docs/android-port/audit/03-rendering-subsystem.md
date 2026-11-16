# Target rendering subsystem audit

Read-only audit of the Generals Reforged **renderer** — the POSIX Direct3D-9-shaped device on SDL3
GPU — as it exists at `109962c`. Companion to [`01-target-architecture.md`](01-target-architecture.md)
(whole-repo architecture) and [`02-reference-architecture.md`](02-reference-architecture.md)
(the reference port's different stack). No source file was changed.

The renderer is the largest and highest-risk Android surface in the target: it is ~22 200 lines of
hand-written SDL3-GPU code that has only ever run on macOS (Metal) and Linux (Vulkan), it compiles
shaders at runtime through glslang, and it selects depth/colour formats and texture compression from
GPU capability queries. This document maps it precisely enough to plan the Android work.

## 1. Where the renderer lives

All of it is under
`GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/` (71 files, ~22 200 lines), built into
the static library **`posixd3d9`** (`CMakeLists.txt:1464-1478`), which stands in for `d3d9.lib`:

```cmake
add_library(posixd3d9 STATIC ${POSIXD3D9_SOURCES} .../ParticleGpuShader.cpp)
target_link_libraries(posixd3d9 PRIVATE SDL3::SDL3-static sdl3shadercompile
                                          engineshader ffshader ffvertex postprocess)
```

It is gated by `ZH_PLATFORM_POSIX` (`CMakeLists.txt:27-35`), which is **any non-Windows system** —
`CMAKE_SYSTEM_NAME STREQUAL "Windows"` picks Windows, everything else is POSIX with
`ZH_PLATFORM_MACOS` / `ZH_PLATFORM_LINUX` as refinements. **Android is a Linux kernel, so an Android
NDK build lands in the `ZH_PLATFORM_POSIX` + `ZH_PLATFORM_LINUX` branch by default.** This matters:
`ZH_PLATFORM_LINUX` is currently *not* the same thing as "desktop Linux", and several Linux-only code
paths (X11/Wayland assumptions, fontconfig, `libdrm`/`libgbm` links) will be wrong on Android. See §7.

### File map

| File | Lines | Responsibility |
|:--|--:|:--|
| `PosixDevice9.h` | 696 | The device class; the D3D9-shaped `IDirect3DDevice9` implementation |
| `PosixDevice9.cpp` | 1086 | Device state, bindings, scenes, presentation, teardown, `Create_Gpu_Frame` |
| `PosixDevice9Draw.cpp` | 2095 | The draw path: `DrawPrimitive*`, state → pipeline key, `DrawIndexedPrimitive` |
| `PosixDevice9Resources.cpp` | 522 | Every resource-creating and surface method, `Clear`, `Create_Implicit_Surfaces` |
| `PosixDevice9Capture.cpp` | 640 | Scene capture (colour/depth), water snapshot hooks |
| `PosixDevice9Post.cpp` | 656 | Post-processing chain invocation |
| `PosixDevice9SunShadow.cpp` | 467 | Sun-shadow depth pass |
| `PosixDevice9Lighting.cpp` | 99 | Lighting constants / stage handling |
| `PosixDevice9Timing.cpp` | 504 | GPU timing / frame instrumentation (`ZH_GPU_TIMING*`) |
| `PosixDevice9Warmup.cpp` | 215 | Pipeline/warm-up precompilation |
| `PosixDirect3D9.cpp` | 143 | `Direct3DCreate9`, the single adapter, mode list, `CreateDevice` |
| `PosixD3D9Caps.cpp` | 225 | Caps, `Check*` answers, adapter identifier |
| `PosixResources9.cpp/.h` | 730 / 323 | The D3D9 resource classes (texture, surface, buffer, state block) |
| `PosixImage.cpp/.h`, `PosixImageOps.*`, `PosixPixelCodec.*` | ~880 | CPU image storage and format conversion (A2) |
| **`SdlGpuFrame.h/.cpp`** | 633 / 2153 | The SDL3 GPU frame: device, claimed window, targets, batch, `Present` |
| `SdlGpuFrameBatch.cpp` | 748 | The recorded batch: copy pass + render passes, staging/upload arenas |
| `SdlGpuFrameSmoke.cpp`, `SdlGpuFrameSunShadow.cpp`, `SdlGpuFramePathTracing.cpp` | ~680 | Specialised frame passes |
| **`SdlPipelineCache.h/.cpp`** | 168 / 566 | Pipeline + sampler caches, D3D9 state → SDL3 GPU key |
| **`SdlProgramCache.h/.cpp`** | 127 / 398 | Generated HLSL → SPIR-V/MSL program cache |
| **`SdlResourceMirror.h/.cpp`** | 131 / 500 | GPU copies of A2 CPU textures/buffers |
| `SdlGpuStorage.h/.cpp`, `SdlConstants.h`, `SdlGpuDepthFormat.h`, `SdlRenderMatrix.h`, `SdlCreationLog.h` | ~460 | Storage buffers, uniform layouts, depth-format policy |
| `SdlPostProcess*`, `SdlPostAtmosphere/Distortion/Occlusion`, `SdlPostShader` | ~1500 | The post chain (AO, atmosphere, distortion, occlusion) |
| `SdlHazardCloud*` (7 files), `SdlReachOverlay*`, `SdlGpuParticles*` | ~3200 | Hazard clouds, reach overlay, GPU particles |

Supporting libraries: `sdl3shadercompile` (`Libraries/Source/WWVegas/WW3D2/sdl3shadercompile.cpp`,
205 lines), the shader **generators** `ffvertex.cpp` / `ffshader.cpp` / `engineshader.cpp`, and
`SDL_shadercross` (one C file). The shader generators are unchanged from Windows and still emit HLSL.

## 2. The rendering entry chain

```
PosixMain.cpp main():583   GameMain(argc, argv)
  GameMain.cpp:37          TheGameEngine->init(); TheGameEngine->execute()
    GameEngine::init()     -> createGameClient() -> W3DGameClient -> W3DDisplay
       W3DDisplay.cpp:1226 WW3D::Set_Render_Device(0, w, h, bits, windowed, ...)
         ww3d.cpp:455      WW3D::Set_Render_Device -> DX8Wrapper::Set_Render_Device
           dx8wrapper.cpp  DX8Wrapper::Init -> Direct3DCreate9(D3D_SDK_VERSION)
             PosixDirect3D9.cpp:29  Direct3DCreate9  -> new PosixDirect3D9
             PosixDirect3D9.cpp:104 CreateDevice    -> new PosixDevice9(...)
               PosixDevice9::Create_Gpu_Frame(offscreen)   [PosixDevice9.cpp:231]
                 SdlGpuFrame::Create(window, w, h, error)  [SdlGpuFrame.cpp:192]
                   SDL_CreateGPUDevice(SPIRV|MSL, debug, NULL)
                   SDL_ClaimWindowForGPUDevice(device, window)
                   Sdl_Select_Depth_Format(...)  -> D24S8 / D32FS8
                   Create_Targets(w, h)          -> back buffer + depth
                 new SdlProgramCache(device)
                 new SdlPipelineCache(device)
                 new SdlSamplerCache(device)
                 new SdlResourceMirrors(frame)
```

`Direct3DCreate9` is the seam. `dx8wrapper.cpp` calls it **directly** on non-Windows (the
`#if defined(_WIN32)` block at line 448 loads `d3d9.dll` by hand only on Windows); off Windows the
symbol comes from `PosixDirect3D9.cpp`. This is why the reference port's `dx8wrapper.cpp` needed no
Android change either — but note the target's `dx8wrapper.cpp` has **no `dlopen` at all** off
Windows: it links `posixd3d9` statically, so an Android `.so` must contain the whole renderer, and
there is no runtime-loadable D3D library to drop in.

### Per-frame

`W3DDisplay::draw` → `WW3D::Begin_Render` / `WW3D::End_Render` → … → `PosixDevice9::Present`
(`PosixDevice9.cpp:594`) → `SdlGpuFrame::Present(ramp)` (`SdlGpuFrame.cpp:1892`):

1. `SDL_AcquireGPUCommandBuffer`.
2. `Record_Batch(commands)` — replay the recorded draws.
3. `Copy_To_Front(commands)` — back buffer → front.
4. `SDL_WaitAndAcquireGPUSwapchainTexture(commands, Window, &swapchain, &w, &h)`.
5. `Present_Into(commands, swapchain, w, h, SDL_GetGPUSwapchainTextureFormat(device, window), ramp)`
   — the gamma-ramp pass.
6. `Submit(commands)` + `End_Batch()`.

The frame also tracks visibility: `SDL_GetWindowFlags & (HIDDEN|OCCLUDED|MINIMIZED)` decides whether
the frame is "not visible" (`NotVisible`), and a null swapchain (minimised) is **not** a failure.

## 3. `SdlGpuFrame` — the SDL3 GPU frame

`SdlGpuFrame` owns the `SDL_GPUDevice`, the claimed `SDL_Window`, the back buffer + depth, and the
recording batch. Key API (`SdlGpuFrame.h:143+`):

| Method | Role |
|:--|:--|
| `Create(RenderWindow, w, h, error)` | `SDL_CreateGPUDevice` + claim + depth format + targets |
| `Resize(w, h, *failure)` | Resize attachments; failure keeps the old ones |
| `Set_Target` / `Back_Buffer_Target` | Where clears/draws go (the back buffer by default) |
| `Clear_Back_Buffer` / `Clear_Rect` | Whole-target and rectangle clears |
| `Stage`, `Upload_Space`, `Constants` | Arenas for vertex/index bytes and uniform blocks |
| `Record_Draw` / `Record_Native_Draw[_Batch]` | Record a draw packet |
| `Flush` | Run the batch (copy pass + render passes) |
| `Present(ramp)` / `Present_To` | Swapchain or offscreen present, through the gamma ramp |
| `Capture_Scene_Colour` / `Capture_Scene_Depth` | Independent scene captures |
| `Depth_Format`, `Supports_Scene_Depth`, `Depth_Usage` | Capability queries |
| `Read_Back` / `Read_Depth` | Diagnostic readback |

Design notes that matter for Android:

- **Nothing is issued as the engine asks for it.** Clears and draws are recorded into a batch, with a
  copy of every byte a draw reads that can still change (the staging stream) and the uploads the GPU
  copies need. `Flush` runs one copy pass, then render passes that replay the records in order. A
  whole-target clear is the *load operation* of the next pass; a partial clear is a clear draw.
- **Main thread only**, "as every SDL GPU call is" (header comment). Android must keep the render
  thread == the thread that created the GPU device; the reference port had to reason about this too.
- **Staging limit** 64 MiB (`SdlGpuFrameBatch.cpp:25`, `BATCH_LIMIT`), 16-byte alignment; the batch
  flushes early when full (`Batch_Is_Full`, `ZH_GPU_FLUSH_LIMIT`).
- **No window is a real case** (`-headless`, `-offscreen`): the device is created with a null
  `RenderWindow` and either draws nothing (`-headless`, no GPU device at all) or into its own target
  (`-offscreen`, `ZH_OFFSCREEN_FRAMES`).
- **`RenderWindow`** is opaque off Windows: `typedef struct RenderWindowOpaque *RenderWindow`
  (`Platform/RenderTypes.h:75`), "C2's window; the device never looks inside". `SdlGpuFrame::Create`
  is the one place it is cast back: `reinterpret_cast<SDL_Window*>(window)`.

## 4. `SdlPipelineCache` — state translation

SDL3 GPU bakes blend, depth, stencil and rasterizer state into the pipeline, so `SdlPipelineKey`
(`SdlPipelineCache.h:61`) is a POD holding: the vertex/pixel `SDL_GPUShader*`, `FVF`,
`ColourFormat`, `DepthFormat`, depth/slope bias, primitive, fill, cull, the full blend state, write
mask, depth test/write/compare, and the full stencil state. It is compared and hashed **by its
bytes**. The stencil reference, viewport, scissor and blend constant are dynamic (not in the key).

`Sdl_Pipeline_Key(render_states[256], primitive, vs, ps, fvf, colour_format, depth_format, key,
refusal)` is a free function (testable without a GPU). **What D3D9 has and SDL3 GPU has not is a
refusal with a reason, never an approximation** — the refusals found so far are: blend-weighted
position (vertex blending), point fill mode, and unknown primitive/cull/blend/depth/stencil values
(`SdlPipelineCache.cpp:58,207,213,220,230,253,272`). Refusals are logged once per key and counted.

`SdlSamplerCache` keys on a stage's **14 D3D9 sampler states** (`memcmp`), with a 4-entry recent-hit
ring; `Sdl_Sampler_Description` returns a refusal for an address mode or filter SDL3 GPU has not got.

`Sdl_Vertex_Layout(fvf, layout, refusal)` maps a flexible vertex format to SDL3 attributes at D3D's
locations: position 0, normal 1, diffuse 2, specular 3, texcoord set n at 4+n; point size is skipped;
sets past the fourth are dropped. Max 8 attributes.

## 5. `SdlProgramCache` — shader compilation

`SdlProgramCache` (`SdlProgramCache.h:52`) keeps one `SdlProgram` per D3 description:

```cpp
struct SdlProgram {
    SDL_GPUShader *Shader;      // null for a refusal
    std::string Key;            // D3's key for the description
    std::string Refusal;        // why, for a refusal
    unsigned SamplerSlots, UniformBuffers;
    std::vector<int> SlotTexture, SlotSampler;   // per slot: which stage's texture/sampler
    unsigned Uses;
};
```

Entry points: `Vertex_Program`, `Water_Vertex_Program`, `Pixel_Program`, `Sun_Pixel_Program`,
`Shore_Pixel_Program`, and the engine's own programs keyed as the D3D11 backend keys them. A
description the generator refuses, or a program the compiler or the device refuses, is **kept as a
refusal** so it is tried once and counted every time.

The compile chain (`sdl3shadercompile.cpp`):

```
generator HLSL (ffvertex/ffshader/engineshader)
  -> SDL3_Compile_HLSL_To_SPIRV   glslang HLSL front end   (sdl3shadercompile.cpp:64)
       glslang_initialize_process() once; GLSLANG_SOURCE_HLSL;
       link with SPV_RULES | VULKAN_RULES | READ_HLSL; program_SPIRV_generate
  -> SDL3_Translate_SPIRV_To_MSL  SDL_ShaderCross_TranspileMSLFromSPIRV   (line 18/29)  [Metal only]
  -> SDL_ShaderCross_CompileGraphicsShaderFromSPIRV (line 95)
```

**Android needs SPIR-V, not MSL.** `SDL_CreateGPUDevice` is asked for `SPIRV | MSL`
(`SdlGpuFrame.cpp:197`); on Android the driver reports the Vulkan backend, so SPIR-V is the format
that is used and the MSL half is inert. That is fine — but it means Android takes the same path as
Linux, so **the glslang HLSL front end must work on the NDK toolchain**, and `SDL_shadercross` must
build there (the target already compiles it as one C file, `CMakeLists.txt:1032`). glslang's HLSL
front end is deprecated upstream (`PORTING.md` decision 4) with DXC as the stated fallback.

## 6. `SdlResourceMirror` — CPU → GPU resource copies

`SdlResourceMirrors` (`SdlResourceMirror.h:60`) makes a GPU copy of an A2 CPU texture/buffer the
first time a draw needs it, and refreshes it whenever the A2 object's `version()` moves.

- Texture formats: DXT1/2/3/4/5 → BC1/BC2/BC3 where the GPU samples them; A8R8G8B8 → B8G8R8A8 byte
  for byte; **everything else is expanded to B8G8R8A8 with A2's codec, because SDL3 GPU has no
  swizzle** (X8R8G8B8 must read alpha 1; luminance formats replicate).
- `bc_ok` is a runtime capability (BC sampling), and a block-compressed base level whose sides are
  not multiples of four is expanded too.
- A copy a draw of this batch has already used, and that changes again, flushes the batch first;
  those flushes are counted (`Stale_Flushes`), "since they should be rare".
- A2 objects die on any thread; their copies leave the table under a mutex at once and are handed to
  the frame to release after the batch, on the main thread.

**Android relevance:** BC (S3TC) texture compression support is *not* universal on Android GPUs —
many mobile GPUs support ETC2/ASTC but not BC, and some Adreno/Mali parts expose BC only through an
extension. `Sdl_Texture_Format` already handles the "GPU does not sample BC" case by expanding to
B8G8R8A8, so the renderer will *work*, but at much higher bandwidth and memory — the exact failure
mode the reference port's Mali patches (`Patches/dxvk-mali-*`) exist to fight. This is a performance
risk, not a correctness one, and the capability query already makes it safe.

## 7. Platform-specific code inside the renderer

`__linux__` appears **13 times in 9 renderer files**; `__APPLE__` appears **zero times** in the
renderer (the Metal difference is entirely inside SDL3 and the MSL translation). The guards:

| File:line | What is Linux-only |
|:--|:--|
| `SdlGpuFrame.h:310` | `Read_Water_Depth`, `Inject_Water_Snapshot` declarations |
| `SdlGpuFrame.cpp:2067` | `Read_Water_Depth` definition |
| `SdlProgramCache.cpp:298` | water program key gets `MaterialProfile`/`CleanProfile` |
| `PosixDevice9.h:289,538` | water-bank members |
| `PosixDevice9.cpp:224` | `WaterBank.reset()` in teardown |
| `PosixDevice9.cpp:622` | `Water_Bank_Present` after present |
| `PosixDevice9Draw.cpp:54,1609,2048` | water bank / surf GPU draw paths |
| `PosixDevice9Capture.cpp:414`, `PosixDevice9Post.cpp:53`, `PosixDevice9SunShadow.cpp:444` | diagnostics / dumps |

These are all **modern-water feature gates**, not platform adaptation — the modern water path is
Linux-only in the fork's current state. On Android they will silently compile *in* (because
`__linux__` is defined by the NDK), so the first question is whether they are wanted on Android or
should be gated on something narrower than `__linux__`. This is a real trap: **`__linux__` is true on
Android**, so any `#if defined(__linux__)` in this tree currently means "Android too".

## 8. Existing Linux dependencies and what changes on Android

The POSIX renderer links `SDL3::SDL3-static` (built in-tree with `SDL_VIDEO`, `SDL_GPU`,
`SDL_DIALOG` on and `SDL_OPENGL`/`SDL_OPENGLES` off — `CMakeLists.txt:904-912`), `sdl3shadercompile`
(glslang + SDL_shadercross + SPIRV-Cross), and the generators. Desktop Linux additionally needs
(`Tools/build-posix.sh:114-116`): `libx11`, `libxext`, `libxcursor`, `libxi`, `libxfixes`,
`libxrandr`, `libxss`, `libxtst`, `libwayland-client`, `libxkbcommon`, `wayland-protocols`,
`libegl`, `libdrm`, `libgbm`, `libvulkan`, `spirv-tools`, `fontconfig`.

On Android none of those exist as system libraries; SDL3's Android backend uses `libandroid`,
`liblog`, `libEGL`/`libGLESv2` (or the Vulkan loader `libvulkan.so`) provided by the NDK/platform,
and the GPU API resolves to **Vulkan**. `fontconfig` is a `find_package(Fontconfig REQUIRED)` gated
on `ZH_PLATFORM_LINUX` (`CMakeLists.txt:958-961`) and is **wrong for Android** — the reference port
excludes fontconfig on Android and uses bundled fonts. So `ZH_PLATFORM_LINUX` needs to be split, or
the Android build needs its own branch, before this CMake will configure.

## 9. Testing surface (a resource for the Android work)

The renderer is covered by an unusually large test suite: **131 `add_test` calls** in
`GeneralsMD/Code/CMakeLists.txt`, of which **51 match sdl/gpu/render/post/water**. Notable pairs run
twice — once as a CPU-logic test and once with a real GPU (`_gpu` suffix): `test_renderer_foundation`,
`test_renderer_foundation_gpu`, `test_sdl_post[_gpu]`, `test_sdl_ao[_gpu]`, `test_sdl_atmosphere[_gpu]`,
`test_water_path_gpu`, `test_sdl_storage[_gpu]`, `test_sdl_owner_release[_gpu]`, and more. There are
also GPU-less self-checks that skip (`exit 77`) where no GPU device is available:
`sdl_gpu_depth_format_selfcheck`, `sdl_gpu_frame_selfcheck`, `sdl_program_cache_selfcheck`,
`sdl_pipeline_state_selfcheck`, `posix_gpu_draw_selfcheck`, `ffref_gpu_selfcheck`.

`PORTING.md` documents that the CI runs CTest with **Lavapipe (software Vulkan) and Xvfb**, no game
data. **This is the single most valuable asset for Android bring-up**: a software-Vulkan,
window-server-free test path already exists, so much of the renderer can be validated on an Android
emulator or a device with the software driver before any game data is present. The `_gpu` tests and
the `exit 77` skips are the mechanism.

## 10. Android compatibility problems (renderer)

| # | Problem | Evidence | Severity |
|:--|:--|:--|:--|
| R1 | `ZH_PLATFORM_LINUX` is true on Android, but it means "desktop Linux" (X11/Wayland/fontconfig/libdrm). | `CMakeLists.txt:27-35, 958-961` | high — blocks configure |
| R2 | `__linux__` is true on Android, so 13 modern-water guards silently switch on. | §7 table | medium — behavioural |
| R3 | No `CMakePresets.json`, no NDK toolchain file, no vcpkg; deps come from `Tools/vendor.sh` which is desktop-shaped. | `01-target-architecture.md` §7 | high — no Android build exists |
| R4 | Game builds as an **executable** `generals`, not a shared library. | `CMakeLists.txt:2168` | high — Android needs `libmain.so` |
| R5 | `SDL_CreateGPUDevice` asks for `SPIRV\|MSL`; Android gives SPIRV (fine), but glslang HLSL front end + SDL_shadercross must build under the NDK. | `SdlGpuFrame.cpp:197`, `CMakeLists.txt:1032` | medium |
| R6 | BC texture sampling may be absent on mobile GPUs → silent expansion to B8G8R8A8 (bandwidth/memory). | `SdlResourceMirror.h` format table | medium — perf |
| R7 | Depth format policy assumes D24S8/D32FS8 + R32F sampling; must be re-queried per Android GPU. | `SdlGpuDepthFormat.h` | low — already a query |
| R8 | `SDL_WINDOW_HIGH_PIXEL_DENSITY` + the points-vs-pixels division is written for Retina/Wayland scale; Android reports a different density model. | `SdlGameEngine.cpp:205`, `PORTING.md` "Scaled displays" | medium |
| R9 | `Present` assumes a swapchain from `SDL_WaitAndAcquireGPUSwapchainTexture`; Android lifecycle (pause/resume, surface loss) can invalidate the window/device. | `SdlGpuFrame.cpp:1892-1957` | high |
| R10 | Batch is main-thread-only and the device is created on the main thread; Android lifecycle callbacks arrive on the UI thread. | `SdlGpuFrame.h` header comment | medium |
| R11 | 64 MiB staging batch + full-screen render targets assume desktop memory; mobile budgets are far tighter. | `SdlGpuFrameBatch.cpp:25` | low — tunable |

## 11. What the reference port does differently (and why it does not transfer)

The reference renders through **native GLES3** (`Core/Libraries/Source/d3d8gles/`) or **DXVK**
(D3D8→Vulkan), not through an SDL3-GPU translation of D3D9. Consequences:

- The reference never calls `SDL_CreateGPUDevice`; it creates an SDL GL context or a Vulkan device
  through DXVK. So the reference has **no analogue** of `SdlGpuFrame`, `SdlPipelineCache`,
  `SdlProgramCache` or `SdlResourceMirror` — the whole §3–§6 machinery is target-specific.
- The reference's Android graphics experience (Vulkan driver fragmentation, Mali patches, AdrenoTools
  for custom drivers) is about *DXVK's* Vulkan usage. The target's Vulkan usage is SDL3 GPU's, so the
  specific patches do not apply, but the **class** of problem (mobile Vulkan driver quality) does.
- The reference's `SDL3GameEngine.cpp` lifecycle gate (render only while the window is valid) **is**
  the relevant lesson, and its idea maps onto target item **R9**.

## 12. Unresolved questions

1. Should the modern-water path (the 13 `__linux__` guards) be enabled on Android, or gated off?
   This needs a decision, because today Android would get it by default.
2. Is there a target GPU budget for Android (memory, staging batch, render-target count)? The 64 MiB
   batch and the full-screen post chain have never run under a mobile budget.
3. Which Android GPU family is the first target (Adreno, Mali, PowerVR)? BC support and depth-format
   availability both hinge on it.
4. Should the Android build keep `Tools/vendor.sh` and add an NDK preset, or adopt the reference's
   vcpkg + preset model? The two have different dependency sets.
5. Does the CI's Lavapipe path extend to an Android emulator, or is a device farm needed?

## 13. Provenance

- Target: `https://github.com/redaf12-Decode/Generals-Reforged` @ `109962c`, branch `main`.
- Design document: `PORTING.md` (526 lines) — decisions 1-11, work items A/B/C/D/E/L/N/P/R/T/V/W/X/M.
- Read-only: no source, build, workflow or dependency file was modified.
