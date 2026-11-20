# Generals-Reforged — CMake port of Zero Hour (this repo: redaf12-Decode/Generals-Reforged)

NOTE: everything below this section describes a **different** project (the GeneralsZH-Android-Port
launcher: `android/`, `UpdateChecker`, `ApkInstaller`, gradle).  None of those paths exist in this
repository; treat that part as reference-only when working here.

- **What this repo is.** A CMake + SDL3 port of C&C Generals Zero Hour ("Reforged").  The build is
  entered from `GeneralsMD/Code/CMakeLists.txt` (`project(GeneralsMD CXX C)`; there is **no root
  CMakeLists.txt**).  `Generals/Code/CMakeLists.txt` is the older sibling tree.
- **The design/decision source is `PORTING.md`** (root, ~526 lines).  Its "decision N" / work-item
  labels (A/B/C/D/E/L/N/P/R/T/V/W/X/M) are referenced throughout the code comments.  Read it before
  touching renderer or platform code.
- **Platform detection lives in `GeneralsMD/Code/cmake/DetectPlatform.cmake`** — one function,
  `zh_detect_platform(system_name android_flag OUT...)`, called from `CMakeLists.txt:34-36`.  Flags:
  `ZH_PLATFORM_WINDOWS`, `ZH_PLATFORM_POSIX` (umbrella = "not Windows"), `ZH_PLATFORM_MACOS`,
  `ZH_PLATFORM_LINUX`, and now `ZH_PLATFORM_ANDROID`.  Android sets POSIX and **never** LINUX (it
  would otherwise pull in fontconfig, X11/Wayland/libdrm and Steam packaging).  Windows/Linux/macOS
  answers are unchanged.  Unit-tested by `Tests/test_platform_detection.cmake` under `cmake -P`
  (ctest `platform_detection_check`), so the matrix is checkable without a toolchain.
- **Two repo-wide gates that bite every new file:** (1) `BuildFingerprint.manifest` (under
  `GeneralsMD/Code/`) is generated from `git ls-files` by `Tools/fingerprint-manifest.sh`; regenerate
  it (no args) after adding/removing/renaming anything under `GeneralsMD/Code/`, or ctest
  `build_fingerprint_manifest` fails.  (2) `Tools/license-headers.py --check/--apply --base <commit>`
  adds the GPL notice new files must carry; it defaults to `merge-base HEAD upstream/main`, which does
  **not** exist in this clone, so pass `--base` explicitly.
- **Building locally.** `Tools/vendor.sh --no-art` fetches the gitignored third-party sources (SDL3,
  glslang, SPIRV-Cross, SDL_shadercross, freetype, FFmpeg, miniaudio, mbedtls, zlib, lzhl; needs
  network + `unzip`).  Then `cmake -S GeneralsMD/Code -B build-linux -G Ninja -DCMAKE_BUILD_TYPE=Release`.
  Build trees `build/`, `build-linux/`, `build-mac/`, `build-perf/` are gitignored.  A real Android
  configure needs the NDK toolchain file (`.../build/cmake/android.toolchain.cmake`); setting
  `-DCMAKE_SYSTEM_NAME=Android` alone fails at `project()`.
- **Test suite:** ~200 ctest entries; many are script tests (`Tests/*.sh`, `Tools/*-check.sh`) that
  skip with return code 77 without game data.  Lints worth running before committing:
  `workfolder_guard_check`, `include_case_check`, `unix_define_check`, `widechar_check`,
  `build_fingerprint_manifest`, `platform_detection_check`.
- **Audit reports** for the Android port live in `docs/android-port/audit/` (`01-target-architecture.md`,
  `02-reference-architecture.md`, `03-rendering-subsystem.md`).  The renderer is
  `GeneralsMD/Code/GameEngineDevice/Source/PosixDevice/Render/` (the `posixd3d9` static lib standing in
  for `d3d9.lib`), reaching `SdlGpuFrame`/`SdlPipelineCache`/`SdlProgramCache`/`SdlResourceMirrors`.
- **Platform decision docs** live in `docs/android-port/architecture/` (`05`…`12` plus
  `FINAL-PLATFORM-DECISIONS.md`); they resolve the Section-11 questions of the target audit
  (`docs/android-port/audit/01-target-architecture.md#11-unresolved-questions`). Key decision:
  **Vulkan is the only Android GPU backend** (SDL3 3.4.16 ships Metal/D3D12/Vulkan only — `SDL_gpu.c`
  backend table), so no GLES/DXVK; a device without Vulkan fails the existing chain
  `SdlGpuFrame::Create` (NULL) → `PosixDevice9::Create_Gpu_Frame` (`D3DERR_NOTAVAILABLE`) →
  `W3DDisplay::init` (`ERROR_INVALID_D3D`).
- **The `__linux__` trap (the single most important Android fact here):** NDK Clang **defines
  `__linux__`** for Android, so every Linux-only guard compiles on Android. There are **200 `__linux__`
  sites in 55 files** (engine + GameEngineDevice + WWVegas + `Libraries/Include`, excluding
  `GameNetwork/GeneralsOnline/json.hpp` and `Tests/`); many are wrong for Android (inotify in
  `posixpath.cpp`, `/proc/self/exe` in `ExecutableDirectory.cpp`, `getenv`-driven tuning in
  `Libraries/Include/Platform/*.h`, `ModernWaterBuild.h`). Re-guard with `__ANDROID__`, never treat
  `__linux__` as "desktop Linux". (Note `ZH_PLATFORM_ANDROID` in CMake is deliberately NOT
  `ZH_PLATFORM_LINUX`, but that is a build flag, not the preprocessor macro.)
