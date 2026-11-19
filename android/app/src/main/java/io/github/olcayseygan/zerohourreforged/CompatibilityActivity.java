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

import android.os.Bundle;

import androidx.activity.ComponentActivity;

import io.github.olcayseygan.zerohourreforged.ui.CompatibilityUi;

/**
 * The System Requirements &amp; Device Compatibility screen, opened from the setup screen.
 *
 * <p>It shows three groups:
 *
 * <ul>
 *   <li><b>Application requirements</b> - the Android version, ABI, Vulkan floor and hardware
 *       profiles, all read from {@link AndroidRequirements} so the screen and the documentation
 *       cannot disagree.</li>
 *   <li><b>Your device</b> - what {@link DeviceCapabilitiesReader} could read: manufacturer, model,
 *       Android version, ABI, RAM, and the Vulkan facts the Android platform API exposes. The
 *       Vulkan physical-device name, vendor/device IDs and per-extension/per-feature support are
 *       <em>not</em> available from the platform API, so they are shown as unverified rather than
 *       invented.</li>
 *   <li><b>Compatibility</b> - the result of {@link CompatibilityEvaluator}, with the per-check
 *       detail.</li>
 * </ul>
 *
 * <p>All the logic is in those three pure classes, unit-tested on the host. This Activity reads the
 * device and evaluates the verdict exactly as before; only the rendering is Compose, through
 * {@link CompatibilityUi}.
 */
public class CompatibilityActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceCapabilities device = DeviceCapabilitiesReader.read(this);
        CompatibilityEvaluator.Report report = CompatibilityEvaluator.evaluate(device);
        CompatibilityUi.installCompatibilityContent(this, device, report);
    }
}
