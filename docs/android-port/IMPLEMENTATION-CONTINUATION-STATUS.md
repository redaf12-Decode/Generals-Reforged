# Android ARM64 port — implementation continuation status

Phase 1 — deep audit and implementation gap analysis of the **target** branch
`android-arm64-port` in `redaf12-Decode/Generals-Reforged`, to answer one question: what is already
implemented, what is incomplete, and what is missing before this repository can ship a runnable
Android application.

This document does not replace the existing design records — it indexes them, states what is still to
be built, and adds the dependency/integration map and the per-stage verification criteria the
implementation phases will follow. **Nothing was changed to write it: no application, native, CMake,
workflow or dependency file was touched. Only this document was written.**

> Provenance of this revision. An earlier revision of this same file was committed as `8855b7c`
> ("docs(android): audit the existing port and plan the application layer") on this development
> branch and described itself as a "Phase 0 audit". This revision is the Phase 1 deliverable: every
> fact below was re-read from the working tree at the audited commit, and the sections the earlier
> revision lacked (the dependency/integration map, the staged plan with inter-stage dependencies, and
> the verification criteria per stage) have been added. The earlier findings were re-verified, not
> copied.

> **Phase 2 (application shell) is now implemented** on this branch: Stage 1 (Gradle project,
> manifest, thin `SDLActivity` subclass, resources, SDL3 Java glue staged by a script,
> `libc++_shared.so` staged), Stage 3 (packaging) and the Gradle half of Stage 5 (CI) of §10 below.
> Stage 2 (the folder picker) and Stage 4 (font substitute + on-device verification) remain. The
> shell's design, the values chosen and what a green CI run does and does not prove are recorded in
> [`architecture/16-android-application-shell.md`](architecture/16-android-application-shell.md). No
> file under `GeneralsMD/Code/` was changed.

> **Phase 3 (game folder access) is now implemented** on this branch: Stage 2 of §10 below - the
> Android folder picker. A `SetupActivity` launcher screen drives the system Storage Access Framework
> picker (`ACTION_OPEN_DOCUMENT_TREE`), takes the read grant as persistable so it survives a restart,
> validates the picked folder against the engine's own rule (`INIZH.big` + the base game's
> `Textures.big`), and stages it **once, incrementally** into the app's own external files directory
> - a real POSIX path, because the engine's file layer cannot open a `content://` URI. The engine is
> rooted there with `-root`, which `PosixMain` already honours. No `MANAGE_EXTERNAL_STORAGE` is
> requested; the two Phase-2 permissions are unchanged. Stage 4 (font substitute + on-device
> verification) remains, and nothing here is verified on a device. The design is recorded in
> [`architecture/17-game-folder-access.md`](architecture/17-game-folder-access.md). No file under
> `GeneralsMD/Code/` was changed.

> **Phase 4 (native dependencies and APK packaging) is now implemented** on this branch: Stage 3
> (packaging) and the CI half of Stage 5 of §10 below, made real. The application workflow
> (`ci-android-app.yml`) now builds the engine itself with `scripts/build/android/build-android.sh`
> (no compatible native output exists to reuse — the native workflow uploads logs, not the `.so`),
> reads the engine's runtime dependency set back from the linked ELF, classifies every `DT_NEEDED`
> as Android-system or packaged, and inspects the actual APK: exactly `libmain.so` +
> `libc++_shared.so` under `lib/arm64-v8a`, no other ABI, no duplicates, no game data, every
> non-system `DT_NEEDED` present, `SDL_main` exported, SDL3 provably static (zero undefined `SDL_*`
> symbols), `minSdk 28` / `target 35`, `SetupActivity` the launcher, no storage permission. The
> design is recorded in [`architecture/18-native-dependency-packaging.md`](architecture/18-native-dependency-packaging.md).
> Stage 4 (font substitute + on-device verification) remains, and nothing here is verified on a
> device. No file under `GeneralsMD/Code/` was changed.

> **Phase 5 (Android lifecycle, Vulkan, touch and audio integration) is now implemented** on this
> branch, in its first integration slice: the application lifecycle the platform layer already
> watched now also takes the live touch gesture away when the app leaves the foreground. SDL delivers
> no `FINGER_UP` on a backgrounding, so a gesture that was down was left latched and the next finger
> was read as the second of a stale one; `SdlAndroidLifecycle_cancelsInput` (the same transition that
> stops the audio device, once) drives `SdlTouch_reset`, which now resolves a gesture that owes the
> engine a button-up (a held UI press, a committed placement, an area-selection box) as a
> cancellation before dropping it, and a canceled pan no longer coasts. The design and the
> verification are recorded in
> [`architecture/19-android-lifecycle-and-input-integration.md`](architecture/19-android-lifecycle-and-input-integration.md).
> Vulkan device-loss handling, the audio lifecycle on a real device and Stage 4 (font substitute +
> on-device verification) remain, and nothing here is verified on a device.

> **Phase 6 (Android documentation and in-app device information) is now implemented** on this
> branch: the Android project is documented for users in [`../../android/README.md`](../../android/README.md),
> the implementation and its hardware floor are audited in
> [`audit/ANDROID-IMPLEMENTATION-FACTS.md`](audit/ANDROID-IMPLEMENTATION-FACTS.md) and
> [`audit/ANDROID-HARDWARE-COMPATIBILITY.md`](audit/ANDROID-HARDWARE-COMPATIBILITY.md), and the setup
> screen now opens a **System Requirements & Device Compatibility** screen (`CompatibilityActivity`)
> that shows the application's requirements beside this device's reported capabilities and an
> evaluated result. The requirements live in one place (`AndroidRequirements`) that both the screen
> and the documentation read, so they cannot disagree. The Vulkan physical-device name, vendor/device
> IDs and per-feature support are **not** available from the Android platform API, so the screen
> reports them as unverified rather than inventing them; the mandatory Vulkan *features* are checked
> only by the engine when it creates the device. No file under `GeneralsMD/Code/` was changed.

