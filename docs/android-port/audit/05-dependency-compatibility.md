# Android ARM64 dependency compatibility report (Phase 1)

This report is the Phase 1 deliverable for **Task 4 (Audit Dependencies)** of the Android ARM64
platform foundation. It covers every third-party library the POSIX build actually pulls in, as it is
configured in `GeneralsMD/Code/CMakeLists.txt`, and classifies each one against the Android NDK
build. It is a snapshot of the build configuration at the branch `android-arm64-port`; the earlier
read-only inventory in
[`../architecture/11-dependency-inventory.md`](../architecture/11-dependency-inventory.md) is the
audit that this one turns into build decisions.

Nothing here disables a dependency to make the configure pass. Where a dependency is not built for
Android, it is because it is already gated out of a non-Windows build by design (the Windows-only
lobby client, the DirectX SDK, the desktop Linux system libraries) or because it is not needed until
Phase 2. The gate is `ZH_PLATFORM_LINUX` / `ZH_PLATFORM_WINDOWS`, which
`cmake/DetectPlatform.cmake` answers, and Android is neither.

## Classification

1. **Android-compatible and ready** - builds under the NDK with no source change.
2. **Requires Android-specific configuration** - the build must pass Android-only settings (a
   toolchain file, an `ANDROID_*` variable, an API level, a back-end selection), but no source edit.
3. **Requires source modifications** - a source change is needed before it compiles or runs.
4. **Requires replacement or an alternative backend** - the library cannot be used on Android; a
   substitute must be found.
5. **Not required for the initial Android build** - gated out of Android by platform, or deferred.

| Dependency | Version / source | Where it is used | Class | Evidence / note |
|:--|:--|:--|:--:|:--|
| **SDL3** | 3.4.16, vendored (`Libraries/Source/SDL3`, `Tools/vendor.sh`) | window, events, entry point, GPU API, dialog | **1** | SDL3 has a native Android video driver and a Vulkan GPU backend; the NDK toolchain file selects them. Configured in CI. |
| **SDL3 GPU** | part of SDL3 (`SDL_GPU=ON`) | the POSIX renderer | **1** | `SDL_gpu_vulkan.c` is built when `SDL_VIDEO`/`SDL_GPU` are on; the NDK sysroot ships `<vulkan/vulkan.h>` for API 28. |
| **Vulkan** | NDK sysroot (`libvulkan.so`, headers) | SDL3 GPU back end | **2** | Not vendored: the NDK provides `libvulkan` and the headers for `android-28`; nothing to build, only to link. |
| **miniaudio** | 0.11.25, vendored | audio under the Miles surface | **1** | One C file; AAudio (Android 8+) and OpenSL ES (API 16+) back ends are in `miniaudio.h`. Built in CI. |
| **FreeType** | 2.14.3, vendored | glyph rasteriser | **1** | Builds under the NDK with `FT_DISABLE_*` on (already set for POSIX). |
| **fontconfig** | system (`find_package(Fontconfig)`) | font discovery in `glyphrasteriser.cpp` | **4** | Desktop-only, gated `if(ZH_PLATFORM_LINUX)`, so Android never links it. Android needs a font substitute (bundle TrueType files, or query the platform) - a Phase-2 item. The guard is already in place (`glyphrasteriser.cpp` takes the no-fontconfig branch, as macOS does). |
| **mbedTLS** | 3.6.7, vendored | TLS under the online lobby | **1** | Portable C; `library/*.c` compiled directly with the stock config. Built in CI. |
| **zlib** | 1.1.4, vendored | compression | **1** | Portable C. |
| **LZH-Light** | 1.0, vendored | compression | **1** | Portable C++ with the `lzhl-clear-history.patch` applied everywhere. |
| **GameSpy SDK** | vendored (`Libraries/Source/GameSpy`) | online lobby menus | **3** | Builds on POSIX with two patches; under the NDK it stops on `gsthreadlinux.c`'s `pthread_cancel`, which bionic does not have. The call is a thread-teardown path that never runs until a player opens the online menus; the fix (a bionic-safe join/cancel shim or a `#if defined(__ANDROID__)` arm) is Phase 2. |
| **glslang** | vulkan-sdk-1.4.357.0, vendored | HLSL to SPIR-V | **2** | Pure C++; must be compiled by the NDK. Configured in CI; a full build is a Phase-2 confirmation. |
| **SPIRV-Cross** | vendored | SPIR-V reflection | **2** | Same as glslang. |
| **SDL_shadercross** | vendored | SPIR-V to SDL GPU shader | **2** | Same as glslang. |
| **FFmpeg** | 8.1.2 source tarball, vendored | movie playback (Bink on FFmpeg) | **3** | The POSIX block builds it for the *host* (`Tools/ffmpeg-build-posix.sh`), and the arch axis is Apple-only, so an Android link pulls x86-64 objects. It needs an NDK cross-compile (arm64-v8a, API 28) - a Phase-2 build task. The engine side (the Bink demuxer on FFmpeg) is platform-neutral once the library exists. |
| **Bink** | header-only shim, vendored (`WWVegas/Bink/include`) | the Bink video API | **5** | The retail Bink DLLs are 32-bit and Windows-only; the API is reconstructed over FFmpeg on POSIX. Android inherits the FFmpeg path (Class 3 above); the shim itself needs nothing. |
| **Miles** | header-only shim + `Miles6/miniaudio`, vendored | the Miles audio API | **5** | Same shape as Bink: the retail Miles DLLs are Windows-only, the API is reconstructed over miniaudio on POSIX. Android uses the miniaudio path (Class 1). |
| **X11 / Wayland / xkbcommon** | system, via SDL3 | SDL video back ends | **5** | Gated out: SDL3 on Android uses the Android video driver. `DetectPlatform.cmake` never sets `ZH_PLATFORM_LINUX` for Android, and the link dependencies are gated on it. |
| **DirectX 8/9 headers and libs** | vendored, Windows-only | Windows renderer | **5** | The POSIX/Android renderer is SDL3 GPU; the DirectX SDK is not read off Windows. |
| **DXVK** | - | - | **5** | Not used by this port at all (the reference port used it; this one uses SDL3 GPU directly). |
| **CURL + GameNetworkingSockets + OpenSSL** | vcpkg `x64-windows-static` | GeneralsOnline lobby client | **5** | Gated `ZH_PLATFORM_WINDOWS AND ... CMAKE_GENERATOR_PLATFORM STREQUAL "x64"`; never built off Windows, so Android inherits their absence. |
| **`sys/timeb.h` host tools** | system | `Tools/{matchbot,mangler}/wlib` only | **5** | Bionic has no `<sys/timeb.h>`. The only `ftime`/`struct timeb` users are host build tools, which the Android configure does not build. The engine's own include is already guarded (`!defined(__ANDROID__)`, commit `087180d`). |

