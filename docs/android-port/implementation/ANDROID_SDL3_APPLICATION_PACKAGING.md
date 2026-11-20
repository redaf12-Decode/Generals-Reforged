# Phase 2 — SDL3 application integration and native library packaging

Phase 2 of the Android port. Phase 1 made two native artifacts (`libmain.so` for the engine,
`libSDL3.so` for SDL3) and taught the build to link them; this phase makes the application carry
them: the matching SDL3 Java glue is integrated from the vendored tree, the load order is stated and
checked, exactly three libraries are staged into `jniLibs/arm64-v8a`, and the payload is verified
statically at three points (staging, packaging, CI) against the ELF objects themselves.

Read first, as required: the Phase 1 record
[`ANDROID_ENGINE_AND_SDL3_SHARED_LIBRARY.md`](ANDROID_ENGINE_AND_SDL3_SHARED_LIBRARY.md) (the two
artifacts and the `SDL_main` / `JNI_OnLoad` split), and the design records it builds on:
[`../architecture/16-android-application-shell.md`](../architecture/16-android-application-shell.md)
(the Gradle project), [`../architecture/18-native-dependency-packaging.md`](../architecture/18-native-dependency-packaging.md)
(the Phase 4 packaging rules) and [`../architecture/17-game-folder-access.md`](../architecture/17-game-folder-access.md)
(why the game Activity is not the launcher).

No application build was run locally. Nothing was copied from the reference port: it was read for
the model only (its `package-android-zh.sh` also wipes and re-stages `jniLibs`, and its workflow
compares a packaged library against the built one), and none of its DXVK, ANGLE, OpenAL,
adrenotools or Box64 work is involved.

## 1. The application module, as it stands

