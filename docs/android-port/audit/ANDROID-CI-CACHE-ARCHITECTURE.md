# Android CI build architecture and cache inventory

The state of the target's Android CI build as it actually is on
`agent/android-arm64-application-port`, so a cache decision can be made per directory instead of
over the whole run. Every path below is read from the workflows and scripts in this repository, not
assumed.

Two workflows build Android:

| Workflow | File | Job | What it produces |
|:--|:--|:--|:--|
| CI Android Test | `.github/workflows/ci-android-test.yml` | `android-arm64` | configures and compiles the engine for arm64-v8a; no APK |
| CI Android App | `.github/workflows/ci-android-app.yml` | `android-app` | the same native engine plus the Gradle APK, packaged and inspected |

Both run `scripts/build/android/build-android.sh`, which configures CMake with
`scripts/build/android/android.toolchain.cmake` (a thin layer over the NDK's own toolchain file) and
builds the `generals` target as `libmain.so` for `arm64-v8a`, API 28, `c++_shared`. The app workflow
then runs `scripts/build/android/package-android.sh`, which stages `libmain.so`, `libc++_shared.so`
and SDL3's Java glue into `android/app/src/main` and runs Gradle.

## Directory inventory

`$RUNNER_TEMP` is `${{ runner.temp }}` (e.g. `/home/runner/work/_temp`); `$WORKSPACE` is
`${{ github.workspace }}` (e.g. `/home/runner/work/Generals-Reforged/Generals-Reforged`).

| # | Category | Path | Where it comes from | Notes |
|:--|:--|:--|:--|:--|
| 1 | Downloaded tool | `/home/runner/android-sdk` (SDK; NDK at `.../ndk/27.2.12479018`) | `sdkmanager` from the pinned, checksummed cmdline-tools zip | Under `$HOME`, not `/opt`, so `actions/cache` writes it without root |
| 1 | Downloaded tool | Gradle distribution | `gradle/actions/setup-gradle` | Managed by the action, not a path we set |
| 1 | Downloaded tool | `cmake`, `ninja` (`/usr/bin`, `/usr/local/bin`) | `apt-get` in "Prepare the ephemeral runner" | ~10 s; not worth caching |
| 2 | Downloaded source dependency | `$RUNNER_TEMP/zhr-vendor` | `GeneralsMD/Code/Tools/vendor.sh` (`work="${TMPDIR:-/tmp}/zhr-vendor"`) | Immutable archives: SDL3, miniaudio, mbedTLS, glslang, FreeType, SPIRV-Cross, SDL_shadercross, GameSpy, the FFmpeg 8.1.2 tarball, … |
| 2 | Downloaded source dependency | `$WORKSPACE/GeneralsMD/Code/Libraries/Source/<dep>` | unpacked from `zhr-vendor` by `vendor.sh` each run | Re-derived from the cached archives; not cached itself |
| 3 | Prebuilt dependency package | (none) | — | This port builds every dependency from source; it uses no vcpkg, no Conan, no prebuilt NDK package |
| 4 | Compiler output | `/home/runner/.ccache` (`CCACHE_DIR`) | ccache, launcher-wrapped into CMake by `build-android.sh` | The object cache for the ~1.6M-LOC engine |
| 4 | Compiler output | `$WORKSPACE/build-android-arm64` (app) / `$RUNNER_TEMP/build-android-arm64` (test) | CMake/Ninja | Ninja `.o` files, `libmain.so`, the FFmpeg build under `<build>/ffmpeg`, glslang's build |
| 5 | Generated build-system state | `<build>/CMakeCache.txt`, `<build>/build.ninja`, `<build>/CMakeFiles/` | CMake configure | Bakes absolute paths; only meaningful inside the same run |
| 6 | Final artifact | `android/app/build/outputs/apk/debug/app-debug.apk` | `package-android.sh` → Gradle | Uploaded as an artifact; never a cache |

## Which workflow writes which directory

| Path | CI Android Test | CI Android App |
|:--|:--|:--|
| Android SDK | `/home/runner/android-sdk` | `/home/runner/android-sdk` |
| Gradle home | — (no Gradle) | `~/.gradle` (`GRADLE_USER_HOME`) |
| Vendor downloads | `$RUNNER_TEMP/zhr-vendor` | `$RUNNER_TEMP/zhr-vendor` |
| ccache | `/home/runner/.ccache` | `/home/runner/.ccache` |
| Native build tree | `$RUNNER_TEMP/build-android-arm64` | `$WORKSPACE/build-android-arm64` |

The two workflows share the SDK, the vendor downloads and the ccache directory, and differ only in
where the native build tree lands. Because `build-android.sh` configures identically in both
(Release, arm64-v8a, API 28), the ccache key inputs are identical, so one workflow's saved ccache is
a valid restore for the other.

## Cache categories and the decision for each

The categories above map onto what a cache may hold, and each is decided on its own:

1. **Downloaded tools** — SDK/NDK: cached (large, immutable, expensive to fetch). `apt` tools: not
   cached (cheap, and the runner image is disposable by design).
2. **Downloaded source dependencies** — the `zhr-vendor` archives: cached by the existing
   `android-vendor-v1-*` key. They are immutable, so the key only needs to change when `vendor.sh`
   or a patch changes.
3. **Prebuilt dependency packages** — none exist for this port. Nothing to cache, and no reason to
   introduce a package manager for caching's sake.
4. **Compiler outputs** — ccache (the object cache) is the high-value entry, added by this work; the
   Ninja build tree itself is not cached (see below).
5. **Generated build-system state** — the CMake/Ninja build tree: **not cached.** `CMakeCache.txt`
   and `build.ninja` bake absolute paths and the toolchain location; restoring them into a different
   `$WORKSPACE`/`$RUNNER_TEMP` (the app and test workflows use different trees) is the class of
   failure the reference workflow hit with its renamed repository. ccache already captures the
   expensive part (the object files) without that risk.
6. **Final artifacts** — the APK: uploaded as an artifact, never cached.

## Why the compiler cache, and only it, was the gap

Before this work the run restored the SDK, the vendor downloads and the Gradle home, but the native
compile — the single longest step — had no cache at all, so every run recompiled every translation
unit from scratch. The build tree is not a safe cache target (category 5), and there are no prebuilt
packages (category 3); the correct cache for "expensive, reusable, path-independent compiler output"
is a compiler cache, which is ccache (category 4).

## What is deliberately not cached, and why

- **The CMake/Ninja build tree** — absolute paths and toolchain state; unsafe across the two
  different build-tree locations and across runner images. ccache gives the reuse without the risk.
- **The Gradle distribution** — owned by `gradle/actions/setup-gradle`; caching it separately would
  duplicate the action's own cache for the same directory.
- **The Android SDK/NDK licenses** — not secrets, but re-issued by `sdkmanager` on install; nothing
  to persist.
- **The FFmpeg and glslang build directories** — they live inside the (uncached) build tree; their
  object files are captured by ccache, and their outputs are cheap relative to the engine.
- **Any APK or game data** — artifacts and user data, not caches.
