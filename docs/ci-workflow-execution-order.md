# CI workflow execution order

This document records how the GitHub Actions workflows are staged, which validation command runs in
which stage, and why. It exists because a check's position must follow from its real prerequisites,
not from how cheap or how simple it looks. The rule the pipeline follows is:

> A check runs at the earliest stage where all of its prerequisites exist, and never earlier.

Nothing here changes what a check asserts. Every command, argument, environment variable, working
directory and exit-code behaviour is the one the workflow ran before; only the stage changed.

## Original workflow structure

Four workflows, all triggered on `pull_request`, plus per-workflow extras:

| Workflow | File | Jobs (before) | Triggers (before) |
|:--|:--|:--|:--|
| CI Android App | `ci-android-app.yml` | `android-app` (single job, 22 steps) | `workflow_dispatch`, `push` to `agent/android-arm64-application-port`, `pull_request` on `android/**` etc. |
| CI Android Test | `ci-android-test.yml` | `android-arm64` (single job, 26 steps) | `workflow_dispatch`, `push` to `android-arm64-port`, `pull_request` on `GeneralsMD/Code/**` etc. |
| Linux build and release | `linux-release.yml` | `build` (15 steps) → `packages` / `flatpak` → `package-check` → `assemble` → `release` | `pull_request`, `workflow_dispatch`, `push` to `main` and `v*` tags |
| Commit messages | `commit-messages.yml` | `check` (2 steps) | `pull_request` |

The structural problem was in the three build workflows: each was a **single job**, so every static
check sat at the top of the same step list as the multi-minute build. A one-character typo in a shell
script was discovered only after the SDK download, the vendored-source fetch, or (in `linux-release`)
a full apt install and two cache restores. The checks were not wrong, only late, and a failure could
not stop a build that had not started yet because there was nothing to stop.

Two ordering defects were more than cosmetic:

- In `ci-android-app.yml`, the "Assert the Gradle configuration and the packaged manifest" step ran
  **after** the native engine build and **after** APK packaging, although the step only needs Gradle,
  the SDK and the vendored SDL3 Java glue. A manifest error cost the whole native build first.
- In `linux-release.yml`, one "Validate CI helpers" step bundled three commands with three different
  prerequisite sets (`bash -n` needs nothing; `test_vendor_no_art.sh` needs bash/git/awk;
  `test_linux_release.py` needs zstd). They could not be scheduled independently.

## New workflow structure

The six stages are applied per workflow, and a workflow only gets the stages it has work for.

| Stage | Meaning | `ci-android-app.yml` | `ci-android-test.yml` | `linux-release.yml` |
|:--|:--|:--|:--|:--|
| 1 Preliminary validation | No build output, no installed dependency | `preflight` | `preflight` | `preflight` |
| 2 Dependency preparation | JDK, SDK, NDK, Gradle, apt, caches | `android-app` (head) | `android-arm64` (head) | `build` (head) |
| 3 Pre-compilation dependency checks | Installed dependency, no compiled output | `android-app` | `android-arm64` | `dependency-checks` |
| 4 Native and Android build | Compiles and links | `android-app` | `android-arm64` | `build` |
| 5 Post-build testing | Needs compiled/generated output | `android-app` | `android-arm64` | `build` (CTest) |
| 6 Artifact validation and publishing | Only after stage 5 passes | `android-app` upload | `android-arm64` upload | `packages`, `flatpak`, `package-check`, `assemble`, `release` |

Stages 2–6 stay in one job where they share an expensive, non-transferable prerequisite (the native
build tree, the ccache, the Gradle home). Splitting those across jobs would force a second SDK setup
and a second native build, which the brief explicitly forbids. Stages are therefore **steps** inside
those jobs, in order, and the job boundary is drawn where an artifact handoff already existed.

Step counts after the change: `ci-android-app.yml` `preflight` 3 + `android-app` 20 (was 22);
`ci-android-test.yml` `preflight` 3 + `android-arm64` 24 (was 26); `linux-release.yml` `preflight` 3 +
`dependency-checks` 3 + `build` 14 (was 15); `commit-messages.yml` unchanged at 2.

### Job dependencies (after)

```
ci-android-app.yml      preflight ──▶ android-app
ci-android-test.yml     preflight ──▶ android-arm64
linux-release.yml       preflight ──▶ dependency-checks ──▶ build ──▶ packages ─┐
                                                                  ├──▶ flatpak ──┼──▶ assemble ──▶ release
                                                                  └──▶ package-check ─┘
commit-messages.yml     check        (unchanged: one job, already stage-1 only)
```

