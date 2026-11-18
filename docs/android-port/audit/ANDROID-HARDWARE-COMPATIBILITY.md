# Android hardware compatibility — minimum and recommended profiles

What hardware this branch's Android build actually needs, derived from the source (see
[`ANDROID-IMPLEMENTATION-FACTS.md`](ANDROID-IMPLEMENTATION-FACTS.md)) and, where the repository is
silent, from public platform specifications. **No Android device or emulator was used**, so nothing
here is device-tested; the labels below say how each claim is supported.

## Evidence labels

| Label | Meaning |
|:--|:--|
| **Device-tested** | Confirmed by running the game on that hardware. **None of the profiles below are device-tested** — the repository records no such run. |
| **Specification-verified** | Derived from the application's own source and from a public hardware/platform specification, but not run on the device. |
| **Provisional** | A reasonable recommendation from the application's requirements and public data, with no device run behind it. |
| **Unverified** | Stated as an open question; not asserted. |

## 1. What the application requires (the floor)

These come from the branch itself and are **specification-verified** (source-cited in
[`ANDROID-IMPLEMENTATION-FACTS.md`](ANDROID-IMPLEMENTATION-FACTS.md) §1, §4, §5):

| Requirement | Value | Where it comes from |
|:--|:--|:--|
| Minimum Android | **9 (API 28)** | `minSdk 28`, `android/app/build.gradle`; the native engine is cross-compiled against `android-28` (`android.toolchain.cmake`) |
| CPU architecture | **arm64-v8a (AArch64)** only | `abiFilters 'arm64-v8a'`; the CMake build refuses a non-AArch64 Android configure |
| Graphics API | **Vulkan 1.0** | SDL3 3.4.16's GPU floor (`SDL_gpu.h:221-231`); there is no GLES backend and no DXVK |
| Mandatory device extensions | `VK_KHR_swapchain`, `VK_KHR_maintenance1` | same |
| Mandatory device features | `independentBlend`, `imageCubeArray`, `sampleRateShading` | same |
| Optional features (the port drops them if absent) | `shaderClipDistance`, `drawIndirectFirstInstance`, `depthClamp`, `samplerAnisotropy` | `SdlGpuDeviceFeatures.{h,cpp}` |
| Shaders | **SPIR-V** support (inherent to Vulkan) | the SPIR-V half of `SDL_CreateGPUDevice(SPIRV|MSL, …)` |
| Touchscreen | required (`android.hardware.touchscreen`) | `AndroidManifest.xml` |
| Storage permission | none | `AndroidManifest.xml` |
| Game data | the player's own Generals + Zero Hour install | `GameFolderValidator`, doc 17 |

Two capabilities the device may lack are handled at runtime, so they are **not** part of the floor:
BC/S3TC texture sampling (the renderer expands such textures to `B8G8R8A8`) and a specific
depth/stencil format (the renderer queries for `D24S8` then `D32FS8`).

### What the floor does and does not mean

- **Vulkan availability is not the same as feature support.** A driver can advertise Vulkan 1.1 and
  still be missing a mandatory feature. The application asks for the mandatory set and refuses a
  physical device that lacks one (unless it is one of the four optional ones it drops). So a device
  is not "compatible" merely because it says it has Vulkan.
- **Technical compatibility is not performance.** Meeting the floor means the renderer can create a
  device and draw; it says nothing about frame rate. On a GPU that cannot sample BC, the renderer
  works but pays a bandwidth and memory cost (doc 03 §6).