| Item | Value | Where |
|:--|:--|:--|
| Module | `:app`, namespace and applicationId `io.github.olcayseygan.zerohourreforged` | `android/app/build.gradle` |
| ABI | `arm64-v8a` only (`abiFilters`) | `defaultConfig.ndk` |
| SDK | `minSdk 28` (the engine's NDK floor), `compileSdk`/`targetSdk 35` | `defaultConfig` |
| AGP / Gradle / Kotlin | 8.7.3 / 8.12 / 2.2.0 | `android/build.gradle` |
| Java sources | `src/main/java` + **`src/main/java-sdl`** (staged) | `sourceSets.main` |
| Native payload | **`src/main/jniLibs`** → `jniLibs/arm64-v8a/` (staged) | `sourceSets.main` |
| Activities | `SetupActivity` (LAUNCHER), `ZeroHourActivity` (game, extends `SDLActivity`), `CompatibilityActivity` | manifest |
| Permissions | `INTERNET`, `CHANGE_WIFI_MULTICAST_STATE` | manifest |

The native half is not built by Gradle: `scripts/build/android/build-android.sh` cross-compiles the
engine (and SDL3) with the NDK, and `scripts/build/android/package-android.sh` stages the artifacts
into the module and runs Gradle. Both staged directories are gitignored
(`android/.gitignore`), so a checkout never carries a binary.

## 2. SDL3's Java glue: integrated from the vendored tree

The glue is **not** copied into the repository. It is staged from the same SDL3 checkout the engine
compiled against, on every packaging run, and then checked:

| Step | What it guarantees |
|:--|:--|
| `package-android.sh` copies `GeneralsMD/Code/Libraries/Source/SDL3/android-project/app/src/main/java/org/libsdl/app/*.java` into `android/app/src/main/java-sdl/org/libsdl/app/` | the glue is the SDL3 in the tree (`Tools/vendor.sh` fetches it; the pin is `fa2c02bb…`, SDL3 3.4.16) |
| `diff -r` between the two directories | the staged copy is byte-identical to the source - no hand edit can survive a packaging run |
| `verify-android-native-libs.sh --glue` | every native method the staged glue declares is registered by `libSDL3.so` |

That third check is the one that matters at runtime. SDL3's glue declares 65 native methods
(`SDLActivity`, `SDLInputConnection`, `SDLAudioManager`, `SDLControllerManager`, `SDLSensorManager`,
`HIDDeviceManager`); the implementations are in `libSDL3.so`'s `JNINativeMethod` tables
(`src/core/android/SDL_android.c`, `src/hidapi/android/hid.cpp`), where each name is a string. A
glue from a different SDL3 version, or a `libSDL3.so` built from a different tree, is a method that
throws `UnsatisfiedLinkError` the first time the activity calls it - and a green build would hide it.
The verifier extracts the declared names from the staged `.java` files (65 at the pinned commit) and
requires each one to be present in `libSDL3.so`.

`java-sdl` is registered as a Java source directory in `android/app/build.gradle`, so the staged glue
is compiled by the same `:app:compileDebugJavaWithJavac` as the app's own classes. The module takes
no dependency on any SDL3 artifact: its dependency list is Compose, the Activity host and JUnit, and
nothing else.

## 3. Native library loading order

`ZeroHourActivity.getLibraries()` (`android/app/src/main/java/.../ZeroHourActivity.java:60-65`)
returns:

```java
return new String[] { "SDL3", "main" };
```

`SDLActivity.loadLibraries()` calls `System.loadLibrary` on each entry in order, so the sequence at
start-up is:

| Order | Library | Why it must be here |
|:--|:--|:--|
| 1 | `libSDL3.so` | its `JNI_OnLoad` (exported through `src/dynapi/SDL_dynapi.sym`) registers `SDLActivity`'s and friends' native methods; until it runs, every native call from the glue is unresolved |
| 2 | `libmain.so` | the engine; `SDLActivity` then `dlsym`s `SDL_main` in it and runs it on the SDL thread |
| - | `libc++_shared.so` | not loaded by name: it is `libmain.so`'s `DT_NEEDED`, so the dynamic linker loads it when `libmain.so` is loaded |

SDL3 3.4.16's own default `getLibraries()` is already `{"SDL3", "main"}`, so this override states the
contract rather than relying on it. The order is not the only guarantee: `libmain.so` names
`libSDL3.so` in `DT_NEEDED`, so the linker resolves SDL3 even if the Java order were changed. What
the order guarantees is that JNI registration happens before the engine starts.

Three properties keep the order from being silently wrong:

1. `ZeroHourActivity` does not override `getMainSharedObject()` or `getMainFunction()`: the last
   entry of `getLibraries()` is turned into `libmain.so` by SDLActivity's default, and the entry
   point stays SDL's `SDL_main`.
2. `ThemeIsolationTest.theGameActivityStillHonoursItsSdlContract` fails if the library list, the
   `-root` argument, or the "no lifecycle override" rule changes.
3. The verifier rejects a `libmain.so` that exports `JNI_OnLoad` (a second registration authority)
   or defines any `SDL_*` of its own (a second SDL3 implementation).

## 4. APK packaging paths

The packaging script (`scripts/build/android/package-android.sh`) stages into the module and Gradle
copies `jniLibs` into the APK's `lib/<abi>/`:

| File on disk | Path inside the APK | Source |
|:--|:--|:--|
| `android/app/src/main/jniLibs/arm64-v8a/libmain.so` | `lib/arm64-v8a/libmain.so` | `build-android-arm64/libmain.so` (the `generals` target, `OUTPUT_NAME main`) |
| `android/app/src/main/jniLibs/arm64-v8a/libSDL3.so` | `lib/arm64-v8a/libSDL3.so` | `build-android-arm64/libSDL3.so` (`SDL3-shared`, `SDL_SHARED ON`) |
| `android/app/src/main/jniLibs/arm64-v8a/libc++_shared.so` | `lib/arm64-v8a/libc++_shared.so` | `<ndk>/toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/aarch64-linux-android/` |
| `android/app/src/main/java-sdl/org/libsdl/app/*.java` | compiled into `classes.dex` | the vendored SDL3 3.4.16 `android-project` |

Nothing else is packaged. `libc++_shared.so` is required, not optional: `ANDROID_STL=c++_shared`
puts it in `libmain.so`'s `DT_NEEDED`, and the verifier fails a `libmain.so` that imports C++
symbols without naming it. Every other third-party dependency (miniaudio, FreeType, glslang,
SPIRV-Cross, SDL_shadercross, mbedTLS, zlib, GameSpy, FFmpeg) is a static archive inside
`libmain.so` and must not appear as a second `.so`.

The one native library Compose's `ui-graphics` would add, `libandroidx.graphics.path.so`, stays
excluded (`packaging { jniLibs { excludes.add '**/libandroidx.graphics.path.so' } }`), so the payload
is exactly the three above. A future screen that iterates a Compose `Path` must revisit that
exclusion and the CI assertion together.

## 5. Manifest and Activity integration

The manifest needed **no change** in this phase, and that is the finding, not an omission:

| Requirement | Where it is satisfied | How it is verified |
|:--|:--|:--|
| `ZeroHourActivity` extends `org.libsdl.app.SDLActivity` | `ZeroHourActivity.java` | `ThemeIsolationTest`; CI's merged-manifest assertion |
| The game Activity declares SDL3's full `configChanges` set (13 entries) | manifest, `ZeroHourActivity` | `ThemeIsolationTest.theGameActivityIsDeclaredForSdl` (exact string); CI checks the key entries |
| Landscape, `singleInstance`, `alwaysRetainTaskState`, not edge to edge | manifest + `Theme.ZeroHour.Game` | `ThemeIsolationTest` (two tests) |
| The game Activity is not the home-screen entry point | `SetupActivity` holds `LAUNCHER` | CI `aapt2 dump badging` |
| Identity unchanged | applicationId `io.github.olcayseygan.zerohourreforged` | CI asserts it in the merged manifest and the APK |
| Permissions unchanged (two, no storage) | manifest | CI asserts the set and the absence of storage permissions |
| No Vulkan requirement change | `android.hardware.vulkan.version` declared, `required=false` | unchanged from the earlier phases |

The glue is compiled into the APK, which is what makes `extends SDLActivity` resolve at runtime; a
missing `java-sdl` fails the Java compile, so it cannot ship silently.

## 6. Dependency verification

`scripts/build/android/verify-android-native-libs.sh` is the static gate. It is called by
`package-android.sh` (twice) and by `ci-android-app.yml` (once more, independently), and it fails the
build on the first violation.

| Check | Rejects |
|:--|:--|
| payload set | a missing `libmain.so` / `libSDL3.so` / `libc++_shared.so`; **any** other `.so` in the payload (unused library) |
| ELF class | `ELF32` (a 32-bit object cannot load in an arm64-v8a process) |
| ELF machine | `x86_64`, `x86` and anything else that is not `AArch64`, named in the message |
| ELF type | `EXEC` (an executable cannot be `System.loadLibrary`ed) |
| SONAME | a missing SONAME, a SONAME that is not the packaged file name, two objects answering one SONAME |
| `DT_NEEDED` | a desktop-Linux/Windows library by token (`libX11`, `libfontconfig`, `libwayland`, `libxkbcommon`, `libGL.so`, `libdxvk`, `libasound`, …); a dependency that is neither an Android system library nor packaged |
| shared-library resolution | a packaged `DT_NEEDED` that is not a real file in the payload; `libmain.so` importing C++ symbols without `libc++_shared.so` in `DT_NEEDED`; a C++ import that neither `libSDL3.so` nor `libc++_shared.so` defines |
| required exports | `libmain.so` without `SDL_main`; `libSDL3.so` without `JNI_OnLoad`, `SDL_Init`, `SDL_CreateGPUDevice`, `SDL_GetAndroidActivity`, `SDL_GetAndroidJNIEnv`, `SDL_GetAndroidInternalStoragePath`, `SDL_GetVersion`; `libc++_shared.so` without `_Znwm`/`_ZdlPv` |
| JNI symbols | `libmain.so` exporting `JNI_OnLoad` (two registration authorities); a native method the staged glue declares that `libSDL3.so` does not register (glue/native version drift) |
| duplicate SDL3 | more than one `libSDL3*.so`; more than one object defining `SDL_Init`; more than one defining `JNI_OnLoad`; any `SDL_*` defined in `libmain.so` except `SDL_main` |
| stale reuse | with `--reference`: a packaged library whose build id differs from the staged one (falling back to SONAME + `DT_NEEDED` + export set when no build id exists) |

`libc++_shared.so` is in the payload set, not in the system-library list, so its absence is a
failure even though the device also has a `libc++_shared.so` of its own - the two must not be mixed.

### 6.1 Where the gate runs

| Point | Command | Effect |
|:--|:--|:--|
| `package-android.sh`, before Gradle | `--libs jniLibs/arm64-v8a --ndk … --glue java-sdl` | a missing, wrong-architecture or mismatched library stops the packaging before an APK exists |
| `package-android.sh`, after Gradle | `--libs <unpacked APK>/lib/arm64-v8a --reference jniLibs/arm64-v8a` | the APK carries the staged objects, and no other ABI |
| `ci-android-app.yml` | the same two runs, in their own step | the verdict is in the CI log independently of the script's flow |

### 6.2 Preventing stale libraries

Four mechanisms, each closing a different hole:

1. `package-android.sh` **removes** `jniLibs/arm64-v8a` and re-creates it, so a library that is no
   longer produced cannot survive in the staging directory.
2. It also removes AGP's `intermediates/merged_native_libs` and `intermediates/stripped_native_libs`,
   so Gradle cannot package a previous run's merged or stripped output.
3. After packaging it unpacks the APK and requires each library's **build id** to equal the staged
   object's - AGP's symbol strip changes the bytes but not the identity, so a stale or foreign object
   is visible.
4. The payload set check rejects any `.so` that is not one of the three, which is what a leftover
   library from an older layout would be.

The Gradle module adds one more guard: `verifyNativeLibraries` fails `mergeDebugNativeLibs` /
`mergeReleaseNativeLibs` when any of the three is missing, so a bare `gradle assembleDebug` on an
unstaged tree fails instead of producing an APK that installs and then dies at
`System.loadLibrary`. It is wired to the merge task rather than to `preBuild`, because the manifest
and unit-test tasks run before the payload is staged in CI and must not require it.

## 7. Files changed

| File | Change |
|:--|:--|
| `scripts/build/android/verify-android-native-libs.sh` | **new.** The static verifier: payload set, ELF class/machine/type, SONAME, `DT_NEEDED` classification and resolution, required exports, JNI contract with the staged glue, duplicate-SDL3 detection, stale-object detection (`--reference`) |
| `scripts/build/android/package-android.sh` | stages the three libraries into a wiped `jniLibs/arm64-v8a`; stages the glue and `diff -r`s it against the vendored tree; runs the verifier before Gradle; removes AGP's native-lib intermediates; after Gradle, unpacks the APK, rejects another ABI, and runs the verifier with `--reference` |
| `android/app/build.gradle` | `verifyNativeLibraries` task wired to `mergeDebugNativeLibs`/`mergeReleaseNativeLibs` (missing payload fails packaging) |
| `android/app/src/test/kotlin/.../ThemeIsolationTest.kt` | `theGameActivityIsDeclaredForSdl`: the exact SDL3 `configChanges` set, landscape, `singleInstance`, no `LAUNCHER` category; the `activity()` helper now reads a non-self-closing element through its closing tag |
| `.github/workflows/ci-android-app.yml` | preflight validates the verifier (`bash -n`, `--help`); a new step runs the verifier on the staged payload and on the APK's, with `--reference`; header and push trigger updated |
| `.github/workflows/ci-android-test.yml` | push trigger includes this branch (the native workflow now runs on push as well as by hand) |
| `docs/android-port/architecture/16-android-application-shell.md` | §4/§5 rewritten for the two-library model (the "superseded" note is replaced by the real text) |
| `docs/android-port/architecture/18-native-dependency-packaging.md` | `DT_NEEDED` table, static-vs-shared paragraph, ELF audit and APK inspection steps updated for `libSDL3.so` |
| `docs/android-port/IMPLEMENTATION-CONTINUATION-STATUS.md` | Phase 2 note pointing here |
| `GeneralsMD/Code/CMakeLists.txt` | the shared SDL3 target gets `LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}`, so `libSDL3.so` is produced beside `libmain.so` (see §8.1) |
| `docs/android-port/implementation/ANDROID_ENGINE_AND_SDL3_SHARED_LIBRARY.md` | §6.1: what the first CI run found, and the fix |
| `docs/android-port/implementation/ANDROID_SDL3_APPLICATION_PACKAGING.md` | **new.** This document |

Untouched, deliberately: `Main/PosixMain.cpp`, `GeneralsMD/Code/BuildFingerprint.manifest` (the only
`GeneralsMD/Code/` change is a CMake output-directory property, not a source file), the manifest, the
permissions, the Gradle plugin versions, the SDK levels, the application identity, SDL3's Java glue
itself (staged, never edited) and the reference port's dependency set.

## 8. Static validation performed

No application build was run, and no NDK, SDK or Gradle is present in this workspace. What was run:

1. **Shell and YAML syntax.** `bash -n` on both scripts; both scripts answer `--help`;
   `assert-cache-policy.sh` still accepts `ci-android-app.yml` (4 cache paths, all build
   infrastructure); both edited workflows parse as YAML.
2. **The verifier against synthetic payloads.** A throwaway harness (not committed) built real ELF
   shared objects with the host toolchain - `libmain.so` exporting `SDL_main` and needing
   `libSDL3.so` + `libc++_shared.so`, `libSDL3.so` exporting `JNI_OnLoad` and the SDL3 entry points
   plus the JNI name strings, `libc++_shared.so` exporting `_Znwm`/`_ZdlPv` - and a fake glue file
   declaring the same native methods. Every case below exited non-zero with the expected message
   except the first:

   | Case | Verdict |
   |:--|:--|
   | complete synthetic payload, with glue and reference | **pass** (`exit 0`) |
   | `libSDL3.so` missing | rejected: "is missing libSDL3.so" |
   | `libc++_shared.so` missing | rejected |
   | an extra `libextra.so` in the payload | rejected: unused library |
   | a second `libSDL3_v2.so` | rejected: unused/duplicate SDL3 |
   | a real x86_64 `libz.so.1` as `libmain.so` | rejected: "is an x86_64 object … a desktop Linux library was staged" |
   | a non-ELF file | rejected |
   | wrong SONAME | rejected |
   | `libmain.so` without `SDL_main` | rejected |
   | `libmain.so` that also defines `SDL_Init` | rejected: second SDL3 implementation |
   | `libmain.so` importing C++ symbols without `libc++_shared.so` in `DT_NEEDED` | rejected |
   | `DT_NEEDED libstale.so` absent from the payload | rejected: unresolved dependency |
   | `DT_NEEDED libX11` | rejected: desktop library |
   | glue declaring a native `libSDL3.so` does not register | rejected: glue/native drift |
   | packaged object rebuilt after staging (`--reference`) | rejected: build id differs |

3. **The manifest/Activity assertions, simulated against the real manifest.** The new
   `theGameActivityIsDeclaredForSdl` contract was replayed over `AndroidManifest.xml`: the exact
   `configChanges` string is present on `ZeroHourActivity`, it is landscape and `singleInstance`,
   it carries no `LAUNCHER` category, and the helper now returns the full element (733 characters,
   including its `<intent-filter>`) rather than the first `/>`.
4. **The JNI contract, read from the pinned SDL3 source.** All 65 native methods declared by
   `android-project/app/src/main/java/org/libsdl/app/*.java` (12 files) are registered in
   `SDL_android.c` / `hid.cpp` at `fa2c02bb…`, which is what makes the derived check safe rather than
   a guess.
5. **Cross-references.** Every doc reference added here resolves; `grep` finds no remaining
   "SDL3 is static" statement in docs 16 and 18.
6. **The first CI run** (44806441122) did what no local check could: it built the engine for real.
   It found the `libSDL3.so` output-directory defect of §8.1, which is now fixed; the run after that
   fix is the one that has to be green before this phase can be called done.

### 8.1 The first CI run, and what it found

The first run of `ci-android-app.yml` on this branch (run 44806441122) reached the native build and
failed there:

```
libmain.so linked this run at 2026-11-20T20:16:37Z
-rwxr-xr-x 1 runner runner 278474016 Nov 20 20:16 build-android-arm64/libmain.so
##[error]the build did not produce build-android-arm64/libSDL3.so: SDL3 was not built shared
```

The message was misleading; the shared/static choice was right. CMake's
`if(NOT TARGET SDL3::SDL3-shared)` guard had passed, so the shared target existed and `libmain.so`
linked it. SDL3 is added as a subproject (`add_subdirectory(.../SDL3 ${CMAKE_BINARY_DIR}/SDL3)`), and
CMake puts a subproject's library in that subproject's binary directory - so the artifact was
`build-android-arm64/SDL3/libSDL3.so`, while this packaging (and `ci-android-test.yml`) look for it
beside the engine.

Fix: `GeneralsMD/Code/CMakeLists.txt` sets `LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"` on the
real target behind `SDL3::SDL3-shared` (read through `ALIASED_TARGET`, so an SDL3 rename cannot
silently drop it). Both artifacts are now produced in one directory, which is what every check in
this document assumes. Nothing else about the two-artifact design changed.

The lesson is the phase's own point: the first real link was the first thing that could contradict
the design, and it did - one directory deeper than expected.

## 9. What CI proves, and what it does not

`ci-android-app.yml` (now triggered by a push to this branch as well as by hand) builds the real
engine and SDL3, runs the verifier on the staged payload and on the APK's, and inspects the APK:

- `lib/arm64-v8a/` holds exactly `libmain.so`, `libSDL3.so`, `libc++_shared.so`, one ABI, no
  duplicates, exactly one SDL3 shared object;
- `libmain.so` is `ELF64`/`AArch64`/`DYN`, `SONAME libmain.so`, exports `SDL_main`, imports `SDL_*`
  and defines none but `SDL_main`, `DT_NEEDED` = system libraries + `libSDL3.so` +
  `libc++_shared.so`;
- `libSDL3.so` is `ELF64`/`AArch64`/`DYN`, `SONAME libSDL3.so`, exports `JNI_OnLoad` and the SDL3
  entry points, and needs Android system libraries only - before and after AGP's strip;
- every non-system `DT_NEEDED` of the packaged `libmain.so` is a file in the APK;
- the packaged libraries are the staged ones (build id), so nothing stale is shipped;
- the manifest still declares the three Activities, the launcher, `minSdk 28`/`target 35`,
  `arm64-v8a`, the two permissions and no storage permission.

It does not prove the game runs: there is no device and no Vulkan in CI. The first start on hardware
- `System.loadLibrary("SDL3")`, `JNI_OnLoad`, `SDL_main`, the Vulkan device - remains a device
checklist item.

## 10. Remaining blockers

1. **Nothing here has been executed end to end.** No NDK, SDK, Gradle or device exists in this
   workspace: the verifier was validated against synthetic ELF objects and the manifest assertions
   against the real manifest, but the first real run of `package-android.sh` and of the workflow is
   CI - and that first run is what found the `libSDL3.so` output-directory defect in §8.1.
2. **The stale-object check depends on a build id.** NDK links carry `.note.gnu.build-id` and AGP's
   `llvm-strip` keeps it, but if a toolchain ever drops it the verifier falls back to SONAME +
   `DT_NEEDED` + export set, which is weaker: an object rebuilt from unchanged sources would not be
   distinguishable (and would not need to be).
3. **The JNI contract check reads names, not signatures.** It catches a glue/native version
   mismatch, not a changed parameter list. A signature check would need `javap` output compared with
   the `JNINativeMethod` signature strings; not done.
4. **The glue's 65 native methods assume SDL3's default feature set** (HIDAPI included). A build that
   disabled `SDL_HIDAPI` would fail this check - correctly, because the glue would then call
   unregistered methods.
5. **`libandroidx.graphics.path.so` stays excluded.** The payload is exactly three libraries by
   design; a Compose screen that iterates a `Path` must revisit the exclusion and the CI assertion.
6. **No device-level verification of the two-library load order.** It is SDL3's documented contract
   and is now stated and checked statically, but no device has loaded `libSDL3.so` before
   `libmain.so` here.

## 11. Provenance

- Target repository `redaf12-Decode/Generals-Reforged`, branch `agent/android-sdl3-android-completion`,
  on top of Phase 1 (`5790462`).
- SDL3 facts read from the pinned tree `libsdl-org/SDL` @ `fa2c02bb6e21974a89ea9824bc53c9932abe5f9c`
  (3.4.16): `android-project/app/src/main/java/org/libsdl/app/*.java` (12 files, 65 native
  declarations), `SDLActivity.getLibraries()`/`getMainSharedObject()`/`getMainFunction()`,
  `src/core/android/SDL_android.c` (`JNI_OnLoad`, the `JNINativeMethod` tables),
  `src/hidapi/android/hid.cpp`, `src/dynapi/SDL_dynapi.sym`.
- Reference port `redaF12-Tech/GeneralsZH-Android-Port` read for the model only
  (`scripts/build/android/package-android-zh.sh`, `.github/workflows/build-android.yml`): its
  "fail loudly on a missing library, then verify the APK against the built library" pattern is the
  same idea; none of its code, and none of its DXVK/ANGLE/OpenAL/adrenotools dependencies, is used.
- No application build was run locally. §8 lists exactly what was executed.