## What CI verifies for this report

The `CI Android Test` workflow configures the ARM64 build with the NDK and then asserts, from the
generated `build.ninja` and `CMakeCache.txt`, that:

- the Android-off dependencies (fontconfig, X11/Wayland, LLD/mold) are **absent** from the link
  graph - the check is a negative one, so it cannot pass by accident;
- SDL3 configures for Android with the video and GPU subsystems on;
- the compiler is `aarch64-linux-android` and the ABI is `arm64-v8a`.

It then compiles a bounded, dependency-light set of targets under the NDK - `test_platform_predicates`,
SDL3, miniaudio and mbedTLS - which is a real Android compilation of both first-party and third-party
code. The Class-3 dependencies (GameSpy `pthread_cancel`, FFmpeg host build) are not hidden: they are
recorded here and in
[`../architecture/12-platform-macro-audit.md`](../architecture/12-platform-macro-audit.md) as Phase-2
work, and the workflow does not attempt a target that would fail on them.

## Open items handed to Phase 2

1. fontconfig substitute for the glyph rasteriser (Class 4) - the one genuine replacement.
2. FFmpeg NDK cross-compile (Class 3).
3. GameSpy `pthread_cancel` bionic fix (Class 3).
4. Confirm glslang / SPIRV-Cross / SDL_shadercross compile under the NDK (Class 2).
5. `backtrace()` -> `_Unwind_Backtrace` + `dladdr` (see
   [`../architecture/09-ndk-api-and-backtrace.md`](../architecture/09-ndk-api-and-backtrace.md));
   it is not a dependency but the same kind of blocker.
