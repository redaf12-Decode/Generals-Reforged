# Android audio, storage and game services

Phase 5 gives the port the three platform services the desktop gets from the operating system and
Android does not hand over for free: sound that survives an application being backgrounded, a place
to write the player's data that Android will always allow, and the small set of platform calls the
game makes out to the OS (open a web page, keep the screen awake, hold the Wi-Fi radio for the LAN
lobby). It is the design of record for
`GameEngineDevice/{Include,Source}/SdlDevice/Common/SdlAndroidPlatform.{h,cpp}`,
`Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp`, and
`GameEngine/Include/Common/EarlyOptions.h`.

Nothing here changes a desktop build. Every Android branch is behind `ZH_ANDROID`
(`Platform/PlatformPredicates.h`), which is `0` on Windows, Linux and macOS, and the storage
convention is chosen by `PLATFORM_USER_FOLDERS`, which is `USER_FOLDERS_ANDROID` only when
`__ANDROID__` is defined.

## 1. Audio

### What was inspected

The game's audio is the Miles 6.5 API, and off Windows that API is implemented on miniaudio
(`Libraries/Source/WWVegas/Miles6/miniaudio/miles_miniaudio.cpp`; the Windows path is XAudio2). The
engine's own layer, `MilesAudioManager.cpp`, is unchanged and calls `AIL_*`; it never learns which
backend is under it. The manager already has the two volume controls that matter to a phone:

- `AudioManager::pauseAudio` / `resumeAudio` - the game's own mute (a pause menu, a lost match);
- the focus path - `AudioManager::loseFocus` / `regainFocus` - which the SDL window's focus events
  drive on the desktop.

Neither of those stops the output device. They silence or duck the mix; the device keeps running.
On Android that is the wrong behaviour when the app leaves the foreground: the OS suspends the whole
application, and a stream that keeps running there is either cut off by the system (a log full of
underruns) or, worse, keeps playing over whatever the player switched to.

### The device, held open but stopped

`MSS/mss_ex_device.h` declares a small extension, `AIL_ex_device_pause` / `AIL_ex_device_resume` /
`AIL_ex_device_is_open`, implemented only by the miniaudio backend. `pause` calls `ma_device_stop`
and `resume` calls `ma_device_start`. That is deliberately not `AIL_shutdown` + `AIL_quick_startup`:
stopping the miniaudio device leaves it open, so every sample's voice and every stream's decoder stay
exactly where they are and the music picks up mid-phrase. Tearing the device down and rebuilding it
would restart every sound from the beginning and re-open the hardware, which is slow and, on a
phone, an audible glitch.

The functions are no-ops on a device that was never opened and on the null backend of an offline mix
(`AIL_ex_offline_mix`), so the caller keeps no state of its own.

### The lifecycle decision

The application lifecycle is not a game concept, so it is kept out of the game: `SdlAndroidPlatform`
holds a tiny state machine, and its *decisions* are free functions of their arguments with no SDL and
no JNI:

```
SdlAndroidLifecycle            { backgrounded, devicePaused, terminated }
SdlAndroidLifecycle_begin()    -> foreground, playing, alive
SdlAndroidLifecycle_onEvent(s, e)
SdlAndroidLifecycle_pausesDevice(before, after)
SdlAndroidLifecycle_resumesDevice(before, after)
```

`devicePaused` is derived from the state on every event - the device is stopped exactly while the app
is not the one the player is looking at - which is what makes a repeated
`SDL_EVENT_WILL_ENTER_BACKGROUND` a no-op instead of a second stop. Android posts the pause event
more than once for one backgrounding, so this is not a hypothetical: a state machine that acted on
the event rather than the state would stop the device twice and start it once.

On the device, an SDL event watch installed by `SdlAndroidPlatform_install` maps the five lifecycle
events onto the three the machine knows and carries out its decision:

| SDL event | meaning | action |
|:--|:--|:--|
| `WILL_ENTER_BACKGROUND`, `DID_ENTER_BACKGROUND` | app leaving the foreground | `AIL_ex_device_pause()` |
| `WILL_ENTER_FOREGROUND`, `DID_ENTER_FOREGROUND` | app interactive again | `AIL_ex_device_resume()` |
| `TERMINATING` | OS taking the app away | `AIL_ex_device_pause()`, never resume after |