> **Phase 2 of the SDL3 application work is now implemented** on branch
> `agent/android-sdl3-android-completion`: SDL3's Java glue is integrated from the vendored 3.4.16
> tree (staged, compared byte for byte with the source, and checked against the JNI methods
> `libSDL3.so` actually registers), the load order (`libSDL3.so` before `libmain.so`) is stated in
> `ZeroHourActivity` and asserted, and the three libraries the APK carries are staged into
> `android/app/src/main/jniLibs/arm64-v8a/` by a script that wipes the directory, verifies the ELF
> objects (`scripts/build/android/verify-android-native-libs.sh`), drops AGP's stale native-lib
> intermediates, and re-verifies the APK's own payload against the staged one. Missing, unused,
> desktop, x86/x86_64, duplicate-SDL3 and unresolved libraries are packaging failures, not warnings.
> The design, the exact files and the static validation are recorded in
> [`implementation/ANDROID_SDL3_APPLICATION_PACKAGING.md`](implementation/ANDROID_SDL3_APPLICATION_PACKAGING.md).
> Nothing was built or run locally, no file under `GeneralsMD/Code/` was changed, and no device has
> loaded the pair yet.

---

## 1. Repository and branch information

| Item | Value |
|:--|:--|
| Target repository | `https://github.com/redaf12-Decode/Generals-Reforged` |
| Target base branch | `android-arm64-port` |
| Development branch | `agent/android-arm64-application-port` |
| Remote | `origin` → `https://github.com/redaf12-Decode/Generals-Reforged.git` |
| Clone depth | shallow (`git rev-parse --is-shallow-repository` → `true`) |
| Reference repository | `https://github.com/redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1` |

Remote branches present at audit time (`git ls-remote --heads origin`):

| Branch | Commit | Note |
|:--|:--|:--|
| `main` | `109962c` | untouched default |
| `android-arm64-port` | `e3a00c4` | the base branch audited here |
| `agent/android-arm64-application-port` | `8855b7c` | this development branch (the earlier audit revision) |
| `docs/android-port-audit-01` | `45340c4` | the earlier read-only architecture audit |
| `Android/port` | `6d15c5d` | unrelated |

Working tree at the start of this phase: clean except the untracked `.openhands/` memory directory
(agent tooling, not part of the port).

## 2. Audited commit SHA

- **Audited commit:** `e3a00c4eebd0e5c0f47ce3ee1c80081b16fc781a`
  (`feat(android): audio, storage and game services for ARM64`, 2026-11-17T05:39:48Z) on
  `android-arm64-port`. This is the base branch's tip and the parent of this development branch.
- **Last green CI on the audited commit:** `CI Android Test` run `44025684120`, conclusion `success`.
- **Last green CI on this development branch (earlier revision):** `CI Android Test` run
  `44037451257`, `workflow_dispatch`, `success` (2026-11-17T07:16:39Z).

All paths in this document are relative to the repository root and were read at `e3a00c4`.

## 3. Existing native implementation inventory (do not recreate)

The native engine half of the port is complete and CI-green. Everything below exists at the audited
commit. It is protected by the phase rules — preserve existing working implementations, do not
recreate existing files/workflows/targets, do not change native engine behaviour, do not replace the
SDL3 GPU/Vulkan renderer or the touch/miniaudio implementations, and do not introduce DXVK, Wine,
Box64, OpenAL, Turnip or AdrenoTools.

