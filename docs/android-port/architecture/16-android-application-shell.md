# 16 — The Android application shell

Phase 2 of the Android port (Stage 1, Stage 3 and the Gradle half of Stage 5 in
`../IMPLEMENTATION-CONTINUATION-STATUS.md` §10). Phase 1 left the native half complete and CI-green
and recorded that the Gradle application project, the manifest, the Java Activity and the packaging
script were missing. This document records what was added, why each value is what it is, and what it
does **not** claim.

Nothing under `GeneralsMD/Code/` changed: the native engine, its CMake target and its entry point are
exactly as Phase 1 found them.

## 1. What the shell is

A Gradle application project under `android/` that compiles a thin Java Activity and packages an APK
around the engine library the native build already produces. It does not build the engine: that stays
with `scripts/build/android/build-android.sh` (CMake + the NDK), and a packaging script stages the
result into the project.

```
scripts/build/android/build-android.sh     →  build-android-arm64/libmain.so   (native, Phase 1)
scripts/build/android/package-android.sh   →  stages libmain.so, libc++_shared.so and SDL3's Java
                                              glue into android/, then runs Gradle
android/                                    →  app-debug.apk
```

## 2. Files

Created (all new; nothing existing was modified):

| Path | Role |
|:--|:--|
| `android/settings.gradle` | plugin/dependency repositories, `:app` module |
| `android/build.gradle` | the Android Gradle Plugin version |
| `android/gradle.properties` | JVM heap, non-transitive R class |
| `android/.gitignore` | excludes the staged directories and Gradle output |
| `android/app/build.gradle` | namespace, application id, SDK levels, ABI, version, source sets |
| `android/app/src/main/AndroidManifest.xml` | package identity, Activity, features, permissions |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/ZeroHourActivity.java` | the Activity |
| `android/app/src/main/res/values/strings.xml` | the application label |
| `android/app/src/main/res/values/styles.xml` | the fullscreen theme |
| `android/app/src/main/res/mipmap-*/ic_launcher.png` | the launcher icons, five densities |
| `scripts/build/android/package-android.sh` | stages the libraries and runs Gradle |
| `.github/workflows/ci-android-app.yml` | the shell's CI job |

The icons are generated from the repository's own `GeneralsMD/Code/Main/Generals.ico` with the
repository's own `GeneralsMD/Code/Tools/generals-icon.py`, at the five densities the density buckets
expect (48, 72, 96, 144, 192).

## 3. Gradle decisions

| Value | Choice | Why |
|:--|:--|:--|
| AGP | `8.7.3` | the version SDL3 3.4.16 ships in its own `android-project/build.gradle`, so the shell is built with the toolchain SDL3's Java glue is written against |
| Gradle | `8.12` | SDL3 3.4.16's own wrapper version |
| `namespace` / `applicationId` | `io.github.olcayseygan.zerohourreforged` | a new identity derived from this project's own published id (`io.github.olcayseygan.ZeroHourReforged`, its Flatpak app id); the reference port's `com.generalsx.zerohour` is not copied |
| `compileSdk` / `targetSdk` | `35` | SDL3 3.4.16's android-project targets 35, and it is the platform the shell is compiled against |
| `minSdk` | `28` | the engine is cross-compiled against `android-28` (`scripts/build/android/android.toolchain.cmake`); a lower floor would install on a device that cannot load the library |
| `abiFilters` | `arm64-v8a` | the only ABI the port builds |
| `versionCode` / `versionName` | `40000` / `4.0.0` | the engine's own version (`VERSION_STRING "v4.0.0"`, written by `GeneralsMD/Code/CMakeLists.txt`), in the `major*10000+minor*100+patch` scheme the version name sorts by |
| `ndkVersion` | `27.2.12479018` | the NDK the engine is built with; Gradle uses its strip on the staged libraries, so naming the revision keeps that deterministic |
| source sets | `java-sdl` and `jniLibs` added | both are staged by the packaging script and gitignored |

`android/app/build.gradle` declares no dependency on any library other than the Android platform:
the shell needs none, and SDL3's Java glue is compiled from source (staged), not resolved from a
repository.

## 4. SDL3 Activity integration

`ZeroHourActivity` extends `org.libsdl.app.SDLActivity` (staged from the vendored SDL3 3.4.16 tree)
and overrides exactly one method.

The engine is built as `libmain.so` (`add_library(generals SHARED ...)` with `OUTPUT_NAME main`) from
`Main/PosixMain.cpp`, whose `main()` SDL's `SDL_main.h` renames to the exported `SDL_main`.
`SDLActivity`'s defaults already resolve to `libmain.so` (`getMainSharedObject`) and `SDL_main`
(`getMainFunction`), so the entry point is not overridden and no second native entry point exists.

The one override is `getLibraries()`. SDL3's default returns `{"SDL3", "main"}`, which would ask the
loader for a `libSDL3.so` the APK does not carry — SDL3 is linked **statically** into `libmain.so`
(the build forces `SDL_STATIC` on and `SDL_SHARED` off). `libmain.so` therefore already contains SDL3
and exports its `JNI_OnLoad`, which registers `SDLActivity`'s JNI bindings when the library loads
(SDL3's CMake keeps `JNI_OnLoad` alive for a static build with `-Wl,-u,JNI_OnLoad`). The override
returns `{"main"}` only.

Nothing else is overridden: lifecycle (`onPause`/`onResume`/`onDestroy`), the surface, input dispatch
and the start-up sequence are inherited unchanged. No custom lifecycle logic was added, so nothing
can conflict with SDL3's own.

## 5. Native library integration

The packaging script stages into `android/app/src/main/jniLibs/arm64-v8a/`:

- `libmain.so`, copied from the native build tree (default `build-android-arm64/`, overridable with
  `--build-dir`); a missing library is a failure that names it;
- `libc++_shared.so`, from the NDK sysroot of the same NDK the engine was built with
  (`ANDROID_STL=c++_shared`), located under
  `toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/aarch64-linux-android/`.

The script does not build or replace `libmain.so`; it copies the artifact the native build already
produced.

SDL3's Java glue is staged into `android/app/src/main/java-sdl/org/libsdl/app/` from
`GeneralsMD/Code/Libraries/Source/SDL3/android-project/app/src/main/java/org/libsdl/app/`, so the glue
always matches the SDL3 the engine linked. The script fails if that tree is absent (it is fetched by
`Tools/vendor.sh`, not committed).

## 6. Manifest and resources

Identity, orientation and the `configChanges` set are as §3 and §4 above. Beyond those:

- **Vulkan** is declared (`android.hardware.vulkan.version` `0x401000`) but **not required**: the
  renderer is SDL3's GPU API, whose only Android backend is Vulkan, so a device without a usable
  Vulkan driver fails loudly at start-up by design rather than being refused installation. The
  reference port reaches the same conclusion for the same reason.
- **Touchscreen** is required.
- **Permissions** are the two the existing native code actually uses and nothing else:
  `CHANGE_WIFI_MULTICAST_STATE` for the LAN lobby's Wi-Fi multicast lock
  (`SdlAndroidPlatform.cpp`), and `INTERNET` for the LAN lobby's UDP sockets
  (`GameEngine/Source/GameNetwork/udp.cpp`), which Android refuses to open without it. No storage
  permission is declared: the game reads a folder the player picks and writes only to its own private
  storage (`08-game-data-delivery.md`). `MANAGE_EXTERNAL_STORAGE`, which the reference port declares,
  is deliberately not adopted.
- **Theme** is the fullscreen `AppTheme`; there are no widgets to theme.

## 7. Packaging script

`scripts/build/android/package-android.sh`:

```
scripts/build/android/package-android.sh [--sdk DIR] [--ndk DIR] [--build-dir D] [--release] [--install]
```

It stages the two libraries and the Java glue, then runs `gradle assembleDebug` (or `assembleRelease`)
in `android/`, and reports the APK path. `--install` runs `adb install -r`. The SDK must hold platform
`android-35`; the NDK must be named for `libc++_shared.so`; Gradle is taken from `android/gradlew` if
present, otherwise from `PATH`.

## 8. CI

`.github/workflows/ci-android-app.yml` ("CI Android App") is a dedicated job, separate from
`ci-android-test.yml` because it answers a different question. That workflow proves the native half
(configure, compile, link `libmain.so`); this one proves the shell half:

1. the Android SDK, build-tools, platform 35 and the NDK are set up from pinned Google archives, and
   Gradle 8.12 is on `PATH`;
2. `vendor.sh --no-art` provides SDL3's Java glue;
3. the packaging script is syntactically valid;
4. a stand-in `libmain.so` (exporting `SDL_main`) is compiled with the NDK and placed where
   `build-android.sh` writes the real one, so the packaging path is exercised without a multi-hour
   native build in this job; the stand-in is never shipped and never committed;
5. the merged manifest is asserted: the project's own package id, no reference id, the launcher
   Activity, landscape, the SDL `configChanges` set, the two justified permissions, no storage
   permission, at most two permissions in total;
6. an APK is assembled and its contents inspected: `libmain.so` and `libc++_shared.so` under
   `lib/arm64-v8a`, no other ABI, no separate SDL3 shared object, and the packaged `libmain.so` is an
   AArch64 object.

The workflow is triggered on this branch, on pull requests touching `android/**` or the packaging
script, and manually.

## 9. Verification and limits

Verified locally, then by CI (see the pull request for the run):

- `gradle :app:assembleDebug` and `:app:assembleRelease` both produce an APK.
- `gradle :app:lintDebug` passes.
- The merged manifest and the APK contents satisfy every assertion in §8.
- `aapt2 dump badging` reports the expected package id, version, SDK levels, label, icon and launcher
  Activity.

What is **not** claimed, because it cannot be shown in CI and no device was used:

- that the APK installs or launches on a physical device;
- that the game renders, plays audio or accepts touch input on device;
- that the game finds its data — the folder picker is Stage 2 and is not part of this phase.

A green run here proves the shell configures, compiles and packages a correct APK around the engine
library; it does not prove the game runs.
