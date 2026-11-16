# 09 — NDK / API level and the crash-handler backtrace on Android

Resolves **question 3 of Section 11** in
[`../audit/01-target-architecture.md`](../audit/01-target-architecture.md#11-unresolved-questions)
("How should the POSIX crash handler's `backtrace()` (glibc `<execinfo.h>`) work under Bionic,
which lacks it?"). Read-only: no source, build or dependency file was changed. **No code was
changed** — this document records the decision and the evidence; the implementation is Phase 2.

## Decision

- **NDK r27c, `minSdkVersion` API 28, `targetSdk`/`compileSdk` 35, ABI `arm64-v8a`** — unchanged from
  the build scaffolding already present (`scripts/build/android/android.toolchain.cmake`).
- The crash handler's unwinder on Android is **`_Unwind_Backtrace` from `<unwind.h>`** (the Itanium
  C++ ABI unwinder that ships with the NDK's libc++/compiler-rt), **not** glibc's `backtrace()`.
- Symbolisation stays **in-process and allocation-free**, using `dladdr()` (available on Android
  since API 1) for the module and the nearest exported symbol, exactly as the POSIX handler does
  today. Android's `backtrace()`/`backtrace_symbols_fd()` are **not** used — see E2.

## Evidence

### E1 — The target's POSIX crash handler and stack dump use glibc `<execinfo.h>`

Two files build the stack trace today:

- `GameEngine/Source/Common/System/CrashHandlerPosix.cpp:77` `#include <execinfo.h>`;
  `:472` `backtrace(walked, MAX_FRAMES)`; `:720` `backtrace(frames, 4)` (priming the unwinder).
- `GameEngine/Source/Common/System/StackDumpPosix.cpp:39` `#include <execinfo.h>`;
  `:77` `backtrace(frames, wanted)`; `:92` `backtrace_symbols_fd(addresses, n, STDERR_FILENO)`;
  `:53-68` `dladdr()` for the module + symbol (`formatFrame`).

Both are compiled on POSIX (guarded by `IG_DEBUG_STACKTRACE`, which the build sets —
`CMakeLists.txt:1221`, `target_compile_definitions(gameengine PUBLIC IG_DEBUG_STACKTRACE Z_PREFIX)`;
`StackDump.h:33-35`). `StackDumpPosix.cpp` is listed at `CMakeLists.txt:1116`; `StackDump.cpp` (the
Windows body) at `:1124`. They are the same `Common/StackDump.h` interface with two bodies
(`StackDump.h:46-53`, `CMakeLists.txt:1092-1093`).

### E2 — Bionic's `<execinfo.h>` exists only at API 33+, and the target floor is API 28

Measured against the NDK r27c sysroot
(`/opt/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/execinfo.h`):

```c
#if __ANDROID_API__ >= 33
int backtrace(void* _Nonnull * _Nonnull buffer, int size) __INTRODUCED_IN(33);
char* _Nullable * _Nullable backtrace_symbols(...) __INTRODUCED_IN(33);
void backtrace_symbols_fd(...) __INTRODUCED_IN(33);
#endif /* __ANDROID_API__ >= 33 */
```

A compile probe confirms the boundary — `backtrace` is **undeclared** at `-DANDROID_API=28` and
declares cleanly at `33`:

```
$ aarch64-linux-android28-clang probe_bt.c
probe_bt.c: error: call to undeclared function 'backtrace'
$ aarch64-linux-android33-clang probe_bt.c   # ok
```

So on the API-28 floor the handler cannot call glibc-style `backtrace()`. Raising the floor to 33
just to keep it would cut off Android 9–12 devices for no benefit; the alternative below works at
API 28 and is the same kind of API (a frame-address walk).

### E3 — `dladdr()` is available at API 28 (symbolisation needs no API bump)

`dlfcn.h` in the NDK r27c sysroot declares `int dladdr(const void* addr, Dl_info* info)` with **no
`__INTRODUCED_IN` guard** (i.e. available from API 1). A compile probe at
`aarch64-linux-android28-clang` succeeds. So the in-process symbolisation the handler already does
(module + nearest exported symbol, `StackDumpPosix.cpp:53-68`) ports directly.

Note the limit the target already documents (`StackDumpPosix.cpp:26-30`): in-process `dladdr` gives
**module + exported symbol + offset**, not file/line — the target accepts this and resolves lines
offline (a launcher that symbolicates "would have to add it"). The same limit applies on Android,
with the same mitigation: keep raw addresses, symbolicate offline against the unstripped `.so` /
`libmain.so` from the build (with a symbol server or the NDK's `llvm-symbolizer`).

### E4 — `_Unwind_Backtrace` is the portable unwinder and works at API 28

`<unwind.h>` is provided by the NDK's Clang resource headers
(`.../lib/clang/18/include/unwind.h`), declaring:

```c
typedef _Unwind_Reason_Code (*_Unwind_Trace_Fn)(struct _Unwind_Context *, void *);
_Unwind_Reason_Code _Unwind_Backtrace(_Unwind_Trace_Fn, void *);
```

A compile probe at `aarch64-linux-android28-clang` succeeds. `_Unwind_Backtrace` is the Itanium C++
ABI unwinder used by the whole Android toolchain (libc++ exception handling and `-funwind-tables`),
so it is present on every Android version the port targets and needs no API level. It walks the
stack and hands each frame's `_Unwind_Context` to a callback; `_Unwind_GetIP(ctx)` yields the
frame's program counter, which is exactly what `backtrace()` returns and what `dladdr()` consumes.

## Implementation shape (Phase 2 — not done here)

Replace the two glibc calls with one small internal helper, leaving the public
`Common/StackDump.h` interface unchanged:

- `FillStackAddresses` (`StackDumpPosix.cpp:70`) and `StackDumpFromAddresses` (`:85`): collect
  addresses with `_Unwind_Backtrace` instead of `backtrace`. `_Unwind_Backtrace` has no "skip"
  parameter, so the helper counts frames itself and drops the first `skip+1` (the same semantics
  `FillStackAddresses` already implements for `backtrace`'s leading frames).
- The no-callback branch (`:92`, `backtrace_symbols_fd`) becomes: format each address with the
  existing `formatFrame` (`:53`, `dladdr`-based) and `write(2, ...)` to `STDERR_FILENO` — the
  handler must not allocate, which `formatFrame` already respects (stack buffer, `snprintf`).
- `CrashHandlerPosix.cpp:472`/`:720`: same swap; the priming call (`:715-720`, "backtrace()'s
  unwinder, whose first call allocates") stays useful because `_Unwind_Backtrace` also initialises
  lazily — keep a priming walk at install time.
- Keep `execinfo.h` **out** of the Android build; the `#include` becomes guarded (e.g.
  `#if defined(__linux__) && !defined(__ANDROID__)` → glibc path, `#else` → `_Unwind_Backtrace`),
  or the two POSIX files get a small `#ifdef` around the unwinder calls only.

This is a **mechanical** change to two functions plus the include guards; it does not alter the
crash-report format, the `Common/StackDump.h` interface, or any caller.

### Android-side alternative considered and not required

Android also offers `android_get_exported_namespace`-based unwinding, `libunwindstack`, or a
`debuggerd` tombstone. Those are heavier (extra dependency / out-of-process) and the target's design
already accepts module+offset in-process traces with offline symbolication (E3), so
`_Unwind_Backtrace` + `dladdr` is the minimal, faithful port. A tombstone remains available from the
OS for hard crashes regardless.

## Other libc differences to watch (recorded, decided in Phase 2)

- **No `fork()`/`execv()`** on Android for spawning helpers; the target's own rule (see
  `cmake/DetectPlatform.cmake` header comment: "no fork() or execv()"). Anything that shells out
  (font tools, `zenity` dialogs) must not be used on Android; SDL's dialog replaces the picker
  (doc 08).
- **No `/proc` guarantees** — `posixpath.cpp:118-130` already treats missing `/proc` as merely
  disabling a listing reuse, which is the right posture on Android.
- **`getauxval`/CPU detection** — `cpudetect.cpp` is the file the build's own comment names as the
  example of a platform file that must `#error` when unported (`CMakeLists.txt:22-27`); its Android
  answer is a Phase-2 item.

## Open items handed to Phase 2

1. Implement the `_Unwind_Backtrace` swap in `StackDumpPosix.cpp` / `CrashHandlerPosix.cpp` and the
   `execinfo.h` guard.
2. Confirm `-funwind-tables` (or `-fno-omit-frame-pointer`) is on for the Android build so
   `_Unwind_Backtrace` walks reliably; the POSIX build already relies on unwind tables.
3. Decide offline symbolication tooling for Android traces (NDK `llvm-symbolizer` against the
   unstripped `libmain.so`, or a symbol server).
4. Give `cpudetect.cpp` its Android answer.

## Provenance

- Paths relative to the repository root, read at `c91ab1a` (branch `docs/android-port-audit-01`;
  default `main` at `109962c`).
- NDK/API facts measured against the local NDK r27c sysroot and Clang 18 resource headers with
  compile probes (commands and error text quoted above).
- Read-only: no source, build or dependency file was changed.
