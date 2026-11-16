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

// SdlTouch.cpp: see SdlTouch.h and docs/android-port/architecture/14-native-touch-input.md.
//
// Two halves.  The first is pure arithmetic - the thresholds and the decisions - and it compiles
// on every platform, because Tests/test_sdl_touch.cpp drives it on the host with no SDL.  The
// second is the event plumbing and the engine actions, behind ZH_ANDROID.

#include "PreRTS.h"     // This must go first in EVERY cpp file in the GameEngine

#include "SdlDevice/GameClient/SdlTouch.h"

#include "Platform/PlatformPredicates.h"	// ZH_ANDROID

// ---- Gesture decisions --------------------------------------------------------------------------

// 600 ms is the reference port's long press, kept: it is the delay at which a held finger stops
// reading as a tap and starts reading as a deliberate hold.
const UnsignedInt SdlTouch_LONG_PRESS_MS = 600;
// Shorter than the long press, and deliberately its own constant: reusing 600 ms before a selection
// box would even begin to respond felt laggy.  It is a different gesture (a drag, not a release), so
// there is no ambiguity in letting it elapse first.
const UnsignedInt SdlTouch_SELECT_HOLD_MS = 250;
// The single dead-zone distance that decides tap versus drag.  Also Android's own touch slop, and
// enough slack to absorb a stationary finger's tremor while it waits out the long press.
const Real SdlTouch_TAP_DEAD_ZONE_PX = 16.0f;
const UnsignedInt SdlTouch_DOUBLE_TAP_MS = 350;
const Real SdlTouch_DOUBLE_TAP_DIST_PX = 40.0f;
// A little more generous than the single-finger dead zone: two fingers drift more than one.
const Real SdlTouch_TWO_FINGER_TAP_MAX_PX = 24.0f;
const Real SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME = 2.0f;
const Real SdlTouch_MOMENTUM_STOP_PX_PER_FRAME = 0.5f;
const Real SdlTouch_MOMENTUM_FRICTION = 0.92f;
const Real SdlTouch_ZOOM_PX_PER_TICK = 40.0f;

Bool SdlTouch_withinTapDeadZone( Real movedPx )
{
        return movedPx < SdlTouch_TAP_DEAD_ZONE_PX;
}

Bool SdlTouch_isDoubleTap( Bool hasLastTap, UnsignedInt sinceLastMs, Real distPx )
{
        return hasLastTap && sinceLastMs <= SdlTouch_DOUBLE_TAP_MS && distPx <= SdlTouch_DOUBLE_TAP_DIST_PX;
}

Bool SdlTouch_isTwoFingerTap( Real move1Px, Real move2Px )
{
        return move1Px < SdlTouch_TWO_FINGER_TAP_MAX_PX && move2Px < SdlTouch_TWO_FINGER_TAP_MAX_PX;
}

Bool SdlTouch_momentumStarts( Real speedPxPerFrame )
{
        return speedPxPerFrame >= SdlTouch_MOMENTUM_MIN_START_PX_PER_FRAME;
}

Bool SdlTouch_momentumEnds( Real speedPxPerFrame )
{
        return speedPxPerFrame < SdlTouch_MOMENTUM_STOP_PX_PER_FRAME;
}

Real SdlTouch_zoomNotchesForPixels( Real pixels )
{
        return pixels / SdlTouch_ZOOM_PX_PER_TICK;
}

SdlTouchDragKind SdlTouch_classifyPendingDrag( Real movedPx, UnsignedInt heldMs, Bool placementPending )
{
        if (movedPx < SdlTouch_TAP_DEAD_ZONE_PX)
                return SDLTOUCH_DRAG_PAN;       // not a drag at all; the caller should not have asked
        // A pending building's drag rotates the ghost, whatever the hold was: the player is placing,
        // not panning or boxing.
        if (placementPending)
                return SDLTOUCH_DRAG_PLACE;
        if (heldMs >= SdlTouch_SELECT_HOLD_MS)
                return SDLTOUCH_DRAG_SELECT;
        return SDLTOUCH_DRAG_PAN;
}

Bool SdlTouch_isTouchMouseEvent( unsigned int mouseId, unsigned int touchMouseId, unsigned int penMouseId )
{
        return mouseId == touchMouseId || mouseId == penMouseId;
}

// ---- The event plumbing and the engine actions (Android only) ------------------------------------

#if ZH_ANDROID

#include "Common/GlobalData.h"
#include "Common/MessageStream.h"
#include "Common/Player.h"
#include "Common/PlayerList.h"
#include "Common/Radar.h"
#include "Common/ThingTemplate.h"
#include "GameClient/CommandXlat.h"
#include "GameClient/ControlBar.h"
#include "GameClient/Display.h"
#include "GameClient/Drawable.h"
#include "GameClient/GameClient.h"
#include "GameClient/GameWindow.h"
#include "GameClient/GameWindowManager.h"
#include "GameClient/InGameUI.h"
#include "GameClient/SelectionInfo.h"
#include "GameClient/SelectionXlat.h"
#include "GameClient/Shell.h"
#include "GameClient/View.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Object.h"
#include "SdlDevice/GameClient/SdlInput.h"
#include "SdlDevice/GameClient/SdlKeyboard.h"
#include "SdlDevice/GameClient/SdlMouse.h"
#include "SdlDevice/GameClient/SdlTouch.h"

#include <SDL3/SDL.h>

#include <math.h>

namespace {

struct TouchState
{
        enum Phase
        {
                IDLE,           ///< no finger tracked
                PENDING,        ///< finger 1 down, gesture identity not yet known, nothing sent
                PANNING,        ///< finger 1 dragged past the dead zone: direct camera pan
                MOMENTUM,       ///< a fast pan was released: coasting, decelerating
                TWOFINGER,      ///< two fingers: centroid pan, pinch zoom, twist rotate
                PLACING,        ///< a drag with a build pending: rotate the ghost
                SELECTING,      ///< held then dragged: an area-selection box
                TARGETING,      ///< an armed command's finger is the aiming reticle
                UI_PRESS        ///< finger 1 landed on a widget: a real held left press
        };

