# 17 — Game folder access on Android

Phase 3 of the Android port (Stage 2 of `../IMPLEMENTATION-CONTINUATION-STATUS.md` §10, the folder
picker). Phase 1 left the native engine complete and CI-green; Phase 2 added the Gradle application
shell, the manifest, the thin `SDLActivity` subclass and the packaging script
(`16-android-application-shell.md`). Both recorded the same single largest functional gap: a launch
with no `-root` cannot select a game folder, because SDL3 3.4.16's Android dialog backend returns
`SDL_Unsupported()` for `SDL_FILEDIALOG_OPENFOLDER`, and the file dialogs it does implement return a
`content://` URI the engine's POSIX file layer cannot open (§8.2 of the status doc).

This document records the Phase 3 implementation of that picker, the storage architecture behind it,
and what it does **not** claim. No file under `GeneralsMD/Code/` changed: the native engine, its
storage layer (`PosixLocalFileSystem`, `PosixLocalFile`, `PosixInstallRoot`) and the `Registry.ini`
marker are exactly as Phase 1 found them. The Android picker is a Java activity that hands the
engine a real path through the existing `-root` argument, which `PosixMain.cpp` already honours as
its first choice (`chooseInstallRoot`, step 1).

## 1. Why a Java activity, and why staging

The decision in `08-game-data-delivery.md` is "user-provided files in app storage, chosen once
through a folder picker, no `MANAGE_EXTERNAL_STORAGE`". That decision stands. Two facts constrain how
it is implemented on Android 11+ (the port's `minSdk` is 28, but path-based SAF access is what
matters on 30+):

1. The engine's file access is C++ POSIX: `chdir`, `opendir`/`readdir`, `stat`, `open`,
   `realpath` (`PosixLocalFileSystem.cpp`, `Win32BIGFileSystem.cpp`'s `*.big` enumeration). It
   cannot open a `content://` URI, and routing every open through JNI would rewrite the whole file
   layer (E3 of doc 08).
2. The Storage Access Framework is the only way to read an arbitrary folder without the restricted
   "all files access" permission, and it returns `content://` URIs. On Android 11+ the framework
   deliberately does **not** hand back a raw filesystem path for an arbitrary picked tree; a direct
   path is available only for the app's own directories and for the special-case trees (SD card
   root, `Documents`, `Download`), which is not where a game install lives.

So the picked tree is **staged once** into a real filesystem directory the engine can open: the
app's own external files directory (`getExternalFilesDir(null)/gamedata`), which the app may always
write with no permission and no `MANAGE_EXTERNAL_STORAGE`. The engine is rooted there with
`-root`. This is the "persistent incremental staging" alternative the Phase 3 brief names, chosen
over a filesystem-layer rewrite because it is the least invasive change that satisfies the engine's
verified requirements.

The staging is **incremental**: the first launch copies the tree, and every later launch copies only
files whose size or modification time changed and deletes files the source no longer has
(`GameDataStaging`). A launch after the first copies nothing when the game data has not moved, so
the several-GB copy happens once, not on every launch.

## 2. Files

Created (all new):

| Path | Role |
|:--|:--|
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/SetupActivity.java` | the launcher screen: pick, stage, launch, diagnose |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/SafTreeStager.java` | walks a `content://` tree and stages it into a real path (platform `DocumentsContract`/`ContentResolver`, no third-party dependency) |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/GameDataStaging.java` | pure incremental-copy plan (no Android types) |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/GameFolderValidator.java` | pure install-folder rule, mirroring `PosixCheckInstallFolder` (no Android types) |
| `android/app/src/main/java/io/github/olcayseygan/zerohourreforged/GameFolderStore.java` | persisted tree URI + grant, start-up readiness check, engine root path |
| `android/app/src/test/java/io/github/olcayseygan/zerohourreforged/GameDataStagingTest.java` | host unit tests for the staging plan |
| `android/app/src/test/java/io/github/olcayseygan/zerohourreforged/GameFolderValidatorTest.java` | host unit tests for the folder rule |

Modified:

| Path | Change |
|:--|:--|
| `android/app/src/main/AndroidManifest.xml` | `.SetupActivity` declared and made the LAUNCHER entry point; `.ZeroHourActivity` keeps MAIN (a pre-existing shortcut still resolves). No new permission. |
| `android/app/src/main/java/.../ZeroHourActivity.java` | `getArguments()` returns `-root <staged path>`; `onCreate` sends the player to setup when the staged folder does not validate |
| `android/app/src/main/res/values/strings.xml` | the setup screen's strings |
| `android/app/build.gradle` | the JUnit test dependency (the app still has no runtime dependency) |
| `.github/workflows/ci-android-app.yml` | run the unit tests; assert the setup Activity, the launcher entry point and the absence of storage permissions in the packaged APK |

## 3. How the flow looks at runtime

