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

// The optional Vulkan device features SDL3's GPU API requires by default (SDL_gpu.h:221-231).  On a
// desktop driver they are all present, so a default device is the whole story; on Android they are
// not - depthClamp, shaderClipDistance and drawIndirectFirstInstance are optional in Vulkan 1.0 and
// are missing on real mobile drivers, and SDL3 refuses a physical device that lacks one it was asked
// for (SDL_gpu_vulkan.c:12259-12266).  A device that refuses the default set is asked again without
// the next feature the game can do without, so a mobile GPU is a supported device rather than a
// failed start.  See docs/android-port/architecture/13-android-vulkan-rendering.md.
//
// Which features the game actually needs, measured in the source rather than assumed:
//   - shaderClipDistance: nothing.  No generated or hand-written program writes a clip distance
//     (SV_ClipDistance/gl_ClipDistance appear in no game shader), and D3D9's user clip planes are
//     stored but never reach a program (PosixDevice9::SetClipPlane only memcpys).  Dropped first.
//   - drawIndirectFirstInstance: nothing.  The renderer issues no indirect draw (no
//     SDL_DrawGPU*PrimitivesIndirect anywhere).  Dropped second.
//   - depthClamp: only for clamping, and the game wants clipping.  D3D9's D3DRS_CLIPPING defaults on
//     and the device enables it (PosixDevice9Draw.cpp), which is enable_depth_clip = true; without
//     depthClamp SDL3 requires exactly that.  Dropped third, and the pipeline sites set
//     enable_depth_clip = true so the reduced device is legal.
//   - samplerAnisotropy: used.  The caps advertise D3DPTFILTERCAPS_*ANISOTROPIC and texturefilter.cpp
//     selects D3DTEXF_ANISOTROPIC, so an anisotropic sampler is a real request.  Dropped last, and the
//     sampler cache then falls back to linear filtering (SdlSamplerCache).

#pragma once

#ifndef SDLGPUDEVICEFEATURES_H
#define SDLGPUDEVICEFEATURES_H

/// The four optional features a default SDL3 GPU device asks for, each true when it is wanted.
struct SdlGpuOptionalFeatures
{
        bool ClipDistance;
        bool IndirectDrawFirstInstance;
        bool DepthClamping;
        bool Anisotropy;
};

/// How many device-creation attempts there are: attempt 0 asks for all four (the desktop call,
/// unchanged), and each later attempt drops the next feature the game needs least.
unsigned int Sdl_Gpu_Attempt_Count();

/// What attempt `attempt` asks for.  Attempt 0 is all four; the last is none of them.  A function of
/// the attempt alone, so the sequence can be checked without a GPU (Tests/test_sdl_gpu_device_features.cpp).
SdlGpuOptionalFeatures Sdl_Gpu_Features_For_Attempt(unsigned int attempt);

/// A one-line name of the features `attempt` gives up, for the log when a device is made: empty at
/// attempt 0, and "clip distance, indirect draw first instance" at attempt 2, say.
const char * Sdl_Gpu_Dropped_Feature_Names(unsigned int attempt);

#endif // SDLGPUDEVICEFEATURES_H
