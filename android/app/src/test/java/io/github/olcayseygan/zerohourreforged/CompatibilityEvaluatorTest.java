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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Host tests for {@link CompatibilityEvaluator}: the rule that decides whether a device is
 * compatible, with no Android types.
 *
 * <p>The important cases are the conservative ones: a device is never called {@code COMPATIBLE}
 * merely because it has Vulkan or a modern API level, and a device whose mandatory Vulkan features
 * cannot be read is {@code PARTIALLY_COMPATIBLE} at best.
 */
public class CompatibilityEvaluatorTest {

    /** A device that meets every verifiable requirement, with a Vulkan version above the floor. */
    private static DeviceCapabilities capableDevice() {
        DeviceCapabilities device = new DeviceCapabilities();
        device.sdkInt = 31;
        device.androidRelease = "12";
        device.supportedAbis = new String[] { "arm64-v8a", "armeabi-v7a" };
        device.vulkanSupported = DeviceCapabilities.FieldState.PRESENT;
        device.vulkanHardwareVersion = AndroidRequirements.packVulkanVersion(1, 1, 0);
        device.totalRamBytes = 8L * 1024 * 1024 * 1024;
        return device;
    }

    @Test
    public void aCapableDeviceIsPartiallyCompatibleNotCompatible() {
        // Every mandatory item is verified except the Vulkan features, which the platform API never
        // reports. The device must not be called fully compatible.
        CompatibilityEvaluator.Report report =
                CompatibilityEvaluator.evaluate(capableDevice());
        assertEquals(CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE, report.result);
    }

    @Test
    public void anOldAndroidVersionIsUnsupported() {
        DeviceCapabilities device = capableDevice();
        device.sdkInt = 27;
        assertEquals(CompatibilityEvaluator.Result.UNSUPPORTED,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aNonArm64DeviceIsUnsupported() {
        DeviceCapabilities device = capableDevice();
        device.supportedAbis = new String[] { "x86_64" };
        assertEquals(CompatibilityEvaluator.Result.UNSUPPORTED,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aDeviceWithNoVulkanIsUnsupported() {
        DeviceCapabilities device = capableDevice();
        device.vulkanSupported = DeviceCapabilities.FieldState.ABSENT;
        assertEquals(CompatibilityEvaluator.Result.UNSUPPORTED,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aVulkanVersionBelowTheFloorIsUnsupported() {
        DeviceCapabilities device = capableDevice();
        // Some early device that reports Vulkan but a version below 1.0 is not realistic; the point
        // is that a reported version below the floor fails rather than passes.
        device.vulkanHardwareVersion = AndroidRequirements.packVulkanVersion(0, 9, 0);
        assertEquals(CompatibilityEvaluator.Result.UNSUPPORTED,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aDeviceThatDoesNotReportVulkanIsUnableToVerify() {
        DeviceCapabilities device = capableDevice();
        device.vulkanSupported = DeviceCapabilities.FieldState.UNKNOWN;
        assertEquals(CompatibilityEvaluator.Result.UNABLE_TO_VERIFY,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aDeviceThatReportsNothingMandatoryIsUnableToVerify() {
        DeviceCapabilities device = new DeviceCapabilities();
        // sdkInt stays -1, ABIs empty, Vulkan unknown: nothing mandatory can be verified.
        assertEquals(CompatibilityEvaluator.Result.UNABLE_TO_VERIFY,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void vulkanHardwareWithoutAReportedVersionIsUnableToVerify() {
        DeviceCapabilities device = capableDevice();
        device.vulkanHardwareVersion = 0;   // reported hardware, no version
        // Not unsupported (nothing is known to be missing), but the version - a core fact - could not
        // be read, so no verdict can be reached.
        assertEquals(CompatibilityEvaluator.Result.UNABLE_TO_VERIFY,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void aLowRamDeviceIsStillNotUnsupported() {
        DeviceCapabilities device = capableDevice();
        device.totalRamBytes = 2L * 1024 * 1024 * 1024;
        // RAM is a recommendation, not a mandatory requirement.
        assertEquals(CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE,
                CompatibilityEvaluator.evaluate(device).result);
    }

    @Test
    public void everyMandatoryFailureIsReportedAsAFailedCheck() {
        DeviceCapabilities device = capableDevice();
        device.sdkInt = 27;
        CompatibilityEvaluator.Report report = CompatibilityEvaluator.evaluate(device);
        boolean sawFailed = false;
        for (CompatibilityEvaluator.Check check : report.checks) {
            if (check.mandatory && check.failed) {
                sawFailed = true;
            }
        }
        assertTrue(sawFailed);
    }
}