`packages` and `flatpak` still run in parallel after `build`; `package-check` still depends only on
`packages`; `assemble` still waits for all three; `release` still waits for `assemble` and still runs
only for a `v*` tag. No new serialisation was introduced.

### Artifact handoff (unchanged)

| From | Artifact | To | Gate |
|:--|:--|:--|:--|
| `build` | `linux-x86_64-portable` | `packages`, `flatpak` | `if-no-files-found: error`; upload is the last step of `build`, so it only runs if CTest passed |
| `packages` | `linux-native-packages` | `package-check`, `assemble` | `if-no-files-found: error` |
| `flatpak` | `linux-flatpak-package` | `assemble` | `if-no-files-found: error` |
| `assemble` | `linux-x86_64-packages` | `release` | `if-no-files-found: error` |
| `android-app` | `android-app-apk` | humans (documented in `android/README.md`) | `if: always()` diagnostics, as before |
| `android-arm64` | `android-arm64-diagnostics` | humans | `if: always()` diagnostics, as before |

The two Android `if: always()` uploads are deliberately still unconditional: they carry failure
diagnostics. They are uploads of logs, not publishing, and the `android-app-apk` upload has never been
a release gate. Making them conditional would delete failure evidence.

## Complete test classification

Every validation command that existed before this change, with its real prerequisites, its stage, and
the reason. "Category" is the brief's A / B / C classification.

### `ci-android-app.yml`

| Check / Test | Required prerequisites | Assigned stage | Category | Reason |
|:--|:--|:--|:--|:--|
| `assert-cache-policy.sh ci-android-app.yml` | bash + awk, repository text | 1 `preflight` | A | Reads the workflow file; needs no toolchain and no output |
| `bash -n package-android.sh`; `package-android.sh --help` | bash; script only parses/exits before any tool lookup | 1 `preflight` | A | `--help` returns before the SDK/NDK/engine checks |
| `setup-java` (JDK 17) | — (dependency install) | 2 `android-app` | — | Toolchain preparation, not a check |
| `actions/cache` SDK/NDK restore | — | 2 `android-app` | — | Dependency preparation |
| `sdkmanager --install ndk/platforms/build-tools/platform-tools` | network, JDK | 2 `android-app` | — | Dependency preparation; skipped on cache hit |
| `gradle/actions/setup-gradle` (8.12) | — | 2 `android-app` | — | Dependency preparation |
| "Export the Android environment" (NDK/SDK dir asserts) | installed SDK/NDK | 2 `android-app` | B | Asserts the install itself succeeded |
| vendored download cache + ccache restore | — | 2 `android-app` | — | Dependency preparation |
| "Report the restored ccache state" | ccache installed | 2 `android-app` | B | Configures the cache before it is used |
| `vendor.sh --no-art` + vendored-source asserts | network, git, curl, tar | 2 `android-app` | B | Source dependency fetch |
| **`gradle :app:processDebugMainManifest` + merged-manifest asserts** | JDK, SDK platform 35, build-tools, Gradle 8.12, vendored SDL3 Java glue | **3 `android-app`, moved before the native build** | B | Needs dependencies but **no** compiled native output; previously ran after the engine build and APK packaging |
| `build-android.sh --config Release --build-dir … --target generals` | NDK, cmake, ninja, ccache, vendored sources | 4 `android-app` | C | The native build itself |
| `ccache -s -v` statistics | the build above | 5 `android-app` | C | Reports the hit rate of that build |
| "Audit the native dependencies from the ELF" (readelf/nm on `libmain.so`) | `libmain.so` | 5 `android-app` | C | Inspects a compiled shared object |
| `package-android.sh --build-dir …` (stages libs, runs `assembleDebug`) | `libmain.so`, NDK `libc++_shared.so`, SDL3 glue, Gradle | 4 `android-app` | C | Produces the APK |
| `gradle :app:testDebugUnitTest` + JUnit report asserts | compiled Java/Kotlin app classes | 5 `android-app` | C | JVM unit tests against compiled project classes |
| "Inspect the packaged APK" (unzip, readelf, aapt2) | `app-debug.apk`, NDK readelf, build-tools aapt2 | 5 `android-app` | C | APK structure, ABI, DT_NEEDED, badging |
| Upload `android-app-apk` | the APK + logs | 6 `android-app` | C | Publishing step; `if: always()` for diagnostics |
| `actions/cache/save` ccache | — | 6 `android-app` | — | Cache maintenance, `if: always()` |