        Phase phase = IDLE;
        Uint64 finger1 = 0, finger2 = 0;
        Real downX = 0, downY = 0;              ///< finger 1's landing point, fixed until release
        Real lastX = 0, lastY = 0;              ///< finger 1's latest point
        Uint64 downTicks = 0;

        Real panLastPxX = 0, panLastPxY = 0;    ///< the last processed point of a single-finger pan
        Real panVelX = 0, panVelY = 0;          ///< the delta applied on the last pan frame
        Real momentumX = 0, momentumY = 0;      ///< the virtual finger the coast advances

        Real f1px = 0, f1py = 0, f2px = 0, f2py = 0;    ///< both fingers' latest points
        Real twoCentroidLastX = 0, twoCentroidLastY = 0;
        Real twoDistLastPx = 0;
        Real twoAngleLastRad = 0;               ///< the finger-to-finger angle on the previous frame
        Real twoTwistAccumRad = 0;              ///< how far the gesture has twisted in total
        Bool twoRotateArmed = FALSE;            ///< the twist is deliberate enough to rotate

        Real twoDownX1 = 0, twoDownY1 = 0, twoDownX2 = 0, twoDownY2 = 0;        ///< where each landed

        Bool hasLastTap = FALSE;
        Uint64 lastTapTicks = 0;
        Real lastTapX = 0, lastTapY = 0;
};

TouchState s_touch;

/// A twist of less than this is a two-finger pan that happens to wobble, not a deliberate rotate.
const Real TWIST_ARM_RAD = 0.14f;               // ~8 degrees

// --- Screen geometry -----------------------------------------------------------------------------------------

/// A normalized touch point (SDL reports 0..1 against the window) in the game's pixels.  SDLInput's
/// own conversion is reused so a touch lands where the mouse would: it scales through the pillarbox
/// and holds the point inside the screen.
void touchPoint( Real normX, Real normY, Int &gameX, Int &gameY )
{
        SDL_Window *window = SdlInput_gameWindow();
        Int winW = 0, winH = 0;
        if (window != NULL)
                SDL_GetWindowSize( window, &winW, &winH );
        SdlInput_scaleToGame( normX * winW, normY * winH, winW, winH,
                TheDisplay != NULL ? (Int)TheDisplay->getWidth() : 0,
                TheDisplay != NULL ? (Int)TheDisplay->getHeight() : 0,
                gameX, gameY );
}

/// TRUE when the window manager would route a press at this point to a widget.  A tap there is UI,
/// not battlefield.
Bool pointBelongsToUi( Int x, Int y )
{
        return TheWindowManager != NULL && TheWindowManager->getWindowUnderCursor( x, y ) != NULL;
}

/// A press that landed on a real button, as opposed to a panel or a list: it takes the UI_PRESS
/// path, which sends its press immediately rather than deferring it.
Bool isRealButton( GameWindow *hit )
{
        return hit != NULL && BitTest( hit->winGetStyle(), GWS_PUSH_BUTTON );
}

/// The engine's modifier flags, carried on every message a touch publishes, exactly as the mouse's
/// own messages carry them.
Int modifierFlags()
{
        return TheKeyboard != NULL ? TheKeyboard->getModifierFlags() : 0;
}

// --- The message stream ------------------------------------------------------------------------------------

void pushMousePosition( Int x, Int y )
{
        if (TheMessageStream == NULL)
                return;
        GameMessage *msg = TheMessageStream->appendMessage( GameMessage::MSG_RAW_MOUSE_POSITION );
        msg->appendPixelArgument( ICoord2D{ x, y } );
        msg->appendIntegerArgument( modifierFlags() );
}

void pushMouseButton( GameMessage::Type type, Int x, Int y )
{
        if (TheMessageStream == NULL)
                return;
        GameMessage *msg = TheMessageStream->appendMessage( type );
        msg->appendPixelArgument( ICoord2D{ x, y } );
        msg->appendIntegerArgument( modifierFlags() );
        msg->appendIntegerArgument( 0 );
}

/// Publish the live finger position as the value the preview drawing reads.  InGameUI's radius
/// cursor and placement ghost read the mouse position each frame; with nothing writing it on a
/// touch device they would draw in the corner.  This writes the field and nothing else - no
/// MSG_RAW_MOUSE_POSITION, which is the event that drives the GUI hilite and the edge scroll.
void publishPreviewPoint( Int x, Int y )
{
        if (TheMouse == NULL)
                return;
        SdlMouse *sdlMouse = dynamic_cast<SdlMouse *>(TheMouse);
        if (sdlMouse != NULL)
                sdlMouse->setTouchCursorPos( x, y );
}

// --- Camera control ----------------------------------------------------------------------------------------

/// TRUE when a script or a camera lock owns the camera, or the UI has disabled input.  The same
/// rule LookAtTranslator::setScrolling uses, so a cutscene behaves the same on mouse and touch.
Bool cameraIsScripted()
{
        if (TheTacticalView == NULL)
                return TRUE;
        if (TheInGameUI != NULL && !TheInGameUI->getInputEnabled())
                return TRUE;
        if (TheTacticalView->getCameraLockDrawable() != NULL || TheTacticalView->getCameraLock() != INVALID_ID)
                return TRUE;
        if (!TheTacticalView->isCameraMovementFinished())
                return TRUE;
        return FALSE;
}

/// TRUE when the camera may be moved by a gesture at all.
Bool cameraControlAllowed()
{
        if (cameraIsScripted())
                return FALSE;
        if (TheShell != NULL && TheShell->isShellActive())
                return FALSE;
        return TRUE;
}

/// Move the camera so the world under `fromPx` is under `toPx`.  Projecting both points onto the
/// terrain and moving by the world-space difference is exact at every zoom, angle and resolution.
void panCamera( Real fromPxX, Real fromPxY, Real toPxX, Real toPxY )
{
        if (!cameraControlAllowed())
                return;
        ICoord2D from, to;
        from.x = (Int)fromPxX;
        from.y = (Int)fromPxY;
        to.x = (Int)toPxX;
        to.y = (Int)toPxY;
        Coord3D worldFrom, worldTo;
        if (!TheTacticalView->screenToTerrain( &from, &worldFrom ))
                return;
        if (!TheTacticalView->screenToTerrain( &to, &worldTo ))
                return;
        Coord2D delta;
        delta.x = worldFrom.x - worldTo.x;
        delta.y = worldFrom.y - worldTo.y;
        TheTacticalView->scrollBy( &delta );
}

/// A pinch that changed the finger spread by `pixels` moves the zoom by this many wheel notches.
/// Sign: a spread finger zooms out, so the notch count is positive.
void zoomCamera( Real pixels )
{
        if (!cameraControlAllowed())
                return;
        const Real notches = SdlTouch_zoomNotchesForPixels( pixels );
        if (notches > 0.0f)
                TheTacticalView->zoomOut( notches );
        else if (notches < 0.0f)
                TheTacticalView->zoomIn( -notches );
}

/// Rotate the camera about its up axis by a two-finger twist.
void rotateCamera( Real deltaRad )
{
        if (!cameraControlAllowed())
                return;
        TheTacticalView->setAngle( TheTacticalView->getAngle() + deltaRad );
}

// --- The engine's order and selection rules -------------------------------------------------------------------

/// The object under a screen point, as the ORDER path sees it.  Mirrors what CommandXlat does before
/// every evaluateContextCommand() call: pick with the context's types, then discard a corpse, which
/// would otherwise block a positional order.
Drawable *pickForOrder( const ICoord2D &pixel )
{
        if (TheTacticalView == NULL || TheInGameUI == NULL)
                return nullptr;
        const Bool forceAttack = TheInGameUI->isInForceAttackMode();
        Drawable *draw = TheTacticalView->pickDrawable( &pixel, forceAttack, (PickType)getPickTypesForContext( forceAttack ) );
        const Object *obj = draw != NULL ? draw->getObject() : NULL;
        if (obj == NULL || (obj->isEffectivelyDead() && !obj->isKindOf( KINDOF_ALWAYS_SELECTABLE )))
                return nullptr;
        return draw;
}

/// The object under a screen point the player could SELECT, or null.
Drawable *pickForSelection( const ICoord2D &pixel )
{
        if (TheTacticalView == NULL)
                return nullptr;
        Drawable *draw = TheTacticalView->pickDrawable( &pixel, FALSE, PICK_TYPE_SELECTABLE );
        if (!CanSelectDrawable( draw, FALSE ))
                return nullptr;
        return draw;
}

/// Replace the whole selection with this drawable, and tell the logic about it.  The selection is
/// game state, not a local highlight: it travels over the network and into replays, so the message
/// is not optional.  playSound distinguishes an ordinary select (which answers) from the first step
/// of a double tap (whose second step answers instead).
void selectOnly( Drawable *draw, Bool playSound )
{
        if (draw == NULL || TheInGameUI == NULL)
                return;
        TheInGameUI->deselectAllDrawables();
        TheInGameUI->selectDrawable( draw );
        Object *obj = draw->getObject();
        if (obj != NULL && TheMessageStream != NULL)
        {
                GameMessage *msg = TheMessageStream->appendMessage( playSound ? GameMessage::MSG_CREATE_SELECTED_GROUP
                                                                                                                                        : GameMessage::MSG_CREATE_SELECTED_GROUP_NO_SOUND );
                msg->appendBooleanArgument( TRUE );
                msg->appendObjectIDArgument( obj->getID() );
        }
}

/// Does the current selection have a MORE SPECIFIC interaction with this object than "move there"?
/// Tapping your own transport while infantry is selected means "get in"; on a mouse those are
/// separate buttons a finger does not have.  Ask the engine in EVALUATE_ONLY mode rather than guess:
/// a plain move (or nothing) is no special meaning, so the tap is a selection.
Bool selectionInteractsWith( Drawable *draw, const Coord3D &pos )
{
        if (draw == NULL || TheGameClient == NULL)
                return FALSE;
        const GameMessage::Type t = TheGameClient->evaluateContextCommand( draw, &pos, CommandTranslator::EVALUATE_ONLY );
        switch (t)
        {
                case GameMessage::MSG_INVALID:
                case GameMessage::MSG_DO_MOVETO:
                case GameMessage::MSG_DO_ATTACKMOVETO:
                case GameMessage::MSG_ADD_WAYPOINT:
                        return FALSE;
                default:
                        return TRUE;
        }
}

/// Issue the context order for a point, exactly as the click path does.
void issueContextOrder( Drawable *draw, const Coord3D &pos )
{
        if (TheGameClient == NULL || TheInGameUI == NULL)
                return;
        TheGameClient->evaluateContextCommand( draw, &pos, CommandTranslator::DO_COMMAND );
        TheInGameUI->clearAttackMoveToMode();
}

/// While a movie is up the only thing a tap can mean is "skip it".  Skipping was never a translator
/// chain behaviour: WindowXlat watches for an unconsumed raw left-button-down.  Handled here first.
Bool skipMovieIfPlaying()
{
        if (TheDisplay != NULL && TheDisplay->isMoviePlaying() && TheGlobalData != NULL && TheGlobalData->m_allowExitOutOfMovies)
        {
                TheDisplay->stopMovie();
                return TRUE;
        }
        return FALSE;
}

Bool hasArmedCommand()
{
        return TheInGameUI != NULL && TheInGameUI->getGUICommand() != NULL;
}

Bool hasControllableSelection()
{
        return TheInGameUI != NULL && TheInGameUI->areSelectedObjectsControllable();
}

/// An armed command's radius decal is created by setRadiusCursor(), which the mouse path reaches
/// from createCommandHint() on every position message.  Native aiming sends none, so create it here
/// when a finger actually starts aiming.
void beginAiming( Int x, Int y )
{
        if (TheInGameUI == NULL)
                return;
        const CommandButton *command = TheInGameUI->getGUICommand();
        if (command == NULL)
                return;
        TheInGameUI->setRadiusCursor( command->getRadiusCursorType(), command->getSpecialPowerTemplate(), PRIMARY_WEAPON );
}

/// The force-attack mode's next tap: whatever is under the finger, the player's own units included,
/// is the target - never a selection.  Mirrors CommandXlat's Control-click, which answers with
/// evaluateForceAttack() rather than evaluateContextCommand().  Returns TRUE when the mode was armed
/// (and the tap is spent, even off the terrain - the player simply missed).
Bool forceAttackTap( const ICoord2D &pixel )
{
        if (TheInGameUI == NULL || !TheInGameUI->isInForceAttackMode())
                return FALSE;
        if (!TheInGameUI->areSelectedObjectsControllable())
        {
                TheInGameUI->setForceAttackMode( FALSE );
                return FALSE;
        }
        Coord3D pos;
        if (TheTacticalView != NULL && TheGameClient != NULL && TheTacticalView->screenToTerrain( &pixel, &pos ))
        {
                TheGameClient->evaluateForceAttack( pickForOrder( pixel ), &pos, CommandTranslator::DO_COMMAND );
                TheInGameUI->setForceAttackMode( FALSE );
        }
        return TRUE;
}

/// A tap on the battlefield, resolved against the engine's own rules.
void battlefieldTap( Int x, Int y )
{
        if (skipMovieIfPlaying())
                return;
        if (TheInGameUI == NULL || TheTacticalView == NULL)
                return;

        // A tap that arrived while a command is armed is not this path's to resolve: the TARGETING
        // phase owns it.  This branch exists so a tap arriving by another route cannot fall through
        // and be read as a move order.
        if (hasArmedCommand())
                return;

        ICoord2D pixel{ x, y };

        // 1. The force-attack mode owns the next tap the same way.
        if (forceAttackTap( pixel ))
                return;

        Coord3D pos;
        const Bool onTerrain = TheTacticalView->screenToTerrain( &pixel, &pos );

        // 2. Something of the player's own under the finger.
        Drawable *selectable = pickForSelection( pixel );
        if (selectable != NULL)
        {
                const Object *obj = selectable->getObject();
                if (obj != NULL && obj->isLocallyControlled())
                {
                        const Bool interacts = onTerrain && hasControllableSelection() && !selectable->isSelected()
                                && selectionInteractsWith( selectable, pos );
                        if (!interacts)
                        {
                                selectOnly( selectable, TRUE );
                                return;
                        }
                        // else: fall through, and let it be an order onto that object
                }
        }

        if (!onTerrain)
                return;

        // 3. An order, if there is anything to order.
        if (hasControllableSelection())
        {
                issueContextOrder( pickForOrder( pixel ), pos );
                return;
        }

        // 4. Nothing selectable, nothing to order: a tap on empty ground clears the selection, the
        //    same as a click on empty ground does.
        if (selectable != NULL)
                selectOnly( selectable, TRUE );
        else
                TheInGameUI->deselectAllDrawables();
}

/// The second tap in the same spot: select every unit of that type on screen, matching the PC's
/// double click; or, on bare ground with the option on, an attack-move.
void battlefieldDoubleTap( Int x, Int y )
{
        if (skipMovieIfPlaying())
                return;
        if (TheInGameUI == NULL || TheTacticalView == NULL)
                return;
        if (hasArmedCommand())
                return;

        // With a touch modifier mode on, the second tap is just another tap, not a select-all-of-type
        // or a guard order the mode was never asked for.
        if (TheInGameUI->isInForceAttackMode() || TheInGameUI->isInWaypointMode())
        {
                battlefieldTap( x, y );
                return;
        }

        ICoord2D pixel{ x, y };
        Drawable *picked = pickForSelection( pixel );
        Object *obj = picked != NULL ? picked->getObject() : NULL;

        // Only the player's own, mass-selectable units answer to this.
        if (picked == NULL || obj == NULL || !obj->isLocallyControlled() || !picked->isMassSelectable())
        {
                // ...except a double tap on bare ground, which Options -> CONTROL OPTIONS ->
                // "Double Click Guard" turns into an attack-move.  CommandXlat reads that flag only
                // while handling MSG_MOUSE_LEFT_DOUBLE_CLICK, so do here what that case does.
                if (picked == NULL && TheGlobalData != NULL && TheGlobalData->m_doubleClickAttackMove
                                && hasControllableSelection() && TheMessageStream != NULL)
                {
                        Coord3D pos;
                        if (TheTacticalView->screenToTerrain( &pixel, &pos ))
                        {
                                GameMessage *newMsg = TheMessageStream->appendMessage( GameMessage::MSG_DO_GUARD_POSITION );
                                newMsg->appendLocationArgument( pos );
                                newMsg->appendIntegerArgument( GUARDMODE_NORMAL );
                                if (ThePlayerList != NULL && ThePlayerList->getLocalPlayer() != NULL)
                                        ThePlayerList->getLocalPlayer()->getAcademyStats()->recordDoubleClickAttackMoveOrderGiven();
                                TheInGameUI->triggerDoubleClickAttackMoveGuardHint();
                                return;
                        }
                }
                battlefieldTap( x, y );
                return;
        }

        selectOnly( picked, FALSE );
        TheInGameUI->selectMatchingAcrossScreen();
}

/// Back out of an armed command or a pending building; deselect otherwise.  The precedence a
/// right-click has, minus the right-click.
void cancelOrDeselect()
{
        if (TheInGameUI == NULL)
                return;
        if (TheInGameUI->getGUICommand() != NULL)
        {
                TheInGameUI->setGUICommand( NULL );
                return;
        }
        if (TheInGameUI->isInForceAttackMode() || TheInGameUI->isInWaypointMode())
        {
                TheInGameUI->setForceAttackMode( FALSE );
                TheInGameUI->setWaypointMode( FALSE );
                return;
        }
        if (TheInGameUI->getPendingPlaceSourceObjectID() != INVALID_ID)
        {
                TheInGameUI->placeBuildAvailable( NULL, NULL );
                TheInGameUI->setPreventLeftClickDeselectionInAlternateMouseModeForOneClick( FALSE );
                return;
        }
        TheInGameUI->deselectAllDrawables();
}

/// A held finger on the minimap moves the camera there, and nothing else.  On the radar a left
/// click orders and a right click looks; a tap is the left one, so this is the right one.
Bool lookAtRadarPoint( Int x, Int y )
{
        if (TheRadar == NULL || TheWindowManager == NULL || TheTacticalView == NULL)
                return FALSE;
        Bool onRadar = FALSE;
        for (GameWindow *win = TheWindowManager->getWindowUnderCursor( x, y ); win != NULL; win = win->winGetParent())
        {
                if (TheRadar->isRadarWindow( win ))
                {
                        onRadar = TRUE;
                        break;
                }
        }
        if (!onRadar)
                return FALSE;
        ICoord2D pixel{ x, y };
        Coord3D world;
        if (!TheRadar->screenPixelToWorld( &pixel, &world ))
                return FALSE;
        TheTacticalView->lookAt( &world );
        return TRUE;
}

/// TRUE when the window manager would take a tap at this point.  Asked before the battlefield is
/// given the tap, so a tap inside an open panel cannot also act on the ground beneath it.
Bool uiTookTap( Int x, Int y, Bool longPress )
{
        if (TheWindowManager == NULL)
                return FALSE;
        ICoord2D point{ x, y };
        TheWindowManager->winProcessMouseEvent( GWM_MOUSE_POS, &point, NULL );
        const Bool shellLongPress = longPress && TheShell != NULL && TheShell->isShellActive();
        const WinInputReturnCode usedDown = TheWindowManager->winProcessMouseEvent( shellLongPress ? GWM_RIGHT_DOWN : GWM_LEFT_DOWN, &point, NULL );
        const WinInputReturnCode usedUp = TheWindowManager->winProcessMouseEvent( shellLongPress ? GWM_RIGHT_UP : GWM_LEFT_UP, &point, NULL );
        ICoord2D nowhere{ -1, -1 };
        TheWindowManager->winProcessMouseEvent( GWM_MOUSE_POS, &nowhere, NULL );
        return usedDown == WIN_INPUT_USED || usedUp == WIN_INPUT_USED;
}

// --- The gesture machine -----------------------------------------------------------------------------------------

/// TRUE when the game is a real match, as opposed to the shell's battle backdrop.
Bool inRealGame()
{
        return TheGameLogic != NULL && TheGameLogic->isInGame() && !TheGameLogic->isInShellGame();
}

void resetGesture( TouchState::Phase to )
{
        const Bool keepLastTap = s_touch.hasLastTap;
        const Uint64 lastTapTicks = s_touch.lastTapTicks;
        const Real lastTapX = s_touch.lastTapX, lastTapY = s_touch.lastTapY;
        s_touch = TouchState();
        s_touch.hasLastTap = keepLastTap;
        s_touch.lastTapTicks = lastTapTicks;
        s_touch.lastTapX = lastTapX;
        s_touch.lastTapY = lastTapY;
        s_touch.phase = to;
}

void onFingerDown( Real px, Real py, Uint64 fingerId )
{
        const Int ix = (Int)px, iy = (Int)py;

        if (s_touch.phase == TouchState::IDLE || s_touch.phase == TouchState::MOMENTUM)
        {
                // A finger landing on a real button is unambiguous the instant it happens: route it
                // straight to the engine as a held press, rather than deferring it through PENDING,
                // which would delay the engine's own press by up to SELECT_HOLD_MS.
                GameWindow *hit = TheWindowManager != NULL ? TheWindowManager->getWindowUnderCursor( ix, iy ) : NULL;
                if (isRealButton( hit ))
                {
                        s_touch.finger1 = fingerId;
                        s_touch.phase = TouchState::UI_PRESS;
                        s_touch.downX = s_touch.lastX = px;
                        s_touch.downY = s_touch.lastY = py;
                        s_touch.downTicks = SDL_GetTicks();
                        pushMousePosition( ix, iy );
                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN, ix, iy );
                        return;
                }

                // An armed ability turns the finger into the aiming reticle.  At touch-down a
                // targeting drag and a camera drag look identical, so classify on the armed command
                // instead of on the gesture - but only for a finger on the battlefield, never on UI.
                if (hasArmedCommand() && !pointBelongsToUi( ix, iy ))
                {
                        s_touch.finger1 = fingerId;
                        s_touch.phase = TouchState::TARGETING;
                        s_touch.downX = s_touch.lastX = px;
                        s_touch.downY = s_touch.lastY = py;
                        s_touch.downTicks = SDL_GetTicks();
                        beginAiming( ix, iy );
                        return;
                }

                // Otherwise defer: the finger could still become a tap, a pan, a long press or the
                // first of a two-finger gesture.  A premature click would be a real order.
                s_touch.finger1 = fingerId;
                s_touch.phase = TouchState::PENDING;
                s_touch.downX = s_touch.lastX = px;
                s_touch.downY = s_touch.lastY = py;
                s_touch.downTicks = SDL_GetTicks();

                // In the shell, hover before the tap so a widget that only accepts a click once it is
                // hilited sees the enter.  In a game the battlefield tap needs no pointer, and
                // publishing one would put a phantom cursor on the field.
                if (!inRealGame())
                        pushMousePosition( ix, iy );
                return;
        }

