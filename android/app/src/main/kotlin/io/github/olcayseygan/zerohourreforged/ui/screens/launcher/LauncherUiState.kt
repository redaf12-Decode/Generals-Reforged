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
import java.util.Locale

/**
 * Everything the launcher screen draws, as plain values.
 *
 * <p>The screen is a pure function of this state and its callbacks, so SetupActivity keeps every
 * decision - which folder, whether the staged copy validates, whether staging is running - and only
 * renders the answer. Nothing here reads storage, the SAF grant or the validator, and nothing here
 * guesses: every field is either a value the Activity read from the backend or a string it formatted
 * from one.
 */
data class LauncherUiState(
    /** Which situation the screen is in; the Activity decides, the screen only draws it. */
    val status: LauncherStatus = LauncherStatus.NO_FOLDER,
    /** The headline status line, already formatted by the Activity. */
    val headline: String = "",
    /** The supporting line beneath it, or empty for none. */
    val detail: String = "",
    /** The display name of the picked folder, or null when none is remembered. */
    val folderName: String? = null,
    /** The real filesystem path the engine reads from, or null until a copy has validated. */
    val enginePath: String? = null,
    /** Staging is running: the actions are disabled and progress is shown. */
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
     * <p>Mapped from [status] rather than re-derived from the flags, so the card's colour cannot
     * disagree with the sentence the Activity wrote: the Activity is the only thing that knows
     * whether a missing archive is a wrong folder or a revoked permission.
     */
    internal val tone: StatusTone
        get() = when (status) {
            LauncherStatus.READY -> StatusTone.SUCCESS
            LauncherStatus.STAGING -> StatusTone.INFO
            LauncherStatus.NO_FOLDER -> StatusTone.NEUTRAL
            LauncherStatus.PERMISSION_LOST -> StatusTone.WARNING
            LauncherStatus.INCOMPLETE -> StatusTone.WARNING
            LauncherStatus.INVALID -> StatusTone.DANGER
            LauncherStatus.STAGING_FAILED -> StatusTone.DANGER
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

    /**
     * "3.4 GB / 8.1 GB", or null when the copy has not reported a total.
     *
     * <p>Formatted from the byte counts the staging callback actually reported, so it says what was
     * copied rather than an estimate. Null rather than "0%" when no total is known: an unknown
     * length is drawn with the indeterminate bar, not with a number that would be a guess.
     */
    internal val progressBytes: String?
        get() = if (hasProgressTotal) {
            formatBytes(progressCopied) + " / " + formatBytes(progressTotal)
        } else {
            null
        }
}

/**
 * A byte count as a short, human-sized string: "812 MB", "3.4 GB".
 *
 * <p>Decimal-looking units with a binary divisor, matching how Android's own storage settings
 * reports sizes. One decimal below ten of a unit and none above it, so a copy that creeps from
 * "3.2 GB" to "3.4 GB" visibly moves instead of sitting on "3 GB" for minutes. Locale.US so the
 * decimal separator does not change under a test.
 */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) {
        return "$bytes B"
    }
    val megabytes = bytes / (1024.0 * 1024.0)
    return when {
        megabytes < 1 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        megabytes < 10 -> String.format(Locale.US, "%.1f MB", megabytes)
        megabytes < 1024 -> String.format(Locale.US, "%.0f MB", megabytes)
        else -> {
            val gigabytes = megabytes / 1024.0
            if (gigabytes < 10) {
                String.format(Locale.US, "%.1f GB", gigabytes)
            } else {
                String.format(Locale.US, "%.0f GB", gigabytes)
            }
        }
    }
}
