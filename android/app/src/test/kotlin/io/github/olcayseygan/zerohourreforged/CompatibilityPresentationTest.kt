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

package io.github.olcayseygan.zerohourreforged

import io.github.olcayseygan.zerohourreforged.ui.components.StatusTone
import io.github.olcayseygan.zerohourreforged.ui.screens.compatibility.CompatibilityPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Host tests for [CompatibilityPresentation]: how a verdict and a check are drawn.
 *
 * <p>The rule worth pinning is the precedence in [CompatibilityPresentation.checkTone]. The
 * evaluator can report a check as failed, unverified or satisfied, and a hard failure must never be
 * drawn as merely "unverified" - that would tell the player a missing requirement might be fine.
 */
class CompatibilityPresentationTest {

    private fun check(satisfied: Boolean, failed: Boolean, unverified: Boolean) =
        CompatibilityEvaluator.Check("a check", "a detail", true, satisfied, failed, unverified)

    @Test
    fun failedWinsOverUnverifiedAndSatisfied() {
        assertEquals(
            StatusTone.DANGER,
            CompatibilityPresentation.checkTone(check(satisfied = false, failed = true, unverified = true)),
        )
        assertEquals(
            StatusTone.DANGER,
            CompatibilityPresentation.checkTone(check(satisfied = false, failed = true, unverified = false)),
        )
    }

    @Test
    fun unverifiedIsNotSatisfied() {
        assertEquals(
            StatusTone.WARNING,
            CompatibilityPresentation.checkTone(check(satisfied = false, failed = false, unverified = true)),
        )
    }

    @Test
    fun satisfiedCheckReadsAsSatisfied() {
        assertEquals(
            StatusTone.SUCCESS,
            CompatibilityPresentation.checkTone(check(satisfied = true, failed = false, unverified = false)),
        )
    }

    @Test
    fun everyVerdictHasADistinctTone() {
        assertEquals(
            StatusTone.SUCCESS,
            CompatibilityPresentation.resultTone(CompatibilityEvaluator.Result.COMPATIBLE),
        )
        assertEquals(
            StatusTone.DANGER,
            CompatibilityPresentation.resultTone(CompatibilityEvaluator.Result.UNSUPPORTED),
        )
        assertEquals(
            StatusTone.WARNING,
            CompatibilityPresentation.resultTone(CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE),
        )
        assertEquals(
            StatusTone.NEUTRAL,
            CompatibilityPresentation.resultTone(CompatibilityEvaluator.Result.UNABLE_TO_VERIFY),
        )
    }

    @Test
    fun missingDeviceValuesAreSpelledOutRatherThanBlank() {
        assertEquals("unknown", CompatibilityPresentation.orUnknown(null))
        assertEquals("unknown", CompatibilityPresentation.orUnknown(""))
        assertEquals("Pixel", CompatibilityPresentation.orUnknown("Pixel"))
    }

    @Test
    fun checkEvidenceUsesTheScreensOwnVocabulary() {
        assertEquals(
            CompatibilityPresentation.Evidence.VERIFIED,
            CompatibilityPresentation.checkEvidence(check(satisfied = true, failed = false, unverified = false)),
        )
        assertEquals(
            CompatibilityPresentation.Evidence.UNSUPPORTED,
            CompatibilityPresentation.checkEvidence(check(satisfied = false, failed = true, unverified = false)),
        )
        assertEquals(
            CompatibilityPresentation.Evidence.UNABLE_TO_VERIFY,
            CompatibilityPresentation.checkEvidence(check(satisfied = false, failed = false, unverified = true)),
        )
    }

    /**
     * The three ways of not knowing stay distinct. This is the property the whole screen exists to
     * keep: an API that cannot answer is not the same as a device that would not, and neither is the
     * same as a requirement that is known to be missing.
     */
    @Test
    fun theThreeUnknownsAreDistinctValues() {
        val unknowns = listOf(
            CompatibilityPresentation.Evidence.UNABLE_TO_VERIFY,
            CompatibilityPresentation.Evidence.NOT_REPORTED,
            CompatibilityPresentation.Evidence.NOT_AVAILABLE,
        )
        assertEquals(3, unknowns.toSet().size)
        assertFalse(unknowns.contains(CompatibilityPresentation.Evidence.VERIFIED))
        assertFalse(unknowns.contains(CompatibilityPresentation.Evidence.UNSUPPORTED))
    }

    @Test
    fun onlyVerifiedAndUnsupportedEarnAnAssertiveTone() {
        // A missing fact must not be drawn in the same colour as a passing one.
        assertEquals(StatusTone.SUCCESS, CompatibilityPresentation.evidenceTone(CompatibilityPresentation.Evidence.VERIFIED))
        assertEquals(StatusTone.DANGER, CompatibilityPresentation.evidenceTone(CompatibilityPresentation.Evidence.UNSUPPORTED))
        assertEquals(StatusTone.WARNING, CompatibilityPresentation.evidenceTone(CompatibilityPresentation.Evidence.UNABLE_TO_VERIFY))
        assertEquals(StatusTone.NEUTRAL, CompatibilityPresentation.evidenceTone(CompatibilityPresentation.Evidence.NOT_REPORTED))
        assertEquals(StatusTone.NEUTRAL, CompatibilityPresentation.evidenceTone(CompatibilityPresentation.Evidence.NOT_AVAILABLE))
    }

    @Test
    fun vulkanDistinguishesAbsentFromUnreported() {
        val absent = DeviceCapabilities().apply {
            vulkanSupported = DeviceCapabilities.FieldState.ABSENT
        }
        val unreported = DeviceCapabilities().apply {
            vulkanSupported = DeviceCapabilities.FieldState.UNKNOWN
        }
        val presentNoVersion = DeviceCapabilities().apply {
            vulkanSupported = DeviceCapabilities.FieldState.PRESENT
        }
        val presentWithVersion = DeviceCapabilities().apply {
            vulkanSupported = DeviceCapabilities.FieldState.PRESENT
            vulkanHardwareVersion = AndroidRequirements.packVulkanVersion(1, 0, 0)
        }
        assertEquals(CompatibilityPresentation.Evidence.NOT_AVAILABLE, CompatibilityPresentation.vulkanEvidence(absent))
        assertEquals(CompatibilityPresentation.Evidence.NOT_REPORTED, CompatibilityPresentation.vulkanEvidence(unreported))
        assertEquals(CompatibilityPresentation.Evidence.NOT_REPORTED, CompatibilityPresentation.vulkanEvidence(presentNoVersion))
        assertEquals(CompatibilityPresentation.Evidence.VERIFIED, CompatibilityPresentation.vulkanEvidence(presentWithVersion))
    }

    /** A refused RAM query is "not reported", never 0 GB. */
    @Test
    fun ramIsNotReportedWhenTheQueryWasRefused() {
        assertEquals(
            CompatibilityPresentation.Evidence.NOT_REPORTED,
            CompatibilityPresentation.ramEvidence(DeviceCapabilities()),
        )
        val device = DeviceCapabilities().apply { totalRamBytes = 6L * 1024 * 1024 * 1024 }
        assertEquals(CompatibilityPresentation.Evidence.VERIFIED, CompatibilityPresentation.ramEvidence(device))
        assertEquals("6", CompatibilityPresentation.gb(device.totalRamBytes))
    }
}
