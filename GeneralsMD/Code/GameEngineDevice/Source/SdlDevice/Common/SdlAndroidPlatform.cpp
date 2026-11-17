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

// SdlAndroidPlatform.cpp: see SdlAndroidPlatform.h.

// Two halves, as SdlTouch.cpp has.  The first is the lifecycle decision - pure functions of their
// arguments - and it compiles on every platform, because Tests/test_sdl_android_platform.cpp drives
// it on the host with no SDL and no JNI.  The second is the event watch and the JNI calls, behind
// ZH_ANDROID.

#include "PreRTS.h"     // This must go first in EVERY cpp file in the GameEngine

#include "SdlDevice/Common/SdlAndroidPlatform.h"

#include "Platform/PlatformPredicates.h"        // ZH_ANDROID

#include <stdio.h>

// ---- The lifecycle decision ----------------------------------------------------------------------

SdlAndroidLifecycle SdlAndroidLifecycle_begin( void )
{
	SdlAndroidLifecycle state;
	state.backgrounded = FALSE;
	state.devicePaused = FALSE;
	state.terminated = FALSE;
	return state;
}

SdlAndroidLifecycle SdlAndroidLifecycle_onEvent( SdlAndroidLifecycle state, SdlAndroidLifecycleEvent event )
{
	switch (event)
	{
		case SDL_ANDROID_EVENT_BACKGROUND:
			state.backgrounded = TRUE;
			break;
		case SDL_ANDROID_EVENT_FOREGROUND:
			// A terminated app is not coming back: a foreground event after it is ignored.
			if (!state.terminated)
				state.backgrounded = FALSE;
			break;
		case SDL_ANDROID_EVENT_TERMINATE:
			state.backgrounded = TRUE;
			state.terminated = TRUE;
			break;
	}
	// The device follows the state, not the event: stopped exactly while the app is not the one
	// the player is looking at.  Keeping it here is what makes a repeated background event a
	// no-op instead of a second stop.
	state.devicePaused = state.backgrounded || state.terminated;
	return state;
}

Bool SdlAndroidLifecycle_pausesDevice( SdlAndroidLifecycle before, SdlAndroidLifecycle after )
{
	// Exactly one stop per backgrounding: the transition into the stopped state, and only that.
	return !before.devicePaused && after.devicePaused;
}

Bool SdlAndroidLifecycle_resumesDevice( SdlAndroidLifecycle before, SdlAndroidLifecycle after )
{
	// The output only starts again for a live app returning to the foreground.
	return before.devicePaused && !after.devicePaused;
}

Bool SdlAndroidLifecycle_cancelsInput( SdlAndroidLifecycle before, SdlAndroidLifecycle after )
{
	// The fingers are lost exactly when the app stops being interactive - the same transition that
	// stops the device - and only then, so a repeated background event is a no-op.
	return !before.devicePaused && after.devicePaused;
}

// ---- The services (installed by SdlGameEngine) ----------------------------------------------------

#if ZH_ANDROID

#include <jni.h>

#include <SDL3/SDL.h>

#include "MSS/mss_ex_device.h"                 // AIL_ex_device_pause/resume: the mix's device
#include "SdlDevice/GameClient/SdlTouch.h"     // SdlTouch_reset: the fingers the backgrounding takes

