# Android ARM64 port — implementation continuation status (Phase 0 audit)

Read-only audit of the **target** branch `android-arm64-port` in
`redaf12-Decode/Generals-Reforged`, to answer one question: what is already implemented, what is
incomplete, and what is missing before this repository can ship a runnable Android application.

This document does not replace the existing design records — it indexes them and states what is
still to be built. Nothing was changed to write it.

- **Audited commit:** `e3a00c4eebd0e5c0f47ce3ee1c80081b16fc781a`
  (`feat(android): audio, storage and game services for ARM64`, 2026-11-17T05:39:48Z).
- **Audited branch:** `android-arm64-port` (base branch). Development happens on
  `agent/android-arm64-application-port`.
- **Last green CI on the audited commit:** `CI Android Test` run `44025684120`, success, 23m23s.
- **Reference repository (design guidance only, never authoritative):**
  `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`. Its tree is *not* this repository's; its
  engine, renderer and dependency set must not be copied (rules 5, 8 of the phase plan).

---

## 1. What is already implemented (do not recreate)

The native engine half of the port is complete and CI-green. Everything below exists at the audited
commit and is protected by rule 6 (preserve the target's SDL3 / SDL3 GPU / Vulkan / miniaudio /
SdlTouch / Android platform implementations) and rule "existing implementation protection".

| Component | Path | State |
|:--|:--|:--|
| Android toolchain layer | `scripts/build/android/android.toolchain.cmake` | complete |
| Android build script | `scripts/build/android/build-android.sh` | complete |
| Platform detection (CMake) | `GeneralsMD/Code/cmake/DetectPlatform.cmake` | complete; Android is POSIX, never Linux |
| Architecture detection (CMake) | `GeneralsMD/Code/cmake/DetectArchitecture.cmake` | complete; ABI decides alone |
| C++ platform predicates | `GeneralsMD/Code/Libraries/Include/Platform/PlatformPredicates.h` | complete (`ZH_ANDROID`, `ZH_DESKTOP_LINUX`) |
| Engine as `libmain.so` | `GeneralsMD/Code/CMakeLists.txt:2282-2290` | complete; `add_library(generals SHARED …)`, `OUTPUT_NAME main` |
| Android entry point | `GeneralsMD/Code/Main/PosixMain.cpp` | complete; `main()` becomes `SDL_main` via `<SDL3/SDL_main.h>` |
| Android storage bootstrap | `PosixMain.cpp` `establishAndroidStorage()` (`:436-467`) | complete; sets `HOME` from `SDL_GetAndroidInternalStoragePath()` |
| User-data convention | `GameEngine/Include/Common/EarlyOptions.h` (`USER_FOLDERS_ANDROID`) | complete; `<HOME>/Command and Conquer Generals Zero Hour Data` |
| Executable-path handling | `GameEngine/Source/Common/System/ExecutableDirectory.cpp` | complete; Android branch returns `""`, `isExecutablePackaged()` → TRUE |
| Crash-handler unwinder | `Libraries/Include/Platform/StackWalk.h` (`zh_backtrace`) | complete; `_Unwind_Backtrace` + `dladdr` on Android |
| SDL3 GPU Vulkan renderer | `GameEngineDevice/Source/PosixDevice/Render/` + `SdlGpuDeviceFeatures` | complete; feature fallback, depth clip, cross-format present |
| Native touch input | `GameEngineDevice/Source/SdlDevice/GameClient/SdlTouch.{h,cpp}` | complete; Android half behind `ZH_ANDROID` |
| Android platform services | `GameEngineDevice/Source/SdlDevice/Common/SdlAndroidPlatform.{h,cpp}` | complete; lifecycle watch, screen-on, Wi-Fi multicast lock, JNI |
| miniaudio Android backends | `Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp` | complete; AAudio then OpenSL ES, `AIL_ex_device_pause/resume` |
| GameSpy bionic fix | `Libraries/Source/gamespy-android-thread.patch` | complete; `pthread_cancel` guarded |
| FFmpeg aarch64 cross-build | `Tools/ffmpeg-build-posix.sh` + `CMakeLists.txt:1886-1905` | complete; NDK wrapper handed to `configure` |
| Android CI (native only) | `.github/workflows/ci-android-test.yml` | complete for its scope: configure + compile + link + assert |

`libmain.so` at that commit is an ELF64/AArch64/DYN object exporting `SDL_main` with `NEEDED`
`libm.so, libdl.so, liblog.so, libandroid.so, libc++_shared.so, libc.so` — no desktop-Linux
library, no SDL3 shared library, no GameSpy/FFmpeg shared object. **`libc++_shared.so` is the only
library the APK must supply besides `libmain.so` itself.**

