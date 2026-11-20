# Zero Hour Reforged on Android (arm64)

Command & Conquer: Generals **Zero Hour** running natively on **Android, 64-bit ARM (arm64-v8a)**,
through **SDL3 and Vulkan**. This is the Android half of the port on this branch; the desktop half is
the Linux build documented in the [root README](../README.md).

> **Status.** The Android application, the native engine, the folder picker, the touch layer, the
> audio lifecycle and the APK packaging are implemented and pass CI (native cross-compile + APK
> build and inspection). **Nothing here has been run on a physical Android device yet**, so no
> device is claimed compatible. See [Verification](#verification-and-limitations).

You need your own installed copies of **Generals** and **Zero Hour**. Game data is not included and
is not bundled in the APK.

---

## What this is, and how it relates to upstream

Zero Hour Reforged is a source port of Electronic Arts' released Command & Conquer Generals and Zero
Hour source. This branch builds on
[olcayseygan/CnCGeneralsZH-Reforged](https://github.com/olcayseygan/CnCGeneralsZH-Reforged) (the
upstream Reforged project, which carries the macOS/Linux/Windows port by İlyas Akın) and adds an
**Android arm64** target: a Gradle application shell, an NDK cross-build of the same engine, a
Storage-Access-Framework folder picker, a native touch layer, and Android platform services.

It is **not** the desktop port repackaged: the same engine source is cross-compiled for
`aarch64-linux-android`, and only the Android-specific layers (window/entry, touch, audio lifecycle,
storage, folder access) differ. The full design record is under
[`../docs/android-port/`](../docs/android-port/).

The Linux build's graphics enhancements (Modern water, volumetric clouds, sun shadows, reflection
modes) are inherited, because Android takes the **same SPIR-V rendering path as Linux** — there is no
Android-specific renderer. What runs is the fork's own D3D9-shaped device over SDL3's GPU API.

## Architecture and technologies

| Layer | What it is |
|:--|:--|
| Application shell | A Gradle project under [`android/`](.) (`app/`): a thin Java Activity and the manifest. AGP 8.7.3, Gradle 8.12. |
| Entry point | The engine's existing `main()` in `Main/PosixMain.cpp`, exported as `SDL_main`; `ZeroHourActivity` extends SDL3's `SDLActivity`, which loads `libmain.so` and calls it. |
| Engine | `libmain.so` — the whole native engine cross-compiled with the NDK (Clang, C++17, `arm64-v8a`, API 28). |
| Platform layer | **SDL3 3.4.16** as a standalone shared library, `libSDL3.so`, loaded by `SDLActivity` before `libmain.so`. |
| Graphics | The fork's renderer over **SDL3's GPU API**; on Android the only backend is **Vulkan** (SDL3 3.4.16 has no GLES GPU backend, and DXVK is not used). Shaders are HLSL → glslang → SPIR-V. |
| Audio | The Miles 6.5 API implemented over **miniaudio**, with the **AAudio** backend first and **OpenSL ES** as the fallback. |
| Input | A native touch layer (`SdlTouch`) for battlefield gestures, plus the unchanged mouse/keyboard paths; SDL's IME for text entry. |
| Storage | The player's picked game folder is staged once into app storage; saves, options and logs go to app-private storage. |

SDL3 is the one third-party library the APK carries as its own shared object, because SDL3's Java
glue owns the process entry on Android: `SDLActivity` loads `libSDL3.so` and then `libmain.so`, and
SDL3's `JNI_OnLoad` lives in `libSDL3.so`. Every other third-party library (miniaudio, FreeType,
glslang, SPIRV-Cross, SDL_shadercross, mbedTLS, zlib, LZH-Light, GameSpy, FFmpeg) is a static archive
linked into `libmain.so`. The APK's native payload is therefore three files under
`lib/arm64-v8a/`: `libmain.so`, `libSDL3.so` and the NDK's `libc++_shared.so` (the C++ runtime
`libmain.so` needs).

## Implemented Android features

- Native arm64-v8a engine built as `libmain.so`, loaded by SDL3's `SDLActivity`.
- Vulkan rendering through SDL3's GPU API, with a device-creation fallback that drops the optional
  Vulkan features the game does not need, so a driver missing `depthClamp`, `shaderClipDistance`,
  `drawIndirectFirstInstance` or `samplerAnisotropy` is still a supported device.
- Runtime capability handling for BC/S3TC texture sampling and depth/stencil format.
- A folder-picker launcher screen that reads a folder the player chooses through the system Storage
  Access Framework picker, validates it, and stages it once (incrementally) into app storage.