The `SDL_EVENT_TERMINATING` case must be handled in an event watch (not the normal queue), which is
why this is a watch rather than a polled event.

### The Android backends

`AIL_quick_startup` names the context's backend list explicitly on Android instead of letting
miniaudio walk its whole list: **AAudio** first and **OpenSL ES** after it. AAudio is the modern API
(Android 8+), low latency, and the one that reports focus and interruptions; OpenSL ES is on every
device back to API 21 and is the fallback where a vendor's AAudio is missing or refuses. Naming the
list also keeps miniaudio from probing the desktop Linux backends - ALSA, JACK, PulseAudio - on
Android at all.

Two backend-specific settings are applied to the device config, and only on Android:

- **AAudio usage `game`, content type `music`** - how the OS routes and ducks the stream. A game's
  audio is media, not a ringtone or an alarm, and the content is music so a voice call ducks it rather
  than muting it. Left unset they fall to AAudio's own default, which is not the same on every device.
- **OpenSL stream type `media`** - so the sound follows the media volume, the stream AAudio's usage
  maps to as well.
- **`noAutoStartAfterReroute = MA_FALSE`** - let the OS re-open the stream after a reroute (a headset
  plugged in, a Bluetooth device connecting) instead of leaving the device stopped until the app
  restarts it.

A device notification callback is installed that logs started/stopped/rerouted/interruption events.
The engine's own pause and resume are driven by the SDL lifecycle above; on Android the OS owns the
stream across a reroute or an interruption and re-opens it itself, so those are logged, not acted on.

A bug this phase fixed: the AAudio and OpenSL fields live in **`ma_device_config`**, not
`ma_context_config` (the context holds only backend-agnostic settings). Applying them to the context
would have compiled against the wrong union member and silently done nothing.

## 2. Storage

### What was inspected

Every path the engine writes goes through `EarlyOptions.h`'s `findUserDataDirectory`: `Options.ini`,
saves, replays, the crash report, `Registry.ini`'s install path, the one-copy lock. On the desktop
that is `HOME` and `XDG_DATA_HOME` under the XDG convention, or Application Support on macOS.

Android has neither. Bionic's environment carries no `HOME` and `getpwuid` has no passwd database, so
`findHomeDirectory` answers nothing and the XDG resolution answers nothing with it. The one directory
an Android app may always write is its own private storage: `Context.getFilesDir()`, which SDL3
exposes as `SDL_GetAndroidInternalStoragePath()`.

### The convention

`EarlyOptions.h` gains a fourth `UserFolderConvention`, `USER_FOLDERS_ANDROID`, and
`PLATFORM_USER_FOLDERS` becomes it when `__ANDROID__` is defined. The leaf directory sits **directly**
in the private storage - neither Application Support nor `.local/share`:

```
/data/user/0/<package>/files/Command and Conquer Generals Zero Hour Data
```

`composeUserDataDirectory` gains the one Android branch; `findDesktopDirectory` and
`composeDesktopDirectory` answer nothing for the convention, because Android has no Desktop and the
shared-storage locations (Downloads, Documents) are not the app's to write without a permission and a
scope it does not ask for. The replay menu's "copy to Desktop" therefore keeps the replay in the user
data directory rather than pretending to copy it somewhere.

`Main/PosixMain.cpp` (`establishAndroidStorage`) points `HOME` at
`SDL_GetAndroidInternalStoragePath()` before the engine starts, so the existing XDG resolution names
the directory with no new code path in the engine. It sets `HOME` only when it is unset, so a harness
that brings one keeps it. SDL's JNI context is ready before `SDL_main` runs (`SDLActivity` calls
`nativeSetupJNI` first), so this works this early.

## 3. Game services

Three platform calls the game makes out to the OS, all in `SdlAndroidPlatform`:

- **Open a web page.** The online login's "Create Account" calls `OnlineOpenURL`
  (`GameNetwork/Online/OnlinePeer.cpp`), which accepts only `https://` and `http://` (the address
  comes from the server, and `ShellExecute` would run a program path or open a `file://` as readily
  as a page). Off Windows it forwards to the `TheOnlineOpenURLHook`, which `SdlGameEngine` sets to
  `SDL_OpenURL`. SDL3's Android backend implements that with the activity's `openURL` method
  (`Android_JNI_OpenURL`, an `ACTION_VIEW` intent), so the same hook works on Android with no new
  code - the engine already routes it through SDL.
