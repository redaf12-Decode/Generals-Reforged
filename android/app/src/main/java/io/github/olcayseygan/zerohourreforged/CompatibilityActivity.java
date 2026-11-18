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

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * The System Requirements &amp; Device Compatibility screen, opened from the setup screen.
 *
 * <p>It shows three groups, built with the same plain widgets the setup screen uses (no UI
 * framework is added):
 *
 * <ul>
 *   <li><b>Application requirements</b> — the Android version, ABI, Vulkan floor and hardware
 *       profiles, all read from {@link AndroidRequirements} so the screen and the documentation
 *       cannot disagree.</li>
 *   <li><b>Your device</b> — what {@link DeviceCapabilitiesReader} could read: manufacturer, model,
 *       Android version, ABI, RAM, and the Vulkan facts the Android platform API exposes. The
 *       Vulkan physical-device name, vendor/device IDs and per-extension/per-feature support are
 *       <em>not</em> available from the platform API, so they are shown as unverified rather than
 *       invented.</li>
 *   <li><b>Compatibility</b> — the result of {@link CompatibilityEvaluator}, with the per-check
 *       detail.</li>
 * </ul>
 *
 * <p>All the logic is in those three pure classes, unit-tested on the host; this Activity only
 * renders their answers.
 */
public class CompatibilityActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        addHeading(root, getString(R.string.compat_title), pad);

        DeviceCapabilities device = DeviceCapabilitiesReader.read(this);
        CompatibilityEvaluator.Report report = CompatibilityEvaluator.evaluate(device);

        addSubheading(root, getString(R.string.compat_group_result), pad);
        addResult(root, report, pad);

        addSubheading(root, getString(R.string.compat_group_app), pad);
        addRequirements(root, pad);

        addSubheading(root, getString(R.string.compat_group_device), pad);
        addDevice(root, device, pad);

        addParagraph(root, getString(R.string.compat_performance_note), pad);
        return scroll;
    }

    private void addResult(LinearLayout root, CompatibilityEvaluator.Report report, int pad) {
        int resId;
        switch (report.result) {
            case COMPATIBLE:
                resId = R.string.compat_result_compatible;
                break;
            case PARTIALLY_COMPATIBLE:
                resId = R.string.compat_result_partial;
                break;
            case UNSUPPORTED:
                resId = R.string.compat_result_unsupported;
                break;
            default:
                resId = R.string.compat_result_unable;
                break;
        }
        TextView verdict = new TextView(this);
        verdict.setText(getString(resId));
        verdict.setTypeface(Typeface.DEFAULT_BOLD);
        verdict.setPadding(0, 0, 0, pad / 2);
        root.addView(verdict);

        for (CompatibilityEvaluator.Check check : report.checks) {
            String mark = check.failed ? "✗" : check.satisfied ? "✓" : "?";
            addBullet(root, mark + " " + check.name + " — " + check.detail, pad);
        }
    }

    private void addRequirements(LinearLayout root, int pad) {
        addBullet(root, "Android: " + AndroidRequirements.MIN_ANDROID_NAME + " or newer.", pad);
        addBullet(root, "CPU: " + AndroidRequirements.SUPPORTED_ABI + " (64-bit ARM) only.", pad);
        addBullet(root, "Graphics: " + AndroidRequirements.REQUIRED_VULKAN_NAME
                + " with " + join(AndroidRequirements.REQUIRED_EXTENSIONS)
                + " and the features " + join(AndroidRequirements.REQUIRED_FEATURES)
                + ". No OpenGL ES fallback.", pad);
        addBullet(root, "Optional Vulkan features (dropped if absent): "
                + join(AndroidRequirements.OPTIONAL_FEATURES) + ".", pad);
        addBullet(root, "GPUs: " + AndroidRequirements.GPU_FAMILIES, pad);
        addBullet(root, "RAM: " + gb(AndroidRequirements.MIN_RAM_BYTES)
                + " GB recommended as a floor, " + gb(AndroidRequirements.RECOMMENDED_RAM_BYTES)
                + " GB recommended (provisional; no verified figure).", pad);
        addBullet(root, "Storage: " + AndroidRequirements.STORAGE_NOTE, pad);
        addBullet(root, "Minimum processor: " + AndroidRequirements.MIN_SOC_PROVISIONAL, pad);
        addBullet(root, "Recommended processor: " + AndroidRequirements.RECOMMENDED_SOC_PROVISIONAL, pad);
    }

    private void addDevice(LinearLayout root, DeviceCapabilities device, int pad) {
        addBullet(root, "Manufacturer: " + orUnknown(device.manufacturer), pad);
        addBullet(root, "Model: " + orUnknown(device.model), pad);
        addBullet(root, "Android version: "
                + (device.androidRelease == null || device.androidRelease.isEmpty() ? "unknown" : device.androidRelease)
                + (device.sdkInt > 0 ? " (API " + device.sdkInt + ")" : ""), pad);
        addBullet(root, "CPU ABIs: "
                + (device.supportedAbis != null && device.supportedAbis.length > 0
                    ? join(device.supportedAbis) : "unknown"), pad);
        addBullet(root, "Total RAM: "
                + (device.totalRamBytes > 0 ? gb(device.totalRamBytes) + " GB" : "not reported"), pad);

        String vulkan;
        switch (device.vulkanSupported) {
            case PRESENT:
                vulkan = "Vulkan hardware reported.";
                if (device.vulkanHardwareVersion > 0) {
                    vulkan += " Advertised API version: "
                            + AndroidRequirements.formatVulkanVersion(device.vulkanHardwareVersion) + ".";
                } else {
                    vulkan += " Advertised API version: not exposed by the platform API.";
                }
                if (device.vulkanHardwareLevel >= 0) {
                    vulkan += " Hardware level: " + device.vulkanHardwareLevel + ".";
                }
                break;
            case ABSENT:
                vulkan = "No Vulkan hardware reported.";
                break;
            default:
                vulkan = "Not reported.";
                break;
        }
        addBullet(root, vulkan, pad);
        addBullet(root, "Vulkan physical-device name, vendor/device IDs and per-extension/per-feature "
                + "support: unable to verify — the Android platform API does not expose them; the "
                + "engine checks the mandatory features when it creates the device.", pad);
    }

    // --- small view helpers, matching the setup screen's plain-widget style ---

    private void addHeading(LinearLayout root, String text, int pad) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, pad / 2);
        root.addView(view);
    }

    private void addSubheading(LinearLayout root, String text, int pad) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, pad, 0, pad / 4);
        root.addView(view);
    }

    private void addParagraph(LinearLayout root, String text, int pad) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, 0, 0, pad / 2);
        root.addView(view);
    }

    private void addBullet(LinearLayout root, String text, int pad) {
        TextView view = new TextView(this);
        view.setText("• " + text);
        view.setPadding(0, 0, 0, pad / 4);
        root.addView(view);
    }

    private static String orUnknown(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.0f", (double) bytes / (1024 * 1024 * 1024));
    }

    private static String join(String[] values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                text.append(", ");
            }
            text.append(values[i]);
        }
        return text.toString();
    }
}
