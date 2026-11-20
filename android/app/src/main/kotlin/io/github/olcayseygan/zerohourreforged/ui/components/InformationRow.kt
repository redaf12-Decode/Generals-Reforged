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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * A label and its value, the atom of every list of facts in this application.
 *
 * <p>Two shapes, chosen by a single flag rather than by two components:
 *
 * <ul>
 *   <li>a wide row - label on the left, value right-aligned on the right - for short values such as
 *       a device name or a version;</li>
 *   <li>a stacked pair - label above, value below, both left-aligned - when the value is long, a
 *       path or a sentence, and would be squeezed to a few characters by a right-hand column.</li>
 * </ul>
 *
 * <p>Nothing here fixes a height or truncates: a long value wraps and the row grows, which is what
 * makes the list survive a 200% font scale. A null or blank [value] renders an em dash placeholder
 * so a missing fact is visibly missing instead of the row collapsing.
 *
 * @param stacked whether to put the value below the label instead of beside it.
 */
@Composable
internal fun InformationRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val shown = if (value.isNullOrBlank()) "—" else value
    val labelStyle = MaterialTheme.typography.bodyMedium
    val valueStyle = MaterialTheme.typography.bodyLarge
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface

    if (stacked) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline),
        ) {
            Text(text = label, style = labelStyle, color = labelColor)
            Text(text = shown, style = valueStyle, color = valueColor)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = labelStyle,
                color = labelColor,
            )
            Text(
                text = shown,
                modifier = Modifier.weight(1f),
                style = valueStyle,
                color = valueColor,
            )
        }
    }
}
