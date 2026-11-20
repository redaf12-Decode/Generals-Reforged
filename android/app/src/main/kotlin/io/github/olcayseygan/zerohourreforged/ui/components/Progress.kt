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

package io.github.olcayseygan.zerohourreforged.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.theme.LayoutRules
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * A determinate progress bar with a label and a percentage.
 *
 * <p>Determinate because every operation this application shows progress for reports a real count
 * (files staged, bytes copied). An indeterminate spinner would tell the player less. The percentage
 * is drawn as text as well as in the bar, so the value is readable at a glance and by a screen
 * reader without interpreting a bar.
 *
 * <p>[progress] outside 0..1 is coerced rather than thrown on: the caller is reporting a count that
 * may briefly exceed the total it announced, and a launcher must not crash over a cosmetic mismatch.
 *
 * @param progress completion from 0 to 1.
 * @param label what is being measured, or null when the surrounding card already says it - a caller
 *   that shows the same sentence twice reads as a rendering bug.
 */
@Composable
internal fun DeterminateProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    // Stepped, so the live region below changes about ten times over a whole copy rather than once
    // per file. The visible percentage keeps the exact value.
    val announced = LayoutRules.announcedPercent(percent)
    val progressDescription = stringResource(R.string.progress_announcement, announced)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$percent%",
                modifier = Modifier.padding(start = if (label != null) MaterialTheme.spacing.small else 0.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // The bar publishes its own progressBarRangeInfo, so a screen reader can query the value
        // on focus. It is not enough on its own: a bar that changes while unfocused is silent, and
        // this operation runs for minutes. The live region below announces the stepped percentage
        // instead - stepped, because the copy reports once per file and announcing every file would
        // bury whatever the player was listening to.
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = progressDescription
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/**
 * A progress bar for an operation that cannot report how far along it is.
 *
 * <p>Only used when a copy has not yet reported a total. The bar announces itself as indeterminate
 * rather than pretending to be at 0%, so an operation of unknown length is not shown as one that has
 * not started.
 *
 * @param label what is being measured, or null when the surrounding card already says it.
 */
@Composable
internal fun IndeterminateProgress(modifier: Modifier = Modifier, label: String? = null) {
    val indeterminateDescription = stringResource(R.string.progress_indeterminate)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Indeterminate operations announce themselves once rather than on a timer: there is no
        // progress to report, and a repeating announcement would be noise.
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = indeterminateDescription
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
