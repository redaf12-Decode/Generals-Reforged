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
 * What the platform reported about this device, in the plain form {@link CompatibilityEvaluator}
 * reads.
 *
 * <p>Every field is nullable so "the platform did not say" is distinct from "the platform said no":
 * a missing value is {@link FieldState#UNKNOWN} and never counts as a pass. This is the reason the
 * evaluator can answer {@link CompatibilityEvaluator.Result#UNABLE_TO_VERIFY} rather than guessing.
 */
public final class DeviceCapabilities {

    /** How a single field stands: reported and usable, reported and unusable, or not reported. */
    public enum FieldState {
        /** The platform reported a value this build can use. */
        PRESENT,
        /** The platform reported, and the value is not usable (too old, wrong ABI, absent feature). */
        ABSENT,
        /** The platform did not report the value at all. */
        UNKNOWN
    }

    // --- the device (informational) ---
    public String manufacturer;
    public String model;
    public String androidRelease;
    public int sdkInt = -1;
    public String[] supportedAbis = new String[0];

    // --- memory ---
    public long totalRamBytes = -1;

    // --- Vulkan, as far as the Android platform API exposes it ---
    /** {@code PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL} is present. */
    public FieldState vulkanSupported = FieldState.UNKNOWN;
    /**
     * {@code FEATURE_VULKAN_HARDWARE_VERSION}'s value: the highest
     * {@code VkPhysicalDeviceProperties::apiVersion} the device reports, packed as a Vulkan version
     * ({@code major << 22 | minor << 12 | patch}); 0 when the platform did not report one.
     */
    public int vulkanHardwareVersion = 0;
    /**
     * {@code FEATURE_VULKAN_HARDWARE_LEVEL}'s value: the optional-feature level the device claims.
     * Shown for information; it is not proof of any specific mandatory feature.
     */
    public int vulkanHardwareLevel = -1;

    /** The device is running on an emulator ({@code Build.FINGERPRINT} looks emulated). */
    public boolean emulator;

    public DeviceCapabilities() {
    }
}
