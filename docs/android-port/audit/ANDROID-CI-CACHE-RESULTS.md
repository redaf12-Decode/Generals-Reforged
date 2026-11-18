# Android CI cache architecture — results

What was adapted from the reference Android workflow, what was implemented here, what was
deliberately not, and what is proven versus still unverified. It distinguishes implemented caching
from empirically verified behaviour: the numbers below come from actual GitHub Actions runs, and
nothing is invented.

- Target: `redaf12-Decode/Generals-Reforged`, branch `agent/android-arm64-application-port`.
- Reference (read only): `redaF12-Tech/GeneralsZH-Android-Port`, `main`,
  `.github/workflows/build-android.yml`.

## Reference workflow analysis

The reference is a CMake-preset + vcpkg + DXVK + meson build. Its storage architecture:

| Mechanism | Reference | Applies to this port? |
|:--|:--|:--|
| Android SDK/NDK | `actions/cache/restore`+`save` of `/opt/android-sdk`, key `android-sdk-<cmdline>-ndk<ver>-p35-bt35`, install only on miss | The target already caches the SDK the same way (under `$HOME` instead of `/opt`, to avoid `sudo` on restore). Kept as is. |
| ccache | `~/.cache/ccache`, `actions/cache/restore`+`save`, key `ccache-android-<preset>-<run_id>` with `restore-keys: ccache-android-<preset>-`, `ccache --set-config=max_size=3G compiler_check=content`, save `if: always()` | **Adapted** (see below). The reason it exists in the reference — "the z_generals target alone is ~1.6M LOC and was being fully recompiled from scratch on every run" — is exactly this port's problem. |
| vcpkg | `/opt/vcpkg` + `~/.cache/vcpkg`, pinned `VCPKG_COMMIT`, binary package cache | **Not applicable.** This port has no vcpkg; every dependency is built from a vendored source tree by `Tools/vendor.sh` and CMake. Introducing vcpkg would replace the dependency architecture, which is forbidden. |
| Gradle | `gradle/actions/setup-gradle@v4` | The target already uses it; the home cache is the action's. |
| DXVK meson build dir | `actions/cache/restore`+`save` of `build/<preset>/_deps/dxvk-build-android`, key includes `github.repository` because meson bakes the absolute checkout path | **Not applicable** (no DXVK, no meson). The underlying lesson — never restore a generated build directory that bakes absolute paths — is why the target's CMake/Ninja tree is not cached. |
| Explicit restore/save with `if: always()` | Plain `actions/cache` only saves on a successful job; the reference is expected to fail while bringing the engine up, so it uses explicit pairs | **Adapted** for ccache — see the failure this fixed below. |

The reference's DXVK, vcpkg and meson mechanisms are all inapplicable here; only the ccache design
and the restore/save discipline carry over. Nothing was copied wholesale.

## Target build architecture and cache directories

Full inventory: `docs/android-port/audit/ANDROID-CI-CACHE-ARCHITECTURE.md`. Summary of the
directories, by category:

| Category | Path | Cached? |
|:--|:--|:--|
| Downloaded tool (SDK/NDK) | `/home/runner/android-sdk` (`.../ndk/27.2.12479018`) | Yes (existing `android-sdk-*`) |
| Downloaded tool (Gradle) | Gradle distribution + `~/.gradle` | Yes (existing `gradle/actions/setup-gradle`) |
| Downloaded source dependency | `$RUNNER_TEMP/zhr-vendor` (SDL3, FFmpeg, glslang, FreeType, mbedTLS, …) | Yes (existing `android-vendor-v1-*`) |
| Prebuilt dependency package | none | — |
| Compiler output | `/home/runner/.ccache` | Yes (**new** `android-ccache-*`) |
| Generated build-system state | `<build>/CMakeCache.txt`, `build.ninja` | No (absolute paths; unsafe) |
| Final artifact | `android/app/build/outputs/apk/debug/app-debug.apk` | No (artifact, not a cache) |

## Cache key design and invalidation

