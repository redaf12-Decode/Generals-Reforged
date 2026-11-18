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

import java.util.ArrayList;
import java.util.List;

/**
 * Compares what the platform reported ({@link DeviceCapabilities}) against the application's
 * requirements ({@link AndroidRequirements}) and returns one of four results.
 *
 * <p>The rule is deliberately conservative and is a pure function of its arguments, so it is
 * unit-tested on the host with no Android types:
 *
 * <ul>
 *   <li>{@link Result#UNSUPPORTED} — a mandatory requirement is known to be missing (the API level
 *       is too low, the ABI is not {@code arm64-v8a}, or the platform says there is no Vulkan
 *       hardware or one too old).</li>
 *   <li>{@link Result#UNABLE_TO_VERIFY} — a mandatory fact the platform API <em>should</em> be able
 *       to report (the API level, the ABIs, or whether and how recent Vulkan is) could not be read,
 *       so there is not enough to reach a verdict.</li>
 *   <li>{@link Result#PARTIALLY_COMPATIBLE} — every core fact above is verified, but the mandatory
 *       Vulkan <em>features</em> cannot be read through the Android platform API, so the device is
 *       likely but not proven to work.</li>
 *   <li>{@link Result#COMPATIBLE} — every mandatory requirement, including the Vulkan features, was
 *       positively verified. Kept for completeness of the rule.</li>
 * </ul>
 *
 * <p>The mandatory Vulkan <em>features</em> ({@code independentBlend}, {@code imageCubeArray},
 * {@code sampleRateShading}) are never available from the Android platform API, so a device whose
 * core facts are all good lands in {@link Result#PARTIALLY_COMPATIBLE} at best — it is never marked
 * {@link Result#COMPATIBLE} on the strength of a processor name or Vulkan availability alone. In
 * practice {@link Result#COMPATIBLE} is therefore unreachable from the app today; the branch exists
 * only so the rule is stated correctly.
 */
public final class CompatibilityEvaluator {

    private CompatibilityEvaluator() {
    }

    /** The four results the screen can show. */
    public enum Result {
        COMPATIBLE,
        PARTIALLY_COMPATIBLE,
        UNSUPPORTED,
        UNABLE_TO_VERIFY
    }

    /** One line of the evaluation: what was checked, what was found, and how it came out. */
    public static final class Check {
        public final String name;
        public final String detail;
        public final boolean mandatory;
        /** True when the check was satisfied; false when it failed or could not be verified. */
        public final boolean satisfied;
        /** True when the requirement is known to be missing (a hard failure). */
        public final boolean failed;
        /** True when the value could not be read (neither satisfied nor failed). */
        public final boolean unverified;

        Check(String name, String detail, boolean mandatory, boolean satisfied, boolean failed,
                boolean unverified) {
            this.name = name;
            this.detail = detail;
            this.mandatory = mandatory;
            this.satisfied = satisfied;
            this.failed = failed;
            this.unverified = unverified;
        }
    }

    /** The evaluation: the result and the per-check detail the screen lists. */
    public static final class Report {
        public final Result result;
        public final List<Check> checks;

        Report(Result result, List<Check> checks) {
            this.result = result;
            this.checks = checks;
        }
    }