The design decisions this rests on are recorded, and should be read before changing anything:
`docs/android-port/architecture/07-gpu-backend-decision.md` (Vulkan only),
`08-game-data-delivery.md` (user-picked folder, no APK assets),
`09-ndk-api-and-backtrace.md`, `10-android-entry-point.md`, `11-dependency-inventory.md`,
`12-platform-macro-audit.md`, `13-android-vulkan-rendering.md`, `14-native-touch-input.md`,
`15-audio-storage-and-game-services.md`, `FINAL-PLATFORM-DECISIONS.md`.

---

## 2. What is missing (the application layer)

**There is no Android application.** Verified: no `android/` directory anywhere in the tree
(`ls android` → "no android dir"); no `settings.gradle`, no `build.gradle`, no
`AndroidManifest.xml`, no Java source, no `gradle` wrapper, no APK packaging script, no `.apk`
anywhere in git.

Consequently the following are all missing and are what phases 1–6 build:

1. **Gradle project** — `android/settings.gradle`, `android/build.gradle`,
   `android/gradle.properties`, `android/app/build.gradle`.
2. **Manifest** — `android/app/src/main/AndroidManifest.xml` with a stable package id, arm64-v8a,
   `minSdk 28`, Vulkan declared, and only justified permissions.
3. **Java shell** — a thin `Activity extends org.libsdl.app.SDLActivity` naming the libraries to
   load (`SDL3`, `main`).
4. **SDL3 Java glue** — `org.libsdl.app.*` from the vendored SDL3 3.4.16 `android-project`
   (staged, not copied by hand; see §5).
5. **Resources** — app name ("Generals Reforged"), icon, theme, strings.
6. **Game-folder selection on Android** — the existing `chooseInstallRoot()` path calls
   `SDL_ShowFileDialogWithProperties(SDL_FILEDIALOG_OPENFOLDER, …)`, which **SDL3 3.4.16 does not
   implement on Android** (see §3.1). A working picker is a genuine gap.
7. **`libc++_shared.so` staging** and APK packaging — no script exists.
8. **CI APK job** — the existing workflow stops at the native link and never packages an APK.

---

## 3. Incomplete integrations (audited, with evidence)

### 3.1 Folder selection cannot work as written on Android

`PosixMain.cpp:192-227` (`chooseFolderWithSdl`) uses
`SDL_ShowFileDialogWithProperties(SDL_FILEDIALOG_OPENFOLDER, …)`. In SDL3 3.4.16 the Android dialog
backend (`src/dialog/android/SDL_androiddialog.c`) returns `SDL_Unsupported()` for
`SDL_FILEDIALOG_OPENFOLDER` and only implements open/save **file** dialogs via
`ACTION_OPEN_DOCUMENT`. Even those return a `content://` URI string
(`SDLActivity.onActivityResult`, `singleFileUri.toString()`), which the engine's POSIX
`fopen`/`chdir`/`opendir` path (`PosixLocalFileSystem`, `Win32BIGFileSystem`) cannot use.

`PosixMain.cpp:307` only sets `request.chooser` when *not* unattended and *not* Steam Game Mode, and
`isExecutablePackaged()` returns TRUE on Android, so on Android the flow is: `-root` → `Registry.ini`
→ known places → **chooser (SDL folder dialog, which fails)**. So today an Android launch without
`-root` cannot select a game folder. This is the single largest functional gap.

**Decision for phase 2 (see §4):** an Android-specific folder chooser that yields a real filesystem
path, plugged into the *existing* `PosixInstallChooser` seam, so `PosixInstallRoot`'s validation and
`Registry.ini` write are reused unchanged. Do not rewrite the storage layer (rule: "do not replace
the target's existing storage implementation without proving it is insufficient"). Do not adopt the
reference port's `MANAGE_EXTERNAL_STORAGE` "All files access" (doc 08 explains why; it is a
Play-policy-restricted permission the target deliberately avoids).

### 3.2 `Registry.ini` and the user-data directory are already Android-correct

`findRegistryFile()` resolves to `<user data>/Registry.ini` and `findUserDataDirectory()` to
`<app private storage>/Command and Conquer Generals Zero Hour Data/`, which is writable and
app-private. So the *persistence* half of the folder flow already works; only the *picker* is
missing. Confirmed by `PosixMain.cpp:340-345`, which writes `InstallPath` there.

### 3.3 Font discovery is a real gap on Android

