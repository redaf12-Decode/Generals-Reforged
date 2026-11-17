# Android lifecycle, touch and audio integration

Phase 5 wires the Android application lifecycle into the two subsystems that already exist and
were, until now, unaware of each other: the platform layer
(`GameEngineDevice/{Include,Source}/SdlDevice/Common/SdlAndroidPlatform.{h,cpp}`) that owns the
background/foreground watch and the audio device, and the touch layer
(`SdlDevice/GameClient/SdlTouch.{h,cpp}`) that owns the gesture state machine. It builds on
[`14-native-touch-input.md`](14-native-touch-input.md) (the gesture machine) and
[`15-audio-storage-and-game-services.md`](15-audio-storage-and-game-services.md) (the lifecycle
state machine and the device pause/resume).

Nothing here changes a desktop build. Every Android branch is behind `ZH_ANDROID`
(`Platform/PlatformPredicates.h`), which is `0` on Windows, Linux and macOS.

## The defect

When the app leaves the foreground, SDL delivers **no** `SDL_EVENT_FINGER_UP` for a finger that was
down. The platform layer's lifecycle watch stops the audio device and nothing else, and the touch
layer's `SdlTouch_reset()` — documented for exactly this ("a window change") — had **no production
caller** at all: the only call site was the host unit test.

The consequence is a stale gesture that survives the backgrounding. A finger held when the player
switches away leaves the state machine in `PENDING`, `PANNING`, `SELECTING`, `PLACING`, `TARGETING`
or `UI_PRESS`. When the player returns and touches the screen again, `onFingerDown` is entered with
the phase already past `IDLE`, so the new finger is treated as the *second* finger of the old
gesture — a two-finger pan/zoom where the player made a single tap, a camera that pans from a
position the player never chose, or a mouse button left latched down in the engine's message stream.

### Why SDL does not cancel the touch

`SDL_androidevents.c`'s `Android_OnPause` (SDL3 3.4.16, commit `fa2c02b`) calls
`SDL_OnApplicationWillEnterBackground` / `SDL_OnApplicationDidEnterBackground`; the former sends
`SDL_EVENT_WINDOW_MINIMIZED` to each window and clears the keyboard focus, and neither touches the
touch device. The Android video driver implements `Android_InitTouch` / `Android_QuitTouch` and
defines no `ResetTouch` (`SDL_androidvideo.c`), and `SDL_OnWindowHidden` does not walk the fingers.
So no `SDL_EVENT_FINGER_CANCELED` is synthesized on a backgrounding and the engine must drop the
gesture itself.

## The decision

The gesture is dropped on exactly the transition that stops the audio device — the app ceasing to
be interactive — and once, so a repeated `WILL_ENTER_BACKGROUND` (Android posts the pause event more
than once for one backgrounding) is a no-op. That transition is the existing pure predicate's
mirror, `SdlAndroidLifecycle_cancelsInput`:

```
SdlAndroidLifecycle_cancelsInput(before, after)  ->  !before.devicePaused && after.devicePaused
```

It is a free function of its arguments with no SDL and no JNI, so it is tested on the host where the
`ZH_ANDROID` half is compiled out. It is `TRUE` on the first background event and on the termination
of a playing app, and `FALSE` on a repeated background event and on any foreground return.

## The watch

`SdlAndroidPlatform`'s lifecycle event watch, after it stops or starts the device, asks
`SdlAndroidLifecycle_cancelsInput` and, when it answers `TRUE`, calls `SdlTouch_reset()`. The watch
already runs before SDL blocks the app on the background event (`Android_OnPause` blocks *after* the
event is queued, and the watch sees it while it is queued), so the reset happens while the app is
still running.

## Resolving, not merely dropping

A gesture can owe the engine a mouse button-up that no `FINGER_UP` will now deliver. `SdlTouch_reset`
therefore resolves the gesture as a **cancellation** — the same path a `SDL_EVENT_FINGER_CANCELED`
takes — before it clears the state:

* `UI_PRESS` releases the held button at its anchor;
* `SELECTING` sends the button-up `SelectionTranslator` needs to clear its drag lock;
* `PLACING` backs out of the placement (`placeBuildAvailable(NULL, NULL)`) so the anchor is not left
  committed with no way to resolve it;
* `PENDING` and `TARGETING` do nothing, because a canceled touch must not become a committed tap or
  order;
* `PANNING` does not start momentum, because only a deliberate release should leave the camera
  coasting — a canceled pan simply stops.

The last point is the one behavioural change to `onFingerUp`: the momentum start is now gated on
`!canceled`. A canceled two-finger gesture already collapses to a single pan without momentum.

## What is deliberately not here

* **Vulkan device-loss handling.** SDL's Vulkan GPU backend already recreates the swapchain on
  `VK_ERROR_OUT_OF_DATE_KHR` / `VK_ERROR_SURFACE_LOST_KHR` (`SDL_gpu_vulkan.c`), and Android destroys
  and recreates the native window across a backgrounding, which SDL reports as a suboptimal or
  out-of-date present; the port relies on SDL's own path rather than adding a second one. A real
  device is needed to exercise it, and there is none in CI.
* **The audio lifecycle on a real device.** The `ma_device_stop` / `ma_device_start` pair, the OS
  re-opening the stream after a reroute, and the multicast lock over real Wi-Fi are not simulated.
* **Keyboard and focus reset.** SDL clears the keyboard focus in
  `SDL_OnApplicationWillEnterBackground` (`SDL_SetKeyboardFocus(NULL)`), so a key held across the
  backgrounding does not stay latched; the port adds nothing there.

## Testing and CI

The decision is pure, so it is tested on the host (`ZH_ANDROID == 0`):
`GeneralsMD/Code/Tests/test_sdl_android_platform.cpp` (the `test_sdl_android_platform` ctest entry,
which links `sdldevice`, the target that globs `SdlAndroidPlatform.cpp`). The new checks: the first
background event takes the fingers once, a repeated one does not, a foreground return takes none,
the next backgrounding takes them again, a foreground event from a fresh install takes none, and a
termination takes them once and nothing after it resumes.

The event plumbing needs the game on a device, and the test says so rather than pretend to cover it.
What the `CI Android Test` workflow covers is that the Android half *compiles and is what was built*:
after the engine links as `libmain.so`, the Phase-5 step checks that `SdlAndroidPlatform.cpp.o` is
AArch64, defines `SdlAndroidPlatform_install/shutdown`, and references `SDL_AddEventWatch`,
`SDL_GetAndroidActivity` and `SdlTouch_reset` — the last a symbol only the touch layer defines, so a
desktop stub could not satisfy it.
