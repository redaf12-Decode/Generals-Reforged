# Upstream sync 4.1.0 (`upstream-sync-4.1.0`)

A careful, non-destructive sync of upstream bug fixes into the Android ARM64 port of this fork.
This document records what the target branch actually contains, which upstream commits were taken,
which were left out and why, and how the result was validated. Every claim here is backed by a
commit or a file in this repository, or is explicitly marked as unverified.

Sync identifier: `upstream-sync-4.1.0`.
Working branch: `agent/upstream-sync-4.1.0`.
Base: `agent/android-arm64-application-port` @ `3a0d6b4`.

## 1. What the target actually is

The target is **not** a clean copy of upstream `CnCGeneralsZH-Reforged`. It is the Linux fork
(`Shin-Aska/CnCGeneralsZH-Reforged-Linux`, re-hosted at `redaf12-Decode/Generals-Reforged`)
imported as a single snapshot, plus the Android ARM64 port on top.

| Fact | Evidence |
|---|---|
| Imported snapshot | root commit `109962c2d` "Import CnC Generals Zero Hour Reforged snapshot", 2026-11-15 |
| Engine version string | `GeneralsMD/Code/CMakeLists.txt` writes `VERSION_STRING "v4.0.0"` |
| Engine baseline is upstream tag `v4.0.0` | core engine files (`GameCommon.h`, `AIPathfind.cpp`, `CrowdModel.cpp`) are byte-identical to upstream `v4.0.0` (`61dbc593`) |
| Upstream remote | `https://github.com/olcayseygan/CnCGeneralsZH-Reforged.git` |
| Upstream release target | tag `v4.1.0` = `fa98637f3dd9fd33b57615a787993ea03fe17f79`, 2026-11-18 |
| Sync range | `v4.0.0..v4.1.0` = 476 commits, 1755 changed files under `GeneralsMD/Code` |

A raw `git diff v4.0.0 HEAD -- GeneralsMD/Code` is **462 files** (227 added, 235 modified): the
fork's Linux overlay, the Android port, and a handful of fork-only engine patches. This overlay is
why a naive "merge upstream 4.1.0" is unsafe — many engine files diverge, and several fork files
have been refactored far enough that upstream hunks no longer apply.

## 2. Method

The sync takes only upstream changes that are provably safe against this tree.

1. **Range**: every commit in `v4.0.0..v4.1.0` was classified by conventional-commit type
   (201 `feat`, 149 `fix`, 51 `revert`, 39 `perf`, 14 `test`, 9 `docs`, 6 `build`, 5 `refactor`,
   1 merge, 1 chore).
2. **Byte-identity gate**: a candidate `fix`/`perf` commit was eligible only if **every**
   `GeneralsMD/Code` file it touches is byte-identical in the target to that commit's parent
   (pre-image). This guarantees the change applies to exactly the code it was written against and
   cannot disturb the fork's own edits or the Android port.
3. **Clean-apply confirmation**: each candidate was cherry-picked onto the base with no code
   conflict (the only allowed conflict was `CHANGELOG.md`, which this fork owns and keeps
   byte-identical — see §5).
4. **Platform review**: each change was read and checked for Windows-only tokens
   (`MessageBox`, `_snprintf`, `DX8*`, `ULONG_PTR`) and confirmed to compile in the Android build
   globs (`GAMEENGINE_SOURCES = ${GE}/Source/*.cpp`, the WWLib sources, the SDL/W3D device globs).

Features (`feat`), refactors, reverts and the "32-player / logic-pass" merge were **not** taken:
they are large, gameplay-visible, and cross many fork-divergent files. They are a separate,
larger decision, not a safe sync.

## 3. Integrated changes

Twelve upstream fixes, each cherry-picked with `-x` provenance and applied as an ordinary commit on
the branch. Local SHA → upstream SHA.

| Local | Upstream | Change | Files |
|---|---|---|---|
| `e7c68fcc1` | `bc370a51e` | observer of a takeover skirmish sees stealthed units | `GameLogic/System/GameLogic.cpp` |
| `fffebb633` | `4e76855a3` | load a save whose particle master or slave was lost | `GameClient/System/ParticleSys.cpp` |
| `297209cdf` | `065271b0e` | Aurora keeps its speed through the bomb run | `GameLogic/Object/Locomotor.cpp` |
| `d3648211d` | `94eb8d074` | keep a thin exhaust trail after fuel-out or target loss | `MissileAIUpdate.{h,cpp}` |
| `464089052` | `f80a6550c` | a refused replay no longer reads from a closed file | `Common/GameEngine.cpp`, `Common/Recorder.cpp` |
| `d41aa7cfb` | `8a84d37cd` | draw generated map previews the right way up | `Common/RandomMapGenerator.cpp`, `Common/RandomMapTemplate.cpp`, `Tests/test_gameengine.cpp` |
| `eaf7a9002` | `1e8730545` | unknown script team name returns NULL, not a throw | `Include/GameLogic/SidesList.h`, `Common/RTS/Team.cpp`, `Common/Thing/ThingFactory.cpp` |
| `e16cf2928` | `6619197ab` | a broken pathfind parent chain gives no path | `Include/GameLogic/AIPathfind.h`, `GameLogic/AI/AIPathfind.cpp` |
| `4310dca16` | `a2a58274c` | waypoint path states tolerate a unit with no locomotor | `GameLogic/AI/AIStates.cpp` |
| `7a287d6d6` | `fb3d29c61` | a copied `SimpleVecClass` owns its own buffer | `WWLib/simplevec.h`, `Tests/test_wwlib.cpp` |
| `dfc027289` | `77864bac8` | control bar scheme falls back to Default, else none | `GUI/ControlBar/ControlBarScheme.cpp` |
| `a2b02c45f` | `4e4281b95` | a mod's retail main menu layout no longer crashes | `Common/System/Debug.cpp`, `GUI/.../MainMenu.cpp`, `GUI/.../OptionsMenu.cpp` |

