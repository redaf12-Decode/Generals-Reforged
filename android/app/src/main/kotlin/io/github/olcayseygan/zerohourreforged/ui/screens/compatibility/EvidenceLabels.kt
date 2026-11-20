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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.olcayseygan.zerohourreforged.R

/**
 * The words for each [CompatibilityPresentation.Evidence] value.
 *
 * <p>Kept beside the rules rather than inside them: [CompatibilityPresentation] is plain Kotlin with
 * no Android types so it can be unit-tested on the host, and a string resource needs a
 * [android.content.Context]. The mapping itself is still one table, so a new state cannot be added
 * to the rules without the compiler asking for its label.
 */
@Composable
internal fun CompatibilityPresentation.Evidence.label(): String = stringResource(
    when (this) {
        CompatibilityPresentation.Evidence.VERIFIED -> R.string.evidence_verified
        CompatibilityPresentation.Evidence.UNSUPPORTED -> R.string.evidence_unsupported
        CompatibilityPresentation.Evidence.UNABLE_TO_VERIFY -> R.string.evidence_unable_to_verify
        CompatibilityPresentation.Evidence.NOT_REPORTED -> R.string.evidence_not_reported
        CompatibilityPresentation.Evidence.NOT_AVAILABLE -> R.string.evidence_not_available
    },
)