- **The C++ twin of the CMake split is `Libraries/Include/Platform/PlatformPredicates.h`** (Task 2.1,
  branch `android-arm64-port`, commit `087180d`): `ZH_ANDROID` (`__ANDROID__`) and `ZH_DESKTOP_LINUX`
  (`__linux__ && !__ANDROID__`). It reaches every target via the directory-wide
  `include_directories()` at `CMakeLists.txt:123`. Applied so far: `PreRTS.h` guards `<sys/timeb.h>`
  with `!defined(__ANDROID__)` (bionic lacks it; cleared 13/15 first-party Android compile failures),
  `ExecutableDirectory.cpp` (Linux branch → `ZH_DESKTOP_LINUX`, new `ZH_ANDROID` branch returns `""`),
  `PosixMain.cpp` Flatpak app-id, `glyphrasteriser.cpp` fontconfig. `Tests/test_platform_predicates.cpp`
  pins them. **Verification recipe:** configure the Android tree to get `compile_commands.json`, replay
  each command with `-o`/`-c` stripped + `-fsyntax-only` via Python over the first-party files that
  contain `__linux__` — 15 failures → 2 (both `backtrace()`, doc 09's task). Report:
  `docs/android-port/audit/04-linux-android-conditional-classification.md`. The ~190 Class-B renderer
  gates are left as-is on purpose (narrowing them needs the renderer decision).

# Project memory — Generals-Android-Port

Index of durable facts learned about this repository. Detail lives in the daily logs.

> **Note (2026-11-16):** the workspace at that date was `redaf12-Decode/Generals-Reforged`
> (the Zero Hour *Reforged* macOS/Linux port), a different codebase from the
> `Generals-Android-Port` entries below. Its audit facts live in
> `2026-11-16.md` (target architecture, `#error` guards, renderer chain, no touch input).

## Repository topology (verified 2026-11-04)

- `redaf12-Decode/Generals-Android-Port` mirrors `MYSOREZ/GeneralsZH-Android-Port` branch
  `claude/network-diagnostics`. `mirror.yml` (on `main`, cron `17 */6 * * *`, secret
  `MIRROR_TOKEN`) pushes a history-free snapshot to **`mirror/claude-network-diagnostics`** only
  (`TARGET_REF="refs/heads/mirror/claude-network-diagnostics"`, `--force`) — verified 2026-11-04 by
  reading the workflow. **No workflow in this repo writes to `New-UI`.** `New-UI`'s head is a
  mirror-bot snapshot that was copied onto it by hand (author `mirror-bot@users.noreply.github.com`,
  subject "Mirror snapshot of claude/network-diagnostics"), and `commit-transfer.yml` (which can
  push to any branch) is `workflow_dispatch`-only, i.e. manual.
  An earlier note here claiming `mirror.yml` overwrites `New-UI` was **wrong**; commits on `New-UI`
  are safe to push. Check `git ls-remote origin` before pushing anyway.
- `main` holds a *different project shape* (its own five-stage/four-engine CI, no `android/` app
  in the same form). It cannot be fast-forwarded from `New-UI`; the documented release flow
  (push `main` → `Publish Android Release`) is unusable here as-is.
- 0 releases, 0 issues. `apk/` + `apk/testers/` = 680 MB of committed APKs; the current APK and
  `apk/testers/…-a48.apk` are byte-identical (md5 a1f65ae3…).
- Stale branch carrying real work: `claude/ui-redesign-dashboard-drawer` (2026-10-18) adds
  `ToolboxConfig/ToolboxIconPreviewView/ToolboxIconRenderer/ToolboxOverlayView.java`,
  `accent_colors.xml`, **and the Gradle wrapper that `New-UI` lacks**.

## Build facts that cost time to learn

- **Compile-checking the Java shell without Gradle/SDK** (no JDK, no Android SDK, no Gradle wrapper
  on `New-UI`): download `platform-35_r01.zip` from `dl.google.com/android/repository/` (65 MB,
  `android-35/android.jar` inside), plus `material-1.12.0.aar`, `androidx core-1.13.1`,
  `appcompat-1.7.0`, `cardview-1.0.0` from `maven.google.com` (extract `classes.jar` from each).
  `org.json` is inside `android.jar`. Two things must be stubbed because they are staged at build
  time and absent from the clone: `org.libsdl.app.SDLActivity` (stub needs `public void
  loadLibraries()`, `public void setOrientationBis(int,int,boolean,String)`, `protected static
  ViewGroup mLayout`, `protected static SurfaceView mSurface` — `Surface`, not `SurfaceView`, fails
  on `mSurface.requestFocus()`) and `com.generalsx.zerohour.R` (generate from the resource XMLs;
  normalise dotted style names `Theme.X` -> `Theme_X`). Then
  `javac -nowarn -proc:none -cp <all of the above> -d out app/src/main/java/com/generalsx/zerohour/*.java`
  gives a real 0-error build (50 classes). Compile the pre-change tree the same way as a baseline:
  if both are clean, a removal cannot be hiding a regression. This catches dangling `R.*` references
  that no grep would.
- The build is **two halves**: CMake preset `android-vulkan` (outside Gradle) produces the engines;
  Gradle only compiles a thin Java shell and packages staged `.so` files. Staged dirs
  `android/app/src/main/jniLibs/` and `java-sdl/` are gitignored and created by
  `scripts/build/android/package-android-zh.sh`.
- Four engines ship: `libmain[60].so` (Zero Hour) + `libgenerals[60].so` (base game). The tick rate
  is a compile-time constant (`WWSyncPerSecond` enum) that changes `GameLogic`'s class layout, so
  it can only be a different `.so`. Build both with `build-dual-hz.sh` — one pass per tick rate.
- `build-local-sandboxed.sh` needs `android-staging/` pre-populated (fonts, Turnip driver,
  validation layer) because it cannot reach GitHub release assets.
- CI (`build-android.yml`) builds **only** `z_generals` — one game, one tick rate. Its APK can never
  satisfy `publish-release.yml`, which requires both `libmain.so` and `libmain60.so`.
- `android/app/build.gradle` uses `assembleDebug` → shipped APKs are `debuggable` (verified in the
  committed APK manifest) and the dex is unshrunk.

## Code facts worth not rediscovering

- The launcher↔engine interface is **marker files**, not IPC. ~20 of them
  (`gamedata_path.txt`, `mods_active.txt`, `logging_off`, `mouse_mode`, `control_scheme`,
  `subtitles`, `render_backend.cfg`, `gx_*.txt`, `files/update/remote_config.ini`, …), each with a
  Java writer and a native reader (`Core/GameEngine/Include/Common/GX*.h`). No schema, so a
  one-sided rename is a silent no-op.
- The engine's Android user-data path is **per game** (`SDL3Main.cpp` `GX_USERDATA_LEAF`):
  `…/Generals/Command and Conquer Generals Data` vs `…Zero Hour Data`.
  `DataPackInstaller.userDataDir()` hardcodes the Zero Hour leaf, and `ReplayCheckActivity` +
  `LogViewerActivity` use it — so with the base game selected they read Zero Hour's folder.
- Update and language-pack endpoints all point at `MYSOREZ/GeneralsZH-Android-Port`, not this fork
  (`UpdateManager.RAW_ROOT`, `SetupActivity.LANGUAGE_PACK_INDEX/_BASE`, `update/config.json`).
- **Some launcher state lives on the `updates` branch, not in the repo** — settings, the language-pack
  index and (until 2026-11-04) the support card are read at runtime from `raw.githubusercontent.com`
  paths named by a signed `manifest.json` on that branch. Deleting a file from the working tree does
  **not** withdraw what it fed: the branch keeps serving it, and only a fresh publish
  (`scripts/update/publish-update.py` → `push-updates-branch.sh` → `Actions → Sign update`) changes
  what installed launchers see. The branch always holds a single commit, force-replaced. Relevant to
  any change that removes or renames a published payload.
- `update/support.json`, the support card renderer (`SupportLinks.java`), its `SetupActivity` UI, the
  `--support` option in `publish-update.py`, the docs for all of it, the README's donation and heroes
  sections (and their badges), the donation sections of the `v1.4.0`/`v1.4.1`/`v1.4.2` release notes,
  and the donation entries in the September and October diaries were all removed on 2026-11-04
  (commits `091c110`, `3cad008`, `0936fa4` on `New-UI`). The card's text was published, not in the
  APK, so the `updates` branch must be re-published once to withdraw it for launchers built before
  the change.
- Near-misses when hunting donation leftovers: the campaign strings `"…Soberi $20 000…"` in
  `languages/*/generals*.str` and `tools/translation-work/*/out/008.json` are game text; `$5` in
  `README.md` is the game's Steam price; `❤️` was only ever the "Heroes" heading. A loose `tip\b`
  regex matches `TOOLTIP:` in every `.str` file — use donation-specific terms.
- UI: no layout XML at all — every screen is built in code through `UiKit.java`. Three palettes
  (standard lavender, **navy+gold**, day) remapped **by resource name** in `ThemeHelper.mapColor`,
  so a new `gzh_*` colour without a `gzh_navy_*`/`gzh_day_*` twin silently ignores the theme.
- 457 strings in all 13 locales, sets exactly equal — a new string must go into all of them.
  (488 → 487 when `setup_support_popup_later` went, 04/11/2026; 487 → 457 when 11 strings with no
  reader anywhere went, commit `53b35d3`.)
- **Lifecycle: two leaks existed until `53b35d3`.** `GeneralsZHActivity` had no `onDestroy` at all,
  so a `WifiManager.MulticastLock` acquired from `udp.cpp` (`setAndroidLanMulticastLock`, called
  from `UDP::SetBroadcastReceive`) was never released when the game screen closed; `onDestroy` now
  releases it, `synchronized` on the same monitor as `setLanMulticastLock()`. And
  `NetworkDiagnosticsActivity` had no `onDestroy`, so its sweep thread kept `handler.post`-ing to
  dead views; the thread is now a field, interrupted on destroy. Both files are the place to look
  when adding anything that outlives a screen.
- No tests anywhere: no `android/app/src/test/`, no `androidTest/`, no test task in CI. No Gradle
  wrapper either (neither `android/gradlew` nor a root one), and `android/app/build.gradle` has no
  `testImplementation` lines — so a JVM test suite cannot be run here without first building test
  infrastructure. Prefer a throwaway `javac`/`java` harness against a dependency-free class (see
  the custom-driver entry below).
- CI failure root cause seen in run 40084873349: `build/libmain60.so is not AArch64`. The workflow
  already retries this race for `libmain.so` (5 attempts) but the 60 Hz verify step uses the
  one-shot form.

## Conventions to follow

- Analysis/report files: this fork's `docs/WORKDIR/reports/` is the right place (the
  `docs.instructions.md` rules apply). Android-specific analysis: `docs/WORKDIR/reports/android-port-analysis/`.
