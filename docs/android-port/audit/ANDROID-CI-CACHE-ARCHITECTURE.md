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

## Cache policy: infrastructure only, never an application output

The rule the two workflows enforce, stated once:

> A cache may hold only build infrastructure that is reusable independently of the application's
> changing source. It must never hold an application output.

Cached (the complete allow-list):

| Cache | Path | Key | Saved by |
|:--|:--|:--|:--|
| SDK + NDK | `${{ env.ANDROID_SDK_ROOT }}` | `android-sdk-cmdline…-ndk…-p…-n…` | `actions/cache` post-step (success) |
| Vendored downloads | `${{ runner.temp }}/zhr-vendor` | `android-vendor-v1-<hash>` | `actions/cache` post-step (success) |
| Compiler objects | `${{ env.CCACHE_DIR }}` (`/home/runner/.ccache`) | `android-ccache-…-<run_id>-<run_attempt>` | `actions/cache/save` with `if: always()` |
| Gradle deps | (owned by `gradle/actions/setup-gradle`) | (the action's own) | `gradle/actions/setup-gradle` |

Explicitly excluded — nothing may store, restore or reuse these:

- `libmain.so` and any other application-built native shared library.
- The CMake/Ninja build tree (`build-android-arm64`, `$RUNNER_TEMP/build-android-arm64`) — including
  the FFmpeg/glslang sub-builds and every object file under it.
- Application object files outside the dedicated ccache storage.
- Intermediate native linking outputs.
- Generated APKs and AABs (`android/app/build/outputs/**`).
- Staged `android/app/src/main/jniLibs/**` and `android/java-sdl/**`.

### Why `libmain.so` must be rebuilt every run

`libmain.so` is the application. If a cache could restore it, a later run could package a binary
built from older source — the code would not match the checkout, and the APK would ship stale
behaviour with no build error to show for it. So the engine is always configured from the current
source, compiled (ccache may reuse a valid object) and **linked afresh**. The build tree is not
cached, and each engine build step deletes any existing `libmain.so` first and asserts the file it
finds is newer than the step's own start, so "the engine exists" can never be satisfied by an older
artifact.

### ccache objects vs the final linked binary — the distinction

This is the crux, and it is why the policy does not sacrifice the warm-build win:

- **ccache objects** are *per-translation-unit* compiler outputs, stored and restored **only inside
  `$CCACHE_DIR`**. A hit is a *content match*: ccache re-runs the compiler and compares the
  preprocessed source, flags and compiler identity (with `compiler_check=content`), and returns the
  cached object only when they are identical. A changed source file is a miss and recompiles.
- **The final `libmain.so`** is a *link* output. It is never stored in any cache. The link runs in
  every build, consuming the (possibly cached) objects and the current build tree, and produces the
  binary that is staged and packaged.

So reusing ccache objects is safe — they are inputs whose validity ccache re-establishes — while the
linked application binary is always produced from the current source. Removing ccache would only
make the build slower, not more correct.

### How a future developer avoids introducing output caching

1. The workflows run `scripts/build/android/assert-cache-policy.sh <workflow>` as an early step. It
   reads the workflow file and fails the run if any `actions/cache*` step targets a path outside the
   allow-list, or uses a multi-path block scalar (`path: |`) that could hide an output directory.
2. To cache a new *infrastructure* directory, add it to the script's `allowed_paths` array **and**
   justify it in this document — deliberately, not by adding a `path:` line alone.
3. Never add `build-android-arm64`, `jniLibs`, `java-sdl`, `outputs/`, `*.apk` or `*.aab` to any
   cache `path:`; the guard rejects them.