Why these and not others: each is a self-contained crash, hang or correctness fix; each touched
only files the target had not modified (byte-identity gate); and each is cross-platform code that
compiles on Android. None of them changes gameplay balance, the interface design, or the engine's
logic rate.

Notes on platform reach:

- `Debug.cpp`'s `ReleaseCrash` hunk sits inside `#if defined(_WIN32)`, so it is **inert on
  Android**; the `MainMenu.cpp` and `OptionsMenu.cpp` hunks of `a2b02c45f` are cross-platform and
  are the ones that matter on Android (a modded `MainMenu.wnd` no longer dereferences a missing
  window).
- `4e76855a3` (particle save/load), `94eb8d074` (missile exhaust), `065271b0e` (Aurora),
  `1e8730545` (script team), `6619197ab` (pathfind) and `a2a58274c` (waypoint states) all live in
  `GameEngine/Source/**`, which the Android build compiles (`GAMEENGINE_SOURCES`).

## 4. Deliberately not integrated

| Category | Example | Why left out |
|---|---|---|
| Feature work (201 `feat`) | Red Alert-style sidebar, AI influence-map layers, 32-player slots, rating curves | Gameplay- and UI-visible; touches many fork-divergent files; a separate, larger decision |
| Refactors | `feat(gameengine): run logic at 30 Hz behind one constant` | Renames symbols (`LOGICFRAMES_PER_GAME_SECOND` → `LOGICFRAMES_PER_SECOND`) that the fork's overlay also rewrites; conflicts with the fork's own logic-rate work |
| Reverts (51 `revert`) | the FX/explosion series | Revert upstream FX experiments that this fork never took; applying them alone is meaningless |
| Windows-only fixes | `StackDump.cpp` x64 address, `win32device` BIG archive, Miles/XAudio2, D3D9/D3D11 twin | Not reached by the Android build (Windows sources are not in the Android link graph); no benefit, some risk |
| Networking service-config | `b3000ccb2` NGMP run-ahead | `GameNetwork/GeneralsOnline/**` is a Windows-only, C++20 library the Android build does not fetch |
| Files with a divergent pre-image | e.g. `AIPathfind.h`'s `getWalkGeneration`/`clampCost` | The fork already carries its own version; upstream hunks do not apply cleanly and would risk regressing fork behaviour |

## 5. CHANGELOG.md is intentionally untouched

This fork has not edited `CHANGELOG.md` since the snapshot (`git log 109962c2d..3a0d6b4 --
CHANGELOG.md` is empty), and the upstream v4.1.0 `CHANGELOG.md` is a different document with a
different structure. Cherry-picking it would either conflict or silently rewrite fork history.
Every synced commit therefore carries **engine code only**; `CHANGELOG.md` on this branch is
byte-identical to the base. This keeps one consistent source of truth for the fork's own changelog.

## 6. Validation

Automated application tests are forbidden for this task. The only permitted source of truth is CI.

- **Static review performed**: pre-image byte-identity gate; clean cherry-pick (no code conflict);
  full diff read file by file; `git diff --check` clean (no whitespace errors); symbol existence
  checked for every newly referenced function/field/macro (`hasInfo`, `getFirstNode`,
  `deleteInstance`, `withSystem`, `setSizeMultiplier`, `getSlave`, `getBraking`,
  `m_movementPenaltyDamageState`, `OBJECT_STATUS_IS_ATTACKING`, `testStatus`, `m_headless`,
  `m_autoSkirmishObserver`, `findPlayerWithNameKey`, `findControlBarScheme`, `missionPanelPresent`
  inputs, etc.).
- **Android compile coverage confirmed**: every changed source is inside an Android build glob.
- **CI**: run via `workflow_dispatch` on the `CI Android Test` and `CI Android App` workflows for
  branch `agent/upstream-sync-4.1.0` (neither auto-triggers on this branch name). See the CI
  results section below.

No local build, APK, emulator or device run was performed, and none is claimed.

### CI results

Both workflows passed on branch `agent/upstream-sync-4.1.0` at commit `593bebb41`, dispatched by
hand because neither triggers on this branch name.

| Workflow | Run | Result | Key evidence |
|---|---|---|---|
| CI Android Test | [44400654385](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44400654385) | success (28m15s) | ARM64 configure; bounded NDK compile; SDL3 Vulkan backend compiled; native touch layer compiled; **the engine links as `libmain.so` for arm64-v8a and is an AArch64 object exporting `SDL_main`**; audio/storage/services layer compiled |
| CI Android App | [44400675124](https://github.com/redaf12-Decode/Generals-Reforged/actions/runs/44400675124) | success (26m34s) | native engine built; native dependencies audited from the ELF; APK packaged and inspected (structure, ABI, manifest); pure-Java unit tests run; Gradle config and merged manifest asserted |

This proves the twelve fixes compile and link for Android and that the APK still packages
correctly. It does **not** prove runtime behaviour; no device or emulator was used.

## 7. Remaining unknowns and limitations

- The 12 fixes were validated by inspection and by CI compilation only. They were **not** run on a
  device; behavioural confirmation (e.g. the Aurora speed curve, the particle-save load) rests on
  upstream's own testing, not on this fork's.
- The large upstream feature set between v4.0.0 and v4.1.0 remains unsynced. A future, larger sync
  would need per-feature review against the fork's overlay.
- The `AIPathfind.cpp` fix carries an upstream TODO: which code path released a pathfind cell's
  info is still open upstream. This sync does not resolve it; it only stops the fault.