### `ci-android-test.yml`

| Check / Test | Required prerequisites | Assigned stage | Category | Reason |
|:--|:--|:--|:--|:--|
| `assert-cache-policy.sh ci-android-test.yml` | bash + awk, repository text | 1 `preflight` | A | Reads the workflow file |
| `bash -n build-android.sh`; `build-android.sh --help` | bash | 1 `preflight` | A | `--help` returns before the SDK/NDK checks |
| "Prepare the ephemeral runner" (apt: cmake, ninja, git, curl, ccache, JDK) | — | 2 `android-arm64` | — | Dependency preparation |
| **"Platform and architecture detection matrices" (`cmake -P` ×2)** | **`cmake` only** | **3 `android-arm64`, moved before the SDK download** | B | Pure `cmake -P` scripts: no compiler, no toolchain, no vendored sources. Previously ran after the SDK/NDK install and the vendored fetch |
| **"Android is its own platform and arm64-v8a is AArch64"** | **`cmake` only** | **3 `android-arm64`, moved with the matrices** | B | Same: includes `DetectPlatform.cmake`/`DetectArchitecture.cmake` and asserts the flags |
| SDK/NDK cache + `sdkmanager` install | network, JDK | 2 `android-arm64` | — | Dependency preparation |
| "Export the Android environment" | installed NDK/SDK | 2 `android-arm64` | B | Asserts the install succeeded |
| vendored cache + `vendor.sh --no-art` | network, git, curl, tar | 2 `android-arm64` | B | Source dependency fetch |
| ccache restore / "Report the restored ccache state" | ccache | 2 `android-arm64` | B | Configures the cache before use |
| `build-android.sh --no-build` (configure) | NDK, cmake, ninja, vendored sources | 4 `android-arm64` | C | CMake configure produces `CMakeCache.txt` |
| "Assert the toolchain, ABI, API and STL" | configure output, NDK compiler | 5 `android-arm64` | C | Reads `CMakeCache.txt`/`CMakeCXXCompiler.cmake`; compiles a probe object |
| "Assert the desktop-Linux dependencies are absent…" | configure output (`build.ninja`) | 5 `android-arm64` | C | Greps the generated Ninja files |
| "Report the dependency compatibility result" | — | 5 `android-arm64` | C | Step summary; kept adjacent to the asserts it reports |
| "Compile a bounded set of targets under the NDK" | configure output, NDK | 4 `android-arm64` | C | Compiles real targets |
| "Assert the SDL3 Vulkan GPU backend is compiled" | generated `SDL_build_config.h`, `build.ninja` | 5 `android-arm64` | C | Reads generated build config |
| "Assert the native touch layer compiled (Phase 4)" | compiled `SdlTouch.cpp.o` | 5 `android-arm64` | C | Inspects an object file |
| `cmake --build … --target generals` (link `libmain.so`) | configure output, NDK, all vendored deps | 4 `android-arm64` | C | The engine link |
| `ccache -s -v` statistics | the link above | 5 `android-arm64` | C | Reports the build's hit rate |
| "Assert libmain.so is an AArch64 shared object…" | `libmain.so` | 5 `android-arm64` | C | ELF/DT_NEEDED inspection |
| "Assert the Android audio, storage and game-services layer compiled" | objects from the link | 5 `android-arm64` | C | Symbol inspection of compiled objects |
| Upload `android-arm64-diagnostics` | build logs | 6 `android-arm64` | C | `if: always()` diagnostics |
| `actions/cache/save` ccache | — | 6 `android-arm64` | — | Cache maintenance, `if: always()` |

### `linux-release.yml`

