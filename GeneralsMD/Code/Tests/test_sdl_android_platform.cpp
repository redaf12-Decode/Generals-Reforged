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

// Phase 5: the Android lifecycle decision (SdlDevice/Common/SdlAndroidPlatform.h).  The event watch,
// the JNI and the SDL calls are compiled out on the host (ZH_ANDROID == 0); what is left - and what
// this drives - is the pure state machine that decides whether an application-lifecycle event stops
// the audio output, starts it again, or does nothing.  A second stop on a second pause, or a start
// after the app was terminated, is a bug here, so it is checked here.

#include "test_harness.h"

#include "SdlDevice/Common/SdlAndroidPlatform.h"

namespace {

// One event through the machine, reporting whether it stopped or started the device.
struct Step
{
	SdlAndroidLifecycle state;
	Bool paused;
	Bool resumed;
};

Step step( SdlAndroidLifecycle before, SdlAndroidLifecycleEvent event )
{
	Step s;
	s.state = SdlAndroidLifecycle_onEvent( before, event );
	s.paused = SdlAndroidLifecycle_pausesDevice( before, s.state );
	s.resumed = SdlAndroidLifecycle_resumesDevice( before, s.state );
	return s;
}

} // namespace

// A fresh install is in the foreground, playing, alive.
TEST(android_lifecycle_starts_in_the_foreground)
{
	const SdlAndroidLifecycle start = SdlAndroidLifecycle_begin();
	CHECK(start.backgrounded == FALSE);
	CHECK(start.devicePaused == FALSE);
	CHECK(start.terminated == FALSE);
}

// Going to the background stops the output once and only once, and returning starts it once and only
// once: Android posts WILL_ENTER_BACKGROUND more than once for one pause.
TEST(android_lifecycle_pauses_and_resumes_the_device_exactly_once_per_backgrounding)
{
	SdlAndroidLifecycle state = SdlAndroidLifecycle_begin();

	// The first background event is the one that stops the output.
	Step back = step(state, SDL_ANDROID_EVENT_BACKGROUND);
	CHECK(back.state.backgrounded == TRUE);
	CHECK(back.paused == TRUE);
	CHECK(back.resumed == FALSE);
	state = back.state;

	// A second background event changes nothing and does not stop the device again.
	Step again = step(state, SDL_ANDROID_EVENT_BACKGROUND);
	CHECK(again.paused == FALSE);
	CHECK(again.resumed == FALSE);
	state = again.state;

	// Returning to the foreground starts the output again, once.
	Step front = step(state, SDL_ANDROID_EVENT_FOREGROUND);
	CHECK(front.state.backgrounded == FALSE);
	CHECK(front.paused == FALSE);
	CHECK(front.resumed == TRUE);
	state = front.state;

	// A second foreground event does nothing.
	Step frontAgain = step(state, SDL_ANDROID_EVENT_FOREGROUND);
	CHECK(frontAgain.paused == FALSE);
	CHECK(frontAgain.resumed == FALSE);
}

// A foreground event with nothing paused must not start the device: it was never stopped by this
// layer, and starting a device the desktop path manages would be the wrong thing.
TEST(android_lifecycle_does_not_start_a_device_it_never_stopped)
{
	const Step s = step(SdlAndroidLifecycle_begin(), SDL_ANDROID_EVENT_FOREGROUND);
	CHECK(s.resumed == FALSE);
	CHECK(s.state.backgrounded == FALSE);
}

// Termination stops a playing app once, and nothing starts the output again afterwards.
TEST(android_lifecycle_stops_on_termination_and_never_resumes_after_it)
{
	Step terminated = step(SdlAndroidLifecycle_begin(), SDL_ANDROID_EVENT_TERMINATE);
	CHECK(terminated.state.terminated == TRUE);
	CHECK(terminated.paused == TRUE);
	CHECK(terminated.resumed == FALSE);

	// Even a foreground event after termination does not bring the app back.
	Step after = step(terminated.state, SDL_ANDROID_EVENT_FOREGROUND);
	CHECK(after.state.terminated == TRUE);
	CHECK(after.state.backgrounded == TRUE);
	CHECK(after.resumed == FALSE);
	CHECK(after.paused == FALSE);
}

// A terminate that arrives while already backgrounded (the device is already stopped) does not stop
// it a second time, but it does mark the app gone.
TEST(android_lifecycle_termination_while_backgrounded_does_not_stop_twice)
{
	SdlAndroidLifecycle state = step(SdlAndroidLifecycle_begin(), SDL_ANDROID_EVENT_BACKGROUND).state;
	const Step terminated = step(state, SDL_ANDROID_EVENT_TERMINATE);
	CHECK(terminated.state.terminated == TRUE);
	CHECK(terminated.paused == FALSE);
	CHECK(terminated.resumed == FALSE);
}
