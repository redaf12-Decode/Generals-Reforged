/*
**      Copyright 2026 İlyas Akın
**      Additional terms under GNU GPL section 7 apply: see LICENSE.md.
**
**      This program is free software: you can redistribute it and/or modify
**      it under the terms of the GNU General Public License as published by
**      the Free Software Foundation, either version 3 of the License, or
**      (at your option) any later version.
**
**      This program is distributed in the hope that it will be useful,
**      but WITHOUT ANY WARRANTY; without even the implied warranty of
**      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**      GNU General Public License for more details.
**
**      You should have received a copy of the GNU General Public License
**      along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// SdlTouch.h: native touch input, for the battlefield and the UI, without a synthesized pointer
// (Phase 4).  See docs/android-port/architecture/14-native-touch-input.md for the design.
//
// The desktop mouse and keyboard are untouched.  The event entry points are compiled on Android
// only: on every other platform SdlTouch_dispatch is never reached, SdlInput_dispatch keeps its
// mouse path as it was, and this file's Android half compiles to nothing under ZH_ANDROID == 0.
//
// A touch never also arrives as a mouse event.  SDL_HINT_TOUCH_MOUSE_EVENTS is set to "0" at
// install time, and SdlInput_dispatch drops any mouse event carrying SDL_TOUCH_MOUSEID or
// SDL_PEN_MOUSEID as a second line of defence (SdlTouch_isTouchMouseEvent).
//
// The gesture *decisions* - the thresholds and the arithmetic that turn pixel movement and
// elapsed time into "this is a tap" or "this is a drag" - are free functions of their arguments,
// with no SDL and no engine state.  They are what Tests/test_sdl_touch.cpp drives on the host.
// The event plumbing and the engine actions live in SdlTouch.cpp behind ZH_ANDROID.

#pragma once

#ifndef __SDLTOUCH_H
#define __SDLTOUCH_H

#include "Lib/BaseType.h"

union SDL_Event;
struct SDL_Window;

// ---- Gesture decisions (pure; the same code on every platform, tested on the host) --------------

/// A press-and-hold longer than this, released without moving, is the cancel/deselect gesture.
extern const UnsignedInt SdlTouch_LONG_PRESS_MS;
/// Hold this long, then drag, and the drag is an area-selection box rather than a camera pan.
extern const UnsignedInt SdlTouch_SELECT_HOLD_MS;
/// Movement below this (Manhattan, pixels) is still a tap; the only thing separating tap from drag.
extern const Real SdlTouch_TAP_DEAD_ZONE_PX;
extern const UnsignedInt SdlTouch_DOUBLE_TAP_MS;
extern const Real SdlTouch_DOUBLE_TAP_DIST_PX;
/// Both fingers of a two-finger gesture staying inside this is a tap (cancel), not a pan/zoom.
extern const Real SdlTouch_TWO_FINGER_TAP_MAX_PX;
/// A pan released faster than this coasts; a coast slower than the stop speed ends.
extern const Real SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME;
extern const Real SdlTouch_MOMENTUM_STOP_PX_PER_FRAME;
extern const Real SdlTouch_MOMENTUM_FRICTION;
/// Pinch movement, in pixels, that equals one wheel notch's worth of zoom.
extern const Real SdlTouch_ZOOM_PX_PER_TICK;

/// Still a tap: the finger has not left the dead zone.
Bool SdlTouch_withinTapDeadZone( Real movedPx );

/// The second tap landed soon enough and close enough to the first to be a double tap.
Bool SdlTouch_isDoubleTap( Bool hasLastTap, UnsignedInt sinceLastMs, Real distPx );

/// Neither finger of a two-finger gesture left its landing point far enough to be a pan/zoom.
Bool SdlTouch_isTwoFingerTap( Real move1Px, Real move2Px );

/// A released pan this fast keeps going as a coast.
Bool SdlTouch_momentumStarts( Real speedPxPerFrame );

/// A coast this slow has died down enough to stop.
Bool SdlTouch_momentumEnds( Real speedPxPerFrame );

/// The wheel notches a pinch of this many pixels is worth. Sign: a spread finger (positive) zooms
/// out, so the count is positive.
Real SdlTouch_zoomNotchesForPixels( Real pixels );

/// What a drag that has just left the dead zone, from a press that is still down, turns out to be.
enum SdlTouchDragKind
{
        SDLTOUCH_DRAG_PAN = 0,          ///< an ordinary drag: move the camera
        SDLTOUCH_DRAG_SELECT,           ///< held long enough first: draw a selection box
        SDLTOUCH_DRAG_PLACE             ///< a build is pending: rotate the placement ghost
};
SdlTouchDragKind SdlTouch_classifyPendingDrag( Real movedPx, UnsignedInt heldMs, Bool placementPending );

/// TRUE when a mouse event was synthesized from a touch or a pen rather than a real pointer.
Bool SdlTouch_isTouchMouseEvent( unsigned int mouseId, unsigned int touchMouseId, unsigned int penMouseId );

// ---- The event entry points (Android only in effect; the definitions are guarded) ----------------

/** The hints the touch layer needs, once: SDL stops synthesizing mouse events from touches, so a
        * finger is interpreted here and nowhere else.  A no-op off Android. */
void SdlTouch_install( void );

/** One event.  TRUE when it was a touch and was taken.  FALSE for every other event, and for
        * every event at all off Android, so SdlInput_dispatch falls through to the mouse. */
Bool SdlTouch_dispatch( const SDL_Event &event );

/** Once per frame, after the events have been drained: applies the camera motion a live gesture
        * asks for (a pan, a two-finger pan/zoom/rotate, or a coast).  Per frame and not per event
        * because several motion events can arrive in one frame and the projection would go stale
        * between them.  A no-op off Android. */
void SdlTouch_update( void );

/// Resolves and drops the gesture state, as a FINGER_CANCELED would: a gesture that owes the
/// engine a button-up (a held UI press, a committed placement, an area-selection box) is released
/// rather than left latched.  For a window change, and for the app leaving the foreground (no
/// FINGER_UP arrives then).  A no-op off Android.
void SdlTouch_reset( void );

/// The touch layer is compiled into this build.  TRUE on Android only.
Bool SdlTouch_isCompiledIn( void );

#endif // __SDLTOUCH_H
