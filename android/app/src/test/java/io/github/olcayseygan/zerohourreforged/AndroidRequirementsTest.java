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

import org.junit.Test;

/**
 * Host tests for {@link AndroidRequirements}: the requirements the screen and the documentation
 * share must be the values the build is written against.
 */
public class AndroidRequirementsTest {

    @Test
    public void theMinimumSdkMatchesTheBuildGradle() {
        // android/app/build.gradle: minSdk 28.
        assertEquals(28, AndroidRequirements.MIN_SDK_INT);
    }

    @Test
    public void theSupportedAbiIsArm64() {
        assertEquals("arm64-v8a", AndroidRequirements.SUPPORTED_ABI);
    }

    @Test
    public void theVulkanFloorIsVersion1_0() {
        assertEquals(AndroidRequirements.packVulkanVersion(1, 0, 0),
                AndroidRequirements.REQUIRED_VULKAN_API_VERSION);
        assertEquals("1.0.0",
                AndroidRequirements.formatVulkanVersion(AndroidRequirements.REQUIRED_VULKAN_API_VERSION));
    }

    @Test
    public void theVulkanVersionPackingMatchesVkMakeVersion() {
        // VK_MAKE_VERSION(1, 1, 0) = (1 << 22) | (1 << 12) = 0x401000, the value the manifest declares.
        assertEquals(0x401000, AndroidRequirements.packVulkanVersion(1, 1, 0));
        assertEquals("1.1.0", AndroidRequirements.formatVulkanVersion(0x401000));
    }

    @Test
    public void theMandatorySetsAreNotEmpty() {
        assertEquals(2, AndroidRequirements.REQUIRED_EXTENSIONS.length);
        assertEquals(3, AndroidRequirements.REQUIRED_FEATURES.length);
        assertEquals(4, AndroidRequirements.OPTIONAL_FEATURES.length);
    }
}
