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


package io.github.olcayseygan.zerohourreforged.ui.screens.compatibility

import io.github.olcayseygan.zerohourreforged.CompatibilityEvaluator
import io.github.olcayseygan.zerohourreforged.DeviceCapabilities
import io.github.olcayseygan.zerohourreforged.ui.components.StatusTone
import java.util.Locale

/**
 * The presentation rules of the compatibility screen, kept out of the composable so they can be
 * unit-tested on the host with plain Java objects - the same approach the existing
 * CompatibilityEvaluator and DeviceCapabilities already use.
 *
 * <p>Nothing here evaluates compatibility: [CompatibilityEvaluator.evaluate] remains the only source
 * of the verdict, and [DeviceCapabilities] remains the only source of what the device reported. This
 * object only decides how a verdict, a check or a device field is displayed, and which shared
 * [StatusTone] carries it.
 */
internal object CompatibilityPresentation {

    /**
     * How much is actually known about one fact, in the exact vocabulary the screen states.
     *
     * <p>Five words, and the difference between the last three is the point of this screen. A fact
     * the platform reported and that passes is [VERIFIED]; one it reported and that fails a
     * mandatory requirement is [UNSUPPORTED]. The remaining three all mean "we do not know", and
     * they are not interchangeable:
     *
     * <ul>
     *   <li>[NOT_REPORTED] - the platform could have answered and returned nothing, so this may
     *       differ on the next read or on another device.</li>
     *   <li>[NOT_AVAILABLE] - the public Android API does not expose this fact at all, on any
     *       device. No amount of retrying will produce it; only the engine, which loads the Vulkan
     *       driver, can.</li>
     *   <li>[UNABLE_TO_VERIFY] - a required fact is missing, so no verdict can be reached. This is
     *       the only one of the three that, on its own, can withhold the overall result.</li>
     * </ul>
     *
     * <p>Collapsing these into "warning" would tell a player their Vulkan features might be fine
     * when in fact no build of this app can check them - the distinction the hardware audit is
     * careful to keep.
     */
    enum class Evidence {
        /** The platform reported the fact and it satisfies the requirement. */
        VERIFIED,

        /** The platform reported the fact and it fails a mandatory requirement. */
        UNSUPPORTED,

        /** A required fact is missing, so no verdict can be reached. */
        UNABLE_TO_VERIFY,

        /** The platform could have reported this fact and did not. */
        NOT_REPORTED,

        /** The public Android API does not expose this fact on any device. */
        NOT_AVAILABLE,
    }

    /** The tone that carries an [Evidence] value, so its glyph and fill match its meaning. */
    fun evidenceTone(evidence: Evidence): StatusTone = when (evidence) {
        Evidence.VERIFIED -> StatusTone.SUCCESS
        Evidence.UNSUPPORTED -> StatusTone.DANGER
        Evidence.UNABLE_TO_VERIFY -> StatusTone.WARNING
        Evidence.NOT_REPORTED -> StatusTone.NEUTRAL
        Evidence.NOT_AVAILABLE -> StatusTone.NEUTRAL
    }

    /** The verdict as a tone, so the banner and the individual marks agree on the state. */
    fun resultTone(result: CompatibilityEvaluator.Result): StatusTone = when (result) {
        CompatibilityEvaluator.Result.COMPATIBLE -> StatusTone.SUCCESS
        CompatibilityEvaluator.Result.UNSUPPORTED -> StatusTone.DANGER
        CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE -> StatusTone.WARNING
        CompatibilityEvaluator.Result.UNABLE_TO_VERIFY -> StatusTone.NEUTRAL
    }

    /**
     * A single check's evidence, read only from the three flags the evaluator already set.
     *
     * <p>Order matters: a hard failure wins over an unverifiable one, so a missing requirement is
     * never softened into "could not check". The evaluator guarantees at most one flag is true, and
     * this ordering keeps that true if it ever stops guaranteeing it.
     */
    fun checkEvidence(check: CompatibilityEvaluator.Check): Evidence = when {
        check.failed -> Evidence.UNSUPPORTED
        check.unverified -> Evidence.UNABLE_TO_VERIFY
        check.satisfied -> Evidence.VERIFIED
        else -> Evidence.NOT_REPORTED
    }

    /** A check's tone, derived from its evidence so the two can never disagree. */
    fun checkTone(check: CompatibilityEvaluator.Check): StatusTone = evidenceTone(checkEvidence(check))

    /**
     * Whether the platform reported a usable total RAM figure.
     *
     * <p>[DeviceCapabilities.totalRamBytes] is -1 when the platform refused the query, so a
     * non-positive value means "not reported" and must not be drawn as 0 GB.
     */
    fun ramEvidence(device: DeviceCapabilities): Evidence =
        if (device.totalRamBytes > 0) Evidence.VERIFIED else Evidence.NOT_REPORTED

    /**
     * The evidence for the Vulkan facts the platform exposes.
     *
     * <p>Three distinct answers, and they are not interchangeable: no Vulkan at all is a reported
     * and final "no" ([Evidence.NOT_AVAILABLE]); a platform that would not answer is
     * [Evidence.NOT_REPORTED]; Vulkan present with no version is Vulkan present, version
     * [Evidence.NOT_REPORTED]. Whether the device's Vulkan is *usable* is the evaluator's question,
     * not this one.
     */
    fun vulkanEvidence(device: DeviceCapabilities): Evidence = when (device.vulkanSupported) {
        DeviceCapabilities.FieldState.PRESENT ->
            if (device.vulkanHardwareVersion > 0) Evidence.VERIFIED else Evidence.NOT_REPORTED
        DeviceCapabilities.FieldState.ABSENT -> Evidence.NOT_AVAILABLE
        DeviceCapabilities.FieldState.UNKNOWN -> Evidence.NOT_REPORTED
    }

    /** A text field's evidence: [Evidence.NOT_REPORTED] when the platform left it empty. */
    fun textEvidence(value: String?): Evidence =
        if (value.isNullOrEmpty()) Evidence.NOT_REPORTED else Evidence.VERIFIED

    /** The value, or null when it is absent, so the caller can choose how to state the absence. */
    fun orNull(value: String?): String? = value?.takeIf { it.isNotEmpty() }

    /** "unknown" rather than an empty value, so a missing field reads as missing. */
    fun orUnknown(value: String?): String = orNull(value) ?: "unknown"

    /** Whole gigabytes, matching the existing screen's formatting. */
    fun gb(bytes: Long): String = String.format(Locale.US, "%.0f", bytes.toDouble() / (1024 * 1024 * 1024))

    fun join(values: Array<String>): String = values.joinToString(", ")
}
