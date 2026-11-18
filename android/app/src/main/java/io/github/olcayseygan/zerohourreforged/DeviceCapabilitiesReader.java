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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Reads what this device reports into a {@link DeviceCapabilities}, using only the public Android
 * platform API and standard Java.
 *
 * <p>Everything is wrapped so a value the platform refuses to give is recorded as
 * {@link DeviceCapabilities.FieldState#UNKNOWN} rather than thrown: the screen must be able to say
 * "unable to verify" instead of crashing.
 *
 * <p>What the Android platform API does <em>not</em> expose, and this reader therefore cannot fill:
 * the Vulkan physical-device name, the vendor and device IDs, and the per-extension/per-feature
 * support. Those need the device's own Vulkan driver, which the app does not load to read
 * capabilities (the engine loads it only when it starts). The screen marks them unverified.
 */
final class DeviceCapabilitiesReader {

    private static final String TAG = "ZH.DeviceCapabilities";

    private DeviceCapabilitiesReader() {
    }

    static DeviceCapabilities read(Context context) {
        DeviceCapabilities device = new DeviceCapabilities();
        device.manufacturer = safe(Build.MANUFACTURER);
        device.model = safe(Build.MODEL);
        device.androidRelease = safe(Build.VERSION.RELEASE);
        device.sdkInt = Build.VERSION.SDK_INT;
        device.supportedAbis = Build.SUPPORTED_ABIS != null ? Build.SUPPORTED_ABIS : new String[0];
        device.emulator = looksLikeEmulator();

        device.totalRamBytes = totalRam(context);
        readVulkan(context, device);
        return device;
    }

    private static void readVulkan(Context context, DeviceCapabilities device) {
        try {
            PackageManager packages = context.getPackageManager();
            FeatureInfo[] features = packages.getSystemAvailableFeatures();
            if (features == null) {
                device.vulkanSupported = DeviceCapabilities.FieldState.UNKNOWN;
                return;
            }
            boolean sawLevel = false;
            for (FeatureInfo feature : features) {
                if (feature == null || feature.name == null) {
                    continue;
                }
                if (PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL.equals(feature.name)) {
                    // Presence of the level feature is how the platform says "there is Vulkan". Its
                    // version field is the hardware level (the optional-feature limits), not the API
                    // version, so it is shown as the level.
                    sawLevel = true;
                    device.vulkanHardwareLevel = feature.version;
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                    // Documented as "the highest VkPhysicalDeviceProperties::apiVersion supported by
                    // the physical devices", encoded exactly like a Vulkan version (major in bits
                    // 31-22, minor in 21-12, patch in 11-0) - the same packing AndroidRequirements
                    // uses, so it can be compared with the requirement directly.
                    device.vulkanHardwareVersion = feature.version;
                }
            }
            device.vulkanSupported = sawLevel
                ? DeviceCapabilities.FieldState.PRESENT : DeviceCapabilities.FieldState.ABSENT;
        } catch (RuntimeException e) {
            Log.w(TAG, "the platform refused a Vulkan feature query", e);
            device.vulkanSupported = DeviceCapabilities.FieldState.UNKNOWN;
        }
    }

    private static long totalRam(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return -1;
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return info.totalMem;
        } catch (RuntimeException e) {
            Log.w(TAG, "the platform refused a memory query", e);
            return -1;
        }
    }

    /** A conservative emulator check, so the screen can say so without claiming compatibility. */
    private static boolean looksLikeEmulator() {
        String fingerprint = safe(Build.FINGERPRINT).toLowerCase(java.util.Locale.ROOT);
        String model = safe(Build.MODEL).toLowerCase(java.util.Locale.ROOT);
        String hardware = safe(Build.HARDWARE).toLowerCase(java.util.Locale.ROOT);
        return fingerprint.contains("generic") || fingerprint.contains("emulator")
            || model.contains("sdk") || model.contains("emulator")
            || hardware.contains("goldfish") || hardware.contains("ranchu");
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
