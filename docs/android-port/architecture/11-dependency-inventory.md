# 11 — External and Linux dependency inventory for Android

Resolves the dependency half of **question 7 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
(the `sys/timeb.h` include and the platform-specific dependency set) and supports
[`12-platform-macro-audit.md`](12-platform-macro-audit.md). Read-only: no source, build or
dependency file was changed.

## What this document is

An inventory of every external dependency the POSIX build pulls in, marked **Android-OK**,
**Android-substitute**, **Android-off**, or **desktop-only**, with the evidence for each. It answers
"what must build or change for the Android build to link".

## Inventory

| Dependency | Source | Where used | Android status |
|:--|:--|:--|:--|
| **SDL3 3.4.16** | vendored (`Libraries/Source/SDL3`, `vendor.sh:434`) | window, events, entry, GPU, dialog | **Android-OK** — SDL3 supports Android natively (doc 07, doc 10) |
| **miniaudio 0.11.25** | vendored (`vendor.sh:477`) | audio under the Miles surface | **Android-OK** — has AAudio (Android 8+) and OpenSL ES (API 16+) backends (`miniaudio.h:518-524,3630-3631`); the current build links it with `Threads` only |
| **glslang** (vulkan-sdk-1.4.357.0) | vendored (`vendor.sh:516`) | HLSL → SPIR-V | **Android-OK to build** — pure C++ front end; must be compiled by the NDK (Phase-2 verification) |
| **SPIRV-Cross** (1a616956) | vendored (`vendor.sh:542`) | SPIR-V reflection | **Android-OK to build** |
| **SDL_shadercross** (1ff05bec) | vendored (`vendor.sh:565`) | SPIR-V → SDL GPU shader | **Android-OK to build** |
| **FreeType 2.14.3** | vendored (`vendor.sh:582`) | glyph rasteriser | **Android-OK** — FreeType builds under the NDK; the glyph rasteriser is the same code as Linux |
| **fontconfig** | **system** (`find_package(Fontconfig REQUIRED)`, `CMakeLists.txt:957`) | finding Liberation fonts (`glyphrasteriser.cpp:35,90-117`) | **Android-off** — gated `if(ZH_PLATFORM_LINUX)`, so Android does not link it. Android has no fontconfig; a font-finding substitute is a Phase-2 item |
| **X11 / Wayland / xkbcommon** | **system**, via SDL3 | SDL video backends | **Android-off** — SDL3 on Android uses the Android video driver, not X11/Wayland (`DetectPlatform.cmake` keeps these out) |
| **zlib 1.1.4** | vendored (`vendor.sh:141`) | compression | **Android-OK** — portable C |
| **LZH-Light 1.0** | vendored (`vendor.sh:281`) | compression | **Android-OK** — portable C++ |
| **GameSpy SDK** | vendored (`vendor.sh:326`) | lobby (compiled everywhere since B6) | **Android-OK to build** — already built on POSIX with two patches (`CMakeLists.txt:858-875`); it must compile for Android (Phase-2 verification) |
| **mbedTLS 3.6.7** | vendored (`vendor.sh:622`) | TLS under the online lobby (`OnlineConnection.cpp`) | **Android-OK** — portable C, and bionic ships its own TLS anyway; the vendored copy is what the code links |
| **FFmpeg 8.1.2** | vendored source tarball (`vendor.sh:600`) | movie playback (Bink API on FFmpeg) | **Android-OK to build, but needs work** — see below |
| **CURL + GameNetworkingSockets (Steam)** | vcpkg `x64-windows-static` | GeneralsOnline lobby client | **desktop-only** — gated `ZH_PLATFORM_WINDOWS AND ... CMAKE_GENERATOR_PLATFORM STREQUAL "x64"` (`CMakeLists.txt:1146`); not built on POSIX, so **not an Android dependency** |
| **DirectX 8/9 headers & libs** | vendored, Windows-only (`vendor.sh:315`) | Windows renderer | **not on Android** — the POSIX renderer is SDL3 GPU (doc 07) |
| **DXVK** | — | — | **not used** (doc 07) |
| **`sys/timeb.h` / `_ftime`** | **system** | `Tools/{matchbot,mangler}/wlib` only | **Android-off / build-system concern** — see below |