        if (s_touch.phase == TouchState::PENDING || s_touch.phase == TouchState::PANNING || s_touch.phase == TouchState::TARGETING)
        {
                // A second finger is always a direct two-finger pan/pinch, immediately.
                s_touch.finger2 = fingerId;
                s_touch.f1px = s_touch.lastX;
                s_touch.f1py = s_touch.lastY;
                s_touch.f2px = px;
                s_touch.f2py = py;
                s_touch.twoDownX1 = s_touch.f1px;
                s_touch.twoDownY1 = s_touch.f1py;
                s_touch.twoDownX2 = s_touch.f2px;
                s_touch.twoDownY2 = s_touch.f2py;
                s_touch.twoCentroidLastX = (s_touch.f1px + s_touch.f2px) * 0.5f;
                s_touch.twoCentroidLastY = (s_touch.f1py + s_touch.f2py) * 0.5f;
                const Real ddx = s_touch.f2px - s_touch.f1px, ddy = s_touch.f2py - s_touch.f1py;
                s_touch.twoDistLastPx = sqrtf( ddx * ddx + ddy * ddy );
                s_touch.twoAngleLastRad = atan2f( ddy, ddx );
                s_touch.twoTwistAccumRad = 0.0f;
                s_touch.twoRotateArmed = FALSE;
                s_touch.phase = TouchState::TWOFINGER;
                return;
        }