- Annotate code changes `// GeneralsX @keyword author DD/MM/YYYY Description`.
- Local builds only; the owner's Actions budget is limited and the `push:` trigger was removed
  deliberately on 01/08/2026.

## Custom Vulkan driver library (04/11/2026, branch `New-app`, commit `f74d1fb`)

Two JVM-testable classes with **no `android.*` imports** (they use `org.json` only, which runs on
a plain JVM) carry the whole feature — that is what makes them testable, since the app has no test
infrastructure. `SetupActivity` keeps only the Android-shaped parts (the picked `Uri`, the
localized message per `Reason`, the card and dialogs):

- `CustomDriverPackage.java` — the adrenotools-package pipeline (unpack → validate → install) and
  the per-folder helpers (`describe`, `readDirInfo`, `writeDirInfo`, `INFO_NAME`).
- `DriverLibrary.java` — the **library**: imported drivers live one per folder under
  `<getFilesDir()>/drivers/`; "activating" one **copies** it into the single engine slot. `Entry`,
  `list`, `getActive`, `activate`, `delete`, `resetToDefault`, `reconcile`, `cleanupInterrupted`,
  `adoptActiveSlot`.

Contract with the native side, which is **unchanged** and must stay so:
`<getFilesDir()>/custom_driver.cfg` holds one line, the `.so`'s bare file name, and
`<getFilesDir()>/custom_driver/` holds that `.so` beside `meta.json`. The library keeps that slot
as a *copy* of the active entry. `getFilesDir()` is the same path
`SDL_GetAndroidInternalStoragePath()` returns, and must stay app-private internal storage —
`dlopen` refuses world-writable locations. `Core/Main/SDL3Main.cpp`'s
`TryLoadCustomVulkanDriver()` was **not** modified and must not be. Launcher-only additions:
`custom_driver.info` and the per-folder `info.json` (both the package's `meta.json`, for the
card's wording; absent or corrupt falls back to the file name) and `custom_driver.auto`.

- **"Which driver is active" is not one string.** Two Turnip builds can share a soname
  (`libvulkan_freedreno.so`), so the active entry is matched on the soname **and** the slot's own
  `meta.json` name/version/vendor when readable (`matchesActive`). A cache keyed on the soname
  alone marks both rows active. Identical re-imports dedupe (`findExisting`/`sameDriver`); a
  different build of the same soname stays separate.
- **Activation is atomic and the swap is reversible**: the slot moves to `custom_driver.old`, the
  new driver copies in, any failure restores the backup. `reconcile()` (slot lost / config
  mismatch) and `cleanupInterrupted()` (process died between the renames) heal it; both run on
  every card build and before every import. `adoptActiveSlot()` migrates a pre-library install by
  copying the slot into the list **without changing what is active**.
- **Only the Vulkan branch touches any of this.** `showTab(TAB_GRAPHICS)` builds the driver card
  inside `if (RENDER_BACKEND_VULKAN.equals(getRenderBackendChoice()))`, so GLES/ANGLE never load a
  custom driver. Do not move it out of that guard.

- **`meta.json` schema** (adrenotools ADPKG, what Winlator/AetherSX2/PPSSPP accept): `name`,
  `description`, `author`, `packageVersion`, `vendor`, `driverVersion`, `minApi`, `libraryName`.
  `libraryName` is a bare file name; a separator in it is refused because the native side does
  `std::string(dir) + libraryName`. `minApi` is enforced against `Build.VERSION.SDK_INT`.
- **The named `.so` is ELF-checked** (64-bit, little-endian, `EM_AARCH64`). Without it an x86 or
  32-bit package installs cleanly and only fails later inside adrenotools as a bare `ENOENT`.
- **The install is atomic and the swap is reversible**: the current driver moves to
  `custom_driver.old`, the new one renames in, and any failure restores the backup. That swap
  created a crash window the old code could not reach (backup in place, `custom_driver/` gone), so
  `recoverInterruptedInstall()` heals it — called before every import *and* when the card is built.
  Keep both call sites.
- **The reference branch `fix-ui-Draw-18-10` has nothing extra here.** Its driver code is identical
  to `New-app`'s apart from one unrelated `REQUEST_MODS` constant, and `TryLoadCustomVulkanDriver()`
  is byte-identical. Do not re-run that comparison expecting to find a feature.
- Every locale carries the same string names (487 after the driver library, 04/11/2026); two
  strings that lost their last reader (`setup_toast_driver_import_no_finalize`,
  `setup_toast_driver_config_failed`) were removed from all thirteen in an earlier commit. Check
  with a set comparison, not by eye.
- **Verify resource changes without Gradle**: `aapt2 compile --dir android/app/src/main/res -o
  out.zip` (fetch `aapt2` from `dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/…`;
  it is a jar with an `aapt2` binary inside). A stub JVM harness cannot see raw-`'` and
  dangling-`@ref` errors that only aapt2 catches. `javac`'s parse API
  (`com.sun.source.util.JavacTask.parse()`) syntax-checks a file like `SetupActivity.java` with no
  Android SDK present.

## Launcher accent colours (04/11/2026, branch `New-app`, commit `e10e9f8`)

- The launcher themes at runtime: `ThemeHelper.mapColor(c, colorRes)` maps a standard `gzh_*`
  colour to the chosen palette, and `UiKit.color()` calls it. Three palettes (standard dark, navy,
  day) plus an accent layer now resolve in that order: **accent -> palette -> standard name**. An
  accent that does not define a role falls through to the palette — that one rule is what gives the
  dark palettes accent-only tinting while day mode also recolours its surface ladder.
- **`New-app` has no `values-night/`.** Do not port the reference's `values/`+`values-night/` split
  or its `ThemeOverlay.*.Accent.*` styles verbatim; the reference is a different theming model. The
  reference branches are `fix-ui-Draw-18-10` and `New/app`.
- **The reference's own role names are inconsistent**: its base palette says `gzh_surface_container`
  while its accent overlay says `gzh_accent_blue_container_surface`. `mapColor` *composes* the
  accent name from the base colour's name, so generated resources must follow the **base role**.
  Naming them after the reference's spelling silently resolves nothing (found by a JVM test).
- **A cache keyed on the input colour alone is wrong here**: the two dark palettes name *different*
  resources for the same input (`gzh_primary` -> `gzh_primary` vs `gzh_navy_primary`), so the cache
  is per palette **and** per accent. The bug is invisible until both palettes have been used.
- Resources: `values/colors_accents.xml` (dark, 9 roles), `values/colors_accents_day.xml` (day,
  17 roles), `values/themes_accents.xml` (per accent per palette: activity overlay + Material and
  plain `AlertDialog` styles). Dialogs need their own styles — a dialog is a separate theme and
  never inherits the activity's accent.
- Generator: `scripts/tooling/theme/generate-accent-colors.py` (needs the reference's two
  `accent_colors.xml` under `--ref`, default `/tmp/ref`). Outputs are committed, so it only runs
  when the palette changes. It **validates before writing**: `validate()` fails with the list of
  any `@color`/`@style`/`@drawable` the generated themes reference but nothing defines. Keep that
  ordering — a dangling ref otherwise only surfaces at the end of a full APK build.
- **A real APK build IS possible here** (installed under `/workspace/.buildtools`; `/tmp` gets
  wiped mid-session, so never put a 160 MB SDK there). JDK 21 via apt (`openjdk-17` does not
  exist in this image), Gradle 8.9, cmdline-tools + `platforms;android-35` + `build-tools;35.0.0`.
  `android/app/src/main/java-sdl/` is **gitignored** and staged only by `package-android-zh.sh`;
  without it `GeneralsZHActivity` emits ~64 bogus errors. Stage SDL3 **3.4.2**'s
  `android-project/.../org/libsdl/app/*.java` plus `android/patches/*.patch`, then
  `cd android && gradle :app:assembleDebug --no-daemon`. Fast pre-check without Gradle:
  `aapt2 compile --dir res -o out.zip`.
- **Three build errors reddened CI on `e10e9f8`; fixed in `d5a4ec9`.** The stub harness only drove
  Java logic, so it never ran aapt2. (a) raw `'` in `values-ko/strings.xml` — aapt2 needs `\'`,
  and *every* locale must escape, not just `values/`; (b) `ACCENT_ITEMS` used roles
  `container`/`on_container`/`ripple` instead of the generated `primary_container`/
  `on_primary_container`/`ripple_primary` (33 colours missing); (c) `SetupActivity` used
  `android.view.ViewGroup` with no import.

## Daily logs

- `2026-11-18.md` — the Android CI **build cache**: ccache wired into
  `scripts/build/android/build-android.sh` as a CMake compiler launcher, persisted from both
  workflows with an explicit `actions/cache/restore`+`save` pair (plain `actions/cache` saves only
  on job success, so a cancelled run kept nothing). Warm run measured 4m06s vs 22m42s cold at
  99.86% hits. Also the SAF "Use this folder" crash fix and its CI evidence.
- `2026-11-06.md` — the lightweight launcher-only Android test workflow
  (`android-unit-tests.yml`), `stage-sdl-java.sh`, the `launcherOnly` build type, the
  `engineInstalled()` guard, and the facts that cost time: positional printf args are legitimately
  reordered, `UiKit` needs the app theme, an UpdateManager-delivered engine is not in the APK, and
  Gradle lint reports can be stale. Plus the touch editor's layout rework, background picture and
  Clear Key (report `TOUCH_EDITOR_LAYOUT_BACKGROUND_CLEARKEY_20261106.md`): the four reported layout
  symptoms were one cause — a **horizontal `LinearLayout` does not wrap** (fixed with the new
  `FlowLayout`), and a `layout_weight="1"` **empty** spacer in a `WRAP_CONTENT` parent has no size
  of its own and stretches the parent; orientation must be decided in the container's own
  `onMeasure`/`onSizeChanged` (never `OnGlobalLayoutListener`), and that guard must include the
  **width** or the panel freezes at the pre-layout fallback width; `drawable-nodpi` + centre-crop
  (max ratio) for a bundled background; Clear Key is `KEYCODE_UNKNOWN`, kept by `readButton()`
  (`SCHEMA` 5).
- `2026-11-04.md` — full repository analysis, the eight reports, and the verification steps used;
  plus the removal of the financial-support system from `android/` (commit `091c110`), of its
  published payload and docs (commit `3cad008`), of every remaining trace from the README,
  release notes and diaries (commit `0936fa4`), of the in-app updater (Home "Updates" card +
  its silent check, commit `c7a2c8d`), and of the dead code plus the two lifecycle leaks
  (commit `53b35d3`) — all on `New-UI`. Also the four-engine CI change (commit `6b93f01`), and
  the launcher accent colours on `New-app` (commit `e10e9f8`).

## Launcher-only testing, no native build (06/11/2026, branch `New-app`)

Only `GeneralsZHActivity` needs the engine; the other eight screens are pure Java, and the launcher
compiles against SDL's **Java** only. So `.github/workflows/android-unit-tests.yml` runs
`testDebugUnitTest` + `lintDebug` + `assembleLauncherOnly` with no CMake/vcpkg/NDK at all, staging
SDL3's Java via `scripts/build/android/stage-sdl-java.sh` (tarball 3.4.2, SHA-256 checked, two
patches) instead of the CMake FetchContent tree. `launcherOnly` is a debug-derived build type with
`**/*.so` excluded through `androidComponents`, same signing key, so it installs over a debug APK.
Details and the traps (reordered positional printf args; `UiKit` needing the app theme; an
UpdateManager-delivered engine living outside the APK; stale Gradle lint reports) are in
`.openhands/memory/2026-11-06.md`.

