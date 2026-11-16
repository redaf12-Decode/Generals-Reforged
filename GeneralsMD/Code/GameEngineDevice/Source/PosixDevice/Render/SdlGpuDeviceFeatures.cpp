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

#include "SdlGpuDeviceFeatures.h"

#include <string.h>

// The drop order, least-needed first, is fixed here and read by everything else.  Kept as data so the
// name and the flags cannot drift apart.
namespace {
const char *const DROP_ORDER[] = {
        "clip distance",
        "indirect draw first instance",
        "depth clamping",
        "anisotropy",
};
const unsigned int DROP_COUNT = sizeof(DROP_ORDER) / sizeof(DROP_ORDER[0]);
}

unsigned int Sdl_Gpu_Attempt_Count()
{
        return DROP_COUNT + 1;
}

SdlGpuOptionalFeatures Sdl_Gpu_Features_For_Attempt(unsigned int attempt)
{
        // Attempt 0 asks for all four, exactly what SDL_CreateGPUDevice's default does; each later
        // attempt drops one more, in DROP_ORDER's order.
        SdlGpuOptionalFeatures features = { true, true, true, true };
        if (attempt > DROP_COUNT) attempt = DROP_COUNT;
        if (attempt >= 1) features.ClipDistance = false;
        if (attempt >= 2) features.IndirectDrawFirstInstance = false;
        if (attempt >= 3) features.DepthClamping = false;
        if (attempt >= 4) features.Anisotropy = false;
        return features;
}

const char * Sdl_Gpu_Dropped_Feature_Names(unsigned int attempt)
{
        static char names[128];
        if (attempt > DROP_COUNT) attempt = DROP_COUNT;
        names[0] = '\0';
        size_t used = 0;
        for (unsigned int i = 0; i < attempt; ++i) {
                const char *name = DROP_ORDER[i];
                const size_t length = strlen(name);
                if (used + length + 3 >= sizeof(names)) break;
                if (used != 0) {
                        names[used++] = ',';
                        names[used++] = ' ';
                }
                memcpy(names + used, name, length);
                used += length;
                names[used] = '\0';
        }
        return names;
}