        if (s_touch.phase == TouchState::PLACING)
        {
                // A second finger while rotating a pending building is an immediate cancel: the only
                // other way out would be to drag to an illegal spot and release.
                if (TheInGameUI != NULL)
                        TheInGameUI->placeBuildAvailable( NULL, NULL );
                resetGesture( TouchState::IDLE );
                return;
        }

        if (s_touch.phase == TouchState::SELECTING)
        {
                // No clean abort for a selection box: SelectionTranslator only clears its drag lock
                // from the button-up, so finalize with whatever box was drawn.  Selecting the "wrong"
                // units is trivially undone by tapping again.
                pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, (Int)s_touch.lastX, (Int)s_touch.lastY );
                resetGesture( TouchState::IDLE );
                return;
        }
}

void onFingerMotion( Real px, Real py, Uint64 fingerId )
{
        if (fingerId == s_touch.finger1)
        {
                s_touch.lastX = px;
                s_touch.lastY = py;
                if (s_touch.phase == TouchState::TWOFINGER)
                {
                        s_touch.f1px = px;
                        s_touch.f1py = py;
                }
        }
        else if (s_touch.phase == TouchState::TWOFINGER && fingerId == s_touch.finger2)
        {
                s_touch.f2px = px;
                s_touch.f2py = py;
        }
        else
        {
                return;
        }

        if (s_touch.phase == TouchState::PENDING && fingerId == s_touch.finger1)
        {
                const Real moved = fabsf( px - s_touch.downX ) + fabsf( py - s_touch.downY );
                const SdlTouchDragKind kind = SdlTouch_classifyPendingDrag( moved,
                        (UnsignedInt)(SDL_GetTicks() - s_touch.downTicks),
                        TheInGameUI != NULL && TheInGameUI->getPendingPlaceType() != NULL );
                if (kind == SDLTOUCH_DRAG_PAN)
                        return;

                const Int downIx = (Int)s_touch.downX, downIy = (Int)s_touch.downY;

                if (kind == SDLTOUCH_DRAG_PLACE)
                {
                        // Anchor the building where the finger first touched, exactly as a real
                        // press fires before any drag: PlaceEventTranslator anchors on the down.
                        pushMousePosition( downIx, downIy );
                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN, downIx, downIy );
                        pushMousePosition( (Int)px, (Int)py );
                        s_touch.phase = TouchState::PLACING;
                        return;
                }
                if (kind == SDLTOUCH_DRAG_SELECT)
                {
                        // Held past SELECT_HOLD_MS then dragged: an area-selection box.
                        // SelectionXlat's button-down records the anchor; its position messages draw
                        // the box once past TheMouse's drag tolerance.
                        pushMousePosition( downIx, downIy );
                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN, downIx, downIy );
                        pushMousePosition( (Int)px, (Int)py );
                        s_touch.phase = TouchState::SELECTING;
                        return;
                }

                // An ordinary drag: a direct camera pan, no message-stream involvement.
                s_touch.phase = TouchState::PANNING;
                s_touch.panLastPxX = px;
                s_touch.panLastPxY = py;
                return;
        }

        if (s_touch.phase == TouchState::SELECTING && fingerId == s_touch.finger1)
        {
                pushMousePosition( (Int)px, (Int)py );
        }
        else if (s_touch.phase == TouchState::PLACING && fingerId == s_touch.finger1)
        {
                pushMousePosition( (Int)px, (Int)py );
        }
        // A TARGETING finger needs no message: SdlTouch_update publishes its point as the preview
        // the radius decal follows, and the commit is the release click.
        // PANNING and TWOFINGER apply their camera effect once per frame, in SdlTouch_update.
}