## Findings that need attention

### F1 — `sys/timeb.h` is used only by host tools, not by the engine

`sys/timeb.h` and `_ftime` appear **only** in `Tools/matchbot/wlib/*` and `Tools/mangler/wlib/*`
(`wstypes.h:53`, `xtime.h:46`, `wtime.h:37`, `xtime.cpp:291`, `wtime.cpp:87`, `timezone.cpp:27`).
These are **host build tools**, not part of the game. **Bionic has no `sys/timeb.h`** (verified: the
NDK r27c sysroot does not contain it). So the Android build must simply **not build those host
tools** — which it already won't, because they are desktop-only tools. **No `__ANDROID__` guard is
needed in the engine**; the fix is to keep the tool targets out of the Android configure. (If any
host tool *is* needed at Android build time, it runs on the build host, not on the device, so it
links against the host libc and is unaffected.)

### F2 — fontconfig must be replaced by something on Android

`glyphrasteriser.cpp:35` includes `<fontconfig/fontconfig.h>` and uses it to find Liberation fonts
by family name (`:90-117`). The CMake gates this `if(ZH_PLATFORM_LINUX)` (`CMakeLists.txt:956-958`),
so Android will not link fontconfig — but then **the glyph rasteriser must find its fonts another
way**. Options (Phase 2, not decided here): bundle the needed TrueType files in the APK assets and
point the rasteriser at them, or query Android's own font system. This is a **real gap**, not a
mechanical one; it is the clearest Android-specific dependency substitute required.

### F3 — FFmpeg must be cross-compiled for the NDK, and its configure assumes a desktop host

The POSIX build compiles FFmpeg from the vendored tarball into the build tree
(`CMakeLists.txt:1761-1800`), and its configure "picks the host's architecture" (`:1785-1792`) with
macOS-specific handling. On Android this must become an **NDK cross-compile** (arm64-v8a, API 28),
which is a Phase-2 build task. The engine side (the Bink demuxer on FFmpeg, `W3DDisplay.cpp`,
`MilesAudioManager.cpp`) is platform-neutral once the library exists. **Android-OK but non-trivial.**

### F4 — The online lobby client is Windows-only and stays out

`CURL`, `GameNetworkingSockets`, `OpenSSL` and the `zhonline` library are gated on
`ZH_PLATFORM_WINDOWS` (`CMakeLists.txt:1145-1165`). They are **not** part of the POSIX build, so
Android inherits their absence for free. The GameSpy lobby (vendored, compiled everywhere) remains;
the GeneralsOnline client is a Windows feature and is out of scope for Android unless a future task
adds it.

## Summary

- **Android-OK as-is (build under the NDK):** SDL3, miniaudio, glslang, SPIRV-Cross, SDL_shadercross,
  FreeType, zlib, LZH-Light, GameSpy, mbedTLS.
- **Android-off, already gated:** fontconfig, X11/Wayland/xkbcommon, CURL/GameNetworkingSockets/Steam,
  DirectX, DXVK, `sys/timeb.h` host tools.
- **Needs Phase-2 work:** fontconfig substitute (F2), FFmpeg NDK cross-compile (F3), and confirming
  glslang/SPIRV-Cross/GameSpy compile under the NDK (F-inventory rows above).

## Open items handed to Phase 2

1. Font substitute for the glyph rasteriser (F2) — the one genuine gap.
2. FFmpeg NDK cross-compile (F3).
3. Verify glslang, SPIRV-Cross, SDL_shadercross, GameSpy and mbedTLS configure and compile under the
   NDK r27c toolchain.
4. Confirm the Android configure excludes the host tools that use `sys/timeb.h` (F1).

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- `sys/timeb.h` absence verified against the NDK r27c sysroot; miniaudio backends read from the
  vendored `miniaudio.h`.
- Read-only: no source, build or dependency file was changed.
