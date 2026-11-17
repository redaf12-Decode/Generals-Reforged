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

// SdlAndroidPlatform: the Android platform services the game needs and the desktop does not (Phase 5).
//   - the application lifecycle: SDL posts WILL_ENTER_BACKGROUND and DID_ENTER_FOREGROUND to an event
//     watch, and the game's audio output is stopped when it goes to the background and started again
//     when it returns (SdlDevice/Common/SdlGameEngine.cpp, MSS/mss_ex_device.h);
//   - the screen stays on while the game is in the foreground, so a match is not interrupted by the
//     display dimming (SDL's own default already does this, but it is asked for explicitly here);
//   - the Wi-Fi multicast lock the LAN lobby needs: Android drops broadcast and multicast packets
//     addressed to an application until it holds one, and the lobby's broadcast listener
//     (LANAPI::listenForBroadcasts, GameNetwork/udp.cpp) would otherwise hear nothing.
//
// The lifecycle *decisions* are free functions of their arguments, with no SDL and no engine state,
// so Tests/test_sdl_android_platform.cpp drives them on the host where the SDL/JNI half is compiled
// out (ZH_ANDROID == 0).  The event watch, the SDL call and the JNI calls live in the .cpp behind
// ZH_ANDROID, as SdlTouch's Android half does.

#pragma once

#ifndef __SDLANDROIDPLATFORM_H
#define __SDLANDROIDPLATFORM_H

#include "Lib/BaseType.h"

// ---- The lifecycle decision (pure; the same code on every platform, tested on the host) -----------

/// What an application-lifecycle event means to this layer.
enum SdlAndroidLifecycleEvent
{
	SDL_ANDROID_EVENT_BACKGROUND,	///< WILL_ENTER_BACKGROUND: the app is leaving the foreground
	SDL_ANDROID_EVENT_FOREGROUND,	///< DID_ENTER_FOREGROUND: the app is interactive again
	SDL_ANDROID_EVENT_TERMINATE	///< TERMINATING: the OS is taking the app away
};

/** The lifecycle layer's whole state.  `devicePaused` is the only thing that must not go wrong: an
        * output device stopped twice, or started twice, or started after the app was killed, is a bug, and
        * this is what makes "stop it exactly once" checkable. */
struct SdlAndroidLifecycle
{
	Bool backgrounded;	///< the app is in the background
	Bool devicePaused;	///< the output device was stopped by this layer and not yet started again
	Bool terminated;	///< TERMINATING was seen: nothing more will be started
};

/// The state at install time: foreground, playing, alive.
SdlAndroidLifecycle SdlAndroidLifecycle_begin( void );

/** The state after one event.  Backgrounding while already backgrounded changes nothing (Android posts
        * the pause event more than once), and neither does a foreground event in the foreground. */
SdlAndroidLifecycle SdlAndroidLifecycle_onEvent( SdlAndroidLifecycle state, SdlAndroidLifecycleEvent event );

/// TRUE when this transition is the one that should stop the output device: a backgrounding that was
/// not already backgrounded, or the termination of a playing app.
Bool SdlAndroidLifecycle_pausesDevice( SdlAndroidLifecycle before, SdlAndroidLifecycle after );

/// TRUE when this transition is the one that should start the output device: a return to the foreground
/// that is not a terminated app.
Bool SdlAndroidLifecycle_resumesDevice( SdlAndroidLifecycle before, SdlAndroidLifecycle after );

// ---- The services (installed by SdlGameEngine; a no-op off Android) -------------------------------

/** Installs the lifecycle event watch, keeps the screen on and takes the Wi-Fi multicast lock.  Called
        * once, after SDL's video and events are up.  A no-op off Android. */
void SdlAndroidPlatform_install( void );

/** Removes the event watch and releases the multicast lock.  Called once, as the engine goes away.  A
        * no-op off Android. */
void SdlAndroidPlatform_shutdown( void );

#endif // __SDLANDROIDPLATFORM_H
