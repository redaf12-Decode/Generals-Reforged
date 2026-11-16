# Final platform decisions — Android port of Generals Reforged

This is the master record of the Android platform architecture decisions. It resolves every question
in **Section 11 of [`../audit/01-target-architecture.md`](../audit/01-target-architecture.md)**, each
with the evidence gathered in a dedicated document. **No source, build or dependency file was
changed** by this work: these are decisions and evidence, to be implemented in Phase 2.

Read-only audit; branch `docs/android-port-audit-01`; repository read at `c91ab1a` (default `main`
at `109962c`).

## The decisions at a glance

| # | Question (Section 11) | Decision | Document |
|:--|:--|:--|:--|
| 1 | SDL3 backend — Vulkan only, or GLES fallback? | **Vulkan only.** SDL3 3.4.16 GPU has no GLES backend; keep the existing SDL3-GPU architecture, no DXVK, no GLES, no feature disabled. | [`07-gpu-backend-decision.md`](07-gpu-backend-decision.md) |
| 2 | How does game data reach the device? | **User-provided files in a folder the player picks once**, path stored in `Registry.ini`; reuse `PosixInstallRoot`/`PosixLocalFileSystem`. No APK assets, no `MANAGE_EXTERNAL_STORAGE`, no data pack. | [`08-game-data-delivery.md`](08-game-data-delivery.md) |
| 3 | ABI/API/NDK and the `backtrace()` problem? | **arm64-v8a, API 28, NDK r27c.** Replace glibc `backtrace()` with **`_Unwind_Backtrace` + `dladdr`** (works at API 28; `backtrace()` needs API 33). | [`09-ndk-api-and-backtrace.md`](09-ndk-api-and-backtrace.md) |
| 4 | Native-activity or SDLActivity entry? | **`SDLActivity` + `libmain.so` + `SDL_main`.** `PosixMain.cpp`'s `main()` is unchanged; only the build target changes to a shared library. | [`10-android-entry-point.md`](10-android-entry-point.md) |
| 5 | Does the fingerprint need an Android axis? | **No.** The fingerprint is over source, so Android and desktop of one commit already agree; Android players join desktop games. | [`05-compatibility-and-fingerprint.md`](05-compatibility-and-fingerprint.md) |
| 6 | Do the 131 tests run on an emulator? | **No** — desktop CI stays the gate; Android adds a small device/emulator smoke set. | [`06-testing-strategy.md`](06-testing-strategy.md) |
| 7 | How to narrow `__linux__` for Android? | **Android defines `__linux__`.** Keep kernel-behaviour sites; narrow desktop-intent sites to `__linux__ && !__ANDROID__`; add an `__ANDROID__` branch where Linux code is wrong (`/proc/self/exe`). | [`12-platform-macro-audit.md`](12-platform-macro-audit.md) |
| — | What must build or change to link? | Dependency inventory: Android-OK vs off vs needs work (fontconfig substitute, FFmpeg NDK build). | [`11-dependency-inventory.md`](11-dependency-inventory.md) |

## The two findings that shape the port

1. **Android defines `__linux__`** (measured with `aarch64-linux-android28-clang -dM -E`). All 200
   `__linux__` sites in the engine/device/WWVegas sources compile their Linux branch on Android. The
   CMake `ZH_PLATFORM_*` macros do **not** protect C++ (they appear 0 times in C++), so each
   `__linux__` site must be classified as kernel-behaviour (keep), desktop-intent (narrow), or
   Linux-specific-and-wrong-on-Android (branch). This is the single largest source of latent Android
   bugs and the reference port hit it too (`SDL3Main.cpp:260-263`).
2. **The build fingerprint is over source, not the executable** (`GlobalData.cpp:1474-1514`), so
   Android and desktop are compatible by construction. The reference port hashes the executable
   bytes (and skips it on Linux/Android); the target's source fingerprint is the stronger design and
   already covers Android.

## Device / Vulkan capability matrix (the compatibility floor)

The renderer needs SDL3 3.4.16's Vulkan requirement set (doc 07, E2). This matrix is what to test
against; it is a **testing** guide, not a support promise.

| Tier | Vulkan | Required features present? | Expected result |
|:--|:--|:--|:--|
| Modern Adreno/Mali (Vulkan 1.1–1.3) | yes | yes | runs; the target tier |
| Older Vulkan 1.1 devices | yes | usually, minus `depthClamp`/`shaderClipDistance`/`drawIndirectFirstInstance` on some | runs **if** the Phase-2 `SDL_CreateGPUDeviceWithProperties` fallback drops the unused features (doc 07, E2) |
| Vulkan present, driver broken/absent | partial | no | SDL3 GPU device creation fails; the game reports a compatibility limitation and stops (doc 07, Decision) |
| No Vulkan at all | no | no | unsupported — no GLES fallback exists in SDL3 3.4.16 GPU |

Capabilities already handled at runtime (not disabled): BC/S3TC sampling (`SdlResourceMirror`
expands when unsupported) and depth/colour format policy (`SdlGpuDepthFormat.h`).

## Phase-2 backlog produced by these decisions

Grouped by the document that raised them:

**Entry point / build (doc 10)**
- Build `PosixMain.cpp` as `libmain.so` under `ZH_PLATFORM_ANDROID`; keep the `generals` executable
  for the other platforms.
- Add the Gradle shell and a thin `Activity extends SDLActivity`.
- Confirm `__linux__`-guarded early code in `PosixMain.cpp` (the app-id branch) does not run on
  Android.

**Game data (doc 08)**
- `findUserDataDirectory` → app-private internal storage.
- `Registry.ini` marker location on Android.
- Folder picker (SDL dialog or a small `SetupActivity`).
- Confirm read-only install root + no install-root writes (`GameEngine.cpp:988`).

**Crash handler (doc 09)**
- `_Unwind_Backtrace` swap in `StackDumpPosix.cpp`/`CrashHandlerPosix.cpp`; guard `execinfo.h`.
- Ensure `-funwind-tables` for the Android build; offline symbolication tooling.
- `cpudetect.cpp` Android answer.

**Platform macros (doc 12)**
- `ExecutableDirectory.cpp` `__ANDROID__` branch (return `""`).
- Sweep the Class-B `__linux__` sites; decide the water-reflection keybinding and
  `HOTKEYS_FIXED_ORDER_KEYS_DEFAULT` on Android.
- Consider the `ZH_DESKTOP_LINUX` / `ZH_HAS_SDL3_GPU_RENDERER` predicate header.

**Dependencies (doc 11)**
- Font substitute for the glyph rasteriser (fontconfig is desktop-only).
- FFmpeg NDK cross-compile.
- Verify glslang, SPIRV-Cross, SDL_shadercross, GameSpy, mbedTLS build under the NDK.

**GPU (doc 07)**
- Confirm glslang/`SDL_shadercross` under the NDK; measure the real feature floor; add the
  `SDL_CreateGPUDeviceWithProperties` fallback if needed.

**Testing (doc 06)**
- Define the Android smoke set and its host; keep desktop CI (Lavapipe + Xvfb) as the gate.

## Provenance

- Every claim cites an exact file path and line in the per-question documents; those paths are
  relative to the repository root at `c91ab1a`.
- Reference-port citations are `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1` (design guidance
  only, never authoritative).
- Read-only: this task changed no source, build configuration, workflow or dependency.
