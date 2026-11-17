# Native Android touch input

Phase 4 adds a touch input layer to the SDL3 device. It turns battlefield gestures into the
orders the engine already knows how to execute, and it leaves the desktop mouse and keyboard
paths untouched. This document records what was inspected, what was built, and why each decision
was made. It is the design of record for the touch code under
`GeneralsMD/Code/GameEngineDevice/{Include,Source}/SdlDevice/GameClient/`.

## Why a touch layer at all

The engine's input reaches game logic through the message stream. `Mouse::createStreamMessages()`
(`GameEngine/Source/GameClient/Input/Mouse.cpp`) appends `MSG_RAW_MOUSE_POSITION` and
`MSG_RAW_MOUSE_*_BUTTON_*` messages, and the translator chain
(`WindowTranslator` 10, `MetaEventTranslator` 20, `HotKeyTranslator` 25, `PlaceEventTranslator` 30,
`GUICommandTranslator` 40, `SelectionTranslator` 50, `LookAtTranslator` 60, `CommandTranslator` 70,
`HintSpyTranslator` 100) turns them into selections and orders.

That chain is written around a pointer: something that has a position even when no button is
pressed, that hovers, that can be parked near a screen edge, and whose button releases always
arrive. A finger has none of those properties. Synthesizing a mouse from touches is what the
reference port did first, and every control bug it reports came out of that gap. This phase does
not repeat it.

The engine's decision-making does not require a pointer. It is already reachable from a screen
point and a world position, and one part of the engine already calls it that way: a click on the
radar minimap issues real orders through `evaluateContextCommand()` with no `MSG_RAW_MOUSE_*` at
all (`ControlBarCallback.cpp`). The touch layer is that same path, for the battlefield:

```
TheTacticalView->pickDrawable()          screen point -> object under the finger
TheTacticalView->screenToTerrain()       screen point -> world position
TheGameClient->evaluateContextCommand()  (object, world) -> the order that would be issued
TheInGameUI->selectDrawable() et al      selection
```

`evaluateContextCommand` in `EVALUATE_ONLY` mode answers "would this be a legal order" without
issuing it. That is the source of truth for target feedback.

## Two mechanisms, on purpose

The touch layer uses two mechanisms, each where it is the right one:

* **Native resolution** for a tap on the battlefield. The tap is resolved against the engine's own
  rules (pick, evaluate, select or order). No pointer is invented.
* **A real click** where the engine's own dispatch is the only correct answer: a tap on a GUI
  widget, a committed building placement, an area-selection drag, and the commit of an armed
  command. Each of those is a genuine click in the engine's model, and reimplementing the engine's
  rules to avoid the click would be the bug, not the fix.

A tap on the control bar or a menu is a click and always was; the window manager's handling of it
is correct and unchanged.

## Gesture state machine

The gesture machine lives in `SdlTouch.cpp`. Its phases:

| Phase | Entered when | Means |
|:--|:--|:--|
| `IDLE` | no finger tracked | — |
| `PENDING` | finger 1 lands on the battlefield | gesture not yet classified; nothing sent |
| `PANNING` | finger 1 moves past the dead zone | direct camera pan |
| `MOMENTUM` | a fast pan is released | camera coasts, decelerating |
| `TWOFINGER` | a second finger lands | centroid pan + pinch zoom + twist rotate |
| `PLACING` | a drag past the dead zone with a build pending | rotate the placement ghost |
| `SELECTING` | held past `SELECT_HOLD_MS`, then dragged | area selection box |
| `TARGETING` | finger lands while a GUI command is armed | the finger is the aiming reticle |
| `UI_PRESS` | finger lands on a GUI widget | a real left press, held at the anchor |

Thresholds, each its own constant:

* `LONG_PRESS_MS` 600 — press-and-hold then release cancels/deselects (the right-click gesture).
* `SELECT_HOLD_MS` 250 — hold then drag starts an area selection. Shorter than the long press
  because it is a different gesture (drag vs. release) and reusing 600 ms felt laggy.
* `TAP_DEAD_ZONE_PX` 16 — the only thing separating a tap from a drag; also Android's touch-slop
  range.
* `DOUBLE_TAP_MS` 350, `DOUBLE_TAP_DIST_PX` 40 — the double tap.
* `TWO_FINGER_TAP_MAX_PX` 24 — a two-finger tap (cancel) rather than a pan/zoom.
* `MOMENTUM_*` — the coasting envelope: a released pan coasts until it slows below the stop speed.
* `ZOOM_PX_PER_TICK` 40 — a pinch of this many pixels is one wheel notch.