`Libraries/Source/WWVegas/WW3D2/glyphrasteriser.cpp` finds fonts through fontconfig on desktop
Linux (`ZH_GLYPHS_FONTCONFIG`) and through fixed `/System/Library/Fonts/Supplemental/` paths on
macOS. On Android neither branch applies: the `#else` branch points at a macOS path that does not
exist. Without a substitute the game renders no text. `docs/android-port/audit/05-dependency-compatibility.md`
Class 4 records this. It is not needed to *build or package* an APK, but it is needed for the game
to be usable; it belongs to phase 4/6 verification, not phase 1.

### 3.4 The multicast-lock permission is not yet declared

`SdlAndroidPlatform.cpp:141-152` says so in its own comment: the code needs
`android.permission.CHANGE_WIFI_MULTICAST_STATE` in the manifest, which this repository does not
carry yet. That permission is justified by existing native functionality (the LAN lobby's broadcast
listener) and should be declared in phase 1.

### 3.5 Other declared Android items already done

`-funwind-tables` (StackWalk), `cpudetect.cpp` non-x86 arm (compiles; x86 half is `CPUDETECT_X86`-gated),
FFmpeg NDK cross-compile, GameSpy `pthread_cancel`, glslang/SPIRV-Cross/SDL_shadercross — all
complete at the audited commit. Not repeated here.

---

## 4. What the reference contributes, and what it does not

The reference `android/` project is a **large launcher application** (30+ Java files: `SetupActivity`
with Material Components, `GeneralsOnlineActivity`, `ReplayCheckActivity`, `NetworkDiagnosticsActivity`,
`LogViewerActivity`, `UpdateManager`, `DataPackInstaller`, `LocaleHelper`, multi-language `strings.xml`,
custom icons, Turnip/ANGLE/DXVK staging). Almost none of that is applicable to Generals Reforged,
which has no online client, no DXVK, no update mechanism, and must not adopt them (rule 8).

Applicable patterns, and only these:

