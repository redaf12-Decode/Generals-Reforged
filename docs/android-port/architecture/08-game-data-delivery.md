# 08 — Game data delivery on Android

Resolves **question 2 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("How should Zero Hour game data reach the device — user-provided files in app storage, first-run
import, or sideloaded data pack?"). Read-only: no source, build or dependency file was changed.

## Decision

**User-provided files in app storage, chosen once through a first-run folder picker, with the path
persisted to the existing `Registry.ini` marker — the same model the target already uses on desktop
Linux, reusing `PosixInstallRoot` and `PosixLocalFileSystem`.** The game data (the player's own
Zero Hour and Generals `.big` archives plus `Data/`) is **never bundled in the APK** and **never
copied into the app's private directory**. No `MANAGE_EXTERNAL_STORAGE` ("All files access") is
requested; the player picks a folder through the system folder picker and the app is granted access
to that folder only.

## Evidence

### E1 — How the desktop build finds game data today

`Main/PosixMain.cpp:280-330` (`chooseInstallRoot`) resolves the install root before the engine
starts, in this order (`PosixInstallRoot.h:24-38`):

1. `-root <dir>` on the command line;
2. `Registry.ini`'s `InstallPath`, if it still validates;
3. known places (`~/Games`, `/Applications`, Steam libraries, CrossOver/Whisky bottles) — packaged
   builds only;
4. the player's own choice through an SDL folder dialog — packaged builds only;
5. the executable's own directory — unpackaged builds.

The chosen folder is **the one thing written back**, as `Registry.ini`'s `InstallPath`, "so the
chooser runs once" (`PosixInstallRoot.h:30-31`). Validation is `INIZH.big` at the folder's top level
and the base game's `Textures.big` found where `Win32BIGFileSystem::init` looks
(`PosixInstallRoot.h:37-57`). `PosixMain.cpp:471-476` then `chdir()`s into the root and marks it
read-only (`PosixPath_Set_Root_Read_Only`, `posixpath.h:87`).

This is a first-run picker with a persisted marker. **Android inherits exactly this flow.**

### E2 — Why the folder picker, not "All files access"

The reference port used `MANAGE_EXTERNAL_STORAGE` and its own in-app folder browser
(`../audit/02-reference-architecture.md`, and `ANDROID_PORT.md` §4). That permission is a
restricted, Play-policy-sensitive "All files access" grant. The target does **not** need it:

- The game's data access is **read-only** and confined to the chosen folder. `PosixLocalFileSystem`
  opens, stats and lists files under that folder; it does not scan the whole device.
- The write side (saves, replays, `Options.ini`, the crash log) already goes to a **separate user
  data directory**, not the install root (E4 below). So the app never needs broad write access.

A picker that grants a single tree (via `ACTION_OPEN_DOCUMENT_TREE` and a persisted URI permission,
or SDL's native folder dialog) is the least-privilege model that still lets the player put the
`.big` files wherever they like. The target already uses **SDL's native folder dialog** for this on
desktop (`PosixMain.cpp` step 4, `PosixInstallRoot.h:32-34`); SDL3's Android dialog support is
enabled in the build (`SDL_DIALOG ON`, `CMakeLists.txt:908`).

### E3 — Why POSIX file I/O cannot be replaced by the Storage Access Framework (SAF)

SAF (`ContentResolver`/`DocumentFile`) is a Java API. The engine's file access is C++ POSIX
syscalls, deep under the file-system abstraction:

- `PosixLocalFileSystem.cpp:123` uses `stat()`; `:149` `mkdir`; `:165` `fstat`; `:227` `unlink`;
  `:242` `rename` (`zh_unlink`/`zh_rename`/`zh_mkdir` in `zhio.h`).
- `PosixLocalFile.cpp` (the `File` implementation) reads/writes through file descriptors.
- The archive layer (`Win32BIGFileSystem`, used on POSIX too — see E5) enumerates `*.big` with
  `opendir`/`readdir` and opens them by path.

Routing all of that through SAF would mean a per-open JNI round trip and a rewrite of the whole
file layer — out of scope, and unnecessary, because the chosen folder is a real POSIX path the
app can hold a descriptor for. **The folder the player picks must be a real, directly openable
directory path** (the model the reference port also settled on for its data folder, after moving
away from the app-private `Android/data/<pkg>/files` convention; see `ANDROID_PORT.md` §4).

### E4 — Where writes go: a separate user data directory (already platform-neutral)

Saves, replays, `Options.ini` and the crash log live under a **user data folder**, resolved by
`findUserDataDirectory` and stored in `TheGlobalData->m_userDataDir`
(`GameEngine/Source/Common/GlobalData.cpp:1295-1310`; `GlobalData.h:802-810`). This is independent
of the install root, so the install root can stay read-only (E1) while the game still saves.

On Android this folder must resolve to the app's private/internal storage (e.g. under the app's
`getFilesDir()` / a `HOME` set to internal storage), which is **not** the picked game-data folder.
That keeps the game-data folder read-only and the writable state in app-private storage that
survives reinstalls of the data. `findUserDataDirectory` is the single point that needs an Android
answer (a Phase-2 item, below).

### E5 — What validation needs, exactly

The install-root check (`PosixInstallRoot.h:37,63`, `Win32BIGFileSystem.cpp:65-107`) requires:

- `INIZH.big` at the top level of the chosen folder; and
- the base game's `Textures.big` in one of the places `Win32BIGFileSystem::init` looks: the folder
  itself, `ZH_Generals/`, the sibling `Command & Conquer Generals` folders, or `Registry.ini`'s
  Generals `InstallPath` (and its First Decade subfolder).

`Win32BIGFileSystem.cpp:174` loads `*.big` from the root, then `Patch*.big` with overwrite, then the
base game's bigs (loaded second so Zero Hour's copies win), then the Reforged art last. This is the
same on POSIX (the class is compiled for both — `CMakeLists.txt:1419` and `:3045`). So the Android
"does this folder look like a Zero Hour install?" check is the **existing** `PosixCheckInstallFolder`
logic, unchanged.