namespace {

SdlAndroidLifecycle s_lifecycle;
Bool s_installed = FALSE;
jobject s_multicastLock = NULL;

SdlAndroidLifecycleEvent eventKindOf( const SDL_Event &event )
{
	switch (event.type)
	{
		case SDL_EVENT_WILL_ENTER_BACKGROUND:
		case SDL_EVENT_DID_ENTER_BACKGROUND:
			return SDL_ANDROID_EVENT_BACKGROUND;
		case SDL_EVENT_WILL_ENTER_FOREGROUND:
		case SDL_EVENT_DID_ENTER_FOREGROUND:
			return SDL_ANDROID_EVENT_FOREGROUND;
		case SDL_EVENT_TERMINATING:
			return SDL_ANDROID_EVENT_TERMINATE;
	}
	return SDL_ANDROID_EVENT_FOREGROUND;        // unreachable: the watch only asks about these
}

/* One lifecycle event, from SDL's event watch.  The decision is SdlAndroidLifecycle_onEvent's (the
   host-tested half); this only carries it out: stop the output device when the app leaves the
   foreground, start it again when it returns.  The device is held open but stopped
   (MSS/mss_ex_device.h), so the music picks up mid-phrase and no sound restarts. */
bool lifecycleWatch( void *, SDL_Event *event )
{
	switch (event->type)
	{
		case SDL_EVENT_WILL_ENTER_BACKGROUND:
		case SDL_EVENT_DID_ENTER_BACKGROUND:
		case SDL_EVENT_WILL_ENTER_FOREGROUND:
		case SDL_EVENT_DID_ENTER_FOREGROUND:
		case SDL_EVENT_TERMINATING:
			break;
		default:
			return true;
	}

	const SdlAndroidLifecycle before = s_lifecycle;
	s_lifecycle = SdlAndroidLifecycle_onEvent( s_lifecycle, eventKindOf( *event ) );

	if (SdlAndroidLifecycle_pausesDevice( before, s_lifecycle ))
	{
		AIL_ex_device_pause();
		DEBUG_LOG(( "SdlAndroidPlatform: the app left the foreground; the audio output is stopped\n" ));
	}
	else if (SdlAndroidLifecycle_resumesDevice( before, s_lifecycle ))
	{
		AIL_ex_device_resume();
		DEBUG_LOG(( "SdlAndroidPlatform: the app is in the foreground again; the audio output is started\n" ));
	}

	// A finger that was down when the app left the foreground never gets its FINGER_UP (SDL cancels
	// none on a backgrounding), so the gesture has to be resolved and dropped here or the next finger
	// lands as the second of a stale one.
	if (SdlAndroidLifecycle_cancelsInput( before, s_lifecycle ))
	{
		SdlTouch_reset();
		DEBUG_LOG(( "SdlAndroidPlatform: the app left the foreground; the live touch gesture is dropped\n" ));
	}
	return true;
}

/* The Wi-Fi multicast lock the LAN lobby needs.  Android's Wi-Fi power save drops broadcast and
   multicast datagrams addressed to an application until it holds a WifiManager.MulticastLock, and
   the lobby's broadcast listener (LANAPI::listenForBroadcasts) would otherwise hear nothing.  Held
   for the life of the process, released in shutdown.
 *
 * It needs android.permission.CHANGE_WIFI_MULTICAST_STATE in the application manifest, which this
 * repository does not carry yet (the Gradle/APK shell is the later packaging phase); where the
 * permission is absent, createMulticastLock throws and this logs it and carries on rather than
 * failing the start.  The LAN lobby still works between devices on a network that does not drop
 * the packets. */
void acquireMulticastLock( void )
{
	JNIEnv *env = (JNIEnv *)SDL_GetAndroidJNIEnv();
	if (env == NULL)
	{
		DEBUG_LOG(( "SdlAndroidPlatform: no JNIEnv; the Wi-Fi multicast lock is not taken\n" ));
		return;
	}
	jobject activity = (jobject)SDL_GetAndroidActivity();     // a local reference
	if (activity == NULL)
	{
		DEBUG_LOG(( "SdlAndroidPlatform: no activity; the Wi-Fi multicast lock is not taken\n" ));
		return;
	}

	// C++ JNI: JNIEnv is a class with member functions, not the C struct of function pointers.
	jclass activityClass = env->GetObjectClass( activity );
	jmethodID getSystemService = env->GetMethodID( activityClass, "getSystemService",
			"(Ljava/lang/String;)Ljava/lang/Object;" );
	jstring wifiService = env->NewStringUTF( "wifi" );      // Context.WIFI_SERVICE
	jobject wifiManager = env->CallObjectMethod( activity, getSystemService, wifiService );

	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		DEBUG_LOG(( "SdlAndroidPlatform: WifiManager is unavailable; the Wi-Fi multicast lock is not taken\n" ));
	}
	else if (wifiManager != NULL)
	{
		jclass wifiClass = env->GetObjectClass( wifiManager );
		jmethodID createLock = env->GetMethodID( wifiClass, "createMulticastLock",
				"(Ljava/lang/String;)Landroid/net/wifi/WifiManager$MulticastLock;" );
		jstring tag = env->NewStringUTF( "ZeroHourLAN" );
		jobject lock = env->CallObjectMethod( wifiManager, createLock, tag );

		if (env->ExceptionCheck())
		{
			env->ExceptionClear();
			DEBUG_LOG(( "SdlAndroidPlatform: createMulticastLock refused (is CHANGE_WIFI_MULTICAST_STATE "
					"declared?); the LAN lobby may not hear broadcasts\n" ));
		}
		else if (lock != NULL)
		{
			jclass lockClass = env->GetObjectClass( lock );
			jmethodID setReferenceCounted = env->GetMethodID( lockClass, "setReferenceCounted", "(Z)V" );
			jmethodID acquire = env->GetMethodID( lockClass, "acquire", "()V" );
			env->CallVoidMethod( lock, setReferenceCounted, JNI_FALSE );
			env->CallVoidMethod( lock, acquire );
			if (env->ExceptionCheck())
			{
				env->ExceptionClear();
				DEBUG_LOG(( "SdlAndroidPlatform: MulticastLock.acquire failed; the LAN lobby may not hear broadcasts\n" ));
			}
			else
			{
				s_multicastLock = env->NewGlobalRef( lock );
				DEBUG_LOG(( "SdlAndroidPlatform: Wi-Fi multicast lock held for the LAN lobby\n" ));
			}
			env->DeleteLocalRef( lock );
			env->DeleteLocalRef( tag );
			env->DeleteLocalRef( wifiClass );
		}
		env->DeleteLocalRef( wifiManager );
	}
	env->DeleteLocalRef( wifiService );
	env->DeleteLocalRef( activityClass );
	env->DeleteLocalRef( activity );
}