```
first launch
  SetupActivity (LAUNCHER)
    -> ACTION_OPEN_DOCUMENT_TREE (system picker; read + persistable grant)
       player picks the folder holding INIZH.big (or the folder that holds both games)
    -> takePersistableUriPermission(READ)          survives a restart
    -> SafTreeStager.stage(): walk the tree, copy what changed into
       getExternalFilesDir(null)/gamedata          (a real path; incremental)
    -> GameFolderValidator.validate(): INIZH.big + the base game's Textures.big
       where the engine looks (folder, ZH_Generals, or a sibling Generals folder)
  "Start game" -> ZeroHourActivity
    -> GameFolderStore.isReady() re-checks the grant and the staged verdict
    -> getArguments() = { "-root", <staged Zero Hour folder> }
    -> SDLActivity loads libmain.so and calls SDL_main with those argv
       PosixMain chooseInstallRoot step 1: "-root" is taken as given; chdir(root)
    -> engine starts; Win32BIGFileSystem enumerates the *.big set at the root
  saves / Options.ini / logs -> findUserDataDirectory -> app-private storage (unchanged)

later launches
  SetupActivity shows "Ready"; staging copies nothing (nothing changed)
  a revoked grant, a deleted/moved source folder, or an incomplete staging -> the screen says so
  and asks for the folder again, rather than starting the engine without its data
```

The staged copy keeps the picked tree's shape, so a player who picked the folder that holds both
games (Steam installs them as siblings) still gets the base game's archives where the engine's
`../Command & Conquer Generals` lookups expect them: the engine root is the Zero Hour folder
*inside* the staged tree (`GameFolderStore.engineRoot`).

## 4. Validation of the picked folder

`GameFolderValidator` mirrors the engine's own check (`PosixCheckInstallFolder`,
`PosixInstallRoot.cpp:190-220`): Zero Hour's `INIZH.big` at the install folder's top level, and the
base game's `Textures.big` in one of the places `Win32BIGFileSystem::init` looks for it
(`BASE_GAME_DIRECTORIES`, `PosixInstallRoot.cpp:43-45`): the folder itself, `ZH_Generals`, or a
sibling Generals folder reached with `../`. Case is ignored, as the engine's `zh_access` resolves
each path component. The rule is pure Java and unit-tested on the host (`GameFolderValidatorTest`);
the same verdict drives the setup screen's message and the decision to start the game, so an
incomplete or wrong folder never reaches the engine.

## 5. Permissions

**No storage permission is added.** The app declares only the two permissions Phase 2 justified
(`CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`). `MANAGE_EXTERNAL_STORAGE` is **not** used, because:

- The folder picker's single-tree grant is sufficient: the engine reads one folder, read-only.
- `MANAGE_EXTERNAL_STORAGE` is a restricted, Play-policy-sensitive "all files access" grant
  (`support.google.com/googleplay/android-developer/answer/10467955`), and it is not needed for an
  app that reads one folder the player chose and writes only to its own storage.

The staged copy lives in the app's own external files directory, which requires no permission on any
supported API level; the app's private storage, where saves and `Options.ini` go, likewise needs
none.

## 6. Failure handling

| Case | Behaviour |
|:--|:--|
| Player cancels the picker | the previous selection is kept; the screen re-reads its state |
| Grant revoked, or the source folder deleted/moved | `hasPersistedRead`/the staged verdict is false; the screen says access was withdrawn and asks again |
| Folder has no `INIZH.big` | "It has no INIZH.big …", the game cannot be started |
| Zero Hour present but the base game missing | the screen names `ZH_Generals` and what to put there |
| Copy interrupted (player leaves, OS kills the app) | the file is written to a `.part` and renamed into place, so a half file never reaches the engine; the next stage re-copies it |
| Not enough free space | staging refuses before copying, with the size needed and the size free |
| A provider that refuses a directory mid-walk | that directory's files are simply absent, which the validator and the engine both treat as missing data |

## 7. Verification

Locally (no build): XML well-formedness, brace balance, the pure-Java rule and plan read by eye.

On CI (`.github/workflows/ci-android-app.yml`, the only build environment):

- `:app:testDebugUnitTest` runs `GameFolderValidatorTest` and `GameDataStagingTest` (unit tests;
  host JVM, no Android, no device).
- the merged manifest is asserted to carry both activities, the setup Activity as the LAUNCHER, and
  no storage permission; the packaged APK's badging is asserted to name `SetupActivity` as the
  home-screen entry point.
- the APK is assembled and its contents inspected as before.

**Not claimed:** nothing here is verified on a device. There is no device in CI, and the interactive
picker (the system UI, a real `content://` provider, a real multi-GB copy) is a device-only path.
The unit tests prove the folder rule and the copy plan; the CI manifest/APK assertions prove the
wiring is present and packaged. A physical-device acceptance run remains a Phase-4 item.

## Provenance

- Engine facts cited from `PosixInstallRoot.cpp`, `PosixMain.cpp`, `PosixLocalFileSystem.cpp`,
  `Win32BIGFileSystem.cpp` at this branch's head.
- Android behaviour (SAF, persistable grants, app-specific storage) from the official Android
  documentation: the shared-documents guide, the app-specific-storage guide, the `DocumentsContract`
  reference and the "Manage all files" page.
- The reference port (`redaF12-Tech/GeneralsZH-Android-Port`, `FolderPickerActivity`/`SetupActivity`)
  was read for patterns only; its `MANAGE_EXTERNAL_STORAGE` model and its package id are not adopted.
