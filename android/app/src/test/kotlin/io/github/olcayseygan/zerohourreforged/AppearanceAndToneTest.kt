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
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherStatus
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherUiState
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.formatBytes
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the presentation rules the design system depends on: which scheme to draw
 * ([AppearanceMode]), what state the launcher reports ([LauncherStatus], [LauncherUiState.tone],
 * [LauncherStatus.needsFolder]) and how it says it ([LauncherUiState.progressBytes],
 * [formatBytes]).
 *
 * <p>All of it is pure Kotlin with no Android types, which is the point of keeping it out of the
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
     * Every launcher state maps to the tone that expresses it.
     *
     * <p>Each of these is a real branch of SetupActivity's status refresh, so the table is the
     * contract between the backend's verdict and the colour the player sees. A wrong folder and an
     * interrupted copy are both hard failures; a folder that is merely incomplete is a warning,
     * because the player can complete it without losing anything.
     */
    @Test
    fun everyLauncherStateHasItsTone() {
        assertEquals(StatusTone.NEUTRAL, LauncherUiState(status = LauncherStatus.NO_FOLDER).tone)
        assertEquals(StatusTone.INFO, LauncherUiState(status = LauncherStatus.STAGING).tone)
        assertEquals(StatusTone.SUCCESS, LauncherUiState(status = LauncherStatus.READY).tone)
        assertEquals(
            StatusTone.WARNING,
            LauncherUiState(status = LauncherStatus.PERMISSION_LOST).tone,
        )
        assertEquals(StatusTone.WARNING, LauncherUiState(status = LauncherStatus.INCOMPLETE).tone)
        assertEquals(StatusTone.DANGER, LauncherUiState(status = LauncherStatus.INVALID).tone)
        assertEquals(StatusTone.DANGER, LauncherUiState(status = LauncherStatus.STAGING_FAILED).tone)
    }

    /** Every state that is not playable asks the player for a folder; the playable ones do not. */
    @Test
    fun needsFolderCoversEveryUnplayableState() {
        assertTrue(LauncherStatus.NO_FOLDER.needsFolder)
        assertTrue(LauncherStatus.PERMISSION_LOST.needsFolder)
        assertTrue(LauncherStatus.INVALID.needsFolder)
        assertTrue(LauncherStatus.INCOMPLETE.needsFolder)
        assertTrue(LauncherStatus.STAGING_FAILED.needsFolder)
        assertFalse(LauncherStatus.READY.needsFolder)
        // A running copy is not a state the player should be asked to resolve by picking again.
        assertFalse(LauncherStatus.STAGING.needsFolder)
    }

    /** Progress is a fraction of the reported total, clamped, and zero when no total is known. */
    @Test
    fun progressFractionIsClampedAndTotalDependent() {
        assertEquals(0f, LauncherUiState().progressFraction, 0f)
        assertFalse(LauncherUiState().hasProgressTotal)

        val half = LauncherUiState(progressCopied = 50, progressTotal = 100)
        assertTrue(half.hasProgressTotal)
        assertEquals(0.5f, half.progressFraction, 0.0001f)

        // A copy that briefly reports more than it announced must not produce a bar past 100%.
        assertEquals(
            1f,
            LauncherUiState(progressCopied = 150, progressTotal = 100).progressFraction,
            0f,
        )
        assertEquals(
            0f,
            LauncherUiState(progressCopied = -5, progressTotal = 100).progressFraction,
            0f,
        )
    }

    /**
     * The byte line is built from the reported counts, and is absent when no total is known.
     *
     * <p>Absent rather than "0 B / 0 B": an unknown length is drawn with the indeterminate bar, and a
     * zero total would read as a copy that has nothing to do.
     */
    @Test
    fun progressBytesReportTheRealCounts() {
        assertNull(LauncherUiState(progressCopied = 10).progressBytes)
        assertEquals(
            "3.2 GB / 7.5 GB",
            LauncherUiState(progressCopied = 3_435_973_837, progressTotal = 8_053_063_680)
                .progressBytes,
        )
    }

    /** Sizes use the unit a player would use, and stay readable at every magnitude. */
    @Test
    fun formatBytesPicksAReadableUnit() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("512 KB", formatBytes(512L * 1024))
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("812 MB", formatBytes(812L * 1024 * 1024))
        assertEquals("3.4 GB", formatBytes((3.4 * 1024 * 1024 * 1024).toLong()))
        assertEquals("12 GB", formatBytes(12L * 1024 * 1024 * 1024))
    }
}
