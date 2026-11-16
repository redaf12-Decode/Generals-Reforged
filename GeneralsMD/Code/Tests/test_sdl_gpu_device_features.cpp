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

// The device-feature fallback sequence (SdlGpuDeviceFeatures.h): no SDL, no GPU, so it runs wherever
// the suite runs and on the CI host that has neither.  What it holds is the order the game gives
// features up, which is the part of Android device creation a desktop cannot exercise.
#include "SdlGpuDeviceFeatures.h"

#include <stdio.h>
#include <string.h>

static int failures = 0;
#define CHECK(condition) \
        do { if (!(condition)) { ++failures; printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); } } while (0)

int main()
{
        // Attempt 0 is SDL_CreateGPUDevice's default: all four optional features.
        const unsigned int attempts = Sdl_Gpu_Attempt_Count();
        CHECK(attempts == 5);
        const SdlGpuOptionalFeatures first = Sdl_Gpu_Features_For_Attempt(0);
        CHECK(first.ClipDistance && first.IndirectDrawFirstInstance && first.DepthClamping && first.Anisotropy);
        CHECK(strcmp(Sdl_Gpu_Dropped_Feature_Names(0), "") == 0);

        // Each attempt gives up exactly one more, in the order the game needs them least: clip
        // distance, indirect draw first instance, depth clamping, then anisotropy last.
        const SdlGpuOptionalFeatures one = Sdl_Gpu_Features_For_Attempt(1);
        CHECK(!one.ClipDistance && one.IndirectDrawFirstInstance && one.DepthClamping && one.Anisotropy);
        const SdlGpuOptionalFeatures two = Sdl_Gpu_Features_For_Attempt(2);
        CHECK(!two.ClipDistance && !two.IndirectDrawFirstInstance && two.DepthClamping && two.Anisotropy);
        const SdlGpuOptionalFeatures three = Sdl_Gpu_Features_For_Attempt(3);
        CHECK(!three.ClipDistance && !three.IndirectDrawFirstInstance && !three.DepthClamping && three.Anisotropy);
        const SdlGpuOptionalFeatures four = Sdl_Gpu_Features_For_Attempt(4);
        CHECK(!four.ClipDistance && !four.IndirectDrawFirstInstance && !four.DepthClamping && !four.Anisotropy);

        // The last attempt is a device with none of them: every Vulkan 1.0 device can make it.
        CHECK(!(four.ClipDistance || four.IndirectDrawFirstInstance || four.DepthClamping || four.Anisotropy));

        // Monotonic: a later attempt never asks for a feature an earlier one gave up, and it is a
        // function of the attempt alone (a device made at attempt n keeps exactly that feature set).
        for (unsigned int i = 1; i < attempts; ++i) {
                const SdlGpuOptionalFeatures before = Sdl_Gpu_Features_For_Attempt(i - 1);
                const SdlGpuOptionalFeatures after = Sdl_Gpu_Features_For_Attempt(i);
                CHECK(!(after.ClipDistance && !before.ClipDistance));
                CHECK(!(after.IndirectDrawFirstInstance && !before.IndirectDrawFirstInstance));
                CHECK(!(after.DepthClamping && !before.DepthClamping));
                CHECK(!(after.Anisotropy && !before.Anisotropy));
                CHECK((before.ClipDistance + before.IndirectDrawFirstInstance + before.DepthClamping + before.Anisotropy)
                        - (after.ClipDistance + after.IndirectDrawFirstInstance + after.DepthClamping + after.Anisotropy) == 1);
        }

        // The names say what the attempt gave up, so the log line and the drop set cannot drift.
        CHECK(strcmp(Sdl_Gpu_Dropped_Feature_Names(1), "clip distance") == 0);
        CHECK(strcmp(Sdl_Gpu_Dropped_Feature_Names(2), "clip distance, indirect draw first instance") == 0);
        CHECK(strcmp(Sdl_Gpu_Dropped_Feature_Names(4),
                "clip distance, indirect draw first instance, depth clamping, anisotropy") == 0);

        // An out-of-range attempt is clamped rather than reading past the table.
        const SdlGpuOptionalFeatures high = Sdl_Gpu_Features_For_Attempt(999);
        CHECK(!(high.ClipDistance || high.IndirectDrawFirstInstance || high.DepthClamping || high.Anisotropy));

        if (failures) {
                printf("test_sdl_gpu_device_features: %d FAILED\n", failures);
                return 1;
        }
        printf("test_sdl_gpu_device_features: the %u-attempt feature fallback holds\n", attempts);
        return 0;
}