void onFingerUp( Real px, Real py, Uint64 fingerId, Bool canceled )
{
        if (fingerId != s_touch.finger1 && !(s_touch.phase == TouchState::TWOFINGER && fingerId == s_touch.finger2))
                return;

        const Int downIx = (Int)s_touch.downX, downIy = (Int)s_touch.downY;
        Bool continueAsSinglePan = FALSE;
        Bool startedMomentum = FALSE;

        switch (s_touch.phase)
        {
                case TouchState::PENDING:
                {
                        // A canceled touch must not become a committed tap.
                        if (canceled)
                                break;

                        const Bool longPress = (SDL_GetTicks() - s_touch.downTicks) >= SdlTouch_LONG_PRESS_MS;

                        // A long press on the minimap moves the camera there instead of ordering.
                        if (longPress && lookAtRadarPoint( downIx, downIy ))
                                break;

                        // A long press on the battlefield, with no build pending and not on UI, is
                        // the cancel/deselect gesture.
                        if (longPress && !(TheInGameUI != NULL && TheInGameUI->getPendingPlaceType() != NULL)
                                        && !pointBelongsToUi( downIx, downIy ))
                        {
                                cancelOrDeselect();
                                break;
                        }

                        // Otherwise the window manager gets first refusal: a tap inside an open panel
                        // is UI, and must not also act on the battlefield beneath it.
                        if (uiTookTap( downIx, downIy, longPress ))
                                break;

                        if (TheInGameUI != NULL && TheInGameUI->getPendingPlaceType() != NULL)
                        {
                                // Placement is PlaceEventTranslator's own press/drag/release machine:
                                // a click faithfully represents a press-and-release here.
                                pushMousePosition( downIx, downIy );
                                pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN, downIx, downIy );
                                pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, downIx, downIy );
                                break;
                        }

                        const Real dist = fabsf( s_touch.downX - s_touch.lastTapX ) + fabsf( s_touch.downY - s_touch.lastTapY );
                        const Bool isDoubleTap = SdlTouch_isDoubleTap( s_touch.hasLastTap,
                                (UnsignedInt)(SDL_GetTicks() - s_touch.lastTapTicks), dist );

                        if (isDoubleTap)
                                battlefieldDoubleTap( downIx, downIy );
                        else
                                battlefieldTap( downIx, downIy );

                        s_touch.hasLastTap = !isDoubleTap;
                        if (!isDoubleTap)
                        {
                                s_touch.lastTapTicks = SDL_GetTicks();
                                s_touch.lastTapX = s_touch.downX;
                                s_touch.lastTapY = s_touch.downY;
                        }
                        break;
                }

                case TouchState::PANNING:
                {
                        const Real speed = fabsf( s_touch.panVelX ) + fabsf( s_touch.panVelY );
                        if (SdlTouch_momentumStarts( speed ))
                        {
                                s_touch.momentumX = s_touch.lastX;
                                s_touch.momentumY = s_touch.lastY;
                                s_touch.phase = TouchState::MOMENTUM;
                                startedMomentum = TRUE;
                        }
                        break;
                }

                case TouchState::TWOFINGER:
                        if (!canceled)
                        {
                                const Real move1 = fabsf( s_touch.f1px - s_touch.twoDownX1 ) + fabsf( s_touch.f1py - s_touch.twoDownY1 );
                                const Real move2 = fabsf( s_touch.f2px - s_touch.twoDownX2 ) + fabsf( s_touch.f2py - s_touch.twoDownY2 );
                                if (SdlTouch_isTwoFingerTap( move1, move2 ))
                                {
                                        cancelOrDeselect();
                                }
                                else
                                {
                                        // The other finger keeps controlling the camera.
                                        if (fingerId == s_touch.finger1)
                                        {
                                                s_touch.finger1 = s_touch.finger2;
                                                s_touch.lastX = s_touch.panLastPxX = s_touch.f2px;
                                                s_touch.lastY = s_touch.panLastPxY = s_touch.f2py;
                                        }
                                        else
                                        {
                                                s_touch.panLastPxX = s_touch.lastX;
                                                s_touch.panLastPxY = s_touch.lastY;
                                        }
                                        s_touch.finger2 = 0;
                                        s_touch.phase = TouchState::PANNING;
                                        continueAsSinglePan = TRUE;
                                }
                        }
                        break;

                case TouchState::PLACING:
                        // A canceled touch never sends a matching up, so back out of placement
                        // entirely rather than leave the anchor committed with no way to resolve it.
                        if (canceled)
                        {
                                if (TheInGameUI != NULL)
                                        TheInGameUI->placeBuildAvailable( NULL, NULL );
                        }
                        else
                        {
                                pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, (Int)px, (Int)py );
                        }
                        break;

                case TouchState::SELECTING:
                        // Even a canceled touch sends the button-up: SelectionTranslator only clears
                        // its drag lock from that handler, so not sending it would leave the state stuck.
                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, (Int)px, (Int)py );
                        break;

                case TouchState::TARGETING:
                        if (!canceled)
                        {
                                const Real movedFromDown = fabsf( px - s_touch.downX ) + fabsf( py - s_touch.downY );
                                const Bool longPress = (SDL_GetTicks() - s_touch.downTicks) >= SdlTouch_LONG_PRESS_MS;
                                if (longPress && SdlTouch_withinTapDeadZone( movedFromDown ))
                                {
                                        // A held, unmoved finger gets you out of targeting mode.
                                        cancelOrDeselect();
                                }
                                else
                                {
                                        // The aim stayed native; only the commit is a click, because
                                        // GUICommandTranslator (guard, evacuate, ...) dispatches on
                                        // MSG_MOUSE_LEFT_CLICK and nothing else.
                                        pushMousePosition( (Int)px, (Int)py );
                                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_DOWN, (Int)px, (Int)py );
                                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, (Int)px, (Int)py );
                                }
                        }
                        break;

                case TouchState::UI_PRESS:
                        // Release at the original anchor, so finger drift during a hold cannot leave
                        // the widget.  A hold long enough to read a description necessarily completes
                        // a click, so undo the intent: back out of whatever it armed.
                        pushMousePosition( downIx, downIy );
                        pushMouseButton( GameMessage::MSG_RAW_MOUSE_LEFT_BUTTON_UP, downIx, downIy );
                        if ((SDL_GetTicks() - s_touch.downTicks) >= SdlTouch_LONG_PRESS_MS)
                                cancelOrDeselect();
                        break;

                default:
                        break;
        }

        if (!continueAsSinglePan && !startedMomentum)
                resetGesture( TouchState::IDLE );
}

}  // anonymous namespace

