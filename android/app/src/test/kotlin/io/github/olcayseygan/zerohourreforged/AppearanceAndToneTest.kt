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

import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherUiState
import io.github.olcayseygan.zerohourreforged.ui.components.StatusTone
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the two presentation rules the design system depends on: which scheme to draw
 * ([AppearanceMode]) and what state the launcher reports ([LauncherUiState.tone]).
 *
 * <p>Both are pure Kotlin with no Android types, which is the point of keeping them out of the
 * composables: the rule is testable here, and the composable only renders the answer.
 */
class AppearanceAndToneTest {

    @Test
    fun systemModeFollowsTheDevice() {
        assertTrue(AppearanceMode.SYSTEM.resolve(systemInDarkTheme = true))
        assertFalse(AppearanceMode.SYSTEM.resolve(systemInDarkTheme = false))
    }

    /** An explicit choice must win, or offering it would be meaningless. */
    @Test
    fun explicitModesIgnoreTheDevice() {
        assertFalse(AppearanceMode.LIGHT.resolve(systemInDarkTheme = true))
        assertTrue(AppearanceMode.DARK.resolve(systemInDarkTheme = false))
    }

    @Test
    fun defaultIsSystem() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.DEFAULT)
    }

    /** A stored value round-trips through its own name. */
    @Test
    fun storageRoundTrips() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStorage(mode.storageValue()))
        }
    }

    /**
     * An unknown or absent stored value falls back to the default instead of throwing.
     *
     * <p>The value can be stale - written by a build with a different set of modes, or absent on a
     * fresh install - and a launcher must still open.
     */
    @Test
    fun unknownStoredValueFallsBackToTheDefault() {
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromStorage("SEPIA"))
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromStorage("dark"))
    }

    /**
     * The launcher's status tone follows the same flags its buttons do.
     *
     * <p>The precedence is what matters: staging is in progress (INFO) even if the previous state
     * could launch, and a remembered-but-unusable folder is a WARNING because the player can fix it
     * by choosing again - not a DANGER, which is reserved for a hard failure.
     */
    @Test
    fun launcherToneFollowsItsFlags() {
        assertEquals(StatusTone.NEUTRAL, LauncherUiState(status = "").tone)
        assertEquals(
            StatusTone.WARNING,
            LauncherUiState(status = "", canClear = true).tone,
        )
        assertEquals(
            StatusTone.SUCCESS,
            LauncherUiState(status = "", canLaunch = true, canClear = true).tone,
        )
        assertEquals(
            StatusTone.INFO,
            LauncherUiState(status = "", staging = true, canLaunch = true).tone,
        )
    }

    /** Progress is a fraction of the reported total, clamped, and zero when no total is known. */
    @Test
    fun progressFractionIsClampedAndTotalDependent() {
        assertEquals(0f, LauncherUiState(status = "").progressFraction, 0f)
        assertFalse(LauncherUiState(status = "").hasProgressTotal)

        val half = LauncherUiState(status = "", progressCopied = 50, progressTotal = 100)
        assertTrue(half.hasProgressTotal)
        assertEquals(0.5f, half.progressFraction, 0.0001f)

        // A copy that briefly reports more than it announced must not produce a bar past 100%.
        assertEquals(
            1f,
            LauncherUiState(status = "", progressCopied = 150, progressTotal = 100).progressFraction,
            0f,
        )
        assertEquals(
            0f,
            LauncherUiState(status = "", progressCopied = -5, progressTotal = 100).progressFraction,
            0f,
        )
    }
}
