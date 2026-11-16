# 13 — Android ARM64 rendering: the SDL3 GPU Vulkan path

Phase 3. Builds on [`07-gpu-backend-decision.md`](07-gpu-backend-decision.md) (Vulkan is the only
Android backend, no DXVK, no GLES) and records what had to change in the **existing** SDL3-GPU
renderer for a mobile Vulkan driver, and what did not. The desktop (Windows D3D9/D3D11, macOS
Metal, Linux Vulkan) behaviour is unchanged: every change is either a fallback that only a driver
missing a feature can reach, or a correctness fix that also helps the desktop.

## Decision

The Generals Reforged SDL3-GPU rendering architecture is **kept as it is** and made to run on
Android. No second renderer, no DXVK, no GLES, no feature is disabled. Three things change, all
inside the existing device:

1. **Device creation falls back over the four optional Vulkan features** instead of failing on the
   first refusal (`SdlGpuDeviceFeatures.h`, `SdlGpuFrame::Create_Gpu_Device`).
2. **Every graphics pipeline enables depth clipping**, so a device made without the optional
   `depthClamp` feature is legal (SDL maps `enable_depth_clip` to `!depthClampEnable`).
3. **A Present to a target whose format is not the back buffer's goes through the gamma pass**
   instead of a raw blit, because SDL's blit does not swizzle channels and Android's SDR swapchain
   is `R8G8B8A8` while the back buffer is `B8G8R8A8`.

## Evidence

### E1 — Android's only SDL3 GPU backend is Vulkan

Doc 07 established it. The generated Android build config confirms it at configure time
(`include/build_config/SDL_build_config_android.h:195` → `SDL_GPU_VULKAN 1`), and CI now asserts it
on the generated header (`ci-android-test.yml`, "Assert the SDL3 Vulkan GPU backend is compiled for
Android").

### E2 — A default device requires four optional Vulkan features

`SDL_gpu.h:2274-2293` and `SDL_gpu_vulkan.c:12259-12266`: `SDL_CreateGPUDevice` asks for
`shaderClipDistance`, `drawIndirectFirstInstance`, `depthClamp` and `samplerAnisotropy` and
**refuses a physical device that lacks one**. They are optional in Vulkan 1.0 and several are absent
on real mobile drivers. A single attempt therefore fails where a usable device existed.

`SdlGpuFrame::Create_Gpu_Device` now tries five devices in order: attempt 0 is the unchanged
`SDL_CreateGPUDevice` call (so a desktop machine makes exactly the device it made before), and each
later attempt uses `SDL_CreateGPUDeviceWithProperties` dropping the next feature the game needs
least — clip distance, indirect draw first instance, depth clamping, anisotropy. The last attempt
asks for none of them, which every Vulkan 1.0 device can make. The log names what was given up.

Which features the game actually needs was measured, not assumed (`SdlGpuDeviceFeatures.h`):

| Feature | Used by the game? | Why |
|:--|:--|:--|
| `shaderClipDistance` | no | no shader writes `SV_ClipDistance`/`gl_ClipDistance`; `SetClipPlane` only stores |
| `drawIndirectFirstInstance` | no | no `SDL_DrawGPU*PrimitivesIndirect` anywhere |
| `depthClamp` | only for clamping | D3D9 clipping is on (`D3DRS_CLIPPING`); SDL needs `enable_depth_clip` |
| `samplerAnisotropy` | yes | the caps advertise it and `texturefilter.cpp` selects `D3DTEXF_ANISOTROPIC` |

### E3 — Depth clipping is the pipeline's job, and it is now set everywhere

SDL maps `rasterizer_state.enable_depth_clip` to `VkPipelineRasterizationStateCreateInfo.depthClampEnable
= !enable_depth_clip` (`SDL_gpu_vulkan.c:6483`). A device made **without** `depthClamp` must not ask
for `depthClampEnable = true`, so it must clip. The scene pipeline already set it; the full-screen
and particle passes did not. Every graphics-pipeline creation site now sets
`enable_depth_clip = true` (twelve sites across `SdlGpuFrame.cpp`, `SdlGpuFrameBatch.cpp`,
`SdlGpuFramePathTracing.cpp`, `SdlGpuFrameSmoke.cpp`, `SdlHazardCloud*.cpp`), matching D3D9's
default-on clipping.

### E4 — Anisotropy is dropped, and the sampler cache follows

SDL sets `vkSamplerCreateInfo.anisotropyEnable` straight from `enable_anisotropy`
(`SDL_gpu_vulkan.c:6821`), which is invalid when the device was made without the feature. The
sampler cache is told the frame's answer (`SdlGpuFrame::Anisotropy_Available`) and turns an
anisotropic request into plain linear filtering when the device lacks it — the same fallback D3D9
makes on a device whose caps lack anisotropic filtering.

### E5 — SDL's blit does not swizzle, and Android's swapchain is R8G8B8A8

`SDL_BlitGPUTexture` is `VULKAN_Blit`, a bare `vkCmdBlitImage` (`SDL_gpu_vulkan.c:9336-9440`) with no
channel swizzle. The SDR swapchain is `B8G8R8A8`, with an `R8G8B8A8` fallback SDL chooses when the
platform prefers it — as Android's `VK_FORMAT_R8G8B8A8_UNORM`-preferring surfaces do
(`SDL_gpu_vulkan.c:226-240`). The back buffer is `B8G8R8A8`, so an identity-ramp Present to an
`R8G8B8A8` swapchain through a blit would swap red and blue. `Present_Into` now takes the blit only
when the target's format is the back buffer's; otherwise it runs the gamma pass with the identity
ramp, which samples the back buffer (a correct swizzle) and writes the target's format.

## Consequences

- A mobile GPU missing one of the four optional features is now a supported device rather than a
  failed start, and the game says in the log which feature it gave up.
- The desktop is unchanged: attempt 0 is the same call, depth clipping was already wanted, and the
  sampler cache still honours anisotropy where the device has it.
- The gamma pass carries an extra case on a cross-format present; on a desktop whose swapchain is
  `B8G8R8A8` the blit shortcut is still taken.

## Not done in this phase

- No Vulkan renderer rewrite, no `AndroidMain.cpp`, no touch controls, no APK packaging.
- No runtime test on a real Android GPU: CI cross-compiles for `aarch64-linux-android` and asserts
  the backend and the renderer are in the graph, but has no Vulkan device to create one on. The
  device-feature fallback is covered by a host test of its ordering (`test_sdl_gpu_device_features`).

## Provenance

Read from the vendored SDL3 3.4.16 source (`Libraries/Source/SDL3/src/gpu/vulkan/SDL_gpu_vulkan.c`,
`include/SDL3/SDL_gpu.h`) and the first-party renderer under
`GameEngineDevice/Source/PosixDevice/Render/`. Changed in Phase 3: `SdlGpuDeviceFeatures.{h,cpp}`
(new), `SdlGpuFrame.{h,cpp}`, `SdlPipelineCache.{h,cpp}`, `PosixDevice9.cpp`, the twelve pipeline
sites, `Tests/sdl_gpu_probe.cpp`, `Tests/test_sdl_gpu_device_features.cpp` (new),
`CMakeLists.txt`, `.github/workflows/ci-android-test.yml`.