// ---- Public entry points -------------------------------------------------------------------------------------------

void SdlTouch_install( void )
{
        static Bool installed = FALSE;
        if (installed)
                return;
        installed = TRUE;
        // The one thing this layer must guarantee: a touch never also arrives as a mouse event.
        // With the hint off, SDL delivers only SDL_EVENT_FINGER_*, which SdlInput_dispatch hands here.
        SDL_SetHint( SDL_HINT_TOUCH_MOUSE_EVENTS, "0" );
}

Bool SdlTouch_dispatch( const SDL_Event &event )
{
        switch (event.type)
        {
                case SDL_EVENT_FINGER_DOWN:
                {
                        Real px, py;
                        Int gx, gy;
                        touchPoint( event.tfinger.x, event.tfinger.y, gx, gy );
                        px = (Real)gx;
                        py = (Real)gy;
                        onFingerDown( px, py, event.tfinger.fingerID );
                        return TRUE;
                }
                case SDL_EVENT_FINGER_MOTION:
                {
                        Int gx, gy;
                        touchPoint( event.tfinger.x, event.tfinger.y, gx, gy );
                        onFingerMotion( (Real)gx, (Real)gy, event.tfinger.fingerID );
                        return TRUE;
                }
                case SDL_EVENT_FINGER_UP:
                case SDL_EVENT_FINGER_CANCELED:
                {
                        Int gx, gy;
                        touchPoint( event.tfinger.x, event.tfinger.y, gx, gy );
                        onFingerUp( (Real)gx, (Real)gy, event.tfinger.fingerID, event.type == SDL_EVENT_FINGER_CANCELED );
                        return TRUE;
                }
                default:
                        return FALSE;
        }
}