- **Keep the screen awake.** `SdlAndroidPlatform_install` calls `SDL_DisableScreenSaver()`. SDL
  already disables the screensaver unless `SDL_HINT_VIDEO_ALLOW_SCREENSAVER` is set; asking for it
  again makes the intent explicit here rather than inherited.
- **Hold the Wi-Fi radio for the LAN lobby.** Android's Wi-Fi power save drops broadcast and
  multicast datagrams addressed to an application until it holds a `WifiManager.MulticastLock`, and
  the lobby's broadcast listener (`LANAPI::listenForBroadcasts`) would otherwise hear nothing. The
  lock is taken through JNI and held for the life of the process, released in shutdown.

The multicast lock needs `android.permission.CHANGE_WIFI_MULTICAST_STATE` in the application
manifest, which this repository does not carry yet (the Gradle/APK shell is the later packaging
phase). Where the permission is absent, `createMulticastLock` throws, and the code logs it and
carries on rather than failing the start; the LAN lobby still works between devices on a network
that does not drop the packets. The JNI is written in the C++ member-function form
(`env->GetObjectClass`, `env->CallObjectMethod`), not the C struct-of-function-pointers form
(`(*env)->GetObjectClass(env, ...)`): `JNIEnv` is a class under C++, and the C form does not compile
there.

## Where it is installed

`SdlGameEngine::createWindow` calls `SdlAndroidPlatform_install` once SDL's video and events are up,
and `SdlGameEngine_releaseWindow` calls `SdlAndroidPlatform_shutdown` before SDL's video goes away.
Both are no-ops off Android, so the desktop needs no `#if` of its own. On the desktop, the window
focus events (`SDL_EVENT_WINDOW_FOCUS_GAINED` / `LOST`) are what pause and resume the audio, exactly
as before.

## Testing and CI

The lifecycle decision is pure, so it is tested on the host where the SDL/JNI half is compiled out
(`ZH_ANDROID == 0`): `GeneralsMD/Code/Tests/test_sdl_android_platform.cpp`, registered as the
`test_sdl_android_platform` ctest entry (it links `sdldevice`, which globs `SdlAndroidPlatform.cpp`).
It checks that a fresh install is in the foreground, that one backgrounding stops the device once and
one return starts it once, that a second event in the same state does nothing, that a foreground
event never starts a device this layer did not stop, and that termination stops a playing app once
and nothing resumes it afterwards.

The storage convention is tested on every host through the convention parameter in
`Tests/test_userfolders.cpp`: the private-storage path, that `XDG_DATA_HOME` means nothing to it,
that the override still wins, that no home means no answer, and that Android has no Desktop.

What needs the game on a device - the actual `ma_device_stop` on real hardware, the OS re-opening the
stream after a reroute, the multicast lock over real Wi-Fi, the `ACTION_VIEW` intent - is not
simulated. The `CI Android Test` workflow covers that the code is *what was built*: after the engine
links as `libmain.so`, it checks that `miles_miniaudio.cpp.o` is AArch64, defines the `AIL_ex_device_*`
entry points and references `ma_device_stop` (called only by the pause/resume half), that
`SdlAndroidPlatform.cpp.o` defines `SdlAndroidPlatform_install/shutdown` and references
`SDL_AddEventWatch` and `SDL_GetAndroidActivity` (called only by the `ZH_ANDROID` half), and that
`PosixMain.cpp.o` references `SDL_GetAndroidInternalStoragePath`. A symbol only the Android half calls
cannot be satisfied by a desktop stub, so a green run means the Android halves were compiled.

## What is deliberately not here

- No APK packaging and no `AndroidManifest.xml` - the multicast-lock permission is a packaging-phase
  item, recorded above.
- No OpenAL or audio capture work; the engine's audio is the Miles surface on miniaudio.
- No external-storage or scoped-storage handling: the app uses only its own private directory, which
  needs no runtime permission.