| Check / Test | Required prerequisites | Assigned stage | Category | Reason |
|:--|:--|:--|:--|:--|
| **`bash -n linux-ci-tools.sh linux-{portable,packages,packages-check,check}.sh`** | **bash** | **1 `preflight`** | A | Parses shell scripts; no package, no build. Was bundled with two unrelated checks |
| **`test_vendor_no_art.sh`** | **bash, git, awk, `mktemp`, tar** (curl/sha256sum stubbed by the test) | **1 `preflight`** | A | Drives `vendor.sh` on a synthetic tree; no network, no game art, no compiled output |
| **`test_linux_release.py`** | **python3 + `zstd` binary** (exits immediately without zstd) | **3 `dependency-checks`** (new job; apt installs zstd) | B | Needs an installed tool, not a build; also asserts real tar/zstd behaviour |
| apt install (runner preparation) | — | 2 `build` | — | Dependency preparation |
| vendor download cache / CMake distribution cache | — | 2 `build` | — | Dependency preparation |
| `vendor.sh --no-art` + `linux-ci-tools.sh cmake` | network | 2 `build` | B | Source dependency + verified CMake fetch |
| `docker pull` Sniper SDK → digest | docker | 2 `build` | B | Toolchain image resolution |
| "Check filesystem compatibility" (configure + 3 targets + ctest subset) | Sniper image, CMake | 4→5 `build` | C | Compiles and runs 3 unit tests early, by design |
| `linux-portable.sh --build …` + `linux-release.py audit-portable` | Sniper image, built engine | 5 `build` | C | Audits the built portable folder (ABI/deps/licence) |
| `linux-release.py verify-tag` (tags only) | portable `VERSION` | 5 `build` | C | Compares the built version to the tag |
| "Build tests incrementally" (configure + build all) | Sniper image, build tree | 4 `build` | C | Compiles the test binaries |
| CTest with Lavapipe/Xvfb | compiled test binaries, mesa, xvfb | 5 `build` | C | Runtime tests |
| `linux-release.py ctest-summary` | `ctest.xml` | 5 `build` | C | Requires the Lavapipe checks to have passed |
| `linux-portable.sh --no-build` (handoff) | tested binaries | 6 `build` | C | Re-runs the ABI/licence gates, then tars |
| Upload `linux-x86_64-portable` | the archive | 6 `build` | C | Handoff artifact |
| `linux-packages.sh` (appimage/deb/rpm/arch) + `verify-checksums` | `linux-x86_64-portable` | 6 `packages` | C | Packages the tested binary; no recompile |
| `linux-packages.sh --formats flatpak` + `verify-checksums` | portable archive, Flatpak runtime cache | 6 `flatpak` | C | Builds the Flatpak from the same archive |
| `linux-packages-check.sh` (install + missing-data startup) | built packages | 6 `package-check` | C | Installs and runs the packaged app per distro |
| `linux-release.py assemble` | all package artifacts | 6 `assemble` | C | Requires six formats and one identical engine archive |
| `sha256sum --check`, `gh release …` | assembled set, `contents: write` | 6 `release` | C | Publishes only after everything above passed |

### `commit-messages.yml`

| Check / Test | Required prerequisites | Assigned stage | Category | Reason |
|:--|:--|:--|:--|:--|
| Commit-subject format over `BASE..HEAD` | `actions/checkout` with `fetch-depth: 0` | 1 (its only job) | A | Pure git-log text check; already independent and already gated |

## Changes to execution order

Only the following commands changed position. None changed text.

| Command | Before | After |
|:--|:--|:--|
| `assert-cache-policy.sh` (`ci-android-app.yml`) | step 3 of `android-app`, after the runner apt install | `preflight` job, before any install |
| `bash -n package-android.sh` / `--help` | step 13, after SDK/NDK/Gradle/vendor | `preflight` job |
| `gradle :app:processDebugMainManifest` + manifest asserts | after the engine build **and** after APK packaging | after dependency prep, **before** the native build |
| `assert-cache-policy.sh` (`ci-android-test.yml`) | after the runner apt install | `preflight` job |
| `bash -n build-android.sh` / `--help` | after SDK/NDK install | `preflight` job |
| `cmake -P` detection matrices + platform assert | after SDK/NDK install and `vendor.sh` | after the apt install (cmake), before the SDK download and `vendor.sh` |
| `bash -n` CI helper scripts | bundled in "Validate CI helpers" in `build` | `preflight` job |
| `test_vendor_no_art.sh` | bundled in "Validate CI helpers" in `build` | `preflight` job |
| `test_linux_release.py` | bundled in "Validate CI helpers" in `build` | `dependency-checks` job (installs zstd) |

## Failure handling

- A `preflight` failure marks `android-app`, `android-arm64` and `build` as `skipped`, so the expensive
  work never starts. That is GitHub Actions' default `needs` behaviour; no `if:` was added to soften it.
- A `dependency-checks` failure likewise blocks `build`; the failing job is named "Pre-compilation
  dependency checks" so the log identifies the stage.
- A build failure blocks every dependent test job, exactly as before.
- A CTest or post-build assertion failure still prevents `linux-x86_64-portable` from being uploaded,
  because the upload remains the last step of `build`.