    public static Report evaluate(DeviceCapabilities device) {
        List<Check> checks = new ArrayList<>();
        boolean anyMandatoryFailed = false;
        // A "core" mandatory fact is one the Android platform API should be able to report: the API
        // level, the ABIs, and whether (and how recent) Vulkan is. If one of these cannot be read,
        // there is not enough required information to reach a verdict - UNABLE_TO_VERIFY. The
        // mandatory Vulkan *features* are a separate case: the API never reports them, so their
        // absence of proof is expected and leaves the device PARTIALLY_COMPATIBLE, not unverifiable.
        boolean coreUnverified = false;

        // --- Android version (mandatory, core) ---
        if (device.sdkInt <= 0) {
            checks.add(unverified("Android version", "The platform did not report its API level.", true));
            coreUnverified = true;
        } else if (device.sdkInt < AndroidRequirements.MIN_SDK_INT) {
            checks.add(failed("Android version",
                    "API " + device.sdkInt + "; " + AndroidRequirements.MIN_ANDROID_NAME + " or newer is required.",
                    true));
        } else {
            checks.add(ok("Android version", "API " + device.sdkInt + ".", true));
        }

        // --- CPU ABI (mandatory, core) ---
        if (device.supportedAbis == null || device.supportedAbis.length == 0) {
            checks.add(unverified("CPU ABI", "The platform did not report its supported ABIs.", true));
            coreUnverified = true;
        } else if (contains(device.supportedAbis, AndroidRequirements.SUPPORTED_ABI)) {
            checks.add(ok("CPU ABI", AndroidRequirements.SUPPORTED_ABI + " supported.", true));
        } else {
            checks.add(failed("CPU ABI",
                    "This device is not " + AndroidRequirements.SUPPORTED_ABI + " (reported: "
                        + join(device.supportedAbis) + ").", true));
        }

        // --- Vulkan availability and API version (mandatory, core) ---
        if (device.vulkanSupported == DeviceCapabilities.FieldState.ABSENT) {
            checks.add(failed("Vulkan",
                    "This device reports no Vulkan hardware. The renderer needs Vulkan 1.0; there is "
                        + "no OpenGL ES fallback.", true));
        } else if (device.vulkanSupported == DeviceCapabilities.FieldState.UNKNOWN) {
            checks.add(unverified("Vulkan",
                    "The platform did not report whether it has Vulkan.", true));
            coreUnverified = true;
        } else if (device.vulkanHardwareVersion < AndroidRequirements.REQUIRED_VULKAN_API_VERSION) {
            // A device can report Vulkan hardware and still expose no usable version; treat a
            // version below the floor as a failure, and an unreadable one (0) as unverified.
            if (device.vulkanHardwareVersion == 0) {
                checks.add(unverified("Vulkan version",
                        "Vulkan hardware is present but the platform reported no version.", true));
                coreUnverified = true;
            } else {
                checks.add(failed("Vulkan version",
                        "The device advertises Vulkan " + AndroidRequirements.formatVulkanVersion(
                            device.vulkanHardwareVersion) + "; " + AndroidRequirements.REQUIRED_VULKAN_NAME
                            + " or newer is required.", true));
            }
        } else {
            checks.add(ok("Vulkan version",
                    "The device advertises Vulkan "
                        + AndroidRequirements.formatVulkanVersion(device.vulkanHardwareVersion) + ".", true));
        }

        // --- Mandatory Vulkan features (mandatory, never verifiable here) ---
        checks.add(unverified("Vulkan features",
                "Required: " + join(AndroidRequirements.REQUIRED_FEATURES)
                    + ". The Android platform API does not expose individual Vulkan device features, "
                    + "so they cannot be confirmed from the app; the engine checks them when it creates "
                    + "the device and fails loudly if one is missing.", true));

        // --- Optional Vulkan features (not mandatory) ---
        checks.add(unverified("Optional Vulkan features",
                "These are dropped if absent, so they never make a device unsupported: "
                    + join(AndroidRequirements.OPTIONAL_FEATURES) + ".", false));

        // --- RAM (not mandatory; provisional) ---
        if (device.totalRamBytes <= 0) {
            checks.add(unverified("Memory",
                    "The platform did not report total RAM. A provisional floor of "
                        + gigabytes(AndroidRequirements.MIN_RAM_BYTES) + " GB is recommended.", false));
        } else if (device.totalRamBytes < AndroidRequirements.MIN_RAM_BYTES) {
            checks.add(unverified("Memory",
                    "About " + gigabytes(device.totalRamBytes) + " GB total; a provisional floor of "
                        + gigabytes(AndroidRequirements.MIN_RAM_BYTES)
                        + " GB is recommended. This is a recommendation, not a verified requirement.",
                    false));
        } else {
            checks.add(ok("Memory",
                    "About " + gigabytes(device.totalRamBytes) + " GB total.", false));
        }

        // --- Emulator (informational; never decides compatibility) ---
        if (device.emulator) {
            checks.add(unverified("Emulator",
                    "This looks like an emulator. Compatibility on emulators is not the same as on "
                        + "physical devices and is not claimed.", false));
        }

        // --- Result ---
        for (Check check : checks) {
            if (check.mandatory && check.failed) {
                anyMandatoryFailed = true;
            }
        }

        Result result;
        if (anyMandatoryFailed) {
            result = Result.UNSUPPORTED;
        } else if (coreUnverified) {
            // A required core fact could not be read: there is not enough to reach a verdict.
            result = Result.UNABLE_TO_VERIFY;
        } else {
            // Every core requirement is verified; the mandatory Vulkan features cannot be, so the
            // device is likely but not proven to work. This is why COMPATIBLE is not reachable from
            // the app alone - see the class comment.
            result = Result.PARTIALLY_COMPATIBLE;
        }
        return new Report(result, checks);
    }

    private static Check ok(String name, String detail, boolean mandatory) {
        return new Check(name, detail, mandatory, true, false, false);
    }

    private static Check failed(String name, String detail, boolean mandatory) {
        return new Check(name, detail, mandatory, false, true, false);
    }

    private static Check unverified(String name, String detail, boolean mandatory) {
        return new Check(name, detail, mandatory, false, false, true);
    }

    private static boolean contains(String[] values, String wanted) {
        for (String value : values) {
            if (wanted.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
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

    private static long gigabytes(long bytes) {
        return Math.round((double) bytes / (1024 * 1024 * 1024));
    }
}