| Component | Path | State |
|:--|:--|:--|
| Android toolchain layer | `scripts/build/android/android.toolchain.cmake` | complete (ABI `arm64-v8a`, API 28, `c++_shared`, Clang, C++17) |
| Android build script | `scripts/build/android/build-android.sh` | complete (validates SDK/NDK/tools, configures Ninja into `build-android-arm64/`) |
| Platform detection (CMake) | `GeneralsMD/Code/cmake/DetectPlatform.cmake` | complete; Android is POSIX, **never** Linux |
| Architecture detection (CMake) | `GeneralsMD/Code/cmake/DetectArchitecture.cmake` | complete; the ABI decides alone |
| C++ platform predicates | `GeneralsMD/Code/Libraries/Include/Platform/PlatformPredicates.h` | complete (`ZH_ANDROID`, `ZH_DESKTOP_LINUX`) |
| Engine as `libmain.so` | `GeneralsMD/Code/CMakeLists.txt:2282-2290` | complete; `add_library(generals SHARED …)`, `OUTPUT_NAME main` |
| Android entry point | `GeneralsMD/Code/Main/PosixMain.cpp` | complete; `main()` becomes `SDL_main` via `<SDL3/SDL_main.h>` (first include) |
| Android storage bootstrap | `PosixMain.cpp` `establishAndroidStorage()` (`:436-467`) | complete; sets `HOME` from `SDL_GetAndroidInternalStoragePath()` |
| User-data convention | `GameEngine/Include/Common/EarlyOptions.h` (`USER_FOLDERS_ANDROID`, `:291-297,317,443,475`) | complete; `<HOME>/Command and Conquer Generals Zero Hour Data` |
| Executable-path handling | `GameEngine/Source/Common/System/ExecutableDirectory.cpp` (`:67,119-121`) | complete; Android branch returns `""`, `isExecutablePackaged()` → TRUE |
| Crash-handler unwinder | `Libraries/Include/Platform/StackWalk.h` (`zh_backtrace`, `:74-127`) | complete; `_Unwind_Backtrace` + `dladdr` on Android; used by `CrashHandlerPosix.cpp:475,723`, `StackDumpPosix.cpp:81,96`, `PosixDevice9Timing.cpp`, `PosixDevice9Draw.cpp` |
| SDL3 GPU Vulkan renderer | `GameEngineDevice/Source/PosixDevice/Render/` + `SdlGpuDeviceFeatures.{h,cpp}` | complete; feature fallback, depth clip, cross-format present |
| Native touch input | `GameEngineDevice/{Include,Source}/SdlDevice/GameClient/SdlTouch.{h,cpp}` | complete; Android half behind `ZH_ANDROID` |
| Android platform services | `GameEngineDevice/{Include,Source}/SdlDevice/Common/SdlAndroidPlatform.{h,cpp}` | complete; lifecycle watch, screen-on, Wi-Fi multicast lock, JNI |
| miniaudio Android backends | `Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp` | complete; AAudio then OpenSL ES, `AIL_ex_device_pause/resume` |
| GameSpy bionic fix | `Libraries/Source/gamespy-android-thread.patch` | complete; `pthread_cancel` guarded under `__ANDROID__` (verified in the patch body) |
| FFmpeg aarch64 cross-build | `Tools/ffmpeg-build-posix.sh` + `CMakeLists.txt:1886-1905` | complete; NDK wrapper handed to `configure` |
| Android CI (native only) | `.github/workflows/ci-android-test.yml` (548 lines) | complete **for its scope**: configure + compile + link + assert |

`libmain.so` at the audited commit is an ELF64/AArch64/DYN object exporting `SDL_main`, whose
`DT_NEEDED` is only Android system libraries (`libm`, `libdl`, `liblog`, `libandroid`, `libc++_shared`,
`libc`) — no desktop-Linux library, no SDL3 shared object, no GameSpy/FFmpeg shared object. So the
**only** shared library the APK must supply besides `libmain.so` itself is `libc++_shared.so`.

Design decisions this rests on (read before changing anything):
`docs/android-port/architecture/07-gpu-backend-decision.md` (Vulkan only, no GLES/DXVK),
`08-game-data-delivery.md` (user-picked folder, no APK assets, no `MANAGE_EXTERNAL_STORAGE`),
`09-ndk-api-and-backtrace.md`, `10-android-entry-point.md`, `11-dependency-inventory.md`,
`12-platform-macro-audit.md`, `13-android-vulkan-rendering.md`, `14-native-touch-input.md`,
`15-audio-storage-and-game-services.md`, `FINAL-PLATFORM-DECISIONS.md`.

## 4. Existing Android application components

**None.** There is no Android application layer in the repository at the audited commit. Verified by
direct inspection:

- No `android/` directory (`ls android` → no such directory).
- No `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradle/` wrapper.
- No `AndroidManifest.xml`, no Java/Kotlin source, no `res/`.
- No `jniLibs/`, no staged SDL Java glue (`java-sdl/`).
- No `apk/` directory and no `.apk` anywhere in git.
- No `CMakePresets.json` and no `vcpkg.json` (the target configures through
  `scripts/build/android/build-android.sh`, not presets).

The only Android-side files that exist are the **native** ones listed in §3 (C++ and CMake) and the
CI workflow. The application shell the phase plan calls for — Gradle project, manifest, Activity,
resources, packaging — does not exist yet.

## 5. Missing application components

Everything the application layer needs is missing. The list, with the reason each is needed:

1. **Gradle project** — `android/settings.gradle`, `android/build.gradle`, `android/gradle.properties`,
   `android/app/build.gradle`. Required to compile the Java shell and package the APK.
2. **Manifest** — `android/app/src/main/AndroidManifest.xml` with a stable package id, arm64-v8a,
   `minSdk 28`, Vulkan declared but **not** required (a device without usable Vulkan fails loudly by
   design, `07-gpu-backend-decision.md`), the SDL `configChanges` set, and only justified permissions
   (notably `CHANGE_WIFI_MULTICAST_STATE`, see §8.4).
3. **Java shell** — a thin `Activity extends org.libsdl.app.SDLActivity` naming the libraries to load
   (`SDL3`, `main`). SDLActivity's defaults already are `libmain.so` + `SDL_main` (verified in the
   pinned SDL3 tree, §7), so this is small.
4. **SDL3 Java glue** — `org.libsdl.app.*` staged from the vendored SDL3 3.4.16 `android-project`
   (not hand-copied; the whole SDL3 tree is fetched by `Tools/vendor.sh`, §7).
5. **Resources** — app name ("Generals Reforged"), icon, theme, strings.
6. **Game-folder selection on Android** — the existing `chooseInstallRoot()` path calls
   `SDL_ShowFileDialogWithProperties(SDL_FILEDIALOG_OPENFOLDER, …)`, which **SDL3 3.4.16 does not
   implement on Android** (§8.2). A working picker is a genuine gap.
7. **`libc++_shared.so` staging and APK packaging** — no script exists.
8. **CI APK job** — the existing workflow stops at the native link and never packages an APK.

## 6. Existing CI and build artifact inventory

### 6.1 Workflows present

