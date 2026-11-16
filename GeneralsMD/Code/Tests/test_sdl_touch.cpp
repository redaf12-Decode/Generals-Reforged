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
/*
 * Phase 4: the touch gesture *decisions* (SdlDevice/GameClient/SdlTouch.h).
 *
 * The thresholds and the arithmetic that turn pixel movement and elapsed time into "this is a tap"
 * or "this is a drag" are free functions of their arguments, with no SDL and no engine state.  They
 * are tested here, on the host, where SdlTouch.cpp's Android half is compiled out (ZH_ANDROID == 0)
 * so the file links no SDL and no engine.
 *
 * What it cannot see: the event plumbing and the engine actions behind ZH_ANDROID - the message
 * stream a tap emits, the camera a pan moves, the UI a press routes.  Those need the game on a
 * device (or a device-simulation harness), and this test says so rather than pretend.
 */

#include "test_harness.h"

#include "SdlDevice/GameClient/SdlTouch.h"

// The constants, and the invariants between them.  A tap has to be decided before a long press
// elapses, a selection box before a long press too but after a tap, and a coast has to be able to
// start faster than it stops or it would end the frame it began.
TEST(sdl_touch_constants_are_ordered)
{
        CHECK( SdlTouch_SELECT_HOLD_MS < SdlTouch_LONG_PRESS_MS );
        CHECK( SdlTouch_MOMENTUM_STOP_PX_PER_FRAME < SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME );
        CHECK( SdlTouch_TAP_DEAD_ZONE_PX > 0.0f );
        CHECK( SdlTouch_ZOOM_PX_PER_TICK > 0.0f );
        CHECK( SdlTouch_MOMENTUM_FRICTION > 0.0f && SdlTouch_MOMENTUM_FRICTION < 1.0f );
}

TEST(sdl_touch_tap_dead_zone)
{
        CHECK( SdlTouch_withinTapDeadZone( 0.0f ) );
        CHECK( SdlTouch_withinTapDeadZone( SdlTouch_TAP_DEAD_ZONE_PX - 0.01f ) );
        CHECK( !SdlTouch_withinTapDeadZone( SdlTouch_TAP_DEAD_ZONE_PX ) );     // the boundary is a drag
        CHECK( !SdlTouch_withinTapDeadZone( SdlTouch_TAP_DEAD_ZONE_PX + 50.0f ) );
}

TEST(sdl_touch_double_tap)
{
        CHECK( !SdlTouch_isDoubleTap( FALSE, 10, 0.0f ) );                     // no first tap yet
        CHECK( SdlTouch_isDoubleTap( TRUE, 10, 5.0f ) );                       // quick and close
        CHECK( SdlTouch_isDoubleTap( TRUE, SdlTouch_DOUBLE_TAP_MS, SdlTouch_DOUBLE_TAP_DIST_PX ) );
        CHECK( !SdlTouch_isDoubleTap( TRUE, SdlTouch_DOUBLE_TAP_MS + 1, 0.0f ) );      // too slow
        CHECK( !SdlTouch_isDoubleTap( TRUE, 10, SdlTouch_DOUBLE_TAP_DIST_PX + 1.0f ) );// too far
}

TEST(sdl_touch_two_finger_tap)
{
        CHECK( SdlTouch_isTwoFingerTap( 0.0f, 0.0f ) );
        CHECK( SdlTouch_isTwoFingerTap( SdlTouch_TWO_FINGER_TAP_MAX_PX - 0.1f, 0.0f ) );
        CHECK( !SdlTouch_isTwoFingerTap( SdlTouch_TWO_FINGER_TAP_MAX_PX, 0.0f ) );     // one finger moved
        CHECK( !SdlTouch_isTwoFingerTap( 0.0f, SdlTouch_TWO_FINGER_TAP_MAX_PX + 10.0f ) );
}

TEST(sdl_touch_momentum_thresholds)
{
        CHECK( !SdlTouch_momentumStarts( 0.0f ) );
        CHECK( !SdlTouch_momentumStarts( SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME - 0.1f ) );
        CHECK( SdlTouch_momentumStarts( SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME ) );

        CHECK( SdlTouch_momentumEnds( 0.0f ) );
        CHECK( SdlTouch_momentumEnds( SdlTouch_MOMENTUM_STOP_PX_PER_FRAME - 0.1f ) );
        CHECK( !SdlTouch_momentumEnds( SdlTouch_MOMENTUM_STOP_PX_PER_FRAME ) );
}

TEST(sdl_touch_zoom_notches)
{
        CHECK_NEAR( SdlTouch_zoomNotchesForPixels( 0.0f ), 0.0f, 1e-5f );
        CHECK_NEAR( SdlTouch_zoomNotchesForPixels( SdlTouch_ZOOM_PX_PER_TICK ), 1.0f, 1e-5f );
        CHECK_NEAR( SdlTouch_zoomNotchesForPixels( -SdlTouch_ZOOM_PX_PER_TICK ), -1.0f, 1e-5f );
        // a spread finger (positive) is a zoom out (positive notches); a pinch, negative.
        CHECK( SdlTouch_zoomNotchesForPixels( 80.0f ) > 0.0f );
        CHECK( SdlTouch_zoomNotchesForPixels( -80.0f ) < 0.0f );
}

TEST(sdl_touch_classify_pending_drag)
{
        // Below the dead zone the caller should not have asked, and the answer is the harmless one.
        CHECK_EQ( SdlTouch_classifyPendingDrag( 0.0f, 1000, FALSE ), SDLTOUCH_DRAG_PAN );

        // A drag from a quick press is a camera pan.
        CHECK_EQ( SdlTouch_classifyPendingDrag( 50.0f, 100, FALSE ), SDLTOUCH_DRAG_PAN );

        // A drag from a press held past SELECT_HOLD_MS is a selection box.
        CHECK_EQ( SdlTouch_classifyPendingDrag( 50.0f, SdlTouch_SELECT_HOLD_MS, FALSE ), SDLTOUCH_DRAG_SELECT );
        CHECK_EQ( SdlTouch_classifyPendingDrag( 50.0f, 5000, FALSE ), SDLTOUCH_DRAG_SELECT );

        // A pending building wins over both: the drag rotates the ghost, whatever the hold was.
        CHECK_EQ( SdlTouch_classifyPendingDrag( 50.0f, 100, TRUE ), SDLTOUCH_DRAG_PLACE );
        CHECK_EQ( SdlTouch_classifyPendingDrag( 50.0f, 5000, TRUE ), SDLTOUCH_DRAG_PLACE );
}

TEST(sdl_touch_touch_mouse_event)
{
        const unsigned int TOUCH = 0xFFFFFFFDu;        // SDL_TOUCH_MOUSEID
        const unsigned int PEN = 0xFFFFFFFCu;          // SDL_PEN_MOUSEID
        CHECK( SdlTouch_isTouchMouseEvent( TOUCH, TOUCH, PEN ) );
        CHECK( SdlTouch_isTouchMouseEvent( PEN, TOUCH, PEN ) );
        CHECK( !SdlTouch_isTouchMouseEvent( 1u, TOUCH, PEN ) );        // a real mouse
        CHECK( !SdlTouch_isTouchMouseEvent( 0u, TOUCH, PEN ) );
}

// The entry points exist on every platform and are inert off Android; the header promises this so
// SdlInput_dispatch can call them unconditionally.
TEST(sdl_touch_inert_off_android)
{
        CHECK( !SdlTouch_isCompiledIn() );             // this test runs on the host, not a device
        SdlTouch_install();
        SdlTouch_update();
        SdlTouch_reset();
}