- Native touch controls: tap-to-select/order resolved against the engine's own rules, drag to pan,
  pinch to zoom, twist to rotate, two-finger gestures, momentum, long-press and double-tap, and area
  selection. Touch never doubles as a mouse event.
- Android application lifecycle: backgrounding stops the audio output (held open, not torn down) and
  drops a live touch gesture, once per backgrounding.
- Screen kept awake while playing; the Wi-Fi multicast lock is held for the LAN lobby.
- Landscape, immersive full-screen game screen; a separate setup screen as the home-screen entry
  point.
- Saving to app-private storage; no storage permission and no "all files access" permission.
- An in-app **System requirements** screen, opened from the setup screen, that shows the application's
  requirements beside this device's reported capabilities and an evaluated compatibility result
  (Compatible / Partially compatible / Unsupported / Unable to verify). See
  [In-app requirements screen](#in-app-requirements-screen).

## Known limitations

- **Not device-tested.** No physical Android device or emulator has run this build.
- **No on-screen modifier buttons** (force fire, waypoint, add-to-selection). The engine exposes the
  modes; the buttons are not built.
- **Vulkan device-loss handling** relies on SDL's own swapchain recreation; no Android-specific code.
- **Font rendering on device is unverified**: the glyph rasteriser probes `/system/fonts/` because
  Android has no fontconfig, but this has not been confirmed on a real vendor font set.
- **Release builds are not signed** in this repository (the debug variant uses AGP's debug keystore).
- **No release is published.** CI builds a debug APK and uploads it as a short-lived run artifact;
  it does not create a signed release or a GitHub release.
- The in-app device screen reads the platform's advertised Vulkan API version and hardware level, but
  the Vulkan physical-device name, vendor/device IDs and per-extension/per-feature support are not
  exposed by the Android platform API; the screen reports those as unverified.

## In-app requirements screen

The setup screen has a **System requirements** button that opens a screen showing three groups:

- **Application requirements** — the Android version, ABI, Vulkan floor and hardware profiles.
- **Your device** — manufacturer, model, Android version, CPU ABIs, total RAM, and the Vulkan facts
  the Android platform API exposes (the advertised API version and hardware level). The Vulkan
  physical-device name, vendor/device IDs and per-extension/per-feature support are **not** exposed by
  the platform API, so the screen reports them as unverified rather than inventing them.
- **Compatibility** — one of **Compatible**, **Partially compatible**, **Unsupported** or
  **Unable to verify**, with the per-check detail.

The screen never marks a device compatible from its processor name or from Vulkan being present: the
mandatory Vulkan *features* cannot be read from the app, so a device whose other requirements are met
is **Partially compatible** at best; the engine makes the final check when it creates the Vulkan
device and fails loudly if a mandatory feature is missing. The screen's requirements come from the
same single source (`AndroidRequirements`) the documentation is written against, so they cannot
disagree. Compatibility is not a promise of performance.

## Requirements

See [`../docs/android-port/audit/ANDROID-HARDWARE-COMPATIBILITY.md`](../docs/android-port/audit/ANDROID-HARDWARE-COMPATIBILITY.md)
for the full derivation and evidence labels. In summary:

| Requirement | Value |
|:--|:--|
| Android version | **Android 9 (API 28)** or newer |
| CPU architecture | **arm64-v8a** (64-bit ARM) only |
| Graphics | **Vulkan 1.0** driver with `VK_KHR_swapchain`, `VK_KHR_maintenance1` and the `independentBlend`, `imageCubeArray`, `sampleRateShading` features. No OpenGL ES fallback. |
| GPU families | Adreno, Mali, Immortalis, PowerVR and Xclipse families all have Vulkan-capable members, but **no family is universally compatible** — support depends on the generation, the vendor driver, and the mandatory features above. |
| RAM | 4 GB recommended as a provisional floor, 8 GB recommended; **no verified figure exists** |
| Storage | free space for your game data **plus** the one-time staged copy of it (roughly twice the game-data size) plus saves |
| Input | a touchscreen (declared required) |

**Minimum processor profile (provisional).** No Android SoC has been tested, so the minimum is
**provisional**, not confirmed: any arm64-v8a SoC whose GPU driver advertises Vulkan 1.0 with the
mandatory features. As an illustration (not a confirmed minimum), a 2018–2019 arm64 flagship such as
a Qualcomm Snapdragon 845 (Adreno 630) or a Samsung Exynos 9810 (Mali-G72) is the class of device
that meets the floor on paper.

**Recommended processor profile (provisional).** A 2022-or-newer arm64 flagship SoC with a
Vulkan 1.1+ driver — for example an Adreno 7xx, Arm Immortalis-G715, or Samsung Xclipse 920 — for
comfortable headroom on the Modern-water, volumetric-cloud and shadow passes.

A processor name is not a compatibility decision. A device is compatible only if its driver provides
the mandatory Vulkan features **and** the game actually starts and draws on it.

## Game data

You must supply your own legally obtained **Generals** and **Zero Hour** installations. The APK
contains no game data. The picker's validation matches the engine's own rule: Zero Hour's
`INIZH.big` at the top level of the folder you pick, and the base game's `Textures.big` in one of the
places the engine looks — the same folder, a `ZH_Generals/` subfolder, or a sibling Generals folder
(Steam installs the two games as siblings).

## Install and first launch

1. **Get the APK.** There is no published release. CI builds a debug APK
   (`android/app/build/outputs/apk/debug/app-debug.apk`) and uploads it as the `android-app-apk`
   artifact on a run of `ci-android-app.yml` (kept 7 days); download it from there, or build it
   yourself (see [Building](#building)).
2. **Install it** on the device (`adb install -r app-debug.apk`, or open the APK on the device and
   allow installation from that source).
3. **Open the app.** The launcher is the setup screen.
4. **Choose your game folder.** Tap **Choose game folder**. The system folder picker opens; select
   the folder that holds `INIZH.big` (the Zero Hour install). If you pick the folder that holds both
   games, the app finds Zero Hour inside it. The app reads the folder read-only and copies its
   contents **once** into the app's own storage — this first copy is the size of your game data and
   takes a while; a progress bar is shown.
5. **Start the game.** When the folder validates, **Start game** is enabled. The game reads its data
   from the staged copy. Saves, options and logs go to app-private storage.
6. **Later launches** re-use the staged copy and copy nothing unless your game data changed. If the
   folder permission is revoked or the folder moved, the setup screen says so and asks you to pick it
   again.

**Permissions.** The app declares only two: `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE`, both for
the LAN lobby (UDP sockets and the Wi-Fi multicast lock). It requests **no storage permission** —
access to your game folder is the single-tree grant the system picker gives for the folder you
choose.

## Building

Building is a developer task (SDK + NDK + JDK 17 + Gradle + CMake/Ninja). It is not needed to install
an APK.

```sh
# 1. Cross-compile the engine as libmain.so (arm64-v8a, API 28)
ANDROID_SDK_ROOT=/path/to/sdk ANDROID_NDK_HOME=/path/to/ndk \
  scripts/build/android/build-android.sh

# 2. Stage the libraries and SDL3's Java glue, then package the APK
ANDROID_SDK_ROOT=/path/to/sdk ANDROID_NDK_HOME=/path/to/ndk \
  scripts/build/android/package-android.sh --install
```

`package-android.sh` stages `libmain.so`, `libSDL3.so`, `libc++_shared.so` and SDL3's Java glue into
`android/` (both staged directories are gitignored), then runs Gradle. The SDK must hold platform
`android-35`; the NDK must be named so `libc++_shared.so` can be found.

## Verification and limitations

A green CI run proves the engine cross-compiles for `aarch64-linux-android`, links as `libmain.so`,
and that a correct APK is packaged around it (right ABI, right libraries, no game data, the setup
Activity as the launcher, the expected manifest). **It does not prove the game runs on a device.**
There is no device or emulator in CI, so compatibility with any specific Android device remains to
be shown by running the build on it.

The full evidence, per item, is in
[`../docs/android-port/audit/ANDROID-IMPLEMENTATION-FACTS.md`](../docs/android-port/audit/ANDROID-IMPLEMENTATION-FACTS.md).

## Licensing and credits

This is a **modified version** of EA's GPL-released Generals/Zero Hour source; it is not the original
program and is not affiliated with, endorsed by or sponsored by Electronic Arts. It is distributed
under the **GPL-3.0-or-later with EA's additional terms** — see [`../LICENSE.md`](../LICENSE.md) and
[`../NOTICE.md`](../NOTICE.md). "Command & Conquer" and other EA trademarks are used only to say
where the code comes from.

Upstream credits are preserved: the base fork
[olcayseygan/CnCGeneralsZH-Reforged](https://github.com/olcayseygan/CnCGeneralsZH-Reforged), the
macOS/Linux/Windows port by **İlyas Akın**, and work inherited from
[TheSuperHackers/GeneralsGameCode](https://github.com/TheSuperHackers/GeneralsGameCode).
Third-party components (SDL3, miniaudio, FreeType, glslang, SPIRV-Cross, SDL_shadercross, mbedTLS,
zlib, LZH-Light, GameSpy, FFmpeg) are fetched at pinned versions and keep their own licences.