- **A driver is required.** On Android the Vulkan runtime (`libvulkan.so`) is part of the platform,
  but the implementation is a **GPU-vendor driver** (AOSP: "Vulkan Driver — SoC — maps the Vulkan API
  onto hardware-specific GPU commands"). A device whose vendor driver is broken or absent has no
  usable Vulkan, and the game fails loudly at start-up by design.

## 2. GPU families the Android Vulkan ecosystem covers

The mobile Android Vulkan drivers come from a handful of GPU families. This list is
**specification-verified** at the family level (each family has Vulkan-capable members) but **no
family is universally compatible**: within a family, support depends on the generation, the driver
build the vendor shipped for that specific device, and whether the mandatory features above are
present.

| Family | Vendor | Example of a Vulkan-capable generation | Note |
|:--|:--|:--|:--|
| **Adreno** | Qualcomm | Adreno 5xx (Vulkan 1.0), 6xx (Vulkan 1.1), 7xx (Vulkan 1.1–1.3) | The dominant Android GPU family; the reference Android ports of this game target it first |
| **Mali** | Arm | Mali-G5x/G7x generations advertise Vulkan 1.0–1.2 | Very common in mid-range and Exynos/Dimensity devices |
| **Immortalis** | Arm | Immortalis-G715 and later advertise Vulkan 1.3 | Arm's flagship family; a subset of Mali's lineage |
| **PowerVR** | Imagination | Rogue/IMG series advertise Vulkan 1.0–1.3 | Less common on phones; more on tablets/TVs |
| **Xclipse** | Samsung/AMD | Xclipse 920 (RDNA-based) advertises Vulkan 1.3 | Exynos only |

Because the port's renderer was developed and CI-tested against desktop Vulkan (Mesa) and has never
been run on a phone GPU, **no family is device-tested here**. The mandatory-feature check the
application performs is the only thing that decides compatibility at run time.

## 3. Minimum profile (provisional)

**There is no tested minimum SoC.** The repository records no device on which the Android port has
run (verified in [`ANDROID-IMPLEMENTATION-FACTS.md`](ANDROID-IMPLEMENTATION-FACTS.md) §9), so the
minimum below is **provisional**: it is the smallest hardware that meets the application's own floor,
with no device run behind it.

| Component | Provisional minimum | Evidence status |
|:--|:--|:--|
| Android version | **Android 9 (API 28)** | Specification-verified (`minSdk 28`) |
| CPU | any **arm64-v8a** SoC | Specification-verified (the only ABI built) |
| GPU | a GPU whose driver advertises **Vulkan 1.0** and provides `VK_KHR_swapchain`, `VK_KHR_maintenance1`, `independentBlend`, `imageCubeArray`, `sampleRateShading` | Specification-verified as the requirement; **the per-device support is Unverified** |
| Example SoC class (illustrative, not tested) | 2018–2019 arm64 SoCs whose drivers advertise Vulkan 1.1 — for example Qualcomm Snapdragon 845 (Adreno 630) or Samsung Exynos 9810 (Mali-G72) | **Provisional**; the GPU generation is documented as Vulkan 1.1 in public GPU references, but the mandatory-feature support on any given device is **Unverified** |
| RAM | ≥ 4 GB recommended as a floor; no verified figure exists | **Provisional** |
| Storage | free space for the player's game data **plus** the one-time staged copy of it (the picker stages a second copy into app storage, so plan for roughly twice the game-data size), plus saves | Specification-verified from `SafTreeStager`'s free-space check; the game-data size itself is **Unverified** (no figure in the repository) |

> The illustrative SoCs are named only to give the minimum a concrete shape. They are **not** a
> confirmed minimum: the port has not been run on them, and a specific device must be tested before
> it can be called supported.

## 4. Recommended profile (provisional)

A device comfortably above the floor, with headroom for the renderer's Modern-water, volumetric-cloud
and shadow passes. **Provisional** — no device run.

| Component | Provisional recommendation | Evidence status |
|:--|:--|:--|
| Android version | **Android 12 (API 31)** or newer | Provisional |
| CPU | a modern **arm64-v8a** flagship SoC (2022 or newer) | Provisional |
| GPU | a GPU whose driver advertises **Vulkan 1.1 or newer** with the mandatory features — for example Adreno 7xx, Arm Immortalis-G715, or Xclipse 920 | **Provisional**; the generations are documented as Vulkan 1.3-capable in public GPU references, but the application's mandatory-feature support on any given device is **Unverified** |
| RAM | ≥ 8 GB | **Provisional** (no verified figure) |
| Storage | ≥ 8 GB free, to hold the player's install plus the staged copy plus saves | **Provisional** |

## 5. How to judge a specific device

Do not decide from the processor name. The only sound test is:

1. the device is **Android 9+ arm64** (the floor), **and**
2. its driver exposes **Vulkan 1.0 with the mandatory extensions and features** (the in-app
   compatibility screen reports what the platform exposes and flags the rest as unverifiable), **and**
3. the game actually starts and draws on it (the engine fails loudly if it cannot create the device).

Steps 1 and 2 are what the in-app screen can partly check; step 3 needs the device.

## 6. Open questions

- **Tested minimum SoC:** none exists. Until a device run is recorded, the minimum is provisional.
- **RAM:** no figure is derived anywhere in the repository; the recommendations above are
  provisional.
- **Game-data size:** the repository says "several-GB"/"multi-GB" but gives no number, so the
  storage recommendation is stated as a multiple of it rather than an absolute figure.
- **Per-device mandatory-feature support:** cannot be decided from the repository or the Android
  platform API; it needs the device's Vulkan driver.