- **No `continue-on-error: true` was added anywhere.** The two `if: always()` uploads predate this
  change and only collect diagnostics; `if: always()` on the ccache save is cache maintenance. Neither
  can turn a failure into a pass, and neither was touched.

## Remaining limitations

1. **Stages 2–6 are steps, not jobs, in the Android workflows.** The native build tree, the ccache and
   the Gradle home cannot cross a job boundary without either re-downloading the SDK or rebuilding the
   engine. Splitting them would violate "no unnecessary duplicate build", so the boundary is drawn
   where an artifact handoff already existed. A failure inside `android-app` therefore reports one job
   name; the step name identifies the stage.
2. **`test_vendor_no_art.sh` fails on this branch, before and after this change.** The fixture it
   builds under `$TMPDIR` copies `vendor.sh` but not the `Libraries/Source/*.patch` files, so
   `install_gamespy_android_thread_patch` cannot find `gamespy-android-thread.patch` and reports
   "did not apply". Reproduced locally with `bash GeneralsMD/Code/Tests/test_vendor_no_art.sh` and
   visible in run 44148144123. This is a pre-existing fixture defect, not a pipeline defect; fixing it
   would change what a test asserts, which this task must not do. Because it is now a Stage 1 gate, it
   will block `linux-release` `build` until the fixture is fixed — the same outcome as before, only
   earlier and cheaper.
3. **`linux-release.yml` still needs Docker and network** for the Sniper image; no static stage can
   cover the ABI/linker gates, because they read the built binary.
4. **The Android workflows' `pull_request` path filters are unchanged.** They were already narrow, and
   a Stage 1 gate can only run when the workflow itself triggers. Widening the filters would change
   when the workflows run, which the brief asks not to do.
5. **No new caching was introduced**; `assert-cache-policy.sh` still validates every cache path, and
   the only paths are the SDK/NDK, the vendored downloads and ccache.
6. **Workflow triggers, `permissions:`, `concurrency:` and every action pin are unchanged.**

## Verification performed

- `actionlint 1.7.7` over all four workflow files: clean, no diagnostics.
- YAML parse of all four files; every `needs:` target resolves to a real job.
- A command-set diff against `HEAD`: all 14 / 20 / 19 / 1 `run:` blocks still present, no line lost,
  and the only new lines are the `zstd` install in `dependency-checks`.
- `scripts/build/android/assert-cache-policy.sh` on both Android workflows: passes (8 cache paths).
- `bash -n` and `--help` for `build-android.sh` and `package-android.sh` run locally: pass.
- `git diff --check`: clean.

## GitHub Actions execution results

Dispatched on `agent/ci-dependency-aware-pipeline` at commit `3cd8224` (all three build workflows
have narrow `push` triggers, so `workflow_dispatch` is how they run on a development branch):

| Run | Workflow | Result | Evidence the staging works |
|:--|:--|:--|:--|
| [44703124037](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44703124037) | CI Android App | **success** | `preflight` green in ~30 s; then "Assert the Gradle configuration and the packaged manifest" **passed before** "Build the native engine" — the reordering is real, and the manifest check no longer waits for the engine. Post-build stages then ran in order: 98 unit tests in 11 suites, 0 failures, APK inspection green |
| [44703183164](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44703183164) | CI Android Test | **success** | `preflight` green; "Platform and architecture detection matrices" and "Android is its own platform and arm64-v8a is AArch64" ran **immediately after** the apt install and **before** the SDK/NDK download and `vendor.sh`, then configure/compile/link/assert all passed |
| [44703187551](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44703187551) | Linux build and release | **failure (pre-existing)** | The new `preflight` job passed `bash -n`, then failed `test_vendor_no_art.sh` in 17 s with the pre-existing fixture defect (limitation 2). `dependency-checks`, `build`, `packages`, `flatpak`, `package-check`, `assemble` and `release` were all **skipped** — the gate stopped the pipeline instead of letting a full apt install, a Docker pull and the Sniper build run first, which is exactly the intended behaviour |

The `linux-release` failure is the pre-existing `test_vendor_no_art.sh` defect, unchanged by this work:
it fails identically on the base branch (run 44148144123, step "Validate CI helpers"). Fixing it would
mean changing what the test asserts, which is out of scope here. The value of the change is visible in
the timing: 17 s to fail instead of after the runner preparation and both cache restores.