void releaseMulticastLock( void )
{
	if (s_multicastLock == NULL)
		return;
	JNIEnv *env = (JNIEnv *)SDL_GetAndroidJNIEnv();
	if (env != NULL)
	{
		jclass lockClass = env->GetObjectClass( s_multicastLock );
		jmethodID release = env->GetMethodID( lockClass, "release", "()V" );
		env->CallVoidMethod( s_multicastLock, release );
		if (env->ExceptionCheck())
			env->ExceptionClear();
		env->DeleteLocalRef( lockClass );
		env->DeleteGlobalRef( s_multicastLock );
	}
	s_multicastLock = NULL;
}

} // namespace

void SdlAndroidPlatform_install( void )
{
	if (s_installed)
		return;
	s_installed = TRUE;
	s_lifecycle = SdlAndroidLifecycle_begin();

	SDL_AddEventWatch( lifecycleWatch, NULL );

	/* The screen stays on while the game is in the foreground.  SDL already disables the screen saver
	   unless SDL_HINT_VIDEO_ALLOW_SCREENSAVER is set, and this asks for it again so the intent is
	   explicit here rather than inherited. */
	SDL_DisableScreenSaver();

	acquireMulticastLock();

	DEBUG_LOG(( "SdlAndroidPlatform: installed (lifecycle watch, screen-on, multicast lock)\n" ));
}

void SdlAndroidPlatform_shutdown( void )
{
	if (!s_installed)
		return;
	s_installed = FALSE;
	SDL_RemoveEventWatch( lifecycleWatch, NULL );
	releaseMulticastLock();
	DEBUG_LOG(( "SdlAndroidPlatform: shut down\n" ));
}

#else   // !ZH_ANDROID

// Off Android there is no application lifecycle to follow and no multicast lock to take: the
// desktop's window focus (SdlGameEngine's SDL_EVENT_WINDOW_FOCUS_*) is what pauses and resumes the
// audio there.  The functions exist so the callers need no #if of their own.
void SdlAndroidPlatform_install( void ) {}
void SdlAndroidPlatform_shutdown( void ) {}

#endif  // ZH_ANDROID
