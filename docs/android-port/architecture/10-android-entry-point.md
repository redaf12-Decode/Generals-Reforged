# 10 — Android entry point and the shared-library model

Resolves **question 4 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("What is the Android entry point — `main()` via SDL3's Android glue, or an `SDL_Activity`/JNI
`SDL_main` shared library?"). Read-only: no source, build or dependency file was changed. No code
was changed — the implementation is Phase 2.

## Decision

The Android entry point is **the existing `main()` in `Main/PosixMain.cpp`, built into a shared
library (`libmain.so`) and called by SDL3's `SDLActivity` through the `SDL_main` symbol** — the
standard SDL3-on-Android model. **The body of `main()` is not rewritten**: `PosixMain.cpp`'s
`main(int argc, char *argv[])` stays as it is, and SDL3's `SDL_main.h` turns it into the exported
`SDL_main` entry point on Android. No new entry-point file, no separate Android `main`, no change to
`PosixMain`'s arguments contract.

## Evidence

### E1 — How SDL3 on Android starts a game

SDL3 on Android does **not** call the app's `main()` from a native `main`. It ships a Java activity
(`SDLActivity.java`) that loads a native shared library and calls an exported entry function:

- `SDLActivity.java:255-260`: `getMainSharedObject()` + `getMainFunction()` are passed to
  `nativeRunMain(library, function, arguments)`.
- `SDLActivity.java:268-275`: the default library is **`libmain.so`**.
- `SDLActivity.java:283-285`: the default function is **`SDL_main`**.
- `SDL_android.c:827-852`: `nativeRunMain` `dlopen`s the library (`RTLD_GLOBAL`) and
  `dlsym`s the function, then calls it with an argv built from the activity's arguments.

So the contract is: **a shared library exporting `SDL_main`, loaded by `SDLActivity`.**

### E2 — `SDL_main.h` provides exactly that contract on Android

`SDL_main.h:179-195`:

```c
#elif defined(SDL_PLATFORM_ANDROID)
    #define SDL_MAIN_NEEDED
    /* ... exports an `SDL_main()` function (to be called from Java), but don't
       implement a native `int main(int argc, char* argv[])` ... */
    #define SDL_MAIN_EXPORTED
```

`SDL_MAIN_EXPORTED` makes `SDL_main.h`/`SDL_main_impl.h` rename the app's `main` to `SDL_main`
and **not** emit a native `main`. The target's entry file already includes the header first:

```cpp
// Main/PosixMain.cpp:47
#include <SDL3/SDL_main.h>   // SDL3's main: on macOS and Linux an ordinary main
```

On macOS/Linux this header leaves `main` as an ordinary `main`; on Android the same include makes
that same function the exported `SDL_main`. **Because the include is already first and the function
is already spelled `int main(int argc, char *argv[])`** (`PosixMain.cpp:438`), the Android entry
point is produced by the build target alone — no source change to `PosixMain.cpp`.

### E3 — What changes is the build target, not the code

Today `PosixMain.cpp` is built into an **executable**:

```cmake
# CMakeLists.txt:2168-2172 (inside if(ZH_PLATFORM_POSIX))
add_executable(generals ${CMAKE_CURRENT_SOURCE_DIR}/Main/PosixMain.cpp)
target_include_directories(generals PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/Main ${GENERATED})
set_target_properties(generals PROPERTIES CXX_STANDARD 17)
target_link_libraries(generals PRIVATE sdldevice installroot)
```

The Windows path builds `generals` as a `WIN32` executable from `Main/WinMain.cpp`
(`CMakeLists.txt:1998`). For Android the same source set must become a **shared library named
`libmain.so`** — `add_library(main SHARED ...)` with `OUTPUT_NAME main`, so the artifact is
`libmain.so` and `SDLActivity` finds it by its default name (E1). This is a target-definition change
gated on `ZH_PLATFORM_ANDROID`; it adds no source file and changes no C++.

### E4 — `PosixMain` already avoids the things Android forbids before SDL starts

`main()` does, before the engine exists (`PosixMain.cpp:438-520`):

- `installCrashHandlers()` — fine (doc 09).
- `setlocale(LC_TIME, "")` — fine on Bionic.
- The `SDL_SetAppMetadata` call is **already** guarded `#if defined(__linux__)`
  (`PosixMain.cpp:448-455`), so Android (which never sets `ZH_PLATFORM_LINUX`, and where
  `__linux__` is also not defined by the NDK's Clang for `-target aarch64-linux-android`) does not
  run the Linux-only app-id branch. This is the correct existing posture — see
  [`12-platform-macro-audit.md`](12-platform-macro-audit.md).
- `chooseInstallRoot()` (doc 08), `chdir(root)`, `PosixPath_Set_Overlays`,
  `PosixPath_Set_Root_Read_Only` — all POSIX file logic, portable.
- It includes `<SDL3/SDL.h>` and `<SDL3/SDL_main.h>` and uses SDL's folder dialog and message box —
  SDL is already the window/event layer, so this is consistent.

No `fork()`/`execv()` is used in `main` (the port's own rule, `cmake/DetectPlatform.cmake` header).

### E5 — The reference port confirms the model

The reference port builds the game as **`libmain.so` loaded by SDL3's `SDLActivity`**
(`ANDROID_PORT.md:40-48, 74`: "game = `libmain.so` loaded by SDL3's `SDLActivity`";
"Android: `z_generals` builds as `libmain.so` (SDLActivity convention) instead of an executable"),
with `GeneralsZHActivity extends SDLActivity` for the Java shell. This is the same model this
decision adopts; the target needs only the shared-library target and a thin Java activity, not a
rewritten entry point.

## Consequences

- **`PosixMain.cpp` is unchanged.** The Android entry is the same `main()`, exported as `SDL_main`
  by `SDL_main.h`.
- **Build change (Phase 2):** under `ZH_PLATFORM_ANDROID`, build the `PosixMain.cpp` source set as
  `libmain.so` (shared library, `OUTPUT_NAME main`) instead of the `generals` executable. The
  desktop `generals` executable stays for Windows/macOS/Linux.
- **Java shell (Phase 2):** a minimal `Activity extends SDLActivity` (the reference port's
  `GeneralsZHActivity`) to set the app name, icon and (if needed) the folder-picker flow of doc 08.
  SDL3's `SDLActivity.java` ships in the vendored tree
  (`Libraries/Source/SDL3/android-project/app/src/main/java/org/libsdl/app/SDLActivity.java`) and is
  reused, not modified.
- **`argv`:** SDL3 passes the activity's arguments as `argv` (`SDL_android.c:846-852`), so
  `PosixMain`'s `-root`, `-win` etc. parsing still works; on Android they will normally be empty and
  the install root comes from the picker/`Registry.ini` (doc 08).

## Open items handed to Phase 2

1. Add the `ZH_PLATFORM_ANDROID` branch that builds `PosixMain.cpp` as `libmain.so`
   (`add_library(main SHARED ...)`, `OUTPUT_NAME main`), leaving the `generals` executable for the
   other platforms.
2. Add the Gradle shell and the thin `Activity extends SDLActivity`; set the app id/icon.
3. Confirm `PosixMain`'s early `SDL_SetAppMetadata`/`__linux__` branch does not run on Android (it
   must not; `__linux__` is not defined by the NDK Clang for Android targets — verify in the build).
4. Decide how `HOME` (used by `findUserDataDirectory`, doc 08) is set before `main` runs on Android
   — the Java side or an early `setenv` in `main`.

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- SDL3 facts read from the vendored tree `Libraries/Source/SDL3` (3.4.16).
- Reference-port claims cite `redaF12-Tech/GeneralsZH-Android-Port` @ `51966b1`,
  `docs/port/ANDROID_PORT.md` (design guidance only).
- Read-only: no source, build or dependency file was changed.