## The Android APK carries four engines (04/11/2026)

Both games at both simulation rates: Zero Hour's `libmain.so` / `libmain60.so` and the base game's
`libgenerals.so` / `libgenerals60.so`, selected by `GeneralsZHActivity.getLibraries()` /
`SetupActivity.engineLibraries()`. `SAGE_HIGH_FPS_SIM` (default OFF in `cmake/config-build.cmake`)
is a **compile-time** switch — 30 Hz vs 60 Hz cannot be a runtime option — so any build path that
produces all four must compile the engines twice. Local source of truth:
`scripts/build/android/build-dual-hz.sh` (pass 1 60 Hz, stash `build/libmain60.so` +
`build/libgenerals60.so`, pass 2 30 Hz, then package with `GX_SECOND_GAME_LIB` /
`GX_SECOND_BASE_GAME_LIB`). `.github/workflows/build-android.yml` was restructured to the same two
passes on 04/11/2026 (commit `6b93f01`); before that it built one engine (ZH 30 Hz) and no base-game
engine, which `docs/WORKDIR/reports/android-port-analysis/CI_AND_WORKFLOWS.md` had already recorded.


## The launcher has no in-app updater any more (04/11/2026)

The Home "Updates" card was removed **and** the silent start-up check with it (owner's choice of
the fuller option). Consequences to remember:

