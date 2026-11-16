# 06 — Testing strategy: where the 131 tests run for the Android port

Resolves **question 6 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("Are the tests (131 `add_test` targets) expected to run under an Android emulator, or only on
desktop, given several require a GPU and game data?"). Read-only: no source, build or dependency
file was changed.

## Decision

**The 131 tests stay a desktop-CI gate; they do not run under an Android emulator.** Android adds a
**small, separate, device/emulator smoke set** (Phase 2) that checks the Android-only glue (the
`libmain.so` load, startup, window/surface, input, the folder picker), not the 131. The desktop
suite continues to guard the shared engine, renderer, filesystem and network code that Android
reuses, so a regression that would break Android is caught on desktop before it ships.

## Evidence

### E1 — The suite is large and mixed, and many tests need a GPU and/or game data

`GeneralsMD/Code/CMakeLists.txt` registers **131** `add_test(...)` targets. They are not uniform:

- **Data-dependent tests** are explicitly written to be **Skipped (77)**, not failed, when the game
  data is absent: `gametext_csf` (`CMakeLists.txt:1316,1374-1375`), `test_install_textures`
  (`:1855,1876-1877`), `test_render_hooks` (`:1882-1888`), the Miles/audio test (`:2182-2185`), each
  gated on `ZH_GAME_DATA` and given `ZH_DATA_DIR`.
- **GPU-dependent tests** (the SDL3-GPU renderer, water, particles) need a real or software Vulkan
  device. `sdl_gpu_probe` and `w3d_view` are "built but not registered with ctest" precisely because
  they "need a GPU and a display" (`CMakeLists.txt:964-990`).
- **Windows-only test blocks** are already excluded on POSIX (`CMakeLists.txt:2692`, `:2704`).

So the suite is already designed to run **without** original game data (skip) and **with** a
software Vulkan driver in CI — not to run on a phone.

### E2 — CI runs the suite on a Linux host with a software Vulkan driver and no game data

`.github/workflows/linux-release.yml` runs ctest "with Lavapipe and Xvfb, without original game
data" (`:145-146`), inside a compatibility container, excluding `^linux_portable_check$`
(`:167-170`). Lavapipe is Mesa's **software Vulkan** implementation; Xvfb provides a display. This is
the established way the project runs GPU-touching tests on a machine with no GPU — and it is exactly
the kind of environment an Android emulator cannot reproduce faithfully.

### E3 — An Android emulator is a poor host for these tests

- **Vulkan on an emulator** is either a software path (SwiftShader) or host-GPU passthrough that
  varies by host and emulator version; a test that passes there does not prove the device behaviour,
  and a failure there is ambiguous. The project already has a **better** software-Vulkan host
  (Lavapipe, E2).
- **Game data** is the player's own and is not in the repo (doc 08); the data tests are Skip(77)
  anyway, so running them on an emulator adds nothing.
- The 131 tests are **host-side compatibility tests** (filesystem, INI, fingerprint, D3D9-portable
  oracles, renderer read-backs). Their value is to keep the **shared** code correct; they do not
  exercise Android-specific code (which barely exists yet).

### E4 — What Android actually needs tested is the Android-only glue

The parts that are Android-specific and *cannot* be covered by the desktop suite are:

1. `libmain.so` builds and `SDLActivity` loads it and calls `SDL_main` (doc 10);
2. `main()` starts, resolves the install root through the picker/`Registry.ini` (doc 08), and reaches
   the shell map;
3. a window and a Vulkan surface are created (doc 07), and a frame is drawn;
4. touch input reaches the engine;
5. the app pauses/resumes without crashing (Android lifecycle).

These are **integration/smoke** checks, best run on a **device or emulator with a working Vulkan
driver**, and they are a new, small set — not the 131.

## Decision details

- **Desktop CI keeps running the 131** (unchanged); it is the regression gate for the shared code.
- **Android CI (Phase 2)** builds the APK (`assembleLauncherOnly`-style / full NDK build) and runs
  the smoke set above on an emulator or attached device. If no GPU-capable emulator is available in
  CI, the smoke set runs on a physical device manually, and CI at least builds and lints the APK.
- **Do not port the 131 to the emulator.** It would be slow, flaky (emulator Vulkan), and redundant
  with the desktop gate.
- **Reuse, don't duplicate:** where a test is platform-neutral and cheap, it can also be run in the
  Android build if the NDK can build it — but that is an optimisation, not the plan.

## Open items handed to Phase 2

1. Define the Android smoke set (the five checks in E4) and the host it runs on (emulator vs
   device), and wire it into CI.
2. Decide whether any *cheap, platform-neutral* subset of the 131 (e.g. `build_fingerprint_manifest`,
   pure-logic tests) is also worth building under the NDK; optional.
3. Keep the desktop CI (Lavapipe + Xvfb, no game data) as the primary gate; do not weaken it.

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- Test/CI facts read from `GeneralsMD/Code/CMakeLists.txt` and `.github/workflows/linux-release.yml`.
- Read-only: no source, build or dependency file was changed.
