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

import io.github.olcayseygan.zerohourreforged.ui.components.CheckState
import io.github.olcayseygan.zerohourreforged.ui.screens.compatibility.CompatibilityPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host tests for [CompatibilityPresentation]: how a verdict and a check are drawn.
 *
 * <p>The rule worth pinning is the precedence in [CompatibilityPresentation.checkState]. The
 * evaluator can report a check as failed, unverified or satisfied, and a hard failure must never be
 * drawn as merely "unverified" - that would tell the player a missing requirement might be fine.
 */
class CompatibilityPresentationTest {

    private fun check(satisfied: Boolean, failed: Boolean, unverified: Boolean) =
        CompatibilityEvaluator.Check("a check", "a detail", true, satisfied, failed, unverified)

    @Test
    fun failedWinsOverUnverifiedAndSatisfied() {
        assertEquals(
            CheckState.FAILED,
            CompatibilityPresentation.checkState(check(satisfied = false, failed = true, unverified = true)),
        )
        assertEquals(
            CheckState.FAILED,
            CompatibilityPresentation.checkState(check(satisfied = false, failed = true, unverified = false)),
        )
    }

    @Test
    fun unverifiedIsNotSatisfied() {
        assertEquals(
            CheckState.UNVERIFIED,
            CompatibilityPresentation.checkState(check(satisfied = false, failed = false, unverified = true)),
        )
    }

    @Test
    fun satisfiedCheckReadsAsSatisfied() {
        assertEquals(
            CheckState.SATISFIED,
            CompatibilityPresentation.checkState(check(satisfied = true, failed = false, unverified = false)),
        )
    }

    @Test
    fun everyVerdictHasADistinctState() {
        assertEquals(
            CheckState.SATISFIED,
            CompatibilityPresentation.resultState(CompatibilityEvaluator.Result.COMPATIBLE),
        )
        assertEquals(
            CheckState.FAILED,
            CompatibilityPresentation.resultState(CompatibilityEvaluator.Result.UNSUPPORTED),
        )
        assertEquals(
            CheckState.UNVERIFIED,
            CompatibilityPresentation.resultState(CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE),
        )
        assertEquals(
            CheckState.UNKNOWN,
            CompatibilityPresentation.resultState(CompatibilityEvaluator.Result.UNABLE_TO_VERIFY),
        )
    }

    @Test
    fun missingDeviceValuesAreSpelledOutRatherThanBlank() {
        assertEquals("unknown", CompatibilityPresentation.orUnknown(null))
        assertEquals("unknown", CompatibilityPresentation.orUnknown(""))
        assertEquals("Pixel", CompatibilityPresentation.orUnknown("Pixel"))
    }
}
