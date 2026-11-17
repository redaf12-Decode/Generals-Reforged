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
 * The mix's output device, held open but stopped.  Not Miles 6.5 API - an extension, "AIL_ex_" like
 * the capture and the PCM voice, implemented only by miles_miniaudio.cpp.
 *
 * Why it exists.  Android suspends the whole application when it goes to the background, and an
 * audio stream that keeps running there is either cut off by the OS (with a log full of underruns)
 * or, worse, keeps playing over whatever the player switched to.  The platform's answer is to stop
 * the stream when the app loses the foreground and start it again when it returns.  Stopping the
 * miniaudio device (ma_device_stop) does exactly that and nothing more: the device stays open, every
 * sample's voice and every stream's decoder stay where they are, so the music picks up mid-phrase
 * and no sound restarts from the beginning - which tearing the device down and rebuilding it, as
 * AIL_shutdown/AIL_quick_startup do, would not.
 *
 * The engine's own volumes are a separate matter (AudioManager::pauseAudio/loseFocus): this only
 * silences the output at the device, and the two are used together (SdlDevice/Common/SdlAndroidPlatform.cpp).
 *
 * The engine lock serialises these calls with the rest of the Miles surface.
 */

#ifndef MSS_EX_DEVICE_H
#define MSS_EX_DEVICE_H

/* Windows builds miles_miniaudio.cpp too, for -wav (miles_dispatch.cpp), and it defines these under
   their plain names; the desktop path there uses XAudio2 and calls none of them. */
#if defined(_WIN32) && !defined(MSS_BACKEND_PREFIX)
#error "mss_ex_device.h is miles_miniaudio's; on Windows the desktop path is XAudio2's"
#endif

#include "MSS/MSS.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Stops the mix's output.  A device that was never opened, or the null device of an offline mix
   (AIL_ex_offline_mix), answers with no effect, so the caller needs no state of its own. */
void AILCALL AIL_ex_device_pause(void);

/* Starts it again.  A no-op when it was never opened or is already running. */
void AILCALL AIL_ex_device_resume(void);

/* Whether the digital driver is open (AIL_quick_startup opened a device): what the lifecycle layer
   asks before it bothers to pause or resume. */
S32  AILCALL AIL_ex_device_is_open(void);

#ifdef __cplusplus
}
#endif

#endif /* MSS_EX_DEVICE_H */
