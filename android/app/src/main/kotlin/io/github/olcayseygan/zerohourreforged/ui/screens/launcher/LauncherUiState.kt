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

package io.github.olcayseygan.zerohourreforged.ui.screens.launcher

import io.github.olcayseygan.zerohourreforged.ui.components.StatusTone

/**
 * Everything the launcher screen draws, as plain values.
 *
 * <p>The screen is a pure function of this state and the four callbacks, so SetupActivity keeps
 * every decision - which folder, whether the staged copy validates, whether staging is running -
 * and only renders the answer. Nothing here reads storage, the SAF grant or the validator.
 */
data class LauncherUiState(
    /** The headline status line, already formatted by the Activity. */
    val status: String,
    /** The supporting line beneath it, or empty for none. */
    val detail: String = "",
    /** Staging is running: the buttons are disabled and progress is shown. */
    val staging: Boolean = false,
    /** Copied bytes so far, and the total when the copy reported one (0 means unknown). */
    val progressCopied: Long = 0,
    val progressTotal: Long = 0,
    /** The staged folder validated, so the game can start. */
    val canLaunch: Boolean = false,
    /** There is a remembered folder, so it can be forgotten. */
    val canClear: Boolean = false,
) {
    /**
     * The state of the game folder, as the shared status vocabulary.
     *
     * <p>Derived from the flags rather than passed in, so it cannot disagree with the buttons: if
     * the screen offers "Start game", the card is a success. The order matters - staging outranks
     * everything (nothing else is true while it runs), and a remembered-but-unusable folder is a
     * warning rather than a failure, because the player can still fix it by choosing again.
     */
    internal val tone: StatusTone
        get() = when {
            staging -> StatusTone.INFO
            canLaunch -> StatusTone.SUCCESS
            canClear -> StatusTone.WARNING
            else -> StatusTone.NEUTRAL
        }

    /** Whether the copy reported a total, so progress can be shown as a fraction. */
    internal val hasProgressTotal: Boolean
        get() = progressTotal > 0

    /** Copied fraction, coerced, for the determinate bar. Zero when there is no total. */
    internal val progressFraction: Float
        get() = if (hasProgressTotal) {
            (progressCopied.toDouble() / progressTotal.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}