| Workflow | Trigger | Relevance to Android |
|:--|:--|:--|
| `.github/workflows/ci-android-test.yml` | `workflow_dispatch`; `push` to `android-arm64-port`; `pull_request` on a path filter | The Android gate. Native only: configure, compile, link, assert. |
| `.github/workflows/linux-release.yml` | `pull_request`; `workflow_dispatch`; `push` to `main`/tags | Desktop gate (ctest under Lavapipe + Xvfb). |
| `.github/workflows/commit-messages.yml` | `pull_request` | Commit-subject lint. |

### 6.2 What `ci-android-test.yml` does (verified step by step)

- Installs a pinned, checksummed cmdline-tools, NDK `27.2.12479018`, and platform `android-28` only.
- Runs the two `cmake -P` detection matrices (`test_platform_detection.cmake`,
  `test_architecture_detection.cmake`); asserts Android is never Linux and arm64-v8a is AArch64.
- Configures the ARM64 tree, asserts toolchain/ABI/API/STL, asserts the desktop-Linux libraries
  (fontconfig, X11/Wayland, LLD/mold) are **absent**, and asserts `SDL_GPU_VULKAN` is on.
- Compiles a bounded target set, then **links the whole engine as `libmain.so`** and asserts it is
  AArch64/DYN, exports `SDL_main`, and links only Android system libs plus `libc++_shared.so`.
- Asserts the Phase-4 (touch) and Phase-5 (audio/storage/platform) objects define the Android-only
  symbols, so a desktop stub cannot satisfy them.
- Uploads configure/build logs as the artifact `android-arm64-diagnostics`
  (`android-configure.log`, `android-build.log`, `CMakeSystem.cmake`, `CMakeCXXCompiler.cmake`).

### 6.3 Artifacts

- **No GitHub Releases** exist (`releases` API → 0).
- **No APK artifact** is produced or uploaded by any workflow. The only uploaded artifact is the
  diagnostics bundle above; `libmain.so` is built in the runner's temp tree and discarded.
- Build output trees (`build-android-arm64/`, `build/`, `build-linux/`, `build-mac/`) are gitignored
  and not present in the checkout.

### 6.4 CI capabilities and limitations

`ci-android-test.yml` never packages an APK, never runs Gradle, never validates a manifest, never
runs on a device or emulator, and installs only platform `android-28` — not the `android-35` platform
and `build-tools` that Gradle needs. It has no explicit JDK/Gradle setup (only a bare
`openjdk-17-jre-headless` for `sdkmanager`). Its `pull_request` path filter does **not** include
`android/**`, and it does not trigger on `push` to `agent/android-arm64-application-port`; both must
be extended when the application layer lands. No Android lifecycle/Vulkan/touch/audio runtime tests
exist, and cannot exist here (no device, no Vulkan). The project's testing decision
(`06-testing-strategy.md`) is that the desktop suite stays the gate and Android adds a small device
smoke set — a physical-device checklist, not CI.

## 7. SDL3 version and entry-point architecture

### 7.1 SDL3 version

- **SDL3 3.4.16**, pinned by commit `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c` in
  `Tools/vendor.sh:463`. The SDL3 tree is **fetched, not committed** (`Tools/vendor.sh` unpacks it
  into `Libraries/Source/SDL3`, which is absent until `vendor.sh` runs).
- The build keeps `SDL_VIDEO`, `SDL_GPU` and `SDL_DIALOG` on and forces `SDL_OPENGL`/`SDL_OPENGLES`
  off (`GeneralsMD/Code/CMakeLists.txt:895-913`). On Android the only SDL3 GPU backend is Vulkan
  (`SDL_GPU_VULKAN 1`), which CI asserts.

### 7.2 Entry point (the exact mechanism, verified in the pinned SDL3 tree)

The mechanism is the standard SDL3-on-Android model:

1. `SDLActivity.java` (in SDL3's `android-project/app/src/main/java/org/libsdl/app/`) loads a native
   shared library and calls an exported function:
   - `getMainSharedObject()` default returns **`"libmain.so"`** (`SDLActivity.java:268-274`).
   - `getMainFunction()` default returns **`"SDL_main"`** (`:283-285`).
   - it passes both to `nativeRunMain(library, function, arguments)` (`:255-260`).
2. `SDL_main.h` on Android defines `SDL_MAIN_EXPORTED`, which renames the app's `main` to `SDL_main`
   and emits no native `main`. `PosixMain.cpp:47` already includes `<SDL3/SDL_main.h>` first and its
   function is already `int main(int argc, char *argv[])` (`:471`), so **no source change** is needed
   — the build target alone produces `libmain.so`.
3. The target already builds that shared library: `GeneralsMD/Code/CMakeLists.txt:2288-2290`
   (`add_library(generals SHARED …)`, `OUTPUT_NAME main`) gated on `ZH_PLATFORM_ANDROID`, keeping the
   CMake target name `generals` so every `$<TARGET_FILE:generals>` reference is unchanged.

### 7.3 Duplicate entry-point risk

There is **no** duplicate entry-point risk. The desktop path builds `generals` as an executable from
`PosixMain.cpp`; the Android path builds the **same source** as `libmain.so`. The two are mutually
exclusive branches of one `if(ZH_PLATFORM_ANDROID)`, so an Android build cannot also emit a native
`main`, and a desktop build cannot emit `libmain.so`. SDL's `SDL_MAIN_EXPORTED` is what makes the
rename safe: on macOS/Linux the header leaves `main` an ordinary `main`.

### 7.4 `argv` contract

SDL3 passes the activity's arguments as `argv` (`SDL_android.c` `nativeRunMain`), so `PosixMain`'s
`-root`, `-win` etc. parsing still works; on Android they are normally empty and the install root
comes from the picker / `Registry.ini`.

## 8. Storage and filesystem compatibility findings

### 8.1 What is already Android-correct

- `findUserDataDirectory()` → `<app private storage>/Command and Conquer Generals Zero Hour Data/`
  (`EarlyOptions.h`, `USER_FOLDERS_ANDROID`), writable and app-private. `findDesktopDirectory` and
  `composeDesktopDirectory` answer nothing for the convention (Android has no Desktop).
- `establishAndroidStorage()` sets `HOME` to `SDL_GetAndroidInternalStoragePath()` before the engine
  starts (only when unset), so the existing XDG resolution names the user-data directory.
- `findRegistryFile()` → `<user data>/Registry.ini`; `PosixMain.cpp:340-345` writes `InstallPath`
  there. So the **persistence** half of the folder flow already works; only the **picker** is missing.
- `ExecutableDirectory.cpp` returns `""` on Android and `isExecutablePackaged()` returns TRUE, so
  logs/crash reports go to the app-private directory and the install-root chooser is reached.

### 8.2 The picker gap (the single largest functional gap)

`PosixMain.cpp:192-227` (`chooseFolderWithSdl`) calls
`SDL_ShowFileDialogWithProperties(SDL_FILEDIALOG_OPENFOLDER, …)`. In the pinned SDL3 3.4.16 the
Android dialog backend `src/dialog/android/SDL_androiddialog.c` returns **`SDL_Unsupported()`** for
`SDL_FILEDIALOG_OPENFOLDER` (verified in the fetched source: the `OPENFOLDER` case calls
`SDL_Unsupported()` and invokes the callback with a negative error count). It implements only
open/save **file** dialogs, and those return a `content://` URI, which the engine's POSIX
`fopen`/`chdir`/`opendir` path cannot use.

`PosixMain.cpp:307` sets `request.chooser` only when not unattended and not Steam Game Mode, and
`isExecutablePackaged()` is TRUE on Android, so the Android flow is `-root` → `Registry.ini` → known
places → **chooser (which fails)**. A launch without `-root` therefore cannot select a game folder
today. This is not a storage-layer defect; it is a missing Android picker at the existing
`PosixInstallChooser` seam.

### 8.3 SAF versus real paths

SAF (`ACTION_OPEN_DOCUMENT_TREE`, `content://`) is a Java API and yields a URI the engine's C++ POSIX
syscalls cannot open directly; routing the whole file layer through SAF would mean a JNI round-trip
per open. `08-game-data-delivery.md` E3 records this. The player-picked folder must therefore be a
real, directly openable directory path, and the game data (the `.big` archives) must never be copied
into app storage — the engine `chdir`s into the picked folder and reads it read-only.

### 8.4 The multicast-lock permission is not declared yet

`SdlAndroidPlatform.cpp:147-157` states in its own comment that the Wi-Fi multicast lock needs
`android.permission.CHANGE_WIFI_MULTICAST_STATE` in the application manifest, which the repository
does not carry. Where the permission is absent, `createMulticastLock` throws and the code logs it and
continues (the LAN lobby still works on networks that do not drop the packets). This permission is
justified by existing native functionality and should be declared with the manifest.

### 8.5 Font discovery is a real gap on Android (does not block build/packaging)

`Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp:40` uses fontconfig only under `ZH_DESKTOP_LINUX`
(the guard is now `ZH_DESKTOP_LINUX || defined(ZH_GLYPHS_USE_FONTCONFIG)`, so Android takes the
no-fontconfig branch). But the no-fontconfig branch points at fixed macOS font paths that do not
exist on Android, so without a substitute the game renders no text. `audit/05-dependency-compatibility.md`
Class 4 records this. It is needed for the game to be *usable*, not to build or package an APK.

### 8.6 Read-only root

`PosixMain.cpp` `chdir`s into the picked root and calls `PosixPath_Set_Root_Read_Only(true)` (unless
`-writableRoot`), so the `GameEngine.cpp` deletion of `Data\INI\INIZH.big` is a no-op, exactly as on a
desktop read-only root. The install root stays read-only; writes go to the user-data directory.

## 9. Dependency and integration map

### 9.1 Runtime library set (what the APK must carry)

```
libmain.so (the engine, built from PosixMain.cpp as `generals` SHARED, OUTPUT_NAME main)
   DT_NEEDED:  libm  libdl  liblog  libandroid  libc++_shared  libc     (Android system only)
   APK must supply:  libc++_shared.so  (from the NDK sysroot, ANDROID_STL=c++_shared)
   APK may supply:   libSDL3.so only if the SDL3 build is shared — it is static here
                     (SDL_STATIC ON, CMakeLists.txt:907-909), so SDL3 is linked into libmain.so.
```

No SDL3 shared object, no GameSpy/FFmpeg shared object is `DT_NEEDED`; those are static archives
linked into `libmain.so`. This is why the APK's native payload is small.

### 9.2 Build-time dependency classification (from `audit/05` and `architecture/11`, re-checked)

| Dependency | Android class | Note |
|:--|:--|:--|
| SDL3 3.4.16 (+ GPU Vulkan) | 1 (ready) | NDK toolchain selects the Android video driver and Vulkan backend |
| Vulkan | 2 (config only) | NDK sysroot provides `libvulkan` + headers for API 28 |
| miniaudio 0.11.25 | 1 | AAudio (8+) / OpenSL ES backends |
| FreeType 2.14.3 | 1 | builds under the NDK |
| glslang / SPIRV-Cross / SDL_shadercross | 2 | pure C++, must compile under the NDK (Phase-2 confirmation) |
| mbedTLS 3.6.7 | 1 | portable C |
| zlib 1.1.4 / LZH-Light 1.0 | 1 | portable C/C++ |
| GameSpy SDK | 3 → done | `pthread_cancel` patched via `gamespy-android-thread.patch` |
| FFmpeg 8.1.2 | 3 → done | NDK cross-compile wired (`CMakeLists.txt:1886-1905`) |
| fontconfig | 4 (replacement) | desktop-only; Android needs a font substitute (§8.5) |
| X11 / Wayland / xkbcommon | 5 (gated out) | Android uses SDL's Android video driver |
| DirectX 8/9, DXVK | 5 (not used) | the renderer is SDL3 GPU |
| CURL / GameNetworkingSockets / OpenSSL | 5 (Windows-only) | gated `ZH_PLATFORM_WINDOWS` |

### 9.3 Integration map (component → component)

```
                        ┌──────────────────────────────────────────────┐
  Gradle project  ──────┤ android/settings.gradle, build.gradle,        │  (MISSING)
  (app module)          │ android/app/build.gradle                      │
                        └───────────────┬──────────────────────────────┘
                                        │ compiles + packages
                        ┌───────────────▼──────────────────────────────┐
  AndroidManifest.xml ──┤ package id, arm64-v8a, minSdk 28, Vulkan       │  (MISSING)
                        │ (not required), configChanges, MULTICAST perm  │
                        └───────────────┬──────────────────────────────┘
                                        │ declares
                        ┌───────────────▼──────────────────────────────┐
  Java Activity   ──────┤ extends org.libsdl.app.SDLActivity            │  (MISSING)
                        │   getLibraries() → {"SDL3","main"}            │
                        │   setOrientationBis → landscape               │
                        └───────────────┬──────────────────────────────┘
                                        │ extends / loads
                        ┌───────────────▼──────────────────────────────┐
  SDL3 Java glue  ──────┤ org.libsdl.app.* (staged from SDL3 3.4.16)     │  (MISSING, staged)
                        │   nativeRunMain("libmain.so","SDL_main",argv)  │
                        └───────────────┬──────────────────────────────┘
                                        │ dlsym
                        ┌───────────────▼──────────────────────────────┐
  libmain.so      ──────┤ `generals` SHARED, OUTPUT_NAME main           │  (EXISTS)
                        │   PosixMain.cpp main() == SDL_main            │
                        └───┬───────────┬───────────┬───────────┬──────┘
                            │           │           │           │
                  ┌─────────▼──┐ ┌──────▼─────┐ ┌───▼──────┐ ┌──▼─────────────┐
  SDL3 native ────┤ window/    │ │ SDL3 GPU   │ │ miniaudio│ │ SdlTouch        │ (EXISTS)
                  │ events     │ │ Vulkan     │ │ AAudio/  │ │ (ZH_ANDROID)    │
                  └─────┬──────┘ │ (SPIR-V)   │ │ OpenSLES │ └──┬──────────────┘
                        │        └─────┬──────┘ └───┬──────┘    │
                        │              │            │           │
                  ┌─────▼──────────────▼────────────▼───────────▼──────┐
  Native deps ────┤ glslang, SPIRV-Cross, SDL_shadercross, FreeType,   │ (EXISTS/static)
                  │ GameSpy, FFmpeg, mbedTLS, zlib, LZH-Light           │
                  └─────────────────────────────────────────────────────┘

  Game-folder picker ── PosixMain.cpp chooseFolderWithSdl (SDL OPENFOLDER — FAILS on Android) (INCOMPLETE)
        │  writes Registry.ini InstallPath  (EXISTS, persistence OK)
        ▼
  Filesystem access ─── PosixLocalFileSystem / PosixLocalFile / Win32BIGFileSystem (EXISTS)
        │  chdir(root); PosixPath_Set_Root_Read_Only(true)
        ▼
  Writes ────────────── findUserDataDirectory → app-private storage (EXISTS)

  CI ────────────────── ci-android-test.yml: native configure+compile+link+assert (EXISTS)
        └ needs extension: JDK+Gradle, android-35 platform/build-tools, APK job, path filters (MISSING)
```

## 10. Required implementation stages

The stages are ordered so each depends only on stages before it. This is a plan, not work done in
Phase 1.

- **Stage 1 — Application shell.** Gradle project, manifest, thin SDLActivity subclass, resources,
  SDL3 Java glue staged by a script, `libc++_shared.so` staged. Declare
  `CHANGE_WIFI_MULTICAST_STATE`; declare no storage permissions.
- **Stage 2 — Folder picker.** An Android folder chooser that yields a real filesystem path, plugged
  into the existing `PosixInstallChooser` seam so `PosixCheckInstallFolder` and the `Registry.ini`
  write are reused unchanged. Do not adopt the reference's `MANAGE_EXTERNAL_STORAGE`.
- **Stage 3 — Packaging.** `scripts/build/android/package-android.sh` staging `libmain.so` and
  `libc++_shared.so` into `jniLibs/arm64-v8a`, then `gradle assembleDebug`; assert ELF arch,
  `DT_NEEDED`, APK ZIP structure and manifest metadata.
- **Stage 4 — Font substitute + runtime verification.** Provide Android font discovery for
  `glyphrasteriser.cpp`; verify lifecycle/Vulkan/touch/audio on a physical device against a
  checklist (CI cannot do this — no device, no Vulkan).
- **Stage 5 — CI APK pipeline.** Extend `ci-android-test.yml` (or add a workflow) with a JDK,
  Gradle, platform `android-35`/`build-tools`, reuse the native job's `libmain.so`, package, validate,
  upload. Extend triggers/path filters to `android/**` and this branch.
- **Stage 6 — Final status.** Report the true completion level: a green native build or an APK does
  **not** prove the game runs on a device.

## 11. Dependencies between stages

| Stage | Depends on | Why |
|:--|:--|:--|
| 1 (shell) | — | needs only the existing `libmain.so` target and the SDL3 glue |
| 2 (picker) | 1 | the picker is reached only after the activity starts the game |
| 3 (packaging) | 1 | packages the shell's `jniLibs`/Java staging directories |
| 4 (fonts + runtime) | 1, 3 | needs a packaged APK to install and run |
| 5 (CI APK) | 1, 3 | the CI job runs the same packaging path |
| 6 (status) | 1–5 | reports on all of them |

Stage 2 and Stage 3 are independent of each other and may be done in either order once Stage 1 is
green. Stage 4's font work can start as soon as Stage 1 builds an APK; its device verification needs
Stage 3.

## 12. Technical risks

1. **No working Android folder picker** (§8.2). Highest functional risk: without it the game cannot
   find its data on a normal launch. Mitigation: a real-path picker at the existing chooser seam; do
   not weaken storage to `MANAGE_EXTERNAL_STORAGE`.
2. **No font substitute** (§8.5). The game renders no text until it is added.
3. **Vulkan-only backend** (`07-gpu-backend-decision.md`). A device without usable Vulkan fails
   loudly; the four optional-feature fallback is already implemented but has never been measured on a
   real device.
4. **BC (S3TC) sampling is not universal on mobile GPUs** — handled by the capability query
   (`SdlResourceMirror` expands to B8G8R8A8), so it is a bandwidth/memory cost, not a wrong image.
5. **No device/emulator in CI.** Correctness on real hardware (lifecycle, touch, audio, Vulkan) is
   unverifiable in CI; a green run proves compilation and linking only.
6. **`BuildFingerprint.manifest` regeneration.** Adding any file under `GeneralsMD/Code/` requires
   regenerating it (`Tools/fingerprint-manifest.sh`) or the `build_fingerprint_manifest` ctest fails.
   Files under `android/` are outside `GeneralsMD/Code/` and do not affect it.
7. **CI SDK level.** Gradle needs platform `android-35` and `build-tools`; the current workflow
   installs only `android-28`. Stage 5 must install them.
8. **`libc++_shared.so` provenance.** It must come from the same NDK the engine was built with, or
   the loader rejects the mismatch.

## 13. Verification criteria for every stage

Each criterion is objectively checkable; "device" criteria are a physical-device checklist because CI
has no GPU/device.

- **Stage 1**
  - `gradle :app:assembleDebug` produces an APK (with a placeholder or the real `libmain.so`).
  - The merged manifest declares `minSdk 28`, `abiFilters arm64-v8a`, the landscape activity, the SDL
    `configChanges` set, and `CHANGE_WIFI_MULTICAST_STATE`; it declares **no** `MANAGE_EXTERNAL_STORAGE`
    and no `READ/WRITE_EXTERNAL_STORAGE`.
  - The staged `java-sdl/org/libsdl/app/` matches the linked SDL3 3.4.16.
- **Stage 2**
  - The picker returns a real absolute path; `PosixCheckInstallFolder` accepts a valid Zero Hour
    folder (has `INIZH.big` + base `Textures.big`) and rejects an invalid one.
  - Path resolution is tested for: valid, missing files, invalid folder, inaccessible folder,
    reopened, revoked permission.
  - `Registry.ini` `InstallPath` is written once and honoured on the next launch.
- **Stage 3**
  - `libmain.so` in `jniLibs/arm64-v8a` is ELF64/AArch64/DYN and exports `SDL_main`.
  - `libc++_shared.so` is staged from the same NDK.
  - Every `DT_NEEDED` of `libmain.so` resolves either from the APK or from the Android system; the
    packaging script fails loudly on any missing library.
  - The APK ZIP contains `lib/arm64-v8a/libmain.so` and `lib/arm64-v8a/libc++_shared.so`; the manifest
    inside the APK matches Stage 1.
- **Stage 4**
  - Text renders on device (the font substitute works).
  - On device: the game starts, creates a Vulkan surface, draws a frame, accepts touch, and
    pauses/resumes without crashing.
- **Stage 5**
  - The CI job runs the native build **and** the packaging, and uploads the APK.
  - The job fails on a missing library, a wrong arch, or a manifest mismatch.
- **Stage 6**
  - A written status that distinguishes "builds/packages" from "runs on a device".

## 14. Exact files expected to be modified in subsequent stages

New files (Stages 1–3):

- `android/settings.gradle`, `android/build.gradle`, `android/gradle.properties`
- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/<pkg>/<Name>Activity.java`
- `android/app/src/main/res/values/strings.xml`, `themes.xml`, and icon resources
- `android/app/debug.keystore` (a committed debug-only key so CI builds install as updates)
- `android/app/src/main/java-sdl/**` and `android/app/src/main/jniLibs/**` — **staged, gitignored**
- `scripts/build/android/package-android.sh`
- Stage 2 picker: either an addition to `PosixMain.cpp`/`PosixInstallRoot` (native) or a Java
  activity that writes the same `Registry.ini` marker.

Existing files that may need a targeted edit:

- `GeneralsMD/Code/Main/PosixMain.cpp` — only if the Android picker needs a new chooser arm.
- `GeneralsMD/Code/BuildFingerprint.manifest` — **must be regenerated** after adding any file under
  `GeneralsMD/Code/`.
- `.github/workflows/ci-android-test.yml` — extend for Stage 5.
- `.gitignore` — add `android/app/src/main/jniLibs/`, `android/app/src/main/java-sdl/`,
  `android/app/build/`, `android/.gradle/`, `android/local.properties`.
- `Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp` — the Android font branch (Stage 4).

## 15. Components that must never be recreated

These exist and work; the phase rules forbid replacing or recreating them:

- The native engine, device layer and libraries under `GeneralsMD/Code/`.
- The **SDL3 GPU / Vulkan renderer** (`PosixDevice/Render/`, `SdlGpuDeviceFeatures`). No DXVK, no
  GLES renderer, no second renderer.
- The **native touch input** (`SdlTouch.{h,cpp}`) and the desktop mouse/keyboard path.
- The **miniaudio** audio implementation (`miles_miniaudio.cpp`).
- The **Android platform services** (`SdlAndroidPlatform.{h,cpp}`).
- The build scaffolding: `scripts/build/android/{android.toolchain.cmake,build-android.sh}`,
  `cmake/DetectPlatform.cmake`, `cmake/DetectArchitecture.cmake`, `PlatformPredicates.h`.
- The `libmain.so` target and the `SDL_main` entry (`CMakeLists.txt:2282-2290`, `PosixMain.cpp`).
- The GameSpy `pthread_cancel` fix and the FFmpeg NDK cross-build.
- The existing workflows (`ci-android-test.yml` in its native scope, `linux-release.yml`,
  `commit-messages.yml`) — extend, do not replace.
- The storage layer (`PosixLocalFileSystem`, `PosixLocalFile`, `PosixInstallRoot`) and the
  `Registry.ini` marker — reuse, do not rewrite.

## 16. Verified facts versus assumptions and unresolved questions

**Verified (read from the working tree or the pinned SDL3 source):** the audited commit SHA; the
absence of `android/`, `CMakePresets.json`, `vcpkg.json`, `jniLibs/`, `apk/` and any APK; the
`libmain.so` CMake target; the `SDL_main` contract and SDLActivity's defaults (`libmain.so`,
`SDL_main`); `SDL_Unsupported()` for `SDL_FILEDIALOG_OPENFOLDER` in the pinned Android dialog backend;
SDL3 3.4.16 pinned at `fa2c02bb…`; the CI steps, triggers and artifact; no GitHub Releases; the
Android-correct user-data/registry/executable-path handling; the GameSpy patch; the `_Unwind_Backtrace`
crash unwinder; the multicast-lock permission gap; the fontconfig guard.

**Assumptions (not measured on hardware):** that the SDL3-GPU Vulkan path runs on a real mobile GPU
(the four-feature fallback exists but is untested on device); that `SDLActivity` will load
`libmain.so` cleanly once packaged; that miniaudio's AAudio/OpenSL backends behave on the target
device; that the reference's `SetupActivity`/`FolderPickerActivity` *patterns* (not code) suit this
repository's picker.

**Unresolved questions:** the exact package id and app name to use; whether the picker is a native
`PosixInstallChooser` arm or a Java activity writing the `Registry.ini` marker; the Android font
substitute (bundle TrueType in assets, or query the platform); whether any cheap, platform-neutral
subset of the 131 tests should also build under the NDK; and the Android smoke-set host (emulator
versus physical device).

## 17. Recommended implementation order

1. **Stage 1 — application shell.** Gradle project, manifest, thin SDLActivity subclass, resources,
   SDL Java glue staged by a script, `libc++_shared.so` staged. Validate with `gradle assembleDebug`.
2. **Stage 2 — folder picker.** A minimal Android folder chooser that yields a real path, wired into
   the existing `PosixInstallChooser` seam; reuse `PosixCheckInstallFolder` and the `Registry.ini`
   write.
3. **Stage 3 — packaging.** `scripts/build/android/package-android.sh` staging `libmain.so` and
   `libc++_shared.so`; assert ELF arch, `DT_NEEDED`, APK ZIP structure, manifest metadata.
4. **Stage 4 — fonts and runtime verification.** Font substitute plus a physical-device acceptance
   checklist.
5. **Stage 5 — CI APK pipeline.** Extend the workflow to package, validate and upload the APK.
6. **Stage 6 — final status.** Report the true completion level.

## 18. Provenance

- Target repository read at `e3a00c4` (branch `android-arm64-port`); development branch
  `agent/android-arm64-application-port` (parent revision `8855b7c`).
- SDL3 facts read from the pinned upstream source (`libsdl-org/SDL` @
  `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c`): `src/dialog/android/SDL_androiddialog.c`,
  `android-project/app/src/main/java/org/libsdl/app/SDLActivity.java`, `docs/README-android.md`.
- Reference-port facts read at `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1` (design guidance
  only, never authoritative): `android/**`, `scripts/build/android/package-android-zh.sh`,
  `.github/workflows/build-android.yml`, `docs/port/ANDROID_PORT.md`.
- Read-only: no source, build, workflow or dependency file was changed by this audit. Only this
  document was written.