void SdlTouch_update( void )
{
        // Publish the live finger position as the preview point the radius cursor and the placement
        // ghost read, but never for a touch on the UI (a ghost would then draw behind the button).
        if (s_touch.phase == TouchState::PENDING || s_touch.phase == TouchState::PANNING || s_touch.phase == TouchState::TARGETING)
        {
                const Int ix = (Int)s_touch.lastX, iy = (Int)s_touch.lastY;
                if (!pointBelongsToUi( ix, iy ))
                        publishPreviewPoint( ix, iy );
        }

        switch (s_touch.phase)
        {
                case TouchState::PANNING:
                {
                        if (TheTacticalView == NULL)
                                break;
                        const Real dx = s_touch.lastX - s_touch.panLastPxX;
                        const Real dy = s_touch.lastY - s_touch.panLastPxY;
                        s_touch.panVelX = dx;
                        s_touch.panVelY = dy;
                        if (dx != 0.0f || dy != 0.0f)
                                panCamera( s_touch.panLastPxX, s_touch.panLastPxY, s_touch.lastX, s_touch.lastY );
                        s_touch.panLastPxX = s_touch.lastX;
                        s_touch.panLastPxY = s_touch.lastY;
                        break;
                }

                case TouchState::TWOFINGER:
                {
                        const Real cx = (s_touch.f1px + s_touch.f2px) * 0.5f;
                        const Real cy = (s_touch.f1py + s_touch.f2py) * 0.5f;
                        const Real dcx = cx - s_touch.twoCentroidLastX;
                        const Real dcy = cy - s_touch.twoCentroidLastY;
                        if (dcx != 0.0f || dcy != 0.0f)
                                panCamera( s_touch.twoCentroidLastX, s_touch.twoCentroidLastY, cx, cy );
                        s_touch.twoCentroidLastX = cx;
                        s_touch.twoCentroidLastY = cy;

                        const Real dx = s_touch.f2px - s_touch.f1px, dy = s_touch.f2py - s_touch.f1py;
                        const Real dist = sqrtf( dx * dx + dy * dy );
                        if (s_touch.twoDistLastPx > 0.0f)
                                zoomCamera( dist - s_touch.twoDistLastPx );
                        s_touch.twoDistLastPx = dist;

                        const Real angle = atan2f( dy, dx );
                        Real twist = angle - s_touch.twoAngleLastRad;
                        // keep the delta in (-pi, pi] so a crossing of the +-pi seam is not a full turn
                        while (twist > PI)
                                twist -= 2.0f * PI;
                        while (twist < -PI)
                                twist += 2.0f * PI;
                        s_touch.twoAngleLastRad = angle;

                        if (!s_touch.twoRotateArmed)
                        {
                                s_touch.twoTwistAccumRad += twist;
                                if (fabsf( s_touch.twoTwistAccumRad ) >= TWIST_ARM_RAD)
                                {
                                        s_touch.twoRotateArmed = TRUE;
                                        // apply only from here on, so the arming twist is not replayed
                                }
                        }
                        else
                        {
                                rotateCamera( twist );
                        }
                        break;
                }

                case TouchState::MOMENTUM:
                {
                        if (TheTacticalView == NULL)
                                break;
                        const Real newX = s_touch.momentumX + s_touch.panVelX;
                        const Real newY = s_touch.momentumY + s_touch.panVelY;
                        panCamera( s_touch.momentumX, s_touch.momentumY, newX, newY );
                        s_touch.momentumX = newX;
                        s_touch.momentumY = newY;
                        s_touch.panVelX *= SdlTouch_MOMENTUM_FRICTION;
                        s_touch.panVelY *= SdlTouch_MOMENTUM_FRICTION;
                        const Real speed = fabsf( s_touch.panVelX ) + fabsf( s_touch.panVelY );
                        if (SdlTouch_momentumEnds( speed ))
                                resetGesture( TouchState::IDLE );
                        break;
                }

                default:
                        break;
        }
}

void SdlTouch_reset( void )
{
        resetGesture( TouchState::IDLE );
}

Bool SdlTouch_isCompiledIn( void )
{
        return TRUE;
}

#else   // !ZH_ANDROID

// Off Android a touch never arrives, so these are inert.  They exist so SdlInput_dispatch can call
// them unconditionally and the desktop build has one code path.

void SdlTouch_install( void ) {}
Bool SdlTouch_dispatch( const SDL_Event & ) { return FALSE; }
void SdlTouch_update( void ) {}
void SdlTouch_reset( void ) {}
Bool SdlTouch_isCompiledIn( void ) { return FALSE; }

#endif  // ZH_ANDROID
