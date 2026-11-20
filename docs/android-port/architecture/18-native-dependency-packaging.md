# 18 — Native dependency packaging and APK inspection

Phase 4 of the Android port: classify the engine's native dependencies from the built ELF, package
exactly the ones the APK must carry, and prove the packaged APK is correct by inspecting it. Read
with [`11-dependency-inventory.md`](11-dependency-inventory.md) (what each dependency is) and
[`16-android-application-shell.md`](16-android-application-shell.md) (the Gradle project that
packages them).

## What Phase 4 is not

It does not change rendering, touch, audio, the game filesystem or the graphics stack, and it
packages no game data. It changes only how the app's native payload is built, verified and packaged,
and it is the phase that turns the application shell from "packages a stand-in" into "packages the
real engine and proves it".

## The engine's runtime dependency set

The engine is `libmain.so` (`PosixMain.cpp` built as the `generals` target, `add_library(main
SHARED)`, `OUTPUT_NAME main` — `CMakeLists.txt:2288-2290`). Built for arm64-v8a, its `DT_NEEDED` is
the ground truth for what the APK must carry. Read from the object with the NDK's `llvm-readelf -d`:

> **Superseded (Phase 1, 2026-11-20).** SDL3 is now a standalone shared library, so `libSDL3.so` is
> the second packaged dependency and the APK's native payload is three files. See
> [`../implementation/ANDROID_ENGINE_AND_SDL3_SHARED_LIBRARY.md`](../implementation/ANDROID_ENGINE_AND_SDL3_SHARED_LIBRARY.md).

| `DT_NEEDED` | Class | Provided by | In the APK? |
|:--|:--|:--|:--|
| `libc.so` | Android system | bionic | no (device) |
| `libm.so` | Android system | bionic | no (device) |
| `libdl.so` | Android system | bionic | no (device) |
| `liblog.so` | Android system | bionic | no (device) |
| `libandroid.so` | Android system | bionic (ANativeWindow, etc.) | no (device) |
| `libc++_shared.so` | required in the APK | the NDK sysroot (`ANDROID_STL=c++_shared`) | **yes** |

Every other dependency — SDL3 3.4.16, miniaudio, FreeType, glslang, SPIRV-Cross, SDL_shadercross,
mbedTLS, zlib, LZH-Light, GameSpy, FFmpeg — is a **static archive linked into `libmain.so`**
(`BUILD_SHARED_LIBS OFF`; `SDL_STATIC ON`, `CMakeLists.txt:953`). None appears in `DT_NEEDED` and
none needs a second `.so` in the APK. This is why the APK's native payload is two files.

The classification is not inferred from the CMake declarations: it is read back from the linked
object, so a dependency that silently became shared (the failure mode the reference port hit — a
`libgamespy.so` that was `DT_NEEDED` but not packaged) fails the audit instead of shipping.

## Why the app workflow builds the engine

No compatible native output exists to reuse:

- `ci-android-test.yml` builds and links `libmain.so`, but its artifact is **logs only** — the
  `.so` is not uploaded — and it does not trigger on this branch (its push trigger is
  `android-arm64-port`; its `pull_request` paths do not include `android/**`).
- Nothing commits a `libmain.so`, and it is gitignored where the packaging script stages it.

So the app workflow (`ci-android-app.yml`) builds the engine with the same
`scripts/build/android/build-android.sh` the native workflow uses, then packages it. Rebuilding is
therefore the only way to package the real engine, and it happens in GitHub Actions — never locally.

## What the workflow proves

In order, in `ci-android-app.yml`:

1. the SDK (platforms 35 **and** 28), build-tools 35, the NDK and Gradle 8.12 are installed from
   pinned Google archives;
2. the engine links as `libmain.so` from the real sources;
3. **the ELF audit** reads the dependency set back from the object: `Class: ELF64`,
   `Machine: AArch64`, `Type: DYN`, `SONAME libmain.so`, the exported `SDL_main`, the `DT_NEEDED`
   set classified into system vs packaged (a library from neither class fails), no desktop or
   duplicate library, **zero undefined `SDL_*` symbols** (proving SDL3 is static), the
   `ANDROID_PLATFORM=android-28` build record and the NDK's Android ident note;
4. the Gradle project configures and the packaged manifest is the merged one AGP produces (the
   Phase-2/3 assertions, unchanged);
5. the folder logic is unit-tested on the host (`GameFolderValidatorTest`, `GameDataStagingTest`);
6. `package-android.sh` stages `libmain.so` and `libc++_shared.so` into `jniLibs/arm64-v8a` and runs
   Gradle;
7. **the APK is inspected**: valid zip; exactly `lib/arm64-v8a/libmain.so` and
   `lib/arm64-v8a/libc++_shared.so` and nothing else under `lib/`; no other ABI; no duplicate
   library; no game data and no `assets/` directory; the packaged `libmain.so` is ELF64/AArch64/DYN
   exporting `SDL_main` with the same `DT_NEEDED` as the engine that was built; every non-system
   `DT_NEEDED` is present in the APK; and the badging confirms `applicationId`, `native-code:
   'arm64-v8a'`, `sdkVersion:'28'`, `targetSdkVersion:'35'`, `SetupActivity` as the launcher, and no
   storage permission.

## Permissions

Unchanged from Phase 2/3: `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE`, no storage permission. The
APK inspection asserts their absence. Nothing in Phase 4 introduces a permission.

## What a green run does and does not prove

Proves: the engine and the shell build and package into a structurally correct arm64-v8a APK whose
native payload satisfies the engine's own `DT_NEEDED`, with no game data and no extra ABI.

Does not prove: that the game runs. CI has no device and no Vulkan; the device checklist is Stage 4
(font substitute + on-device verification), which remains.