- **An engine update now needs a new APK.** Nothing calls `UpdateManager.check(ctx, true)`.
- The **signed settings** still arrive: `GeneralsOnlineActivity` checks the `updates` branch when
  the account screen opens. The **community data patch** is still checked and installed there too,
  now unconditionally (the removed "Check automatically" switch was its only gate's only writer).
- Kept but unused on purpose: the engine-download path in `UpdateManager`, the manifest `engine`
  entry, and `publish-update.py`'s engine steps. Restoring the card restores the feature; do not
  "clean up" these without asking the owner. (The now-callerless `isAutoCheckEnabled` /
  `setAutoCheckEnabled` were kept at first and then removed as dead code in `53b35d3`;
  `KEY_AUTO` stays only as the stored preference's documented name.)

## Where maps live, and the Tools tab (07/11/2026, branch `New-app`)

- **Custom maps are `<user data>/Maps/<name>/<name>.map`.** The engine scans
  `MapCache::getUserMapDir()` = `getPath_UserData() + "Maps"` on every map-list refresh
  (`loadMapsFromDisk`), and `MapCache::hasValidMapLeafLayout` accepts a `.map` **only** at
  `<name>\<name>.map` — the folder must be named after the map file. A `.map` is an `EAR\0`
  RefPack stream or, uncompressed, a `CkMp` chunk tree. On Android `getPath_UserData()` is the
  folder `SDL3Main.cpp` exports as `GENERALSX_USERDATA_DIR`
  (`/storage/emulated/<uid>/Generals/<leaf>`), which is exactly `SetupActivity.userDataDir(ctx)`.
  So the launcher writes maps with plain `File` I/O and the game finds them with no restart.
- **Control-bar assets are a chain**: `Window\ControlBar.wnd` (loaded by
  `winCreateFromScript("ControlBar.wnd")`, `InGameUI.cpp`), schemes under
  `Data\INI\{Default\}ControlBarScheme\*`, mapped images under `Data\INI\MappedImages\*`
  (`Image.cpp`), textures under `Art\`. All ordinary archive entries, so a mod that ships them in
  a `.big` only needs to be **mounted** — no extraction into the game folder.
- **BIG table** = `BIGF`/`BIG4`, **little-endian** declared size, then **big-endian** count and
  header size, then per entry big-endian offset + size + NUL-terminated path (the same rules
  `ModHealthCheck.checkBigTable` and `ModPacker` use).
- **New Tools screens** (`Setup → Tools`): `MapPackManager`/`MapPackActivity` — map **packs** live
  under `<user data>/maps-manager/<pack>`; *activating* a pack **copies** its maps into
  `<user data>/Maps` (where `MapCache` finds them), deactivating removes those copies. A pack is
  never moved, so it can be toggled any number of times. Ownership: the desired set is the union of
  the active packs' maps keyed by name, and a manifest (`maps-manager/.active.txt`) records the
  `Maps` folders the manager itself created — a hand-placed map in `Maps/` is never deleted.
  `sync()` heals an interrupted activation (called on screen open and from `GeneralsZHActivity`
  before the engine starts). The **200-map limit counts unique active maps**; an over-limit
  activation is refused whole with the exact message the owner specified. Export writes a pack as a
  folder/ZIP from the pack root (so the ZIP imports back as the same pack). `MapManager` is kept as
  the shared map-location/map-format helper (its old `MapManagerActivity` is deleted);
  `ControlBarBig`/`ControlBarBigActivity` (read a `.big`, select only the control-bar chain, write
  it into a managed mod folder and `ModPacker.ensurePacked` it). All reuse `ModImportService`'s
  path helpers (`normalizeZipPath`, `resolveSafeRelative`, `sanitizeInstallName`, `uniqueTarget`,
  `isInside`, `deleteRecursively`, `INSTALLING_DIR`) rather than a second importer/parser.
- Unit tests that export a map go through `ContentResolver.openOutputStream()`, which Robolectric
  backs with a `ParcelFileDescriptor`; closing it reflects into `java.io.FileDescriptor.fd`, which
  JDK 17+ refuses. `app/build.gradle` sets
  `unitTests.all { jvmArgs '--add-opens=java.base/java.io=ALL-UNNAMED' }`.
- Report: `docs/WORKDIR/reports/TOOLS_MAP_MANAGER_AND_CONTROLBAR_BIG_20261107.md` (the original
  Map Manager) and `docs/WORKDIR/reports/TOOLS_MAP_PACKS_MANAGER_20261107.md` (the Map Packs
  Manager rewrite: activation, the 200-map limit, export).


## The Mods "scripts folder" option (removed 13/11/2026) and a local Android launcher toolchain

- **The Mods screen's scripts-folder switch is GONE (owner's request, 13/11/2026).** `ModScriptsBackup`,
  its two test classes, the `mods_scripts_backup_*` strings in all 13 locales, the Mods-screen card and
  `ModManager.anyEnabled`/`anyEnabledForPath` were all deleted; the launcher no longer renames
  `Data\Scripts` for any reason. History: `docs/DEV_BLOG/2026-11-DIARY.md` (13/11) and
  `docs/WORKDIR/reports/MODS_SCRIPTS_FOLDER_BACKUP_20261107.md` (marked removed).
  The facts below outlived the feature and still apply to launcher code that touches the game folder.
- **The port has TWO `data` directories in the game folder, and the engine matches either.** Retail
  spells it `Data` (loose scripts at `Data/Scripts/SkirmishScripts.scb`); the launcher installs its
  language packs into a lower-case `data/<lang>/generals.str` (`SetupActivity.downloadLanguagePack`)
  in the same folder. So a plain `findChild(game, "data")` — exact match first — can return the
  *language* folder and miss the game's own. Check for this whenever Android launcher code resolves
  `data/...` in the game folder.
- Prefer `java.nio.file.Files.move` over `File.renameTo` for renames in the game folder (FUSE-backed
  shared storage; `renameTo` fails silently with no reason).
- Ordering trap worth remembering: the folder moves between the mod switch changing and the order
  list being saved, so "is any mod on?" must be read from the in-memory list, not the saved one.
  Every *other* place mods are switched off outside the Mods screen (safe start's
  `ModManager.switchOff` in `SetupActivity.onLaunchGame`) must follow with `ModScriptsBackup.sync`.
- The Java-only launcher checks (`:app:testDebugUnitTest`, `:app:lintDebug`,
  `:app:assembleLauncherOnly`, `scripts/qa/android/check-translations.py`) run with JDK 21 + Android
  SDK platform/build-tools 35 + Gradle 8.9 + `stage-sdl-java.sh` — no NDK/CMake/DXVK. Setup details
  and the traps (cmdline-tools `bin/*` loses its execute bit; no `unzip`; `/opt` is root-owned —
  `chown` it; `UiKit.switchRow`'s label is a sibling view) are in `.openhands/memory/2026-11-07.md`.

## App identity, 60 Hz default, icon and system theme (08/11/2026, branch `New-app`)

- **`applicationId` and the Java `namespace` are now the SAME: `com.ports.generalsx`** (unified
  10/11/2026). They used to differ (`namespace com.generalsx.zerohour`), which is why the old name
  is still mentioned in comments and historical docs — treat those as history, not state. The
  source lives under `android/app/src/main/java/com/ports/generalsx/`, the manifest components are
  relative (`.SetupActivity`), the FileProvider authority (`${applicationId}` in the manifest,
  `getPackageName()` in Java) resolves to `com.ports.generalsx.fileprovider`, and the JNI symbols
  are `Java_com_ports_generalsx_*` (in `AndroidTextEditor.cpp` and the `native` declarations — a
  mismatch there is a runtime `UnsatisfiedLinkError`, not a compile error).
- **The update system is one mechanism, signed (10/11/2026).** `UpdateChecker` reads
  `update.json` + `update.json.sig` from the newest GitHub Release of `redaf12-Decode/Generals-Android-Port`
  and verifies an ECDSA P-256 signature (public key in `UpdateChecker.PUBLIC_KEY_B64`) before
  trusting any field; `ApkInstaller` re-checks size, SHA-256, package, versionCode and signing
  certificate. Metadata is generated from the published APK by `scripts/update/generate-update-metadata.py`
  and signed with the `UPDATE_METADATA_KEY` secret (not yet set — owner action). The old MYSOREZ
  engine-OTA system (signed manifest on an `updates` branch, `UPDATE_SIGNING_KEY`, `publish-update.py`)
  is gone; the community data patch (`DataPackInstaller`, GeneralsOnline CDN) is a separate feature
  and still works.
- **Seven native files build the launcher's private-files path by hand** —
  `/data/user/<uid>/<applicationId>/files/...` — because they run before `SDL_main` or are
  `Context`-less inline helpers: `AndroidCrashHandler.cpp`, `GXLogging.h`, `GXSubtitles.h`,
  `GXControlScheme.h`, `GXMouseMode.h`, `GXManagedMods.h`, `GXRemoteConfig.h`. The `<applicationId>`
  segment IS the id, so any id change must change them too. The id now lives once in
  `Core/GameEngine/Include/Common/GXAndroidApp.h` (`GX_ANDROID_APP_ID`), included by all seven.
  `NetworkTraceRedactTest` used to hardcode the id in the external-files path; it now asserts against
  `getPackageName()`.
- **Default engine tick rate is now 60 Hz** (`SetupActivity.getSimHz()` default → `SIM_HZ_CROSSPLAY`).
  Only the SharedPreferences default changed: the Simulation Rate card and
  `GeneralsZHActivity.getLibraries()` both already read `getSimHz()`, and the 30 Hz fallback when the
  60 Hz `.so` is missing is unchanged. `build-dual-hz.sh` already packaged all four libraries.
- **Default launcher theme is now SYSTEM** (`ThemeHelper.getMode()` default/fallback). `palette()`
  already resolved `SYSTEM` via `Configuration.UI_MODE_NIGHT_MASK` (light→DAY, dark→STANDARD), and no
  launcher Activity lists `uiMode` in `configChanges`, so a system light/dark switch recreates the
  screen and re-resolves the palette on its own.
- **Adaptive launcher icon** foreground+monochrome are the official Zero Hour icon from SteamGridDB
  (icon 59649, by pix0wl, CDN `.../647a924986a26860d4813818f03672f5.ico`, 256×256 frame), scaled so
  its farthest opaque pixel sits at ~32dp from centre (inside the 33dp safe circle of the 108dp
  canvas), regenerated for all five densities. Attribution is in `mipmap-anydpi-v26/ic_launcher.xml`.
- **Local Java-only launcher toolchain** (no NDK/CMake/DXVK needed). The `~/tools/*` installs are
  ephemeral — recreate them per container from the internet: apt `openjdk-21-jdk-headless` (Debian
  here has no JDK 17) + `unzip`, Gradle 8.9 from `services.gradle.org` into `/opt/gradle` (symlink
  into `/usr/local/bin`), Android cmdline-tools `13114758` (sha1
  `5fdcc763663eefb86a5b8879697aa6088b041e70`) → `platforms;android-35` `build-tools;35.0.0`, then
  `android/local.properties` = `sdk.dir=<sdk>` (gitignored). `android/app/src/main/java-sdl` (SDL3's
  Java glue) is committed, so `stage-sdl-java.sh` is only needed if it is missing. Then
  `gradle --no-daemon :app:{testDebugUnitTest,lintDebug,assembleLauncherOnly}`. Verify identity
  without the SDK by parsing the binary `AndroidManifest.xml` string pool, or with
  `aapt2 dump badging` (package id, launchable-activity, `application-icon-*`). Report:
  `docs/WORKDIR/reports/ANDROID_APP_IDENTITY_60HZ_ICON_THEME_20261108.md`.
- **A source commit does NOT refresh the downloadable `apk/` artifact.** After the four changes
  landed in `9502bc2`, `apk/GeneralsXZH-android-generals-a48.apk` still carried the OLD package id
  and the OLD icon (`git diff <parent> HEAD -- apk/` empty). The branch and a fresh
  `assembleLauncherOnly` APK are correct; only a full `build-dual-hz.sh` run (NDK + vcpkg + DXVK,
  ~4 engine builds) and a commit of the result makes the shipped APK match. Check this whenever a
  commit claims an identity/icon change.
- **One update mechanism now, and it is release-asset based (10/11/2026, branch
  `android-package-migration-update`, PR #8).** `UpdateChecker` reads `update.json` + `update.json.sig`
  from this repo's **newest GitHub Release** (`releases/latest/download/...`), refuses anything whose
  detached ECDSA P-256/SHA-256 signature does not verify against the embedded public key, then compares
  `versionCode`. `ApkInstaller` downloads and checks size + SHA-256 + package id + versionCode +
  signing-cert before the official installer runs. `scripts/update/{generate-metadata-key,
  generate-update-metadata,validate-release}.py` generate and sign the metadata **from the published
  APK** (badging + apksigner + sha256sum) inside `publish-release.yml` / `build-android.yml`.
  `UpdateManager` survives but is **datapack-only** now -- the MYSOREZ engine-OTA system (signed
  manifest on an `updates` branch, `update/`, `update.json`, `sign-update.yml`, `publish-update.yml`)
  is gone; an engine update is a new APK. Report:
  `docs/WORKDIR/reports/GITHUB_JSON_UPDATE_CHECKER_20261109.md` is superseded by PR #8.
- **`JSONObject` silently coerces a JSON number.** `1.5` read through `Number.longValue()` becomes
  `1` -- so `UpdateChecker.readVersionCode` checks `value == Math.floor(value)` before casting, or a
  fractional version code would ship a wrong number. Keep this in mind for any untrusted JSON field.
- **Gradle wrapper is absent** (`android/` has no `gradlew`/`gradle/`); run the system `gradle`
  8.9. `gradle --no-daemon :app:testDebugUnitTest` takes ~40-70 s cold. The launcher-only variant
  (`:app:assembleLauncherOnly`) is the full Java check with no NDK/CMake/DXVK.
- **Phase 1 of the native Android ARM64 port is done and green (16/11/2026).** Branch
  `android-arm64-port` (HEAD `c8cfe07`). `cmake/DetectArchitecture.cmake` normalises
  `CMAKE_SYSTEM_PROCESSOR` / `CMAKE_CXX_COMPILER_ARCHITECTURE_ID` / `ANDROID_ABI` into
  `ZH_ARCH_X86_64`/`ZH_ARCH_AARCH64`/`ZH_ARCH_KNOWN`; the ABI, when set, decides alone (a host-leaked
  `x86_64` processor otherwise set both flags). `Tests/test_architecture_detection.cmake` is a
  `cmake -P` matrix; `test_platform_predicates.cpp` proves Android is never desktop Linux at compile
  time. The two `CMAKE_CXX_COMPILER_ARCHITECTURE_ID STREQUAL "ARM64"` tests became `ZH_ARCH_AARCH64`.
  Workflow `.github/workflows/ci-android-test.yml` ("CI Android Test") configures arm64-v8a/API 28/
  c++_shared with NDK r27.2.12479018 and compiles SDL3, miniaudio, mbedTLS and the predicate tests
  under aarch64-linux-android. Report: `docs/android-port/audit/05-dependency-compatibility.md`.
- **CI lessons that cost several runs (16/11/2026).** CMake's Ninja generator does NOT put the
  `aarch64-linux-android` triple in `build.ninja` OR `CMakeFiles/rules.ninja`; prove the target by
  running the NDK wrapper `aarch64-linux-android<api>-clang++ -dumpmachine` (it prints
  `aarch64-unknown-linux-android28` — optional vendor field + API suffix) and by compiling a TU and
  reading `llvm-readelf -h` (expect `Machine: AArch64`). A cross-compile `CMakeCache.txt` has no
  `CMAKE_CXX_COMPILER` entry — read it from `CMakeFiles/<ver>/CMakeCXXCompiler.cmake`. `actions/cache`
  needs the SDK under the runner user's home (`/home/runner/...`), not `/opt`. The 157 MiB
  cmdline-tools download needs `--http1.1 --retry-all-errors` (curl 92 at 93%). Workflow/step names:
  each step's log is prefixed by `job\tstep\t`.
- **Android/NDK is the *native* CMake build; it is a different tree from the `android/` Gradle
  launcher.** `scripts/build/android/{build-android.sh,android.toolchain.cmake}` configure
  `GeneralsMD/Code` with `-DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared`.
  `ZH_PLATFORM_ANDROID` implies `ZH_PLATFORM_POSIX` but never `ZH_PLATFORM_LINUX`, so X11/Wayland/
  fontconfig/LLD/Steam-Runtime stay out.
- **The full engine builds and links for Android arm64-v8a as `libmain.so` (Phase 2, 16/11/2026).**
  ELF64/AArch64/DYN, exports `SDL_main`, `NEEDED` only Android system libs (`libm,libdl,liblog,
  libandroid,libc++_shared,libc`). `libmain.so` is `PosixMain.cpp` built as a shared library
  (`add_library(main SHARED)`, `OUTPUT_NAME main`) — the `generals` target — which SDL3's SDLActivity
  `dlsym`s. The old Phase-2 blockers are resolved: GameSpy's `pthread_cancel` (patched / PIC walk),
  FFmpeg (cross-built for aarch64 by `ffmpeg-build-posix.sh` given the NDK's
  `aarch64-linux-android28-clang` wrapper). CI run 43929052937 built and asserted it. See
  `.openhands/memory/2026-11-16.md`.
- **Android runtime bootstrap (16/11/2026).** `PosixMain.cpp`'s `establishAndroidStorage()` sets
  `HOME` from `SDL_GetAndroidInternalStoragePath()` (bionic has no HOME, no passwd DB, so
  `findUserDataDirectory` answered nothing and Options.ini/saves/crash report/one-copy lock had no
  home). `isExecutablePackaged()` returns TRUE on Android (an APK is a package; `readExecutablePath`
  is ""), sending logs to the user-data dir and reaching the install-root chooser.
- **`set -o pipefail` + `grep -q` on a big tool's output = SIGPIPE false negative.** `llvm-readelf
  --dyn-syms libmain.so | grep -q SDL_main` exits 74 (grep closes the pipe while readelf still
  writes), so a present symbol reads as missing. Capture to a variable first, then `grep <<<"$var"`.
  Bit me in CI once; the small `-h` header fit the pipe buffer and hid it.
- **Reference-branch port (18/11/2026, branch `agent/android-arm64-application-port`, commit
  `6fecf3c`).** Reference repo `louay0khaled/Zero-Hour-Reforged-Android` branch
  `android/phase2-bootstrap` @ `a8ab00a8` (base/main `b37515b1`). Report:
  `docs/android-port/audit/REFERENCE-BRANCH-COMPARISON.md`; changelog:
  `REFERENCE-PORT-CHANGELOG.md`. **The target's Android CI builds Release**, so every reference
  C++ "fix" that lives inside `_DEBUG`/`_INTERNAL`/`DUMP_PERF_STATS`/`DO_UNIT_TIMINGS` is not
  compiled by CI — they are latent Debug-config errors, not current breakage. The one
  runtime-affecting import was the Android font branch in `glyphrasteriser.cpp::resolve()`: with
  no Android arm, `ZH_GLYPHS_FONTCONFIG`=0 (Android is `__linux__` but `ZH_DESKTOP_LINUX`=0) so it
  read `/System/Library/Fonts/Supplemental/` and resolved nothing → no text. Ported, gated on the
  target's `ZH_ANDROID` (not `defined(__ANDROID__)`), body = `/system/fonts/` Roboto/Noto/Droid
  Sans Mono with a `fileExists()` probe each and Roboto as last resort.
- **What the reference did NOT add anything for (already implemented in target, do not re-port):**
  `PreRTS.h` `sys/timeb.h` guard; `execinfo.h` → the target's `Platform/StackWalk.h`
  `_Unwind_Backtrace` (better); `profile.h` + `GameMemory.cpp` `_MSC_VER` auto-link-pragma guards;
  GameSpy PIC (`zh_android_gamespy_pic`); GameSpy `pthread_cancel` (target's own
  `gamespy-android-thread.patch`); `ZH_PLATFORM_ANDROID` platform detection; host fingerprint tool;
  FFmpeg `--target-os=android`; `-ffp-contract=off`; `Common/file.h` case. Reference-only items
  NOT imported: shared SDL3 + its `GeneralsReforgedActivity`/manifest (target links SDL3 *static*
  into `libmain.so`, loads `{"main"}` — a regression to change), `ZH_SDL3_TARGET`, `android-build.yml`.
- **Pre-existing target defect, unrelated to the reference: `linux-release.yml` fails at
  `GeneralsMD/Code/Tests/test_vendor_no_art.sh`.** Its fixture builds a fake source tree and copies
  `vendor.sh` but NOT the `*.patch` files, so `vendor.sh`'s `install_gamespy_android_thread_patch`
  runs `git apply …/gamespy-android-thread.patch` against a missing file → "can't open patch" →
  "did not apply" → test fails. Reproduced locally with `bash GeneralsMD/Code/Tests/test_vendor_no_art.sh`.
  Fix would be to copy `Libraries/Source/*.patch` into the fixture (left for a separate task).
- **Which files are actually compiled for Android:** `w3ddevice` is inside the POSIX block (no
  enclosing Windows guard at CMakeLists ~1823), so W3DDevice/*.cpp build; WorldBuilder/Tools do
  not. `debug`/`profile` libs are Windows-only (`if(ZH_PLATFORM_WINDOWS)`), so `debug.h`'s
  unguarded `#pragma comment` is never reached on Android. W3DDisplay's `StatDumpClass`
  `GetModuleFileName` is inside `DUMP_PERF_STATS` (off in Release).

- **SAF "Use this folder" crash — root cause was URI shape, not permissions (18/11/2026, commit
  `043d04c`).** `ACTION_OPEN_DOCUMENT_TREE` returns a **tree** URI
  `content://<auth>/tree/<treeId>`, which is NOT a document URI. A `DocumentsProvider`'s `final
  query()` matches only `document/*`, `document/*/children`, `tree/*/document/*`,
  `tree/*/document/*/children` (AOSP `DocumentsProvider.registerAuthority`), so a bare tree URI
  hits `default: throw new UnsupportedOperationException("Unsupported Uri")`; `DocumentsContract
  .getDocumentId(treeUri)` likewise throws `IllegalArgumentException`. `SetupActivity
  .onActivityResult`'s first provider call was `SafTreeStager.displayName(resolver, treeUri)`
  querying the bare tree URI; it caught only `IllegalArgumentException|SecurityException`, so the
  `UnsupportedOperationException` escaped on the UI thread → instant crash on "Use this folder".
  `listChildren` had the same bug (`getDocumentId(treeUri)` → swallowed → staged 0 files). Fix:
  `SafTreeStager.rootDocumentUri(treeUri)` builds `…/tree/<treeId>/document/<treeId>` (a tree's
  root doc id IS the tree id, what `DocumentFile.fromTreeUri` builds) for both the walk and the
  name; never let a provider exception escape the callback. Report:
  `docs/android-port/audit/SAF-CRASH-FIX.md`. Lesson: a tree URI is never a document URI.
- **Android Java host tests are pure JVM only** (`:app:testDebugUnitTest`, no Robolectric/Android
  types). To unit-test Android-touching logic, extract the *rule* into a pure class
  (`SafTreeUri`, `SafPickerResult`) with no `android.*` imports and have the production code call
  it — mirrors of the rule are not real tests. `ci-android-app.yml` runs these plus manifest/APK
  assertions; `ci-android-test.yml` only triggers on push to branch `android-arm64-port` (not
  `agent/android-arm64-application-port`) or on PR paths, so dispatch it by hand
  (`gh workflow run`) for this branch. Use `GH_TOKEN="$GITHUB_TOKEN" gh …`.
- **Compose + Material 3 in the Android shell (19/11/2026, `agent/material3-ui-redesign` @
  `b029894f`).** Detail in `2026-11-19.md`. Kotlin **2.2.0** is the first KGP supporting AGP
  8.7.3 (2.1.x stops at 8.7.2); from Kotlin 2.0 the Compose compiler is the
  `org.jetbrains.kotlin.plugin.compose` plugin at the *same version* —
  `composeOptions.kotlinCompilerExtensionVersion` is gone. BOM 2025.06.01 → material3 1.3.2.
  **Compose silently breaks two `ci-android-app.yml` assertions**, both fixed in-manifest/DSL
  rather than relaxed: (1) `androidx.core` 1.13.1 adds a `<permission>` + `<uses-permission>`
  for `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, pushing the merged manifest
  past the `<= 2` cap → `tools:node="remove"` both (safe: no not-exported runtime receiver
  here); (2) `androidx.graphics:graphics-path` ships `jni/*/libandroidx.graphics.path.so` and
  CI demands only `libmain`+`libc++_shared` → `packaging { jniLibs { excludes.add
  '**/libandroidx.graphics.path.so' } }` (only reached by Compose `Path.iterator()`).
- **Standalone `kotlinc` type-checking of Android/Compose code works and is the best local
  gate, but stop at the frontend.** Classpath = `classes.jar` unzipped from each cached AAR +
  `org.robolectric:android-all` (API 35) as `android.jar` + a stub `R`. The Compose plugin's
  *bytecode generation* then crashes even on a trivial `@Composable` (`ERROR_EXPR 'Default
  Stub'` on `Modifier.Companion`) — a harness artifact, not app breakage. Read the count of
  `: error:` lines, ignore the exit code. Java must be compiled first (Kotlin imports it).
  Pure-logic tests can be run for real: `kotlinc` + `java org.junit.runner.JUnitCore`.
- **Kotlin↔Java, verified by compiling, not assumed:** an exhaustive `when` over a **Java**
  enum needs **no `else`** (adding one is only a "redundant else" warning), and
  **package-private Java constructors are callable from Kotlin in the same package**.
- **Material 3 phase 1 is CI-verified green** (`agent/material3-ui-redesign`; run 44620945167 @
  `44a5e17`, re-run 44627851527 @ `b6a5717`): native engine + `assembleDebug` + 53 unit tests
  + APK/manifest assertions all pass. Detail in `2026-11-19.md`. Additions since the entry
  above: (a) **a `darkColorScheme`/`lightColorScheme` pair is not automatically accessible** —
  the shared verdict tones were 1.3–2.8:1, so verdicts now come from a per-scheme
  `VerdictColors` via `LocalVerdictColors` (body text was always ≥6.4:1); (b) `ui-tooling`
  adds no manifest entry and nothing pulls `ui-test-manifest`; (c) material3 1.3.2 exposes
  `foundation`/`foundation-layout`/`ui` as **api** deps, so `androidx.compose.foundation.*` is
  importable without declaring it; (d) KGP 2.2.0 checks only a **minimum** AGP (7.3.1) — no
  upper bound, so 8.7.3 is fine; (e) `jvmTarget` is `Property<JvmTarget>`, so use
  `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }`. **Dispatch this branch's CI by hand**
  (`gh workflow run ci-android-app.yml --ref <branch>`): its `push` trigger lists only the
  older port branch, so a plain push runs nothing.
- **Material 3 phase 2 (design system) is CI-verified green** (`agent/material3-ui-redesign`;
  run 44645246453 @ `2e24156`): engine + `assembleDebug` + all 10 JVM suites (66 WCAG pair
  assertions) + APK/manifest assertions pass. Detail in `2026-11-19.md`.
- **The one thing static audits could NOT catch: a duplicate resource *name*.** A new
  `res/values/themes.xml` re-declared `<style name="AppTheme">`, which already lived in the
  pre-existing `res/values/styles.xml` → aapt2 `:app:mergeDebugResources FAILED` "Duplicate
  resources". Nothing in the Kotlin/XML source looks wrong; only `mergeDebugResources` knows.
  Before adding any `values*/` file, grep the *whole* `res/` tree for each resource name —
  including the legacy `styles.xml` nobody remembers. My own dup-checker script (scan all
  `values*/*.xml` per qualifier bucket) is the cheap substitute when no local build exists.
- **`AppTheme` is the game's own bare fullscreen theme** (`styles.xml`,
  `android:Theme.NoTitleBar.Fullscreen`) and must stay separate from `Theme.ZeroHour` (the
  Compose window theme). `ZeroHourActivity` inherits `AppTheme` from `<application>`; only
  `SetupActivity`/`CompatibilityActivity` carry `android:theme="@style/Theme.ZeroHour"`.
