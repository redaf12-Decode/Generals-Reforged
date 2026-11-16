# 05 — Multiplayer compatibility and the build fingerprint on Android

Resolves **question 5 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("How does the compatibility fingerprint interact with an Android build, and must an Android build
be able to play a desktop build?"). Read-only: no source, build or dependency file was changed. No
code was changed — the implementation is Phase 2.

## Decision

**The build fingerprint is already platform-independent and is used unchanged on Android. The
Android build reports the same `m_exeCRC` as a desktop build of the same source, so Android and
desktop players of the same commit are **compatible by construction** for the executable half of the
data check.** No Android-specific fingerprint is introduced, and the executable's own bytes are
never hashed (they are not hashed on any platform — see E1).

## Evidence

### E1 — The executable CRC is a source fingerprint, not the binary's bytes

`GlobalData::computeExeCRC` (`GameEngine/Source/Common/GlobalData.cpp:1474-1514`) builds `m_exeCRC`
from:

1. `ZH_BUILD_FINGERPRINT` — a 32-bit CRC over the **content of every tracked source file** under
   `GeneralsMD/Code` (`GlobalData.cpp:1478`; generated at build time by
   `Tools/fingerprint/build_fingerprint.cpp`, `:18-40`);
2. the game **version number** (`TheVersion->getVersionNumber()`, `GlobalData.cpp:1481-1486`);
3. the **multiplayer scripts** `Data\Scripts\{SkirmishScripts,MultiplayerScripts,SkirmishScriptsRetail}.scb`
   as they resolve now (`GlobalData.cpp:1490-1506`);
4. a fixed sentinel `"NOUD"` (`0x4E4F5544`) only when there is **no** user data folder
   (`GlobalData.cpp:1506-1511`), so an ordinary machine's value is unchanged.

The comment at `GlobalData.cpp:1269-1278` states the design plainly: the executable's bytes are
"Not the executable's bytes any more, on any platform… A Mac or Linux binary can never have
generals.exe's bytes, so hashing them made cross-platform play impossible by construction; two
builds of the same source now agree whatever compiled them." **This is exactly the property Android
needs**, and it already holds: the fingerprint is over source, so an Android `libmain.so` and a
desktop `generals` built from the same commit produce the same `ZH_BUILD_FINGERPRINT`.

### E2 — The fingerprint is deterministic and platform-neutral

`Tools/fingerprint/build_fingerprint.cpp:30-38` defines the value as CRC-32 (IEEE 802.3, zlib's
`crc32`) over, for each manifest file in order, its path, a NUL, its content **with every run of CRs
before an LF removed** (so a Windows CRLF checkout and a Unix LF checkout agree), and a NUL. The
manifest is the committed list `GeneralsMD/Code/BuildFingerprint.manifest`, written from
`git ls-files` by `Tools/fingerprint-manifest.sh` (which excludes the FFmpeg `dist/` and
`dist-arm64/` trees that a build rewrites). Nothing in the value depends on the compiler, the OS,
the architecture or the build system — only on the source bytes. `Tools/fingerprint-check.sh` proves
the CRLF/LF invariance and that one changed byte changes the value.

Consequence: **an Android build of commit X and a desktop build of commit X have the same
`m_exeCRC`** (given the same version number and the same MP scripts). No Android-specific work is
needed for this half.

### E3 — How the CRC gates a join

`m_exeCRC` (and `m_iniCRC`, the INI set) are exchanged when joining a game and compared by
`compareGameData` (`GameEngine/Include/GameNetwork/GameDataMatch.h:24-48`,
`Source/GameNetwork/GameDataMatch.cpp:24-42`):

- `GAMEDATA_EXE_DIFFERS` — different build or different multiplayer scripts;
- `GAMEDATA_INI_DIFFERS` — same build, different INI set;
- `GAMEDATA_MATCHES` — the game may start.

The values travel in the staging room (`GameNetwork/GameSpy/StagingRoomGameInfo.h:115-116`,
`setExeCRC`/`getExeCRC`) and the WOL lobby checks them on join
(`GameClient/GUI/GUICallbacks/Menus/WOLLobbyMenu.cpp:1699`: `roomToJoin->getExeCRC() != TheGlobalData->m_exeCRC || roomToJoin->getIniCRC() != TheGlobalData->m_iniCRC`).
Recorded games also carry it (`Common/Recorder.cpp:593`, compared at `:1085`/`:1191`).

So the only thing that decides "same build" is the fingerprint + version + MP scripts, all of which
are platform-neutral (E2). **An Android and a desktop player on the same commit pass the executable
check.**

### E4 — What still differs between Android and desktop, and how the existing design handles it

The **INI half** (`m_iniCRC`) is over the text of every INI file the simulation reads, "as resolved,
so a loose file under Run/Data that shadows an archive is what gets CRCed" (`GameDataMatch.h:29-34`).
This is about the **player's data**, not the platform: two players whose installs differ in INIs
already get `GAMEDATA_INI_DIFFERS` and a refused join — Android is no different from a desktop with
a different data set. The `noUserDataDir` sentinel (`GlobalData.cpp:1506-1511`) is the one place the
fingerprint depends on a user-data folder existing; on Android, once `findUserDataDirectory` returns
the app's private dir (doc 08), the sentinel is **not** added, matching an ordinary desktop machine.

### E5 — Reference port solves the same problem differently; the target's approach is stronger

The reference port (`redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`,
`GeneralsMD/Code/GameEngine/Source/Common/GlobalData.cpp:1426-1470`) **hashes the executable's own
bytes** on Windows (`GetModuleFileName`) and macOS (`_NSGetExecutablePath`), and on **Linux skips the
executable hash entirely** — the comment says reading "the entire 180 MB+ binary for CRC is
prohibitively slow and unnecessary… use version-based CRC which is fast and sufficient". (Android's
Clang defines `__linux__` too, so the reference's Android build takes that same skip branch.) So the
reference port's executable CRC is a *version* value on Linux/Android and an *executable-bytes*
value on Windows/macOS — two different values for the same game, which is exactly the cross-platform
mismatch the target's design removes.

The **target** does not hash the executable at all, on any platform: it hashes the **source**
(`ZH_BUILD_FINGERPRINT`, E1). So a Windows, macOS, Linux and Android build of the same commit all
produce the **same** `m_exeCRC` — no platform branch, no "skip on Linux" workaround. **This is the
stronger design and it already covers Android**; the Android task adds nothing here and must simply
not break it.

## Decision details

- **Android and desktop are compatible when built from the same commit** (same fingerprint, same
  version, same MP scripts). This is the intended default and requires no code.
- **No Android-specific fingerprint** — one fingerprint covers all platforms, which is the whole
  point of a source-based value (E1).
- **The fingerprint is not weakened for Android.** The `BuildFingerprint.manifest` and the tooling
  (`fingerprint-manifest.sh`, `fingerprint-check.sh`, `test_fingerprint_manifest.sh`) are unchanged;
  a new Android source file simply joins the manifest the next time it is regenerated
  (`fingerprint-manifest.sh`), exactly as any new source file does. **Android source added in Phase
  2 must be committed and the manifest regenerated** so the fingerprint stays correct — a routine
  step, but one to remember.
- **A deliberate Android-only incompatibility is not adopted.** If a future need arises to keep
  Android out of desktop games (or vice versa), that is a *policy* decision to be taken explicitly,
  not a side effect of the build — and it is not taken here.

## Open items handed to Phase 2

1. After adding any Android source, run `Tools/fingerprint-manifest.sh` and commit the updated
   `BuildFingerprint.manifest`, then let `ctest`'s `build_fingerprint_manifest` check confirm it.
2. Confirm the Android build generates `BuildFingerprint.h` (`ZH_BUILD_FINGERPRINT`) the same way the
   desktop build does (the generator is a host tool; ensure it runs for the Android configuration).
3. Confirm the game **version number** (`Common/version.h` / generated `GeneratedVersion.h`) is the
   same on Android and desktop for a given release, since it is folded into the CRC (E1).
4. Decide whether an Android build should advertise its platform anywhere in the join UI (a
   **display** concern only; it must not enter `m_exeCRC`).

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- Reference-port claims cite `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`,
  `docs/port/ANDROID_PORT.md` (design guidance only).
- Read-only: no source, build or dependency file was changed.