The state machine is a plain struct, and the *classification* it depends on is a set of free
functions of their arguments in `SdlTouch.h` (`SdlTouch_withinTapDeadZone`, `SdlTouch_isDoubleTap`,
`SdlTouch_isTwoFingerTap`, `SdlTouch_momentumStarts/Ends`, `SdlTouch_zoomNotchesForPixels`,
`SdlTouch_classifyPendingDrag`, `SdlTouch_isTouchMouseEvent`) with no SDL and no engine state, so
`Tests/test_sdl_touch.cpp` drives them on the host.

## Camera control

Pan projects the finger's previous and current screen position onto the terrain with
`View::screenToTerrain()` and moves the camera by the world-space difference. That is dimensionally
exact by construction and adapts to zoom, camera angle and resolution, unlike a fixed
pixel-to-world constant. Zoom is continuous and calibrated against the same sensitivity the wheel
already used: `View::zoomIn()/zoomOut()` step by `ZOOM_STEP_HEIGHT` per wheel notch, so a pinch of
`ZOOM_PX_PER_TICK` pixels is one notch.

The camera is only moved when the game owns it. The gate in `SdlTouch.cpp` refuses while a
scripted camera move is running, while the camera is locked to an object, or while
`TheInGameUI->getInputEnabled()` is false — the same rule `LookAtTranslator::setScrolling` uses for
the desktop mouse, so a cutscene behaves the same on both.

## Duplicate input prevention

The one hard requirement of this phase is that a touch must never also arrive as a mouse event.
`SdlTouch_install()` sets `SDL_HINT_TOUCH_MOUSE_EVENTS` to `"0"` on Android, so SDL does not
synthesize mouse events from touches. As a second line of defence, `SdlInput_dispatch()` drops any
`SDL_EVENT_MOUSE_*` whose `which` field is `SDL_TOUCH_MOUSEID` or `SDL_PEN_MOUSEID`, so a
touch-derived mouse event cannot reach `SdlMouse` even if a platform delivers one anyway. A real
mouse, a real touchpad and a real pen keep working: their events carry an ordinary mouse id.

The mouse and keyboard paths are otherwise untouched. A desktop build never compiles the touch
layer in (it is behind `ZH_ANDROID`), and even on Android a real mouse still drives `SdlMouse`
exactly as before.

## What is deliberately not here

* No `AndroidMain.cpp`: the lifecycle lives in the SDL3 device (`SdlAndroidPlatform`) and SDL's
  `SDLActivity`, and the touch layer's part of it — a backgrounding resolves and drops the live
  gesture, since SDL delivers no `FINGER_UP` then — is recorded in
  [`19-android-lifecycle-and-input-integration.md`](19-android-lifecycle-and-input-integration.md).
* No on-screen modifier buttons (force fire, waypoint, add-to-selection). The engine already
  exposes the modes (`setForceAttackMode`, `setWaypointMode`); the buttons that drive them are UI
  work, and the tap resolution already consults the modes where they change a tap's meaning.
* No touch keyboard. Text entry keeps using SDL's IME path, which already works.

## Testing and CI

The pure decisions are tested on the host, where `SdlTouch.cpp`'s Android half is compiled out
(`ZH_ANDROID == 0`) so the file links no SDL and no engine: `GeneralsMD/Code/Tests/test_sdl_touch.cpp`,
registered as the `test_sdl_touch` ctest entry (it links `sdlinput`, which globs `SdlTouch.cpp`).
It checks the constant ordering, the dead zone, the double-tap and two-finger-tap windows, the
momentum thresholds, the zoom-notch arithmetic, the pending-drag classification, and the
touch-mouse-id test.

The event plumbing and the engine actions behind `ZH_ANDROID` need the game on a device, and the
test says so rather than pretend to cover them. What CI does cover is that the Android half
*compiles and is what was built*: the `CI Android Test` workflow compiles the `sdlinput` target
under the NDK, then checks that `SdlTouch.cpp.o` is AArch64, defines
`SdlTouch_install/dispatch/update/reset`, and references `SDL_SetHint` — a symbol called only by
the `ZH_ANDROID` half, so a desktop stub could not satisfy it. The engine still has to link as
`libmain.so` afterwards, which puts the touch object into the real link graph.