### E6 — The reference port confirms the model, not a pack

The reference port ships **no assets in the APK** ("No assets ship in the APK (2.7 GB, and they're
the user's own)", `ANDROID_PORT.md:251`) and has the player copy their own install to a folder they
pick, then points the engine at it via a marker file (`ANDROID_PORT.md:266-274`). It differs from
this decision only in requiring `MANAGE_EXTERNAL_STORAGE`; this target uses a single-folder grant
instead, which is stricter and Play-compliant. There is no "data pack" to download.

### E7 — A data pack is explicitly not chosen

A sideloaded data pack would mean redistributing the player's copyrighted game data — not
permissible — or building a custom archive format and importer, which changes the asset pipeline
the fingerprint depends on (doc 05). Neither is done. The player supplies their own data.

## How the flow looks at runtime (unchanged logic, Android host)

```
first run
  PosixMain -> chooseInstallRoot()          (PosixMain.cpp:275)
      -root?  -> use it
      else Registry.ini InstallPath? -> validate, use it
      else Android: no known places -> SDL folder dialog (step 4)
              player picks the folder holding INIZH.big
              -> PosixCheckInstallFolder validates (INIZH.big + Textures.big)
              -> write Registry.ini InstallPath  (runs once)
  chdir(root); PosixPath_Set_Root_Read_Only(true)
  ... engine starts; Win32BIGFileSystem loads the .big set (E5)
  saves/Options.ini/logs -> findUserDataDirectory -> app-private storage (E4)
```

## Consequences for the APK

- The APK stays small (engine + `libmain.so` + SDL3 + resources), no multi-GB assets.
- No `MANAGE_EXTERNAL_STORAGE`; the manifest requests no broad storage permission.
- A first-run screen (or the game's own start) must present the folder picker; the existing
  `PosixInstallRoot` chooser path is the natural place. On Android, SDL's dialog support provides
  the picker (already enabled, `CMakeLists.txt:908`), or a thin `SetupActivity`-style screen can
  wrap `ACTION_OPEN_DOCUMENT_TREE` and write the same `Registry.ini` marker — a UI choice, not an
  architecture change.

## Open items handed to Phase 2

1. **`findUserDataDirectory` on Android** — return the app's internal files dir (or a `HOME` under
   it). This is the only new filesystem decision; the install-root side needs none.
2. **`Registry.ini` location on Android** — where the marker file itself lives. On desktop it is
   beside the executable / in the user area; on Android it must be writable app-private storage
   (it is written once). Confirm `PosixInstallRoot`'s registry path resolution
   (`PosixChooseInstallRoot`, `PosixMain.cpp:280-330`) points there.
3. **Folder picker on Android** — confirm SDL's Android dialog returns a usable path (or add a
   small `SetupActivity` if SDL's dialog is insufficient). This is the one place the reference
   port's `SetupActivity` is a useful precedent, not a required dependency.
4. **Read-only enforcement** — confirm `PosixPath_Set_Root_Read_Only` is compatible with an Android
   picked-folder path, and that no engine code tries to write the install root (`GameEngine.cpp:988`
   deletes `Data\INI\INIZH.big`; on a read-only root this must be a no-op, as it already is on the
   desktop read-only root).

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- Reference-port claims cite `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`,
  `docs/port/ANDROID_PORT.md` (design guidance only).
- Read-only: no source, build or dependency file was changed.