ccache key (both workflows, identical inputs so either can restore the other's):

```
android-ccache-<runner.os>-<runner.arch>-ndk<NDK_VERSION>-<NATIVE_ABI>-<NATIVE_BUILD_TYPE>-<hash of build-android.sh + android.toolchain.cmake>-<github.run_id>-<github.run_attempt>
```

- **Primary key** carries `github.run_id`/`run_attempt`, so it is unique per run and never collides
  with an existing entry; `actions/cache/save` always writes.
- **restore-keys**, longest first:
  - `…-<script hash>-` (same toolchain, script changed since the saved run),
  - `…-<ABI>-<build type>-` (same toolchain, script differs),
  - `…-<ABI>-` (same NDK/ABI).
- **Invalidation inputs**: runner OS/arch (different libc/Clang changes codegen), NDK version
  (compiler + sysroot), ABI, build type, and the build script/toolchain file (their flags).
- **Not in the key**: ordinary source files. A ccache entry is a content match on the preprocessed
  source plus the compiler and flags, so editing one file recompiles that translation unit and hits
  the rest — exactly the behaviour required.

The vendor and SDK caches keep their existing keys (`android-vendor-v1-<hash of vendor.sh + patches>`,
`android-sdk-cmdline<…>-ndk<…>-p<…>-n<…>`); they are unchanged.

## Implementation

Files modified:

- `.github/workflows/ci-android-app.yml`
- `.github/workflows/ci-android-test.yml`

(plus the earlier ccache wiring in `scripts/build/android/build-android.sh` from the preceding
commit, and the two new audit docs).

Changes in this commit:

1. **ccache restore/save pair.** The ccache directory is restored with `actions/cache/restore`
   (unique per-run key + prefix `restore-keys`) and saved with `actions/cache/save` under
   `if: always()`, in both workflows. The save key matches the restore key exactly.
2. **`compiler_check=content`.** Added to the ccache configuration, for reliable hits across
   checkouts of a cross-compile (the reference sets it for the same reason).
3. **`actions/cache/restore@v6.1.0` and `actions/cache/save@v6.1.0`**, pinned to the same commit SHA
   as the existing `actions/cache@v6.1.0` uses.

Why the restore/save pair: with plain `actions/cache`, the automatic post-step save runs only when
the job succeeds. A push run on this branch was cancelled at ~97% of the native build
(`[1576/1631]`) and the run right before it was cancelled at ~8 min — in both cases the ccache was
**not saved** ("Post Restore the ccache compiler cache: skipped"). The explicit pair with
`if: always()` keeps whatever compiled, which is the whole point of a compiler cache on a job that
can be cancelled mid-build.

## Cache restoration and saving configuration (order)

Both workflows restore in the same order, before the build tools run:

1. Checkout.
2. Prepare runner; `apt-get install … ccache`.
3. Restore the Android SDK/NDK cache; install only on miss.
4. (App only) `gradle/actions/setup-gradle`.
5. Restore the vendor-download cache.
6. **Restore the ccache cache.**
7. **Report/configure ccache** (`max_size`, `compression`, `compiler_check=content`, `--zero-stats`).
8. Fetch code dependencies; configure CMake (with `CMAKE_<LANG>_COMPILER_LAUNCHER=ccache`).
9. Build native; print ccache statistics; audit the ELF; package the APK; run unit tests.
10. Upload artifacts.
11. **Save the ccache cache** (`if: always()`).

## Static configuration review

- Both workflow files parse as valid YAML (`yaml.safe_load`).
- The pinned SHA `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` is `actions/cache@6.1.0`, and its
  `restore/action.yml` and `save/action.yml` both resolve (HTTP 200) — the sub-actions exist at that
  commit.
- Restore keys and the save key in each workflow are byte-for-byte identical.
- `CCACHE_DIR` (`/home/runner/.ccache`) is under `$HOME`, so `actions/cache` reads and writes it
  without `sudo`.
- No source hash is in the ccache key; no secret, signing key or APK is cached.

## Proven (from actual runs)

The compiler cache mechanism was already exercised by the preceding commit
(`43c4319`), whose runs are real evidence:

- **Cold run** — CI Android Test, run
  [44356952608](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44356952608):
  `Cache not found for input keys: android-ccache-…`; then `Cacheable calls: 1384/1596`,
  `Hits: 1/1384 (0.07%)`, `Misses: 1383/1384`; `Cache saved with key: android-ccache-…-98ca1f76…`.
- **Warm run** — CI Android Test, run
  [44363168205](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44363168205):
  `Cache restored from key: android-ccache-…-98ca1f76…`; `Hits: 1382/1384 (99.86%)`,
  `Misses: 2/1384`; total run **4m06s**.
- The pre-ccache CI Android Test run
  [44349584568](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44349584568) took
  **25m52s** (native compile step 17:22:41→17:37:31 plus the engine link).

So a warm compiler cache reduces the engine build from a full rebuild to near-instant, and the
compiler cache is genuinely used (the hit rate is read from `ccache -s`, not assumed).

The explicit restore/save pair (this commit) was exercised by CI Android App run
[44366426888](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44366426888) —
the **first successful CI Android App run on this branch**, and the first to reach and pass the
save step:

- The run is `success`; every step, including "Build the native engine as libmain.so",
  "Package the APK", "Run the game-folder unit tests" and "Inspect the packaged APK", passed.
- "Restore the ccache compiler cache" ran and reported
  `Cache not found for input keys: …-44366426888-1, …98ca1f76…-, …-Release-, …-arm64-v8a-`.
  It missed for a benign, one-time reason: the earlier entry had been saved by commit `43c4319`
  under the **old** key format `…-98ca1f76…` (no run-id suffix), and the new prefix `restore-keys`
  end in `-`, which a key without that trailing segment cannot match. The entry this run saved is in
  the new format, so the next run's first restore-key (`…98ca1f76…-`) matches it.
- "Save the ccache compiler cache" ran with `if: always()` and reported
  `Cache saved with key: android-ccache-Linux-X64-ndk27.2.12479018-arm64-v8a-Release-98ca1f76…-44366426888-1`
  (172 MB). This is the behaviour the explicit pair exists for: the save fires regardless of job
  outcome, and a unique key means it always writes.
- The native build ran cold (restore missed): `Cacheable calls: 1376/1588`, `Hits: 0/1376 (0.00%)`,
  `Misses: 1376/1376`, from 18:11:52 to 18:33:29.

`gh api …/actions/caches` confirms the new entry
(`android-ccache-…-98ca1f76…-44366426888-1`, `ref=refs/heads/agent/android-arm64-application-port`)
is stored, and the SDK (`android-sdk-cmdline13114758-ndk27.2.12479018-p35-n28`), vendor
(`android-vendor-v1-ff8704…`) and Gradle (`gradle-*`, `gradle-home-v1|…`) caches are all present.

The next CI Android App run
[44372296090](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44372296090) (fired
by the existing push trigger, commit `8434c95`) closed the loop — it restored that new-format entry
and hit it:

- `Cache restored from key: android-ccache-…-98ca1f76…-44366426888-1`.
- Native build `Hits: 1374/1376 (99.85%)`, `Misses: 2/1376`, and the build step ran **2m48s**
  (18:44:01→18:46:49) against the cold run's **21m37s** (18:11:52→18:33:29) — a **7.7×** faster
  native build.
- Whole run **7m32s**, against the cold run's **27m20s**.
- "Save the ccache compiler cache" ran again and wrote `…-44372296090-1`.

So the design is proven on the app workflow itself: restore → hit → build → save, with the warm
native build reduced from ~22 min to under 3 min.

## Implemented but not yet empirically verified

- **`compiler_check=content`** — set, but its effect (reliable hits across checkouts) is not
  separately measured; the hits above were obtained with it on, but not compared against a run with
  it off.

## Remaining limitations

- The CI Android App workflow sets `concurrency: { group: ci-android-app-${{ github.ref }},
  cancel-in-progress: true }`, so a newer push to this branch cancels an in-flight app run. The two
  earlier app runs cancelled inside the native build (one at ~97% of the engine, `[1576/1631]`, one
  at ~8 min) were cancelled by a subsequent push on the same branch, not by a workflow or runner
  fault. The explicit save pair is the mitigation: a cancelled run now keeps its compiled objects
  instead of losing them (with plain `actions/cache`, a cancelled app run saved nothing).
- The first run on a new key format restores nothing (a key saved under a different format does not
  match a `…-` restore-key), so a key-format change costs one cold build before warm hits resume.
- GitHub caches are evicted after 7 days of no access; the ccache is an optimization and a fully
  cold run still builds correctly (proven above).
- ccache stores per-ABI, per-NDK objects; the key prevents an incompatible restore, so a genuinely
  different toolchain simply misses and recompiles.

## Intentionally excluded

- **vcpkg** — the target does not use it; introducing it would replace the vendored-source
  dependency architecture.
- **DXVK / meson build directory** — the target uses SDL3 GPU with native Vulkan, no DXVK, no meson.
- **The CMake/Ninja build tree** — bakes absolute paths; the reference's renamed-repository incident
  is the cautionary case. ccache captures the valuable output without that risk.
- **A separate Gradle cache** — `gradle/actions/setup-gradle` owns it; a second mechanism over the
  same directory would duplicate it.
- **The SDK under `/opt`** — the target keeps it under `$HOME` so the cache needs no `sudo`.

## Commit

- `80fb8d5` — "ci(android): persist ccache with an explicit restore/save pair"
  (https://github.com/redaf12-Decode/Generals-Reforged/commit/80fb8d5)
- Preceded by `43c4319` — "ci(android): cache the native compile with ccache; fix the Gradle cache
  never saving" (https://github.com/redaf12-Decode/Generals-Reforged/commit/43c4319)

## CI results

| Run | Workflow | Commit | Result | Cache evidence |
|:--|:--|:--|:--|:--|
| [44349584568](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44349584568) | CI Android Test | `043d04c` (pre-ccache) | success, 25m52s | no ccache |
| [44356952608](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44356952608) | CI Android Test | `43c4319` | success, 22m42s | cold: 1/1384 hits; saved `…-98ca1f76…` |
| [44363168205](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44363168205) | CI Android Test | `43c4319` | success, **4m06s** | warm: **1382/1384 hits** |
| [44366426888](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44366426888) | CI Android App | `68a15c6` | **success**, 27m20s | restore miss (old-format key); save ran: `…-44366426888-1` |
| [44372296090](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44372296090) | CI Android App | `8434c95` | **success**, **7m32s** | restore hit `…-44366426888-1`; **1374/1376 hits**; native build 2m48s |

The `44366426888` app run is the first **successful** CI Android App run on this branch and the
first run of the restore/save pair; it passed every step and saved the ccache under the new key. The
`44372296090` run then restored that entry and rebuilt the engine in 2m48s instead of 21m37s.
