# 07 — SDL3 GPU backend decision: Vulkan on Android

Resolves **question 1 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("Which Android SDL3 backend is intended — Vulkan only, or also GLES as a fallback?"), and items
**R5/R6/R7** of [`../audit/03-rendering-subsystem.md`](../audit/03-rendering-subsystem.md#10-android-compatibility-problems-renderer).
Read-only: no source, build or dependency file was changed.

## Decision

**Vulkan is the primary and only Android GPU backend.** The existing SDL3-GPU rendering
architecture is preserved as-is. **No DXVK**, **no GLES renderer**, **no rendering feature is
disabled**, and **no second renderer** is introduced. If a device has no usable Vulkan driver, the
existing device-creation path fails loudly and the run stops: `SdlGpuFrame::Create` returns `NULL`
with `SDL_GetError()` (`SdlGpuFrame.cpp:192-200`), `PosixDevice9::Create_Gpu_Frame` returns
`D3DERR_NOTAVAILABLE` (`PosixDevice9.cpp:238-241`), and `W3DDisplay::init` throws
`ERROR_INVALID_D3D` (`W3DDisplay.cpp:1233-1240`) — the same failure the game already has when no
render device can be made. It does not silently switch to an unrelated implementation.

## Evidence

### E1 — SDL3 3.4.16 ships exactly three GPU backends: Metal, D3D12, Vulkan (no GLES)

The pinned SDL3 is **3.4.16** (`GeneralsMD/Code/Tools/vendor.sh:434-455`, commit
`fa2c02bb6e21974a89ea9824bc53c9932abe5f9c`; `Libraries/Source/SDL3/include/SDL3/SDL_version.h` →
`SDL_MICRO_VERSION 16`). Its GPU driver table lists only three backends:

```c
// Libraries/Source/SDL3/src/gpu/SDL_gpu.c:314-328
static const SDL_GPUBootstrap *backends[] = {
#ifdef SDL_GPU_METAL
    &MetalDriver,
#endif
#ifdef SDL_GPU_D3D12
    &D3D12Driver,
#endif
#ifdef SDL_GPU_VULKAN
    &VulkanDriver,
#endif
    NULL
};
```

`src/gpu/` contains only `metal/`, `d3d12/` and `vulkan/` — **there is no `gles/` (or `opengl/`)
directory**, and `SDL_gpu.h:29` states the API targets "Metal, Vulkan, and Direct3D 12". So on
Android the only backend SDL3 GPU can select is Vulkan. There is nothing to fall back to inside
SDL3 GPU.

### E2 — Android is explicitly a supported Vulkan platform, surface and loader included

`SDL_gpu.h:221-231` names the supported platforms and requirements:

```
 * Supported on Windows, Linux, Nintendo Switch, and certain Android devices.
 * Requires Vulkan 1.0 with the following extensions and device features:
 * - `VK_KHR_swapchain`
 * - `VK_KHR_maintenance1`
 * - `independentBlend`
 * - `imageCubeArray`
 * - `depthClamp`
 * - `shaderClipDistance`
 * - `drawIndirectFirstInstance`
 * - `sampleRateShading`
```

The swapchain path is Android-aware: `src/gpu/vulkan/SDL_gpu_vulkan.c:4782-4785` forces
`swapchainCreateInfo.preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR` under
`#ifdef SDL_PLATFORM_ANDROID`, and the swapchain is created with `VK_KHR_swapchain`
(`SDL_gpu.h:224`). The surface comes from the platform: the Android video device registers
`Vulkan_CreateSurface = Android_Vulkan_CreateSurface`
(`src/video/android/SDL_androidvideo.c:131-134`), whose body calls `vkCreateAndroidSurfaceKHR`
(`src/video/android/SDL_androidvulkan.c:126-176`) on the `ANativeWindow` SDL already holds
(`src/video/android/SDL_androidwindow.c:72`, `data->native_window = Android_JNI_GetNativeWindow()`).
The loader is Android's own `libvulkan.so` (`SDL_androidvulkan.c:55`). SDL3's CMake enables this on
Android by default: `SDL_VULKAN` defaults `ON` for `ANDROID` (`SDL3/CMakeLists.txt:371`) and the
Android branch sets `SDL_VIDEO_VULKAN 1` (`:1578-1581`).

The GPU device also calls `SDL_Vulkan_GetInstanceExtensions`
(`SDL_gpu_vulkan.c:11984`), whose Android implementation returns `VK_KHR_android_surface`
(`SDL_androidvulkan.c:120-125`) — so the extension the surface needs is requested by the GPU path
itself, with no change from the target.

### E3 — The target asks for the right shader formats; Android takes the SPIR-V half

`SdlGpuFrame.cpp:197`:

```cpp
frame->GpuDevice = SDL_CreateGPUDevice(SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL, debug, NULL);
```

`SPIRV` is "for Vulkan" (`SDL_gpu.h:1037`), `MSL` is "for Metal" (`:1040`). On Linux the Vulkan
driver is chosen and SPIR-V is used; on macOS the Metal driver is chosen and MSL is used. Android
behaves like Linux: the driver reports Vulkan, so SPIR-V is the format that is used and the MSL half
is inert. The target already takes exactly this path on Linux, so **the SPIR-V pipeline is
preserved and no shader-format change is required.**

The shader pipeline is: HLSL generators (`ffvertex`/`ffshader`/`engineshader`) → glslang HLSL front
end → SPIR-V. The cache compiles HLSL to SPIR-V at
`GameEngineDevice/Source/PosixDevice/Render/SdlProgramCache.cpp:96`
(`SDL3_Compile_HLSL_To_SPIRV`), whose definition is
`Libraries/Source/WWVegas/WW3D2/sdl3shadercompile.cpp:64` (glslang, `:23-24`). The resulting SPIR-V
is handed to `SDL_ShaderCross_CompileGraphicsShaderFromSPIRV`
(`Libraries/Source/SDL_shadercross/`). MSL is produced only for the Metal path. Android needs the
SPIR-V half only, which is the Linux half — but it does mean glslang and `SDL_shadercross` must
build under the NDK (a Phase-2 build task, tracked in §"Open items").

### E4 — "GLES fallback" is not a renderer option here

Two independent facts make a GLES fallback a non-option:

1. **SDL3 GPU cannot use GLES.** E1: no GLES backend exists in this SDL3 version.
2. **The build already disables SDL's GL/GLES drivers.** `GeneralsMD/Code/CMakeLists.txt:895-908` (inside `if(ZH_PLATFORM_POSIX)`, which Android is)
   forces `SDL_OPENGL OFF` and `SDL_OPENGLES OFF` (the comment says so explicitly: "the OpenGL/GLES
   video drivers, which the GPU API does not use"). The renderer is `posixd3d9` over SDL3 GPU, not
   SDL's 2D renderer.

So a "GLES fallback" would mean writing an entirely separate renderer — the reference port's
approach (`d3d8gles`, GLES3, per `../audit/02-reference-architecture.md` §4) — which the task
forbids ("do not replace the renderer with GLES"). The only way GLES would matter is if SDL3 gained
a GLES GPU backend; 3.4.16 has not.

### E5 — The window does not need `SDL_WINDOW_VULKAN`

`SdlGameEngine.cpp:221` creates the window with `SDL_WINDOW_HIGH_PIXEL_DENSITY` only (no
`SDL_WINDOW_VULKAN`). SDL3's GPU backend creates its own `VkSurfaceKHR` at `ClaimWindow` time by
calling the video device's `Vulkan_CreateSurface` (`SDL_gpu_vulkan.c:9974-9982`), independent of the
window flag. On Android `Android_Vulkan_CreateSurface` uses `windowData->native_window`, which SDL
sets for every Android window (`SDL_androidwindow.c:72`), not only OpenGL/Vulkan-flagged ones. The
existing window flags therefore need no change for Vulkan; the `SDL_WINDOW_VULKAN` flag is only
required when the app manages its own `VkInstance`/surface (the error text at
`SDL_androidvulkan.c:161` says so), which the target does not.

## Minimum required Vulkan capabilities (the compatibility floor)

A device can run the renderer when SDL3 GPU can create a device and claim the window. The floor is
SDL3 3.4.16's own requirement set (`SDL_gpu.h:221-231`):

| Requirement | Kind | Note |
|:--|:--|:--|
| Vulkan **1.0** | instance | the API level SDL3 GPU needs |
| `VK_KHR_swapchain` | device extension | present on essentially all Android Vulkan drivers |
| `VK_KHR_maintenance1` | device extension | — |
| `independentBlend` | feature | — |
| `imageCubeArray` | feature | — |
| `depthClamp` | feature | **optional** — can be dropped (see below) |
| `shaderClipDistance` | feature | **optional** — can be dropped |
| `drawIndirectFirstInstance` | feature | **optional** — can be dropped |
| `sampleRateShading` | feature | — |

SDL3 provides `SDL_CreateGPUDeviceWithProperties()` with four `SDL_PROP_GPU_DEVICE_CREATE_FEATURE_*`
booleans (`SDL_gpu.h:234-240`) that **remove** the harder requirements to widen Android
compatibility:

- `SDL_PROP_GPU_DEVICE_CREATE_FEATURE_CLIP_DISTANCE_BOOLEAN`
- `SDL_PROP_GPU_DEVICE_CREATE_FEATURE_DEPTH_CLAMPING_BOOLEAN`
- `SDL_PROP_GPU_DEVICE_CREATE_FEATURE_INDIRECT_DRAW_FIRST_INSTANCE_BOOLEAN`
- `SDL_PROP_GPU_DEVICE_CREATE_FEATURE_ANISOTROPY_BOOLEAN`

The target uses the plain `SDL_CreateGPUDevice` today (`SdlGpuFrame.cpp:197`). **Recommendation for
Phase 2 (not done in this audit):** keep the plain call as the first attempt, and only if a target
device fails device creation, retry with these properties set false, logging which feature was
dropped. This changes no rendering path and disables no feature; it is a device-creation fallback,
not a renderer fallback. Whether the game actually needs clip distance, depth clamp or indirect
first-instance is a Phase-2 measurement (the shaders are generated by the fork's own generators, so
it can be checked).

`VK_KHR_swapchain` and `VK_KHR_maintenance1` are near-universal on Android Vulkan drivers (Android
requires Vulkan 1.0 with swapchain for any Vulkan-capable device; maintenance1 is in the same era),
but this is a statement about the platform, not a measurement on the target's devices — see the
testing matrix in [`FINAL-PLATFORM-DECISIONS.md`](FINAL-PLATFORM-DECISIONS.md).

## Feature impact of choosing Vulkan (nothing is lost)

Because Android takes the same SPIR-V path as Linux, every renderer feature the Linux build already
has is available on Android — the post chain (AO, atmosphere, distortion, occlusion), the GPU
particles, hazard clouds, reach overlay, sun shadows, and the Modern water path (subject to the
separate `__linux__`-guard decision in
[`12-platform-macro-audit.md`](12-platform-macro-audit.md)). Nothing is gated out by the backend
choice itself. Two performance caveats remain, and both are already handled by capability queries,
not by disabling features:

- **BC (S3TC) sampling is not universal on mobile GPUs** (`../audit/03-rendering-subsystem.md` R6).
  `SdlResourceMirror` already expands a block-compressed texture to `B8G8R8A8` when the device
  cannot sample BC (`bc_ok` capability), so this is a bandwidth/memory cost, never a wrong image.
- **Depth/colour format policy** (`SdlGpuDepthFormat.h`) is already a runtime query, so a device
  that cannot do D24S8/D32FS8 is handled by the existing query (R7).

## What this decision rules out

- **DXVK** — not introduced; the target renders through SDL3 GPU, not a D3D8/D3D9→Vulkan
  translation, and no DXVK dependency is added.
- **A GLES renderer** — not written; SDL3 3.4.16 GPU has no GLES backend, and the build keeps
  `SDL_OPENGL`/`SDL_OPENGLES` off.
- **Disabling rendering features** — none; the SPIR-V path is the Linux path, which already
  exercises them.

## Open items handed to Phase 2

1. Confirm glslang's HLSL front end and `SDL_shadercross` build under the NDK r27c toolchain (they
   do on Linux; the toolchain differs).
2. Measure the real feature floor on the first target device class and decide whether the
   `SDL_CreateGPUDeviceWithProperties` fallback is needed.
3. Decide the Modern-water enable/disable on Android — that is
   [`12-platform-macro-audit.md`](12-platform-macro-audit.md), not this backend question.

## Provenance

- All paths relative to the repository root, read at `c91ab1a`, branch `docs/android-port-audit-01`
  (default `main` at `109962c`).
- SDL3 facts are read from the vendored tree `GeneralsMD/Code/Libraries/Source/SDL3` (3.4.16).
- Read-only: no file was created, renamed, deleted or modified during the investigation except this
  document and its siblings.