| Reference file | What to take | What to leave |
|:--|:--|:--|
| `android/settings.gradle`, `build.gradle`, `gradle.properties` | AGP-based single-module project shape; `google()`/`mavenCentral()` | — |
| `android/app/build.gradle` | `namespace`/`applicationId`, `minSdk 28`, `abiFilters 'arm64-v8a'`, `jniLibs` + `java-sdl` staging `sourceSets`, a committed debug keystore so CI builds install as updates | Material dependency, versionCode scheme, `useLegacyPackaging`, DXVK `keepDebugSymbols`, ANGLE/Turnip assets |
| `AndroidManifest.xml` | thin `Activity` in landscape with the SDL `configChanges` set; `android:isGame`/`appCategory`; `vulkan` `uses-feature` **not required** | `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE`, `requestLegacyExternalStorage`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET` (unless justified), the second launcher icon, FileProvider |
| `GeneralsZHActivity.java` | the `extends SDLActivity` + `getLibraries()` shape; the `setOrientationBis` override pinning landscape | asset extraction, `UpdateManager`, engine override, safe-insets args, gesture-nav workarounds |
| `FolderPickerActivity.java` | the *idea* of a plain `java.io.File` browser that returns a real path | `MANAGE_EXTERNAL_STORAGE`; port as a minimal picker or use SDL/SAF correctly |
| `package-android-zh.sh` | stage `libmain.so` + `libc++_shared.so` into `jniLibs/arm64-v8a`; stage SDL Java glue from the vendored tree; `gradle assembleDebug`; fail loudly on a missing library | DXVK, openal, adrenotools, ANGLE, VVL, Turnip, dual-Hz engines, fonts assets |
| `.github/workflows/build-android.yml` | JDK setup, Gradle setup, sdkmanager install of platform/build-tools, "verify every `DT_NEEDED` of `libmain.so` is packaged" | vcpkg, DXVK submodule, meson, the whole vcpkg cache machinery |

The reference's `SetupActivity.isValidGameFolder()` checks `INIZH.big` **and** `INI.big`. This
repository's `PosixCheckInstallFolder` checks `INIZH.big` and the base game's `Textures.big`. **Use
the target's own validation** (`PosixCheckInstallFolder`) — it is the existing, tested one.

---

## 5. CI capabilities and limitations (audited)

`CI Android Test` (`.github/workflows/ci-android-test.yml`) at the audited commit:

- Triggers: `workflow_dispatch`, `push` to `android-arm64-port`, `pull_request` on a path filter.
  It does **not** currently trigger on `push` to `agent/android-arm64-application-port`, and its
  `pull_request` path filter does not include `android/**`. Phase 5 must extend both.
- Installs a pinned, checksummed cmdline-tools, NDK `27.2.12479018`, platform `android-28`.
- Runs the two `cmake -P` detection matrices; asserts Android is never Linux and arm64-v8a is AArch64.
- Configures the ARM64 tree, asserts toolchain/ABI/API/STL, asserts desktop-Linux libraries are
  absent, asserts `SDL_GPU_VULKAN` is on.
- Compiles a bounded target set, then **links the whole engine as `libmain.so`** and asserts it is
  AArch64/DYN, exports `SDL_main`, links only Android system libs plus `libc++_shared.so`.
- Asserts the Phase-4 (touch) and Phase-5 (audio/storage/platform) objects define the Android-only
  symbols, so a desktop stub cannot satisfy them.
- Uploads configure/build logs as `android-arm64-diagnostics`.

**Limitations:** it never packages an APK, never runs Gradle, never validates a manifest, never runs
on a device or emulator, and installs only platform `android-28` (not the `android-35` platform and
`build-tools` that Gradle needs). It also has no `java`-independent Gradle path: the runner has a
JDK, but the workflow does not set one up explicitly.

**No Android lifecycle/Vulkan/touch/audio runtime tests exist**, and cannot exist in this CI (no
device, no Vulkan). The project's testing decision
(`docs/android-port/architecture/06-testing-strategy.md`) is that the desktop suite stays the gate
and Android adds a small device smoke set — which is a physical-device acceptance checklist, not CI.

---

## 6. Exact files that require changes

New files (phase 1–3):

- `android/settings.gradle`, `android/build.gradle`, `android/gradle.properties`
- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/<pkg>/<Name>Activity.java`
- `android/app/src/main/res/values/strings.xml`, `themes.xml`
- `android/app/src/main/res/mipmap-*/ic_launcher.png` (or an adaptive icon)
- `android/app/debug.keystore` (a committed debug-only key, as the reference does)
- `android/app/src/main/java-sdl/**` and `android/app/src/main/jniLibs/**` — **staged, gitignored**
- `scripts/build/android/package-android.sh`
- `.github/workflows/ci-android-test.yml` (extend; see phase 5)
- Phase 2 picker: either an addition to `PosixMain.cpp`/`PosixInstallRoot` or a Java activity that
  writes the same `Registry.ini` marker.

Existing files that may need a targeted edit:

- `GeneralsMD/Code/Main/PosixMain.cpp` — only if the Android picker needs a new chooser arm.
- `GeneralsMD/Code/BuildFingerprint.manifest` — **must be regenerated** after adding any file under
  `GeneralsMD/Code/` (ctest `build_fingerprint_manifest`). Files added under `android/` are outside
  `GeneralsMD/Code/`, so they do not affect it; a new native file does.
- `.gitignore` — add `android/app/src/main/jniLibs/`, `android/app/src/main/java-sdl/`,
  `android/app/build/`, `android/.gradle/`, `android/local.properties`.

---

## 7. Recommended implementation order

1. **Phase 1 — application shell.** Gradle project, manifest, thin SDLActivity subclass, resources,
   SDL Java glue staged by a script, `libc++_shared.so` staged. Validate: `gradle :app:assembleDebug`
   produces an APK (with a placeholder or real `libmain.so`). Declare
   `CHANGE_WIFI_MULTICAST_STATE`; do not declare storage permissions.
2. **Phase 2 — folder picker.** A minimal Android folder chooser that yields a real path, wired into
   the existing `PosixInstallChooser` seam so `PosixCheckInstallFolder` and `Registry.ini` are
   reused. Test path resolution (valid / missing files / invalid / inaccessible / reopened / revoked).
3. **Phase 3 — packaging.** `scripts/build/android/package-android.sh` staging `libmain.so` and
   `libc++_shared.so`; assert ELF arch, `DT_NEEDED`, APK ZIP structure, manifest metadata.
4. **Phase 4 — lifecycle/Vulkan/touch/audio verification.** Mostly assertions plus a physical-device
   acceptance checklist; fix the font-discovery gap if the game must render text.
5. **Phase 5 — CI APK pipeline.** Extend `ci-android-test.yml` to add a JDK, Gradle, platform
   `android-35`/`build-tools`, reuse the native job's `libmain.so`, package, validate, upload.
6. **Phase 6 — final status.** Report the true completion level; a green native build or an APK does
   not prove the game runs.

## Provenance

- Target repository read at `e3a00c4` (branch `android-arm64-port`).
- SDL3 facts read from the vendored SDL3 3.4.16 source at
  `Libraries/Source/SDL3` (upstream commit `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c`).
- Reference-port facts read at `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1` (design guidance
  only).
- Read-only: no source, build, workflow or dependency file was changed by this audit.
