/*
**      Command & Conquer Generals Zero Hour(tm)
**      Copyright 2025 Electronic Arts Inc.
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

package io.github.olcayseygan.zerohourreforged;

/**
 * The application's requirements, as one place both the in-app screen and
 * {@link CompatibilityEvaluator} read.
 *
 * <p>Every value here is derived from the Android build itself, not chosen: the SDK floor, ABI,
 * Vulkan requirement set and the drop order of the optional Vulkan features are the numbers the
 * native build and the SDL3-GPU renderer are written against. The evidence, per value, is in
 * {@code docs/android-port/audit/ANDROID-IMPLEMENTATION-FACTS.md} and
 * {@code docs/android-port/audit/ANDROID-HARDWARE-COMPATIBILITY.md}, which restate the same values.
 *
 * <p>This class is pure Java (no Android types) so the requirements can be read and tested on the
 * host. It mirrors {@code android/app/build.gradle} ({@code minSdk}, {@code abiFilters}) and
 * {@code SdlGpuDeviceFeatures.h} (the optional-feature drop order).
 */
public final class AndroidRequirements {

    private AndroidRequirements() {
    }

    /** {@code minSdk} in {@code android/app/build.gradle}; the native engine's NDK API floor. */
    public static final int MIN_SDK_INT = 28;

    /** The Android release {@link #MIN_SDK_INT} names, for display. */
    public static final String MIN_ANDROID_NAME = "Android 9 (API 28)";

    /** The only ABI the port builds ({@code abiFilters 'arm64-v8a'}). */
    public static final String SUPPORTED_ABI = "arm64-v8a";

    /**
     * The lowest Vulkan API version the renderer needs: SDL3 3.4.16's GPU floor
     * ({@code SDL_gpu.h:221-231}). Packed as {@code VK_MAKE_VERSION(major, minor, patch)} =
     * {@code (major << 22) | (minor << 12) | patch}, the same packing Android reports through
     * {@code PackageManager.FEATURE_VULKAN_HARDWARE_VERSION}.
     */
    public static final int REQUIRED_VULKAN_API_VERSION = packVulkanVersion(1, 0, 0);

    /** Display name of {@link #REQUIRED_VULKAN_API_VERSION}. */
    public static final String REQUIRED_VULKAN_NAME = "Vulkan 1.0";

    /** The device extensions SDL3 GPU requires. */
    public static final String[] REQUIRED_EXTENSIONS = {
        "VK_KHR_swapchain",
        "VK_KHR_maintenance1",
    };

    /** The device features SDL3 GPU requires; none can be verified through the Android platform API. */
    public static final String[] REQUIRED_FEATURES = {
        "independentBlend",
        "imageCubeArray",
        "sampleRateShading",
    };

    /**
     * The optional features the port drops, one at a time, when a driver lacks them, in the order it
     * drops them ({@code SdlGpuDeviceFeatures.h}). A device missing one of these is still supported.
     */
    public static final String[] OPTIONAL_FEATURES = {
        "shaderClipDistance",
        "drawIndirectFirstInstance",
        "depthClamp",
        "samplerAnisotropy",
    };

    /** Provisional RAM floor. No verified figure exists; see the hardware audit. */
    public static final long MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024;

    /** Provisional RAM recommendation. */
    public static final long RECOMMENDED_RAM_BYTES = 8L * 1024 * 1024 * 1024;

    /** Storage note: the staged copy roughly doubles the game-data footprint, plus saves. */
    public static final String STORAGE_NOTE =
        "Free space for your game data, plus the one-time staged copy of it "
            + "(about twice the game-data size), plus saves.";

    /** Provisional minimum processor profile; no SoC has been tested. */
    public static final String MIN_SOC_PROVISIONAL =
        "Provisional: any arm64-v8a SoC whose GPU driver advertises Vulkan 1.0 with the mandatory "
            + "extensions and features. No Android SoC has been tested.";

    /** Provisional recommended processor profile. */
    public static final String RECOMMENDED_SOC_PROVISIONAL =
        "Provisional: a 2022-or-newer arm64 flagship SoC with a Vulkan 1.1+ driver "
            + "(for example Adreno 7xx, Arm Immortalis-G715, or Samsung Xclipse 920).";

    /** The GPU families that have Vulkan-capable members; no family is universally compatible. */
    public static final String GPU_FAMILIES =
        "Adreno, Mali, Immortalis, PowerVR and Xclipse families all have Vulkan-capable members, "
            + "but no family is universally compatible: support depends on the generation, the "
            + "vendor driver, and the mandatory features above.";

    /** {@code VK_MAKE_VERSION}'s packing. */
    public static int packVulkanVersion(int major, int minor, int patch) {
        return (major << 22) | (minor << 12) | patch;
    }

    /** The major.minor.patch of a packed Vulkan version, for display; e.g. {@code "1.0.0"}. */
    public static String formatVulkanVersion(int packed) {
        return ((packed >> 22) & 0x3ff) + "." + ((packed >> 12) & 0x3ff) + "." + (packed & 0xfff);
    }
}
