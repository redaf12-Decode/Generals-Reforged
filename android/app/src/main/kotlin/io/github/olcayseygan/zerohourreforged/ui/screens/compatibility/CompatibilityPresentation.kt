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
import io.github.olcayseygan.zerohourreforged.ui.components.StatusTone
import java.util.Locale

/**
 * The presentation rules of the compatibility screen, kept out of the composable so they can be
 * unit-tested on the host with plain Java objects - the same approach the existing
 * CompatibilityEvaluator and DeviceCapabilities already use.
 *
 * <p>Nothing here evaluates compatibility: [CompatibilityEvaluator.evaluate] remains the only
 * source of the verdict. This object only decides how a verdict or a device field is displayed, and
 * which shared [StatusTone] carries it.
 */
internal object CompatibilityPresentation {

    /** The verdict as a tone, so the banner and the individual marks agree on the state. */
    fun resultTone(result: CompatibilityEvaluator.Result): StatusTone = when (result) {
        CompatibilityEvaluator.Result.COMPATIBLE -> StatusTone.SUCCESS
        CompatibilityEvaluator.Result.UNSUPPORTED -> StatusTone.DANGER
        CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE -> StatusTone.WARNING
        CompatibilityEvaluator.Result.UNABLE_TO_VERIFY -> StatusTone.NEUTRAL
    }

    /** A single check's tone: failed wins over unverified, so a hard failure is never hidden. */
    fun checkTone(check: CompatibilityEvaluator.Check): StatusTone = when {
        check.failed -> StatusTone.DANGER
        check.unverified -> StatusTone.WARNING
        check.satisfied -> StatusTone.SUCCESS
        else -> StatusTone.NEUTRAL
    }

    /** "unknown" rather than an empty value, so a missing field reads as missing. */
    fun orUnknown(value: String?): String =
        if (value == null || value.isEmpty()) "unknown" else value

    /** Whole gigabytes, matching the existing screen's formatting. */
    fun gb(bytes: Long): String = String.format(Locale.US, "%.0f", bytes.toDouble() / (1024 * 1024 * 1024))

    fun join(values: Array<String>): String = values.joinToString(", ")
}
